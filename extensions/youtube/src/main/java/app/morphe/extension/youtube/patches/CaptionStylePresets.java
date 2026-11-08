/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import app.morphe.extension.youtube.patches.CaptionStylePatch.CaptionBackgroundStyle;
import app.morphe.extension.youtube.patches.CaptionStylePatch.CaptionEdgeType;
import app.morphe.extension.youtube.patches.CaptionStylePatch.CaptionFontFamily;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Ready made caption styles. A preset sets all the custom caption style options at once.
 */
public enum CaptionStylePresets {

    /** Same look as YouTube, with the custom style turned on. */
    CLASSIC(new Values(
            CaptionFontFamily.DEFAULT, 400, false, 0,
            "#FFFFFFFF", CaptionEdgeType.NONE, "#FF000000", 100,
            CaptionBackgroundStyle.SOLID, "#BF000000", "#00000000",
            4, 0, 0, "#40FFFFFF", 0, "#80000000")),

    /** Translucent glass background with a light rim and a soft shadow. */
    GLASS(new Values(
            CaptionFontFamily.DEFAULT, 500, false, 0,
            "#FFFFFFFF", CaptionEdgeType.DROP_SHADOW, "#99000000", 60,
            CaptionBackgroundStyle.GLASS, "#33FFFFFF", "#00000000",
            14, 4, 0, "#40FFFFFF", 8, "#66000000")),

    /** Soft yellow text with a black outline, no background. */
    CINEMA(new Values(
            CaptionFontFamily.SANS_SERIF_MEDIUM, 400, false, 0,
            "#FFFFE066", CaptionEdgeType.OUTLINE, "#FF000000", 100,
            CaptionBackgroundStyle.NONE, "#00000000", "#00000000",
            4, 0, 0, "#40FFFFFF", 0, "#80000000")),

    /** Bold white text on an opaque black background. */
    HIGH_CONTRAST(new Values(
            CaptionFontFamily.DEFAULT, 700, false, 0,
            "#FFFFFFFF", CaptionEdgeType.NONE, "#FF000000", 100,
            CaptionBackgroundStyle.SOLID, "#FF000000", "#00000000",
            2, 2, 0, "#40FFFFFF", 0, "#80000000")),

    /** White text with a soft shadow, no background. */
    MINIMAL(new Values(
            CaptionFontFamily.DEFAULT, 400, false, 0,
            "#FFFFFFFF", CaptionEdgeType.DROP_SHADOW, "#B3000000", 150,
            CaptionBackgroundStyle.NONE, "#00000000", "#00000000",
            4, 0, 0, "#40FFFFFF", 0, "#80000000"));

    /**
     * All the options of a preset.
     */
    private static final class Values {
        final CaptionFontFamily fontFamily;
        final int fontWeight;
        final boolean italic;
        final int letterSpacing;
        final String textColor;
        final CaptionEdgeType edgeType;
        final String edgeColor;
        final int edgeStrength;
        final CaptionBackgroundStyle backgroundStyle;
        final String backgroundColor;
        final String windowColor;
        final int cornerRadius;
        final int padding;
        final int borderWidth;
        final String borderColor;
        final int shadowSize;
        final String shadowColor;

        Values(CaptionFontFamily fontFamily, int fontWeight, boolean italic, int letterSpacing,
               String textColor, CaptionEdgeType edgeType, String edgeColor, int edgeStrength,
               CaptionBackgroundStyle backgroundStyle, String backgroundColor, String windowColor,
               int cornerRadius, int padding, int borderWidth, String borderColor,
               int shadowSize, String shadowColor) {
            this.fontFamily = fontFamily;
            this.fontWeight = fontWeight;
            this.italic = italic;
            this.letterSpacing = letterSpacing;
            this.textColor = textColor;
            this.edgeType = edgeType;
            this.edgeColor = edgeColor;
            this.edgeStrength = edgeStrength;
            this.backgroundStyle = backgroundStyle;
            this.backgroundColor = backgroundColor;
            this.windowColor = windowColor;
            this.cornerRadius = cornerRadius;
            this.padding = padding;
            this.borderWidth = borderWidth;
            this.borderColor = borderColor;
            this.shadowSize = shadowSize;
            this.shadowColor = shadowColor;
        }
    }

    private final Values values;

    CaptionStylePresets(Values values) {
        this.values = values;
    }

    /**
     * Turns on the custom caption style and saves all options of this preset.
     */
    public void apply() {
        Settings.CAPTION_STYLE_ENABLED.save(true);
        saveValues();
    }

    /**
     * Turns off the custom caption style and restores all options to the default.
     */
    public static void reset() {
        Settings.CAPTION_STYLE_ENABLED.save(false);
        Settings.CAPTION_IGNORE_CREATOR_STYLE.resetToDefault();
        CLASSIC.saveValues();
    }

    private void saveValues() {
        final Values v = values;
        Settings.CAPTION_FONT_FAMILY.save(v.fontFamily);
        Settings.CAPTION_FONT_WEIGHT.save(v.fontWeight);
        Settings.CAPTION_FONT_ITALIC.save(v.italic);
        Settings.CAPTION_LETTER_SPACING.save(v.letterSpacing);
        Settings.CAPTION_TEXT_COLOR.save(v.textColor);
        Settings.CAPTION_EDGE_TYPE.save(v.edgeType);
        Settings.CAPTION_EDGE_COLOR.save(v.edgeColor);
        Settings.CAPTION_EDGE_STRENGTH.save(v.edgeStrength);
        Settings.CAPTION_BACKGROUND_STYLE.save(v.backgroundStyle);
        Settings.CAPTION_BACKGROUND_COLOR.save(v.backgroundColor);
        Settings.CAPTION_WINDOW_COLOR.save(v.windowColor);
        Settings.CAPTION_CORNER_RADIUS.save(v.cornerRadius);
        Settings.CAPTION_BACKGROUND_PADDING.save(v.padding);
        Settings.CAPTION_BORDER_WIDTH.save(v.borderWidth);
        Settings.CAPTION_BORDER_COLOR.save(v.borderColor);
        Settings.CAPTION_SHADOW_SIZE.save(v.shadowSize);
        Settings.CAPTION_SHADOW_COLOR.save(v.shadowColor);
    }
}
