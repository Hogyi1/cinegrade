package hu.elte.ik.thesis.cinegrade.app.main;

import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.ThreadPoolManager;
import hu.elte.ik.thesis.cinegrade.app.managers.catalog.CatalogManager;
import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.tasks.TaskManager;
import hu.elte.ik.thesis.cinegrade.app.managers.social.CineGradeApi;
import hu.elte.ik.thesis.cinegrade.app.managers.social.PersistentCookieStore;
import hu.elte.ik.thesis.cinegrade.app.managers.social.ShareManager;
import hu.elte.ik.thesis.cinegrade.app.managers.user.UserManager;
import hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetManager;
import hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetStage;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.token.RefreshTokenDao;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.PresetService;
import hu.elte.ik.thesis.cinegrade.app.scope.CatalogScopeFactory;
import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.enums.ViewType;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.domain.navigation.ControllerFactory;
import hu.elte.ik.thesis.cinegrade.domain.navigation.SceneManager;
import hu.elte.ik.thesis.cinegrade.domain.theme.ThemeManager;
import hu.elte.ik.thesis.cinegrade.engine.RenderEngine;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ExportRenderer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ThumbnailRenderer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.config.AppConfig;
import hu.elte.ik.thesis.cinegrade.infra.config.SystemInfo;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.utils.thumbnail.ThumbnailFactory;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.StorageService;
import hu.elte.ik.thesis.cinegrade.infra.utils.logger.LoggerUtils;
import hu.elte.ik.thesis.cinegrade.infra.services.RequirementChecker;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.infra.database.AppDatabase;
import hu.elte.ik.thesis.cinegrade.javafx.ui.catalog.ProjectMenuController;
import hu.elte.ik.thesis.cinegrade.javafx.ui.dialog.SplashScreenController;
import hu.elte.ik.thesis.cinegrade.javafx.ui.editing.*;
import hu.elte.ik.thesis.cinegrade.javafx.ui.presenters.ErrorPresenter;
import hu.elte.ik.thesis.cinegrade.javafx.ui.presenters.TaskToastPresenter;
import hu.elte.ik.thesis.cinegrade.javafx.ui.catalog.SocialsController;
import hu.elte.ik.thesis.cinegrade.domain.tasks.appinit.AppInitService;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import static com.sun.jna.platform.win32.GL.GL_VERSION;
import static org.lwjgl.opengl.GL11.glGetString;

public class App extends Application {

    private ThreadPoolManager threadPoolManager;
    private CatalogManager catalogManager;
    private UserManager userManager;
    private SceneManager sceneManager;
    private TaskManager taskManager;
    private ControllerFactory controllerFactory;
    private RequirementChecker requirementChecker;
    private ThemeManager themeManager;
    private Logger logger;
    private ResourceStack resources;
    private StorageService storageService;
    private ThumbnailFactory thumbnailFactory;
    private RenderEngine renderEngine;
    private ViewportRenderer viewportRenderer;
    private ThumbnailRenderer thumbnailRenderer;
    private ExportRenderer exportRenderer;
    private CookieManager cookieManager;
    private PersistentCookieStore persistentCookieStore;
    private ShareManager shareManager;
    private CineGradeApi api;

    private TaskToastPresenter taskToastPresenter;
    private ErrorPresenter errorToastPresenter;

    private TransactionRunner appTx;
    private PresetManager presetManager;

    @Override
    public void init() {
        LoggerUtils.init();
        logger = LogManager.getLogger(App.class);
        logger.info("Application starting...");

        logger.debug("Installing global error handler");
        ErrorHandler.getInstance().install();
        resources = new ResourceStack();
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            logger.debug("Application start method invoked");
            primaryStage.getIcons().add(new Image(Objects.requireNonNull(App.class.getResourceAsStream("/images/icons/cine-icon.png"))));

            createBootstrapManagers(primaryStage);
            registerControllers();
            createPresenters();

            SplashScreenController splashScreenController = sceneManager.showSplash();
            splashScreenController.startService();

            logger.debug("Task setup finished, starting AppInitService");
        } catch (Exception ex) {
            ErrorHandler.getInstance().handle(new CineGradeException(ErrorCode.STARTUP_ERROR, ex, "FATAL error inside application startup method"));
        }
    }

    // [AI-GENERATED METHOD]
    /**
     * Started by double-clicking a .cgproj (the installer's file association passes its path as the first
     * argument): open that project straight away. Otherwise, or if it can't be opened, the project menu.
     */
    private void showFirstView() {
        Path project = getParameters().getRaw().stream()
                .filter(arg -> arg.toLowerCase(Locale.ROOT).endsWith(".cgproj"))
                .map(Path::of)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        if (project != null) {
            try {
                logger.info("Opening the project the app was started with: {}", project);
                catalogManager.openCatalog(project.toFile());
                sceneManager.switchView(ViewType.EDIT_PAGE);
                return;
            } catch (Exception ex) {
                sceneManager.switchView(ViewType.PROJECT_MENU);
                ErrorHandler.getInstance().handle(ex);
                return;
            }
        }
        sceneManager.switchView(ViewType.PROJECT_MENU);
    }

    /**
     * Creates the application initialization service
     *
     * @return the initialized AppInitService
     */
    private AppInitService createAppInitService() {
        AppInitService appInitService = new AppInitService(requirementChecker, renderEngine, themeManager, storageService);
        taskManager.configure(appInitService);

        appInitService.setOnSucceeded(e -> {
            logger.info("Startup sequence completed successfully");

            createDatabaseManagers();
            PauseTransition pause = new PauseTransition(Duration.millis(1000));
            pause.setOnFinished(ev -> {
                renderEngine.getInteractive().submit(() -> {
                    SystemInfo.INSTANCE.setGlVersion(glGetString(GL_VERSION));
                    return null;
                });
                showFirstView();
            });
            pause.play();
        });

        appInitService.setOnFailed(e -> {
            Throwable ex = appInitService.getException();
            ErrorHandler.getInstance().handle(new CineGradeException(ErrorCode.STARTUP_ERROR, ex));
        });

        return appInitService;
    }

    /**
     * Register all view controllers
     */
    private void registerControllers() {
        logger.info("Registering view controllers");
        controllerFactory.register(ProjectMenuController.class, () -> new ProjectMenuController(catalogManager, userManager, sceneManager));
        controllerFactory.register(SocialsController.class, () -> new SocialsController(sceneManager, api, getHostServices(), threadPoolManager.getIoPool(), presetManager, shareManager, persistentCookieStore));
        controllerFactory.register(SplashScreenController.class, () -> new SplashScreenController(createAppInitService()));

        // Created after catalog is selected
        controllerFactory.register(WorkspaceController.class, () -> new WorkspaceController(catalogManager.getCurrentScope(), controllerFactory));
        controllerFactory.register(NavigationController.class, () -> new NavigationController(sceneManager, userManager, themeManager));
        controllerFactory.register(ImportPanelController.class, () -> new ImportPanelController(
                catalogManager.getCurrentScope().importManager(),
                catalogManager.getCurrentScope().stagingArea()));
        controllerFactory.register(DevelopmentPanelController.class, () -> new DevelopmentPanelController(catalogManager.getCurrentScope()));
        controllerFactory.register(ExportPanelController.class, () -> new ExportPanelController(catalogManager.getCurrentScope()));
        controllerFactory.register(DevelopPresetPanelController.class, () -> new DevelopPresetPanelController(
                sceneManager,
                presetManager,
                shareManager,
                catalogManager.getCurrentScope().editingManager(),
                userManager));
        controllerFactory.register(DevelopViewportController.class, () -> new DevelopViewportController(
                catalogManager.getCurrentScope().editingManager()));
        controllerFactory.register(DevelopEditPanelController.class, () -> new DevelopEditPanelController(
                catalogManager.getCurrentScope().editingManager()));
        controllerFactory.register(DevelopFilmstripController.class, () -> new DevelopFilmstripController(
                catalogManager.getCurrentScope().photoLibrary(),
                catalogManager.getCurrentScope().photoManager(),
                catalogManager.getCurrentScope().thumbnailService()));
    }

    /**
     * Create bootstrap managers
     */
    private void createBootstrapManagers(Stage primaryStage) {
        logger.info("Creating bootstrap managers");

        renderEngine = resources.push(new RenderEngine());
        thumbnailFactory = new ThumbnailFactory();
        storageService = new StorageService(Path.of(AppConfig.INSTANCE.getRootDirectory()));
        requirementChecker = new RequirementChecker();
        controllerFactory = new ControllerFactory();
        themeManager = new ThemeManager();
        sceneManager = new SceneManager(primaryStage, controllerFactory, themeManager);

        threadPoolManager = resources.push(new ThreadPoolManager(16));
        taskManager = resources.push(new TaskManager(threadPoolManager.getTaskPool()));
    }

    /**
     * Create database-dependent managers
     */
    private void createDatabaseManagers() {
        logger.info("Creating database-dependent managers");
        viewportRenderer = resources.push(new ViewportRenderer(renderEngine.getInteractive()));
        thumbnailRenderer = resources.push(new ThumbnailRenderer(renderEngine.getBackground()));
        exportRenderer = resources.push(new ExportRenderer(renderEngine.getBackground()));
        appTx = resources.push(new TransactionRunner(AppDatabase.getInstance().getConnection(), "appTx"));
        try {
            persistentCookieStore = new PersistentCookieStore(new URI(AppConfig.INSTANCE.getAppApi()), new RefreshTokenDao());
            cookieManager = new CookieManager(persistentCookieStore, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            api = resources.push(new CineGradeApi(persistentCookieStore));
            CookieHandler.setDefault(cookieManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        catalogManager = new CatalogManager(new CatalogScopeFactory(taskManager,
                thumbnailFactory,
                threadPoolManager,
                viewportRenderer,
                thumbnailRenderer,
                exportRenderer), storageService);

        resources.push(catalogManager);
        userManager = new UserManager(persistentCookieStore, api, threadPoolManager.getIoPool());
        persistentCookieStore.registerListener(userManager);
        presetManager = new PresetManager(new PresetService(), new PresetStage(), appTx);
        shareManager = new ShareManager(new PresetService(), threadPoolManager.getIoPool(), threadPoolManager.getCpuPool(),
                taskManager, exportRenderer);
    }

    private void createPresenters() {
        taskToastPresenter = new TaskToastPresenter(taskManager, sceneManager.getTaskToastContainer());
        errorToastPresenter = new ErrorPresenter(sceneManager.getErrorToastContainer());
    }

    @Override
    public void stop() {
        logger.info("Application stopped");
        try {
            resources.close();
        } catch (Exception ex) {
            logger.error("Error occurred while closing task manager", ex);
        } finally {
            AppDatabase.getInstance().close();
        }
    }
}
