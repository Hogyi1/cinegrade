package hu.elte.ik.thesis.cinegrade.app.scope;

import hu.elte.ik.thesis.cinegrade.app.managers.catalog.HistoryManager;
import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.ThreadPoolManager;
import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingStage;
import hu.elte.ik.thesis.cinegrade.app.managers.editing.ThumbnailRefresher;
import hu.elte.ik.thesis.cinegrade.app.managers.export.ExportManager;
import hu.elte.ik.thesis.cinegrade.app.managers.export.ExportStage;
import hu.elte.ik.thesis.cinegrade.app.managers.imports.ImportManager;
import hu.elte.ik.thesis.cinegrade.app.managers.imports.StagingArea;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.tasks.TaskManager;
import hu.elte.ik.thesis.cinegrade.app.managers.imports.StagingPipeline;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoLibrary;
import hu.elte.ik.thesis.cinegrade.domain.catalog.Catalog;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ExportRenderer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ThumbnailRenderer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.EditStateDao;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.MetadataDao;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.PhotoDao;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.preview.PhotoBlobDao;
import hu.elte.ik.thesis.cinegrade.infra.services.exiftool.ExifToolService;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.StorageService;
import hu.elte.ik.thesis.cinegrade.infra.utils.thumbnail.ThumbnailFactory;
import hu.elte.ik.thesis.cinegrade.infra.database.CatalogDatabase;
import hu.elte.ik.thesis.cinegrade.infra.database.PreviewDatabase;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.CatalogService;

public class CatalogScopeFactory {

    private final TaskManager taskManager;
    private final ThumbnailFactory thumbnailFactory;
    private final ThreadPoolManager threadPoolManager;
    private final ViewportRenderer viewportRenderer;
    private final ThumbnailRenderer thumbnailRenderer;
    private final ExportRenderer exportRenderer;

    public CatalogScopeFactory(TaskManager taskManager,
                               ThumbnailFactory thumbnailFactory,
                               ThreadPoolManager threadPoolManager,
                               ViewportRenderer viewportRenderer,
                               ThumbnailRenderer thumbnailRenderer,
                               ExportRenderer exportRenderer) {
        this.taskManager = taskManager;
        this.thumbnailFactory = thumbnailFactory;
        this.threadPoolManager = threadPoolManager;
        this.viewportRenderer = viewportRenderer;
        this.thumbnailRenderer = thumbnailRenderer;
        this.exportRenderer = exportRenderer;
    }

    public CatalogScope open(Catalog catalog) {
        ResourceStack resources = new ResourceStack();
        try {
            CatalogDatabase db = resources.push(new CatalogDatabase(catalog.getProjectFilePath()));
            PreviewDatabase previewDb = resources.push(new PreviewDatabase(catalog.getPreviewDatabasePath()));

            TransactionRunner transactionRunner = resources.push(new TransactionRunner(db.getConnection(), "catalog"));
            PhotoBlobDao blobDao = new PhotoBlobDao(previewDb.getConnection());
            PhotoDao photoDao = new PhotoDao(db.getConnection(), catalog.getProjectDirectory());
            MetadataDao metadataDao = new MetadataDao(db.getConnection());
            EditStateDao editStateDao = new EditStateDao(db.getConnection());

            CatalogService catalogService = new CatalogService(db.getConnection());
            ThumbnailService thumbnailService = resources.push(new ThumbnailService(blobDao, thumbnailFactory, threadPoolManager.getCpuPool()));
            ExifToolService exifToolService = new ExifToolService();
            StorageService storageService = new StorageService(catalog.getProjectDirectory());

            HistoryManager historyManager = new HistoryManager();
            PhotoLibrary photoLibrary = new PhotoLibrary();
            PhotoManager photoManager = resources.push(new PhotoManager(
                    taskManager,
                    historyManager,
                    transactionRunner,
                    photoDao,
                    photoLibrary,
                    thumbnailService,
                    threadPoolManager.getIoPool()));

            StagingArea stagingArea = new StagingArea();
            StagingPipeline stagingPipeline = new StagingPipeline(stagingArea,
                    thumbnailFactory,
                    exifToolService,
                    threadPoolManager.getCpuPool(),
                    threadPoolManager.getIoPool(),
                    threadPoolManager.getTaskPool(),
                    photoLibrary::getPhotos);

            ImportManager importManager = resources.push(
                    new ImportManager(catalog,
                            taskManager,
                            photoManager,
                            stagingArea,
                            stagingPipeline,
                            photoDao,
                            metadataDao,
                            editStateDao,
                            transactionRunner,
                            thumbnailService,
                            exifToolService,
                            storageService));

            ThumbnailRefresher thumbnailRefresher = new ThumbnailRefresher(thumbnailRenderer, thumbnailService, threadPoolManager.getCpuPool());
            EditingStage editingStage = new EditingStage(viewportRenderer.frameProperty(), photoManager.selectedPhoto());
            EditingManager editingManager = resources.push(new EditingManager(historyManager, transactionRunner,
                    editStateDao,
                    metadataDao,
                    photoManager,
                    viewportRenderer,
                    thumbnailRefresher,
                    editingStage));

            ExportStage exportStage = new ExportStage(photoLibrary);
            ExportManager exportManager = resources.push(new ExportManager(exportRenderer,
                    editingManager,
                    taskManager,
                    threadPoolManager.getIoPool(),
                    threadPoolManager.getCpuPool(),
                    exportStage));

            return new CatalogScope(resources, catalog, thumbnailService, catalogService, stagingArea, importManager, editingManager, exportManager, photoManager, photoLibrary);
        } catch (Exception e) {
            resources.close();
            throw new RuntimeException("Failed to open catalog scope", e);
        }
    }
}
