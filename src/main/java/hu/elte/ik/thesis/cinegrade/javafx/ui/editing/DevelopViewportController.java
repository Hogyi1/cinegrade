package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
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
    private final ViewportRenderer renderer;

    // TODO remove: engine test files
    private static final File TEST_RAW = new File("C:\\Photos\\Us\\_1033581.RW2");
    private static final File TEST_JPEG = new File("C:\\Photos\\Us\\_1033571.JPG");
    private boolean testShowRawNext = true;

    // Resizing the FBO on every pixel of a window drag is slow: wait until the size settles
    private final PauseTransition resizeDebounce = new PauseTransition(Duration.millis(120));

    @FXML
    private StackPane viewportRoot;
    @FXML
    private StackPane viewportImageContainer;
    @FXML
    private ImageView viewportImageView;
    @FXML
    private Label photoNameLabel;
    @FXML
    private Label photoMetaLabel;
    @FXML
    private Button testLoadBtn;
    @FXML
    private ToggleButton splitViewToggle;
    @FXML
    private Button zoomFitBtn;
    @FXML
    private Button zoom100Btn;
    @FXML
    private Slider zoomSlider;

    public DevelopViewportController(EditingManager editingManager, ViewportRenderer renderer) {
        this.editingManager = editingManager;
        this.renderer = renderer;
    }

    @FXML
    private void initialize() {
        viewportImageView.imageProperty().bind(renderer.frameProperty());

        // The frame is rendered in device pixels; the ImageView scales it back to layout size
        viewportImageView.setPreserveRatio(true);
        viewportImageView.fitWidthProperty().bind(viewportImageContainer.widthProperty());
        viewportImageView.fitHeightProperty().bind(viewportImageContainer.heightProperty());
        // Let the container shrink below the current frame size
        viewportImageContainer.setMinSize(0, 0);

        resizeDebounce.setOnFinished(e -> pushAreaSize());
        viewportImageContainer.widthProperty().addListener((obs, o, n) -> resizeDebounce.playFromStart());
        viewportImageContainer.heightProperty().addListener((obs, o, n) -> resizeDebounce.playFromStart());

        testLoadBtn.setOnAction(e -> loadTestImage());
    }

    private void pushAreaSize() {
        double scale = viewportImageContainer.getScene() != null && viewportImageContainer.getScene().getWindow() != null
                ? viewportImageContainer.getScene().getWindow().getOutputScaleX()
                : Screen.getPrimary().getOutputScaleX();
        int w = (int) Math.round(viewportImageContainer.getWidth() * scale);
        int h = (int) Math.round(viewportImageContainer.getHeight() * scale);
        if (w > 0 && h > 0) {
            renderer.resize(w, h);
        }
    }

    // TODO remove: engine test. Decodes off the FX thread, then hands the pixels to the renderer
    private void loadTestImage() {
        File file = testShowRawNext ? TEST_RAW : TEST_JPEG;
        testShowRawNext = !testShowRawNext;
        testLoadBtn.setDisable(true);
        photoNameLabel.setText(file.getName() + " (decoding...)");
        long start = System.nanoTime();

        CompletableFuture
                .supplyAsync(() -> file == TEST_RAW ? decodeRaw(file) : decodeImage(file))
                .thenCompose(renderer::show)
                .whenComplete((v, ex) -> Platform.runLater(() -> {
                    testLoadBtn.setDisable(false);
                    testLoadBtn.setText(testShowRawNext ? "Test: load RAW" : "Test: load JPEG");
                    if (ex != null) {
                        photoNameLabel.setText(file.getName() + " (failed)");
                        ErrorHandler.getInstance().handle(ex);
                        return;
                    }
                    photoNameLabel.setText(file.getName());
                    photoMetaLabel.setText("%s, %d ms".formatted(file == TEST_RAW ? "RAW 16-bit" : "JPEG 8-bit",
                            (System.nanoTime() - start) / 1_000_000));
                    pushAreaSize();
                }));
    }

    private static LibrawService.DecodedImage decodeRaw(File file) {
        // One LibrawService (one native handle) per decode: never shared between threads
        try (LibrawService librawService = new LibrawService()) {
            return librawService.decodeRaw(file)
                    .orElseThrow(() -> new IllegalStateException("LibRaw could not decode " + file));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * JPEG/PNG through ImageIO into a native RGBA8 buffer. ImageIO ignores the EXIF orientation.
     */
    private static LibrawService.DecodedImage decodeImage(File file) {
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + file, ex);
        }
        if (image == null) {
            throw new IllegalStateException("No ImageIO reader for " + file);
        }

        int w = image.getWidth();
        int h = image.getHeight();
        ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int argb : row) {
                pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
            }
        }
        pixels.flip();
        return new LibrawService.DecodedImage(pixels, w, h, 8);
    }
}
