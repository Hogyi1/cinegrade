// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.javafx.ui.catalog;

import hu.elte.ik.thesis.cinegrade.domain.enums.ViewType;
import hu.elte.ik.thesis.cinegrade.domain.navigation.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;

/**
 * The Socials scene: the web platform in a WebView, with a back button to the projects.
 */
public class SocialsController {

    // Angular dev server for now; the deployed URL comes with the backend
    private static final String SOCIALS_URL = "http://localhost:4200";

    private final SceneManager sceneManager;

    @FXML
    private WebView webView;
    @FXML
    private Button backToProjectsBtn;

    public SocialsController(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @FXML
    private void initialize() {
        webView.getEngine().load(SOCIALS_URL);

        backToProjectsBtn.setOnAction(e -> {
            // stop the page (scripts, requests) before the view is dropped
            webView.getEngine().load(null);
            sceneManager.switchView(ViewType.PROJECT_MENU);
        });
    }
}
