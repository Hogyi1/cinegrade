// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.app.managers.editing;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.engine.grade.WhiteBalancer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ThumbnailRenderer;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.PhotoDecoder;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Keeps the graded thumbnails up to date. Jobs run one after another (decode → upload → render → store), so a paste to
 * 20 photos decodes them one by one instead of 20 at once. {@link #update} is called on the FX thread.
 */
public class ThumbnailRefresher {

    private static final Logger logger = LogManager.getLogger(ThumbnailRefresher.class);

    private final ThumbnailRenderer thumbnailRenderer;
    private final ThumbnailService thumbnailService;
    private final ExecutorService cpuPool;
    private final Map<String, EditState> edits = new HashMap<>();
    private final Map<String, PhotoEntry> photos = new HashMap<>();
    private final Deque<String> queue = new ArrayDeque<>();
    private boolean running = false;

    // The photo in the thumbnail renderer's texture; only read and written inside the chain, so no re-decode
    // while the same photo is edited again and again
    private volatile String loadedPhotoId;
    // As-shot white balance of the photo in the thumbnail texture, null if it has none
    private volatile WhiteBalancer.WhiteBalance loadedAsShot;

    public ThumbnailRefresher(ThumbnailRenderer thumbnailRenderer, ThumbnailService thumbnailService, ExecutorService cpuPool) {
        this.thumbnailRenderer = thumbnailRenderer;
        this.thumbnailService = thumbnailService;
        this.cpuPool = cpuPool;
    }

    public CompletableFuture<Optional<Image>> loadThumbnail(PhotoEntry entry) {
        return thumbnailService.loadImage(entry);
    }

    /**
     * Queues a thumbnail job behind the previous ones, for any photo (open or not). A photo that is already waiting
     * only gets its edit replaced. Pass a snapshot: it is read later on other threads.
     *
     * @param urgent goes to the front of the queue (the photo being edited)
     */
    public void update(PhotoEntry photoEntry, EditState editState, boolean urgent) {
        if (photoEntry == null) {
            return;
        }

        String id = photoEntry.getId();
        boolean waiting = edits.put(id, editState) != null;
        photos.put(id, photoEntry);
        if (waiting) {
            if (urgent) {
                queue.remove(id);
                queue.addFirst(id);
            }
        } else if (urgent) {
            queue.addFirst(id);
        } else {
            queue.addLast(id);
        }

        pump();
    }

    private void pump() {
        if (running || queue.isEmpty()) {
            return;
        }
        running = true;
        String id = queue.poll();
        EditState editState = edits.remove(id);
        PhotoEntry photoEntry = photos.remove(id);

        render(photoEntry, editState).whenCompleteAsync((v, ex) -> {
            if (ex != null) {
                loadedPhotoId = null;
                logger.warn("Failed to update thumbnail of {}", photoEntry.getFile(), ex);
            }
            running = false;
            pump();
        }, Platform::runLater);
    }

    /**
     * One thumbnail job: decode + upload (skipped if the photo is already in the texture) → GPU render →
     * JPEG on the CPU pool → preview DB, then bumps the version so the filmstrip reloads it.
     */
    private CompletableFuture<Void> render(PhotoEntry photoEntry, EditState editState) {
        CompletableFuture<Void> loaded = photoEntry.getId().equals(loadedPhotoId)
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.supplyAsync(() -> {
                    // its own short-lived LibRaw handle: the pool can use another thread for the next job.
                    // Never cancelled: a queued thumbnail is still wanted after the user moved on
                    try (PhotoDecoder decoder = new PhotoDecoder()) {
                        return decoder.decode(photoEntry);
                    }
                }, cpuPool)
                .thenCompose(decoded -> {
                    loadedAsShot = WhiteBalancer.asShotOf(decoded);
                    return thumbnailRenderer.load(decoded)
                            .whenComplete((v, ex) -> MemoryUtil.memFree(decoded.data()));
                })
                .thenRun(() -> loadedPhotoId = photoEntry.getId());

        return loaded
                // not saved here: a queued job's edit can be older than what is in the database by now
                .thenCompose(v -> thumbnailRenderer.render(WhiteBalancer.withAsShot(editState, loadedAsShot)))
                .thenCompose(pixels -> pixels
                        .map(p -> thumbnailService.putPixels(photoEntry.getId(), p.bgra(), p.width(), p.height())
                                .thenApply(v -> true))
                        .orElseGet(() -> CompletableFuture.completedFuture(false)))
                .thenAccept(stored -> {
                    if (stored) {
                        Platform.runLater(() -> photoEntry.getThumbnailVersion().set(photoEntry.getThumbnailVersion().get() + 1));
                    }
                });
    }
}
