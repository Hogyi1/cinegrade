package hu.elte.ik.thesis.cinegrade.app.managers.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.domain.catalog.Catalog;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.EditStateDao;

public class EditingManager implements AutoCloseable {

    private final TransactionRunner transactionRunner;
    private final EditStateDao editStateDao;
    private final Catalog catalog;
    private final PhotoManager photoManager;
    private final ViewportRenderer viewportRenderer;

    public EditingManager(TransactionRunner transactionRunner,
                          EditStateDao editStateDao,
                          Catalog catalog,
                          PhotoManager photoManager,
                          ViewportRenderer viewportRenderer) {
        this.transactionRunner = transactionRunner;
        this.editStateDao = editStateDao;
        this.catalog = catalog;
        this.photoManager = photoManager;
        this.viewportRenderer = viewportRenderer;
    }

    @Override
    public void close() {
    }
}
