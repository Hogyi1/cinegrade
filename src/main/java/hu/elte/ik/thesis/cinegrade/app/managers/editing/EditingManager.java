package hu.elte.ik.thesis.cinegrade.app.managers.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.domain.catalog.Catalog;
import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ThumbnailRenderer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.EditStateDao;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.MetadataDao;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class EditingManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(EditingManager.class);
    private final TransactionRunner catalogTx;
    private final EditStateDao editStateDao;
    private final MetadataDao metadataDao;
    private final PhotoManager photoManager;
    private final ViewportRenderer viewportRenderer;
    private final ThumbnailRenderer thumbnailRenderer;
    private final ExecutorService cpuPool;
    private final LibrawService librawService;
    private final ThumbnailService thumbnailService;
    private final ObjectProperty<EditState> currentEditState = new SimpleObjectProperty<>();
    private final ObjectProperty<EditState> copiedEditState = new SimpleObjectProperty<>();
    private final ObjectProperty<PhotoEntry> currentPhotoEntry = new SimpleObjectProperty<>();
    // Sections whose eye toggle is off: rendered with defaults, still saved with their values. FX thread only
    private final Set<GradeParam.Section> bypassed = EnumSet.noneOf(GradeParam.Section.class);

    public EditingManager(TransactionRunner catalogTx,
                          EditStateDao editStateDao,
                          MetadataDao metadataDao,
                          PhotoManager photoManager,
                          ViewportRenderer viewportRenderer,
                          ThumbnailRenderer thumbnailRenderer,
                          ExecutorService cpuPool,
                          LibrawService librawService, ThumbnailService thumbnailService) {
        this.catalogTx = catalogTx;
        this.editStateDao = editStateDao;
        this.metadataDao = metadataDao;
        this.photoManager = photoManager;
        this.viewportRenderer = viewportRenderer;
        this.thumbnailRenderer = thumbnailRenderer;
        this.cpuPool = cpuPool;
        this.librawService = librawService;
        this.thumbnailService = thumbnailService;

        photoManager.selectedPhoto().addListener((obs, oldVal, newVal) -> onPhotoChanged(oldVal, newVal));
    }

    public ReadOnlyObjectProperty<EditState> currentEditStateProperty() {
        return currentEditState;
    }

    public void onPhotoChanged(PhotoEntry oldVal, PhotoEntry newVal) {
        // every commit is already saved and thumbnailed, nothing to flush for oldVal
        changeEditState(newVal);
    }

    // [AI-GENERATED METHOD]

    /**
     * Slider is moving: change the edit in memory and redraw the viewport. Nothing is saved.
     */
    public void preview(GradeParam param, float value) {
        EditState editState = currentEditState.get();
        if (editState == null) {
            return;
        }
        param.write(editState, value);
        viewportRenderer.requestRender(effective(editState));
    }

    // [AI-GENERATED METHOD]

    /**
     * Slider released / value typed: change the edit, save it and refresh the thumbnail.
     */
    public void commit(GradeParam param, float value) {
        EditState editState = currentEditState.get();
        if (editState == null) {
            return;
        }
        param.write(editState, value);
        applySettings(photoManager.selectedPhoto().get());
    }

    // [AI-GENERATED METHOD]

    /**
     * Eye toggle of a section: bypassed sections render with their defaults but keep their saved values.
     */
    public void setSectionEnabled(GradeParam.Section section, boolean enabled) {
        if (enabled) {
            bypassed.remove(section);
        } else {
            bypassed.add(section);
        }
        if (currentEditState.get() != null) {
            viewportRenderer.requestRender(effective(currentEditState.get()));
        }
    }

    public void changeEditState(PhotoEntry photoEntry) {
        if (photoEntry == null) {
            currentEditState.set(null);
            return;
        }

        CompletableFuture<Optional<EditState>> editState = catalogTx.read(c -> editStateDao.findByPhotoId(photoEntry.getId()))
                .exceptionally(ex -> {
                    logger.error("Failed to apply edit state to database", ex);
                    return Optional.empty();
                });

        currentEditState.set(editState.join().orElseGet(() -> new EditState(photoEntry.getId())));
    }

    public void pasteSettings() {
        if (copiedEditState.get() == null || copiedEditState.get().equals(currentEditState.get()) || currentEditState.get() == null) {
            return;
        }

        String photoId = currentEditState.get().getPhotoId();
        EditState pastedEditState = new EditState(photoId, copiedEditState.get());
        currentEditState.set(pastedEditState);
        applySettings(photoManager.selectedPhoto().get());
    }

    public void applySettings(PhotoEntry photoEntry) {
        if (currentEditState.get() == null) {
            return;
        }
        // the DB and GPU threads read it later while the sliders keep changing the live one
        EditState snapshot = new EditState(currentEditState.get().getPhotoId(), currentEditState.get());

        catalogTx.execute(c -> editStateDao.upsert(snapshot))
                .exceptionally(ex -> {
                    logger.error("Failed to apply edit state to database", ex);
                    return null;
                });

        viewportRenderer.requestRender(effective(snapshot));
        updateThumbnail(photoEntry, snapshot);
    }

    // [AI-GENERATED METHOD]

    /**
     * GPU render → JPEG on the CPU pool → preview DB, then bumps the version so the filmstrip reloads it.
     * Submitted before the next photo's {@code show}, so it still renders this photo.
     */
    private void updateThumbnail(PhotoEntry photoEntry, EditState editState) {
        if (photoEntry == null) {
            return;
        }
        thumbnailRenderer.render(editState)
                .thenCompose(pixels -> pixels
                        .map(p -> thumbnailService.putPixels(photoEntry.getId(), p.bgra(), p.width(), p.height())
                                .thenApply(v -> true))
                        .orElseGet(() -> CompletableFuture.completedFuture(false)))
                .thenAccept(stored -> {
                    if (stored) {
                        Platform.runLater(() -> photoEntry.getThumbnailVersion().set(photoEntry.getThumbnailVersion().get() + 1));
                    }
                })
                .exceptionally(ex -> {
                    logger.warn("Failed to update thumbnail of {}", photoEntry.getFile(), ex);
                    return null;
                });
    }

    // [AI-GENERATED METHOD]

    /**
     * Copy of the edit with the bypassed sections set back to their defaults; this is what gets rendered.
     */
    private EditState effective(EditState editState) {
        EditState copy = new EditState(editState.getPhotoId(), editState);
        for (GradeParam param : GradeParam.values()) {
            if (bypassed.contains(param.getGroup().getSection())) {
                param.write(copy, param.getDefVal());
            }
        }
        return copy;
    }

    public void copySettings() {
        if (currentEditState.get() == null) {
            return;
        }
        // a copy, otherwise later slider moves would change the copied settings too
        copiedEditState.set(new EditState(null, currentEditState.get()));
    }

    public void saveEditState(EditState editState) {
        catalogTx.execute(c -> editStateDao.upsert(editState))
                .exceptionally(ex -> {
                    logger.error("Failed to save edit state to database", ex);
                    return null;
                });
    }

    private CompletableFuture<LibrawService.DecodedImage> extractBytes(PhotoEntry photoEntry) {
        try {
            File imgPath = photoEntry.getFile();

            if (photoEntry.isRaw()) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Optional<LibrawService.DecodedImage> decodedImage = librawService.decodeRaw(imgPath);
                        return decodedImage.orElse((LibrawService.DecodedImage) null);
                    } catch (Exception exception) {
                        logger.error("Failed to decode raw image", exception);
                        return (LibrawService.DecodedImage) null;
                    }
                }, cpuPool);
            } else {
                BufferedImage bufferedImage = ImageIO.read(imgPath);

                // get DataBufferBytes from Raster
                WritableRaster raster = bufferedImage.getRaster();
                DataBufferByte data = (DataBufferByte) raster.getDataBuffer();
                return CompletableFuture.completedFuture(new LibrawService.DecodedImage(ByteBuffer.wrap(data.getData()), bufferedImage.getWidth(), bufferedImage.getHeight(), bufferedImage.getColorModel().getPixelSize()));
            }
        } catch (IOException exception) {
            photoEntry.missingPhotoProperty().set(true);
            return CompletableFuture.failedFuture(new CineGradeException(ErrorCode.FILE_READ_FAILED, exception, "Failed to read image file"));
        }
    }

    @Override
    public void close() throws Exception {

    }
}
