package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Decoded thumbnails for the cells, on top of the JPEG bytes of {@link ThumbnailService}.
 * <p>
 * Keyed by {@code id + thumbnailVersion}: when a photo gets a new thumbnail its version goes up, so the old
 * image is simply never asked for again and ages out of the LRU. FX thread only, except the decoding.
 */
public class ThumbnailImageCache {

    private static final Logger logger = LogManager.getLogger(ThumbnailImageCache.class);

    private final ThumbnailService thumbnailService;
    private final Executor decodePool;
    private final int decodeHeight;
    private final Map<String, Image> images;

    /**
     * @param thumbnailService source of the JPEG bytes
     * @param decodePool       where the JPEGs are decoded, never the FX thread
     * @param decodeHeight     height the image is decoded at, in pixels (cell height * output scale)
     * @param maxImages        how many decoded images are kept
     */
    public ThumbnailImageCache(ThumbnailService thumbnailService, Executor decodePool, int decodeHeight, int maxImages) {
        this.thumbnailService = thumbnailService;
        this.decodePool = decodePool;
        this.decodeHeight = decodeHeight;
        this.images = new LinkedHashMap<>(maxImages, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > maxImages;
            }
        };
    }

    /**
     * Hands the photo's thumbnail to {@code onLoaded} on the FX thread: at once from the cache, otherwise after it
     * is loaded and decoded. Gets {@code null} if the photo has no thumbnail. The caller checks that it still
     * shows the same photo.
     */
    public void load(PhotoEntry photo, Consumer<Image> onLoaded) {
        String key = key(photo);
        Image cached = images.get(key);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }

        thumbnailService.load(photo)
                .thenApplyAsync(bytes -> bytes
                        .map(jpeg -> new Image(new ByteArrayInputStream(jpeg), 0, decodeHeight, true, true))
                        .orElse(null), decodePool)
                .whenComplete((image, ex) -> Platform.runLater(() -> {
                    if (ex != null) {
                        logger.warn("Failed to load thumbnail of photo {}", photo.getId(), ex);
                        onLoaded.accept(null);
                        return;
                    }
                    if (image != null && !image.isError()) {
                        images.put(key, image);
                    }
                    onLoaded.accept(image);
                }));
    }

    public void clear() {
        images.clear();
    }

    private static String key(PhotoEntry photo) {
        return photo.getId() + ":" + photo.getThumbnailVersion().get();
    }
}
