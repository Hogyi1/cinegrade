package hu.elte.ik.thesis.cinegrade.app.scope;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.app.managers.export.ExportManager;
import hu.elte.ik.thesis.cinegrade.app.managers.imports.ImportManager;
import hu.elte.ik.thesis.cinegrade.app.managers.imports.StagingArea;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoLibrary;
import hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetManager;
import hu.elte.ik.thesis.cinegrade.domain.catalog.Catalog;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.CatalogService;

public record CatalogScope(ResourceStack resources,
                           Catalog catalog,
                           ThumbnailService thumbnailService,
                           CatalogService catalogService,
                           StagingArea stagingArea,
                           ImportManager importManager,
                           EditingManager editingManager,
                           ExportManager exportManager,
                           PhotoManager photoManager,
                           PhotoLibrary photoLibrary,
                           PresetManager presetManager) implements AutoCloseable {

    @Override
    public void close() {
        resources.close();
    }
}
