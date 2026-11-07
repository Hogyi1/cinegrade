package hu.elte.ik.thesis.cinegrade.infra.services.libraw;

import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public class PhotoDecoder implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(PhotoDecoder.class);
    private final LibrawService librawService;

    public PhotoDecoder() {
        this.librawService = new LibrawService();
    }

    public LibrawService.DecodedImage decode(PhotoEntry photo, BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Decoding cancelled " + photo.getId());
        }
        File file = photo.getFile();
        if (!photo.isRaw()) {
            try {
                return readImage(file);
            } catch (IOException ex) {
                throw new CineGradeException(ErrorCode.FILE_READ_FAILED, ex, "Failed to read image file");
            }
        }
        return librawService.decodeRaw(file, cancelled).orElseThrow(() -> cancelled.getAsBoolean()
                ? new CancellationException("Decoding cancelled " + photo.getId())
                : new CineGradeException(ErrorCode.FILE_READ_FAILED, "Failed to decode raw image"));
    }

    public LibrawService.DecodedImage decode(PhotoEntry photoEntry) {
        return decode(photoEntry, () -> false);
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

    @Override
    public void close() {
        librawService.close();
    }
}
