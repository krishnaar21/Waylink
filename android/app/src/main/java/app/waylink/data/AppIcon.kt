package app.waylink.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon, like the web app's Home Screen icon choice (ICON_STYLES).
 * Each style is an activity-alias in the manifest; exactly one is enabled. "classic" follows the accent colour.
 */
object AppIcon {
    val STYLES = listOf("classic" to "Classic", "chrome" to "Chrome", "retro" to "Retro", "leather" to "Leather", "ascii" to "ASCII", "wool" to "Wool", "oil" to "Oil slick", "stitch" to "Stitch")
    private val ACCENTS = listOf("rust", "wine", "blue", "slate", "amber", "mint")

    /** The alias key for a style and accent: "chrome", or "classic_wine". */
    fun key(style: String, accent: String): String =
        if (style == "classic" || STYLES.none { it.first == style }) "classic_" + (accent.takeIf { it in ACCENTS } ?: "wine") else style

    private fun aliases(): List<String> = listOf("Launcher") + STYLES.drop(1).map { "Icon_" + it.first } + ACCENTS.map { "Icon_classic_$it" }

    fun apply(ctx: Context, style: String, accent: String) {
        val want = key(style, accent).let { if (it == "classic_wine") "Launcher" else "Icon_$it" }
        val pm = ctx.packageManager
        try {
            // Enable the new one first, so the app always has a launcher entry.
            aliases().sortedBy { if (it == want) 0 else 1 }.forEach { a ->
                val cn = ComponentName(ctx.packageName, "app.waylink.$a")
                val state = if (a == want) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                if (pm.getComponentEnabledSetting(cn) != state) pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) { }
    }
}
