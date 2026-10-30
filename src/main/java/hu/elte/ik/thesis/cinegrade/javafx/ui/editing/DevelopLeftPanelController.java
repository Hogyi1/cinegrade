package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.SearchBar;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Left panel of the development page: presets, history and tags.
 */
public class DevelopLeftPanelController {

    private static final Logger logger = LogManager.getLogger(DevelopLeftPanelController.class);
    private final EditingManager editingManager;
    private final PhotoManager photoManager;

    @FXML
    private VBox leftPanelRoot;
    @FXML
    private ToggleButton tabPresetsBtn;
    @FXML
    private ToggleGroup leftTabGroup;
    @FXML
    private ToggleButton tabHistoryBtn;
    @FXML
    private ToggleButton tabTagsBtn;
    @FXML
    private VBox presetsViewContainer;
    @FXML
    private SearchBar presetSearchField;
    @FXML
    private Button createPresetBtn;
    @FXML
    private VBox presetItemsContainer;
    @FXML
    private VBox historyViewContainer;
    @FXML
    private Button takeSnapshotBtn;
    @FXML
    private VBox historyStepsContainer;
    @FXML
    private VBox tagsViewContainer;
    @FXML
    private Button createNewTagBtn;
    @FXML
    private VBox tagListContainer;

    public DevelopLeftPanelController(EditingManager editingManager, PhotoManager photoManager) {
        this.editingManager = editingManager;
        this.photoManager = photoManager;
    }

    @FXML
    private void initialize() {
    }
}
