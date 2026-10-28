package hu.elte.ik.thesis.cinegrade.infra.services.libraw;

import com.sun.jna.Pointer;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Optional;

public class LibrawService implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(LibrawService.class);
    private final LibRaw libRaw;
    private final Pointer handler;
    private volatile boolean closed = false;

    public LibrawService() {
        this.libRaw = LibRaw.INSTANCE;

        this.handler = libRaw.libraw_init(0);
        if (this.handler == null) {
            logger.error("Failed to initialize native LibRaw context");
            throw new CineGradeException(ErrorCode.REQ_LIBRAW_LOAD_FAILED, "Failed to initialize LibRaw native handler");
        }
    }

    public Optional<RawThumbnail> extractThumbnail(File rawFile) {
        if (closed) return Optional.empty();

        LibRaw.ProcessedImage.ByReference thumbnail;
        if (handler == null) {
            logger.error("Failed to initialize libraw");
            return Optional.empty();
        }

        try {
            int err = libRaw.libraw_open_file(handler, rawFile.getAbsolutePath());
            if (err != 0) {
                logger.error("Failed to open raw file: {}", rawFile.getAbsolutePath());
                return Optional.empty();
            }

            int[] errc = {0};

            libRaw.libraw_unpack_thumb(handler);
            thumbnail = libRaw.libraw_dcraw_make_mem_thumb(handler, errc);

            if (thumbnail != null) {
                thumbnail.read();
                // Thumbnails are small: one heap copy, nothing to free
                byte[] jpegBytes = thumbnail.copyPixelBytes();

                if (thumbnail.type == 2) {
                    logger.debug("Embedded JPEG thumbnail extracted: {} bytes ({} KB)",
                            thumbnail.data_size, thumbnail.data_size / 1024);
                } else {
                    logger.debug("{}x{} bitmap thumbnail decoded, {} bits, {} bytes",
                            (int) thumbnail.width, (int) thumbnail.height, (int) thumbnail.bits, thumbnail.data_size);
                }
                libRaw.libraw_dcraw_clear_mem(thumbnail.getPointer());
                return Optional.of(new RawThumbnail(jpegBytes, thumbnail.type,  thumbnail.width, thumbnail.height,  thumbnail.colors, thumbnail.bits));
            }
        } catch (Exception e) {
            throw new CineGradeException(ErrorCode.LIBRAW_THUMBNAIL_EXTRACTION_FAILED, e);
        } finally {
            libRaw.libraw_recycle(handler);
        }

         return Optional.empty();
    }

    /**
     * Decodes a RAW file into 16-bit sRGB pixels.
     * <p>
     * The returned {@link DecodedRaw#data()} is native memory owned by the caller: free it with
     * {@code MemoryUtil.memFree} once it was uploaded (the GC never frees it).
     * <p>
     * Not thread-safe: one LibrawService (one native handle) per thread, like {@code ThumbnailFactory} does.
     *
     * @param rawFile the RAW file
     * @return the decoded image, or empty if LibRaw could not open or process the file
     */
    public Optional<DecodedRaw> decodeRaw(File rawFile) {
        if (closed) return Optional.empty();

        try {
            libRaw.libraw_set_output_bps(handler, LibRawOptions.OutputBits.BITS_16.value);
            libRaw.libraw_set_demosaic(handler, LibRawOptions.Demosaic.PPG.value);
            libRaw.libraw_set_output_color(handler, LibRawOptions.OutputColor.SRGB.value);
            libRaw.libraw_set_no_auto_bright(handler, LibRawOptions.Toggle.ON.value);
            libRaw.libraw_set_use_camera_wb(handler, LibRawOptions.Toggle.OFF.value);

            libRaw.libraw_set_user_flip(handler, LibRawOptions.Flip.FROM_FILE.value);

            int err = libRaw.libraw_open_file(handler, rawFile.getAbsolutePath());
            if (err != 0) {
                logger.error("Failed to open raw file (code {}): {}", err, rawFile.getAbsolutePath());
                return Optional.empty();
            }

            err = libRaw.libraw_unpack(handler);
            if (err != 0) {
                logger.error("Failed to unpack raw file (code {}): {}", err, rawFile.getAbsolutePath());
                return Optional.empty();
            }

            err = libRaw.libraw_dcraw_process(handler);
            if (err != 0) {
                logger.error("Failed to process raw file (code {}): {}", err, rawFile.getAbsolutePath());
                return Optional.empty();
            }

            int[] errc = {0};
            LibRaw.ProcessedImage.ByReference image = libRaw.libraw_dcraw_make_mem_image(handler, errc);
            if (image == null) {
                logger.error("Failed to create the memory image (code {}): {}", errc[0], rawFile.getAbsolutePath());
                return Optional.empty();
            }

            try {
                image.read();
                ByteBuffer pixels = image.copyPixelsNative();
                logger.debug("{}x{} decoded, {} bits, {} bytes", (int) image.width, (int) image.height,
                        (int) image.bits, image.data_size);
                return Optional.of(new DecodedRaw(pixels, image.width, image.height, image.bits));
            } finally {
                libRaw.libraw_dcraw_clear_mem(image.getPointer());
            }
        } finally {
            libRaw.libraw_recycle(handler);
        }
    }

    @Override
    public void close() throws Exception {
        if (!closed) {
            libRaw.libraw_close(handler);
            closed = true;
            logger.debug("LibRaw handler closed");
        }
    }

    public record RawThumbnail(byte[] data, int type, int width, int height, int colors, int bits) { }
    /**
     * A decoded image ready for upload: 16 bits = RGB16 (RAW), 8 bits = RGBA8 (JPEG/PNG).
     *
     * @param data native pixel memory, owned by whoever holds the record; free it with {@code MemoryUtil.memFree}
     */
    public record DecodedRaw(ByteBuffer data, int width, int height, int bits) { }
}
