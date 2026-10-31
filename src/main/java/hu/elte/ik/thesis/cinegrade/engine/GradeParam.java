// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.engine;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import lombok.Getter;

@Getter
public enum GradeParam {

    // White balance: Kelvin of the scene light, tint is green (-) / magenta (+)
    TEMPERATURE("Temperature", Group.WHITE_BALANCE, 2000.0f, 12000.0f, 5500.0f, "K"),
    TINT("Tint", Group.WHITE_BALANCE, -100.0f, 100.0f, 0.0f, ""),

    // Stops: the shader multiplies by 2^exposure, so 0 leaves the image unchanged
    EXPOSURE("Exposure", Group.TONE, -5.0f, 5.0f, 0.0f, "EV"),
    CONTRAST("Contrast", Group.TONE, -100.0f, 100.0f, 0.0f, ""),
    HIGHLIGHTS("Highlights", Group.TONE, -100.0f, 100.0f, 0.0f, ""),
    SHADOWS("Shadows", Group.TONE, -100.0f, 100.0f, 0.0f, ""),
    WHITES("Whites", Group.TONE, -100.0f, 100.0f, 0.0f, ""),
    BLACKS("Blacks", Group.TONE, -100.0f, 100.0f, 0.0f, ""),

    // Presence
    TEXTURE("Texture", Group.PRESENCE, -100.0f, 100.0f, 0.0f, ""),
    CLARITY("Clarity", Group.PRESENCE, -100.0f, 100.0f, 0.0f, ""),
    DEHAZE("Dehaze", Group.PRESENCE, -100.0f, 100.0f, 0.0f, ""),

    // Color
    VIBRANCE("Vibrance", Group.COLOR, -100.0f, 100.0f, 0.0f, ""),
    SATURATION("Saturation", Group.COLOR, -100.0f, 100.0f, 0.0f, ""),

    // Color wheels, master luminance of shadows / midtones / highlights
    LIFT("Lift", Group.WHEELS, -1.0f, 1.0f, 0.0f, ""),
    GAMMA("Gamma", Group.WHEELS, -1.0f, 1.0f, 0.0f, ""),
    GAIN("Gain", Group.WHEELS, -1.0f, 1.0f, 0.0f, ""),

    // Detail & effects: 0 means the pass is off
    SHARPEN("Sharpen", Group.DETAIL, 0.0f, 100.0f, 0.0f, ""),
    NOISE_REDUCTION("Noise Reduction", Group.DETAIL, 0.0f, 100.0f, 0.0f, ""),
    GRAIN("Grain", Group.EFFECTS, 0.0f, 100.0f, 0.0f, ""),
    VIGNETTE("Vignette", Group.EFFECTS, -100.0f, 100.0f, 0.0f, ""),

    // Color mixer (HSL): shift of each of the 8 hue ranges, same order in all three groups
    HUE_RED("Red", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    HUE_ORANGE("Orange", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    HUE_YELLOW("Yellow", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    HUE_GREEN("Green", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    HUE_AQUA("Aqua", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    HUE_BLUE("Blue", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    HUE_PURPLE("Purple", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    HUE_MAGENTA("Magenta", Group.HSL_HUE, -100.0f, 100.0f, 0.0f, ""),
    SAT_RED("Red", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    SAT_ORANGE("Orange", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    SAT_YELLOW("Yellow", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    SAT_GREEN("Green", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    SAT_AQUA("Aqua", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    SAT_BLUE("Blue", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    SAT_PURPLE("Purple", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    SAT_MAGENTA("Magenta", Group.HSL_SATURATION, -100.0f, 100.0f, 0.0f, ""),
    LUM_RED("Red", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, ""),
    LUM_ORANGE("Orange", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, ""),
    LUM_YELLOW("Yellow", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, ""),
    LUM_GREEN("Green", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, ""),
    LUM_AQUA("Aqua", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, ""),
    LUM_BLUE("Blue", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, ""),
    LUM_PURPLE("Purple", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, ""),
    LUM_MAGENTA("Magenta", Group.HSL_LUMINANCE, -100.0f, 100.0f, 0.0f, "");

    private final String name;
    private final Group group;
    private final float minVal;
    private final float maxVal;
    private final float defVal;
    private final String unit;

    GradeParam(String name, Group group, float min, float max, float defaultValue, String unit) {
        this.name = name;
        this.group = group;
        this.minVal = min;
        this.maxVal = max;
        this.defVal = defaultValue;
        this.unit = unit;
    }

    /**
     * Reads this parameter from the edit. Parameters that EditState does not store yet return their default.
     */
    public float read(EditState editState) {
        return switch (this) {
            case TEMPERATURE -> editState.getTemperature();
            case TINT -> editState.getTint();
            case EXPOSURE -> editState.getExposure();
            case CONTRAST -> editState.getContrast();
            case HIGHLIGHTS -> editState.getHighlights();
            case SHADOWS -> editState.getShadows();
            case WHITES -> editState.getWhites();
            case BLACKS -> editState.getBlacks();
            case VIBRANCE -> editState.getVibrance();
            case SATURATION -> editState.getSaturation();
            default -> isHsl() ? editState.getHsl()[hslIndex()] : defVal;
        };
    }

    /**
     * Writes this parameter into the edit. Parameters that EditState does not store yet are ignored.
     */
    public void write(EditState editState, float value) {
        switch (this) {
            case TEMPERATURE -> editState.setTemperature(value);
            case TINT -> editState.setTint(value);
            case EXPOSURE -> editState.setExposure(value);
            case CONTRAST -> editState.setContrast(value);
            case HIGHLIGHTS -> editState.setHighlights(value);
            case SHADOWS -> editState.setShadows(value);
            case WHITES -> editState.setWhites(value);
            case BLACKS -> editState.setBlacks(value);
            case VIBRANCE -> editState.setVibrance(value);
            case SATURATION -> editState.setSaturation(value);
            default -> {
                if (isHsl()) {
                    editState.getHsl()[hslIndex()] = value;
                }
            }
        }
    }

    private boolean isHsl() {
        return group.getSection() == Section.COLOR_MIXER;
    }

    /**
     * Index in {@link EditState#getHsl()}, which is laid out per color (hue, sat, lum, pad).
     * The constants are declared as 8 HUE_, then 8 SAT_, then 8 LUM_, so the position in that run
     * gives the channel (0 hue, 1 sat, 2 lum) and the color.
     */
    private int hslIndex() {
        int position = ordinal() - HUE_RED.ordinal();
        int channel = position / EditState.HSL_COLORS;
        int color = position % EditState.HSL_COLORS;
        return color * EditState.HSL_STRIDE + channel;
    }

    /**
     * One collapsible pane of the edit panel; its eye toggle bypasses every parameter in it.
     */
    @Getter
    public enum Section {
        BASIC("Basic"),
        COLOR_MIXER("Color Mixer"),
        COLOR_WHEELS("Color Wheels"),
        DETAIL("Detail & Effects");

        private final String title;

        Section(String title) {
            this.title = title;
        }
    }

    /**
     * Small header inside a section; an empty title means no header.
     */
    @Getter
    public enum Group {
        WHITE_BALANCE("White Balance", Section.BASIC),
        TONE("Tone", Section.BASIC),
        PRESENCE("Presence", Section.BASIC),
        COLOR("Color", Section.BASIC),
        // Color mixer groups are tabs, not headers
        HSL_HUE("Hue", Section.COLOR_MIXER),
        HSL_SATURATION("Saturation", Section.COLOR_MIXER),
        HSL_LUMINANCE("Luminance", Section.COLOR_MIXER),
        WHEELS("", Section.COLOR_WHEELS),
        DETAIL("Detail", Section.DETAIL),
        EFFECTS("Effects", Section.DETAIL);

        private final String title;
        private final Section section;

        Group(String title, Section section) {
            this.title = title;
            this.section = section;
        }
    }
}
