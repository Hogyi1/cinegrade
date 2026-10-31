package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.infra.utils.format.FormatUtils;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.InputListener;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Center viewport of the development page: shows the rendered photo.
 */
public class DevelopViewportController {

    private static final Logger logger = LogManager.getLogger(DevelopViewportController.class);
    private final EditingManager editingManager;

    // Resizing the FBO on every pixel of a window drag is slow: wait until the size settles
    private final PauseTransition resizeDebounce = new PauseTransition(Duration.millis(120));

    @FXML
    private StackPane viewportRoot;
    @FXML
    private StackPane viewportImageContainer;
    @FXML
    private ImageView viewportImageView;
    @FXML
    private ImageView thumbnailImageView;
    @FXML
    private Label photoNameLabel;
    @FXML
    private Label photoMetaLabel;
    @FXML
    private ToggleButton splitViewToggle;
    @FXML
    private Button zoomFitBtn;
    @FXML
    private Button zoom150Btn;
    @FXML
    private Button zoom200Btn;
    @FXML
    private Slider zoomSlider;
    @FXML
    private VBox loadProgressOverlay;

    private ViewportNavigator viewportNavigator;
    private InputListener inputListener;

    public DevelopViewportController(EditingManager editingManager) {
        this.editingManager = editingManager;
    }

    @FXML
    private void initialize() {
        viewportImageView.imageProperty().bind(editingManager.viewportImageProperty());
        thumbnailImageView.imageProperty().bind(editingManager.placeholderImageProperty());
        thumbnailImageView.visibleProperty().bind(editingManager.loadingProperty());
        if (loadProgressOverlay != null) {
            loadProgressOverlay.visibleProperty().bind(editingManager.loadingProperty());
            loadProgressOverlay.managedProperty().bind(editingManager.loadingProperty());
        }

        // The frame is rendered in device pixels; the ImageView scales it back to layout size
        viewportImageView.setPreserveRatio(true);
        viewportImageView.fitWidthProperty().bind(viewportImageContainer.widthProperty());
        viewportImageView.fitHeightProperty().bind(viewportImageContainer.heightProperty());
        // Let the container shrink below the current frame size
        viewportImageContainer.setMinSize(0, 0);

        resizeDebounce.setOnFinished(e -> pushAreaSize());
        viewportImageContainer.widthProperty().addListener((obs, o, n) -> resizeDebounce.playFromStart());
        viewportImageContainer.heightProperty().addListener((obs, o, n) -> resizeDebounce.playFromStart());
        viewportImageView.visibleProperty().bind(editingManager.loadingProperty().not());

        // the toggle and Ctrl+Shift+B both flip the manager's flag, which resets itself on a photo change
        splitViewToggle.selectedProperty().bindBidirectional(editingManager.showOriginalProperty());
        editingManager.currentPhotoProperty().addListener((obs, old, photo) -> showPhotoInfo(photo));
        showPhotoInfo(editingManager.currentPhotoProperty().get());

        setupNavigation();
        setupInput();
    }

    private void showPhotoInfo(PhotoEntry photo) {
        if (photo == null) {
            photoNameLabel.setText("No photo selected");
            photoMetaLabel.setText("");
            return;
        }
        String name = photo.getFileName();
        int dot = name.lastIndexOf('.');
        String type = photo.isRaw() ? "RAW" : dot >= 0 ? name.substring(dot + 1).toUpperCase() : "";
        photoNameLabel.setText(name);
        photoMetaLabel.setText(String.format("%s · %s", type, FormatUtils.formatBytes(photo.getFileSize())));
    }

    private void setupNavigation() {
        viewportNavigator = new ViewportNavigator(viewportImageView, editingManager::setView);

        // a new photo starts at fit
        editingManager.loadingProperty().addListener((obs, was, loading) -> {
            if (loading) viewportNavigator.fit();
        });

        zoomFitBtn.setOnAction(e -> viewportNavigator.fit());
        zoom150Btn.setOnAction(e -> viewportNavigator.zoomTo(ViewportNavigator.ZoomType.X150));
        zoom200Btn.setOnAction(e -> viewportNavigator.zoomTo(ViewportNavigator.ZoomType.X200));

        // both ways: setMagnification ignores the value it just reported, so this does not loop
        zoomSlider.setMax(ViewportNavigator.MAX_MAGNIFICATION);
        viewportNavigator.magnificationProperty().addListener((obs, o, n) -> zoomSlider.setValue(n.doubleValue()));
        zoomSlider.valueProperty().addListener((obs, o, n) -> viewportNavigator.setMagnification(n.doubleValue()));
    }

    private void setupInput() {
        viewportRoot.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> viewportRoot.requestFocus());

        inputListener = new InputListener(viewportRoot, InputListener.focusedNotTyping(viewportRoot));
        inputListener.bind(new KeyCodeCombination(KeyCode.TAB), viewportNavigator::cycleZoomType);
        inputListener.bind(new KeyCharacterCombination("+", KeyCombination.SHIFT_ANY), zoomSlider::increment);
        inputListener.bind(new KeyCodeCombination(KeyCode.ADD), zoomSlider::increment);
        inputListener.bind(new KeyCharacterCombination("-", KeyCombination.SHIFT_ANY), zoomSlider::decrement);
        inputListener.bind(new KeyCodeCombination(KeyCode.SUBTRACT), zoomSlider::decrement);
    }

    private void pushAreaSize() {
        double scale = viewportImageContainer.getScene() != null && viewportImageContainer.getScene().getWindow() != null
                ? viewportImageContainer.getScene().getWindow().getOutputScaleX()
                : Screen.getPrimary().getOutputScaleX();
        int w = (int) Math.round(viewportImageContainer.getWidth() * scale);
        int h = (int) Math.round(viewportImageContainer.getHeight() * scale);
        if (w > 0 && h > 0) {
            editingManager.setViewportSize(w, h);
        }
    }
}
