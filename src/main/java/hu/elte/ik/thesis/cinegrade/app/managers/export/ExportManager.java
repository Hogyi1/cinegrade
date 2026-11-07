package hu.elte.ik.thesis.cinegrade.app.managers.export;

import hu.elte.ik.thesis.cinegrade.app.managers.concurrency.tasks.TaskManager;
import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.objects.LutExportOption;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoExportOptions;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.domain.tasks.ExportBatchService;
import hu.elte.ik.thesis.cinegrade.domain.tasks.ExportLutService;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ExportRenderer;
import javafx.application.Platform;
import lombok.Getter;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

public class ExportManager implements AutoCloseable {

    private final ExportRenderer exportRenderer;
    private final EditingManager editingManager;
    private final TaskManager taskManager;
    private final ThreadPoolExecutor ioPool;
    private final ThreadPoolExecutor cpuPool;
    @Getter
    private final ExportStage exportStage;

    public ExportManager(ExportRenderer exportRenderer,
                         EditingManager editingManager,
                         TaskManager taskManager,
                         ThreadPoolExecutor ioPool,
                         ThreadPoolExecutor cpuPool,
                         ExportStage exportStage) {
        this.exportRenderer = exportRenderer;
        this.editingManager = editingManager;
        this.taskManager = taskManager;
        this.ioPool = ioPool;
        this.cpuPool = cpuPool;
        this.exportStage = exportStage;
    }

    public void exportPhotos() {
        List<PhotoEntry> photos = exportStage.photosInScope();
        PhotoExportOptions options = exportStage.snapshotOfPhotoOption();
        if (options == null) return;
        editingManager.loadEditStates(photos)
                .thenAcceptAsync(map -> {
                    ExportBatchService service = new ExportBatchService(cpuPool, ioPool, exportRenderer, options, map);
                    taskManager.configure(service);

                    service.setOnSucceeded(event -> {
                        try {
                            Desktop.getDesktop().open(new File(options.exportDirectory().toUri()));
                        } catch (IOException e) {
                            ErrorHandler.getInstance().handle(new CineGradeException(ErrorCode.EXPORT_INVALID_SETTINGS, "Failed to open export directory"));
                        }
                    });

                    service.start();
                }, Platform::runLater)
                .exceptionally(throwable -> {
                    ErrorHandler.getInstance().handle(new CineGradeException(ErrorCode.EXPORT_QUEUE_ERROR, "Export failed"));
                    return null;
                });
    }

    public void exportLut() {
        LutExportOption options = exportStage.snapshotOfLutOption();
        EditState old = editingManager.getViewModel().currentEditStateProperty().get();
        if (old == null) throw new CineGradeException(ErrorCode.LUT_GENERATION_FAILED, "No EditState is loaded");
        EditState editState = new EditState(null, old);
        if (options != null && options.leaveOutExp()) {
            editState.setExposure(0);
            editState.setTemperature(old.getNeutralTemperature());
            editState.setTemperatureOffset(0);
            editState.setTint(old.getAsShotTint());
        }

        ExportLutService service = new ExportLutService(ioPool, exportRenderer, options, editState);
        taskManager.configure(service);
        service.start();
    }

    @Override
    public void close() {
    }
}
