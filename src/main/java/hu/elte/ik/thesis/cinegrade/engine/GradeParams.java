// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.engine;

import lombok.Getter;

@Getter
public enum GradeParams {

    // White balance: Kelvin of the scene light, tint is green (-) / magenta (+)
    TEMPERATURE("Temperature", 2000.0f, 12000.0f, 5500.0f, "K"),
    TINT("Tint", -100.0f, 100.0f, 0.0f, ""),

    // Stops: the shader multiplies by 2^exposure, so 0 leaves the image unchanged
    EXPOSURE("Exposure", -5.0f, 5.0f, 0.0f, "EV"),
    CONTRAST("Contrast", -100.0f, 100.0f, 0.0f, ""),
    HIGHLIGHTS("Highlights", -100.0f, 100.0f, 0.0f, ""),
    SHADOWS("Shadows", -100.0f, 100.0f, 0.0f, ""),
    WHITES("Whites", -100.0f, 100.0f, 0.0f, ""),
    BLACKS("Blacks", -100.0f, 100.0f, 0.0f, ""),

    // Presence
    TEXTURE("Texture", -100.0f, 100.0f, 0.0f, ""),
    CLARITY("Clarity", -100.0f, 100.0f, 0.0f, ""),
    DEHAZE("Dehaze", -100.0f, 100.0f, 0.0f, ""),
    VIBRANCE("Vibrance", -100.0f, 100.0f, 0.0f, ""),
    SATURATION("Saturation", -100.0f, 100.0f, 0.0f, ""),

    // Color wheels, master luminance of shadows / midtones / highlights
    LIFT("Lift", -1.0f, 1.0f, 0.0f, ""),
    GAMMA("Gamma", -1.0f, 1.0f, 0.0f, ""),
    GAIN("Gain", -1.0f, 1.0f, 0.0f, ""),

    // Detail & effects: 0 means the pass is off
    SHARPEN("Sharpen", 0.0f, 100.0f, 0.0f, ""),
    NOISE_REDUCTION("Noise Reduction", 0.0f, 100.0f, 0.0f, ""),
    GRAIN("Grain", 0.0f, 100.0f, 0.0f, ""),
    VIGNETTE("Vignette", -100.0f, 100.0f, 0.0f, "");

    private final String name;
    private final float minVal;
    private final float maxVal;
    private final float defVal;
    private final String unit;

    GradeParams(String name, float min, float max, float defaultValue, String unit) {
        this.name = name;
        this.minVal = min;
        this.maxVal = max;
        this.defVal = defaultValue;
        this.unit = unit;
    }
}
