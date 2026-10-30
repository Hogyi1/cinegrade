package hu.elte.ik.thesis.cinegrade.engine.renderer;

import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GpuWorker;
import hu.elte.ik.thesis.cinegrade.engine.gl.*;
import hu.elte.ik.thesis.cinegrade.engine.grade.BaseBlockUniform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static hu.elte.ik.thesis.cinegrade.infra.utils.thumbnail.ThumbnailFactory.MAX_EDGE;

/**
 * Renders a graded thumbnail of the photo that is open in the viewport.
 * <p>
 * It has no texture of its own: it samples the {@link ViewportRenderer}'s {@link ImageTexture}. Both run on the
 * same GPU worker (one GL context), so the texture id is valid here too, and jobs run in submit order, so a
 * render submitted before the next {@code show} still sees the current photo.
 */
public class ThumbnailRenderer implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(ThumbnailRenderer.class);
    private final GpuWorker worker;
    private final ViewportRenderer viewport;
    private final ResourceStack resources = new ResourceStack();
    // GPU thread only
    private FrameBuffer fbo;
    private BaseBlockUniform gradeBlock;
    private PixelReader pixelReader;
    private int width, height;

    // [AI-GENERATED METHOD]
    public ThumbnailRenderer(GpuWorker worker, ViewportRenderer viewport) {
        this.worker = worker;
        this.viewport = viewport;

        worker.submit(() -> {
            fbo = resources.push(new FrameBuffer(1, 1));
            pixelReader = resources.push(new PixelReader(1, 1));
            gradeBlock = resources.push(new BaseBlockUniform());
            return null;
        });
    }

    // [AI-GENERATED METHOD]

    /**
     * Renders the viewport's photo with these edit settings, fitted into MAX_EDGE, never zoomed.
     * The edit is read on the GPU thread, so pass a copy that is not changed afterwards.
     *
     * @return BGRA pixels, or empty if the viewport has no photo yet
     */
    public CompletableFuture<Optional<Pixels>> render(EditState editState) {
        return worker.submit(() -> {
            ImageTexture source = viewport.getImageTexture();
            if (source == null || source.getWidth() == 0) {
                return Optional.empty();
            }
            applySize(source);

            fbo.bind();
            try {
                ShaderProgram shader = worker.getShader();
                shader.use();
                gradeBlock.upload(editState);
                // The program keeps the viewport's last zoom/pan, always reset it to the whole image
                shader.bindVec3Uniform("uvZoom_OffsetX_OffsetY", ViewportRenderer.ViewTransform.FIT.vec3());
                source.bind();
                worker.getFullscreenQuad().draw();

                byte[] bgra = new byte[width * height * 4];
                pixelReader.read(buffer -> buffer.get(0, bgra), true);
                return Optional.of(new Pixels(bgra, width, height));
            } finally {
                fbo.unbind();
            }
        });
    }

    // [AI-GENERATED METHOD]

    /**
     * Sizes the FBO and PBOs to the photo's aspect ratio fitted into MAX_EDGE (never upscaled). GPU thread only.
     */
    private void applySize(ImageTexture source) {
        double scale = Math.min(1.0, Math.min((double) MAX_EDGE / source.getWidth(), (double) MAX_EDGE / source.getHeight()));
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
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
