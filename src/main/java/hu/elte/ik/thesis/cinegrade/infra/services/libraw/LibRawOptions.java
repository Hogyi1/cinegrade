package hu.elte.ik.thesis.cinegrade.infra.services.libraw;

// [AI-GENERATED]

/**
 * Named values for the LibRaw processing settings (the {@code libraw_set_*} calls).
 * The numbers are LibRaw's own; the enums only give them names.
 */
public final class LibRawOptions {

    private LibRawOptions() {
    }

    /**
     * {@code libraw_set_output_color}: the colour space LibRaw converts the demosaiced image into.
     */
    public enum OutputColor {
        /** Camera native colours, no conversion. Looks green/flat; only for custom colour pipelines. */
        RAW(0),
        /** sRGB primaries with the sRGB tone curve. What screens and JPEGs expect. */
        SRGB(1),
        /** Adobe RGB (1998). Wider than sRGB; looks dull if shown as sRGB. */
        ADOBE_RGB(2),
        /** Wide Gamut RGB. Much wider than sRGB; looks washed out if shown as sRGB. */
        WIDE_GAMUT(3),
        /** ProPhoto RGB. Widest of the common ones; needs colour management to look right. */
        PROPHOTO(4),
        /** CIE XYZ. Device-independent, not for display. */
        XYZ(5),
        /** ACES (AP0). Film/VFX working space. */
        ACES(6);

        public final int value;

        OutputColor(int value) {
            this.value = value;
        }
    }

    /**
     * {@code libraw_set_demosaic} (user_qual): the algorithm that rebuilds full RGB pixels from the sensor's
     * colour filter mosaic. Higher quality is slower.
     */
    public enum Demosaic {
        /** Bilinear. Fastest, soft and with colour fringes. Fine for quick previews. */
        LINEAR(0),
        /** Variable Number of Gradients. Sharper than linear, slower. */
        VNG(1),
        /** Patterned Pixel Grouping. Good quality/speed balance. */
        PPG(2),
        /** Adaptive Homogeneity-Directed. LibRaw's default, good detail. */
        AHD(3),
        /** DCB. Detailed, fewer artefacts than AHD on fine patterns. */
        DCB(4),
        /** DHT. High quality, slow. */
        DHT(11),
        /** Modified AHD. High quality, slow. */
        AAHD(12);

        public final int value;

        Demosaic(int value) {
            this.value = value;
        }
    }

    /**
     * {@code libraw_set_output_bps}: bits per channel of the output image.
     */
    public enum OutputBits {
        /** 8 bits per channel. Smaller, but banding when graded. */
        BITS_8(8),
        /** 16 bits per channel. Headroom for grading; what the GPU texture expects. */
        BITS_16(16);

        public final int value;

        OutputBits(int value) {
            this.value = value;
        }
    }

    /**
     * {@code libraw_set_user_flip}: whether LibRaw rotates the pixels itself.
     */
    public enum Flip {
        /** Use the orientation stored in the file: LibRaw rotates the pixels. */
        FROM_FILE(-1),
        /** No rotation: pixels stay in sensor order (rotate later, e.g. in the shader). */
        NONE(0),
        /** Rotate 180°. */
        ROTATE_180(3),
        /** Rotate 90° counter-clockwise. */
        ROTATE_90_CCW(5),
        /** Rotate 90° clockwise. */
        ROTATE_90_CW(6);

        public final int value;

        Flip(int value) {
            this.value = value;
        }
    }

    /**
     * On/off settings ({@code libraw_set_no_auto_bright}, {@code libraw_set_use_camera_wb}).
     * <ul>
     *     <li><b>no_auto_bright</b> 0: LibRaw brightens each image so about 1% of pixels clip, so every photo is
     *     brightened by a different amount. 1: off, brightness comes only from the data (and the exposure slider).</li>
     *     <li><b>use_camera_wb</b> 1: use the white balance the camera recorded "as shot". 0: LibRaw's default
     *     daylight multipliers.</li>
     *     <li><b>user_mul</b> (per channel): manual white balance multipliers. Leave unset so the camera white
     *     balance is used.</li>
     * </ul>
     */
    public enum Toggle {
        OFF(0),
        ON(1);

        public final int value;

        Toggle(int value) {
            this.value = value;
        }
    }
}
