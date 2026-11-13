package app.waylink.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waylink.ui.theme.Body
import app.waylink.ui.theme.W

/** The "loop" W from the web app's wordmark, drawn in a 100×100 box. */
private const val LOOP_W = "M2.5 44C3 64 12 80 25 80C37 80 42 64 46 50C48.8 40.5 54.5 31 54 22C53.6 13.5 39 12.5 38.6 22C38.2 31 43.4 40.5 46.8 50C51 64 58 80 71 80C84 80 90 56 91.5 26L91.5 14"

/** The Waylink wordmark: an accent loop "W" and "aylink". [animate] draws the W in, as on the web splash. */
@Composable
fun Logo(fontSize: Float = 27f, modifier: Modifier = Modifier, animate: Boolean = false) {
    val c = W.c
    val path = remember { PathParser().parsePathString(LOOP_W).toPath() }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, 150, CubicBezierEasing(.6f, 0f, .3f, 1f))) }
    Row(modifier.semantics { contentDescription = "Waylink" }, verticalAlignment = Alignment.Bottom) {
        val em = fontSize
        Canvas(Modifier.size((em * 0.98f).sp.toDp(), em.sp.toDp()).offset(y = (em * 0.2f).sp.toDp())) {
            val s = size.height / 100f
            val drawn = if (progress.value >= 1f) path else Path().also { out ->
                val pm = PathMeasure().apply { setPath(path, false) }
                pm.getSegment(0f, pm.length * progress.value, out, true)
            }
            scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(drawn, c.accent, style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Txt(
            "aylink",
            TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = fontSize.sp, letterSpacing = (-0.035).em, lineHeight = fontSize.sp),
            c.ink,
            Modifier.offset(x = (-0.02f * em).sp.toDp()),
        )
    }
}

@Composable
private fun androidx.compose.ui.unit.TextUnit.toDp() = with(androidx.compose.ui.platform.LocalDensity.current) { this@toDp.toDp() }
