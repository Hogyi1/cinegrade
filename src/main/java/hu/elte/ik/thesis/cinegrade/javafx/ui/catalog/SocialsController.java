package hu.elte.ik.thesis.cinegrade.javafx.ui.catalog;

import hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetManager;
import hu.elte.ik.thesis.cinegrade.app.managers.social.CineGradeApi;
import hu.elte.ik.thesis.cinegrade.app.managers.social.CookieListener;
import hu.elte.ik.thesis.cinegrade.app.managers.social.PersistentCookieStore;
import hu.elte.ik.thesis.cinegrade.app.managers.social.ShareManager;
import hu.elte.ik.thesis.cinegrade.domain.enums.ViewType;
import hu.elte.ik.thesis.cinegrade.domain.navigation.SceneManager;
import hu.elte.ik.thesis.cinegrade.infra.config.AppConfig;
import hu.elte.ik.thesis.cinegrade.infra.social.DesktopBridge;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.HttpCookie;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * The Socials scene: the web platform in a WebView, with a back button to the projects.
 * Only our own site and the bundled offline page open inside it; every other link goes to the system browser.
 */
public class SocialsController {

    public static final String SOCIALS_URL = AppConfig.INSTANCE.getAppUrl();
    private static final Logger logger = LogManager.getLogger(SocialsController.class);

    private final SceneManager sceneManager;
    private final CineGradeApi api;
    private final HostServices hostServices;
    private final ShareManager shareManager;
    private static final String OFFLINE_PAGE = "/web/offline.html";
    private final String offlinePageUrl =
            Objects.requireNonNull(getClass().getResource(OFFLINE_PAGE)).toExternalForm();
    private final PersistentCookieStore cookieStore;
    // A field, so the same object can be removed again when the view is left
    private final CookieListener sessionListener = this::onSessionChanged;
    private final ExecutorService ioPool;
    private final PresetManager presetManager;
    private WebEngine webEngine;
    private DesktopBridge desktopBridge;

    @FXML
    private WebView webView;
    @FXML
    private Button backToProjectsBtn;

    public SocialsController(SceneManager sceneManager, CineGradeApi api, HostServices hostServices,
                             ExecutorService ioPool, PresetManager presetManager, ShareManager shareManager, PersistentCookieStore cookieStore) {
        this.sceneManager = sceneManager;
        this.api = api;
        this.hostServices = hostServices;
        this.ioPool = ioPool;
        this.presetManager = presetManager;
        this.shareManager = shareManager;
        this.cookieStore = cookieStore;
    }

    @FXML
    private void initialize() {
        webEngine = webView.getEngine();
        webEngine.setUserAgent(webEngine.getUserAgent() + " CineGradeDesktop/1.0");
        desktopBridge = new DesktopBridge(webView, api, ioPool, presetManager, shareManager);

        webEngine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            String location = webEngine.getLocation();
            logger.debug("Web page load state changed to: {} trying to reach {}", state, location);
            if (state == Worker.State.SUCCEEDED && isAllowed(location)) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("cinegradeDesktop", desktopBridge);
                logger.debug("Loaded socials page: {}", location);
            } else if (state == Worker.State.FAILED && !isOfflinePage(location)) {
                // The bridge is attached when the offline page reaches SUCCEEDED
                webEngine.load(offlinePageUrl);
                logger.debug("Loaded offline page: {}", offlinePageUrl);
            }
        });

        // A link to another site: stop it here and open it in the system browser instead
        webEngine.locationProperty().addListener((obs, old, location) -> {
            if (isExternal(location)) {
                logger.debug("Detected external link: {}", location);
                Platform.runLater(() -> webEngine.getLoadWorker().cancel());   // CANCELLED, not FAILED: no offline page
                hostServices.showDocument(location);
                logger.debug("Opening external document: {}", location);
            }
        });

        webEngine.setCreatePopupHandler(features -> {
            WebEngine popup = new WebEngine();
            popup.locationProperty().addListener((obs, old, location) -> {
                if (location != null && !location.isBlank()) {
                    popup.load(null);
                    if (isAllowed(location)) {
                        webEngine.load(location);
                        logger.debug("Loaded popup page: {}", location);
                    } else {
                        hostServices.showDocument(location);
                        logger.debug("Opening external document in popup: {}", location);
                    }
                }
            });
            return popup;
        });

        // Signed out (from the desktop menu, or a dead session): the page still holds its JWT in memory, so reload it
        cookieStore.registerListener(sessionListener);

        webEngine.load(SOCIALS_URL);

        backToProjectsBtn.setOnAction(e -> {
            // stop the page (scripts, requests) before the view is dropped
            logger.debug("Switching back to projects view");
            cookieStore.removeListener(sessionListener);
            webEngine.load(null);
            sceneManager.switchView(ViewType.PROJECT_MENU);
        });
    }

    // [AI-GENERATED METHOD]
    /**
     * Opens a page of the site instead of the home page, e.g. {@code "/login"}. Call it on the controller that
     * {@code sceneManager.switchView(ViewType.SOCIALS)} returns; it replaces the home page load that just started.
     */
    public void open(String path) {
        webEngine.load(SOCIALS_URL + path);
    }

    // [AI-GENERATED METHOD]
    // Runs on whatever thread cleared the session (often the IO pool), so the WebView work goes to the FX thread
    private void onSessionChanged(HttpCookie cookie) {
        if (cookie != null) {
            return;
        }
        Platform.runLater(() -> {
            if (isOurSite(webEngine.getLocation())) {
                webEngine.load(SOCIALS_URL);
            }
        });
    }

    // Our site (any route) or the bundled offline page: the only places the bridge is attached
    private boolean isAllowed(String location) {
        return isOurSite(location) || isOfflinePage(location);
    }

    private boolean isOurSite(String location) {
        return location != null && (location.equals(SOCIALS_URL) || location.startsWith(SOCIALS_URL + "/"));
    }

    // [AI-GENERATED METHOD]
    // Not an equals(): WebView reports file:///C:/… while toExternalForm() gives file:/C:/…, so match the ending
    private boolean isOfflinePage(String location) {
        return location != null
                && (location.startsWith("file:") || location.startsWith("jar:"))
                && location.endsWith(OFFLINE_PAGE);
    }

    // [AI-GENERATED METHOD]
    // Only web addresses and mail links leave the app. Local pages (file:, jar:), about:blank and the empty
    // location of load(null) always stay in the WebView, so the offline page can never end up in the browser.
    private boolean isExternal(String location) {
        if (location == null || location.isBlank()) {
            return false;
        }
        String lower = location.toLowerCase(Locale.ROOT);
        if (lower.startsWith("mailto:")) {
            return true;
        }
        return (lower.startsWith("http://") || lower.startsWith("https://")) && !isOurSite(location);
    }
}
