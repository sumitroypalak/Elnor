package com.drafto.app

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
val DraftoFontFamily: FontFamily = FontFamily.Default

/**
 * Center: the extra space of a tall line is split above and below the text, so a vowel sign
 * above the letter (ই-কার) and a sign below it (উ-কার, the lower part of a conjunct) both have room.
 * Trim.None: the first line's top and the last line's bottom are never cut off.
 */
private val DraftoLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/**
 * Same size as Material 3 uses, but:
 *  - lineHeight is [lineHeightRatio] times the font size (Material 3 uses about 1.25 to 1.45
 *    for some styles, which is too tight for Bengali)
 *  - letterSpacing is 0, so nothing pushes Bengali vowel signs and conjuncts apart
 *  - one font family and one LineHeightStyle everywhere
 */
private fun TextStyle.forAllScripts(lineHeightRatio: Float): TextStyle = copy(
    fontFamily = DraftoFontFamily,
    lineHeight = fontSize * lineHeightRatio,
    letterSpacing = 0.sp,
    lineHeightStyle = DraftoLineHeightStyle
)

private val material3Defaults = Typography()

/** Use this as MaterialTheme(typography = ...). Every Text and TextField reads its style from it. */
val DraftoTypography = Typography(
    displayLarge = material3Defaults.displayLarge.forAllScripts(1.4f),
    displayMedium = material3Defaults.displayMedium.forAllScripts(1.4f),
    displaySmall = material3Defaults.displaySmall.forAllScripts(1.4f),
    headlineLarge = material3Defaults.headlineLarge.forAllScripts(1.4f),
    headlineMedium = material3Defaults.headlineMedium.forAllScripts(1.4f),
    headlineSmall = material3Defaults.headlineSmall.forAllScripts(1.4f),
    titleLarge = material3Defaults.titleLarge.forAllScripts(1.5f),
    titleMedium = material3Defaults.titleMedium.forAllScripts(1.5f),
    titleSmall = material3Defaults.titleSmall.forAllScripts(1.5f),
    bodyLarge = material3Defaults.bodyLarge.forAllScripts(1.5f),
    bodyMedium = material3Defaults.bodyMedium.forAllScripts(1.5f),
    bodySmall = material3Defaults.bodySmall.forAllScripts(1.5f),
    labelLarge = material3Defaults.labelLarge.forAllScripts(1.5f),
    labelMedium = material3Defaults.labelMedium.forAllScripts(1.5f),
    labelSmall = material3Defaults.labelSmall.forAllScripts(1.5f)
)
