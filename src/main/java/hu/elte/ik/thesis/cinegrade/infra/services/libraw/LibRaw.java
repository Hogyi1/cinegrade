package hu.elte.ik.thesis.cinegrade.infra.services.libraw;

import com.sun.jna.*;
import hu.elte.ik.thesis.cinegrade.infra.utils.process.NativeBinaryLocator;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public interface LibRaw extends Library {

    String path = NativeBinaryLocator.getBinaryPath("libraw.dll").get().toString();
    LibRaw INSTANCE = Native.load(path, LibRaw.class);

    @Structure.FieldOrder({
            "type", "height", "width", "colors", "bits", "data_size"
    })
    // libraw image
    class ProcessedImage extends Structure {
        public int    type;
        public short  height;
        public short  width;
        public short  colors;
        public short  bits;
        public int    data_size;
        // data[1] is NOT mapped — we read it manually via pointer arithmetic

        // enum(4) + ushort*4(8) + unsigned int(4) = 16 bytes: data[] starts right after the fields
        private static final int DATA_OFFSET = 16;

        public static class ByReference
                extends ProcessedImage implements Structure.ByReference {}

        /**
         * Copies the pixel data onto the Java heap. For small images (thumbnails).
         * Must be called before {@code libraw_dcraw_clear_mem}.
         */
        public byte[] copyPixelBytes() {
            if (data_size <= 0) return new byte[0];
            return getPointer().getByteArray(DATA_OFFSET, data_size);
        }

        /**
         * Copies the pixel data into a new native buffer with one memcpy, no heap copy. For full-size images.
         * Must be called before {@code libraw_dcraw_clear_mem}.
         * <p>
         * The caller owns the buffer and must free it with {@link MemoryUtil#memFree}; the GC does not.
         */
        public ByteBuffer copyPixelsNative() {
            ByteBuffer pixels = MemoryUtil.memAlloc(data_size);
            MemoryUtil.memCopy(Pointer.nativeValue(getPointer()) + DATA_OFFSET, MemoryUtil.memAddress(pixels), data_size);
            return pixels;
        }
    }

    // recycle
    Pointer libraw_init(int flags);
    void    libraw_recycle(Pointer lr);
    void    libraw_close(Pointer lr);

    // File
    int libraw_open_file(Pointer lr, String filename);
    int libraw_open_wfile(Pointer lr, WString filename);

    // Processing
    int libraw_unpack(Pointer lr);
    int libraw_unpack_thumb(Pointer lr);
    int libraw_dcraw_process(Pointer lr);

    // Output
    ProcessedImage.ByReference libraw_dcraw_make_mem_image(Pointer lr, int[] errc);
    ProcessedImage.ByReference libraw_dcraw_make_mem_thumb(Pointer lr, int[] errc);
    void libraw_dcraw_clear_mem(Pointer img);

    // Settings
    void libraw_set_demosaic(Pointer lr, int value);
    void libraw_set_output_bps(Pointer lr, int value);
    void libraw_set_output_color(Pointer lr, int value);
    void libraw_set_no_auto_bright(Pointer lr, int value);
    void libraw_set_use_camera_wb(Pointer lr, int value);
    void libraw_set_user_flip(Pointer lr, int value);
    int libraw_get_flip(Pointer lr);
    //void libraw_set_output_rotate(Pointer lr, int value);

    // void libraw_set_user_mul(Pointer lr, int value, float value2);
    void libraw_set_gamma(Pointer lr, int index, float value);

    // cancellable methods callback, binds to libraw memory of cancel
    interface ProgressCallback extends Callback {
        int invoke(Pointer data, int stage, int iteration, int expected);
    }

    void libraw_set_progress_handler(Pointer lr, ProgressCallback callback, Pointer data);

    // Others
    String libraw_version();
    String libraw_strerror(int errorcode);

    // metadata - deprecated
    int libraw_get_iwidth(Pointer lr);
    int libraw_get_iheight(Pointer lr);
    // White balance multipliers per channel (0 R, 1 G, 2 B, 3 second G), valid after libraw_open_file
    // cam_mul: what the camera recorded "as shot", pre_mul: daylight
    float libraw_get_cam_mul(Pointer lr, int index);
    float libraw_get_pre_mul(Pointer lr, int index);
}