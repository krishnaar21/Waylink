package app.waylink.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waylink.R

/*
 * Type, from the web app's computed styles at 390 px wide (1 CSS px = 1 sp).
 * Display: Geist SemiBold, tracked tight (-0.04em). Body: the phone's own sans (Roboto), like the web app's
 * "system" body font. Mono: JetBrains Mono, for number plates and small tags.
 */
private val GEIST = FontFamily(Font(R.font.geist_regular, FontWeight.Normal), Font(R.font.geist_semibold, FontWeight.SemiBold))
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

/** A title face that stands in for every weight the titles ask for (the web sets one weight per font). */
private fun one(res: Int) = FontFamily(Font(res, FontWeight.Normal), Font(res, FontWeight.Medium), Font(res, FontWeight.SemiBold), Font(res, FontWeight.Bold))

/** Body faces: one variable file, a weight instance per step (web --bw 450, --bw-strong 600). */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int) = FontFamily(listOf(400, 500, 600, 700).map { w ->
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(if (w == 400) 450 else w)))
})

/**
 * Settings > Fonts (web FONTS and BODY_FONTS): title face with its tracking, and body face.
 * "System" titles use the phone's own face in bold, as v0 does with system-ui. Read through getters, so a change restyles everything.
 */
object Fonts {
    /** key, label, family, letter spacing (em). */
    val TITLES = listOf("system", "geist", "onest", "garamond", "crimson", "fraunces")
    val TITLE_NAMES = mapOf("system" to "System", "onest" to "Onest", "geist" to "Geist", "garamond" to "EB Garamond", "crimson" to "Crimson", "fraunces" to "Fraunces")
    val BODIES = listOf("system" to "System", "atkinson" to "Atkinson", "public" to "Public Sans")
    fun titleFamily(k: String): FontFamily = when (k) {
        "onest" -> one(R.font.onest_title)
        "garamond" -> one(R.font.garamond_title)
        "crimson" -> one(R.font.crimson_title)
        "fraunces" -> one(R.font.fraunces_title)
        "geist" -> GEIST
        else -> FontFamily.Default // web "system": the phone's own face (Roboto on Android), bold
    }
    fun titleTracking(k: String): Float = when (k) { "onest" -> -0.035f; "garamond", "crimson" -> -0.01f; "fraunces" -> -0.02f; "geist" -> -0.04f; else -> -0.025f }
    /** web --dw: system titles are 700, the rest 600 (Garamond and Crimson files carry their own weight). */
    fun titleWeight(k: String): FontWeight = if (k == "system") FontWeight.Bold else FontWeight.SemiBold
    fun bodyFamily(k: String): FontFamily = when (k) { "atkinson" -> variable(R.font.atkinson_body); "public" -> variable(R.font.public_body); else -> FontFamily.Default }

    var title by mutableStateOf("system")
    var body by mutableStateOf("system")
    private val cache = HashMap<String, FontFamily>()
    val display: FontFamily get() = cache.getOrPut("t:$title") { titleFamily(title) }
    val sans: FontFamily get() = cache.getOrPut("b:$body") { bodyFamily(body) }
    val dls get() = titleTracking(title).em
    val dw get() = titleWeight(title)
}

val Display: FontFamily get() = Fonts.display
val Body: FontFamily get() = Fonts.sans

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

object T {
    /** Page titles: "Friday", "Settings". */
    val title get() = TextStyle(fontFamily = Display, fontWeight = Fonts.dw, fontSize = 30.sp, letterSpacing = Fonts.dls, lineHeight = 33.6.sp)
    /** Onboarding step titles. */
    val onbTitle get() = title.copy(fontSize = 32.sp, lineHeight = 35.8.sp)
    val slideTitle get() = title.copy(fontSize = 28.sp, lineHeight = 32.2.sp)
    val sheetTitle get() = title.copy(fontSize = 24.sp, lineHeight = 25.2.sp)
    val verdict get() = title.copy(fontSize = 28.sp, lineHeight = 29.4.sp)
    /** Big readings; size set per use. */
    fun big(size: Float) = TextStyle(fontFamily = Display, fontWeight = Fonts.dw, fontSize = size.sp, letterSpacing = Fonts.dls, lineHeight = size.sp, lineHeightStyle = tight)
    val cardTitle get() = title.copy(fontSize = 21.sp, lineHeight = 24.sp)
    val tileTitle get() = title.copy(fontSize = 18.sp, lineHeight = 19.8.sp)

    val subtitle get() = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp)
    val body get() = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp)
    val lead get() = TextStyle(fontFamily = Body, fontSize = 15.5.sp, lineHeight = 24.sp)
    /** .sheet .lead: a touch larger than the page lead. */
    val sheetLead get() = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 25.6.sp)
    val onbP get() = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 23.25.sp)
    /** Small grey captions over values ("Odometer", "Trip A", "Verdict"). */
    val eyebrow get() = TextStyle(fontFamily = Body, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    /** Section headings on a page ("Readings", "Do these first"). */
    val section get() = TextStyle(fontFamily = Body, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, lineHeight = 27.sp)
    val link get() = TextStyle(fontFamily = Body, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    val unit get() = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 19.5.sp)
    /** .ms-t: small card titles ("Preview states", "App icon"). */
    val msT get() = TextStyle(fontFamily = Body, fontSize = 17.sp, fontWeight = FontWeight(650), lineHeight = 25.5.sp)
    val row get() = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp)
    val rowTitle get() = TextStyle(fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.5.sp)
    val rowSub get() = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 19.5.sp)
    val note get() = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.7.sp)
    val button get() = TextStyle(fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em, lineHeight = 24.sp)
    val buttonSm get() = button.copy(fontSize = 14.5.sp, lineHeight = 21.75.sp)
    val tab get() = TextStyle(fontFamily = Body, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
    val chip get() = TextStyle(fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
    val input get() = TextStyle(fontFamily = Body, fontSize = 16.5.sp, lineHeight = 24.75.sp)
    /** Number plates: mono, spaced, upper case. */
    val plate get() = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.1.em, lineHeight = 16.5.sp)
}

/** The web's .serif text: Geist at regular weight ("You've ridden 382 km…"). */
val Serif get() = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 21.sp, letterSpacing = (-0.01).em, lineHeight = 28.35.sp)
