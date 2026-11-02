// [AI-GENERATED]
// DEV ONLY: shader hot reload, remove before release (see Documentation/REMOVE_BEFORE_RELEASE.md)
package hu.elte.ik.thesis.cinegrade.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Watches the shader sources in the project folder and, whenever one is saved, rebuilds the shader on every GPU
 * worker and calls {@code onReloaded} (re-render the viewport). A shader that does not compile is only logged, the
 * previous one keeps running.
 * <p>
 * Reads {@code src/main/resources/shaders} directly (not the copy on the classpath), so it only works when the app is
 * started from the project root, e.g. from the IDE. Anywhere else it does nothing.
 */
public class ShaderHotReload implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(ShaderHotReload.class);
    private static final Path SHADER_DIR = Path.of("src", "main", "resources", "shaders");
    private static final String VERTEX = "vertex.vert";
    private static final String FRAGMENT = "fragment.frag";

    private final List<GpuWorker> workers;
    private final Runnable onReloaded;
    private WatchService watchService;

    public ShaderHotReload(List<GpuWorker> workers, Runnable onReloaded) {
        this.workers = workers;
        this.onReloaded = onReloaded;

        if (!Files.isDirectory(SHADER_DIR)) {
            logger.debug("Shader hot reload off: {} not found", SHADER_DIR.toAbsolutePath());
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            SHADER_DIR.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
        } catch (IOException ex) {
            logger.warn("Shader hot reload off: cannot watch {}", SHADER_DIR, ex);
            return;
        }

        Thread thread = new Thread(this::watch, "cinegrade-shader-hot-reload");
        thread.setDaemon(true);
        thread.start();
        logger.info("Shader hot reload on: watching {}", SHADER_DIR.toAbsolutePath());
    }

    private void watch() {
        try {
            while (true) {
                WatchKey key = watchService.take();
                // editors save in several steps (truncate, write, rename): let them finish, then take all events
                Thread.sleep(150);
                boolean shaderChanged = key.pollEvents().stream()
                        .map(event -> event.context().toString())
                        .anyMatch(name -> name.equals(VERTEX) || name.equals(FRAGMENT));
                key.reset();
                if (shaderChanged) {
                    reload();
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException ex) {
            // closed
        }
    }

    private void reload() {
        String vertexSource;
        String fragmentSource;
        try {
            vertexSource = Files.readString(SHADER_DIR.resolve(VERTEX));
            fragmentSource = Files.readString(SHADER_DIR.resolve(FRAGMENT));
        } catch (IOException ex) {
            logger.warn("Shader hot reload: cannot read the sources", ex);
            return;
        }

        CompletableFuture<?>[] reloads = workers.stream()
                .map(worker -> worker.reloadShader(vertexSource, fragmentSource))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(reloads).thenRun(() -> {
            if (((CompletableFuture<Boolean>) reloads[0]).join()) {
                logger.info("Shader reloaded");
                onReloaded.run();
            }
        });
    }

    @Override
    public void close() throws IOException {
        if (watchService != null) {
            watchService.close();
        }
    }
}
