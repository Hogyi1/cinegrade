package hu.elte.ik.thesis.cinegrade.engine.grade;

import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;

import java.util.Optional;

// [AI-GENERATED]

/**
 * Everything that turns measured colors into the temperature / tint of the sliders, using the same blackbody model as
 * {@code whiteBalanceGains} in fragment.frag.
 * <p>
 * The one rule behind "as shot": a RAW is decoded with the camera's white balance, so the pixels are already balanced
 * for it. That temperature / tint is the photo's <b>neutral</b>: at the neutral the shader changes nothing, and the
 * sliders only move away from it. It is known once the photo is decoded, so every destination that decodes a photo fills it
 * in with {@link #withAsShot}. Photos without a camera white balance (JPEG/TIFF) use daylight as their neutral.
 */
public final class WhiteBalancer {

    // keep in sync with fragment.frag: TINT_STOPS, WB_KELVIN_STRENGTH / WB_WARM_KELVIN_STRENGTH = 1.0
    private static final double TINT_STOPS = 1.0 - 0.273612;
    private static final int SEARCH_STEPS = 40;
    // Linear channel values a picked pixel has to stay between
    private static final float MIN_SAMPLE = 0.002f;
    private static final float MAX_SAMPLE = 0.98f;

    private WhiteBalancer() {
    }

    /**
     * The white balance the camera recorded.
     *
     * @return null if this image has none (JPEG/TIFF, or a RAW without a usable one)
     */
    public static WhiteBalance asShotOf(hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService.DecodedImage decoded) {
        if (decoded.multipliers() == null) {
            return null;
        }
        return fromCamera(decoded.multipliers().asShot(), decoded.multipliers().daylight()).orElse(null);
    }

    /**
     * The edit with this photo's as-shot white balance filled in; a copy if anything changes, the same object if not.
     *
     * @param asShot null keeps the edit as it is
     */
    public static EditState withAsShot(EditState editState, WhiteBalance asShot) {
        if (asShot == null || (editState.getAsShotTemperature() == asShot.temperature()
                && editState.getAsShotTint() == asShot.tint())) {
            return editState;
        }
        EditState copy = new EditState(editState.getPhotoId(), editState);
        copy.applyAsShot(asShot.temperature(), asShot.tint());
        return copy;
    }

    /**
     * cam_mul / pre_mul is the as-shot white balance relative to daylight.
     *
     * @param camMul as-shot multipliers (R, G, B), from LibRaw
     * @param preMul daylight multipliers (R, G, B), from LibRaw
     * @return empty if the camera did not record a usable white balance
     */
    public static Optional<WhiteBalance> fromCamera(float[] camMul, float[] preMul) {
        if (camMul == null || preMul == null || camMul.length < 3 || preMul.length < 3) {
            return Optional.empty();
        }
        double[] wanted = new double[3];
        for (int i = 0; i < 3; i++) {
            if (camMul[i] <= 0.0f || preMul[i] <= 0.0f) {
                return Optional.empty();
            }
            wanted[i] = camMul[i] / preMul[i];
        }
        return Optional.of(solve(wanted, EditState.DAYLIGHT_KELVIN, 0.0));
    }

    /**
     * The slider values that turn this pixel neutral (eyedropper, auto white balance).
     *
     * @param r,g,b linear color of the pixel as decoded: rendered with {@code editState.defaults()}, so no slider of
     *              the current edit is in it
     * @param editState the edit of the same photo, its neutral is what the shader measures from
     * @return empty if a channel is near black or clipped: its true color is lost, so its ratio means nothing
     */
    public static Optional<WhiteBalance> fromSample(float r, float g, float b, EditState editState) {
        if (Math.min(r, Math.min(g, b)) < MIN_SAMPLE || Math.max(r, Math.max(g, b)) > MAX_SAMPLE) {
            return Optional.empty();
        }
        double[] wanted = {1.0 / r, 1.0 / g, 1.0 / b};
        return Optional.of(solve(wanted, editState.getNeutralTemperature(), editState.getAsShotTint()));
    }

    /**
     * The temperature is the one whose blackbody gains have the same red / blue ratio as the wanted gains, the tint is
     * the green that is still left over.
     *
     * @param wanted       gains (R, G, B) to reach, only their ratios matter
     * @param neutralKelvin the temperature at which the shader's gains are 1
     * @param neutralTint   the tint at which the shader leaves the green alone
     */
    private static WhiteBalance solve(double[] wanted, double neutralKelvin, double neutralTint) {
        double target = wanted[0] / wanted[2];

        // red / blue of the gains grows with the temperature: bisection in log space over the slider's range
        double low = Math.log(GradeParam.TEMPERATURE.getMinVal());
        double high = Math.log(GradeParam.TEMPERATURE.getMaxVal());
        for (int i = 0; i < SEARCH_STEPS; i++) {
            double mid = (low + high) / 2.0;
            double[] gains = gains(Math.exp(mid), neutralKelvin);
            if (gains[0] / gains[2] < target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        double kelvin = Math.exp((low + high) / 2.0);

        // green gain still needed on top of the blackbody, as the shader's tint: g *= 2^(-tint / 100 * TINT_STOPS)
        double[] gains = gains(kelvin, neutralKelvin);
        double scale = Math.sqrt(gains[0] * gains[2] / (wanted[0] * wanted[2]));
        double green = scale * wanted[1] / gains[1];
        double tint = neutralTint - 100.0 * log2(green) / TINT_STOPS;

        return new WhiteBalance((float) kelvin, (float) Math.clamp(tint, -100.0, 100.0));
    }

    /**
     * Linear gains from the neutral temperature to this one (fragment.frag: whiteBalanceGains).
     */
    private static double[] gains(double kelvin, double neutralKelvin) {
        double[] neutral = kelvinToRgb(neutralKelvin);
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

    public static double srgbToLinear(double value) {
        double v = Math.clamp(value, 0.0, 1.0);
        return v < 0.04045 ? v * 0.0773993808 : Math.pow(v * 0.9478672986 + 0.0521327014, 2.4);
    }

    public static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    public record WhiteBalance(float temperature, float tint) {
    }
}
