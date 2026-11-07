package hu.elte.ik.thesis.cinegrade.app.main;

import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.ThreadPoolManager;
import hu.elte.ik.thesis.cinegrade.app.managers.catalog.CatalogManager;
import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.tasks.TaskManager;
import hu.elte.ik.thesis.cinegrade.app.managers.user.UserManager;
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
import hu.elte.ik.thesis.cinegrade.javafx.ui.test.ErrorTestController;
import hu.elte.ik.thesis.cinegrade.domain.tasks.appinit.AppInitService;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

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

    private TaskToastPresenter taskToastPresenter;
    private ErrorPresenter errorToastPresenter;

    private TransactionRunner appTx;

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
                sceneManager.switchView(ViewType.PROJECT_MENU);
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
        controllerFactory.register(ErrorTestController.class, () -> new ErrorTestController(sceneManager));
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
                catalogManager.getCurrentScope().presetManager(),
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
        // DEV: shader hot reload, remove before release (Documentation/REMOVE_BEFORE_RELEASE.md)
        resources.push(new hu.elte.ik.thesis.cinegrade.engine.ShaderHotReload(
                java.util.List.of(renderEngine.getInteractive(), renderEngine.getBackground()), viewportRenderer::rerender));
        thumbnailRenderer = resources.push(new ThumbnailRenderer(renderEngine.getBackground()));
        exportRenderer = resources.push(new ExportRenderer(renderEngine.getBackground()));
        appTx = resources.push(new TransactionRunner(AppDatabase.getInstance().getConnection(), "appTx"));

        catalogManager = new CatalogManager(new CatalogScopeFactory(taskManager,
                thumbnailFactory,
                threadPoolManager,
                viewportRenderer,
                thumbnailRenderer,
                exportRenderer,
                appTx), storageService);

        resources.push(catalogManager);
        userManager = new UserManager();
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
