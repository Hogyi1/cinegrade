package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.scope.CatalogScope;
import javafx.fxml.FXML;
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

    public DevelopmentPanelController(CatalogScope catalogScope) {
        this.catalogScope = catalogScope;
    }

    @FXML
    private void initialize() {
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

    }
}
