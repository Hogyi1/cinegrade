package hu.elte.ik.thesis.cinegrade.app.managers.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.catalog.HistoryManager;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoMetadata;
import hu.elte.ik.thesis.cinegrade.domain.enums.WhiteBalanceType;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.objects.Preset;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import hu.elte.ik.thesis.cinegrade.engine.grade.WhiteBalancer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ViewportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.EditStateDao;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.catalog.MetadataDao;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.PhotoDecoder;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.ViewportNavigator;
import hu.elte.ik.thesis.cinegrade.javafx.ui.presenters.HistogramService;
import javafx.application.Platform;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.util.*;
import java.util.concurrent.*;

public class EditingManager implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(EditingManager.class);
    private final TransactionRunner catalogTx;
    private final EditStateDao editStateDao;
    private final MetadataDao metadataDao;
    private final PhotoManager photoManager;
    private final ViewportRenderer viewportRenderer;
    private final ThumbnailRefresher thumbnails;
    @Getter
    private final EditingStage viewModel;
    private final PhotoDecoder viewportDecoder;
    private final ExecutorService decoderThread = Executors.newSingleThreadExecutor();
    // Sections whose eye toggle is off: rendered with defaults, still saved with their values. FX thread only
    private final Set<GradeParam.Section> bypassed = EnumSet.noneOf(GradeParam.Section.class);
    @Getter
    private final HistoryManager historyManager;
    private EditState copiedEditState;
    private HistogramService histogramService;
    // The photo the user wants now; a decode for any other photo is stale and gets skipped / cancelled
    private volatile String requestedPhotoId;
    // The edit as last saved: preview changes the live one while a slider moves, so this is the "before" of a step.
    // Never changed, never made live. FX thread only
    private EditState lastCommitted;

    public EditingManager(HistoryManager historyManager, TransactionRunner catalogTx, EditStateDao editStateDao, MetadataDao metadataDao, PhotoManager photoManager, ViewportRenderer viewportRenderer, ThumbnailRefresher thumbnailRefresher, EditingStage editingStage) throws ExecutionException, InterruptedException {
        this.historyManager = historyManager;
        this.catalogTx = catalogTx;
        this.editStateDao = editStateDao;
        this.metadataDao = metadataDao;
        this.photoManager = photoManager;
        this.viewportRenderer = viewportRenderer;
        this.thumbnails = thumbnailRefresher;
        this.viewModel = editingStage;
        this.viewportDecoder = decoderThread.submit(PhotoDecoder::new).get();
        photoManager.selectedPhoto().addListener((obs, oldVal, newVal) -> changeEditState(newVal));
        viewModel.showOriginalProperty().addListener((obs, was, on) -> {
            if (viewModel.getEditState() != null) {
                viewportRenderer.requestRender(effective(viewModel.getEditState()));
            }
        });
    }

    /**
     * The text of a slider step, e.g. "Exposure +0.5 EV", "Temperature 6500 K", "Hue Red +12".
     */
    private static String describe(GradeParam param, float value) {
        String group = switch (param.name().substring(0, 4)) {
            case "HUE_" -> "Hue ";
            case "SAT_" -> "Saturation ";
            case "LUM_" -> "Luminance ";
            default -> "";
        };
        int decimals = param.getIncrement() < 0.1f ? 2 : param.getIncrement() < 1.0f ? 1 : 0;
        String number = String.format(Locale.ROOT, (param.getMinVal() < 0.0f ? "%+." : "%.") + decimals + "f", value);
        return (group + param.getName() + " " + number + " " + param.getUnit()).strip();
    }

    /**
     * Before: renders the photo with every setting at its default; the edit itself is untouched.
     */
    public void showBefore(boolean before) {
        viewModel.setShowOriginal(before);
    }

    /**
     * Slider is moving: change the edit in memory and redraw the viewport. Nothing is saved.
     */
    public void preview(GradeParam param, float value) {
        EditState editState = viewModel.getEditState();
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
        EditState editState = viewModel.getEditState();
        if (editState == null) {
            return;
        }
        EditState before = lastCommitted;
        PhotoEntry photo = photoManager.selectedPhoto().get();
        write(editState, param, value);
        applySettings(photo);
        recordEdit(describe(param, value), photo, before, lastCommitted);
    }

    /**
     * One undo step of one photo. Pass snapshots: they are kept, so nothing may change them later. A commit that
     * changed nothing (focus left the slider) is not a step.
     */
    private void recordEdit(String description, PhotoEntry photo, EditState before, EditState after) {
        if (photo == null || before == null || !photo.getId().equals(after.getPhotoId()) || before.equals(after)) {
            return;
        }
        historyManager.record(new HistoryManager.HistoryEntry(description, Set.of(photo.getId()),
                () -> restoreEdit(photo, before),
                () -> restoreEdit(photo, after)));
    }

    /**
     * Undo / redo of an edit. Always a copy: the live edit is changed by the sliders, the history's snapshot must not be.
     */
    private void restoreEdit(PhotoEntry entry, EditState editState) {
        EditState copy = new EditState(entry.getId(), editState);
        EditState current = viewModel.getEditState();
        if (entry == viewModel.getPhoto() && current != null && entry.getId().equals(current.getPhotoId())) {
            // an old snapshot of a photo that was not open can be missing the as-shot values
            copy.applyAsShot(current.getAsShotTemperature(), current.getAsShotTint());
            viewModel.setEditState(copy);
            applySettings(entry);
        } else {
            saveEditState(copy);
            boolean edited = !copy.equals(copy.defaults());
            photoManager.updateIsEdited(entry, edited);
            thumbnails.update(entry, copy, false);
        }
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
        EditState editState = viewModel.getEditState();
        if (editState == null || type == editState.getWhiteBalance()) {
            return;
        }
        EditState before = lastCommitted;
        PhotoEntry photo = photoManager.selectedPhoto().get();
        editState.applyWhiteBalance(type);
        applySettings(photo);
        recordEdit("White balance " + type, photo, before, lastCommitted);
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
        if (viewModel.getEditState() != null) {
            viewportRenderer.requestRender(effective(viewModel.getEditState()));
        }
    }

    public void changeEditState(PhotoEntry photo) {
        // queued decodes of the previous photos are skipped, a running one is cancelled through LibRaw's callback
        requestedPhotoId = photo == null ? null : photo.getId();
        viewModel.setShowOriginal(false);
        if (photo == null) {
            viewModel.setLoading(false);
            viewModel.setEditState(null);
            lastCommitted = null;
            viewportRenderer.clear();
            return;
        }
        viewModel.setLoading(true);
        viewModel.setPlaceholder(null);
        thumbnails.loadThumbnail(photo).thenAcceptAsync(imageOpt -> {
                    if (photo != viewModel.getPhoto()) return;
                    viewModel.setPlaceholder(imageOpt.orElse(null));
                }, Platform::runLater)
                .thenCompose(v -> catalogTx.read(c -> editStateDao.findByPhotoId(photo.getId())))
                .exceptionally(ex -> {
                    logger.error("Failed to find edit state for photo: {}", photo.getFile(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    return Optional.empty();
                })
                .thenAcceptAsync(found -> {
                    if (photo != viewModel.getPhoto()) return;
                    viewModel.setEditState(found.orElseGet(() -> new EditState(photo.getId())));
                    lastCommitted = new EditState(photo.getId(), viewModel.getEditState());
                    if (photo.isMissingPhoto()) {
                        viewportRenderer.clear();
                        viewModel.setLoading(false);
                        return;
                    }
                    viewportRenderer.requestRender(effective(viewModel.getEditState()));
                    showOnGpu(photo);
                }, Platform::runLater);
    }

    private void showOnGpu(PhotoEntry photo) {
        CompletableFuture.supplyAsync(() -> viewportDecoder.decode(photo, () -> !photo.getId().equals(requestedPhotoId)), decoderThread)
                .thenCompose(decoded -> {
                    if (!photo.getId().equals(requestedPhotoId)) {
                        MemoryUtil.memFree(decoded.data());
                        return CompletableFuture.completedFuture(null);
                    }
                    WhiteBalancer.WhiteBalance asShot = WhiteBalancer.asShotOf(decoded);
                    Platform.runLater(() -> applyAsShot(photo, asShot));
                    return viewportRenderer.show(decoded)
                            .whenComplete((v, ex) -> MemoryUtil.memFree(decoded.data()))
                            .thenRun(() -> Platform.runLater(() -> {
                                if (photo == viewModel.getPhoto()) {
                                    viewModel.setLoading(false);
                                }
                            }));
                }).exceptionally(ex -> {
                    if (ex instanceof CancellationException || ex.getCause() instanceof CancellationException) {
                        return null;
                    }
                    logger.error("Failed to show photo on GPU: {}", photo.getFile(), ex);
                    ErrorHandler.getInstance().handle(ex);
                    Platform.runLater(() -> {
                        if (photo == viewModel.getPhoto()) {
                            viewModel.setLoading(false);
                        }
                        // a corrupt file is not a missing one
                        if (!photo.getFile().exists()) {
                            photo.missingPhotoProperty().set(true);
                        }
                    });
                    return null;
                });
    }

    /**
     * The camera's white balance, known once the RAW is decoded: stored in the edit (and saved), and with AS_SHOT the
     * temperature / tint sliders move to it. The image itself is already decoded with it, so nothing visible changes.
     */
    private void applyAsShot(PhotoEntry photo, WhiteBalancer.WhiteBalance asShot) {
        EditState editState = viewModel.getEditState();
        if (photo != viewModel.getPhoto() || editState == null) {
            return;
        }
        EditState updated = WhiteBalancer.withAsShot(editState, asShot);
        if (updated == editState) {
            return;
        }
        // a new object: the panel listens to the property and shows the new slider values
        viewModel.setEditState(updated);
        viewportRenderer.requestRender(effective(updated));
        lastCommitted = new EditState(updated.getPhotoId(), updated);
        saveEditState(lastCommitted);
    }

    /**
     * Eyedropper: sets the white balance that turns the photo's color at this texture uv neutral, saved like a slider
     * commit. RAWs use the Kelvin temperature, other photos the offset slider.
     *
     * @return completes on the FX thread with false if nothing was set (the spot is near black or clipped)
     */
    public CompletableFuture<Boolean> pickWhiteBalance(ViewportNavigator.UV uv) {
        PhotoEntry photo = viewModel.getPhoto();
        EditState editState = viewModel.getEditState();
        if (photo == null || editState == null) {
            return CompletableFuture.completedFuture(false);
        }
        return viewportRenderer.sample(editState.defaults(), uv).thenApplyAsync(rgb -> {
            EditState current = viewModel.getEditState();
            if (rgb == null || photo != viewModel.getPhoto() || current == null) {
                return false;
            }
            Optional<WhiteBalancer.WhiteBalance> picked = WhiteBalancer.fromSample(rgb[0], rgb[1], rgb[2], current);
            if (picked.isEmpty()) {
                return false;
            }
            // a new object: the panel listens to the property and shows the new slider values
            EditState updated = new EditState(current.getPhotoId(), current);
            if (photo.isRaw()) {
                GradeParam.TEMPERATURE.write(updated, picked.get().temperature());
            } else {
                GradeParam.TEMPERATURE_OFFSET.write(updated, (float) (100.0 * GradeParam.TEMPERATURE.toSliderPosition(picked.get().temperature())));
            }
            GradeParam.TINT.write(updated, picked.get().tint());
            updated.updateWhiteBalanceType();
            EditState before = lastCommitted;
            viewModel.setEditState(updated);
            applySettings(photo);
            recordEdit(String.format(Locale.ROOT, "White balance %.0f K", picked.get().temperature()), photo, before, lastCommitted);
            return true;
        }, Platform::runLater).exceptionally(ex -> {
            ErrorHandler.getInstance().handle(ex);
            return false;
        });
    }

    public void pasteSettings() {
        if (copiedEditState == null) {
            return;
        }

        EditState copied = new EditState(null, copiedEditState);
        List<PhotoEntry> copyWithoutSelected = new ArrayList<>(photoManager.selectedPhotos());
        copyWithoutSelected.remove(viewModel.getPhoto());

        // one undo step for the whole paste: photo -> its edit before and after
        Map<PhotoEntry, EditState> before = new HashMap<>();
        Map<PhotoEntry, EditState> after = new HashMap<>();

        EditState current = viewModel.getEditState();
        if (current != null) {
            PhotoEntry photo = viewModel.getPhoto();
            if (lastCommitted != null) {
                before.put(photo, lastCommitted);
            }
            EditState pastedEditState = new EditState(current.getPhotoId(), copiedEditState);
            pastedEditState.applyAsShot(current.getAsShotTemperature(), current.getAsShotTint());
            viewModel.setEditState(pastedEditState);
            applySettings(photo);
            after.put(photo, lastCommitted);
        }

        // the other photos are not open: their "before" is in the database, read it before the paste overwrites it
        catalogTx.read(c -> {
            Map<PhotoEntry, EditState> saved = new HashMap<>();
            for (PhotoEntry entry : copyWithoutSelected) {
                saved.put(entry, editStateDao.findByPhotoId(entry.getId()).orElseGet(() -> new EditState(entry.getId())));
            }
            return saved;
        }).thenAcceptAsync(saved -> {
            before.putAll(saved);
            for (PhotoEntry entry : copyWithoutSelected) {
                EditState snapshot = new EditState(entry.getId(), copied);
                snapshot.applyAsShot(0.0f, 0.0f);

                saveEditState(snapshot);
                boolean edited = !snapshot.equals(snapshot.defaults());
                photoManager.updateIsEdited(entry, edited);
                thumbnails.update(entry, snapshot, false);
                after.put(entry, snapshot);
            }
            recordPaste(before, after);
        }, Platform::runLater).exceptionally(ex -> {
            ErrorHandler.getInstance().handle(ex);
            return null;
        });
    }

    private void recordPaste(Map<PhotoEntry, EditState> before, Map<PhotoEntry, EditState> after) {
        if (after.isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>();
        after.keySet().forEach(photo -> ids.add(photo.getId()));
        String text = after.size() > 1 ? "Paste settings (" + after.size() + " photos)" : "Paste settings";
        historyManager.record(new HistoryManager.HistoryEntry(text, ids,
                () -> before.forEach(this::restoreEdit),
                () -> after.forEach(this::restoreEdit)));
    }

    /**
     * Ctrl+Shift+R: every setting of the photo back to its default, saved like a paste.
     */
    public void resetSettings() {
        if (viewModel.getEditState() == null) {
            return;
        }
        EditState before = lastCommitted;
        PhotoEntry photo = photoManager.selectedPhoto().get();
        viewModel.setEditState(viewModel.getEditState().defaults());
        if (photo != null) {
            applySettings(photo);
            recordEdit("Reset settings", photo, before, lastCommitted);
        }
    }

    public void applyPreset(Preset preset) {
        PhotoEntry photo = viewModel.getPhoto();
        EditState current = viewModel.getEditState();
        if (photo == null || current == null) {
            return;
        }
        EditState before = lastCommitted;
        EditState next = new EditState(photo.getId(), current);
        preset.applyTo(next);
        viewModel.setEditState(next);
        applySettings(photo);
        recordEdit("Preset: " + preset.getName(), photo, before, lastCommitted);
    }

    public void applySettings(PhotoEntry photoEntry) {
        if (viewModel.getEditState() == null) {
            return;
        }
        // the DB and GPU threads read it later while the sliders keep changing the live one
        EditState snapshot = new EditState(viewModel.getEditState().getPhotoId(), viewModel.getEditState());
        lastCommitted = snapshot;

        saveEditState(snapshot);
        boolean edited = !snapshot.equals(snapshot.defaults());
        photoManager.updateIsEdited(photoEntry, edited);
        viewportRenderer.requestRender(effective(snapshot));
        thumbnails.update(photoEntry, snapshot, true);
    }

    /**
     * Copy of the edit with the bypassed sections set back to their defaults; this is what gets rendered.
     */
    private EditState effective(EditState editState) {
        if (viewModel.isShowOriginal()) {
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
        if (viewModel.getEditState() != null) {
            copiedEditState = new EditState(null, viewModel.getEditState());
            historyManager.notifyListeners("Copy settings", false);
        }
    }

    public void saveEditState(EditState editState) {
        catalogTx.execute(c -> editStateDao.upsert(editState)).exceptionally(ex -> {
            ErrorHandler.getInstance().handle(ex);
            return null;
        });
    }

    public CompletableFuture<HashMap<PhotoEntry, EditState>> loadEditStates(List<PhotoEntry> photos) {
        List<PhotoEntry> snapshot = List.copyOf(photos); // the selection is a live list
        return catalogTx.read(c -> {
            HashMap<PhotoEntry, EditState> states = new LinkedHashMap<>(); // keeps the selection order
            for (PhotoEntry photo : snapshot) {
                states.put(photo, editStateDao.findByPhotoId(photo.getId())
                        .orElseGet(() -> new EditState(photo.getId())));
            }
            return states;
        });
    }

    public CompletableFuture<Optional<PhotoMetadata>> loadMetadata(PhotoEntry photo) {
        return catalogTx.read(c -> metadataDao.findByPhotoId(photo.getId()));
    }

    public void setViewportSize(int w, int h) {
        viewportRenderer.resize(w, h);
    }

    public void setView(ViewportNavigator.ViewTransform view) {
        viewportRenderer.requestView(view);
    }

    @Override
    public void close() throws Exception {
        // the running decode stops, queued ones are skipped, then the handle closes on the thread that owns it
        requestedPhotoId = null;
        decoderThread.submit(viewportDecoder::close);
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
