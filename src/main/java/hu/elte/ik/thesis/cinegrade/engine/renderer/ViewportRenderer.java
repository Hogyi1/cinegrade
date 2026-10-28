package hu.elte.ik.thesis.cinegrade.engine.renderer;

import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GpuWorker;
import hu.elte.ik.thesis.cinegrade.engine.gl.*;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL20.glUniform3f;

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

    // GPU thread only
    private ImageTexture imageTexture;
    private FrameBuffer fbo;
    private BaseBlockUniform gradeBlock;
    private PixelReader pixelReader;
    private WritableImage[] writableImages = new WritableImage[2];
    private int writeIndex, width, height;
    private int areaWidth, areaHeight;
    private boolean hasImage;

    // Written by the FX thread, read by the render job
    private final AtomicReference<EditState> latestEdit = new AtomicReference<>(new EditState());
    private final AtomicReference<ViewTransform> latestView = new AtomicReference<>(ViewTransform.FIT);
    private final AtomicBoolean renderQueued = new AtomicBoolean();

    /**
     * Zoom and pan of the viewport. Not part of the edit (never saved, not in the grade UBO).
     *
     * @param scale   uv scale, 1 = whole image, 0.5 = 2x zoom
     * @param offsetX left edge of the visible region in [0,1] uv space
     * @param offsetY bottom edge of the visible region in [0,1] uv space
     */
    public record ViewTransform(float scale, float offsetX, float offsetY) {
        public static final ViewTransform FIT = new ViewTransform(1f, 0f, 0f);
    }

    public ViewportRenderer(GpuWorker gpuWorker) {
        this.worker = gpuWorker;

        // Runs before any other job of this renderer (the queue is FIFO)
        worker.submit(() -> {
            imageTexture = resources.push(new ImageTexture());
            fbo = resources.push(new FrameBuffer(1, 1));
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
     * Uploads a decoded photo and renders it. Takes ownership of {@code decodedRaw.data()}: it is freed after the
     * upload, the caller must not touch it afterwards.
     */
    public CompletableFuture<Void> show(LibrawService.DecodedRaw decodedRaw) {
        return worker.submit(() -> {
            try {
                imageTexture.uploadTexture(decodedRaw);
            } finally {
                MemoryUtil.memFree(decodedRaw.data());
            }
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
    public void requestView(ViewTransform view) {
        latestView.set(view);
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
        // Cleared before reading, so a request arriving during this render queues the next one
        renderQueued.set(false);
        if (!hasImage || width == 0) {
            return;
        }

        EditState edit = latestEdit.get();
        ViewTransform view = latestView.get();

        ShaderProgram shader = worker.getShader();
        FullscreenQuad quad = worker.getFullscreenQuad();

        fbo.bind();
        shader.use();
        // 48 bytes, so uploading every render costs nothing. Skipping it when "unchanged" would need EditState to be
        // immutable: a mutated-in-place EditState is the same object with new values
        gradeBlock.upload(edit);
        gradeBlock.bindBase();
        glUniform3f(shader.uniform("uvZoom_OffsetX_OffsetY"), view.scale(), view.offsetX(), view.offsetY());
        imageTexture.bind();
        quad.draw();

        // Nothing newer waiting: this is the last frame for now, read it synchronously so it shows
        boolean last = !renderQueued.get();
        int w = width;
        int h = height;
        pixelReader.read(bytes -> {
            WritableImage target = writableImages[writeIndex];
            target.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getByteBgraInstance(), bytes, w * 4);
            writeIndex = 1 - writeIndex;
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
