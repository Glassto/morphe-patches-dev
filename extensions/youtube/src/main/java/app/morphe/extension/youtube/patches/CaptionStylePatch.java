/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.CaptioningManager.CaptionStyle;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.CaptionStyleRenderer.Params;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Overrides the look of captions.
 * <p>
 * YouTube reads the system caption style ({@link CaptionStyle}) to draw captions.
 * The patch hooks every read of the style fields, so no hidden Android API or
 * object construction is needed.
 */
@SuppressWarnings("unused")
public class CaptionStylePatch {

    /**
     * Pen id that is never declared by a timed text document.
     * Looking it up returns the default pen, which uses the user caption style.
     * Same value as used by {@link TranslatedCaptionPensPatch}.
     */
    private static final int DEFAULT_PEN_ID = Integer.MIN_VALUE;

    /**
     * Corner radius that YouTube uses for the caption background.
     */
    private static final int YOUTUBE_CORNER_RADIUS_DP = 4;

    /**
     * Name of the font file that the custom font patch saves in the app files.
     */
    private static final String CUSTOM_FONT_FILE_NAME = "morphe_custom_font";

    /**
     * How many parent views to stop clipping the caption background at.
     */
    private static final int ANCESTOR_CLIP_DEPTH = 6;

    private static final int DEFAULT_BORDER_COLOR = 0x40FFFFFF;
    private static final int DEFAULT_SHADOW_COLOR = 0x80000000;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_BACKGROUND_COLOR = 0xBF000000;
    private static final int DEFAULT_WINDOW_COLOR = 0x00000000;
    private static final int DEFAULT_EDGE_COLOR = 0xFF000000;

    public enum CaptionFontFamily {
        /** Keep the font YouTube normally uses. */
        DEFAULT(null),
        SANS_SERIF("sans-serif"),
        SANS_SERIF_CONDENSED("sans-serif-condensed"),
        SANS_SERIF_MEDIUM("sans-serif-medium"),
        SERIF("serif"),
        MONOSPACE("monospace"),
        CASUAL("casual"),
        CURSIVE("cursive"),
        SMALL_CAPS("sans-serif-smallcaps"),
        /** Font file imported by the custom font patch. Family is not used. */
        CUSTOM_FILE(null);

        /**
         * Android system font family name.
         */
        @Nullable
        public final String family;

        CaptionFontFamily(@Nullable String family) {
            this.family = family;
        }
    }

    public enum CaptionEdgeType {
        NONE(CaptionStyle.EDGE_TYPE_NONE),
        OUTLINE(CaptionStyle.EDGE_TYPE_OUTLINE),
        DROP_SHADOW(CaptionStyle.EDGE_TYPE_DROP_SHADOW),
        RAISED(CaptionStyle.EDGE_TYPE_RAISED),
        DEPRESSED(CaptionStyle.EDGE_TYPE_DEPRESSED);

        /**
         * {@link CaptionStyle} edge type constant.
         */
        public final int edgeType;

        CaptionEdgeType(int edgeType) {
            this.edgeType = edgeType;
        }
    }

    public enum CaptionBackgroundStyle {
        /** Plain filled background, like YouTube. */
        SOLID,
        /** Translucent gradient with a light rim. */
        GLASS,
        /** No background fill. */
        NONE
    }

    /**
     * Hooks that were called at least once, to log each of them only once.
     */
    private static final Set<String> calledHooks = ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<CaptionFontFamily, Typeface> familyTypefaces = new ConcurrentHashMap<>();

    private static Typeface customFileTypeface;
    private static long customFileModified = -1;

    private static boolean isEnabled() {
        return Settings.CAPTION_STYLE_ENABLED.get();
    }

    private static void logFirstCall(String hook, Object result) {
        if (calledHooks.add(hook)) {
            Logger.printDebug(() -> "Caption style hook called first time: " + hook + " result: " + result);
        }
    }

    /**
     * Injection point. Replaces a read of {@code CaptionStyle.foregroundColor}.
     */
    public static int getForegroundColor(int original) {
        try {
            if (isEnabled()) {
                int result = CaptionStyleUtils.parseColor(Settings.CAPTION_TEXT_COLOR.get(), DEFAULT_TEXT_COLOR);
                logFirstCall("getForegroundColor", result);
                return result;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getForegroundColor failure", ex);
        }
        return original;
    }

    /**
     * Injection point. Replaces a read of {@code CaptionStyle.backgroundColor}.
     */
    public static int getBackgroundColor(int original) {
        try {
            if (isEnabled()) {
                int result = CaptionStyleUtils.parseColor(Settings.CAPTION_BACKGROUND_COLOR.get(), DEFAULT_BACKGROUND_COLOR);
                logFirstCall("getBackgroundColor", result);
                return result;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getBackgroundColor failure", ex);
        }
        return original;
    }

    /**
     * Injection point. Replaces a read of {@code CaptionStyle.windowColor}.
     */
    public static int getWindowColor(int original) {
        try {
            if (isEnabled()) {
                int result = CaptionStyleUtils.parseColor(Settings.CAPTION_WINDOW_COLOR.get(), DEFAULT_WINDOW_COLOR);
                logFirstCall("getWindowColor", result);
                return result;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getWindowColor failure", ex);
        }
        return original;
    }

    /**
     * Injection point. Replaces a read of {@code CaptionStyle.edgeColor}.
     */
    public static int getEdgeColor(int original) {
        try {
            if (isEnabled()) {
                int result = CaptionStyleUtils.parseColor(Settings.CAPTION_EDGE_COLOR.get(), DEFAULT_EDGE_COLOR);
                logFirstCall("getEdgeColor", result);
                return result;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getEdgeColor failure", ex);
        }
        return original;
    }

    /**
     * Injection point. Replaces a read of {@code CaptionStyle.edgeType}.
     */
    public static int getEdgeType(int original) {
        try {
            if (isEnabled()) {
                int result = Settings.CAPTION_EDGE_TYPE.get().edgeType;
                logFirstCall("getEdgeType", result);
                return result;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getEdgeType failure", ex);
        }
        return original;
    }

    /**
     * Injection point. Replaces the result of {@code CaptionStyle.hasXXX()}.
     * With a custom style every color and the edge are always specified.
     */
    public static boolean isStylePropertySpecified(boolean original) {
        try {
            return original || isEnabled();
        } catch (Exception ex) {
            Logger.printException(() -> "isStylePropertySpecified failure", ex);
            return original;
        }
    }

    /**
     * Injection point. Called with the typeface of the caption window, and with the typeface of the system caption style.
     *
     * @param original Typeface that YouTube uses. Can be null for the default typeface.
     * @return The typeface of the custom style.
     */
    @Nullable
    public static Typeface getTypeface(@Nullable Typeface original) {
        try {
            if (!isEnabled()) return original;

            final Typeface base = getBaseTypeface(original);

            final int weight = Settings.CAPTION_FONT_WEIGHT.get();
            final boolean italic = Settings.CAPTION_FONT_ITALIC.get();
            if (!CaptionStyleUtils.isStyled(weight, italic)) {
                return base;
            }

            final Typeface styleBase = base != null ? base : Typeface.DEFAULT;
            if (Build.VERSION.SDK_INT >= 28) {
                return Typeface.create(styleBase, CaptionStyleUtils.clamp(weight, 1, 1000), italic);
            }
            return Typeface.create(styleBase, CaptionStyleUtils.styleFlags(weight, italic));
        } catch (Exception ex) {
            Logger.printException(() -> "getTypeface failure", ex);
            return original;
        }
    }

    @Nullable
    private static Typeface getBaseTypeface(@Nullable Typeface original) {
        final CaptionFontFamily family = Settings.CAPTION_FONT_FAMILY.get();

        if (family == CaptionFontFamily.CUSTOM_FILE) {
            final Typeface custom = getCustomFileTypeface();
            return custom != null ? custom : original;
        }

        if (family.family == null) {
            return original;
        }

        Typeface typeface = familyTypefaces.get(family);
        if (typeface == null) {
            typeface = Typeface.create(family.family, Typeface.NORMAL);
            familyTypefaces.put(family, typeface);
        }
        return typeface;
    }

    /**
     * @return The font that the custom font patch imported, or null if there is none.
     */
    @Nullable
    private static synchronized Typeface getCustomFileTypeface() {
        try {
            final File file = new File(Utils.getContext().getFilesDir(), CUSTOM_FONT_FILE_NAME);
            if (!file.isFile()) return null;

            final long modified = file.lastModified();
            if (customFileTypeface == null || modified != customFileModified) {
                customFileTypeface = Build.VERSION.SDK_INT >= 26
                        ? new Typeface.Builder(file).build()
                        : Typeface.createFromFile(file);
                customFileModified = modified;
            }
            return customFileTypeface;
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not load the custom font file", ex);
            return null;
        }
    }

    /**
     * Injection point. Replaces {@code TextPaint.setTypeface(Typeface)} of a caption text line.
     */
    public static void setTypeface(TextPaint paint, @Nullable Typeface typeface) {
        try {
            paint.setTypeface(getTypeface(typeface));
            if (isEnabled()) {
                paint.setLetterSpacing(Settings.CAPTION_LETTER_SPACING.get() / 100f);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "setTypeface failure", ex);
            paint.setTypeface(typeface);
        }
    }

    /**
     * Injection point. Replaces the corner radius of the caption background.
     *
     * @param original Radius in pixels that YouTube uses.
     */
    public static float getCornerRadius(float original) {
        try {
            if (isEnabled()) {
                final int radiusDp = Settings.CAPTION_CORNER_RADIUS.get();
                if (radiusDp != YOUTUBE_CORNER_RADIUS_DP) {
                    return CaptionStyleUtils.dpToPx(radiusDp, getDensity());
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getCornerRadius failure", ex);
        }
        return original;
    }

    /**
     * Injection point. Replaces the size of the caption text edge (outline width or shadow offset).
     *
     * @param original Size in pixels that YouTube uses.
     */
    public static float getEdgeSize(float original) {
        try {
            if (isEnabled()) {
                return CaptionStyleUtils.scaleEdgeSize(original, Settings.CAPTION_EDGE_STRENGTH.get());
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getEdgeSize failure", ex);
        }
        return original;
    }

    /**
     * Injection point. Replaces the alpha of the caption background color.
     * YouTube skips drawing the background if the alpha is 0, but a border or shadow
     * can be shown without a background color.
     */
    public static int getBackgroundAlpha(int alpha) {
        try {
            if (alpha == 0 && isEnabled() && createBackgroundParams(1f).hasEffects()) {
                return 1;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getBackgroundAlpha failure", ex);
        }
        return alpha;
    }

    /**
     * Injection point. Replaces {@code View.setLayerType(int, Paint)} of a caption text line.
     * <p>
     * A software or hardware layer is as big as the view, and clips a padding or shadow
     * around the background. A view without a layer can draw outside of its bounds.
     */
    public static void setLayerType(View view, int layerType, @Nullable Paint paint) {
        int type = layerType;
        try {
            if (layerType == View.LAYER_TYPE_SOFTWARE && isEnabled()
                    && createBackgroundParams(1f).hasEffects() && !isEmbossEdge()) {
                // Raised and depressed edges require a software layer.
                type = View.LAYER_TYPE_NONE;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "setLayerType failure", ex);
        }
        view.setLayerType(type, paint);
    }

    /**
     * Injection point. Replaces {@code Canvas.drawPath(Path, Paint)} that draws the caption background.
     *
     * @param view The caption text line.
     * @param path Shape of the background, around the text.
     * @param fill Paint with the background color.
     */
    public static void drawBackground(View view, Canvas canvas, Path path, Paint fill) {
        try {
            if (isEnabled()) {
                final Params params = createBackgroundParams(view.getResources().getDisplayMetrics().density);
                if (params.hasEffects()) {
                    disableAncestorClipping(view);
                }
                CaptionStyleRenderer.draw(canvas, path, fill, params);
                return;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "drawBackground failure", ex);
        }
        canvas.drawPath(path, fill);
    }

    /**
     * @param density Screen density to convert dp to pixels.
     * @return The options of the caption background, as set by the user.
     */
    public static Params createBackgroundParams(float density) {
        if (!isEnabled()) {
            return new Params(CaptionBackgroundStyle.SOLID, 0, 0, 0, 0, 0, density);
        }

        return new Params(
                Settings.CAPTION_BACKGROUND_STYLE.get(),
                CaptionStyleUtils.dpToPx(Settings.CAPTION_BACKGROUND_PADDING.get(), density),
                CaptionStyleUtils.dpToPx(Settings.CAPTION_BORDER_WIDTH.get() / 10f, density),
                CaptionStyleUtils.parseColor(Settings.CAPTION_BORDER_COLOR.get(), DEFAULT_BORDER_COLOR),
                CaptionStyleUtils.dpToPx(Settings.CAPTION_SHADOW_SIZE.get(), density),
                CaptionStyleUtils.parseColor(Settings.CAPTION_SHADOW_COLOR.get(), DEFAULT_SHADOW_COLOR),
                density
        );
    }

    /**
     * @return If the custom caption style is turned on.
     */
    public static boolean isCustomStyleEnabled() {
        return isEnabled();
    }

    private static boolean isEmbossEdge() {
        final CaptionEdgeType edge = Settings.CAPTION_EDGE_TYPE.get();
        return edge == CaptionEdgeType.RAISED || edge == CaptionEdgeType.DEPRESSED;
    }

    private static float getDensity() {
        return Utils.getContext().getResources().getDisplayMetrics().density;
    }

    /**
     * A parent view can clip the caption background if it is drawn outside of the caption text line.
     */
    private static void disableAncestorClipping(View view) {
        ViewParent parent = view.getParent();
        for (int i = 0; i < ANCESTOR_CLIP_DEPTH && parent instanceof ViewGroup; i++) {
            final ViewGroup group = (ViewGroup) parent;
            if (group.getClipChildren()) {
                group.setClipChildren(false);
            }
            parent = group.getParent();
        }
    }

    /**
     * Injection point. Called with the pen id of a timed text element.
     * <p>
     * Pens carry the text style that a channel defines for its captions (colors, background).
     * Ignoring the pens shows every caption with the caption style of the user.
     *
     * @param penId Pen id of a timed text element.
     * @return The pen id to use.
     */
    public static int getPenId(int penId) {
        try {
            if (Settings.CAPTION_IGNORE_CREATOR_STYLE.get()) {
                return DEFAULT_PEN_ID;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getPenId failure", ex);
        }
        return penId;
    }
}
