package com.example.ui.screens

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration

object POSFontStyles {
    data class StyleConfig(
        val name: String,
        val fontFamily: FontFamily,
        val fontWeight: FontWeight,
        val fontStyle: FontStyle,
        val letterSpacing: TextUnit = 0.sp,
        val systemFontName: String = "sans-serif",
        val customTypefaceStyle: Int = Typeface.NORMAL
    )

    val list: List<StyleConfig> = List(50) { i ->
        val index = i + 1
        when (index) {
            1 -> StyleConfig("Classic", FontFamily.Default, FontWeight.Normal, FontStyle.Normal)
            2 -> StyleConfig("Serif Bold", FontFamily.Serif, FontWeight.Bold, FontStyle.Normal, 0.sp, "serif", Typeface.BOLD)
            3 -> StyleConfig("Monospace Light", FontFamily.Monospace, FontWeight.Light, FontStyle.Normal, 0.sp, "monospace", Typeface.NORMAL)
            4 -> StyleConfig("Condensed Italic", FontFamily.SansSerif, FontWeight.Normal, FontStyle.Italic, 1.sp, "sans-serif-condensed", Typeface.ITALIC)
            5 -> StyleConfig("Cursive Elegant", FontFamily(Typeface.create("cursive", Typeface.NORMAL)), FontWeight.Medium, FontStyle.Normal, 2.sp, "cursive", Typeface.NORMAL)
            6 -> StyleConfig("Casual Warm", FontFamily(Typeface.create("casual", Typeface.NORMAL)), FontWeight.SemiBold, FontStyle.Normal, 1.sp, "casual", Typeface.BOLD)
            7 -> StyleConfig("Serif Elegant Italic", FontFamily.Serif, FontWeight.Normal, FontStyle.Italic, 2.sp, "serif", Typeface.ITALIC)
            8 -> StyleConfig("Condensed Tight", FontFamily.SansSerif, FontWeight.Bold, FontStyle.Normal, (-1).sp, "sans-serif-condensed", Typeface.BOLD)
            9 -> StyleConfig("Black Impact", FontFamily.SansSerif, FontWeight.Black, FontStyle.Normal, 1.sp, "sans-serif-black", Typeface.BOLD)
            10 -> StyleConfig("Monospace Space", FontFamily.Monospace, FontWeight.Normal, FontStyle.Normal, 4.sp, "monospace", Typeface.NORMAL)
            11 -> StyleConfig("Medium Spaced", FontFamily.Default, FontWeight.Medium, FontStyle.Normal, 3.sp, "sans-serif", Typeface.NORMAL)
            12 -> StyleConfig("Serif Medium Italic", FontFamily.Serif, FontWeight.Medium, FontStyle.Italic, 1.sp, "serif", Typeface.ITALIC)
            13 -> StyleConfig("Thin Airy", FontFamily.SansSerif, FontWeight.Thin, FontStyle.Normal, 2.sp, "sans-serif-thin", Typeface.NORMAL)
            14 -> StyleConfig("Casual Loose", FontFamily(Typeface.create("casual", Typeface.NORMAL)), FontWeight.Normal, FontStyle.Normal, 3.sp, "casual", Typeface.NORMAL)
            15 -> StyleConfig("Cursive Bold", FontFamily(Typeface.create("cursive", Typeface.BOLD)), FontWeight.Bold, FontStyle.Normal, 1.sp, "cursive", Typeface.BOLD)
            16 -> StyleConfig("Condensed Small Caps", FontFamily(Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)), FontWeight.Normal, FontStyle.Normal, 2.sp, "sans-serif-smallcaps", Typeface.NORMAL)
            17 -> StyleConfig("Serif Compact", FontFamily.Serif, FontWeight.ExtraBold, FontStyle.Normal, (-0.5).sp, "serif", Typeface.BOLD)
            18 -> StyleConfig("Monospace Bold", FontFamily.Monospace, FontWeight.Bold, FontStyle.Normal, 2.sp, "monospace", Typeface.BOLD)
            19 -> StyleConfig("Light Wide", FontFamily.SansSerif, FontWeight.Light, FontStyle.Normal, 5.sp, "sans-serif-light", Typeface.NORMAL)
            20 -> StyleConfig("Condensed Bold Italic", FontFamily.SansSerif, FontWeight.Bold, FontStyle.Italic, 0.sp, "sans-serif-condensed", Typeface.BOLD_ITALIC)
            21 -> StyleConfig("Black Italic", FontFamily.SansSerif, FontWeight.Black, FontStyle.Italic, 1.sp, "sans-serif-black", Typeface.ITALIC)
            22 -> StyleConfig("Cursive Light", FontFamily(Typeface.create("cursive", Typeface.NORMAL)), FontWeight.Light, FontStyle.Normal, 3.sp, "cursive", Typeface.NORMAL)
            23 -> StyleConfig("Casual Thin", FontFamily(Typeface.create("casual", Typeface.NORMAL)), FontWeight.Light, FontStyle.Normal, 1.sp, "casual", Typeface.NORMAL)
            24 -> StyleConfig("Serif Large Spacing", FontFamily.Serif, FontWeight.Normal, FontStyle.Normal, 6.sp, "serif", Typeface.NORMAL)
            25 -> StyleConfig("Condensed Medium", FontFamily.SansSerif, FontWeight.Medium, FontStyle.Normal, 1.sp, "sans-serif-condensed", Typeface.NORMAL)
            26 -> StyleConfig("Monospace Tight", FontFamily.Monospace, FontWeight.Normal, FontStyle.Normal, (-1).sp, "monospace", Typeface.NORMAL)
            27 -> StyleConfig("Thin Cursive", FontFamily(Typeface.create("cursive", Typeface.NORMAL)), FontWeight.Thin, FontStyle.Italic, 2.sp, "cursive", Typeface.ITALIC)
            28 -> StyleConfig("SemiBold Sans", FontFamily.SansSerif, FontWeight.SemiBold, FontStyle.Normal, 1.sp, "sans-serif", Typeface.BOLD)
            29 -> StyleConfig("Condensed Light", FontFamily.SansSerif, FontWeight.Light, FontStyle.Normal, 0.sp, "sans-serif-condensed", Typeface.NORMAL)
            30 -> StyleConfig("Serif Bold Italic", FontFamily.Serif, FontWeight.Bold, FontStyle.Italic, 2.sp, "serif", Typeface.BOLD_ITALIC)
            31 -> StyleConfig("Small Caps Bold", FontFamily(Typeface.create("sans-serif-smallcaps", Typeface.BOLD)), FontWeight.Bold, FontStyle.Normal, 2.sp, "sans-serif-smallcaps", Typeface.BOLD)
            32 -> StyleConfig("Monospace Elegant", FontFamily.Monospace, FontWeight.Light, FontStyle.Italic, 2.sp, "monospace", Typeface.ITALIC)
            33 -> StyleConfig("Casual Dense", FontFamily(Typeface.create("casual", Typeface.NORMAL)), FontWeight.Bold, FontStyle.Normal, (-0.5).sp, "casual", Typeface.BOLD)
            34 -> StyleConfig("Elegant Sans", FontFamily.SansSerif, FontWeight.Medium, FontStyle.Italic, 3.sp, "sans-serif", Typeface.ITALIC)
            35 -> StyleConfig("Cursive Space", FontFamily(Typeface.create("cursive", Typeface.NORMAL)), FontWeight.Normal, FontStyle.Normal, 5.sp, "cursive", Typeface.NORMAL)
            36 -> StyleConfig("Serif Extra Light", FontFamily.Serif, FontWeight.ExtraLight, FontStyle.Normal, 1.sp, "serif", Typeface.NORMAL)
            37 -> StyleConfig("Condensed Black", FontFamily.SansSerif, FontWeight.Black, FontStyle.Normal, 0.5.sp, "sans-serif-condensed", Typeface.BOLD)
            38 -> StyleConfig("Monospace Wide Italic", FontFamily.Monospace, FontWeight.Normal, FontStyle.Italic, 4.sp, "monospace", Typeface.ITALIC)
            39 -> StyleConfig("Casual Compact", FontFamily(Typeface.create("casual", Typeface.NORMAL)), FontWeight.Medium, FontStyle.Normal, (-0.5).sp, "casual", Typeface.NORMAL)
            40 -> StyleConfig("Serif Ultra Bold", FontFamily.Serif, FontWeight.Black, FontStyle.Normal, 1.5.sp, "serif", Typeface.BOLD)
            41 -> StyleConfig("Sans Wide Tracking", FontFamily.SansSerif, FontWeight.Light, FontStyle.Normal, 8.sp, "sans-serif-light", Typeface.NORMAL)
            42 -> StyleConfig("Condensed Thin Italic", FontFamily.SansSerif, FontWeight.Thin, FontStyle.Italic, 1.sp, "sans-serif-condensed", Typeface.ITALIC)
            43 -> StyleConfig("Small Caps Light", FontFamily(Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)), FontWeight.Light, FontStyle.Normal, 3.sp, "sans-serif-smallcaps", Typeface.NORMAL)
            44 -> StyleConfig("Monospace Heavy", FontFamily.Monospace, FontWeight.Bold, FontStyle.Normal, (-1.5).sp, "monospace", Typeface.BOLD)
            45 -> StyleConfig("Casual Elegant Italic", FontFamily(Typeface.create("casual", Typeface.NORMAL)), FontWeight.Normal, FontStyle.Italic, 2.sp, "casual", Typeface.ITALIC)
            46 -> StyleConfig("Cursive Dense", FontFamily(Typeface.create("cursive", Typeface.NORMAL)), FontWeight.Bold, FontStyle.Normal, (-1).sp, "cursive", Typeface.BOLD)
            47 -> StyleConfig("Sans Condensed Bold", FontFamily.SansSerif, FontWeight.Bold, FontStyle.Normal, 2.sp, "sans-serif-condensed", Typeface.BOLD)
            48 -> StyleConfig("Serif Delicate", FontFamily.Serif, FontWeight.Thin, FontStyle.Italic, 3.sp, "serif", Typeface.ITALIC)
            49 -> StyleConfig("Monospace Medium", FontFamily.Monospace, FontWeight.Medium, FontStyle.Normal, 1.5.sp, "monospace", Typeface.NORMAL)
            else -> StyleConfig("Classic Modern", FontFamily.Default, FontWeight.SemiBold, FontStyle.Normal, 4.sp, "sans-serif", Typeface.BOLD)
        }
    }

    fun getStyle(fontFamilyVal: String): StyleConfig {
        if (fontFamilyVal == "Default") return list[0]
        val matchResult = Regex("Font(\\d+)").find(fontFamilyVal)
        if (matchResult != null) {
            val num = matchResult.groupValues[1].toIntOrNull()
            if (num != null && num in 1..50) {
                return list[num - 1]
            }
        }
        return list[0]
    }

    fun getTypeface(fontFamilyVal: String, isBold: Boolean): Typeface {
        val style = getStyle(fontFamilyVal)
        val baseTypeface = Typeface.create(style.systemFontName, Typeface.NORMAL)
        var finalStyle = style.customTypefaceStyle
        if (isBold) {
            finalStyle = if (finalStyle == Typeface.ITALIC) Typeface.BOLD_ITALIC else Typeface.BOLD
        }
        return Typeface.create(baseTypeface, finalStyle)
    }
}
