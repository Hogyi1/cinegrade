package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoMetadata;
import hu.elte.ik.thesis.cinegrade.domain.enums.WhiteBalanceType;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import hu.elte.ik.thesis.cinegrade.infra.utils.format.FormatUtils;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.EditPane;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.EditSlider;
import hu.elte.ik.thesis.cinegrade.javafx.ui.presenters.HistogramService;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Right panel of the development page: histogram, and below it the Edit tab (adjustment panes) or the Metadata tab.
 */
public class DevelopEditPanelController {

    private static final Logger logger = LogManager.getLogger(DevelopEditPanelController.class);
    private final EditingManager editingManager;
    private final ComboBox<WhiteBalanceType> whiteBalanceComboBox = new ComboBox<>();
    private boolean showingEditState;

    private EditSlider temperature;
    private EditSlider temperatureOffset;
    private EditSlider tint;
    private HBox whiteBalanceRow;

    @FXML
    private Label cameraExifLabel;
    @FXML
    private StackPane histogramHolder;
    @FXML
    private Canvas histogramCanvas;
    @FXML
    private ToggleButton editTabBtn;
    @FXML
    private ToggleButton metadataTabBtn;
    @FXML
    private Node editTab;
    @FXML
    private Node metadataTab;
    @FXML
    private VBox panelsContainer;
    @FXML
    private VBox metadataTable;

    @Getter
    private HistogramService histogramService;

    public DevelopEditPanelController(EditingManager editingManager) {
        this.editingManager = editingManager;
    }

    @FXML
    private void initialize() {
        histogramService = new HistogramService(histogramCanvas);
        editingManager.setHistogramService(histogramService);

        histogramCanvas.widthProperty().bind(histogramHolder.widthProperty());
        histogramCanvas.heightProperty().bind(histogramHolder.heightProperty());

        setupTab(editTabBtn, editTab);
        setupTab(metadataTabBtn, metadataTab);
        oneAlwaysSelected(editTabBtn, metadataTabBtn);

        buildPanes();

        editingManager.getViewModel().currentPhotoProperty().addListener((obs, oldPhoto, newPhoto) -> showPhoto(newPhoto));
        editingManager.getViewModel().currentEditStateProperty().addListener((obs, oldState, newState) -> showEditState(newState));
        showPhoto(editingManager.getViewModel().currentPhotoProperty().get());
        showEditState(editingManager.getViewModel().currentEditStateProperty().get());
    }

    private void buildPanes() {
        temperature = new EditSlider(GradeParam.TEMPERATURE, editingManager);
        temperatureOffset = new EditSlider(GradeParam.TEMPERATURE_OFFSET, editingManager);
        tint = new EditSlider(GradeParam.TINT, editingManager);

        temperature.setOnChanged(this::showCurrentWhiteBalance);
        temperatureOffset.setOnChanged(this::showCurrentWhiteBalance);
        tint.setOnChanged(this::showCurrentWhiteBalance);

        EditPane basicPane = new EditPane(GradeParam.Section.BASIC.getTitle());
        basicPane.setExpanded(true);
        basicPane.enabledProperty().addListener((obs, wasOn, on) -> editingManager.setSectionEnabled(GradeParam.Section.BASIC, on));
        basicPane.getBody().getChildren().addAll(
                header("White Balance"),
                whiteBalanceRow(),
                temperature,
                temperatureOffset,
                tint,
                header("Tone"),
                new EditSlider(GradeParam.EXPOSURE, editingManager),
                new EditSlider(GradeParam.CONTRAST, editingManager),
                new EditSlider(GradeParam.HIGHLIGHTS, editingManager),
                new EditSlider(GradeParam.SHADOWS, editingManager),
                new EditSlider(GradeParam.WHITES, editingManager),
                new EditSlider(GradeParam.BLACKS, editingManager),
                header("Color"),
                new EditSlider(GradeParam.VIBRANCE, editingManager),
                new EditSlider(GradeParam.SATURATION, editingManager));

        VBox hueSliders = new VBox(
                new EditSlider(GradeParam.HUE_RED, editingManager),
                new EditSlider(GradeParam.HUE_ORANGE, editingManager),
                new EditSlider(GradeParam.HUE_YELLOW, editingManager),
                new EditSlider(GradeParam.HUE_GREEN, editingManager),
                new EditSlider(GradeParam.HUE_AQUA, editingManager),
                new EditSlider(GradeParam.HUE_BLUE, editingManager),
                new EditSlider(GradeParam.HUE_PURPLE, editingManager),
                new EditSlider(GradeParam.HUE_MAGENTA, editingManager));
        VBox saturationSliders = new VBox(
                new EditSlider(GradeParam.SAT_RED, editingManager),
                new EditSlider(GradeParam.SAT_ORANGE, editingManager),
                new EditSlider(GradeParam.SAT_YELLOW, editingManager),
                new EditSlider(GradeParam.SAT_GREEN, editingManager),
                new EditSlider(GradeParam.SAT_AQUA, editingManager),
                new EditSlider(GradeParam.SAT_BLUE, editingManager),
                new EditSlider(GradeParam.SAT_PURPLE, editingManager),
                new EditSlider(GradeParam.SAT_MAGENTA, editingManager));
        VBox luminanceSliders = new VBox(
                new EditSlider(GradeParam.LUM_RED, editingManager),
                new EditSlider(GradeParam.LUM_ORANGE, editingManager),
                new EditSlider(GradeParam.LUM_YELLOW, editingManager),
                new EditSlider(GradeParam.LUM_GREEN, editingManager),
                new EditSlider(GradeParam.LUM_AQUA, editingManager),
                new EditSlider(GradeParam.LUM_BLUE, editingManager),
                new EditSlider(GradeParam.LUM_PURPLE, editingManager),
                new EditSlider(GradeParam.LUM_MAGENTA, editingManager));

        ToggleButton hueTab = createTabButton("Hue", hueSliders);
        ToggleButton saturationTab = createTabButton("Saturation", saturationSliders);
        ToggleButton luminanceTab = createTabButton("Luminance", luminanceSliders);
        oneAlwaysSelected(hueTab, saturationTab, luminanceTab);

        HBox mixerTabBar = new HBox(6, hueTab, saturationTab, luminanceTab);
        mixerTabBar.setAlignment(Pos.CENTER);
        mixerTabBar.getStyleClass().add("mixer-tab-bar");

        EditPane colorMixerPane = new EditPane(GradeParam.Section.COLOR_MIXER.getTitle());
        colorMixerPane.enabledProperty().addListener((obs, wasOn, on) -> editingManager.setSectionEnabled(GradeParam.Section.COLOR_MIXER, on));
        colorMixerPane.getBody().getChildren().addAll(mixerTabBar, hueSliders, saturationSliders, luminanceSliders);

        panelsContainer.getChildren().setAll(basicPane, colorMixerPane);
    }

    private static Label header(String title) {
        Label header = new Label(title.toUpperCase());
        header.getStyleClass().add("edit-pane-group");
        return header;
    }

    private static ToggleButton createTabButton(String title, Node sliders) {
        ToggleButton button = new ToggleButton(title);
        button.getStyleClass().add("curve-channel-btn");
        setupTab(button, sliders);
        return button;
    }

    private static void setupTab(ToggleButton button, Node content) {
        content.visibleProperty().bind(button.selectedProperty());
        content.managedProperty().bind(button.selectedProperty());
    }

    /**
     * The first button starts selected; clicking the selected one again does not clear it.
     */
    private static void oneAlwaysSelected(ToggleButton... buttons) {
        ToggleGroup group = new ToggleGroup();
        group.getToggles().addAll(buttons);
        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null && old != null) {
                old.setSelected(true);
            }
        });
        group.selectToggle(buttons[0]);
    }

    /**
     * White balance preset row, laid out like a slider row: name | preset box.
     */
    private HBox whiteBalanceRow() {
        Label name = new Label("WB");
        name.getStyleClass().add("edit-slider-name");

        whiteBalanceComboBox.getStyleClass().add("white-balance-box");
        whiteBalanceComboBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(whiteBalanceComboBox, Priority.ALWAYS);
        whiteBalanceComboBox.valueProperty().addListener((obs, old, type) -> {
            if (type == null || showingEditState) {
                return;
            }
            editingManager.setWhiteBalance(type);
            // the preset moved the temperature / tint
            EditState editState = editingManager.getViewModel().currentEditStateProperty().get();
            temperature.show(editState);
            temperatureOffset.show(editState);
            tint.show(editState);
        });

        whiteBalanceRow = new HBox(name, whiteBalanceComboBox);
        whiteBalanceRow.getStyleClass().add("edit-slider");
        return whiteBalanceRow;
    }

    /**
     * The sliders follow the edit by themselves, only the panel's own state is left here. No photo: panel disabled.
     */
    private void showEditState(EditState editState) {
        panelsContainer.setDisable(editState == null);
        showWhiteBalanceType(editState);
    }

    /**
     * RAWs get the Kelvin temperature slider and the white balance presets, other photos the -100..100 slider only.
     */
    private void showPhoto(PhotoEntry photo) {
        boolean raw = photo == null || photo.isRaw();
        setShown(temperature, raw);
        setShown(temperatureOffset, !raw);
        setShown(whiteBalanceRow, raw);

        showMetadata(photo, null);
        if (photo != null) {
            editingManager.loadMetadata(photo)
                    .exceptionally(ex -> {
                        logger.error("Failed to load metadata of photo: {}", photo.getFile(), ex);
                        hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler.getInstance().handle(ex);
                        return Optional.empty();
                    })
                    .thenAcceptAsync(metadata -> {
                        if (photo == editingManager.getViewModel().currentPhotoProperty().get()) {
                            showMetadata(photo, metadata.orElse(null));
                        }
                    }, Platform::runLater);
        }

        if (photo != null && photo.isRaw()) {
            histogramService.clear();
            panelsContainer.setDisable(true);
        }
    }

    private void showCurrentWhiteBalance() {
        showWhiteBalanceType(editingManager.getViewModel().currentEditStateProperty().get());
    }

    /**
     * The edit's current preset, without firing.
     */
    private void showWhiteBalanceType(EditState editState) {
        List<WhiteBalanceType> types = Arrays.stream(WhiteBalanceType.values()).toList();
        showingEditState = true;
        try {
            whiteBalanceComboBox.getItems().setAll(types);
            whiteBalanceComboBox.setValue(editState == null ? WhiteBalanceType.AS_SHOT : editState.getWhiteBalance());
        } finally {
            showingEditState = false;
        }
    }

    /**
     * Fills the Metadata tab and the one-line summary above the histogram. No metadata (not loaded yet, or the photo
     * has none): only the file's own rows have a value.
     */
    private void showMetadata(PhotoEntry photo, PhotoMetadata metadata) {
        metadataTable.getChildren().clear();
        cameraExifLabel.setText(metadata == null ? "" : exifSummary(metadata));
        if (photo == null) {
            return;
        }

        boolean known = metadata != null;
        metadataTable.getChildren().addAll(
                metadataRow("File", photo.getFileName()),
                metadataRow("Type", photo.isRaw() ? "RAW" : photo.getMimeType()),
                metadataRow("Size", FormatUtils.formatBytes(photo.getFileSize())),
                metadataRow("Make", known ? metadata.getMake() : null),
                metadataRow("Model", known ? metadata.getModel() : null),
                metadataRow("Lens", known ? metadata.getLens() : null),
                metadataRow("ISO", known && metadata.getIso() > 0 ? String.valueOf(metadata.getIso()) : null),
                metadataRow("Aperture", known ? aperture(metadata) : null),
                metadataRow("Shutter", known ? metadata.getShutterSpeed() : null),
                metadataRow("Focal length", known ? metadata.getFocalLength() : null),
                metadataRow("Captured", known ? metadata.getCaptureDate() : null),
                metadataRow("Color style", known ? metadata.getColorStyle() : null));
    }

    private static HBox metadataRow(String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("metadata-name");
        Label valueLabel = new Label(value == null || value.isBlank() ? "—" : value);
        valueLabel.getStyleClass().add("metadata-value");
        valueLabel.setWrapText(true);

        HBox row = new HBox(nameLabel, valueLabel);
        row.getStyleClass().add("metadata-row");
        return row;
    }

    private static String exifSummary(PhotoMetadata metadata) {
        return Stream.of(iso(metadata), metadata.getFocalLength(), aperture(metadata), metadata.getShutterSpeed())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" • "));
    }

    private static String iso(PhotoMetadata metadata) {
        return metadata.getIso() > 0 ? "ISO " + metadata.getIso() : null;
    }

    private static String aperture(PhotoMetadata metadata) {
        return metadata.getAperture() > 0 ? String.format(java.util.Locale.ROOT, "ƒ/%.1f", metadata.getAperture()) : null;
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
