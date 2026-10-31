package hu.elte.ik.thesis.cinegrade.engine.renderer;

import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GpuWorker;
import hu.elte.ik.thesis.cinegrade.engine.gl.*;
import hu.elte.ik.thesis.cinegrade.engine.grade.BaseBlockUniform;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static hu.elte.ik.thesis.cinegrade.infra.utils.thumbnail.ThumbnailFactory.MAX_EDGE;

/**
 * Renders graded thumbnails of any photo, independent of the viewport.
 * <p>
 * Runs on the background GPU worker with its own {@link ImageTexture}, so it never waits behind the viewport's
 * frames. It renders whatever was {@link #load loaded} last: the caller makes sure the edit belongs to that photo.
 */
public class ThumbnailRenderer implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(ThumbnailRenderer.class);
    private final GpuWorker worker;
    private final ResourceStack resources = new ResourceStack();
    // GPU thread only
    private ImageTexture imageTexture;
    private FrameBuffer fbo;
    private BaseBlockUniform gradeBlock;
    private PixelReader pixelReader;
    private int width, height;

    public ThumbnailRenderer(GpuWorker worker) {
        this.worker = worker;

        worker.submit(() -> {
            imageTexture = resources.push(new ImageTexture());
            fbo = resources.push(new FrameBuffer(1, 1));
            pixelReader = resources.push(new PixelReader(1, 1));
            gradeBlock = resources.push(new BaseBlockUniform());
            return null;
        });
    }

    /**
     * Uploads a photo into this renderer's own texture; the next renders use it. Does not take ownership of
     * {@code decoded.data()}: the caller frees it once this future completed.
     */
    public CompletableFuture<Void> load(LibrawService.DecodedImage decoded) {
        return worker.submit(() -> {
            imageTexture.uploadTexture(decoded);
            applySize();
            return null;
        });
    }

    /**
     * Renders the loaded photo with these edit settings, fitted into MAX_EDGE, never zoomed.
     * The edit is read on the GPU thread, so pass a copy that is not changed afterwards.
     *
     * @return BGRA pixels, or empty if nothing was loaded yet
     */
    public CompletableFuture<Optional<Pixels>> render(EditState editState) {
        return worker.submit(() -> {
            if (width == 0) {
                return Optional.empty();
            }

            fbo.bind();
            try {
                ShaderProgram shader = worker.getShader();
                shader.use();
                gradeBlock.upload(editState);
                gradeBlock.bindBase();
                shader.bindVec3Uniform("uvZoom_OffsetX_OffsetY", ViewportNavigator.ViewTransform.FIT.vec3());
                imageTexture.bind();
                worker.getFullscreenQuad().draw();

                byte[] bgra = new byte[width * height * 4];
                pixelReader.read(buffer -> buffer.get(0, bgra), true);
                return Optional.of(new Pixels(bgra, width, height));
            } finally {
                fbo.unbind();
            }
        });
    }

    /**
     * Sizes the FBO and PBOs to the photo's aspect ratio fitted into MAX_EDGE (never upscaled). GPU thread only.
     */
    private void applySize() {
        double scale = Math.min(1.0, Math.min((double) MAX_EDGE / imageTexture.getWidth(), (double) MAX_EDGE / imageTexture.getHeight()));
        int w = Math.max(1, (int) Math.round(imageTexture.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(imageTexture.getHeight() * scale));
        if (w == width && h == height) {
            return;
        }

        fbo.resize(w, h);
        pixelReader.resize(w, h);
        width = w;
        height = h;
    }

    @Override
    public void close() {
        try {
            worker.submit(() -> {
                resources.close();
                return null;
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            logger.warn("Thumbnail renderer GL resources were not released cleanly", ex);
        }
    }

    /**
     * A rendered thumbnail, {@code width * height * 4} bytes in BGRA order, same row order as the viewport frame.
     */
    public record Pixels(byte[] bgra, int width, int height) {
    }
}
