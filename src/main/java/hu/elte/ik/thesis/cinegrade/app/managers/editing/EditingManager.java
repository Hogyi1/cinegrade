package hu.elte.ik.thesis.cinegrade.app.managers.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoMetadata;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.enums.WhiteBalanceType;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import hu.elte.ik.thesis.cinegrade.engine.grade.AsShotWhiteBalance;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ThumbnailRenderer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.EditStateDao;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.MetadataDao;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import hu.elte.ik.thesis.cinegrade.javafx.ui.presenters.HistogramService;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.image.Image;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public class EditingManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(EditingManager.class);
    private final TransactionRunner catalogTx;
    private final EditStateDao editStateDao;
    private final MetadataDao metadataDao;
    private final PhotoManager photoManager;
    private final ViewportRenderer viewportRenderer;
    private final ThumbnailRenderer thumbnailRenderer;
    private final ExecutorService cpuPool;
    private final ThumbnailService thumbnailService;
    private final LibrawService librawService;
    private final ObjectProperty<EditState> currentEditState = new SimpleObjectProperty<>();
    private final ObjectProperty<EditState> copiedEditState = new SimpleObjectProperty<>();
    private final ObjectProperty<PhotoEntry> currentPhotoEntry = new SimpleObjectProperty<>();
    private final ObjectProperty<Image> placeholder = new SimpleObjectProperty<>(null);
    // Sections whose eye toggle is off: rendered with defaults, still saved with their values. FX thread only
    private final Set<GradeParam.Section> bypassed = EnumSet.noneOf(GradeParam.Section.class);
    private final BooleanProperty loading = new SimpleBooleanProperty(false), showOriginal = new SimpleBooleanProperty(false);
    private final ExecutorService decoderThread = Executors.newSingleThreadExecutor();
    private final Map<String, EditState> thumbnailEdits = new HashMap<>();
    private final Map<String, PhotoEntry> thumbnailPhotos = new HashMap<>();
    private final Deque<String> thumbnailQueue = new ArrayDeque<>();
    private boolean thumbnailRunning = false;
    private HistogramService histogramService;

    // The photo the user wants now; a decode for any other photo is stale and gets skipped / cancelled
    private volatile String requestedPhotoId;
    // Thumbnail jobs run one after another (decode → upload → render → store), each chained on the previous one.

    // The photo in the thumbnail renderer's texture; only read and written inside the chain, so no re-decode
    // while the same photo is edited again and again
    private volatile String thumbnailPhotoId;
    // As-shot white balance of the photo in the thumbnail texture, null if it has none
    private volatile AsShotWhiteBalance.AsShot thumbnailAsShot;

    public EditingManager(TransactionRunner catalogTx, EditStateDao editStateDao, MetadataDao metadataDao, PhotoManager photoManager, ViewportRenderer viewportRenderer, ThumbnailRenderer thumbnailRenderer, ExecutorService cpuPool, ThumbnailService thumbnailService, LibrawService librawService) throws ExecutionException, InterruptedException {
        this.catalogTx = catalogTx;
        this.editStateDao = editStateDao;
        this.metadataDao = metadataDao;
        this.photoManager = photoManager;
        this.viewportRenderer = viewportRenderer;
        this.thumbnailRenderer = thumbnailRenderer;
        this.cpuPool = cpuPool;
        this.thumbnailService = thumbnailService;
        this.librawService = decoderThread.submit(LibrawService::new).get();
        photoManager.selectedPhoto().addListener((obs, oldVal, newVal) -> changeEditState(newVal));
        currentPhotoEntry.bind(photoManager.selectedPhoto());
        showOriginal.addListener((obs, was, on) -> {
            if (currentEditState.get() != null) {
                viewportRenderer.requestRender(effective(currentEditState.get()));
            }
        });
    }

    /**
     * JPEG/PNG/TIFF → RGBA8 in native memory (the caller frees it), shared by the viewport and the thumbnail chain.
     */
    private LibrawService.DecodedImage readImage(File imgPath) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(imgPath);
        if (bufferedImage == null) {
            throw new CineGradeException(ErrorCode.FILE_READ_FAILED, "Failed to read image file");
        }
        int w = bufferedImage.getWidth();
        int h = bufferedImage.getHeight();

        int[] pixels = bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null, 0, bufferedImage.getWidth());
        ByteBuffer buffer = MemoryUtil.memAlloc(w * h * 4).order(ByteOrder.nativeOrder());

        for (int argb : pixels) {
            buffer.put((byte) ((argb >> 16) & 0xFF)); // R
            buffer.put((byte) ((argb >> 8) & 0xFF)); // G
            buffer.put((byte) (argb & 0xFF)); // B
            buffer.put((byte) ((argb >> 24) & 0xFF)); // A
        }
        buffer.flip();

        return new LibrawService.DecodedImage(buffer, w, h, 8);
    }

    /**
     * Before: renders the photo with every setting at its default; the edit itself is untouched.
     */
    public void showBefore(boolean before) {
        showOriginal.set(before);
    }

    public BooleanProperty showOriginalProperty() {
        return showOriginal;
    }

    public ReadOnlyObjectProperty<PhotoEntry> currentPhotoProperty() {
        return currentPhotoEntry;
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading;
    }

    public ReadOnlyObjectProperty<Image> viewportImageProperty() {
        return viewportRenderer.frameProperty();
    }

    public ReadOnlyObjectProperty<Image> placeholderImageProperty() {
        return placeholder;
    }

    public ReadOnlyObjectProperty<EditState> currentEditStateProperty() {
        return currentEditState;
    }

    /**
     * Slider is moving: change the edit in memory and redraw the viewport. Nothing is saved.
     */
    public void preview(GradeParam param, float value) {
        EditState editState = currentEditState.get();
        if (editState == null) {
            return;
        }
        write(editState, param, value);
        viewportRenderer.requestRender(effective(editState));
    }

    /**
     * Slider released / value typed: change the edit, save it and refresh the thumbnail.
     */
    public void commit(GradeParam param, float value) {
        EditState editState = currentEditState.get();
        if (editState == null) {
            return;
        }
        write(editState, param, value);
        applySettings(photoManager.selectedPhoto().get());
    }

    /**
     * A moved temperature / tint slider turns the preset into CUSTOM (or back to AS_SHOT at the camera's values).
     */
    private void write(EditState editState, GradeParam param, float value) {
        param.write(editState, value);
        if (param.isWhiteBalance()) {
            editState.updateWhiteBalanceType();
        }
    }

    /**
     * White balance preset picked: sets the temperature / tint it stands for, saved like a slider commit.
     */
    public void setWhiteBalance(WhiteBalanceType type) {
        EditState editState = currentEditState.get();
        if (editState == null || type == editState.getWhiteBalance()) {
            return;
        }
        editState.applyWhiteBalance(type);
        applySettings(photoManager.selectedPhoto().get());
    }

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

    public void changeEditState(PhotoEntry photo) {
        // queued decodes of the previous photos are skipped, a running one is cancelled through LibRaw's callback
        requestedPhotoId = photo == null ? null : photo.getId();
        showOriginal.set(false);
        if (photo == null) {
            loading.set(false);
            currentEditState.set(null);
            viewportRenderer.clear();
            return;
        }
        loading.set(true);
        placeholder.set(null);
        thumbnailService.loadImage(photo)
                .thenAcceptAsync(imageOpt -> {
                    if (photo != currentPhotoEntry.get()) return;
                    placeholder.set(imageOpt.orElse(null));
                }, Platform::runLater)
                .thenCompose(v -> catalogTx.read(c -> editStateDao.findByPhotoId(photo.getId())))
                .exceptionally(ex -> {
                    logger.error("Failed to find edit state for photo: {}", photo.getFile(), ex);
                    return Optional.empty();
                })
                .thenAcceptAsync(found -> {
                    if (photo != currentPhotoEntry.get()) return;
                    currentEditState.set(found.orElseGet(() -> new EditState(photo.getId())));
                    viewportRenderer.requestRender(effective(currentEditState.get()));
                    showOnGpu(photo);
                }, Platform::runLater);
    }

    private void showOnGpu(PhotoEntry photo) {
        extractBytes(photo).thenCompose(decoded -> {
            if (!photo.getId().equals(requestedPhotoId)) {
                MemoryUtil.memFree(decoded.data());
                return CompletableFuture.completedFuture(null);
            }
            AsShotWhiteBalance.AsShot asShot = asShotOf(decoded);
            if (asShot != null) {
                Platform.runLater(() -> applyAsShot(photo, asShot));
            }
            return viewportRenderer.show(decoded)
                    .whenComplete((v, ex) -> MemoryUtil.memFree(decoded.data()))
                    .thenRun(() -> Platform.runLater(() -> {
                        if (photo == currentPhotoEntry.get()) {
                            loading.set(false);
                        }
                    }));
        }).exceptionally(ex -> {
            //cancelled
            if (ex instanceof CancellationException || ex.getCause() instanceof CancellationException) {
                return null;
            }
            logger.error("Failed to show photo on GPU: {}", photo.getFile(), ex);
            Platform.runLater(() -> {
                if (photo == currentPhotoEntry.get()) {
                    loading.set(false);
                }
            });
            return null;
        });
    }

    /**
     * The camera's white balance, known once the RAW is decoded: stored in the edit (and saved), and with AS_SHOT the
     * temperature / tint sliders move to it. The image itself is already decoded with it, so nothing visible changes.
     */
    private void applyAsShot(PhotoEntry photo, AsShotWhiteBalance.AsShot asShot) {
        EditState editState = currentEditState.get();
        if (photo != currentPhotoEntry.get() || editState == null) {
            return;
        }
        if (editState.getAsShotTemperature() == asShot.temperature() && editState.getAsShotTint() == asShot.tint()) {
            return;
        }
        EditState updated = new EditState(editState.getPhotoId(), editState);
        updated.applyAsShot(asShot.temperature(), asShot.tint());
        // a new object: the panel listens to the property and shows the new slider values
        currentEditState.set(updated);
        viewportRenderer.requestRender(effective(updated));
        saveEditState(new EditState(updated.getPhotoId(), updated));
    }

    private static AsShotWhiteBalance.AsShot asShotOf(LibrawService.DecodedImage decoded) {
        LibrawService.WhiteBalanceMultipliers multipliers = decoded.multipliers();
        if (multipliers == null) {
            return null;
        }
        return AsShotWhiteBalance.estimate(multipliers.asShot(), multipliers.daylight()).orElse(null);
    }

    /**
     * The edit with this photo's as-shot white balance filled in, a copy if anything changes.
     */
    private static EditState withAsShot(EditState editState, AsShotWhiteBalance.AsShot asShot) {
        if (asShot == null || (editState.getAsShotTemperature() == asShot.temperature()
                && editState.getAsShotTint() == asShot.tint())) {
            return editState;
        }
        EditState copy = new EditState(editState.getPhotoId(), editState);
        copy.applyAsShot(asShot.temperature(), asShot.tint());
        return copy;
    }

    public void pasteSettings() {
        if (copiedEditState.get() == null) {
            return;
        }

        EditState copied = new EditState(null, copiedEditState.get());
        List<PhotoEntry> copyWithoutSelected = new ArrayList<>(photoManager.selectedPhotos());
        copyWithoutSelected.remove(currentPhotoEntry.get());

        if (currentEditState.get() != null) {
            String photoId = currentEditState.get().getPhotoId();
            EditState pastedEditState = new EditState(photoId, copiedEditState.get());
            pastedEditState.applyAsShot(currentEditState.get().getAsShotTemperature(), currentEditState.get().getAsShotTint());
            currentEditState.set(pastedEditState);
            applySettings(currentPhotoEntry.get());
        }

        for (PhotoEntry entry : copyWithoutSelected) {
            EditState snapshot = new EditState(entry.getId(), copied);
            snapshot.applyAsShot(0.0f, 0.0f);

            saveEditState(snapshot);
            boolean edited = !snapshot.equals(snapshot.defaults());
            photoManager.updateIsEdited(entry, edited);
            updateThumbnail(entry, snapshot, false);
        }
    }

    /**
     * Ctrl+Shift+R: every setting of the photo back to its default, saved like a paste.
     */
    public void resetSettings() {
        if (currentEditState.get() == null) {
            return;
        }
        currentEditState.set(currentEditState.get().defaults());
        if (photoManager.selectedPhoto().get() != null) {
            applySettings(photoManager.selectedPhoto().get());
        }
    }

    public void applySettings(PhotoEntry photoEntry) {
        if (currentEditState.get() == null) {
            return;
        }
        // the DB and GPU threads read it later while the sliders keep changing the live one
        EditState snapshot = new EditState(currentEditState.get().getPhotoId(), currentEditState.get());

        saveEditState(snapshot);
        boolean edited = !snapshot.equals(snapshot.defaults());
        photoManager.updateIsEdited(photoEntry, edited);
        viewportRenderer.requestRender(effective(snapshot));
        updateThumbnail(photoEntry, snapshot, true);
    }

    /**
     * Queues a thumbnail job behind the previous ones, for any photo (open or not), so a paste to 20 photos decodes
     * and renders them one by one instead of 20 decodes at once. Pass a snapshot: it is read later on other threads.
     */
    private void updateThumbnail(PhotoEntry photoEntry, EditState editState, boolean urgent) {
        if (photoEntry == null) {
            return;
        }

        String id = photoEntry.getId();
        boolean waiting = thumbnailEdits.put(id, editState) != null;
        thumbnailPhotos.put(id, photoEntry);
        if (waiting) {
            if (urgent) {
                thumbnailQueue.remove(id);
                thumbnailQueue.addFirst(id);
            }
        } else if (urgent) {
            thumbnailQueue.addFirst(id);
        } else {
            thumbnailQueue.addLast(id);
        }

        pumpThumbnails();
    }

    private void pumpThumbnails() {
        if (thumbnailRunning || thumbnailQueue.isEmpty()) {
            return;
        }
        thumbnailRunning = true;
        String id = thumbnailQueue.poll();
        EditState editState = thumbnailEdits.remove(id);
        PhotoEntry photoEntry = thumbnailPhotos.remove(id);

        renderThumbnail(photoEntry, editState).whenCompleteAsync((v, ex) -> {
            if (ex != null) {
                thumbnailPhotoId = null;
                logger.warn("Failed to update thumbnail of {}", photoEntry.getFile(), ex);
            }
            thumbnailRunning = false;
            pumpThumbnails();
        }, Platform::runLater);
    }

    /**
     * One thumbnail job: decode + upload (skipped if the photo is already in the texture) → GPU render →
     * JPEG on the CPU pool → preview DB, then bumps the version so the filmstrip reloads it.
     */
    private CompletableFuture<Void> renderThumbnail(PhotoEntry photoEntry, EditState editState) {
        CompletableFuture<Void> loaded = photoEntry.getId().equals(thumbnailPhotoId)
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.supplyAsync(() -> decodeForThumbnail(photoEntry), cpuPool)
                .thenCompose(decoded -> {
                    thumbnailAsShot = asShotOf(decoded);
                    return thumbnailRenderer.load(decoded)
                            .whenComplete((v, ex) -> MemoryUtil.memFree(decoded.data()));
                })
                .thenRun(() -> thumbnailPhotoId = photoEntry.getId());

        return loaded
                // not saved here: a queued job's edit can be older than what is in the database by now
                .thenCompose(v -> thumbnailRenderer.render(withAsShot(editState, thumbnailAsShot)))
                .thenCompose(pixels -> pixels
                        .map(p -> thumbnailService.putPixels(photoEntry.getId(), p.bgra(), p.width(), p.height()).thenApply(v -> true))
                        .orElseGet(() -> CompletableFuture.completedFuture(false)))
                .thenAccept(stored -> {
                    if (stored) {
                        Platform.runLater(() -> photoEntry.getThumbnailVersion().set(photoEntry.getThumbnailVersion().get() + 1));
                    }
                });
    }

    /**
     * Decode for the thumbnail chain, on the CPU pool. RAWs get their own short-lived LibRaw handle: the viewport's
     * one belongs to the decoder thread. The chain runs one job at a time, so only one of these exists at once.
     */
    private LibrawService.DecodedImage decodeForThumbnail(PhotoEntry photoEntry) {
        File file = photoEntry.getFile();
        if (!photoEntry.isRaw()) {
            try {
                return readImage(file);
            } catch (IOException exception) {
                throw new CineGradeException(ErrorCode.FILE_READ_FAILED, exception, "Failed to read image file");
            }
        }
        try (LibrawService service = new LibrawService()) {
            // never canceled: a queued thumbnail is still wanted after the user moved on
            return service.decodeRaw(file, () -> false)
                    .orElseThrow(() -> new CineGradeException(ErrorCode.FILE_READ_FAILED, "Failed to decode raw image"));
        } catch (CineGradeException exception) {
            throw exception;
        } catch (Exception exception) {
            // close() of the LibRaw handle
            throw new CineGradeException(ErrorCode.FILE_READ_FAILED, exception, "Failed to decode raw image");
        }
    }

    /**
     * Copy of the edit with the bypassed sections set back to their defaults; this is what gets rendered.
     */
    private EditState effective(EditState editState) {
        if (showOriginal.get()) {
            return editState.defaults();
        }
        EditState defaults = editState.defaults();
        EditState copy = new EditState(editState.getPhotoId(), editState);
        for (GradeParam param : GradeParam.values()) {
            if (bypassed.contains(param.getSection())) {
                param.write(copy, param.read(defaults));
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
        catalogTx.execute(c -> editStateDao.upsert(editState)).exceptionally(ex -> {
            logger.error("Failed to save edit state to database", ex);
            return null;
        });
    }

    private CompletableFuture<LibrawService.DecodedImage> extractBytes(PhotoEntry photoEntry) {

        File imgPath = photoEntry.getFile();

        // asked before the decode starts (queued behind another one) and by LibRaw while it runs
        BooleanSupplier stale = () -> !photoEntry.getId().equals(requestedPhotoId);

        if (photoEntry.isRaw()) {
            // the decoder thread owns the one LibRaw handle: decodes run one by one, the stale ones are skipped
            return CompletableFuture.supplyAsync(() -> {
                if (stale.getAsBoolean()) {
                    throw new CancellationException("Photo changed before decoding " + photoEntry.getId());
                }
                Optional<LibrawService.DecodedImage> decodedImage = librawService.decodeRaw(imgPath, stale);
                if (decodedImage.isPresent()) {
                    return decodedImage.get();
                }
                if (stale.getAsBoolean()) {
                    throw new CancellationException("Photo changed while decoding " + photoEntry.getId());
                }
                logger.error("Failed to decode raw image: {}", imgPath);
                Platform.runLater(() -> photoEntry.missingPhotoProperty().set(true));
                throw new CineGradeException(ErrorCode.FILE_READ_FAILED, "Failed to decode raw image");
            }, decoderThread);
        } else {
            return CompletableFuture.supplyAsync(() -> {
                if (stale.getAsBoolean()) {
                    throw new CancellationException("Photo changed before decoding " + photoEntry.getId());
                }
                try {
                    return readImage(imgPath);
                } catch (IOException exception) {
                    logger.error("Failed to read image file", exception);
                    Platform.runLater(() -> photoEntry.missingPhotoProperty().set(true));
                    throw new CineGradeException(ErrorCode.FILE_READ_FAILED, exception, "Failed to read image file");
                }
            }, cpuPool);
        }
    }

    public CompletableFuture<Optional<PhotoMetadata>> loadMetadata(PhotoEntry photo) {
        return catalogTx.read(c -> metadataDao.findByPhotoId(photo.getId()));
    }

    public void autoWhiteBalance(ViewportNavigator.UV uv) {
        // TODO
    }

    public void setViewportSize(int w, int h) {
        viewportRenderer.resize(w, h);
    }

    public void setView(ViewportNavigator.ViewTransform view) {
        viewportRenderer.requestView(view);
    }

    @Override
    public void close() throws Exception {
        decoderThread.shutdown();
        try {
            if (!decoderThread.awaitTermination(10, TimeUnit.SECONDS)) {
                decoderThread.shutdownNow();
            }
        } catch (InterruptedException ex) {
            decoderThread.shutdownNow();
        }

        logger.debug("{} Executor was succesfully shutdown!", decoderThread.getClass().getName());
    }

    public void setHistogramService(HistogramService histogramService) {
        this.histogramService = histogramService;
        viewportRenderer.setHistogramDraw(histogramService::drawHistogram);
    }
}
