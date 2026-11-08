/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.os.Build;

import app.morphe.extension.youtube.patches.CaptionStylePatch.CaptionBackgroundStyle;

/**
 * Draws the background of a caption line, with the options of the custom caption style.
 * Used for the real captions and for the preview in the settings.
 * <p>
 * Must only be used from the UI thread, since the paints and paths are shared.
 */
public final class CaptionStyleRenderer {

    /**
     * Options of the caption background, in pixels.
     */
    public static final class Params {
        public final CaptionBackgroundStyle style;
        public final float paddingPx;
        public final float borderPx;
        public final int borderColor;
        public final float shadowPx;
        public final int shadowColor;
        public final float density;

        public Params(CaptionBackgroundStyle style, float paddingPx, float borderPx, int borderColor,
                      float shadowPx, int shadowColor, float density) {
            this.style = style;
            this.paddingPx = paddingPx;
            this.borderPx = borderPx;
            this.borderColor = borderColor;
            this.shadowPx = shadowPx;
            this.shadowColor = shadowColor;
            this.density = density;
        }

        /**
         * @return If the background is drawn outside of the shape of the caption text lines,
         * or with a style that is not a plain fill.
         */
        public boolean hasEffects() {
            return paddingPx > 0 || borderPx > 0 || shadowPx > 0 || style == CaptionBackgroundStyle.GLASS;
        }
    }

    private static final int SHADOW_LAYERS = 6;
    private static final int GLASS_HIGHLIGHT_COLOR = 0x38FFFFFF;
    private static final int[] GLASS_RIM_COLORS = {0xB3FFFFFF, 0x26FFFFFF, 0x66FFFFFF};
    private static final float[] GLASS_RIM_POSITIONS = {0f, 0.5f, 1f};

    private static final Path expandedPath = new Path();
    private static final Path ringPath = new Path();
    private static final RectF bounds = new RectF();
    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private CaptionStyleRenderer() {
    }

    /**
     * Draws the background of caption text lines.
     *
     * @param path The shape of the text lines, as YouTube draws it.
     * @param fill Paint with the background color.
     */
    public static void draw(Canvas canvas, Path path, Paint fill, Params params) {
        final Path shape = params.paddingPx > 0 ? expand(path, params.paddingPx) : path;

        shape.computeBounds(bounds, true);
        if (bounds.isEmpty()) {
            if (params.style != CaptionBackgroundStyle.NONE) {
                canvas.drawPath(path, fill);
            }
            return;
        }

        if (params.shadowPx > 0 && CaptionStyleUtils.alpha(params.shadowColor) > 0) {
            drawShadow(canvas, shape, params.shadowPx, params.shadowColor);
        }

        switch (params.style) {
            case GLASS:
                drawGlassFill(canvas, shape, fill.getColor());
                break;
            case NONE:
                break;
            default:
                if (CaptionStyleUtils.alpha(fill.getColor()) > 0) {
                    canvas.drawPath(shape, fill);
                }
                break;
        }

        if (params.borderPx > 0 && CaptionStyleUtils.alpha(params.borderColor) > 0) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(params.borderPx);
            paint.setColor(params.borderColor);
            canvas.drawPath(shape, paint);
        }

        if (params.style == CaptionBackgroundStyle.GLASS) {
            drawGlassRim(canvas, shape, params.density);
        }
    }

    /**
     * Grows a shape by the same distance on all sides, with round corners.
     */
    private static Path expand(Path path, float distance) {
        ringPaint.reset();
        ringPaint.setAntiAlias(true);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeJoin(Paint.Join.ROUND);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStrokeWidth(distance * 2);

        ringPath.reset();
        ringPaint.getFillPath(path, ringPath);

        expandedPath.set(path);
        if (!expandedPath.op(ringPath, Path.Op.UNION)) {
            expandedPath.set(path);
        }
        return expandedPath;
    }

    /**
     * Soft shadow around a shape. Drawn as layers of growing outlines, since a shadow layer
     * on a path is not supported by hardware accelerated drawing.
     */
    @SuppressWarnings("deprecation")
    private static void drawShadow(Canvas canvas, Path shape, float size, int color) {
        canvas.save();
        try {
            // Do not draw the shadow below the background, a translucent background would show it.
            if (Build.VERSION.SDK_INT >= 26) {
                canvas.clipOutPath(shape);
            } else {
                canvas.clipPath(shape, Region.Op.DIFFERENCE);
            }
            canvas.translate(0, size * 0.3f);

            final int layerAlpha = Math.max(1,
                    Math.round(CaptionStyleUtils.alpha(color) * 1.2f / SHADOW_LAYERS));
            for (int layer = SHADOW_LAYERS; layer >= 1; layer--) {
                paint.reset();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(size * 2 * layer / SHADOW_LAYERS);
                paint.setColor(CaptionStyleUtils.withAlpha(color, layerAlpha));
                canvas.drawPath(shape, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    private static void drawGlassFill(Canvas canvas, Path shape, int color) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                CaptionStyleUtils.glassTop(color), CaptionStyleUtils.glassBottom(color),
                Shader.TileMode.CLAMP));
        canvas.drawPath(shape, paint);

        // Light reflection on the upper half.
        final float halfBottom = bounds.top + bounds.height() * 0.5f;
        canvas.save();
        try {
            canvas.clipPath(shape);
            canvas.clipRect(bounds.left, bounds.top, bounds.right, halfBottom);
            paint.setShader(new LinearGradient(0, bounds.top, 0, halfBottom,
                    GLASS_HIGHLIGHT_COLOR, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);
        } finally {
            canvas.restore();
        }
    }

    private static void drawGlassRim(Canvas canvas, Path shape, float density) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                GLASS_RIM_COLORS, GLASS_RIM_POSITIONS, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, paint);
    }
}
