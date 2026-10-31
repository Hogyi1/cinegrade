package hu.elte.ik.thesis.cinegrade.app.managers.photos;

import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.enums.ImportMethod;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.PhotoDao;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.StorageService;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public PhotoManager(TransactionRunner catalogTx, PhotoDao photoDao, PhotoLibrary photoLibrary, ThumbnailService thumbnailService, ExecutorService ioPool) {
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
        catalogTx.execute(c -> photoDao.updateRating(photo.getId(), rating))
                .thenRun(() -> fx(() -> photo.setRating(rating)))
                .exceptionally(ex -> {
                    logger.error("Failed to update rating in database for photo {}", photo.getId(), ex);
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
        catalogTx.execute(c -> photoDao.updateColorLabel(photo.getId(), colorLabel))
                .thenRun(() -> fx(() -> photo.setColorLabel(colorLabel)))
                .exceptionally(ex -> {
                    logger.error("Failed to update color label in database for photo {}", photo.getId(), ex);
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
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateRatings(ids, rating))
                .thenRun(() -> fx(() -> photos.forEach(photo -> photo.setRating(rating))))
                .exceptionally(ex -> {
                    logger.error("Failed to update rating in database for {} photo(s)", ids.size(), ex);
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
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateColorLabels(ids, colorLabel))
                .thenRun(() -> fx(() -> photos.forEach(photo -> photo.setColorLabel(colorLabel))))
                .exceptionally(ex -> {
                    logger.error("Failed to update color label in database for {} photo(s)", ids.size(), ex);
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
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateLiked(ids, liked))
                .thenRun(() -> fx(() -> photos.forEach(photo -> photo.getIsLiked().set(liked))))
                .exceptionally(ex -> {
                    logger.error("Failed to update liked state in database for {} photo(s)", ids.size(), ex);
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
        catalogTx.execute(c -> photoDao.updateComment(photo.getId(), value))
                .thenRun(() -> fx(() -> photo.setComment(value)))
                .exceptionally(ex -> {
                    logger.error("Failed to update comment in database for photo {}", photo.getId(), ex);
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
                .thenRun(() -> fx(() -> photoLibrary.removeAll(photo)))
                .thenRun(() -> thumbnailService.delete(photo.getId()))
                .exceptionally(ex -> {
                    logger.error("Failed to delete photo {} from database", photo.getId(), ex);
                    return null;
                });
    }

    public void updateIsEdited(PhotoEntry photo, boolean edited) {
        catalogTx.execute(c -> photoDao.updateEdited(photo.getId(), edited))
                .thenRun(() -> fx(() -> photo.getIsEdited().set(edited)))
                .exceptionally(ex -> {
                    logger.error("Failed to update edited state for photo {}", photo.getId(), ex);
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
                        Files.deleteIfExists(photo.getFile().toPath());
                    } catch (IOException e) {
                        throw new CineGradeException(ErrorCode.FILE_DELETE_FAILED, e, "Failed to delete photo file");
                    }
                    return null;
                }, ioPool).thenRun(() -> removePhoto(photo))
                .exceptionally(ex -> {
                    ErrorHandler.getInstance().handle(ex.getCause());
                    return null;
                });
    }

    @Override
    public void close() {
        photoLibrary.clear();
        photoLibrary.reset();
    }
}
