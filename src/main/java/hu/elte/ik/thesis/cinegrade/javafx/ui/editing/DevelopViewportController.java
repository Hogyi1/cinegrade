package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.listener.HistoryListener;
import hu.elte.ik.thesis.cinegrade.infra.utils.format.FormatUtils;
import hu.elte.ik.thesis.cinegrade.javafx.ui.animations.TransitionFactory;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.InputListener;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Center viewport of the development page: shows the rendered photo.
 */
public class DevelopViewportController implements HistoryListener {

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
    private Label missingBadge;
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
    @FXML
    private ToggleButton eyedropperToggle;
    @FXML
    private HBox pickHintBar;
    @FXML
    private Label pickHintLabel;
    @FXML
    private HBox historyNoticeBar;
    @FXML
    private Label historyNoticeLabel;

    private SequentialTransition noticeTransition;
    private ViewportNavigator viewportNavigator;
    private InputListener inputListener;

    // On the scene while picking, so Esc cancels wherever the focus is
    private final EventHandler<KeyEvent> pickEscape = e -> {
        if (e.getCode() == KeyCode.ESCAPE) {
            eyedropperToggle.setSelected(false);
            e.consume();
        }
    };

    public DevelopViewportController(EditingManager editingManager) {
        this.editingManager = editingManager;
    }

    @FXML
    private void initialize() {
        viewportImageView.imageProperty().bind(editingManager.getViewModel().viewportImageProperty());
        thumbnailImageView.imageProperty().bind(editingManager.getViewModel().placeholderImageProperty());
        // A missing original can't be decoded: keep its stored thumbnail up instead of the previous photo's frame
        BooleanExpression missing = BooleanExpression.booleanExpression(editingManager.getViewModel().currentPhotoProperty()
                .flatMap(PhotoEntry::missingPhotoProperty).orElse(false));
        BooleanBinding showThumbnail = editingManager.getViewModel().loadingProperty().or(missing);
        thumbnailImageView.visibleProperty().bind(showThumbnail);
        missingBadge.visibleProperty().bind(missing);
        missingBadge.managedProperty().bind(missing);
        if (loadProgressOverlay != null) {
            loadProgressOverlay.visibleProperty().bind(editingManager.getViewModel().loadingProperty());
            loadProgressOverlay.managedProperty().bind(editingManager.getViewModel().loadingProperty());
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
        viewportImageView.visibleProperty().bind(showThumbnail.not());

        // the toggle and Ctrl+Shift+B both flip the manager's flag, which resets itself on a photo change
        splitViewToggle.selectedProperty().bindBidirectional(editingManager.getViewModel().showOriginalProperty());
        editingManager.getViewModel().currentPhotoProperty().addListener((obs, old, photo) -> showPhotoInfo(photo));
        showPhotoInfo(editingManager.getViewModel().currentPhotoProperty().get());

        setupNavigation();
        setupInput();
        setupEyedropper();
        setupHistoryNotice();
    }

    /**
     * What just happened, at the bottom of the viewport: 0.25 s fade in, 1 s shown, 0.25 s fade out. Only one at a
     * time: a new one stops the running one and starts over.
     */
    private void setupHistoryNotice() {
        noticeTransition = TransitionFactory
                .createFadePauseTransition(historyNoticeBar, Duration.millis(250), Duration.seconds(1), null);
        editingManager.getHistoryManager().registerListener(this);
    }

    private void setupEyedropper() {
        eyedropperToggle.disableProperty().bind(editingManager.getViewModel().loadingProperty()
                .or(editingManager.getViewModel().currentPhotoProperty().isNull()));
        eyedropperToggle.selectedProperty().addListener((obs, was, picking) -> {
            if (picking) {
                startPicking();
            } else {
                stopPicking();
            }
        });
        // a new photo ends the picking
        editingManager.getViewModel().loadingProperty().addListener((obs, was, loading) -> {
            if (loading) {
                eyedropperToggle.setSelected(false);
            }
        });
    }

    private void startPicking() {
        pickHintLabel.setText("Click something neutral grey  ·  Esc to cancel");
        pickHintBar.setVisible(true);
        viewportRoot.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, pickEscape);
        viewportNavigator.startPicking(uv -> editingManager.pickWhiteBalance(uv).thenAccept(picked -> {
            if (picked) {
                eyedropperToggle.setSelected(false);
            } else {
                pickHintLabel.setText("Too dark or too bright, pick another spot  ·  Esc to cancel");
            }
        }));
    }

    private void stopPicking() {
        pickHintBar.setVisible(false);
        viewportRoot.getScene().removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, pickEscape);
        viewportNavigator.stopPicking();
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
        editingManager.getViewModel().loadingProperty().addListener((obs, was, loading) -> {
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

    @Override
    public void onHistory(String history, boolean isUndo) {
        noticeTransition.stop();
        historyNoticeLabel.setText(history);
        noticeTransition.playFromStart();
    }
}
