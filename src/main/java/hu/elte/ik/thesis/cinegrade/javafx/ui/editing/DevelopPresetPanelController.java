package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetManager;
import hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetStage;
import hu.elte.ik.thesis.cinegrade.app.managers.user.UserManager;
import hu.elte.ik.thesis.cinegrade.domain.navigation.SceneManager;
import hu.elte.ik.thesis.cinegrade.domain.objects.Preset;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.ConfirmDialog;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.PresetDialog;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.SearchBar;
import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static hu.elte.ik.thesis.cinegrade.engine.GradeParam.*;

/**
 * Left panel of the development page: one collapsible pane per category, the presets as cards inside.
 * Clicking a card applies it, right click opens its menu.
 */
public class DevelopPresetPanelController {

    private static final Logger logger = LogManager.getLogger(DevelopPresetPanelController.class);
    private static final FileChooser.ExtensionFilter PRESET_FILES = new FileChooser.ExtensionFilter("CineGrade preset", "*.cgedit");
    private final PresetStage presetStage;
    private final PresetManager presetManager;
    private final EditingManager editingManager;
    private final UserManager userManager;
    // Collapsed category panes, kept across rebuilds
    private final Set<String> collapsed = new HashSet<>();
    // The preset the context menu was opened on
    private Preset menuPreset;
    // The category the category menu was opened on
    private String menuCategory;

    @FXML
    private VBox leftPanelRoot;
    @FXML
    private VBox presetsViewContainer;
    @FXML
    private SearchBar presetSearchField;
    @FXML
    private Button importPresetBtn;
    @FXML
    private VBox presetGroupsContainer;
    @FXML
    private ContextMenu presetContextMenu;
    @FXML
    private ContextMenu categoryContextMenu;
    @FXML
    private MenuItem deleteCategoryItem;
    @FXML
    private MenuItem renamePresetItem;
    @FXML
    private MenuItem movePresetItem;
    @FXML
    private SeparatorMenuItem editSeparator;
    @FXML
    private MenuItem exportPresetItem;
    @FXML
    private MenuItem sharePresetItem;
    @FXML
    private SeparatorMenuItem deleteSeparator;
    @FXML
    private MenuItem deletePresetItem;
    @FXML
    private Button savePresetBtn;
    @FXML
    private Button clearSettingsBtn;

    public DevelopPresetPanelController(PresetManager presetManager, EditingManager editingManager, UserManager userManager) {
        this.presetManager = presetManager;
        this.presetStage = presetManager.getPresetStage();
        this.editingManager = editingManager;
        this.userManager = userManager;
    }

    @FXML
    private void initialize() {
        presetStage.getNameProperty().bindBidirectional(presetSearchField.textProperty());

        presetStage.getFilteredEntries().addListener((ListChangeListener<Preset>) change -> rebuild());
        presetStage.getCategories().addListener((ListChangeListener<String>) change -> rebuild());
        rebuild();

        importPresetBtn.setOnAction(e -> SceneManager.chooseFile("Import preset", null, PRESET_FILES,
                file -> presetManager.importFile(file.toPath())));
        savePresetBtn.disableProperty().bind(editingManager.getViewModel().currentEditStateProperty().isNull());
        clearSettingsBtn.disableProperty().bind(editingManager.getViewModel().currentEditStateProperty().isNull());
        savePresetBtn.setOnAction(e -> savePreset());
        clearSettingsBtn.setOnAction(e -> editingManager.resetSettings());

        deleteCategoryItem.setOnAction(e -> presetManager.deleteCategory(menuCategory));
        renamePresetItem.setOnAction(e -> {
            Preset preset = menuPreset;
            TextInputDialog dialog = new TextInputDialog(preset.getName());
            dialog.setContentText("Name");
            show(dialog, "Rename preset").ifPresent(name -> presetManager.rename(preset, name));
        });
        movePresetItem.setOnAction(e -> {
            Preset preset = menuPreset;
            ChoiceDialog<String> dialog = new ChoiceDialog<>(preset.getCategory(), presetStage.userCategories());
            dialog.setContentText("Category");
            show(dialog, "Move to category").ifPresent(category -> presetManager.moveTo(preset, category));
        });
        exportPresetItem.setOnAction(e -> {
            Preset preset = menuPreset;
            SceneManager.chooseSaveFile("Export preset", fileName(preset) + ".cgedit", PRESET_FILES,
                    file -> presetManager.exportFile(preset, file.toPath()), null);
        });
        deletePresetItem.setOnAction(e -> {
            Preset preset = menuPreset;
            if (ConfirmDialog.show(window(), "Delete preset", "\"" + preset.getName() + "\" will be removed. This can't be undone.", "Delete", true)) {
                presetManager.remove(preset);
            }
        });
        clearPopupRoot(presetContextMenu);
        clearPopupRoot(categoryContextMenu);
    }

    // [AI-GENERATED METHOD]
    /**
     * All panes again from the categories and the (searched) presets; a few dozen nodes, so no diffing.
     * While searching, a category without a match is left out.
     */
    private void rebuild() {
        List<Preset> presets = new ArrayList<>(presetStage.getFilteredEntries());
        presets.sort(Comparator.comparing(Preset::getName, String.CASE_INSENSITIVE_ORDER));
        boolean searching = !presetStage.getNameProperty().get().isBlank();

        List<TitledPane> panes = new ArrayList<>();
        for (String category : presetStage.getCategories()) {
            VBox cards = new VBox(6.0);
            for (Preset preset : presets) {
                if (preset.getCategory().equalsIgnoreCase(category)) {
                    cards.getChildren().add(card(preset));
                }
            }
            if (searching && cards.getChildren().isEmpty()) {
                continue;
            }
            TitledPane pane = new TitledPane(category, cards);
            pane.getStyleClass().addAll("edit-pane", "preset-group");
            if (cards.getChildren().isEmpty()) {
                pane.getStyleClass().add("empty");
            }
            // only an empty category can be deleted, never User presets (and not while searching, it hides presets)
            if (!searching && cards.getChildren().isEmpty() && !category.equalsIgnoreCase(PresetManager.DEFAULT_CATEGORY)) {
                pane.setOnContextMenuRequested(e -> {
                    menuCategory = category;
                    categoryContextMenu.show(pane, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
            }
            pane.setAnimated(false);
            pane.setExpanded(!collapsed.contains(category));
            pane.expandedProperty().addListener((obs, was, open) -> {
                if (open) collapsed.remove(category);
                else collapsed.add(category);
            });
            panes.add(pane);
        }
        presetGroupsContainer.getChildren().setAll(panes);
    }

    // [AI-GENERATED METHOD]
    /**
     * A preset as a box with its name: click applies it, right click opens the menu, hovering shows the description.
     */
    private Label card(Preset preset) {
        Label card = new Label(preset.getName());
        card.getStyleClass().add("preset-item");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAlignment(Pos.CENTER);
        if (preset.getDescription() != null && !preset.getDescription().isBlank()) {
            Tooltip tooltip = new Tooltip(preset.getDescription());
            tooltip.setShowDelay(Duration.millis(400));
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(260);
            clearPopupRoot(tooltip);
            card.setTooltip(tooltip);
        }
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                apply(preset);
            }
        });
        // built-in presets are read-only: no menu at all
        if (!preset.isBuiltIn()) {
            card.setOnContextMenuRequested(e -> {
                menuPreset = preset;
                presetContextMenu.show(card, e.getScreenX(), e.getScreenY());
                e.consume();
            });
        }
        return card;
    }

    /**
     * Ctrl+Shift+S and the Save button: the current edit as a new preset.
     */
    public void savePreset() {
        if (editingManager.getViewModel().currentEditStateProperty().get() == null) {
            return;
        }
        PresetDialog dialog = new PresetDialog();
        dialog.setAuthor(userManager.getUsername());
        dialog.setCategories(presetStage.userCategories());
        if (!dialog.showAndWait(window())) {
            return;
        }

        Set<GradeParam> included = EnumSet.noneOf(GradeParam.class);
        if (dialog.getExposureCheck().isSelected()) included.add(EXPOSURE);
        if (dialog.getContrastCheck().isSelected()) included.add(CONTRAST);
        if (dialog.getHighlightsCheck().isSelected()) included.add(HIGHLIGHTS);
        if (dialog.getShadowsCheck().isSelected()) included.add(SHADOWS);
        if (dialog.getWhitesCheck().isSelected()) included.add(WHITES);
        if (dialog.getBlacksCheck().isSelected()) included.add(BLACKS);
        if (dialog.getVibranceCheck().isSelected()) included.add(VIBRANCE);
        if (dialog.getSaturationCheck().isSelected()) included.add(SATURATION);
        if (dialog.getHueCheck().isSelected()) {
            included.addAll(List.of(HUE_RED, HUE_ORANGE, HUE_YELLOW, HUE_GREEN, HUE_AQUA, HUE_BLUE, HUE_PURPLE, HUE_MAGENTA));
        }
        if (dialog.getHslSaturationCheck().isSelected()) {
            included.addAll(List.of(SAT_RED, SAT_ORANGE, SAT_YELLOW, SAT_GREEN, SAT_AQUA, SAT_BLUE, SAT_PURPLE, SAT_MAGENTA));
        }
        if (dialog.getLuminanceCheck().isSelected()) {
            included.addAll(List.of(LUM_RED, LUM_ORANGE, LUM_YELLOW, LUM_GREEN, LUM_AQUA, LUM_BLUE, LUM_PURPLE, LUM_MAGENTA));
        }

        presetManager.createFromCurrent(editingManager.getViewModel().currentEditStateProperty().get(), dialog.getName(), dialog.getCategory(), dialog.getAuthor(), dialog.getDescription(),
                included, dialog.getWhiteBalanceCheck().isSelected());
    }

    private void apply(Preset preset) {
        if (editingManager.getViewModel().currentEditStateProperty().get() == null) {
            return;
        }
        editingManager.applyPreset(preset);
    }

    private <T> Optional<T> show(Dialog<T> dialog, String title) {
        dialog.initOwner(window());
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        dialog.getDialogPane().getStylesheets().addAll(presetGroupsContainer.getScene().getStylesheets());
        return dialog.showAndWait();
    }

    // [AI-GENERATED METHOD]
    /**
     * A popup's scene root gets the app's {@code .root} background, which shows as square corners around the
     * rounded menu / tooltip. Making that root transparent removes them (no clip needed).
     */
    private static void clearPopupRoot(PopupWindow popup) {
        popup.setOnShown(e -> popup.getScene().getRoot().setStyle("-fx-background-color: transparent;"));
    }

    private Window window() {
        return presetGroupsContainer.getScene().getWindow();
    }

    /**
     * Windows does not allow these in file names.
     */
    private static String fileName(Preset preset) {
        return preset.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
