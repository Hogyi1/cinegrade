package hu.elte.ik.thesis.cinegrade.domain.tasks;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoExportOptions;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.enums.PhotoExtension;
import hu.elte.ik.thesis.cinegrade.domain.enums.WhiteBalanceType;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.engine.grade.WhiteBalancer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ExportRenderer;
import hu.elte.ik.thesis.cinegrade.engine.renderer.ThumbnailRenderer;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import hu.elte.ik.thesis.cinegrade.infra.services.libraw.PhotoDecoder;
import hu.elte.ik.thesis.cinegrade.infra.utils.file.FileUtils;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static hu.elte.ik.thesis.cinegrade.infra.utils.file.FileUtils.isBlank;

public class ExportBatchService extends Service<Void> {

    private final ExecutorService cpuPool;
    private final ExecutorService ioPool;
    private final ExportRenderer exportRenderer;
    private final PhotoExportOptions options;
    private final LinkedHashMap<PhotoEntry, EditState> photos;
    private final AtomicInteger stepCount = new AtomicInteger(0);
    private Path lastWritten;

    public ExportBatchService(ExecutorService cpuPool,
                              ExecutorService ioPool,
                              ExportRenderer exportRenderer,
                              PhotoExportOptions options,
                              HashMap<PhotoEntry, EditState> photos) {
        this.cpuPool = cpuPool;
        this.ioPool = ioPool;
        this.exportRenderer = exportRenderer;
        this.options = options;
        this.photos = new LinkedHashMap<>(photos);
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() throws IOException {
                int totalSteps = photos.size();
                stepCount.set(0);
                updateProgress(0, totalSteps);
                updateMessage("Starting export batch...");

                for (Map.Entry<PhotoEntry, EditState> entry : photos.entrySet()) {
                    if (isCancelled()) return null;
                    updateProgress(stepCount.getAndIncrement(), totalSteps);
                    updateMessage("Exporting photo " + entry.getKey().getFileName());

                    try (PhotoDecoder photoDecoder = new PhotoDecoder()) {
                        Optional<BufferedImage> image = CompletableFuture.supplyAsync(() -> photoDecoder.decode(entry.getKey(), this::isCancelled), cpuPool)
                                .thenCompose(decodedPhoto -> render(entry.getValue(), decodedPhoto)
                                        .whenComplete((res, ex) -> {
                                            MemoryUtil.memFree(decodedPhoto.data());
                                        }))
                                .exceptionally(ex -> {
                                    ErrorHandler.getInstance().handle(ex);
                                    return Optional.empty();
                                }).join();

                        if (isCancelled()) return null;
                        if (image.isPresent()) {

                            String base = options.fileName() != null && !options.fileName().isBlank()
                                    ? options.fileName() : entry.getKey().getFileNameWithoutExtension();
                            String name = (isBlank(options.namingPrefix()) ? "" : options.namingPrefix() + "_") + base + (isBlank(options.namingPostfix()) ? "" : "_" + options.namingPostfix())
                                    + "." + options.extension().getExtension().toLowerCase();

                            Path uniqueTarget = FileUtils.uniqueTarget(options.exportDirectory(), name);
                            CompletableFuture.supplyAsync(() -> {
                                try {
                                    exportTo(image.get(), uniqueTarget, options.extension(), options.compressionQuality());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                return null;
                            }, ioPool).join();
                        }
                    } catch (Exception ex) {
                        ErrorHandler.getInstance().handle(ex);
                    } finally {
                        if (lastWritten != null && Files.exists(lastWritten)) {
                            Files.deleteIfExists(lastWritten);
                        }
                    }
                }
                return null;
            }
        };
    }

    private CompletableFuture<Optional<BufferedImage>> render(EditState edit, LibrawService.DecodedImage image) {
        if (edit.getWhiteBalance() == WhiteBalanceType.AS_SHOT) {
            WhiteBalancer.WhiteBalance whiteBalance = WhiteBalancer.asShotOf(image);
            edit.applyAsShot(whiteBalance != null ? whiteBalance.temperature() : 0.0f, whiteBalance != null ? whiteBalance.tint() : 0.0f);
        }

        if (isJpeg(options.extension())) {
            return exportRenderer.render8(edit, image).thenApply(p -> p.map(this::fromPixel));
        }
        return exportRenderer.render16(edit, image).thenApply(p -> p.map(this::fromPixel));
    }


    private void exportTo(BufferedImage image, Path destination, PhotoExtension extension, float quality) throws IOException {
        Path part = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(part); // the stream doesn't truncate an old file

        boolean isJpeg = extension.equals(PhotoExtension.JPEG);
        lastWritten = part;

        try {
            ImageWriter writer = writerFor(extension);
            try (ImageOutputStream out = ImageIO.createImageOutputStream(part.toFile())) {
                writer.setOutput(out);

                ImageWriteParam param = writer.getDefaultWriteParam();
                if (isJpeg) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            Files.move(part, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.deleteIfExists(lastWritten);
            throw new CineGradeException(ErrorCode.EXPORT_RENDER_FAILED, ex, "Failed to export photo");
        }
    }

    private ImageWriter writerFor(PhotoExtension extension) {
        java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(extension.getExtension());
        ImageWriter chosen = writers.next();
        while (writers.hasNext() && !chosen.getClass().getName().startsWith("com.sun.imageio")) {
            chosen = writers.next();
        }
        return chosen;
    }

    private BufferedImage fromPixel(ExportRenderer.Pixels16 pixels) {
        WritableRaster raster = Raster.createInterleavedRaster(
                new DataBufferUShort(pixels.rgb(), pixels.rgb().length),
                pixels.width(),
                pixels.height(),
                pixels.width() * 3, // Jump a row
                3, // Per pixel R,G,B
                new int[]{0, 1, 2}, // R, G, B band offsets, alpha skipped
                null
        );

        ColorModel cm = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                false,
                false,
                Transparency.OPAQUE,
                DataBuffer.TYPE_USHORT
        );

        return new BufferedImage(cm, raster, false, null);
    }

    private BufferedImage fromPixel(ThumbnailRenderer.Pixels pixels) {
        WritableRaster raster = Raster.createInterleavedRaster(
                new DataBufferByte(pixels.bgra(), pixels.bgra().length),
                pixels.width(),
                pixels.height(),
                pixels.width() * 4, // Jump a row
                4, // Per pixel B,G,R,A
                new int[]{2, 1, 0}, // B, G, R band offsets, alpha skipped
                null
        );

        ColorModel cm = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                false,
                false,
                Transparency.OPAQUE,
                DataBuffer.TYPE_BYTE
        );

        return new BufferedImage(cm, raster, false, null);
    }

    private boolean isJpeg(PhotoExtension extension) {
        return extension.equals(PhotoExtension.JPEG) || extension.equals(PhotoExtension.JPG);
    }
}
