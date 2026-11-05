package hu.elte.ik.thesis.cinegrade.app.managers.export;

import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoLibrary;
import hu.elte.ik.thesis.cinegrade.domain.editing.LutExportOption;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoExportOptions;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.PhotoExtension;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.nio.file.Path;
import java.util.List;

@lombok.Getter
public class ExportStage {
    private final PhotoLibrary library;
    private final ObjectProperty<Scope> scope = new SimpleObjectProperty<>(Scope.SELECTED);
    private final ObjectProperty<PhotoExtension> format = new SimpleObjectProperty<>(PhotoExtension.JPEG);
    private final IntegerProperty quality = new SimpleIntegerProperty(90);
    private final ObjectProperty<Path> destination = new SimpleObjectProperty<>();
    private final ObjectProperty<String> prefix = new SimpleObjectProperty<>();
    private final ObjectProperty<String> postfix = new SimpleObjectProperty<>();
    private final ObjectProperty<String> fileName = new SimpleObjectProperty<>();
    private final IntegerProperty selectedPhotosCount = new SimpleIntegerProperty(0);
    private final IntegerProperty allPhotosCount = new SimpleIntegerProperty(0);
    private final ObjectProperty<PhotoEntry> currentPhoto = new SimpleObjectProperty<>();
    private final IntegerProperty visiblePhotosCount = new SimpleIntegerProperty(0);
    private final IntegerProperty lastImportCount = new SimpleIntegerProperty(0);
    private final IntegerProperty lutSize = new SimpleIntegerProperty(33);
    private final javafx.beans.property.BooleanProperty lutLookOnly = new javafx.beans.property.SimpleBooleanProperty(true);
    private final ObjectProperty<Path> lutFile = new SimpleObjectProperty<>();

    public ExportStage(PhotoLibrary library) {
        this.library = library;
        this.selectedPhotosCount.bind(Bindings.size(library.getSelectedEntries()));
        this.allPhotosCount.bind(Bindings.size(library.getMasterEntries()));
        this.visiblePhotosCount.bind(Bindings.size(library.getSortedEntries()));
        this.lastImportCount.bind(Bindings.size(library.getLastImportedPhotos()));
        this.currentPhoto.bind(library.getSelectedPhotoProperty());
    }

    public List<PhotoEntry> photosInScope() {
        PhotoEntry current = library.getSelectedPhotoProperty().get();
        return switch (scope.get()) {
            case CURRENT -> current == null ? List.of() : List.of(current);
            case SELECTED -> List.copyOf(library.getSelectedEntries());
            case VISIBLE -> List.copyOf(library.getSortedEntries());
            case LAST_IMPORT -> List.copyOf(library.getLastImportedPhotos());
            case ALL -> List.copyOf(library.getMasterEntries());
        };
    }

    public PhotoExportOptions snapshotOfPhotoOption() {
        return new PhotoExportOptions(destination.get(), format.get(), quality.get() / 100f,
                prefix.get(), postfix.get(), fileName.get());
    }

    public LutExportOption snapshotOfLutOption() {
        return new LutExportOption(lutSize.get(), lutFile.get(), lutLookOnly.get());
    }

    public enum Scope {CURRENT, SELECTED, VISIBLE, LAST_IMPORT, ALL}
}
