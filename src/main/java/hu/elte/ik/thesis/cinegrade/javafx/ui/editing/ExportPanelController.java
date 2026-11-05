package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.export.ExportManager;
import hu.elte.ik.thesis.cinegrade.app.managers.export.ExportStage;
import hu.elte.ik.thesis.cinegrade.app.scope.CatalogScope;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.PhotoExtension;
import hu.elte.ik.thesis.cinegrade.domain.navigation.SceneManager;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.LastPathService;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.BooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

// [AI-GENERATED]
public class ExportPanelController implements PanelController {

    private final ExportManager exportManager;
    private final ExportStage stage;
    private final LastPathService lastPaths = new LastPathService();
    private NavigationController navigationController;

    @FXML
    private ToggleGroup exportTabToggleGroup, formatGroup, lutSizeGroup;
    @FXML
    private ToggleButton tabLutBtn, tabImagesBtn;
    @FXML
    private HBox imagesTabContainer, qualityRow;
    @FXML
    private VBox lutTabContainer;
    @FXML
    private RadioButton scopeCurrentRadio, scopeSelectedRadio, scopeVisibleRadio, scopeLastImportRadio, scopeAllRadio;
    @FXML
    private Label scopeCurrentCount, scopeSelectedCount, scopeVisibleCount, scopeLastImportCount, scopeAllCount;
    @FXML
    private ToggleButton formatJpegBtn, formatPngBtn, formatTiffBtn;
    @FXML
    private Label bitDepthLabel, qualityValueLabel, namingExampleLabel, lutSourceLabel, exportSummaryLabel;
    @FXML
    private Slider qualitySlider;
    @FXML
    private TextField destinationPathField, prefixField, fileNameField, postfixField, lutDestinationField;
    @FXML
    private Button browseDestinationBtn, browseLutBtn, startExportBtn;
    @FXML
    private ToggleButton lutSize17Btn, lutSize33Btn, lutSize65Btn;
    @FXML
    private CheckBox lutLookOnlyCheck;

    public ExportPanelController(CatalogScope catalogScope) {
        this.exportManager = catalogScope.exportManager();
        this.stage = exportManager.getExportStage();
    }

    @FXML
    private void initialize() {
        keepOneSelected(exportTabToggleGroup);
        keepOneSelected(formatGroup);
        keepOneSelected(lutSizeGroup);

        imagesTabContainer.visibleProperty().bind(tabImagesBtn.selectedProperty());
        imagesTabContainer.managedProperty().bind(tabImagesBtn.selectedProperty());
        lutTabContainer.visibleProperty().bind(tabLutBtn.selectedProperty());
        lutTabContainer.managedProperty().bind(tabLutBtn.selectedProperty());

        bindScope();
        bindFileSettings();
        bindLut();
        bindAction();
    }

    private void bindScope() {
        scopeCurrentRadio.setSelected(stage.getScope().get() == ExportStage.Scope.CURRENT);
        scopeSelectedRadio.setSelected(stage.getScope().get() == ExportStage.Scope.SELECTED);
        scopeVisibleRadio.setSelected(stage.getScope().get() == ExportStage.Scope.VISIBLE);
        scopeLastImportRadio.setSelected(stage.getScope().get() == ExportStage.Scope.LAST_IMPORT);
        scopeAllRadio.setSelected(stage.getScope().get() == ExportStage.Scope.ALL);

        scopeCurrentRadio.setOnAction(e -> stage.getScope().set(ExportStage.Scope.CURRENT));
        scopeSelectedRadio.setOnAction(e -> stage.getScope().set(ExportStage.Scope.SELECTED));
        scopeVisibleRadio.setOnAction(e -> stage.getScope().set(ExportStage.Scope.VISIBLE));
        scopeLastImportRadio.setOnAction(e -> stage.getScope().set(ExportStage.Scope.LAST_IMPORT));
        scopeAllRadio.setOnAction(e -> stage.getScope().set(ExportStage.Scope.ALL));

        scopeCurrentCount.textProperty().bind(Bindings.createStringBinding(
                () -> stage.getCurrentPhoto().get() == null ? "0" : "1", stage.getCurrentPhoto()));
        scopeSelectedCount.textProperty().bind(stage.getSelectedPhotosCount().asString());
        scopeVisibleCount.textProperty().bind(stage.getVisiblePhotosCount().asString());
        scopeLastImportCount.textProperty().bind(stage.getLastImportCount().asString());
        scopeAllCount.textProperty().bind(stage.getAllPhotosCount().asString());
    }

    private void bindFileSettings() {
        formatJpegBtn.setSelected(stage.getFormat().get() == PhotoExtension.JPEG);
        formatPngBtn.setSelected(stage.getFormat().get() == PhotoExtension.PNG);
        formatTiffBtn.setSelected(stage.getFormat().get() == PhotoExtension.TIFF_EXT);

        formatJpegBtn.setOnAction(e -> stage.getFormat().set(PhotoExtension.JPEG));
        formatPngBtn.setOnAction(e -> stage.getFormat().set(PhotoExtension.PNG));
        formatTiffBtn.setOnAction(e -> stage.getFormat().set(PhotoExtension.TIFF_EXT));

        qualityRow.visibleProperty().bind(formatJpegBtn.selectedProperty());
        qualityRow.managedProperty().bind(formatJpegBtn.selectedProperty());
        bitDepthLabel.textProperty().bind(Bindings.when(formatJpegBtn.selectedProperty())
                .then("8-bit")
                .otherwise("16-bit, keeps the most tonal detail"));

        qualitySlider.valueProperty().bindBidirectional(stage.getQuality());
        qualityValueLabel.textProperty().bind(stage.getQuality().asString());

        destinationPathField.textProperty().bind(Bindings.createStringBinding(
                () -> stage.getDestination().get() == null ? "" : stage.getDestination().get().toString(), stage.getDestination()));
        browseDestinationBtn.setOnAction(e -> SceneManager.chooseFolder(
                "Choose an export folder", lastPaths.getExportLastFolder().map(Path::toFile).orElse(null), folder -> {
                    stage.getDestination().set(folder.toPath());
                    lastPaths.setExportLastFolder(folder.toPath());
                }));

        prefixField.textProperty().bindBidirectional(stage.getPrefix());
        fileNameField.textProperty().bindBidirectional(stage.getFileName());
        postfixField.textProperty().bindBidirectional(stage.getPostfix());

        namingExampleLabel.textProperty().bind(Bindings.createStringBinding(this::exampleName,
                stage.getPrefix(), stage.getFileName(), stage.getPostfix(), stage.getFormat(), stage.getCurrentPhoto()));
    }

    private void bindLut() {
        lutSize17Btn.setSelected(stage.getLutSize().get() == 17);
        lutSize33Btn.setSelected(stage.getLutSize().get() == 33);
        lutSize65Btn.setSelected(stage.getLutSize().get() == 65);

        lutSize17Btn.setOnAction(e -> stage.getLutSize().set(17));
        lutSize33Btn.setOnAction(e -> stage.getLutSize().set(33));
        lutSize65Btn.setOnAction(e -> stage.getLutSize().set(65));

        lutLookOnlyCheck.selectedProperty().bindBidirectional(stage.getLutLookOnly());
        lutDestinationField.textProperty().bind(Bindings.createStringBinding(
                () -> stage.getLutFile().get() == null ? "" : stage.getLutFile().get().toString(), stage.getLutFile()));
        browseLutBtn.setOnAction(e -> SceneManager.chooseSaveFile(
                "Save the LUT as", "CineGrade_look.cube", new FileChooser.ExtensionFilter("Cube LUT", "*.cube"),
                file -> {
                    stage.getLutFile().set(file.toPath());
                    lastPaths.setExportLastLutFolder(file.getParentFile().toPath());
                }, lastPaths.getExportLastLutFolder().orElse(null)));

        lutSourceLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            PhotoEntry current = stage.getCurrentPhoto().get();
            return current == null ? "No photo selected" : "Current edit of " + current.getFileName();
        }, stage.getCurrentPhoto()));
    }

    private void bindAction() {
        IntegerBinding photoCount = Bindings.createIntegerBinding(() -> switch (stage.getScope().get()) {
                    case CURRENT -> stage.getCurrentPhoto().get() == null ? 0 : 1;
                    case SELECTED -> stage.getSelectedPhotosCount().get();
                    case VISIBLE -> stage.getVisiblePhotosCount().get();
                    case LAST_IMPORT -> stage.getLastImportCount().get();
                    case ALL -> stage.getAllPhotosCount().get();
                }, stage.getScope(), stage.getCurrentPhoto(), stage.getSelectedPhotosCount(),
                stage.getVisiblePhotosCount(), stage.getLastImportCount(), stage.getAllPhotosCount());

        BooleanProperty lutTab = tabLutBtn.selectedProperty();

        startExportBtn.textProperty().bind(Bindings.createStringBinding(() -> {
            if (lutTab.get()) {
                return "Export LUT";
            }
            int count = photoCount.get();
            return count == 0 ? "Export" : count == 1 ? "Export 1 photo" : "Export " + count + " photos";
        }, lutTab, photoCount));

        exportSummaryLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                    if (lutTab.get()) {
                        if (stage.getCurrentPhoto().get() == null) return "Select a photo to export its look";
                        if (stage.getLutFile().get() == null) return "Choose where to save the LUT";
                        return "Saves a size " + stage.getLutSize().get() + " LUT to " + stage.getLutFile().get();
                    }
                    if (photoCount.get() == 0) return "No photos in this choice";
                    if (stage.getDestination().get() == null) return "Choose a folder to save to";
                    return "Saves as " + stage.getFormat().get().getExtension() + " to " + stage.getDestination().get();
                }, lutTab, photoCount, stage.getCurrentPhoto(), stage.getLutFile(), stage.getLutSize(),
                stage.getDestination(), stage.getFormat()));

        startExportBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> lutTab.get()
                        ? stage.getCurrentPhoto().get() == null || stage.getLutFile().get() == null
                        : photoCount.get() == 0 || stage.getDestination().get() == null,
                lutTab, photoCount, stage.getCurrentPhoto(), stage.getLutFile(), stage.getDestination()));

        startExportBtn.setOnAction(e -> {
            if (lutTab.get()) {
                exportManager.exportLut();
            } else {
                exportManager.exportPhotos();
            }
            navigationController.switchToPreviousPanel();
        });
    }

    private String exampleName() {
        PhotoEntry current = stage.getCurrentPhoto().get();
        String typed = stage.getFileName().get();
        String name = typed != null && !typed.isBlank() ? typed
                : current != null ? current.getFileNameWithoutExtension() : "photo";
        return "For example: " + withAffix(stage.getPrefix().get(), "", "_") + name
                + withAffix(stage.getPostfix().get(), "_", "") + "." + stage.getFormat().get().getExtension().toLowerCase();
    }

    private String withAffix(String text, String before, String after) {
        return text == null || text.isBlank() ? "" : before + text + after;
    }

    // A second click on the selected segment must not leave the switch with nothing selected
    private void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                group.selectToggle(old);
            }
        });
    }

    private void reset() {
        stage.getScope().set(ExportStage.Scope.SELECTED);
        stage.getFormat().set(PhotoExtension.JPEG);
        stage.getQuality().set(90);
        stage.getDestination().set(lastPaths.getExportLastFolder().orElse(null));
        stage.getPrefix().set(null);
        stage.getFileName().set(null);
        stage.getPostfix().set(null);
        stage.getLutSize().set(33);
        stage.getLutLookOnly().set(true);
        stage.getLutFile().set(null);

        // These follow the stage only through their click handlers, so they are set by hand
        tabImagesBtn.setSelected(true);
        scopeSelectedRadio.setSelected(true);
        formatJpegBtn.setSelected(true);
        lutSize33Btn.setSelected(true);
    }

    @Override
    public void setNavigationController(NavigationController navigationController) {
        this.navigationController = navigationController;
    }

    @Override
    public boolean canExit() {
        return true;
    }

    @Override
    public void onExit() {
        reset();
    }

    @Override
    public void onEnter() {
        reset();
    }
}
