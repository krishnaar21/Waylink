package app.waylink.ui.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.waylink.ui.components.Txt
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** Arrow shapes in a 100 × 124 box. Left turns are the right ones mirrored. Mirrors G in web-v0/js/views.js. */
private class Glyph(val b: String = "", val h: String = "", val f: String? = null, val c: Boolean = false, val kind: String? = null)

private val G = mapOf(
    "straight" to Glyph("M50 112V18", "M27 41L50 18L73 41"),
    "right" to Glyph("M34 112V66Q34 44 56 44H84", "M66 26L84 44L66 62"),
    "slight-right" to Glyph("M40 112V74L73 41", "M51 37H77V63"),
    "sharp-right" to Glyph("M34 112V40L72 78", "M50 82H76V56"),
    "uturn-left" to Glyph("M72 112V50Q72 22 49 22Q26 22 26 50V84", "M10 68L26 84L42 68"),
    "keep-right" to Glyph("M36 112V82L64 54V22", "M46 40L64 22L82 40", f = "M36 112V12"),
    "fork-right" to Glyph("M50 112V66L72 22", "M53 30L73 19L79 41", f = "M50 112V66L28 22"),
    "ramp-right" to Glyph("M38 112V80Q38 62 54 52L78 36", "M58 31L81 34L74 56", f = "M38 112V12"),
    "merge" to Glyph("M78 112Q78 74 50 56V18", "M27 41L50 18L73 41", f = "M22 112Q22 74 50 56"),
    "roundabout-right" to Glyph("M50 112V80A20 20 0 1 1 72 58H90", "M75 42L91 58L75 74", c = true),
    "destination" to Glyph(kind = "flag"),
    "ferry" to Glyph(kind = "ferry"),
    "train" to Glyph(kind = "train"),
    "wrong-way" to Glyph(kind = "wrong"),
)

private val cache = HashMap<String, Path>()
private fun path(d: String): Path = cache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/** The turn arrow in the accent colour; roundabouts show the exit number. [sw] is the stroke in box units (12 on the big card). */
@Composable
fun TurnArrow(glyph: String, modifier: Modifier, exit: Int = 0, faint: Float = 0.32f, sw: Float = 12f) {
    val c = W.c
    val accent = c.accent
    val (g, mirror) = remember(glyph) {
        when {
            glyph == "left" -> "right" to true
            glyph == "uturn-right" -> "uturn-left" to true
            glyph != "uturn-left" && glyph.endsWith("-left") -> glyph.removeSuffix("-left") + "-right" to true
            else -> glyph to false
        }
    }
    val d = G[g] ?: G.getValue("straight")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val k = minOf(size.width / 100f, size.height / 124f)
            val ox = (size.width - 100f * k) / 2f
            val oy = (size.height - 124f * k) / 2f
            translate(ox, oy) {
                withTransform({
                    scale(k, k, pivot = Offset.Zero)
                    if (mirror) scale(-1f, 1f, pivot = Offset(50f, 0f))
                }) { drawGlyph(d, accent, faint, sw) }
            }
        }
        if (d.c && exit > 0) Txt("$exit", T.plate.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp), c.ink)
    }
}

private fun DrawScope.drawGlyph(d: Glyph, col: Color, faint: Float, sw: Float) {
    fun st(w: Float = sw) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val r = sw / 12f
    val wave = "M8 110q10-8 21 0t21 0t21 0t21 0"
    when (d.kind) {
        "flag" -> {
            drawPath(path("M30 112V14"), col, style = st())
            drawPath(path("M30 16H82L70 34L82 52H30Z"), col)
        }
        "ferry" -> {
            drawPath(path("M14 72H86L74 94H26Z"), col)
            drawPath(path("M36 72V54H64V72M50 54V34"), col, style = st(9f * r))
            drawPath(path(wave), col.copy(alpha = faint), style = st(6f * r))
        }
        "train" -> {
            drawRoundRect(col, Offset(24f, 18f), Size(52f, 66f), CornerRadius(12f), style = st(9f * r))
            drawPath(path("M34 38H66M34 66h4M62 66h4"), col, style = st(8f * r))
            drawPath(path(wave), col.copy(alpha = faint), style = st(6f * r))
        }
        "wrong" -> {
            drawCircle(col, 36f, Offset(50f, 62f), style = st(10f * r))
            drawPath(path("M30 62H70"), col, style = st())
        }
        else -> {
            d.f?.let { drawPath(path(it), col.copy(alpha = faint), style = st()) }
            if (d.c) drawCircle(col.copy(alpha = faint), 22f, Offset(50f, 58f), style = Stroke(8f * r))
            drawPath(path(d.b), col, style = st())
            drawPath(path(d.h), col, style = st())
        }
    }
}
