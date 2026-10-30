package hu.elte.ik.thesis.cinegrade.app.managers.photos;

import hu.elte.ik.thesis.cinegrade.domain.catalog.Catalog;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.PhotoDao;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

public class PhotoManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(PhotoManager.class);

    private final PhotoDao photoDao;
    private final TransactionRunner catalogTx;
    private final PhotoLibrary photoLibrary;
    private final ThumbnailService thumbnailService;

    public PhotoManager(TransactionRunner catalogTx, PhotoDao photoDao, PhotoLibrary photoLibrary, ThumbnailService thumbnailService) {
        this.catalogTx = catalogTx;
        this.photoDao = photoDao;
        this.photoLibrary = photoLibrary;
        this.thumbnailService = thumbnailService;

        loadCatalogPhotos();
    }

    public ObjectProperty<PhotoEntry> selectedPhoto() {
        return photoLibrary.getSelectedPhotoProperty();
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
        photo.setRating(rating);
        catalogTx.execute(c -> photoDao.updateRating(photo.getId(), rating))
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
        photo.setColorLabel(colorLabel);
        catalogTx.execute(c -> photoDao.updateColorLabel(photo.getId(), colorLabel))
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
        photos.forEach(photo -> photo.setRating(rating));
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateRatings(ids, rating))
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
        photos.forEach(photo -> photo.setColorLabel(colorLabel));
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateColorLabels(ids, colorLabel))
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
        photos.forEach(photo -> photo.getIsLiked().set(liked));
        List<String> ids = photos.stream().map(PhotoEntry::getId).toList();
        catalogTx.execute(c -> photoDao.updateLiked(ids, liked))
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
        photo.setComment(value);
        catalogTx.execute(c -> photoDao.updateComment(photo.getId(), value))
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

        photoLibrary.removeAll(photo);

        catalogTx.execute(c -> photoDao.delete(photo.getId()))
                .thenRun(() -> thumbnailService.delete(photo.getId()))
                .exceptionally(ex -> {
                    logger.error("Failed to delete photo {} from database", photo.getId(), ex);
                    return null;
                });
    }

    /**
     * Deletes the photo's file from the disk and removes it from the catalog (Shift+Delete in the filmstrip).
     *
     * @param photo the photo entry to delete
     */
    public void deletePhoto(PhotoEntry photo) {
        // TODO delete the file on the ioPool, then removePhoto(photo)
        logger.warn("deletePhoto is not implemented yet, {} was not deleted", photo.getId());
    }

    @Override
    public void close() {
        photoLibrary.clear();
        photoLibrary.reset();
    }
}
