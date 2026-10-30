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
    VIGNETTE("Vignette", Group.EFFECTS, -100.0f, 100.0f, 0.0f, "");

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
            default -> defVal;
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
                // not in EditState / the shader yet
            }
        }
    }

    /**
     * One collapsible pane of the edit panel; its eye toggle bypasses every parameter in it.
     */
    @Getter
    public enum Section {
        BASIC("Basic"),
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
