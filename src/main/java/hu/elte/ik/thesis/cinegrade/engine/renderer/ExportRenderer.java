package hu.elte.ik.thesis.cinegrade.engine.renderer;

import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GpuWorker;
import hu.elte.ik.thesis.cinegrade.engine.RenderEngine;
import hu.elte.ik.thesis.cinegrade.engine.gl.*;
import hu.elte.ik.thesis.cinegrade.engine.grade.BaseBlockUniform;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteOrder;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL30C.GL_RGBA32F;

/**
 * Renders graded thumbnails of any photo, independent of the viewport.
 * <p>
 * Runs on the background GPU worker with its own {@link ImageTexture}, so it never waits behind the viewport's
 */
public class ExportRenderer implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(ExportRenderer.class);
    private final GpuWorker worker;
    private final ResourceStack resources = new ResourceStack();
    // GPU thread only
    private ImageTexture imageTexture;
    private FrameBuffer fbo;
    private BaseBlockUniform gradeBlock;
    private PixelReader pixelReader;
    private int width, height;
    private RenderEngine.Format currentFormat;

    public ExportRenderer(GpuWorker worker) {
        this.worker = worker;

        worker.submit(() -> {
            imageTexture = resources.push(new ImageTexture());
            fbo = resources.push(new FrameBuffer(1, 1, GL_RGBA32F));
            pixelReader = resources.push(new PixelReader(1, 1));
            gradeBlock = resources.push(new BaseBlockUniform());
            return null;
        });
    }

    public CompletableFuture<Optional<ThumbnailRenderer.Pixels>> render8(EditState edit, LibrawService.DecodedImage
            image) {
        return render(edit, image, RenderEngine.Format.BGRA8, () -> {
            byte[] bgra = new byte[width * height * 4];
            pixelReader.read(buffer -> buffer.get(0, bgra), true);
            return new ThumbnailRenderer.Pixels(bgra, width, height);
        });
    }

    public CompletableFuture<Optional<Pixels16>> render16(EditState edit, LibrawService.DecodedImage image) {
        return render(edit, image, RenderEngine.Format.RGB16, () -> {
            short[] rgb = new short[width * height * 3];
            pixelReader.read(buffer ->
                    buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(0, rgb), true);
            return new Pixels16(rgb, width, height);
        });
    }

    private <T> CompletableFuture<Optional<T>> render(EditState editState,
                                                      LibrawService.DecodedImage decodedImage,
                                                      RenderEngine.Format format,
                                                      Supplier<T> readBack) {
        return worker.submit(() -> {
            if (decodedImage == null) return Optional.empty();
            imageTexture.uploadTexture(decodedImage);
            applySize(format);
            fbo.bind();
            try {
                ShaderProgram shader = worker.getShader();
                shader.use();
                gradeBlock.upload(editState);
                gradeBlock.bindBase();
                shader.bindVec3Uniform("uvZoom_OffsetX_OffsetY", ViewportNavigator.ViewTransform.FIT.vec3());
                shader.bindIntUniform("linearInput", imageTexture.isLinear() ? 1 : 0);
                imageTexture.bind();
                worker.getFullscreenQuad().draw();

                return Optional.of(readBack.get());
            } finally {
                fbo.unbind();
            }
        });
    }

    public CompletableFuture<float[]> renderLut(EditState editState, int lutSize) {
        return worker.submit(() -> {
            ShaderProgram shader = worker.getShader();
            width = lutSize;
            height = lutSize * lutSize;
            fbo.resize(width, height);
            pixelReader.setFormat(RenderEngine.Format.RGB32F);
            pixelReader.resize(width, height);

            try {
                fbo.bind();
                shader.use();
                gradeBlock.upload(editState);
                gradeBlock.bindBase();
                shader.bindVec3Uniform("uvZoom_OffsetX_OffsetY", ViewportNavigator.ViewTransform.FIT.vec3());
                shader.bindIntUniform("linearInput", 0);
                shader.bindIntUniform("lutSize", lutSize);
                worker.getFullscreenQuad().draw();

                float[] lut = new float[lutSize * lutSize * lutSize * 3];
                pixelReader.read(buffer -> buffer.order(ByteOrder.nativeOrder()).asFloatBuffer().get(0, lut), true);
                return lut;
            } finally {
                fbo.unbind();
                shader.bindIntUniform("lutSize", 0);
            }
        });
    }

    /**
     * Sizes the FBO and PBOs to the photo's aspect ratio fitted into MAX_EDGE (never upscaled). GPU thread only.
     */
    private void applySize(RenderEngine.Format format) {
        int w = imageTexture.getWidth();
        int h = imageTexture.getHeight();
        if (w == width && h == height && currentFormat == format) {
            return;
        }
        currentFormat = format;
        pixelReader.setFormat(format);
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

    public record Pixels16(short[] rgb, int width, int height) {
    }
}
