package hu.elte.ik.thesis.cinegrade.domain.enums;

import lombok.Getter;

/**
 * White balance presets of the develop panel. A preset only sets the temperature / tint sliders, the photo is never
 * decoded again for it.
 * <p>
 * AS_SHOT takes the camera's own white balance (per photo), CUSTOM is whatever the sliders were moved to. The fixed
 * presets are the light the photo was taken under, so they only make sense for RAWs.
 */
@Getter
public enum WhiteBalanceType {
    AS_SHOT("As Shot", Float.NaN, Float.NaN),
    DAYLIGHT("Daylight", 5500.0f, 0.0f),
    CLOUDY("Cloudy", 6500.0f, 0.0f),
    SHADE("Shade", 7500.0f, 0.0f),
    TUNGSTEN("Tungsten", 2850.0f, 0.0f),
    FLUORESCENT("Fluorescent", 3800.0f, 20.0f),
    CUSTOM("Custom", Float.NaN, Float.NaN);

    private final String label;
    private final float temperature;
    private final float tint;

    WhiteBalanceType(String label, float temperature, float tint) {
        this.label = label;
        this.temperature = temperature;
        this.tint = tint;
    }

    public boolean hasFixedValues() {
        return !Float.isNaN(temperature);
    }

    /**
     * Stored name back to the enum; unknown or missing values are AS_SHOT.
     */
    public static WhiteBalanceType fromName(String name) {
        if (name == null) {
            return AS_SHOT;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return AS_SHOT;
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
