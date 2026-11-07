package hu.elte.ik.thesis.cinegrade.domain.tasks;

import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

public class ThumbnailBatchService extends Service<Void> {

    private static final Logger logger = LogManager.getLogger(ThumbnailBatchService.class);
    private final List<PhotoEntry> photos;
    private final BiFunction<PhotoEntry, BooleanSupplier, CompletableFuture<Void>> renderOne;
    private final AtomicInteger stepCounter = new AtomicInteger(0);

    public ThumbnailBatchService(List<PhotoEntry> photos, BiFunction<PhotoEntry, BooleanSupplier, CompletableFuture<Void>> renderOne) {
        this.photos = List.copyOf(photos);
        this.renderOne = renderOne;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int totalSteps = photos.size();
                stepCounter.set(0);

                updateProgress(0, totalSteps);
                for (int i = 0; i < totalSteps; i++) {
                    if (isCancelled()) {
                        return null;
                    }
                    PhotoEntry photo = photos.get(i);
                    updateMessage(String.format("Updating thumbnails %d / %d", i + 1, totalSteps));

                    CompletableFuture<Void> done = new CompletableFuture<>();
                    Platform.runLater(() -> renderOne.apply(photo, this::isCancelled).whenComplete((v, ex) -> {
                        if (ex != null) {
                            done.completeExceptionally(ex);
                        } else {
                            done.complete(null);
                        }
                    }));

                    try {
                        done.get();
                    } catch (InterruptedException ex) {
                        // cancel() interrupts the wait
                        if (isCancelled()) {
                            return null;
                        }
                        throw ex;
                    } catch (ExecutionException ex) {
                        // one broken photo does not stop the batch
                        logger.warn("Thumbnail of {} was not updated", photo.getFileName(), ex.getCause());
                    }
                    updateProgress(stepCounter.incrementAndGet(), totalSteps);
                }
                updateMessage(String.format("Updated %d thumbnails", totalSteps));
                return null;
            }
        };
    }
}
