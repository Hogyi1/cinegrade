// [CONTAINS AI-GENERATED CODE: share (rework), finish, fail, deleteFolder]
package hu.elte.ik.thesis.cinegrade.app.managers.social;

import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.tasks.TaskManager;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.enums.PhotoExtension;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.domain.objects.*;
import hu.elte.ik.thesis.cinegrade.domain.tasks.ExportBatchService;
import hu.elte.ik.thesis.cinegrade.domain.tasks.ExportLutService;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ExportRenderer;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.PresetService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Prepares a preset for the web upload form: the .cgedit, a LUT and a before/after pair of the open photo,
 * all in a temp folder. The finished {@link ShareDraft} is what DesktopBridge.shareDraft() hands to the page.
 */
public class ShareManager {

    private static final Logger logger = LogManager.getLogger(ShareManager.class);
    // [AI-GENERATED] 33 is the usual .cube size; JPEG quality kept modest so full-size photos stay under the
    // server's 15 MB image limit
    private static final int LUT_SIZE = 33;
    private static final float JPEG_QUALITY = 0.8f;

    private final PresetService presetService;
    private final ExecutorService ioPool, cpuPool;
    private final ExportRenderer exportRenderer;
    private final TaskManager taskManager;
    private final ObjectProperty<ShareDraft> draftProperty = new SimpleObjectProperty<>();

    public ShareManager(PresetService presetService, ExecutorService ioPool, ExecutorService cpuPool, TaskManager taskManager, ExportRenderer exportRenderer) {
        this.presetService = presetService;
        this.ioPool = ioPool;
        this.cpuPool = cpuPool;
        this.taskManager = taskManager;
        this.exportRenderer = exportRenderer;
    }

    public ShareDraft getDraft() {
        return draftProperty.get();
    }

    // [AI-GENERATED METHOD]
    /**
     * Exports everything in the background; {@code onReady} runs on the FX thread once all of it is written.
     * Before = the photo with default settings, after = the defaults with only this preset applied, the same
     * as applying the preset in the editor, so the post shows the preset itself and not other edits.
     *
     * @param current the open photo's edit state; only its camera white balance (as shot) is used
     */
    public void share(Preset preset, PhotoEntry photo, EditState current, Consumer<ShareDraft> onReady) {
        if (preset == null || photo == null || current == null) {
            return;
        }
        Path folder;
        try {
            folder = Files.createTempDirectory("cinegrade-share-");
        } catch (IOException ex) {
            ErrorHandler.getInstance().handle(new CineGradeException(ErrorCode.FILE_WRITE_FAILED, ex, "share folder"));
            return;
        }

        EditState before = current.defaults();
        EditState after = current.defaults();
        preset.applyTo(after);

        Path cgedit;
        try {
            cgedit = presetService.write(preset, folder.resolve("preset.cgedit"));
        } catch (CineGradeException ex) {
            fail(folder, ex);
            return;
        }
        Path lut = folder.resolve("preset.cube");
        PhotoExportOptions beforeOptions = new PhotoExportOptions(folder, PhotoExtension.JPEG, JPEG_QUALITY, null, null, "before");
        PhotoExportOptions afterOptions = new PhotoExportOptions(folder, PhotoExtension.JPEG, JPEG_QUALITY, null, null, "after");

        List<Service<?>> services = List.of(
                new ExportLutService(ioPool, exportRenderer, new LutExportOption(LUT_SIZE, lut, false), after),
                new ExportBatchService(cpuPool, ioPool, exportRenderer, beforeOptions, new HashMap<>(Map.of(photo, before))),
                new ExportBatchService(cpuPool, ioPool, exportRenderer, afterOptions, new HashMap<>(Map.of(photo, after))));

        ShareDraft draft = new ShareDraft(preset.getName(), preset.getDescription(),
                beforeOptions.getExportPath(), afterOptions.getExportPath(), lut, cgedit);

        // The draft only counts once all three are done; the handlers run on the FX thread, so a plain counter is fine
        int[] remaining = {services.size()};
        boolean[] failed = {false};
        for (Service<?> service : services) {
            taskManager.configure(service);   // toast and error reporting
            service.setOnSucceeded(e -> {
                if (--remaining[0] == 0 && !failed[0]) {
                    finish(draft, folder, onReady);
                }
            });
            service.setOnFailed(e -> {
                if (!failed[0]) {
                    failed[0] = true;
                    deleteFolder(folder);
                }
            });
            service.setOnCancelled(service.getOnFailed());
            service.start();
        }
    }

    // [AI-GENERATED METHOD]
    // ExportBatchService reports a failed photo to ErrorHandler and still ends as SUCCEEDED, so check the files
    private void finish(ShareDraft draft, Path folder, Consumer<ShareDraft> onReady) {
        for (Path file : List.of(draft.before(), draft.after(), draft.lut(), draft.cgedit())) {
            if (!Files.isRegularFile(file)) {
                fail(folder, new CineGradeException(ErrorCode.EXPORT_RENDER_FAILED, "share: " + file.getFileName()));
                return;
            }
        }
        ShareDraft previous = draftProperty.get();
        draftProperty.set(draft);
        if (previous != null) {
            deleteFolder(previous.before().getParent());
        }
        logger.info("Share draft ready in {}", folder);
        onReady.accept(draft);
    }

    // [AI-GENERATED METHOD]
    private void fail(Path folder, CineGradeException ex) {
        deleteFolder(folder);
        ErrorHandler.getInstance().handle(ex);
    }

    // [AI-GENERATED METHOD]
    private static void deleteFolder(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    logger.warn("Couldn't delete {}", path);
                }
            });
        } catch (IOException ex) {
            logger.warn("Couldn't clean up the share folder {}", folder);
        }
    }

    public record ShareDraft(String title, String description, Path before, Path after, Path lut, Path cgedit) {}
}
