/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CaptionStyleUtilsTest {
    private static final int FALLBACK = 0x12345678;

    @Test public void parsesOpaqueColor() {
        assertEquals(0xFFFF8800, CaptionStyleUtils.parseColor("#FF8800", FALLBACK));
        assertEquals(0xFFFF8800, CaptionStyleUtils.parseColor("FF8800", FALLBACK));
    }

    @Test public void parsesColorWithAlpha() {
        assertEquals(0xBF000000, CaptionStyleUtils.parseColor("#BF000000", FALLBACK));
        assertEquals(0x00000000, CaptionStyleUtils.parseColor("#00000000", FALLBACK));
        assertEquals(0x80FFFFFF, CaptionStyleUtils.parseColor("#80ffffff", FALLBACK));
    }

    @Test public void trimsWhitespace() {
        assertEquals(0xFF112233, CaptionStyleUtils.parseColor("  #112233 ", FALLBACK));
    }

    @Test public void invalidColorUsesFallback() {
        assertEquals(FALLBACK, CaptionStyleUtils.parseColor(null, FALLBACK));
        assertEquals(FALLBACK, CaptionStyleUtils.parseColor("", FALLBACK));
        assertEquals(FALLBACK, CaptionStyleUtils.parseColor("#FFF", FALLBACK));
        assertEquals(FALLBACK, CaptionStyleUtils.parseColor("#GGGGGG", FALLBACK));
        assertEquals(FALLBACK, CaptionStyleUtils.parseColor("#FF00000000", FALLBACK));
    }

    @Test public void scalesFontScale() {
        assertEquals(1.0f, CaptionStyleUtils.scaleFontScale(1.0f, 100), 0.0001f);
        assertEquals(2.0f, CaptionStyleUtils.scaleFontScale(1.0f, 200), 0.0001f);
        assertEquals(0.75f, CaptionStyleUtils.scaleFontScale(1.5f, 50), 0.0001f);
    }

    @Test public void clampsFontScale() {
        assertEquals(0.25f, CaptionStyleUtils.scaleFontScale(1.0f, 0), 0.0001f);
        assertEquals(4.0f, CaptionStyleUtils.scaleFontScale(1.0f, 9999), 0.0001f);
    }

    @Test public void scalesEdgeSize() {
        assertEquals(2.0f, CaptionStyleUtils.scaleEdgeSize(2.0f, 100), 0.0001f);
        assertEquals(4.0f, CaptionStyleUtils.scaleEdgeSize(2.0f, 200), 0.0001f);
        assertEquals(0.5f, CaptionStyleUtils.scaleEdgeSize(2.0f, 0), 0.0001f);
        assertEquals(6.0f, CaptionStyleUtils.scaleEdgeSize(2.0f, 5000), 0.0001f);
    }

    @Test public void readsAndSetsAlpha() {
        assertEquals(0xBF, CaptionStyleUtils.alpha(0xBF123456));
        assertEquals(0xFF, CaptionStyleUtils.alpha(0xFF000000));
        assertEquals(0, CaptionStyleUtils.alpha(0x00FFFFFF));
        assertEquals(0x80123456, CaptionStyleUtils.withAlpha(0xFF123456, 0x80));
        assertEquals(0xFF123456, CaptionStyleUtils.withAlpha(0x00123456, 999));
        assertEquals(0x00123456, CaptionStyleUtils.withAlpha(0xFF123456, -5));
    }

    @Test public void scalesAlpha() {
        assertEquals(0x40FFFFFF, CaptionStyleUtils.scaleAlpha(0x80FFFFFF, 0.5f));
        assertEquals(0xFFFFFFFF, CaptionStyleUtils.scaleAlpha(0xC0FFFFFF, 5f));
        assertEquals(0x00FFFFFF, CaptionStyleUtils.scaleAlpha(0x80FFFFFF, 0f));
    }

    @Test public void glassColors() {
        // An almost transparent color gets the minimum alpha.
        assertEquals(0x33112233, CaptionStyleUtils.glassBase(0x00112233));
        assertEquals(0x80112233, CaptionStyleUtils.glassBase(0x80112233));

        final int top = CaptionStyleUtils.glassTop(0x40FFFFFF);
        final int bottom = CaptionStyleUtils.glassBottom(0x40FFFFFF);
        assertTrue(CaptionStyleUtils.alpha(top) > CaptionStyleUtils.alpha(bottom));
        assertEquals(0xFFFFFF, top & 0xFFFFFF);
        assertEquals(0xFFFFFF, bottom & 0xFFFFFF);
    }

    @Test public void fontStyleFlags() {
        assertEquals(CaptionStyleUtils.STYLE_NORMAL, CaptionStyleUtils.styleFlags(400, false));
        assertEquals(CaptionStyleUtils.STYLE_NORMAL, CaptionStyleUtils.styleFlags(500, false));
        assertEquals(CaptionStyleUtils.STYLE_BOLD, CaptionStyleUtils.styleFlags(700, false));
        assertEquals(CaptionStyleUtils.STYLE_ITALIC, CaptionStyleUtils.styleFlags(400, true));
        assertEquals(CaptionStyleUtils.STYLE_BOLD_ITALIC, CaptionStyleUtils.styleFlags(900, true));
    }

    @Test public void detectsStyledFont() {
        assertFalse(CaptionStyleUtils.isStyled(400, false));
        assertTrue(CaptionStyleUtils.isStyled(700, false));
        assertTrue(CaptionStyleUtils.isStyled(400, true));
    }

    @Test public void convertsDpToPx() {
        assertEquals(8f, CaptionStyleUtils.dpToPx(4f, 2f), 0.0001f);
        assertEquals(0f, CaptionStyleUtils.dpToPx(0f, 3f), 0.0001f);
    }

    @Test public void clampsInts() {
        assertEquals(5, CaptionStyleUtils.clamp(5, 0, 10));
        assertEquals(0, CaptionStyleUtils.clamp(-1, 0, 10));
        assertEquals(10, CaptionStyleUtils.clamp(11, 0, 10));
    }
}
