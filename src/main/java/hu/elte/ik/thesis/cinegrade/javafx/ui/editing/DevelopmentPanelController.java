package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.app.scope.CatalogScope;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.InputListener;
import javafx.fxml.FXML;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class DevelopmentPanelController implements PanelController {

    private final CatalogScope catalogScope;

    @FXML
    private BorderPane developmentRoot;
    @FXML
    private VBox developLeftPanel;
    @FXML
    private StackPane developViewport;
    @FXML
    private VBox developEditPanel;
    @FXML
    private VBox developFilmstrip;
    @FXML
    private DevelopLeftPanelController developLeftPanelController;
    @FXML
    private DevelopViewportController developViewportController;
    @FXML
    private DevelopEditPanelController developEditPanelController;
    @FXML
    private DevelopFilmstripController developFilmstripController;

    private InputListener inputListener;

    public DevelopmentPanelController(CatalogScope catalogScope) {
        this.catalogScope = catalogScope;
    }

    /**
     * Page-wide shortcuts. The filmstrip's own listener sits deeper, so while it has the focus its Ctrl+C
     * (comment) wins over copy settings here.
     */
    @FXML
    private void initialize() {
        EditingManager editingManager = catalogScope.editingManager();
        inputListener = new InputListener(developmentRoot, InputListener.focusedNotTyping(developmentRoot));
        inputListener.bind(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN), editingManager::copySettings);
        inputListener.bind(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN), editingManager::pasteSettings);
        inputListener.bind(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), editingManager::resetSettings);
        inputListener.bind(new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                () -> editingManager.showBefore(!editingManager.getViewModel().showOriginalProperty().get()));
        inputListener.bind(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), () -> editingManager.getHistoryManager().undo());
        inputListener.bind(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), () -> editingManager.getHistoryManager().redo());
        inputListener.bind(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> editingManager.getHistoryManager().redo());
    }

    @Override
    public void setNavigationController(NavigationController navigationController) {

    }

    @Override
    public boolean canExit() {
        return false;
    }

    @Override
    public void onExit() {

    }

    @Override
    public void onEnter() {
        // key events only reach the page while something inside it has the focus
        developmentRoot.requestFocus();
    }
}
