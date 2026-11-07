package hu.elte.ik.thesis.cinegrade.app.managers.photos;

import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.Operation;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * View model of the photos of the open catalog: the master list, the filtered and sorted view the filmstrip
 * shows, the filter settings and the selection.
 * <p>
 * Threads: the lists and properties belong to the FX thread. The mutating methods can be called from any thread;
 * off the FX thread they are queued with {@code Platform.runLater}, so the change is not visible yet when the
 * call returns.
 */
@Getter
public class PhotoLibrary {

    private static final Logger logger = LogManager.getLogger(PhotoLibrary.class);

    private final ObservableList<PhotoEntry> masterEntries = FXCollections.observableArrayList(
            e -> new Observable[]{
                    e.getRatingProperty(),
                    e.getIsEdited(),
                    e.getIsLiked(),
                    e.getCommentProperty()});
    private final ObservableSet<PhotoEntry> lastImportedPhotos = FXCollections.observableSet();

    // Filter on different predicates
    private final FilteredList<PhotoEntry> filteredEntries = new FilteredList<>(masterEntries);
    // Sort on filenames, RAW before its JPEG
    private final SortedList<PhotoEntry> sortedEntries = new SortedList<>(filteredEntries);
    private final ObservableList<PhotoEntry> selectedEntries = FXCollections.observableArrayList();

    private final ObjectProperty<PhotoEntry> selectedPhotoProperty = new SimpleObjectProperty<>();
    private final IntegerProperty selectedFilesProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty listedPhotosProperty = new SimpleIntegerProperty(0);

    private final IntegerProperty ratingProperty = new SimpleIntegerProperty(0);
    private final ObjectProperty<Operation> ratingOperationProperty = new SimpleObjectProperty<>(Operation.GREATER_OR_EQUAL);
    private final BooleanProperty likedProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty commentedProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty editedProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty notEditedProperty = new SimpleBooleanProperty(false);
    private final StringProperty nameProperty = new SimpleStringProperty("");

    private final BooleanProperty lastImportedProperty = new SimpleBooleanProperty(false);

    public PhotoLibrary() {
        selectedFilesProperty.bind(Bindings.size(selectedEntries));
        listedPhotosProperty.bind(Bindings.size(filteredEntries));
        sortedEntries.setComparator(Comparator.comparing(PhotoEntry::getFileNameWithoutExtension)
                .thenComparing((PhotoEntry e) -> e.isRaw() ? 0 : 1));

        filteredEntries.predicateProperty().bind(Bindings.createObjectBinding(() -> {
                    boolean liked = likedProperty.get();
                    boolean commented = commentedProperty.get();
                    boolean edited = editedProperty.get();
                    boolean notEdited = notEditedProperty.get();
                    int rating = ratingProperty.get();
                    Operation operation = ratingOperationProperty.get();
                    String nameFilter = nameProperty.get().trim().toLowerCase();
                    boolean lastImported = lastImportedProperty.get();

                    return (Predicate<PhotoEntry>) entry -> {
                        boolean matchesName = nameFilter.isEmpty() || entry.getFileName().toLowerCase().contains(nameFilter);
                        boolean isEdited = entry.getIsEdited().get();
                        boolean matchesEdited = (!edited || isEdited) && (!notEdited || !isEdited);
                        boolean matchesLiked = !liked || entry.getIsLiked().get();
                        boolean matchesComment = !commented || entry.hasComment();
                        boolean matchesRating = operation.test(entry.getRating(), rating);
                        boolean matchesImport = !lastImported || lastImportedPhotos.contains(entry);

                        return matchesName && matchesRating && matchesEdited && matchesLiked && matchesComment && matchesImport;
                    };
                }, ratingProperty, ratingOperationProperty, likedProperty, commentedProperty, editedProperty, notEditedProperty,
                nameProperty, lastImportedProperty));
    }

    private static void fx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Adds photos to the library, e.g. the catalog's photos when it is opened.
     *
     * @param entries the photos to add; null or empty is ignored
     */
    public void addAll(List<PhotoEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        fx(() -> masterEntries.addAll(entries));
    }

    /**
     * Removes every photo and clears the selection.
     */
    public void clear() {
        fx(() -> {
            logger.info("Clearing {} photos from the library", masterEntries.size());
            selectedPhotoProperty.set(null);
            selectedEntries.clear();
            lastImportedPhotos.clear();
            masterEntries.clear();
        });
    }

    /**
     * Starts a new import: the previous import is forgotten and the view switches to the last import.
     */
    public void startImportSession() {
        fx(() -> {
            lastImportedPhotos.clear();
            showLastImportedPhotos(true);
        });
    }

    /**
     * Adds a committed batch of the running import: the photos join the library and the last import.
     *
     * @param entries the photos of the batch; null or empty is ignored
     */
    public void addToImported(List<PhotoEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        fx(() -> {
            lastImportedPhotos.addAll(entries);
            masterEntries.addAll(entries);
        });
    }

    /**
     * Marks photos already in the library as the last import, e.g. after loading the catalog.
     *
     * @param entries the photos of the newest import
     */
    public void markLastImported(List<PhotoEntry> entries) {
        fx(() -> {
            lastImportedPhotos.clear();
            if (entries != null) {
                lastImportedPhotos.addAll(entries);
            }
        });
    }

    /**
     * Resets the filter properties to their default values, which show every photo.
     */
    public void reset() {
        fx(() -> {
            ratingProperty.set(0);
            ratingOperationProperty.set(Operation.GREATER_OR_EQUAL);
            likedProperty.set(false);
            commentedProperty.set(false);
            editedProperty.set(false);
            notEditedProperty.set(false);
            nameProperty.set("");
            lastImportedProperty.set(false);
        });
    }

    /**
     * Removes a photo from the library and the selection.
     *
     * @param entry the photo to remove
     */
    public void removeAll(PhotoEntry entry) {
        fx(() -> {
            selectedEntries.remove(entry);
            if (selectedPhotoProperty.get() == entry) {
                selectNextOne(entry);
            }
            lastImportedPhotos.remove(entry);
            masterEntries.remove(entry);
        });
    }

    /**
     * Sets the selection. Called by the filmstrip when the user clicks.
     *
     * @param active   the photo to show in the viewport, may be {@code null}
     * @param selected every selected photo, including the active one
     */
    public void select(PhotoEntry active, List<PhotoEntry> selected) {
        fx(() -> {
            selectedEntries.setAll(selected);
            if (active == null) {
                return;
            }
            selectedPhotoProperty.set(active);
        });
    }

    private void selectNextOne(PhotoEntry photoEntry) {
        int index = filteredEntries.indexOf(photoEntry);
        if (index >= 0 && index < filteredEntries.size() - 1) {
            selectedPhotoProperty.set(filteredEntries.get(index + 1));
        } else if (index > 0) {
            // the last one: the one before it, not itself
            selectedPhotoProperty.set(filteredEntries.get(index - 1));
        } else {
            selectedPhotoProperty.set(null);
        }
    }

    /**
     * Shows only the last import, or every photo again.
     *
     * @param show {@code true} for the last import only
     */
    public void showLastImportedPhotos(boolean show) {
        fx(() -> {
            lastImportedProperty.set(show);
            // empty at the start of every import; a copy, setAll with its own list would empty it
            if (!filteredEntries.isEmpty() && !filteredEntries.contains(selectedPhotoProperty.get())) {
                select(filteredEntries.getLast(), List.copyOf(selectedEntries));
            }
        });
    }

    /**
     * The selected photos as a copy, safe to hand to a background job.
     */
    public List<PhotoEntry> getSelection() {
        return new ArrayList<>(selectedEntries);
    }

    public ObservableList<PhotoEntry> getPhotos() {
        return FXCollections.unmodifiableObservableList(masterEntries);
    }
}
