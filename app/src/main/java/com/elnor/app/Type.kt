package com.elnor.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The font of the whole app.
 *
 * FontFamily.Default is the phone's own system font. Android picks a font for every single
 * character: Bengali letters come from the system Bengali font (Noto Sans Bengali on most
 * phones), emoji from the system emoji font, Arabic and Japanese from their system fonts.
 * Nothing has to be set up for that.
 *
 * If a custom font is added later:  FontFamily(Font(R.font.my_font))
 * Characters that the custom font does not have still fall back to the system fonts. The
 * fallback only fails if the font file itself draws an empty box for a character it does not
 * support. So test the font with FontAuditPreview before using it.
 */
val ElnorFontFamily: FontFamily = FontFamily.Default

/**
 * Center: the extra space of a tall line is split above and below the text, so a vowel sign
 * above the letter (ই-কার) and a sign below it (উ-কার, the lower part of a conjunct) both have room.
 * Trim.None: the first line's top and the last line's bottom are never cut off.
 */
private val ElnorLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/**
 * Same size as Material 3 uses, but:
 *  - lineHeight is [lineHeightRatio] times the font size (Material 3 uses about 1.25 to 1.45
 *    for some styles, which is too tight for Bengali)
 *  - letterSpacing is 0, so nothing pushes Bengali vowel signs and conjuncts apart
 *  - one font family and one LineHeightStyle everywhere
 *  - the size is multiplied by [scale] (Settings > Appearance > Font size) before the line
 *    height is calculated, so the space between lines grows with the text
 */
private fun TextStyle.forAllScripts(lineHeightRatio: Float, scale: Float): TextStyle {
    val size = fontSize * scale
    return copy(
        fontFamily = ElnorFontFamily,
        fontSize = size,
        lineHeight = size * lineHeightRatio,
        letterSpacing = 0.sp,
        lineHeightStyle = ElnorLineHeightStyle
    )
}

private val material3Defaults = Typography()

/**
 * Use this as MaterialTheme(typography = ...). Every Text and TextField reads its style from it.
 * [scale] is the factor of the chosen font size ([FontSizeChoice.scale]); 1 is the normal size.
 */
fun elnorTypography(scale: Float = 1f): Typography = Typography(
    displayLarge = material3Defaults.displayLarge.forAllScripts(1.4f, scale),
    displayMedium = material3Defaults.displayMedium.forAllScripts(1.4f, scale),
    displaySmall = material3Defaults.displaySmall.forAllScripts(1.4f, scale),
    headlineLarge = material3Defaults.headlineLarge.forAllScripts(1.4f, scale),
    headlineMedium = material3Defaults.headlineMedium.forAllScripts(1.4f, scale),
    headlineSmall = material3Defaults.headlineSmall.forAllScripts(1.4f, scale),
    titleLarge = material3Defaults.titleLarge.forAllScripts(1.5f, scale),
    titleMedium = material3Defaults.titleMedium.forAllScripts(1.5f, scale),
    titleSmall = material3Defaults.titleSmall.forAllScripts(1.5f, scale),
    bodyLarge = material3Defaults.bodyLarge.forAllScripts(1.5f, scale),
    bodyMedium = material3Defaults.bodyMedium.forAllScripts(1.5f, scale),
    bodySmall = material3Defaults.bodySmall.forAllScripts(1.5f, scale),
    labelLarge = material3Defaults.labelLarge.forAllScripts(1.5f, scale),
    labelMedium = material3Defaults.labelMedium.forAllScripts(1.5f, scale),
    labelSmall = material3Defaults.labelSmall.forAllScripts(1.5f, scale)
)

/** The normal size (scale 1). */
val ElnorTypography = elnorTypography(1f)
