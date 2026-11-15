package app.waylink.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** The web app's --spring: cubic-bezier(.32,.72,0,1). Used for nearly every move. */
val Spring = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Sheets leaving: cubic-bezier(.4,0,1,1). */
val EaseIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/** Endless loops (turn signs, loader, link picture) hold still when true: screenshot tests set it so a frame can settle. */
object Motion { @JvmStatic var still = false }
