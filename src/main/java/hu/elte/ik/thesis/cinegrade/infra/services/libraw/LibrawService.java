package hu.elte.ik.thesis.cinegrade.infra.services.libraw;

import com.sun.jna.Pointer;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public class LibrawService implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(LibrawService.class);
    private final LibRaw libRaw;
    private final Pointer handler;
    private volatile boolean closed = false;
    // Asked by LibRaw between steps of the decode; set for the duration of one decodeRaw call
    private volatile BooleanSupplier cancelled = () -> false;
    // Kept in a field: JNA only holds a weak reference, a collected callback crashes the JVM inside LibRaw
    private final LibRaw.ProgressCallback progressCallback = (data, stage, iteration, expected) -> cancelled.getAsBoolean() ? 1 : 0;

    public LibrawService() {
        this.libRaw = LibRaw.INSTANCE;

        this.handler = libRaw.libraw_init(0);
        if (this.handler == null) {
            logger.error("Failed to initialize native LibRaw context");
            throw new CineGradeException(ErrorCode.REQ_LIBRAW_LOAD_FAILED, "Failed to initialize LibRaw native handler");
        }
        libRaw.libraw_set_progress_handler(handler, progressCallback, null);
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
     * Same, but LibRaw asks {@code cancelled} between its processing steps and stops early when it returns true
     * (the result is then empty). Call it on the thread that owns this service, never recycle from another one.
     */
    public Optional<DecodedImage> decodeRaw(File rawFile, BooleanSupplier cancelled) {
        if (closed || cancelled.getAsBoolean()) return Optional.empty();

        this.cancelled = cancelled;
        try {
            libRaw.libraw_set_output_bps(handler, LibRawOptions.OutputBits.BITS_16.value);
            libRaw.libraw_set_demosaic(handler, LibRawOptions.Demosaic.PPG.value);
            libRaw.libraw_set_output_color(handler, LibRawOptions.OutputColor.SRGB.value);
            libRaw.libraw_set_no_auto_bright(handler, LibRawOptions.Toggle.ON.value);
            libRaw.libraw_set_use_camera_wb(handler, LibRawOptions.Toggle.ON.value);
            libRaw.libraw_set_gamma(handler, 0, 1.0f);
            libRaw.libraw_set_gamma(handler, 1, 1.0f);

            libRaw.libraw_set_user_flip(handler, LibRawOptions.Flip.FROM_FILE.value);

            int err = libRaw.libraw_open_file(handler, rawFile.getAbsolutePath());
            if (err != 0) {
                logger.error("Failed to open raw file (code {}): {}", err, rawFile.getAbsolutePath());
                return Optional.empty();
            }

            float[] asShot = new float[3];
            float[] daylight = new float[3];
            for (int i = 0; i < 3; i++) {
                asShot[i] = libRaw.libraw_get_cam_mul(handler, i);
                daylight[i] = libRaw.libraw_get_pre_mul(handler, i);
            }
            WhiteBalanceMultipliers multipliers = new WhiteBalanceMultipliers(asShot, daylight);

            err = libRaw.libraw_unpack(handler);
            if (err != 0) {
                logger.error("Failed to unpack raw file (code {}): {}", err, rawFile.getAbsolutePath());
                return Optional.empty();
            }

            err = libRaw.libraw_dcraw_process(handler);
            if (err != 0 && cancelled.getAsBoolean()) {
                logger.debug("Decode cancelled: {}", rawFile.getAbsolutePath());
                return Optional.empty();
            }
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
                return Optional.of(new DecodedImage(pixels, image.width, image.height, image.bits, multipliers));
            } finally {
                libRaw.libraw_dcraw_clear_mem(image.getPointer());
            }
        } finally {
            libRaw.libraw_recycle(handler);
            this.cancelled = () -> false;
        }
    }

    /**
     * A raw thumbnail image.
     *
     * @param data    the thumbnail data
     * @param type    the thumbnail type
     * @param width   the thumbnail width
     * @param height  the thumbnail height
     * @param colors  the number of color channels
     * @param bits    the number of bits per channel
     */
    @Override
    public void close() {
        if (!closed) {
            libRaw.libraw_close(handler);
            closed = true;
            logger.debug("LibRaw handler closed");
        }
    }

    public record RawThumbnail(byte[] data, int type, int width, int height, int colors, int bits) { }
    /**
     * A decoded image ready for upload: 16 bits = linear RGB16 (RAW), 8 bits = sRGB RGBA8 (JPEG/PNG).
     *
     * @param data        native pixel memory, owned by whoever holds the record; free it with {@code MemoryUtil.memFree}
     * @param multipliers white balance of a RAW, null for other images
     */
    public record DecodedImage(ByteBuffer data, int width, int height, int bits, WhiteBalanceMultipliers multipliers) {
        public DecodedImage(ByteBuffer data, int width, int height, int bits) {
            this(data, width, height, bits, null);
        }
    }
    /**
     * LibRaw's white balance multipliers, R, G, B.
     *
     * @param asShot   cam_mul, what the camera recorded
     * @param daylight pre_mul, daylight for this camera
     */
    public record WhiteBalanceMultipliers(float[] asShot, float[] daylight) { }
}
