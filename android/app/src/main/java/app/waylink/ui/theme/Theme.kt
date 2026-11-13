package app.waylink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Design tokens, copied from the web app's Paper and Paper dark themes (web-v0/styles.css,
 * html[data-theme="sketch"] and its [data-night] variant). Names follow the CSS variables.
 */
@Immutable
data class WaylinkColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val sheet: Color,
    val raised: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val line: Color,
    val segOff: Color,
    val sage: Color,
    val sageInk: Color,
    val amber: Color,
    val red: Color,
    val tintSage: Color,
    val tintAmber: Color,
    val tintRed: Color,
    val heroOk: List<Color>,
    val heroSoon: List<Color>,
    val heroBad: List<Color>,
    val heroOff: List<Color>,
    val accent: Color,
    val onAccent: Color,
    val backdrop: Color,
) {
    /** color-mix(in srgb, var(--ink) N%, transparent) */
    fun inkA(alpha: Float) = ink.copy(alpha = alpha)
    fun hero(colors: List<Color>) = Brush.verticalGradient(colors)
}

enum class Accent(val key: String, val label: String, val light: Color, val dark: Color, val onDark: Color = Color.White) {
    RUST("rust", "Rust", Color(0xFFBF4128), Color(0xFFE46A48)),
    WINE("wine", "Wine", Color(0xFFA8162A), Color(0xFFDA4B5C)),
    BLUE("blue", "Blue", Color(0xFF1D56CF), Color(0xFF6A97EE)),
    SLATE("slate", "Stealth", Color(0xFF4E5A6E), Color(0xFFA7B1C2), Color(0xFF1E1B17)),
    AMBER("amber", "Amber", Color(0xFFA86610), Color(0xFFE19A46), Color(0xFF1E1B17)),
    MINT("mint", "Mint", Color(0xFF24715F), Color(0xFF4FB396), Color(0xFF1E1B17));

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: WINE }
}

fun paper(accent: Accent) = WaylinkColors(
    dark = false,
    bg = Color(0xFFEEE7DA), card = Color(0xFFF7F1E6), sheet = Color(0xFFF4EEE2), raised = Color(0xFFFBF7EF),
    ink = Color(0xFF2A2620), ink2 = Color(0xFF4B4539), ink3 = Color(0xFF746C5E), ink4 = Color(0xFFA49B8B),
    line = Color(0x1F3C301E), segOff = Color(0xFFDDD3C2),
    sage = Color(0xFF7F9C8A), sageInk = Color(0xFF3F6150), amber = Color(0xFFB86E10), red = Color(0xFFB8352A),
    tintSage = Color(0xFFE2E6D6), tintAmber = Color(0xFFF1E2C6), tintRed = Color(0xFFF2DDD4),
    heroOk = listOf(Color(0xFFE3E6D3), Color(0xFFEEEADF)),
    heroSoon = listOf(Color(0xFFF0E0C2), Color(0xFFF2EADB)),
    heroBad = listOf(Color(0xFFEFD8CC), Color(0xFFF2E6DD)),
    heroOff = listOf(Color(0xFFE7E0D3), Color(0xFFEEE8DE)),
    accent = accent.light, onAccent = Color.White,
    backdrop = Color(0x523C3223),
)

fun paperDark(accent: Accent) = WaylinkColors(
    dark = true,
    bg = Color(0xFF1E1B17), card = Color(0xFF2A2621), sheet = Color(0xFF2C2823), raised = Color(0xFF35302A),
    ink = Color(0xFFEDE5D6), ink2 = Color(0xFFCBC1AF), ink3 = Color(0xFF9C9282), ink4 = Color(0xFF6F675B),
    line = Color(0x1CF0E1C8), segOff = Color(0xFF3B352E),
    sage = Color(0xFF93A584), sageInk = Color(0xFFB8C9A8), amber = Color(0xFFE3A452), red = Color(0xFFEE8C72),
    tintSage = Color(0xFF2C3327), tintAmber = Color(0xFF3A2F1E), tintRed = Color(0xFF3A2722),
    heroOk = listOf(Color(0xFF2E3529), Color(0xFF262520)),
    heroSoon = listOf(Color(0xFF3B301F), Color(0xFF2A2620)),
    heroBad = listOf(Color(0xFF3D2823), Color(0xFF2A2421)),
    heroOff = listOf(Color(0xFF2E2A24), Color(0xFF262320)),
    accent = accent.dark, onAccent = accent.onDark,
    backdrop = Color(0x8C000000),
)

/** Brighter accents on the dark Light/Dark/Navy themes ([data-theme="obsidian"|"amoled"][data-accent]). */
private fun nightAccent(a: Accent): Pair<Color, Color> = when (a) {
    Accent.WINE -> Color(0xFFEC3446) to Color.White
    Accent.RUST -> Color(0xFFE8603F) to Color.White
    Accent.BLUE -> Color(0xFF4C8DFF) to Color.White
    Accent.SLATE -> Color(0xFF93A0B6) to Color(0xFF111111)
    Accent.AMBER -> Color(0xFFE8A93F) to Color(0xFF111111)
    Accent.MINT -> Color(0xFF4FC3A1) to Color(0xFF111111)
}

/** "Light" (web data-theme="paper"): cool greys. */
fun cool(accent: Accent) = WaylinkColors(
    dark = false,
    bg = Color(0xFFEDEFF2), card = Color(0xFFF9FAFB), sheet = Color(0xFFF5F6F8), raised = Color(0xFFFFFFFF),
    ink = Color(0xFF12151A), ink2 = Color(0xFF3C424B), ink3 = Color(0xFF626973), ink4 = Color(0xFF9AA0A9),
    line = Color(0x1112151A), segOff = Color(0xFFD9DDE2),
    sage = Color(0xFF7F9C8A), sageInk = Color(0xFF3F6150), amber = Color(0xFFB86E10), red = Color(0xFFB8352A),
    tintSage = Color(0xFFE1EAE4), tintAmber = Color(0xFFF4E9D6), tintRed = Color(0xFFF7E6E3),
    heroOk = listOf(Color(0xFFD8E5DD), Color(0xFFE5EDE8), Color(0xFFEEF2EF)),
    heroSoon = listOf(Color(0xFFF1E4CB), Color(0xFFF5EDDF), Color(0xFFF7F4EE)),
    heroBad = listOf(Color(0xFFF1DAD5), Color(0xFFF5E7E4), Color(0xFFF7F0EF)),
    heroOff = listOf(Color(0xFFE1E4E9), Color(0xFFE9EBEF), Color(0xFFF0F1F4)),
    accent = accent.light, onAccent = Color.White,
    backdrop = Color(0x5719202C),
)

/** "Dark" (obsidian) and "Navy" (amoled). */
fun night(accent: Accent, navy: Boolean): WaylinkColors {
    val (ac, on) = nightAccent(accent)
    return WaylinkColors(
        dark = true,
        bg = if (navy) Color(0xFF0B1530) else Color(0xFF0E1013), card = if (navy) Color(0xFF13203F) else Color(0xFF171A1F),
        sheet = if (navy) Color(0xFF15234A) else Color(0xFF1A1D22), raised = if (navy) Color(0xFF1C2B55) else Color(0xFF23272D),
        ink = if (navy) Color(0xFFEAF0FF) else Color(0xFFEDEFF3), ink2 = if (navy) Color(0xFFB9C4E2) else Color(0xFFBCC2CB),
        ink3 = if (navy) Color(0xFF8794B8) else Color(0xFF8C939D), ink4 = if (navy) Color(0xFF5B6890) else Color(0xFF5E656E),
        line = if (navy) Color(0x12C8D7FF) else Color(0x0FFFFFFF), segOff = if (navy) Color(0xFF26365F) else Color(0xFF2D323A),
        sage = Color(0xFF86A796), sageInk = Color(0xFFA9C9B8), amber = Color(0xFFE6A24E), red = Color(0xFFF08A7C),
        tintSage = Color(0xFF1E2B25), tintAmber = Color(0xFF33291C), tintRed = Color(0xFF33211F),
        heroOk = listOf(Color(0xFF1F2D27), Color(0xFF1A2320), Color(0xFF171C1C)),
        heroSoon = listOf(Color(0xFF342A1C), Color(0xFF28231D), Color(0xFF1E1D1C)),
        heroBad = listOf(Color(0xFF3A2322), Color(0xFF2B2021), Color(0xFF201C1F)),
        heroOff = listOf(Color(0xFF22262C), Color(0xFF1B1E23)),
        accent = ac, onAccent = on,
        backdrop = Color(0x99000000),
    )
}

/** Every theme the picker can show: key, label, page / card / ink colours for its little preview, and its subtitle. */
val THEMES = listOf(
    listOf("auto", "Auto", "#EEE7DA", "#F7F1E6", "#2A2620", "Follows phone"),
    listOf("paper", "Light", "#EDEFF2", "#F9FAFB", "#12151A", "Light"),
    listOf("obsidian", "Dark", "#0E1013", "#171A1F", "#EDEFF3", "Dark"),
    listOf("amoled", "Navy", "#0B1530", "#13203F", "#EAF0FF", "Dark"),
    listOf("sketch", "Paper", "#EEE7DA", "#F7F1E6", "#2A2620", "Light"),
    listOf("sketchdark", "Paper dark", "#1E1B17", "#2A2621", "#EDE5D6", "Dark"),
)

val LocalColors = staticCompositionLocalOf { paper(Accent.WINE) }

object W {
    val c: WaylinkColors @Composable get() = LocalColors.current
}

/** theme: "auto" follows the phone; "sketch" is Paper; "sketchdark" is Paper dark. */
@Composable
fun WaylinkTheme(theme: String, accent: String, font: String = "system", bodyFont: String = "system", content: @Composable () -> Unit) {
    if (Fonts.title != font) Fonts.title = font
    if (Fonts.body != bodyFont) Fonts.body = bodyFont
    val sys = isSystemInDarkTheme()
    val a = Accent.of(accent)
    val colors = when (theme) {
        "sketch" -> paper(a)
        "sketchdark" -> paperDark(a)
        "paper" -> cool(a)
        "obsidian" -> night(a, navy = false)
        "amoled" -> night(a, navy = true)
        else -> if (sys) paperDark(a) else paper(a)
    }
    CompositionLocalProvider(LocalColors provides colors, content = content)
}
