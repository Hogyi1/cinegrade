package hu.elte.ik.thesis.cinegrade.engine.renderer;

import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GpuWorker;
import hu.elte.ik.thesis.cinegrade.engine.gl.*;
import hu.elte.ik.thesis.cinegrade.engine.grade.BaseBlockUniform;
import hu.elte.ik.thesis.cinegrade.engine.grade.WhiteBalancer;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.GL_RGBA32F;

/**
 * Renders the open photo for the development viewport on the interactive GPU worker.
 * <p>
 * Threads: the public methods are called from the FX thread and only queue jobs; every GL object here is created
 * and used only inside those jobs (the worker thread). The finished frame is published on the FX thread through
 * {@link #frameProperty()}.
 * <p>
 * Latest wins: edits and view changes only store the newest value and queue at most one render job. When that job
 * runs it draws whatever is newest at that moment, so a slider moving faster than the GPU skips frames instead of
 * queueing them.
 */
public class ViewportRenderer implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(ViewportRenderer.class);

    private final GpuWorker worker;
    private final ResourceStack resources = new ResourceStack();
    private final ReadOnlyObjectWrapper<Image> frame = new ReadOnlyObjectWrapper<>();
    private final AtomicReference<EditState> latestEdit = new AtomicReference<>(new EditState());
    private final AtomicReference<ViewportNavigator.ViewTransform> latestView = new AtomicReference<>(ViewportNavigator.ViewTransform.FIT);
    private final AtomicBoolean renderQueued = new AtomicBoolean();

    private ImageTexture imageTexture;
    private FrameBuffer fbo, sampleFbo;
    private BaseBlockUniform gradeBlock;
    private PixelReader pixelReader;
    private WritableImage[] writableImages = new WritableImage[2];
    private int writeIndex, width, height;
    private int areaWidth, areaHeight;
    private volatile boolean hasImage;
    @Setter
    private volatile Consumer<ByteBuffer> histogramDraw;

    public ViewportRenderer(GpuWorker gpuWorker) {
        this.worker = gpuWorker;

        worker.submit(() -> {
            imageTexture = resources.push(new ImageTexture());
            fbo = resources.push(new FrameBuffer(1, 1, GL_RGBA8));
            sampleFbo = resources.push(new FrameBuffer(5, 5, GL_RGBA32F));
            pixelReader = resources.push(new PixelReader(1, 1));
            gradeBlock = resources.push(new BaseBlockUniform());
            return null;
        });
    }

    public ReadOnlyObjectProperty<Image> frameProperty() {
        return frame.getReadOnlyProperty();
    }

    /**
     * The size of the area the photo is shown in, in device pixels (layout size * output scale).
     * The frame is rendered at the largest size with the photo's aspect ratio that fits in it.
     */
    public CompletableFuture<Void> resize(int areaWidth, int areaHeight) {
        return worker.submit(() -> {
            this.areaWidth = areaWidth;
            this.areaHeight = areaHeight;
            applySize();
            return null;
        }).thenRun(this::scheduleRender);
    }

    /**
     * Uploads a decoded photo and renders it. Does not take ownership of {@code decodedRaw.data()}: the caller
     * frees it once this future completed.
     */
    public CompletableFuture<Void> show(LibrawService.DecodedImage decodedRaw) {
        return worker.submit(() -> {
            imageTexture.uploadTexture(decodedRaw);
            hasImage = true;
            applySize();
            return null;
        }).thenRun(this::scheduleRender);
    }

    /**
     * Renders with these edit settings (latest wins). Call while a slider moves; nothing is saved here.
     */
    public void requestRender(EditState editState) {
        latestEdit.set(editState);
        scheduleRender();
    }

    /**
     * Renders with this zoom/pan (latest wins). The edit settings stay as they were.
     */
    public void requestView(ViewportNavigator.ViewTransform view) {
        latestView.set(view);
        scheduleRender();
    }

    // [AI-GENERATED METHOD] DEV: shader hot reload, see Documentation/REMOVE_BEFORE_RELEASE.md

    /**
     * Draws the current photo again with the latest edit and view (after the shader was reloaded).
     */
    public void rerender() {
        scheduleRender();
    }

    private void scheduleRender() {
        // Only the first request since the last render queues a job; later ones just replaced the values
        if (renderQueued.compareAndSet(false, true)) {
            worker.submit(() -> {
                renderLatest();
                return null;
            });
        }
    }

    public void clear() {
        hasImage = false;
        frame.set(null);
    }

    /**
     * The average linear color of a few source pixels around this texture uv, as the photo was decoded: draws only
     * that spot into the small float FBO, so no grade and no 8-bit rounding of the viewport frame is in it.
     *
     * @param defaults the photo's edit with every setting at its default
     * @return r, g, b; null if no photo is shown
     */
    public CompletableFuture<float[]> sample(EditState defaults, ViewportNavigator.UV uv) {
        return worker.submit(() -> {
            if (!hasImage) return null;

            // 5 source pixels along the long edge; the uv scale is the same on both axes
            float zoom = 5f / Math.max(imageTexture.getWidth(), imageTexture.getHeight());
            ViewportNavigator.ViewTransform view = new ViewportNavigator.ViewTransform(new ShaderProgram.Vector3(zoom,
                    Math.clamp((float) uv.x() - zoom / 2, 0f, 1f - zoom),
                    Math.clamp((float) uv.y() - zoom / 2, 0f, 1f - zoom)));

            sampleFbo.bind();
            try {
                ShaderProgram shader = worker.getShader();
                shader.use();
                gradeBlock.upload(defaults);
                gradeBlock.bindBase();
                shader.bindVec3Uniform("uvZoom_OffsetX_OffsetY", view.vec3());
                shader.bindIntUniform("linearInput", imageTexture.isLinear() ? 1 : 0);
                imageTexture.bind();
                worker.getFullscreenQuad().draw();

                FloatBuffer floats = org.lwjgl.BufferUtils.createFloatBuffer(5 * 5 * 3);
                glReadPixels(0,0,5,5, GL_RGB, GL_FLOAT, floats);

                float[] avg = new float[3];
                for (int i = 0; i < 5*5*3; i++) {
                    avg[i % 3] += (float) (WhiteBalancer.srgbToLinear(floats.get(i)) / 25.f);
                }

                return avg;
            } finally {
                sampleFbo.unbind();
            }
        });
    }

    /**
     * Sizes the FBO, PBOs and images to the photo's aspect ratio fitted into the area. GPU thread only.
     */
    private void applySize() {
        if (!hasImage || areaWidth <= 0 || areaHeight <= 0) {
            return;
        }
        double scale = Math.min((double) areaWidth / imageTexture.getWidth(), (double) areaHeight / imageTexture.getHeight());
        int w = Math.max(1, (int) Math.round(imageTexture.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(imageTexture.getHeight() * scale));
        if (w == width && h == height) {
            return;
        }

        fbo.resize(w, h);
        pixelReader.resize(w, h);
        writableImages = new WritableImage[]{new WritableImage(w, h), new WritableImage(w, h)};
        width = w;
        height = h;
    }

    private void renderLatest() {
        renderQueued.set(false);
        if (!hasImage || width == 0) {
            return;
        }

        EditState edit = latestEdit.get();
        ViewportNavigator.ViewTransform view = latestView.get();

        ShaderProgram shader = worker.getShader();
        FullscreenQuad quad = worker.getFullscreenQuad();

        fbo.bind();
        shader.use();
        gradeBlock.upload(edit);
        // binding 0 is shared with the thumbnail renderer's UBO in this context: point it back at ours
        gradeBlock.bindBase();
        shader.bindVec3Uniform("uvZoom_OffsetX_OffsetY", view.vec3());
        shader.bindIntUniform("linearInput", imageTexture.isLinear() ? 1 : 0);
        imageTexture.bind();
        quad.draw();

        boolean last = !renderQueued.get();
        int w = width;
        int h = height;
        pixelReader.read(bytes -> {
            WritableImage target = writableImages[writeIndex];
            target.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getByteBgraInstance(), bytes, w * 4);
            writeIndex = 1 - writeIndex;
            if (histogramDraw != null) {
                histogramDraw.accept(bytes);
            }
            Platform.runLater(() -> frame.set(target));
        }, last);
        fbo.unbind();
    }

    @Override
    public void close() {
        try {
            worker.submit(() -> {
                resources.close();
                return null;
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            logger.warn("Viewport renderer GL resources were not released cleanly", ex);
        }
    }
}
