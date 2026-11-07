package hu.elte.ik.thesis.cinegrade.app.managers.photos;

import hu.elte.ik.thesis.cinegrade.app.managers.catalog.HistoryManager;
import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.tasks.TaskManager;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.PhotoDao;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

public class PhotoManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(PhotoManager.class);

    private final PhotoDao photoDao;
    private final TransactionRunner catalogTx;
    private final PhotoLibrary photoLibrary;
    private final ThumbnailService thumbnailService;
    private final ExecutorService ioPool;
    private final HistoryManager historyManager;
    private final TaskManager taskManager;

    public PhotoManager(TaskManager taskManager, HistoryManager historyManager, TransactionRunner catalogTx, PhotoDao photoDao, PhotoLibrary photoLibrary, ThumbnailService thumbnailService, ExecutorService ioPool) {
        this.taskManager = taskManager;
        this.historyManager = historyManager;
        this.catalogTx = catalogTx;
        this.photoDao = photoDao;
        this.photoLibrary = photoLibrary;
        this.thumbnailService = thumbnailService;
        this.ioPool = ioPool;

        loadCatalogPhotos();
    }

    private static void fx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private static String ratingText(int rating) {
        return rating == 0 ? "Rating removed" : "Rating " + rating;
    }

    private static String colorLabelText(String colorLabel) {
        return colorLabel == null ? "Color label removed" : "Color label " + colorLabel;
    }

    /**
     * Records one update for the undo history. Call it before the change is made, on the calling thread: while an
     * undo runs, this same call has to be the one that gets ignored, and each photo's old value is read here.
     *
     * @param read  the photo's current value
     * @param write the update method itself, undo and redo call it with the old / new value
     * @param value the new value; nothing is recorded if every photo already has it
     */
    private <T> void recordUpdate(String description, List<PhotoEntry> photos, java.util.function.Function<PhotoEntry, T> read,
                                  java.util.function.BiConsumer<PhotoEntry, T> write, T value) {
        List<PhotoEntry> targets = List.copyOf(photos);
        if (targets.stream().allMatch(photo -> java.util.Objects.equals(read.apply(photo), value))) {
            return;
        }
        List<T> before = targets.stream().map(read).toList();
        String text = targets.size() > 1 ? description + " (" + targets.size() + " photos)" : description;
        historyManager.record(new HistoryManager.HistoryEntry(text,
                targets.stream().map(PhotoEntry::getId).collect(java.util.stream.Collectors.toSet()),
                () -> {
                    for (int i = 0; i < targets.size(); i++) {
                        write.accept(targets.get(i), before.get(i));
                    }
                },
                () -> targets.forEach(photo -> write.accept(photo, value))));
    }

    public ObjectProperty<PhotoEntry> selectedPhoto() {
        return photoLibrary.getSelectedPhotoProperty();
    }

    public ObservableList<PhotoEntry> selectedPhotos() {
        return photoLibrary.getSelectedEntries();
    }

    /**
     * Starts a new import session by clearing the previous import list.
     */
    public void startImportSession() {
        Platform.runLater(photoLibrary::startImportSession);
    }

    public void addAll(List<PhotoEntry> photoEntries) {
        if (photoEntries != null && !photoEntries.isEmpty()) {
            photoLibrary.addAll(photoEntries);
        }
    }

    /**
     * Loads existing photos from the catalog database into memory at startup.
     */
    public void loadCatalogPhotos() {
        try {
            List<PhotoEntry> existingPhotos = catalogTx.read(c -> photoDao.findAll()).join();
            if (existingPhotos != null) {

                photoLibrary.addAll(existingPhotos);
                logger.info("Loaded {} existing photo(s) from catalog database", existingPhotos.size());

                Optional<PhotoEntry> lastImported = existingPhotos.stream().max(Comparator.comparing(PhotoEntry::getDateImported));
                if (lastImported.isPresent()) {
                    String importId = lastImported.get().getImportId();
                    List<PhotoEntry> lastImportedPhotos = existingPhotos.stream().filter(photo -> importId.equals(photo.getImportId())).toList();

                    // Already added above: only mark them, addToImported would add them twice
                    photoLibrary.markLastImported(lastImportedPhotos);
                }
            }
        } catch (CompletionException e) {
            logger.error("Failed to load photos from catalog database", e.getCause());
        }
    }

    /**
     * Called progressively by ImportService as each batch of photos is committed.
     */
    public void addImportedBatch(List<PhotoEntry> batch) {
        if (batch == null || batch.isEmpty()) return;
        photoLibrary.addToImported(batch);
    }

    /**
     * Updates the rating of a photo in the database.
     *
     * @param photo  the photo entry to update
     * @param rating the new rating
     */
    public void updateRating(PhotoEntry photo, int rating) {
        recordUpdate(ratingText(rating), List.of(photo), PhotoEntry::getRating, this::updateRating, rating);
        catalogTx.execute(c -> photoDao.updateRating(photo.getId(), rating))
                .thenRun(() -> fx(() -> photo.setRating(rating)))
                .exceptionally(ex -> {
                    logger.error("Failed to update rating in database for photo {}", photo.getId(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    /**
     * Updates the color label of a photo in the database.
     *
     * @param photo      the photo entry to update
     * @param colorLabel the new color label
     */
    public void updateColorLabel(PhotoEntry photo, String colorLabel) {
        recordUpdate(colorLabelText(colorLabel), List.of(photo), PhotoEntry::getColorLabel, this::updateColorLabel, colorLabel);
        catalogTx.execute(c -> photoDao.updateColorLabel(photo.getId(), colorLabel))
                .thenRun(() -> fx(() -> photo.setColorLabel(colorLabel)))
                .exceptionally(ex -> {
                    logger.error("Failed to update color label in database for photo {}", photo.getId(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    /**
     * Sets the rating of several photos at once (multi-select, rating shortcuts).
     *
     * @param photos the photos to update
     * @param rating the new rating, 0 clears it
     */
    public void updateRating(List<PhotoEntry> photos, int rating) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        recordUpdate(ratingText(rating), photos, PhotoEntry::getRating, this::updateRating, rating);
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateRatings(ids, rating))
                .thenRun(() -> fx(() -> photos.forEach(photo -> photo.setRating(rating))))
                .exceptionally(ex -> {
                    logger.error("Failed to update rating in database for {} photo(s)", ids.size(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    /**
     * Sets the color label of several photos at once.
     *
     * @param photos     the photos to update
     * @param colorLabel the new color label, {@code null} clears it
     */
    public void updateColorLabel(List<PhotoEntry> photos, String colorLabel) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        recordUpdate(colorLabelText(colorLabel), photos, PhotoEntry::getColorLabel, this::updateColorLabel, colorLabel);
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateColorLabels(ids, colorLabel))
                .thenRun(() -> fx(() -> photos.forEach(photo -> photo.setColorLabel(colorLabel))))
                .exceptionally(ex -> {
                    logger.error("Failed to update color label in database for {} photo(s)", ids.size(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    /**
     * Likes or unlikes several photos at once.
     *
     * @param photos the photos to update
     * @param liked  the new liked state
     */
    public void updateLiked(List<PhotoEntry> photos, boolean liked) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        recordUpdate(liked ? "Liked" : "Like removed", photos, photo -> photo.getIsLiked().get(),
                (photo, value) -> updateLiked(List.of(photo), value), liked);
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateLiked(ids, liked))
                .thenRun(() -> fx(() -> photos.forEach(photo -> photo.getIsLiked().set(liked))))
                .exceptionally(ex -> {
                    logger.error("Failed to update liked state in database for {} photo(s)", ids.size(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    /**
     * Sets the comment of a photo.
     *
     * @param photo   the photo entry to update
     * @param comment the new comment, {@code null} or blank removes it
     */
    public void updateComment(PhotoEntry photo, String comment) {
        String value = comment != null && !comment.isBlank() ? comment.strip() : null;
        recordUpdate(value == null ? "Comment removed" : "Comment", List.of(photo), PhotoEntry::getComment, this::updateComment, value);
        catalogTx.execute(c -> photoDao.updateComment(photo.getId(), value))
                .thenRun(() -> fx(() -> photo.setComment(value)))
                .exceptionally(ex -> {
                    logger.error("Failed to update comment in database for photo {}", photo.getId(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    public void updateIsEdited(PhotoEntry photo, boolean edited) {
        catalogTx.execute(c -> photoDao.updateEdited(photo.getId(), edited))
                .thenRun(() -> fx(() -> photo.getIsEdited().set(edited)))
                .exceptionally(ex -> {
                    logger.error("Failed to update edited state for photo {}", photo.getId(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    /**
     * Removes a photo from the catalog and the database.
     *
     * @param photo the photo entry to remove
     */
    public void removePhoto(PhotoEntry photo) {
        if (photo == null) {
            return;
        }

        catalogTx.execute(c -> photoDao.delete(photo.getId()))
                .thenRun(() -> fx(() -> {
                    photoLibrary.removeAll(photo);
                    historyManager.forgetPhoto(photo.getId());
                }))
                .thenRun(() -> thumbnailService.delete(photo.getId()))
                .exceptionally(ex -> {
                    logger.error("Failed to delete photo {} from database", photo.getId(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return null;
                });
    }

    /**
     * Deletes the photo's file from the disk and removes it from the catalog (Shift+Delete in the filmstrip).
     *
     * @param photo the photo entry to delete
     */
    public void deletePhoto(PhotoEntry photo) {
        if (photo == null) {
            return;
        }

        CompletableFuture.supplyAsync(() -> {
                    try {
                        return Files.deleteIfExists(photo.getFile().toPath());
                    } catch (IOException e) {
                        throw new CineGradeException(ErrorCode.FILE_DELETE_FAILED, e, "Failed to delete photo file");
                    }
                }, ioPool)
                .thenApply(deleted -> {
                    removePhoto(photo);
                    return null;
                })
                .exceptionally(ex -> {
                    ErrorHandler.getInstance().handle(ex.getCause());
                    return null;
                }).join();
    }

    /**
     * Removes multiple photos from the catalog and the database, updating task progress.
     *
     * @param photos the photo entries to remove
     */
    public void removePhotos(List<PhotoEntry> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }

        Service<Void> removeService = new Service<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        int total = photos.size();
                        for (int i = 0; i < total; i++) {
                            if (isCancelled()) {
                                break;
                            }
                            PhotoEntry photo = photos.get(i);
                            updateMessage(String.format("Removing photo %d of %d: %s", i + 1, total, photo.getFile().getName()));
                            updateProgress(i + 1, total);

                            removePhoto(photo);
                        }
                        updateMessage("Photos removed successfully.");
                        return null;
                    }
                };
            }
        };


        taskManager.configure(removeService);
        removeService.start();
    }

    /**
     * Deletes multiple photos' files from disk and removes them from the catalog, updating task progress.
     *
     * @param photos the photo entries to delete
     */
    public void deletePhotos(List<PhotoEntry> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }

        Service<Void> deleteService = new Service<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        int total = photos.size();
                        for (int i = 0; i < total; i++) {
                            updateMessage(String.format("Deleting %d of %d", i + 1, total));
                            updateProgress(i + 1, total);
                            try {
                                if (isCancelled()) {
                                    break;
                                }
                                deletePhoto(photos.get(i));
                            } catch (Exception ex) {
                                ErrorHandler.getInstance().handle(ex);
                                break;
                            }
                        }
                        return null;
                    }
                };
            }
        };

        taskManager.configure(deleteService);
        deleteService.start();
    }

    @Override
    public void close() {
        photoLibrary.reset();
        photoLibrary.clear();
    }
}

