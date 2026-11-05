package hu.elte.ik.thesis.cinegrade.javafx.ui.input;

import hu.elte.ik.thesis.cinegrade.engine.gl.ShaderProgram;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import lombok.Getter;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Zoom and pan of the development viewport's ImageView.
 * <p>
 * Only view state, it never touches the renderer: every change goes to {@code onChange} as a {@link ViewTransform}.
 * Everything is in texture uv [0,1]: {@code zoomFactor} is the visible fraction of the photo (1 = fit, 0.5 = 2x),
 * {@code offX/offY} is the top-left corner of the visible window. Screen top is v = 0 (the quad and the readback
 * each flip once, so no 1 - v).
 * <ul>
 *     <li>click: zoom in at the cursor to the chosen zoom type (150% / 200%), click again: back to fit</li>
 *     <li>left drag while zoomed: pan</li>
 *     <li>buttons / Tab: the zoom types, slider / + -: anything between fit and 400%</li>
 * </ul>
 */
public class ViewportNavigator {

    public static final double MAX_MAGNIFICATION = 4.0;
    private static final float EPSILON = 1e-4f;
    // What a click does: plus at fit (zooms in), minus while zoomed (back to fit). Loaded at 32 px, the usual
    // cursor size; the hotspot is the middle of the lens
    private static final Cursor ZOOM_IN_CURSOR = magnifier("/images/cursors/magnifier_plus.png");
    private static final Cursor ZOOM_OUT_CURSOR = magnifier("/images/cursors/magnifier_minus.png");
    // The hotspot is the tip of the dropper
    private static final Cursor PICK_CURSOR = new javafx.scene.ImageCursor(new javafx.scene.image.Image(
            java.util.Objects.requireNonNull(ViewportNavigator.class.getResourceAsStream("/images/cursors/eyedropper.png")), 32, 32, true, true), 2, 28);

    private final ImageView viewport;
    private final Consumer<ViewTransform> onChange;
    // Magnification relative to fit (1 / zoomFactor), for the slider
    private final ReadOnlyDoubleWrapper magnification = new ReadOnlyDoubleWrapper(1.0);
    @Getter
    private boolean isZoomed;
    private float zoomFactor = 1.0f, offX = 0.0f, offY = 0.0f;
    private double lastX, lastY;
    private boolean dragging;
    // Where a click zooms to; the last zoom type picked with a button or Tab
    private ZoomType clickZoom = ZoomType.X200;
    // Not null while picking: a click goes here as a texture uv instead of zooming
    private Consumer<UV> pickHandler;

    public ViewportNavigator(ImageView viewport, Consumer<ViewTransform> onChange) {
        this.viewport = viewport;
        this.onChange = onChange;
        viewport.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
        viewport.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        viewport.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
        viewport.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleMouseClicked);
        updateCursor();
    }

    public ReadOnlyDoubleProperty magnificationProperty() {
        return magnification.getReadOnlyProperty();
    }

    /**
     * Whole photo, no pan.
     */
    public void fit() {
        zoomFactor = 1.0f;
        offX = 0.0f;
        offY = 0.0f;
        push();
    }

    /**
     * Button: zooms to the type around the middle of the view, and a click zooms there from now on.
     */
    public void zoomTo(ZoomType type) {
        if (type != ZoomType.FIT) {
            clickZoom = type;
        }
        setMagnification(type.getMagnification());
    }

    /**
     * Tab: fit -> 150% -> 200% -> fit. From a slider value in between it goes to the next larger type.
     */
    public void cycleZoomType() {
        double current = magnification.get();
        for (ZoomType type : ZoomType.values()) {
            if (type.getMagnification() > current + EPSILON) {
                zoomTo(type);
                return;
            }
        }
        zoomTo(ZoomType.FIT);
    }

    /**
     * Slider: magnification relative to fit, around the middle of the view. The same value again does nothing, so
     * the slider following the navigator does not re-centre a click zoom.
     */
    public void setMagnification(double value) {
        if (Math.abs(value - magnification.get()) < EPSILON) {
            return;
        }
        zoomAt(new UV(0.5, 0.5), (float) (1.0 / value));
    }

    /**
     * Changes the zoom while the texture point under {@code cursor} stays under it:
     * p = off + c * zoom is kept, so off' = p - c * zoom'.
     *
     * @param cursor  view uv of the fixed point
     * @param newZoom visible fraction, clamped between 400% and fit
     */
    public void zoomAt(UV cursor, float newZoom) {
        double pointX = offX + cursor.x() * zoomFactor;
        double pointY = offY + cursor.y() * zoomFactor;

        zoomFactor = Math.clamp(newZoom, (float) (1.0 / MAX_MAGNIFICATION), 1.0f);
        offX = (float) (pointX - cursor.x() * zoomFactor);
        offY = (float) (pointY - cursor.y() * zoomFactor);
        push();
    }

    /**
     * Moves the photo with the mouse: dragging across the whole view moves the window by its own width.
     */
    public void pan(double dxPixels, double dyPixels) {
        Bounds b = viewport.getLayoutBounds();
        if (b.getWidth() <= 0 || b.getHeight() <= 0) {
            return;
        }
        offX -= (float) (dxPixels / b.getWidth() * zoomFactor);
        offY -= (float) (dyPixels / b.getHeight() * zoomFactor);
        push();
    }

    /**
     * Texture uv under a view uv, the same formula as fragment.frag. The eyedropper will sample here.
     */
    public UV toTextureUV(UV view) {
        return new UV(offX + view.x() * zoomFactor, offY + view.y() * zoomFactor);
    }

    /**
     * From now on a click reports the texture uv under the cursor instead of zooming. Dragging still pans.
     */
    public void startPicking(Consumer<UV> onPicked) {
        pickHandler = onPicked;
        updateCursor();
    }

    public void stopPicking() {
        pickHandler = null;
        updateCursor();
    }

    private void handleMousePressed(MouseEvent mouseEvent) {
        if (mouseEvent.getButton() != MouseButton.PRIMARY || !isZoomed) {
            return;
        }
        dragging = true;
        lastX = mouseEvent.getX();
        lastY = mouseEvent.getY();
    }

    private void handleMouseDragged(MouseEvent mouseEvent) {
        if (!dragging) {
            return;
        }
        pan(mouseEvent.getX() - lastX, mouseEvent.getY() - lastY);
        lastX = mouseEvent.getX();
        lastY = mouseEvent.getY();
        updateCursor();
    }

    private void handleMouseReleased(MouseEvent mouseEvent) {
        dragging = false;
        updateCursor();
    }

    private void handleMouseClicked(MouseEvent mouseEvent) {
        // the end of a pan is also a click: only a click without moving toggles
        if (mouseEvent.getButton() != MouseButton.PRIMARY || !mouseEvent.isStillSincePress()) {
            return;
        }
        if (pickHandler != null) {
            pickHandler.accept(toTextureUV(convertToUV(mouseEvent)));
            mouseEvent.consume();
            return;
        }
        if (isZoomed) {
            fit();
        } else {
            zoomAt(convertToUV(mouseEvent), (float) (1.0 / clickZoom.getMagnification()));
        }
        mouseEvent.consume();
    }

    /**
     * Mouse position as view uv. The layout bounds are the displayed image (preserveRatio), not the fit box.
     */
    public UV convertToUV(MouseEvent mouseEvent) {
        Bounds b = viewport.getLayoutBounds();
        double cu = mouseEvent.getX() / b.getWidth();
        double cv = mouseEvent.getY() / b.getHeight();
        return new UV(cu, cv);
    }

    /**
     * Keeps the visible window [off, off + zoom] inside [0, 1], so nothing outside the photo is ever shown.
     * Near an edge the point under the cursor slides a little instead.
     */
    private void clampOffset() {
        offX = Math.clamp(offX, 0, 1 - zoomFactor);
        offY = Math.clamp(offY, 0, 1 - zoomFactor);
    }

    private void push() {
        clampOffset();
        isZoomed = zoomFactor < 1.0f - EPSILON;
        magnification.set(1.0 / zoomFactor);
        updateCursor();
        onChange.accept(new ViewTransform(new ShaderProgram.Vector3(zoomFactor, offX, offY)));
    }

    private void updateCursor() {
        if (dragging) {
            viewport.setCursor(Cursor.CLOSED_HAND);
        } else if (pickHandler != null) {
            viewport.setCursor(PICK_CURSOR);
        } else {
            viewport.setCursor(isZoomed ? ZOOM_OUT_CURSOR : ZOOM_IN_CURSOR);
        }
    }

    private static Cursor magnifier(String path) {
        Image image = new Image(
                Objects.requireNonNull(ViewportNavigator.class.getResourceAsStream(path), path), 32, 32, true, true);
        return new ImageCursor(image, 13, 13);
    }

    /**
     * The fixed zoom levels of the buttons and of Tab, relative to fit.
     */
    @Getter
    public enum ZoomType {
        FIT(1.0),
        X150(1.5),
        X200(2.0);

        private final double magnification;

        ZoomType(double magnification) {
            this.magnification = magnification;
        }

    }

    /**
     * Zoom and pan of the viewport. Not part of the edit (never saved, not in the grade UBO).
     *
     * @param vec3 x = uv scale, 1 = whole image, 0.5 = 2x zoom,
     *             y = left edge of the visible region in [0,1] uv space,
     *             z = top edge of the visible region in [0,1] uv space
     */
    public record ViewTransform(ShaderProgram.Vector3 vec3) {
        public static final ViewTransform FIT = new ViewTransform(new ShaderProgram.Vector3(1f, 0f, 0f));
        public static final ViewTransform _150 = new ViewTransform(new ShaderProgram.Vector3(1f / 1.5f, 0f, 0f));
        public static final ViewTransform _200 = new ViewTransform(new ShaderProgram.Vector3(1f / 2f, 0f, 0f));
    }

    /**
     * A point in UV space.
     */
    public record UV(double x, double y) {
    }
}
