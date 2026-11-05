// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.engine;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import lombok.Getter;

@Getter
public enum GradeParam {

    // White balance: Kelvin of the scene light, tint is green (-) / magenta (+)
    TEMPERATURE("Temperature", Section.BASIC, 2000.0f, 12000.0f, 5500.0f, "K", 50.0f),
    // JPEG/TIFF (no as-shot white balance): shift from neutral instead of Kelvin, only one of the two is shown
    TEMPERATURE_OFFSET("Temperature", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),
    TINT("Tint", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),

    // Stops: the shader multiplies by 2^exposure, so 0 leaves the image unchanged
    EXPOSURE("Exposure", Section.BASIC, -5.0f, 5.0f, 0.0f, "EV", 0.1f),
    CONTRAST("Contrast", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HIGHLIGHTS("Highlights", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SHADOWS("Shadows", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),
    WHITES("Whites", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),
    BLACKS("Blacks", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),

    // Color
    VIBRANCE("Vibrance", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SATURATION("Saturation", Section.BASIC, -100.0f, 100.0f, 0.0f, "", 1.0f),

    // Color wheels, master luminance of shadows / midtones / highlights
    LIFT("Lift", Section.COLOR_WHEELS, -1.0f, 1.0f, 0.0f, "", 0.01f),
    GAMMA("Gamma", Section.COLOR_WHEELS, -1.0f, 1.0f, 0.0f, "", 0.01f),
    GAIN("Gain", Section.COLOR_WHEELS, -1.0f, 1.0f, 0.0f, "", 0.01f),

    // Detail & effects: 0 means the pass is off
    SHARPEN("Sharpen", Section.DETAIL, 0.0f, 100.0f, 0.0f, "", 1.0f),
    NOISE_REDUCTION("Noise Reduction", Section.DETAIL, 0.0f, 100.0f, 0.0f, "", 1.0f),
    GRAIN("Grain", Section.DETAIL, 0.0f, 100.0f, 0.0f, "", 1.0f),
    VIGNETTE("Vignette", Section.DETAIL, -100.0f, 100.0f, 0.0f, "", 1.0f),

    // Color mixer (HSL): shift of each of the 8 hue ranges, same order in all three groups
    HUE_RED("Red", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HUE_ORANGE("Orange", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HUE_YELLOW("Yellow", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HUE_GREEN("Green", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HUE_AQUA("Aqua", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HUE_BLUE("Blue", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HUE_PURPLE("Purple", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    HUE_MAGENTA("Magenta", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_RED("Red", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_ORANGE("Orange", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_YELLOW("Yellow", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_GREEN("Green", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_AQUA("Aqua", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_BLUE("Blue", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_PURPLE("Purple", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    SAT_MAGENTA("Magenta", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_RED("Red", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_ORANGE("Orange", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_YELLOW("Yellow", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_GREEN("Green", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_AQUA("Aqua", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_BLUE("Blue", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_PURPLE("Purple", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f),
    LUM_MAGENTA("Magenta", Section.COLOR_MIXER, -100.0f, 100.0f, 0.0f, "", 1.0f);

    private final String name;
    private final Section section;
    private final float minVal;
    private final float maxVal;
    private final float defVal;
    private final float increment;
    private final String unit;

    GradeParam(String name, Section section, float min, float max, float defaultValue, String unit, float increment) {
        this.name = name;
        this.section = section;
        this.minVal = min;
        this.maxVal = max;
        this.defVal = defaultValue;
        this.increment = increment;
        this.unit = unit;
    }

    /**
     * Reads this parameter from the edit. Parameters that EditState does not store yet return their default.
     */
    public float read(EditState editState) {
        return switch (this) {
            case TEMPERATURE -> editState.getTemperature();
            case TEMPERATURE_OFFSET -> editState.getTemperatureOffset();
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
            case TEMPERATURE_OFFSET -> editState.setTemperatureOffset(value);
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

    /**
     * Where a value sits on the slider track. Every parameter is linear (position = value) except the temperature,
     * which runs from -1 (min) through 0 (default) to 1 (max), linear in mireds (1e6 / K) on each side: equal mired
     * steps look like equal color steps, while equal Kelvin steps crowd the whole cool shift into the left part.
     */
    public double toSliderPosition(float value) {
        if (this != TEMPERATURE) {
            return value;
        }
        double mired = 1e6 / value;
        double neutral = 1e6 / defVal;
        return value <= defVal
                ? -(mired - neutral) / (1e6 / minVal - neutral)
                : (neutral - mired) / (neutral - 1e6 / maxVal);
    }

    /**
     * The value at a slider position, the inverse of {@link #toSliderPosition}.
     */
    public float fromSliderPosition(double position) {
        if (this != TEMPERATURE) {
            return (float) position;
        }
        double neutral = 1e6 / defVal;
        double mired = position <= 0
                ? neutral - position * (1e6 / minVal - neutral)
                : neutral - position * (neutral - 1e6 / maxVal);
        return (float) (1e6 / mired);
    }

    public boolean isWhiteBalance() {
        return this == TEMPERATURE || this == TEMPERATURE_OFFSET || this == TINT;
    }

    private boolean isHsl() {
        return section == Section.COLOR_MIXER;
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
}
