/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.layout.captions

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.misc.settings.preference.InputType
import app.morphe.patches.shared.misc.settings.preference.ListPreference
import app.morphe.patches.shared.misc.settings.preference.NonInteractivePreference
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.TextPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/CaptionStylePatch;"
private const val PREVIEW_PREFERENCE =
    "app.morphe.extension.youtube.settings.preference.CaptionStylePreviewPreference"
private const val PRESET_PREFERENCE =
    "app.morphe.extension.youtube.settings.preference.CaptionStylePresetPreference"
private const val SEEKBAR_PREFERENCE =
    "app.morphe.extension.shared.settings.preference.SeekBarPreference"
private const val COLOR_PICKER_PREFERENCE =
    "app.morphe.extension.shared.settings.preference.ColorPickerPreference"
private const val COLOR_PICKER_OPACITY_PREFERENCE =
    "app.morphe.extension.shared.settings.preference.ColorPickerWithOpacitySliderPreference"

/** Hooked call sites, printed after patching to show where the caption style is read. */
private val hookedSites = sortedSetOf<String>()

/** Libraries that read the system caption style, but do not draw the YouTube captions. */
private val LIBRARY_PREFIXES = listOf(
    "Landroidx/media3/",
    "Lcom/google/android/exoplayer2/",
    "Lcom/google/android/gms/cast/",
    "Landroidx/",
)

/** Unique key of a method, to hook a method only once. */
private fun MutableMethod.key() = "$definingClass->$name${parameterTypes.joinToString("")}$returnType"

/**
 * Hooks every read of an int field of the system caption style.
 * The read value is passed through the extension method.
 *
 * Instructions are scanned from the end of the method, so inserting a hook
 * does not change the index of instructions that are not hooked yet.
 *
 * @return The number of hooked reads.
 */
private fun hookFieldReads(
    methods: Collection<MutableMethod>,
    definingClass: String,
    fieldName: String,
    extensionMethod: String
): Int {
    var hooked = 0

    methods.forEach { method ->
        val instructions = method.implementation?.instructions ?: return@forEach

        for (index in instructions.indices.reversed()) {
            val instruction = method.getInstruction(index)
            if (instruction.opcode != Opcode.IGET) continue

            val field = instruction.getReference<FieldReference>() ?: continue
            if (field.definingClass != definingClass || field.name != fieldName) continue

            val register = method.getInstruction<TwoRegisterInstruction>(index).registerA
            method.addInstructions(
                index + 1,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS->$extensionMethod(I)I
                    move-result v$register
                """
            )
            hookedSites.add("${method.definingClass}->${method.name} reads $fieldName")
            hooked++
        }
    }

    return hooked
}

/**
 * Hooks every use of the result of a method call that has no parameters.
 *
 * @param moveResult The move-result opcode that matches the return type.
 * @param descriptor Return type descriptor of the method, and of the extension method.
 * @return The number of hooked calls.
 */
private fun hookMethodResults(
    methods: Collection<MutableMethod>,
    definingClass: String,
    methodName: String,
    descriptor: String,
    moveResult: Opcode,
    extensionMethod: String
): Int {
    var hooked = 0

    methods.forEach { method ->
        val instructions = method.implementation?.instructions ?: return@forEach

        for (index in instructions.indices.reversed()) {
            val resultIndex = index + 1
            if (resultIndex >= instructions.size) continue

            val call = method.getInstruction(index).getReference<MethodReference>() ?: continue
            if (call.definingClass != definingClass
                || call.name != methodName
                || call.parameterTypes.isNotEmpty()
                || call.returnType != descriptor
            ) continue

            if (method.getInstruction(resultIndex).opcode != moveResult) continue

            val register = method.getInstruction<OneRegisterInstruction>(resultIndex).registerA
            val move = if (moveResult == Opcode.MOVE_RESULT_OBJECT) "move-result-object" else "move-result"

            method.addInstructions(
                resultIndex + 1,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS->$extensionMethod($descriptor)$descriptor
                    $move v$register
                """
            )
            hookedSites.add("${method.definingClass}->${method.name} calls $methodName")
            hooked++
        }
    }

    return hooked
}

private const val DRAW_PATH =
    "Landroid/graphics/Canvas;->drawPath(Landroid/graphics/Path;Landroid/graphics/Paint;)V"
private const val COLOR_ALPHA =
    "Landroid/graphics/Color;->alpha(I)I"
private const val TEXT_PAINT_SET_TYPEFACE =
    "Landroid/text/TextPaint;->setTypeface(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;"
private const val APPLY_DIMENSION =
    "Landroid/util/TypedValue;->applyDimension(IFLandroid/util/DisplayMetrics;)F"

private fun MethodReference.key() =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

/**
 * Hooks the view that draws one line of captions: the background, the text edge size,
 * the corner radius, the layer type and the typeface.
 *
 * Instructions are scanned from the end of each method, so a hook does not change the index
 * of instructions that are not hooked yet.
 *
 * @param classType The class of the caption line view.
 * @return The number of hooks, for each kind of hook.
 */
private fun hookCaptionLineView(methods: Collection<MutableMethod>, classType: String): Map<String, Int> {
    val counts = linkedMapOf("background" to 0, "alpha" to 0, "layer" to 0, "typeface" to 0, "radius" to 0, "edge" to 0)
    fun count(name: String) {
        counts[name] = counts.getValue(name) + 1
    }

    methods.forEach { method ->
        val instructions = method.implementation?.instructions ?: return@forEach

        for (index in instructions.indices.reversed()) {
            val instruction = method.getInstruction(index)
            val hasNext = index + 1 < instructions.size

            when (instruction.opcode) {
                Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_STATIC -> {
                    val call = instruction.getReference<MethodReference>() ?: continue
                    val key = call.key()

                    if (key == DRAW_PATH && method.name == "onDraw") {
                        // The background. p0 is the caption line view.
                        val registers = method.getInstruction<FiveRegisterInstruction>(index)
                        method.replaceInstruction(
                            index,
                            "invoke-static { p0, v${registers.registerC}, v${registers.registerD}, v${registers.registerE} }, " +
                                    "$EXTENSION_CLASS->drawBackground(Landroid/view/View;Landroid/graphics/Canvas;" +
                                    "Landroid/graphics/Path;Landroid/graphics/Paint;)V"
                        )
                        count("background")
                    } else if (key == COLOR_ALPHA && method.name == "onDraw"
                        && hasNext && method.getInstruction(index + 1).opcode == Opcode.MOVE_RESULT
                    ) {
                        // The background is skipped if the color has no alpha.
                        val register = method.getInstruction<OneRegisterInstruction>(index + 1).registerA
                        method.addInstructions(
                            index + 2,
                            """
                                invoke-static { v$register }, $EXTENSION_CLASS->getBackgroundAlpha(I)I
                                move-result v$register
                            """
                        )
                        count("alpha")
                    } else if (call.definingClass == classType && call.name == "setLayerType"
                        && call.parameterTypes.size == 2
                    ) {
                        val registers = method.getInstruction<FiveRegisterInstruction>(index)
                        method.replaceInstruction(
                            index,
                            "invoke-static { v${registers.registerC}, v${registers.registerD}, v${registers.registerE} }, " +
                                    "$EXTENSION_CLASS->setLayerType(Landroid/view/View;ILandroid/graphics/Paint;)V"
                        )
                        count("layer")
                    } else if (key == TEXT_PAINT_SET_TYPEFACE
                        && !(hasNext && method.getInstruction(index + 1).opcode == Opcode.MOVE_RESULT_OBJECT)
                    ) {
                        val registers = method.getInstruction<FiveRegisterInstruction>(index)
                        method.replaceInstruction(
                            index,
                            "invoke-static { v${registers.registerC}, v${registers.registerD} }, " +
                                    "$EXTENSION_CLASS->setTypeface(Landroid/text/TextPaint;Landroid/graphics/Typeface;)V"
                        )
                        count("typeface")
                    } else if (key == APPLY_DIMENSION && method.name == "<init>"
                        && hasNext && method.getInstruction(index + 1).opcode == Opcode.MOVE_RESULT
                    ) {
                        // The corner radius of the background, set in the constructor.
                        val register = method.getInstruction<OneRegisterInstruction>(index + 1).registerA
                        method.addInstructions(
                            index + 2,
                            """
                                invoke-static { v$register }, $EXTENSION_CLASS->getCornerRadius(F)F
                                move-result v$register
                            """
                        )
                        count("radius")
                    }
                }

                Opcode.MUL_FLOAT_2ADDR -> {
                    // The edge size is a fraction of the text size, saved in a float field.
                    if (!hasNext) continue
                    val next = method.getInstruction(index + 1)
                    if (next.opcode != Opcode.IPUT) continue

                    val field = next.getReference<FieldReference>() ?: continue
                    val register = method.getInstruction<TwoRegisterInstruction>(index).registerA
                    if (field.definingClass != classType || field.type != "F"
                        || (next as TwoRegisterInstruction).registerA != register
                    ) continue

                    method.addInstructions(
                        index + 1,
                        """
                            invoke-static { v$register }, $EXTENSION_CLASS->getEdgeSize(F)F
                            move-result v$register
                        """
                    )
                    count("edge")
                }

                else -> Unit
            }
        }
    }

    return counts
}

/**
 * Hooks every read of the system caption style that YouTube uses to draw captions,
 * and ignores the text styles ("pens") that a video defines for its own captions.
 */
internal val captionStylePatch = bytecodePatch(
    description = "Adds options to change the caption font, colors, edge style and background " +
            "(glass, border, shadow, corner radius), and to ignore the caption style of a video.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    execute {
        hookedSites.clear()

        settingsMenuCaptionGroup.addAll(
            listOf(
                NonInteractivePreference(
                    key = "morphe_caption_style_preview",
                    summaryKey = null,
                    tag = PREVIEW_PREFERENCE
                ),
                SwitchPreference("morphe_caption_style_enabled", summary = true),
                NonInteractivePreference(
                    key = "morphe_caption_style_preset",
                    tag = PRESET_PREFERENCE
                ),
                SwitchPreference("morphe_caption_ignore_creator_style", summary = true),

                // Text.
                ListPreference("morphe_caption_font_family"),
                NonInteractivePreference(
                    key = "morphe_caption_font_weight",
                    tag = SEEKBAR_PREFERENCE
                ),
                SwitchPreference("morphe_caption_font_italic", summary = true),
                NonInteractivePreference(
                    key = "morphe_caption_letter_spacing",
                    tag = SEEKBAR_PREFERENCE
                ),
                TextPreference(
                    "morphe_caption_text_color",
                    tag = COLOR_PICKER_OPACITY_PREFERENCE,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                ListPreference("morphe_caption_edge_type"),
                TextPreference(
                    "morphe_caption_edge_color",
                    tag = COLOR_PICKER_PREFERENCE,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                NonInteractivePreference(
                    key = "morphe_caption_edge_strength",
                    tag = SEEKBAR_PREFERENCE
                ),

                // Background.
                ListPreference("morphe_caption_background_style"),
                TextPreference(
                    "morphe_caption_background_color",
                    tag = COLOR_PICKER_OPACITY_PREFERENCE,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                TextPreference(
                    "morphe_caption_window_color",
                    tag = COLOR_PICKER_OPACITY_PREFERENCE,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                NonInteractivePreference(
                    key = "morphe_caption_corner_radius",
                    tag = SEEKBAR_PREFERENCE
                ),
                NonInteractivePreference(
                    key = "morphe_caption_background_padding",
                    tag = SEEKBAR_PREFERENCE
                ),
                NonInteractivePreference(
                    key = "morphe_caption_border_width",
                    tag = SEEKBAR_PREFERENCE
                ),
                TextPreference(
                    "morphe_caption_border_color",
                    tag = COLOR_PICKER_OPACITY_PREFERENCE,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
                NonInteractivePreference(
                    key = "morphe_caption_shadow_size",
                    tag = SEEKBAR_PREFERENCE
                ),
                TextPreference(
                    "morphe_caption_shadow_color",
                    tag = COLOR_PICKER_OPACITY_PREFERENCE,
                    inputType = InputType.TEXT_CAP_CHARACTERS
                ),
            )
        )

        /**
         * Methods found by the fingerprints, each method once.
         * A fingerprint without a match is not an error, since not every hook is used by every version.
         */
        fun methodsOf(vararg fingerprints: Fingerprint): Collection<MutableMethod> =
            fingerprints.flatMap { fingerprint ->
                runCatching { fingerprint.matchAll().map { it.method } }.getOrDefault(emptyList())
            }.associateBy { it.key() }.values

        // Colors and edge type of the caption style.
        var hookedFields = 0
        mapOf(
            "foregroundColor" to "getForegroundColor",
            "backgroundColor" to "getBackgroundColor",
            "windowColor" to "getWindowColor",
            "edgeColor" to "getEdgeColor",
            "edgeType" to "getEdgeType",
        ).forEach { (field, hook) ->
            hookedFields += hookFieldReads(
                methodsOf(CaptionStyleFieldReadFingerprint(field)),
                CAPTION_STYLE_CLASS,
                field,
                hook
            )
        }


        // Main hooks: the YouTube caption style object, read when the captions are drawn.
        // Unlike the system style above, this also covers styles that YouTube creates by itself.
        var hookedStyleReads = 0
        runCatching {
            val styleClass = CaptionStyleFromSystemConstructorFingerprint.matchAll().first().classDef

            val intConstructor = styleClass.methods.first { method ->
                method.name == "<init>"
                        && method.parameterTypes.size == 6
                        && method.parameterTypes.all { it.toString() == "I" }
            }

            // The six int constructor stores its parameters in the style fields.
            // Parameter order: background, window, edge color, edge type, text color, font.
            val implementation = intConstructor.implementation!!
            val firstParameterRegister = implementation.registerCount - 6
            val fieldNames = arrayOfNulls<String>(6)
            implementation.instructions.forEach { instruction ->
                if (instruction.opcode == Opcode.IPUT) {
                    val register = (instruction as TwoRegisterInstruction).registerA - firstParameterRegister
                    val field = (instruction as ReferenceInstruction).reference as FieldReference
                    if (register in 0..5 && field.definingClass == styleClass.type) {
                        fieldNames[register] = field.name
                    }
                }
            }

            listOf(
                0 to "getBackgroundColor",
                1 to "getWindowColor",
                2 to "getEdgeColor",
                3 to "getEdgeType",
                4 to "getForegroundColor",
            ).forEach { (parameter, hook) ->
                val fieldName = fieldNames[parameter] ?: return@forEach
                hookedStyleReads += hookFieldReads(
                    methodsOf(ClassIntFieldReadFingerprint(styleClass.type, fieldName)),
                    styleClass.type,
                    fieldName,
                    hook
                )
            }
        }.onFailure {
            println("CaptionStyle: WARNING could not hook the YouTube caption style: $it")
        }

        runCatching {
            // p1 is the typeface. Replace it before the window passes it to the caption texts.
            CaptionWindowTypefaceFingerprint.method.addInstructions(
                0,
                """
                    invoke-static { p1 }, $EXTENSION_CLASS->getTypeface(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;
                    move-result-object p1
                """
            )
            hookedSites.add("caption window typeface hooked")
        }.onFailure {
            println("CaptionStyle: WARNING could not hook the caption typeface: $it")
        }

        if (hookedFields == 0 && hookedStyleReads == 0) {
            throw PatchException(
                "YouTube does not read the caption style in this version. The caption style hooks must be updated."
            )
        }
        println("CaptionStyle: $hookedStyleReads reads of the YouTube caption style hooked")

        // Android treats a color or edge as not set unless hasXXX() is true.
        // A custom style always sets all of them.
        listOf(
            "hasForegroundColor",
            "hasBackgroundColor",
            "hasWindowColor",
            "hasEdgeColor",
            "hasEdgeType",
        ).forEach { name ->
            hookMethodResults(
                methodsOf(CaptionStyleMethodCallFingerprint(CAPTION_STYLE_CLASS, name, "Z")),
                CAPTION_STYLE_CLASS,
                name,
                "Z",
                Opcode.MOVE_RESULT,
                "isStylePropertySpecified"
            )
        }

        hookMethodResults(
            methodsOf(CaptionStyleMethodCallFingerprint(CAPTION_STYLE_CLASS, "getTypeface", "Landroid/graphics/Typeface;")),
            CAPTION_STYLE_CLASS,
            "getTypeface",
            "Landroid/graphics/Typeface;",
            Opcode.MOVE_RESULT_OBJECT,
            "getTypeface"
        )

        // Background, edge size, corner radius, layer type and typeface of a caption line.
        runCatching {
            val lineClass = CaptionLineViewFingerprint.matchAll().first().classDef.type
            val counts = hookCaptionLineView(mutableClassDefBy(lineClass).methods, lineClass)
            println("CaptionStyle: caption line view hooks: $counts")
            counts.filterValues { it == 0 }.keys.forEach {
                println("CaptionStyle: WARNING no caption line hook for: $it")
            }
            hookedSites.add("caption line view $lineClass hooked")
        }.onFailure {
            println("CaptionStyle: WARNING could not hook the caption line view: $it")
        }

        println("CaptionStyle: hooked ${hookedSites.size} call sites:")
        hookedSites.forEach { println("CaptionStyle:   $it") }
        if (hookedSites.all { site -> LIBRARY_PREFIXES.any { site.startsWith(it) } }) {
            println(
                "CaptionStyle: WARNING all hooked call sites are in libraries. " +
                        "The captions drawn by YouTube probably do not use the system caption style."
            )
        }

        // Ignore the caption styles of a video by looking up an unknown pen.
        // The same method is hooked by the translated caption pens patch. Both hooks are independent.
        TimedTextSegmentElementFingerprint.instructionMatches[2].getMethodCalled().addInstructions(
            0,
            """
                invoke-static { p1 }, $EXTENSION_CLASS->getPenId(I)I
                move-result p1
            """
        )
    }
}
