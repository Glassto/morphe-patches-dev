/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.settings.preference;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.youtube.patches.CaptionStylePresets;

/**
 * Shows a list of caption style presets. Selecting one saves all the caption style options.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class CaptionStylePresetPreference extends Preference {

    /**
     * Presets in the same order as the names of the preset list strings.
     * The last entry of the list resets the style.
     */
    private static final CaptionStylePresets[] PRESETS = CaptionStylePresets.values();

    public CaptionStylePresetPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CaptionStylePresetPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CaptionStylePresetPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CaptionStylePresetPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        try {
            final Context context = getContext();

            // One name for each preset, then the reset entry.
            final CharSequence[] names = new CharSequence[PRESETS.length + 1];
            for (int i = 0; i < PRESETS.length; i++) {
                names[i] = str("morphe_caption_style_preset_entry_" + PRESETS[i].name().toLowerCase(Locale.ROOT));
            }
            names[PRESETS.length] = str("morphe_caption_style_preset_entry_reset");

            new AlertDialog.Builder(context)
                    .setTitle(str("morphe_caption_style_preset_title"))
                    .setItems(names, (dialog, which) -> {
                        try {
                            if (which < PRESETS.length) {
                                PRESETS[which].apply();
                            } else {
                                CaptionStylePresets.reset();
                            }
                            applied(context);
                        } catch (Exception ex) {
                            Logger.printException(() -> "Apply preset failure", ex);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception ex) {
            Logger.printException(() -> "onClick failure", ex);
        }
    }

    private static void applied(Context context) {
        AbstractPreferenceFragment.setRestartNeeded();
        Utils.showToastShort(str("morphe_caption_style_preset_applied"));

        // Reload the settings screen, to show the new values.
        if (context instanceof Activity) {
            ((Activity) context).recreate();
        }
    }
}
