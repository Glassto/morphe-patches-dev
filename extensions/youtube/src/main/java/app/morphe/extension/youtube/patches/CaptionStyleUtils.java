/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

/**
 * Pure helper functions of {@link CaptionStylePatch}. Has no Android or settings dependencies,
 * so it can be unit tested on the JVM.
 */
public final class CaptionStyleUtils {

    /**
     * Smallest and largest caption size scale, in percent.
     */
    public static final int MIN_FONT_SCALE_PERCENT = 25;
    public static final int MAX_FONT_SCALE_PERCENT = 400;

    /**
     * Smallest and largest edge strength, in percent.
     */
    public static final int MIN_EDGE_STRENGTH_PERCENT = 25;
    public static final int MAX_EDGE_STRENGTH_PERCENT = 300;

    /**
     * Alpha of a glass background if the background color is (almost) transparent.
     */
    private static final int GLASS_MIN_ALPHA = 0x33;

    /**
     * Typeface style flags, same values as {@code android.graphics.Typeface}.
     */
    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_BOLD_ITALIC = 3;

    private CaptionStyleUtils() {
    }

    /**
     * Parses {@code #RRGGBB} or {@code #AARRGGBB} (the leading '#' is optional).
     *
     * @return The color as an ARGB int. A 6 digit color is fully opaque.
     * If the string is invalid then {@code fallback} is returned.
     */
    public static int parseColor(String color, int fallback) {
        if (color == null) return fallback;

        String hex = color.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6 && hex.length() != 8) return fallback;

        try {
            long value = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                value |= 0xFF000000L;
            }
            return (int) value;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * @return The system caption scale multiplied by the user scale percentage.
     * The percentage is clamped to the supported range.
     */
    public static float scaleFontScale(float systemScale, int userPercent) {
        final int percent = clamp(userPercent, MIN_FONT_SCALE_PERCENT, MAX_FONT_SCALE_PERCENT);
        return systemScale * (percent / 100f);
    }

    /**
     * @return The edge size (outline width or shadow offset) multiplied by the user percentage.
     * The percentage is clamped to the supported range.
     */
    public static float scaleEdgeSize(float original, int userPercent) {
        final int percent = clamp(userPercent, MIN_EDGE_STRENGTH_PERCENT, MAX_EDGE_STRENGTH_PERCENT);
        return original * (percent / 100f);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @return The alpha channel of an ARGB color, 0 to 255.
     */
    public static int alpha(int color) {
        return color >>> 24;
    }

    /**
     * @return The ARGB color with a different alpha channel. Alpha is clamped to 0 to 255.
     */
    public static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    /**
     * @return The ARGB color with the alpha channel multiplied by a factor.
     */
    public static int scaleAlpha(int color, float factor) {
        return withAlpha(color, Math.round(alpha(color) * factor));
    }

    /**
     * @return The color of a glass background. Almost transparent colors get a minimum alpha,
     * otherwise the glass would be invisible.
     */
    public static int glassBase(int color) {
        return alpha(color) < GLASS_MIN_ALPHA ? withAlpha(color, GLASS_MIN_ALPHA) : color;
    }

    /**
     * @return The color at the top of a glass background. More opaque than the bottom.
     */
    public static int glassTop(int color) {
        return scaleAlpha(glassBase(color), 1.7f);
    }

    /**
     * @return The color at the bottom of a glass background. More transparent than the top.
     */
    public static int glassBottom(int color) {
        return scaleAlpha(glassBase(color), 0.55f);
    }

    /**
     * @return Typeface style flags for a weight and italic. Used if the platform
     * cannot create a typeface with an exact weight.
     */
    public static int styleFlags(int weight, boolean italic) {
        final boolean bold = weight >= 600;
        if (bold && italic) return STYLE_BOLD_ITALIC;
        if (bold) return STYLE_BOLD;
        if (italic) return STYLE_ITALIC;
        return STYLE_NORMAL;
    }

    /**
     * @return If a font weight and italic differ from a regular font.
     */
    public static boolean isStyled(int weight, boolean italic) {
        return italic || weight != 400;
    }

    /**
     * @return Density independent pixels as pixels.
     */
    public static float dpToPx(float dp, float density) {
        return dp * density;
    }
}
