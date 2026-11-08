/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.settings.preference;

import static app.morphe.extension.shared.StringRef.str;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.youtube.patches.CaptionStylePatch;
import app.morphe.extension.youtube.patches.CaptionStylePatch.CaptionEdgeType;
import app.morphe.extension.youtube.patches.CaptionStyleRenderer;
import app.morphe.extension.youtube.patches.CaptionStyleUtils;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Shows a sample caption that follows the caption style settings.
 * <p>
 * The background is drawn with the same code as the real captions.
 * The text edge is an approximation, since the real captions are drawn by YouTube.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class CaptionStylePreviewPreference extends Preference {

    private static final float BASE_TEXT_SIZE_SP = 18f;
    private static final float YOUTUBE_EDGE_FACTOR = 0.0625f;

    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_BACKGROUND_COLOR = 0xBF000000;
    private static final int DEFAULT_WINDOW_COLOR = 0x00000000;
    private static final int DEFAULT_EDGE_COLOR = 0xFF000000;

    private PreviewView previewView;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (sharedPreferences, key) -> Utils.runOnMainThread(this::updateUI);

    public CaptionStylePreviewPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public CaptionStylePreviewPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public CaptionStylePreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CaptionStylePreviewPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        Setting.preferences.preferences.registerOnSharedPreferenceChangeListener(listener);
        updateUI();
    }

    @Override
    protected void onPrepareForRemoval() {
        super.onPrepareForRemoval();
        Setting.preferences.preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected View onCreateView(ViewGroup parent) {
        Context context = getContext();

        // Dark backdrop, similar to video content.
        FrameLayout backdrop = new FrameLayout(context);
        GradientDrawable backdropDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3A4A5C, 0xFF1B222B});
        backdropDrawable.setCornerRadius(Dim.dp8);
        backdrop.setBackground(backdropDrawable);

        previewView = new PreviewView(context);
        backdrop.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout container = new FrameLayout(context);
        container.setPadding(Dim.dp16, Dim.dp8, Dim.dp16, Dim.dp8);
        container.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Dim.dp(150)));

        updateUI();
        return container;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        updateUI();
    }

    private void updateUI() {
        try {
            if (previewView != null) {
                previewView.invalidate();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "updateUI failure", ex);
        }
    }

    /**
     * Draws two caption lines, in the way that YouTube draws them.
     */
    private static final class PreviewView extends View {
        private final Path backgroundPath = new Path();
        private final RectF lineRect = new RectF();
        private final RectF windowRect = new RectF();
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint windowPaint = new Paint();
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        PreviewView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            try {
                draw(canvas);
            } catch (Exception ex) {
                Logger.printException(() -> "Preview draw failure", ex);
            }
        }

        private void draw(Canvas canvas) {
            final boolean custom = CaptionStylePatch.isCustomStyleEnabled();
            final float density = getResources().getDisplayMetrics().density;

            int textColor = DEFAULT_TEXT_COLOR;
            int backgroundColor = DEFAULT_BACKGROUND_COLOR;
            int windowColor = DEFAULT_WINDOW_COLOR;
            int edgeColor = DEFAULT_EDGE_COLOR;
            CaptionEdgeType edgeType = CaptionEdgeType.NONE;
            if (custom) {
                textColor = CaptionStyleUtils.parseColor(Settings.CAPTION_TEXT_COLOR.get(), textColor);
                backgroundColor = CaptionStyleUtils.parseColor(Settings.CAPTION_BACKGROUND_COLOR.get(), backgroundColor);
                windowColor = CaptionStyleUtils.parseColor(Settings.CAPTION_WINDOW_COLOR.get(), windowColor);
                edgeColor = CaptionStyleUtils.parseColor(Settings.CAPTION_EDGE_COLOR.get(), edgeColor);
                edgeType = Settings.CAPTION_EDGE_TYPE.get();
            }

            // Text.
            final float textSizeSp = CaptionStyleUtils.scaleFontScale(BASE_TEXT_SIZE_SP, Settings.CAPTION_SCALE.get());
            textPaint.reset();
            textPaint.setAntiAlias(true);
            textPaint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, textSizeSp, getResources().getDisplayMetrics()));
            CaptionStylePatch.setTypeface(textPaint, Typeface.DEFAULT);

            final String[] lines = {
                    str("morphe_caption_style_preview_text"),
                    str("morphe_caption_style_preview_text_2")
            };
            final Paint.FontMetrics metrics = textPaint.getFontMetrics();
            final float lineHeight = metrics.descent - metrics.ascent;
            final float lineGap = 2 * density;
            final float padX = 6 * density;
            final float totalHeight = lines.length * lineHeight + (lines.length - 1) * lineGap;
            final float centerX = getWidth() / 2f;
            float top = (getHeight() - totalHeight) / 2f;

            // Background shape, one rounded rectangle for each line.
            final float radius = CaptionStylePatch.getCornerRadius(4 * density);
            backgroundPath.reset();
            final float[] lineTops = new float[lines.length];
            final float[] lineWidths = new float[lines.length];
            float minLeft = Float.MAX_VALUE;
            float maxRight = 0;
            for (int i = 0; i < lines.length; i++) {
                lineWidths[i] = textPaint.measureText(lines[i]);
                lineTops[i] = top;
                lineRect.set(centerX - lineWidths[i] / 2f - padX, top,
                        centerX + lineWidths[i] / 2f + padX, top + lineHeight);
                backgroundPath.addRoundRect(lineRect, radius, radius, Path.Direction.CW);
                minLeft = Math.min(minLeft, lineRect.left);
                maxRight = Math.max(maxRight, lineRect.right);
                top += lineHeight + lineGap;
            }

            // Window, behind all lines.
            if (CaptionStyleUtils.alpha(windowColor) > 0) {
                windowRect.set(minLeft, lineTops[0], maxRight, lineTops[lines.length - 1] + lineHeight);
                windowPaint.setColor(windowColor);
                canvas.drawRect(windowRect, windowPaint);
            }

            backgroundPaint.setStyle(Paint.Style.FILL);
            backgroundPaint.setColor(backgroundColor);
            if (custom) {
                CaptionStyleRenderer.draw(canvas, backgroundPath, backgroundPaint,
                        CaptionStylePatch.createBackgroundParams(density));
            } else if (CaptionStyleUtils.alpha(backgroundColor) > 0) {
                canvas.drawPath(backgroundPath, backgroundPaint);
            }

            // Text edge and text.
            final float edgeSize = CaptionStylePatch.getEdgeSize(
                    YOUTUBE_EDGE_FACTOR * textPaint.getTextSize());
            for (int i = 0; i < lines.length; i++) {
                final float x = centerX - lineWidths[i] / 2f;
                final float baseline = lineTops[i] - metrics.ascent;
                drawLine(canvas, lines[i], x, baseline, textColor, edgeType, edgeColor, edgeSize);
            }
        }

        private void drawLine(Canvas canvas, String text, float x, float baseline,
                              int textColor, CaptionEdgeType edgeType, int edgeColor, float edgeSize) {
            switch (edgeType) {
                case OUTLINE:
                    textPaint.setStyle(Paint.Style.STROKE);
                    textPaint.setStrokeJoin(Paint.Join.ROUND);
                    textPaint.setStrokeWidth(edgeSize * 2);
                    textPaint.setColor(edgeColor);
                    canvas.drawText(text, x, baseline, textPaint);
                    textPaint.setStyle(Paint.Style.FILL);
                    break;
                case DROP_SHADOW:
                    textPaint.setShadowLayer(edgeSize, edgeSize, edgeSize, edgeColor);
                    break;
                case RAISED:
                    textPaint.setShadowLayer(1f, -edgeSize, -edgeSize, edgeColor);
                    break;
                case DEPRESSED:
                    textPaint.setShadowLayer(1f, edgeSize, edgeSize, edgeColor);
                    break;
                default:
                    textPaint.clearShadowLayer();
                    break;
            }

            textPaint.setColor(textColor);
            canvas.drawText(text, x, baseline, textPaint);
            textPaint.clearShadowLayer();
        }
    }
}
