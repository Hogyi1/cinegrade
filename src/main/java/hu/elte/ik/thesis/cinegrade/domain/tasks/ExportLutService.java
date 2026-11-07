package hu.elte.ik.thesis.cinegrade.domain.tasks;

import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.objects.LutExportOption;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ExportRenderer;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class ExportLutService extends Service<Void> {

    private final ExecutorService ioPool;
    private final ExportRenderer exportRenderer;
    private final LutExportOption option;
    private final EditState editState;

    public ExportLutService(ExecutorService ioPool,
                            ExportRenderer exportRenderer,
                            LutExportOption option,
                            EditState editState) {
        this.ioPool = ioPool;
        this.exportRenderer = exportRenderer;
        this.option = option;
        this.editState = editState;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Generating LUT...");
                updateProgress(0f, 1f);
                exportRenderer.renderLut(editState, option.lutSize())
                        .thenAcceptAsync(floats -> {
                            try {
                                updateProgress(.75f, 1f);
                                exportTo(floats);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }, ioPool).join();
                return null;
            }
        };
    }

    private void exportTo(float[] data) throws IOException {
        Path part = option.destination().resolveSibling(option.destination().getFileName() + ".part");
        Files.deleteIfExists(part); // the stream doesn't truncate an old file

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                writer.write("LUT_3D_SIZE " + option.lutSize());
                writer.newLine();

                // 2. Iterate over the flat float[] array in steps of 3 (R, G, B)
                for (int i = 0; i < data.length; i += 3) {
                    String line = String.format(Locale.ROOT, "%.6f %.6f %.6f",
                            data[i], data[i + 1], data[i + 2]);
                    writer.write(line);
                    writer.newLine();
                }
            }
            Files.move(part, option.destination(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }
    }
}
