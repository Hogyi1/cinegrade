package hu.elte.ik.thesis.cinegrade.engine.renderer;

import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GpuWorker;
import hu.elte.ik.thesis.cinegrade.engine.gl.*;
import hu.elte.ik.thesis.cinegrade.engine.grade.BaseBlockUniform;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    /**
     * -- GETTER --
     *  The uploaded photo, shared with
     * . GPU thread only; null until the setup job ran.
     */
    // GPU thread only
    @Getter
    private ImageTexture imageTexture;
    private FrameBuffer fbo;
    private BaseBlockUniform gradeBlock;
    private PixelReader pixelReader;
    private WritableImage[] writableImages = new WritableImage[2];
    private int writeIndex, width, height;
    private int areaWidth, areaHeight;
    private boolean hasImage;

    public ViewportRenderer(GpuWorker gpuWorker) {
        this.worker = gpuWorker;

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
        imageTexture.bind();
        quad.draw();

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
