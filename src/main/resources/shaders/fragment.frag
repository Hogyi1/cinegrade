#version 430

// region Inputs: uniform block, view transform, texture

// multiple of 16
layout (std140, binding = 0) uniform BasicBlock {
    float exposure; // offset 0 - 4 bytes   // in stops -5, 5
    float temp; // offset 4 - 4 bytes   // 2000 - 12000
    float tint; // offset 8 - 4 bytes   // -100, 100
    float contrast; // offset 12 - 4 bytes   // -100, 100

    float highlights; // offset 16 - 4 bytes   // -100, 100
    float shadows; // offset 20 - 4 bytes   // -100, 100
    float whites; // offset 24 - 4 bytes   // -100, 100
    float blacks; // offset 28 - 4 bytes   // -100, 100

    float vibrance; // offset 32 - 4 bytes   // -100, 100
    float saturation; // offset 36 - 4 bytes   // -100, 100
    float asShotTemperature; // offset 40 - Kelvin the image is decoded with (camera as shot, 5500 without one)
    float asShotTint; // offset 44 - tint of the same, the tint slider is relative to it

    // per color: x = hue, y = sat, z = lum shift (-100..100), w = padding. Names with '__' are reserved in GLSL
    vec4 hsl_red;
    vec4 hsl_orange;
    vec4 hsl_yellow;
    vec4 hsl_green;
    vec4 hsl_aqua;
    vec4 hsl_blue;
    vec4 hsl_purple;
    vec4 hsl_magenta;       /// Everythin is in a range of -100, 100
};

// View transform, not part of the edit: x = zoom (uv scale, 1 = whole image), yz = pan offset in [0,1] uv space
uniform vec3 uvZoom_OffsetX_OffsetY;

in vec2 vs_out_tex;
out vec4 fs_out_col;

uniform sampler2D texImage;
// 1: the texture is linear light (RAW from LibRaw, gamma 1.0), 0: sRGB encoded (JPEG/PNG/TIFF)
uniform int linearInput;
uniform int lutSize; // 0 = normal photo

// https://webgpufundamentals.org/webgpu/lessons/webgpu-3dlut.html
vec3 latticeColor() {
    ivec2 p = ivec2(floor(gl_FragCoord.xy));
    return vec3(p.x, p.y % lutSize, p.y / lutSize) / float(lutSize - 1);
}
// endregion

// region Constants: hue centers, luma weights, eye tuned strengths

const float PI2 = 6.2831853071;
const float red = 0.0 / 360.0;
const float orange = 30.0 / 360.0;
const float yellow = 60.0 / 360.0;
const float green = 120.0 / 360.0;
const float aqua = 180.0 / 360.0;
const float blue = 240.0 / 360.0;
const float purple = 270.0 / 360.0;
const float magenta = 300 / 360.0;
const float red2 = 360 / 360;
const vec3 rec709 = vec3(0.2126, 0.7152, 0.0722);

// Eye tuned values
// Tint - magenta gain scale factor for green channel
const float TINT_STOPS = 1.0 - 0.273612;
//White balance - how aggressively shift per EV the red and blues - applyTemperature
const float WB_STRENGHT = 0.8915;
// Daylight kelvin - neutral when the photo has no as-shot white balance
const float WB_NEUTRAL_KELVIN = 5500.0;
// exponent on the Kelvin gains: 1 = full blackbody correction. Keep both at 1.0: WhiteBalancer.java estimates
// the as-shot temperature with the full model, otherwise the presets (e.g. Daylight) no longer match the camera
const float WB_KELVIN_STRENGTH = 1.0;
// above neutral the blackbody barely changes
const float WB_WARM_KELVIN_STRENGTH = 1.0;
// Contrast: slope at the pivot is 2^(contrast/100 * C_STRENGHT)
const float C_STRENGHT = 1.2;
// sRGB middle grey (18% linear), stays in place Gamma
const float C_PIVOT = 0.18;
// Tones: how far luma moves at the peak of each mask (slider at +-100)
const float BLACKS_STRENGTH = 0.15; // at x = 0
const float SHADOWS_STRENGTH = 0.45; // * 0.42 mask peak -> +-0.19 at x = 0.25
const float HIGHLIGHTS_STRENGTH = 0.45; // * 0.42 mask peak -> +-0.19 at x = 0.75
const float WHITES_STRENGTH = 0.22; // at x = 1
const float TONE_CHANNEL_MIX = 0.35; // share of the per-channel add vs. the luma ratio scale

const float HSL_LUM_STOPS = 1.0;
const float HSL_HUE_RANGE = 30.0 / 360.0; // 360 degree
// endregion

// #region Color spaces: sRGB <-> linear, luminance

// https://physicallybased.info/tools/
vec4 srgb_to_linear(vec3 sRGB) {
    vec3 clamped = clamp(sRGB, 0.0, 1.0);

    vec3 linear = mix(
        pow(clamped * 0.9478672986 + 0.0521327014, vec3(2.4)),
        clamped * 0.0773993808,
        lessThan(clamped, vec3(0.04045))
    );

    return vec4(linear, 1.0);
}

vec4 linear_to_srgb(vec3 linear) {
    vec3 sRGB = mix(
        1.055 * pow(linear, vec3(0.4166666667)) - 0.055,
        linear * 12.92,
        lessThan(linear, vec3(0.0031308))
    );

    return vec4(sRGB, 1.0);
}

float getLuminance(vec3 rgb) {
    return dot(rgb, rec709);
}

// #endregion

// region Color spaces: HSV / HSL / hue rotation (for the HSL mixer)

// https://www.shadertoy.com/view/XljGzV
// Source - https://stackoverflow.com/a/17897228
// Posted by sam hocevar, modified by community. See post 'Timeline' for change history
// Retrieved 2026-11-01, License - CC BY-SA 4.0

// All components are in the range [0…1], including hue.
vec3 rgb2hsv(vec3 c)
{
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb2hsl(in vec3 c) {
    float h = 0.0;
    float s = 0.0;
    float l = 0.0;
    float r = c.r;
    float g = c.g;
    float b = c.b;
    float cMin = min(r, min(g, b));
    float cMax = max(r, max(g, b));

    l = (cMax + cMin) / 2.0;
    if (cMax > cMin) {
        float cDelta = cMax - cMin;

        //s = l < .05 ? cDelta / ( cMax + cMin ) : cDelta / ( 2.0 - ( cMax + cMin ) ); Original
        s = l < 0.5 ? cDelta / (cMax + cMin) : cDelta / (2.0 - (cMax + cMin));

        if (r == cMax) {
            h = (g - b) / cDelta;
        } else if (g == cMax) {
            h = 2.0 + (b - r) / cDelta;
        } else {
            h = 4.0 + (r - g) / cDelta;
        }

        if (h < 0.0) {
            h += 6.0;
        }
        h = h / 6.0;
    }
    return vec3(h, s, l);
}

vec3 hsl2rgb(in vec3 c)
{
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);

    return c.z + c.y * (rgb - 0.5) * (1.0 - abs(2.0 * c.z - 1.0));
}

// endregion

// region Linear light adjustments: white balance

vec4 applyTemperature(vec3 color, float temp, float tint) {
    float before = dot(color, rec709);

    float w = log2(temp / 5500.0);
    float t = tint / 100.0;
    color.r *= exp2(w * WB_STRENGHT);
    color.g *= exp2(-t * TINT_STOPS);
    color.b *= exp2(-w * WB_STRENGHT);

    // compare the luminance
    color *= before / max(dot(color, rec709), 1e-5);
    return vec4(color, 1.0);
}

// [AI-GENERATED METHOD]
// Tanner Helland: blackbody color of a Kelvin temperature, curve fitted, sRGB [0..1], valid 1000..40000 K
// https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html
// Divides by 100.0 instead of the original integer '\ 100', so the slider moves smoothly instead of in 100 K steps
vec3 kelvinToRgb_Helland(float kelvin) {
    float t = clamp(kelvin, 1000.0, 40000.0) / 100.0;
    vec3 c;

    if (t <= 66.0) {
        c.r = 255.0;
        c.g = 99.4708025861 * log(t) - 161.1195681661;
    } else {
        c.r = 329.698727446 * pow(t - 60.0, -0.1332047592);
        c.g = 288.1221695283 * pow(t - 60.0, -0.0755148492);
    }

    if (t >= 66.0) {
        c.b = 255.0;
    } else if (t <= 19.0) {
        c.b = 0.0;
    } else {
        c.b = 138.5177312231 * log(t - 10.0) - 305.0447927307;
    }

    return clamp(c / 255.0, 0.0, 1.0);
}

// Color temperature (sRGB) stuff
// Copyright (C) 2014 by Benjamin 'BeRo' Rosseaux, CC0 license http://creativecommons.org/publicdomain/zero/1.0/
// https://help.pixera.one/en_US/glsl-effects/colortemperatureglsl
// Valid from 1000 to 40000 K (and additionally 0 for pure full white)
vec3 kelvinToRgb_BeRo(const in float temperature) {
    // Values from: http://blenderartists.org/forum/showthread.php?270332-OSL-Goodness&p=2268693&viewfull=1#post2268693
    mat3 m = (temperature <= 6500.0) ? mat3(vec3(0.0, -2902.1955373783176, -8257.7997278925690),
    vec3(0.0, 1669.5803561666639, 2575.2827530017594),
    vec3(1.0, 1.3302673723350029, 1.8993753891711275)) :
    mat3(vec3(1745.0425298314172, 1216.6168361476490, -8257.7997278925690),
    vec3(-2666.3474220535695, -2173.1012343082230, 2575.2827530017594),
    vec3(0.55995389139931482, 0.70381203140554553, 1.8993753891711275));
    return mix(clamp(vec3(m[0] / (vec3(clamp(temperature, 1000.0, 40000.0)) + m[1]) + m[2]), vec3(0.0), vec3(1.0)), vec3(1.0), smoothstep(1000.0, 0.0, temperature));
}

// [AI-GENERATED METHOD]
// White balance gains from a blackbody color. temp is the Kelvin of the scene light (like Lightroom), so the image is
// divided by that light's color: a low temp (orange light) is corrected towards blue, a high temp towards orange.
// Dividing by the neutral temperature's color too keeps the neutral (as shot) at no change. The blackbody colors are sRGB
// encoded, so they are linearized first, because the gains multiply linear light
// Tint (green -, magenta +) scales the green channel, Tanner Helland:
// https://tannerhelland.com/2014/07/01/simple-algorithms-adjusting-image-temperature-tint.html
vec3 whiteBalanceGains(vec3 neutralRgb, vec3 lightRgb, float neutral, float temp, float tint) {
    vec3 gains = srgb_to_linear(neutralRgb).rgb / max(srgb_to_linear(lightRgb).rgb, vec3(1e-4));
    // the full correction is +7 stops of blue at 2000 K, the strengths scale it in stops. The gains are 1 (0 stops)
    // at neutral, so switching the strength there has no jump
    float strength = temp > neutral ? WB_WARM_KELVIN_STRENGTH : WB_KELVIN_STRENGTH;
    gains = exp2(log2(gains) * strength);
    gains.g *= exp2(-(tint / 100.0) * TINT_STOPS);
    return gains;
}

// The decoded image is already balanced for this temperature / tint, the sliders move relative to it
float neutralTemperature() {
    return asShotTemperature > 0.0 ? asShotTemperature : WB_NEUTRAL_KELVIN;
}

// [AI-GENERATED METHOD]
vec4 applyTemperature_Helland(vec3 color, float temp, float tint) {
    float before = dot(color, rec709);
    float neutral = neutralTemperature();
    color *= whiteBalanceGains(kelvinToRgb_Helland(neutral), kelvinToRgb_Helland(temp), neutral, temp, tint - asShotTint);
    // keep the luminance, white balance only changes the color
    color *= before / max(dot(color, rec709), 1e-5);
    return vec4(color, 1.0);
}

// [AI-GENERATED METHOD]
vec4 applyTemperature_BeRo(vec3 color, float temp, float tint) {
    float before = dot(color, rec709);
    float neutral = neutralTemperature();
    color *= whiteBalanceGains(kelvinToRgb_BeRo(neutral), kelvinToRgb_BeRo(temp), neutral, temp, tint - asShotTint);
    // keep the luminance, white balance only changes the color
    color *= before / max(dot(color, rec709), 1e-5);
    return vec4(color, 1.0);
}

// endregion

// region Display (sRGB) tone adjustments: contrast, highlights, shadows, whites, blacks

// Power S-curve around C_PIVOT: 0 stays 0, 1 stays 1, the pivot stays put, and both halves meet with the same
// slope k, so there is no kink. k > 1 steepens the midtones (more contrast), k < 1 flattens them.
vec3 applyContrast(vec3 c) {
    float k = exp2(contrast / 100.0 * C_STRENGHT);
    vec3 x = clamp(c, 0.0, 1.0);
    vec3 lo = C_PIVOT * pow(x / C_PIVOT, vec3(k));                                // below the pivot
    vec3 hi = 1.0 - (1.0 - C_PIVOT) * pow((1.0 - x) / (1.0 - C_PIVOT), vec3(k));  // above the pivot
    return mix(lo, hi, step(C_PIVOT, x));
}

// [AI-GENERATED METHOD] (fix: one mechanism per slider, named strengths, no ratio blow up near black)
vec3 applyTones(vec3 c) {
    float b = blacks / 100.0;
    float s = shadows / 100.0;
    float h = highlights / 100.0;
    float w = whites / 100.0;

    c = clamp(c, 0.0, 1.0);

    // Partition of Unity across [0..1] using Bernstein basis polynomials:
    // Every tonal zone smoothly hands off to the next without kinks
    float x = dot(c, rec709);
    float invX = 1.0 - x;

    float maskBlacks = invX * invX * invX * invX;         // (1-x)^4 : 0.00 - 0.20
    float maskShadows = 4.0 * x * invX * invX * invX;      // 4x(1-x)^3 : peaks at 0.25
    float maskHighlights = 4.0 * x * x * x * invX;            // 4x^3(1-x) : peaks at 0.75
    float maskWhites = x * x * x * x;                     // x^4 : 0.80 - 1.00

    float offset = (b * BLACKS_STRENGTH * maskBlacks)
    + (s * SHADOWS_STRENGTH * maskShadows)
    + (h * HIGHLIGHTS_STRENGTH * maskHighlights)
    + (w * WHITES_STRENGTH * maskWhites);

    // Apply to RGB while preserving color saturation in shadows/highlights
    float newLuma = clamp(x + offset, 0.0, 1.0);
    vec3 ratioScaled = clamp(c * (newLuma / max(x, 1e-4)), 0.0, 1.0);

    // Blend between per-channel curve and luma-ratio so highlights don't oversaturate when pulled down.
    // Near black newLuma / x explodes (x = 0.01 lifted to 0.2 is a 20x gain), so below luma 0.15 the
    // per-channel add takes over; there is hardly any color down there to preserve anyway
    vec3 channelAdjusted = clamp(c + offset, 0.0, 1.0);
    float channelMix = mix(1.0, TONE_CHANNEL_MIX, smoothstep(0.0, 0.15, x));
    return mix(ratioScaled, channelAdjusted, channelMix);
}

// endregion

// region Color adjustments: vibrance, saturation
// Saturation: 0.0 = szürke, 1.0 = eredeti, >1.0 = túltelített
vec3 applySaturation(vec3 c) {
    float grey = dot(c, rec709);
    return mix(vec3(grey), c, 1.0 + (saturation / 100.0));
}

vec3 applyVibrance(vec3 c) {
    float luma = getLuminance(c);
    
    // Megkeressük a szín RGB csatornáinak maximumát és minimumát
    float maxColor = max(c.r, max(c.g, c.b));
    float minColor = min(c.r, min(c.g, c.b));
    
    // Telítettség mértéke egyedi képpontra
    float colorSaturation = maxColor - minColor;

    // 1. Normalizáljuk a bemenetet -1.0 és 1.0 közé
    float v = vibrance / 100.0;
    
    // 2. Kiszámítjuk a skálázási tényezőt (amount)
    // Ha v > 0: a kevésbé telt részeken erősebb (1.0 - colorSaturation)
    // Ha v < 0: lineárisan húz a szürke felé, függetlenül az eredeti telítettségtől
    float amount = (v >= 0.0) ? v * (1.0 - colorSaturation) : v;
    
    // 3. A mix harmadik paramétere határozza meg a végső egyensúlyt:
    // amount = 0.0  -> mix(luma, c, 1.0) -> Eredeti szín
    // amount = 1.0  -> mix(luma, c, 2.0) -> Erősen vibráló szín
    // amount = -1.0 -> mix(luma, c, 0.0) -> Teljesen szürke (luma)
    return mix(vec3(luma), c, 1.0 + amount);
}

vec3 applyHsl(vec3 c) {
vec3 hsv = rgb2hsv(c);

    const float centers[9] = float[9](red, orange, yellow, green, aqua, blue, purple, magenta, red2);
    vec4 bands[9] = vec4[9](hsl_red, hsl_orange, hsl_yellow, hsl_green, hsl_aqua, hsl_blue, hsl_purple, hsl_magenta, hsl_red);

    vec4 shift = vec4(0.0);

    for (int i = 0; i < 8; ++i) {
        // Check if the hue is within the current band
        if (hsv.x >= centers[i] && hsv.x < centers[i + 1]) {
            float t = (hsv.x - centers[i]) / (centers[i + 1] - centers[i]);
            shift = mix(bands[i], bands[i + 1], smoothstep(0.0, 1.0, t)) / 100.0;
            break;
        }
    }
    
    // Szürke, deszaturált pixelek védelme
    float amount = smoothstep(0.0, 0.15, hsv.y);

    // 1. Hue eltolás (körbefordulással)
    hsv.x = fract(hsv.x + shift.x * amount * HSL_HUE_RANGE);

    // 2. Saturation eltolás
    hsv.y = clamp(hsv.y * (1.0 + shift.y * amount), 0.0, 1.0);

    // 3. Luminance (expozíció stop skálázás lineáris RGB-szerűen)
    c = hsv2rgb(hsv) * exp2(shift.z * amount * HSL_LUM_STOPS);

    return c;
}
// endregion

void main()
{

    vec2 uv = uvZoom_OffsetX_OffsetY.yz + vs_out_tex * uvZoom_OffsetX_OffsetY.x;
    vec3 source = lutSize > 0 ? latticeColor() : texture(texImage, uv).rgb;
    vec3 color = linearInput == 1 ? source : srgb_to_linear(source).rgb;

    // 1. white balance (linear)
    color = applyTemperature_BeRo(color, temp, tint).rgb;
    // 2. exposure (linear), in stops: +1 = twice as bright
    color *= exp2(exposure);
    vec3 c = linear_to_srgb(color).rgb;

    // 3. contrast (display), then highlights / shadows / whites / blacks
    c = applyContrast(c);
    c = applyTones(c);
    c = applyVibrance(c);
    c = applyHsl(c);
    c = applySaturation(c);

    fs_out_col = vec4(clamp(c, 0.0, 1.0), 1.0);
}
