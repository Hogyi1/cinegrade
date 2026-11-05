// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.domain.editing;

import hu.elte.ik.thesis.cinegrade.domain.enums.WhiteBalanceType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
public class EditState {
    public transient static final int HSL_COLORS = 8;
    // hue, sat, lum, padding: one std140 vec4 per color
    public transient static final int HSL_STRIDE = 4;
    public transient  static final int HSL_SIZE = HSL_STRIDE * HSL_COLORS;
    // Neutral of photos without an as-shot white balance, the shader's fallback too
    public static final float DAYLIGHT_KELVIN = 5500.0f;
    private String photoId;
    private float exposure = 0.0f;
    private float contrast = 0.0f;
    private float highlights = 0.0f;
    private float shadows = 0.0f;
    private float whites = 0.0f;
    private float blacks = 0.0f;
    private float temperature = DAYLIGHT_KELVIN;
    private float tint = 0.0f;
    private float temperatureOffset = 0.0f;
    private WhiteBalanceType whiteBalance = WhiteBalanceType.AS_SHOT;
    private float asShotTemperature = 0.0f;
    private float asShotTint = 0.0f;
    private float saturation = 0.0f;
    private float vibrance = 0.0f;
    // Color mixer, per color: red_hue, red_sat, red_lum, pad, orange_hue, ... for
    // red, orange, yellow, green, aqua, blue, purple, magenta (same layout as hsl_* in fragment.frag)
    private float[] hsl = new float[HSL_SIZE];
    private String lutPath;
    private String curvesJson;
    private Instant updatedAt = Instant.now();

    // [AI-GENERATED METHOD]
    public EditState() {
    }

    // [AI-GENERATED METHOD]
    public EditState(String photoId) {
        this.photoId = photoId;
    }

    // [AI-GENERATED METHOD]
    public EditState(String photoId, float exposure, float contrast, float highlights, float shadows, float whites, float blacks, float temperature, float tint, float saturation, float vibrance, String lutPath, String curvesJson, Instant updatedAt) {
        this.photoId = photoId;
        this.exposure = exposure;
        this.contrast = contrast;
        this.highlights = highlights;
        this.shadows = shadows;
        this.whites = whites;
        this.blacks = blacks;
        this.temperature = temperature;
        this.tint = tint;
        this.saturation = saturation;
        this.vibrance = vibrance;
        this.lutPath = lutPath;
        this.curvesJson = curvesJson;
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public EditState(String photoId, EditState editState) {
        this.photoId = photoId;
        this.exposure = editState.getExposure();
        this.contrast = editState.getContrast();
        this.highlights = editState.getHighlights();
        this.shadows = editState.getShadows();
        this.whites = editState.getWhites();
        this.blacks = editState.getBlacks();
        this.temperature = editState.getTemperature();
        this.tint = editState.getTint();
        this.temperatureOffset = editState.getTemperatureOffset();
        this.whiteBalance = editState.getWhiteBalance();
        this.asShotTemperature = editState.getAsShotTemperature();
        this.asShotTint = editState.getAsShotTint();
        this.saturation = editState.getSaturation();
        this.vibrance = editState.getVibrance();
        // a copy, the snapshot must not change with the live edit
        this.hsl = editState.getHsl().clone();
        this.lutPath = editState.getLutPath();
        this.curvesJson = editState.getCurvesJson();
        this.updatedAt = Instant.now();
    }

    /**
     * The temperature the image is decoded with: the as-shot one of a RAW, daylight when unknown.
     */
    public float getNeutralTemperature() {
        return asShotTemperature > 0.0f ? asShotTemperature : DAYLIGHT_KELVIN;
    }

    /**
     * A fresh edit of the same photo: every setting at its default, white balance as shot.
     */
    public EditState defaults() {
        EditState defaults = new EditState(photoId);
        defaults.asShotTemperature = asShotTemperature;
        defaults.asShotTint = asShotTint;
        defaults.temperature = getNeutralTemperature();
        defaults.tint = asShotTint;
        return defaults;
    }

    /**
     * Stores the camera's white balance (known once the RAW is decoded); with AS_SHOT the sliders follow it.
     */
    public void applyAsShot(float temperature, float tint) {
        this.asShotTemperature = temperature;
        this.asShotTint = tint;
        if (whiteBalance == WhiteBalanceType.AS_SHOT) {
            this.temperature = getNeutralTemperature();
            this.tint = tint;
        }
    }

    /**
     * Sets the temperature / tint of a preset. CUSTOM keeps the current values.
     */
    public void applyWhiteBalance(WhiteBalanceType type) {
        whiteBalance = type;
        if (type == WhiteBalanceType.AS_SHOT) {
            temperature = getNeutralTemperature();
            tint = asShotTint;
            temperatureOffset = 0.0f;
        } else if (type.hasFixedValues()) {
            temperature = type.getTemperature();
            tint = type.getTint();
            temperatureOffset = 0.0f;
        }
    }

    /**
     * After a temperature / tint slider moved: AS_SHOT if it is back at the camera's values, CUSTOM otherwise.
     */
    public void updateWhiteBalanceType() {
        boolean asShot = temperature == getNeutralTemperature() && tint == asShotTint && temperatureOffset == 0.0f;
        whiteBalance = asShot ? WhiteBalanceType.AS_SHOT : WhiteBalanceType.CUSTOM;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EditState that = (EditState) o;
        return Float.compare(that.exposure, exposure) == 0
                && Float.compare(that.contrast, contrast) == 0
                && Float.compare(that.highlights, highlights) == 0
                && Float.compare(that.shadows, shadows) == 0
                && Float.compare(that.whites, whites) == 0
                && Float.compare(that.blacks, blacks) == 0
                && Float.compare(that.temperature, temperature) == 0
                && Float.compare(that.tint, tint) == 0
                && Float.compare(that.temperatureOffset, temperatureOffset) == 0
                && whiteBalance == that.whiteBalance
                && Float.compare(that.asShotTemperature, asShotTemperature) == 0
                && Float.compare(that.asShotTint, asShotTint) == 0
                && Float.compare(that.saturation, saturation) == 0
                && Float.compare(that.vibrance, vibrance) == 0
                && java.util.Arrays.equals(hsl, that.hsl)
                && Objects.equals(lutPath, that.lutPath)
                && Objects.equals(curvesJson, that.curvesJson);
    }

    @Override
    public int hashCode() {
        return 31 * java.util.Arrays.hashCode(hsl) + Objects.hash(
                exposure,
                contrast,
                highlights,
                shadows,
                whites,
                blacks,
                temperature,
                tint,
                temperatureOffset,
                whiteBalance,
                asShotTemperature,
                asShotTint,
                saturation,
                vibrance,
                lutPath,
                curvesJson
        );
    }
}
