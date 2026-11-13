package app.waylink.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waylink.R

/*
 * Type, from the web app's computed styles at 390 px wide (1 CSS px = 1 sp).
 * Display: Geist SemiBold, tracked tight (-0.04em). Body: the phone's own sans (Roboto), like the web app's
 * "system" body font. Mono: JetBrains Mono, for number plates and small tags.
 */
val Display = FontFamily(Font(R.font.geist_regular, FontWeight.Normal), Font(R.font.geist_semibold, FontWeight.SemiBold))
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_medium, FontWeight.Medium))
val Body = FontFamily.Default

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

object T {
    /** Page titles: "Friday", "Settings". */
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.04).em, lineHeight = 33.6.sp)
    /** Onboarding step titles. */
    val onbTitle = title.copy(fontSize = 32.sp, lineHeight = 35.8.sp)
    val slideTitle = title.copy(fontSize = 28.sp, lineHeight = 32.2.sp)
    val sheetTitle = title.copy(fontSize = 24.sp, lineHeight = 25.2.sp)
    val verdict = title.copy(fontSize = 28.sp, lineHeight = 29.4.sp)
    /** Big readings; size set per use. */
    fun big(size: Float) = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = size.sp, letterSpacing = (-0.04).em, lineHeight = size.sp, lineHeightStyle = tight)
    val cardTitle = title.copy(fontSize = 21.sp, lineHeight = 24.sp)
    val tileTitle = title.copy(fontSize = 18.sp, lineHeight = 19.8.sp)

    val subtitle = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp)
    val lead = TextStyle(fontFamily = Body, fontSize = 15.5.sp, lineHeight = 24.sp)
    val onbP = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 23.25.sp)
    /** Small grey captions over values ("Odometer", "Trip A", "Verdict"). */
    val eyebrow = TextStyle(fontFamily = Body, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    /** Section headings on a page ("Readings", "Do these first"). */
    val section = TextStyle(fontFamily = Body, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, lineHeight = 27.sp)
    val link = TextStyle(fontFamily = Body, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    val unit = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 19.5.sp)
    val row = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp)
    val rowTitle = TextStyle(fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.5.sp)
    val rowSub = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 19.5.sp)
    val note = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.7.sp)
    val button = TextStyle(fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em, lineHeight = 24.sp)
    val buttonSm = button.copy(fontSize = 14.5.sp, lineHeight = 21.75.sp)
    val tab = TextStyle(fontFamily = Body, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
    val chip = TextStyle(fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
    val input = TextStyle(fontFamily = Body, fontSize = 16.5.sp, lineHeight = 24.75.sp)
    /** Number plates: mono, spaced, upper case. */
    val plate = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.1.em, lineHeight = 16.5.sp)
}

/** The web's .serif text: Geist at regular weight ("You've ridden 382 km…"). */
val Serif = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 21.sp, letterSpacing = (-0.01).em, lineHeight = 28.35.sp)
