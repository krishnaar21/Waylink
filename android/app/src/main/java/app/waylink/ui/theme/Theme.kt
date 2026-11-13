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

val LocalColors = staticCompositionLocalOf { paper(Accent.WINE) }

object W {
    val c: WaylinkColors @Composable get() = LocalColors.current
}

/** theme: "auto" follows the phone; "sketch" is Paper; "sketchdark" is Paper dark. */
@Composable
fun WaylinkTheme(theme: String, accent: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "sketch" -> false
        "sketchdark" -> true
        else -> isSystemInDarkTheme()
    }
    val a = Accent.of(accent)
    CompositionLocalProvider(LocalColors provides if (dark) paperDark(a) else paper(a), content = content)
}
