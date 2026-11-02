package hu.elte.ik.thesis.cinegrade.engine.grade;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;

import java.util.Optional;

// [AI-GENERATED]
/**
 * Turns a RAW's white balance multipliers into the temperature / tint of the slider, using the same blackbody model
 * as {@code whiteBalanceGains} in fragment.frag, so moving the slider from the as-shot value to e.g. 5500 K / 0 gives
 * exactly the daylight decode.
 * <p>
 * cam_mul / pre_mul is the as-shot white balance relative to daylight. The temperature is the one whose blackbody
 * gains (from daylight) have the same red / blue ratio, the tint is the green that is still left over.
 */
public final class AsShotWhiteBalance {

    // keep in sync with fragment.frag: TINT_STOPS, WB_KELVIN_STRENGTH / WB_WARM_KELVIN_STRENGTH = 1.0
    private static final double TINT_STOPS = 1.0 - 0.273612;
    private static final int SEARCH_STEPS = 40;

    public record AsShot(float temperature, float tint) { }

    private AsShotWhiteBalance() {
    }

    /**
     * @param camMul as-shot multipliers (R, G, B), from LibRaw
     * @param preMul daylight multipliers (R, G, B), from LibRaw
     * @return empty if the camera did not record a usable white balance
     */
    public static Optional<AsShot> estimate(float[] camMul, float[] preMul) {
        if (camMul == null || preMul == null || camMul.length < 3 || preMul.length < 3) {
            return Optional.empty();
        }
        double[] m = new double[3];
        for (int i = 0; i < 3; i++) {
            if (camMul[i] <= 0.0f || preMul[i] <= 0.0f) {
                return Optional.empty();
            }
            m[i] = camMul[i] / preMul[i];
        }
        double target = m[0] / m[2];

        // red / blue of the gains grows with the temperature: bisection in log space over the slider's range
        double low = Math.log(GradeParam.TEMPERATURE.getMinVal());
        double high = Math.log(GradeParam.TEMPERATURE.getMaxVal());
        for (int i = 0; i < SEARCH_STEPS; i++) {
            double mid = (low + high) / 2.0;
            double[] gains = gains(Math.exp(mid));
            if (gains[0] / gains[2] < target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        double kelvin = Math.exp((low + high) / 2.0);

        // green gain still needed on top of the blackbody, as the shader's tint: g *= 2^(-tint / 100 * TINT_STOPS)
        double[] gains = gains(kelvin);
        double scale = Math.sqrt(gains[0] * gains[2] / (m[0] * m[2]));
        double green = scale * m[1] / gains[1];
        double tint = Math.clamp(-100.0 * log2(green) / TINT_STOPS, -100.0, 100.0);

        return Optional.of(new AsShot((float) kelvin, (float) tint));
    }

    /**
     * Linear gains that take a daylight image to one balanced for this light (fragment.frag: whiteBalanceGains).
     */
    private static double[] gains(double kelvin) {
        double[] neutral = kelvinToRgb(EditState.DAYLIGHT_KELVIN);
        double[] light = kelvinToRgb(kelvin);
        double[] gains = new double[3];
        for (int i = 0; i < 3; i++) {
            gains[i] = srgbToLinear(neutral[i]) / Math.max(srgbToLinear(light[i]), 1e-4);
        }
        return gains;
    }

    /**
     * Port of kelvinToRgb_BeRo in fragment.frag (Benjamin 'BeRo' Rosseaux, CC0).
     */
    private static double[] kelvinToRgb(double kelvin) {
        double[] m0;
        double[] m1;
        double[] m2;
        if (kelvin <= 6500.0) {
            m0 = new double[]{0.0, -2902.1955373783176, -8257.7997278925690};
            m1 = new double[]{0.0, 1669.5803561666639, 2575.2827530017594};
            m2 = new double[]{1.0, 1.3302673723350029, 1.8993753891711275};
        } else {
            m0 = new double[]{1745.0425298314172, 1216.6168361476490, -8257.7997278925690};
            m1 = new double[]{-2666.3474220535695, -2173.1012343082230, 2575.2827530017594};
            m2 = new double[]{0.55995389139931482, 0.70381203140554553, 1.8993753891711275};
        }
        double t = Math.clamp(kelvin, 1000.0, 40000.0);
        double[] rgb = new double[3];
        for (int i = 0; i < 3; i++) {
            rgb[i] = Math.clamp(m0[i] / (t + m1[i]) + m2[i], 0.0, 1.0);
        }
        return rgb;
    }

    private static double srgbToLinear(double value) {
        double v = Math.clamp(value, 0.0, 1.0);
        return v < 0.04045 ? v * 0.0773993808 : Math.pow(v * 0.9478672986 + 0.0521327014, 2.4);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
