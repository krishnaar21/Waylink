package app.waylink.ui.fuel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * Two tank cross-sections side by side: stopped at the pump's click (a gap under the neck, different each time)
 * and filled to the brim (fuel up into the neck, the same line every time).
 */
@Composable
fun BrimPicture() {
    val c = W.c
    Row(
        Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 12.dp, vertical = 16.dp)
            .semantics { contentDescription = "Two fuel tanks. Left: stopped at the click, with a gap below the filler neck. Right: filled to the brim, fuel up into the neck." },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tank(0.74f, false, "At the click", "Gap changes each time", c.amber, Modifier.weight(1f))
        Tank(1f, true, "To the brim", "Same line every time", c.sage, Modifier.weight(1f))
    }
}

@Composable
private fun Tank(level: Float, brim: Boolean, title: String, sub: String, mark: Color, modifier: Modifier) {
    val c = W.c
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(118.dp)) {
            drawTank(level, brim, c.ink, c.accent, c.inkA(0.06f), mark)
        }
        Spacer(Modifier.height(10.dp))
        Txt(title, T.rowTitle.copy(fontSize = 14.sp, textAlign = TextAlign.Center), c.ink)
        Txt(sub, T.rowSub.copy(fontSize = 12.sp, textAlign = TextAlign.Center), c.ink3)
    }
}

private fun DrawScope.drawTank(level: Float, brim: Boolean, ink: Color, fuel: Color, empty: Color, mark: Color) {
    val w = size.width; val h = size.height
    val bodyW = minOf(w * 0.86f, h * 1.25f)
    val left = (w - bodyW) / 2
    val neckW = bodyW * 0.26f; val neckH = h * 0.16f
    val bodyTop = neckH; val bodyH = h - neckH - 2f
    val neckL = (w - neckW) / 2
    val r = 18.dp.toPx()
    val outline = Path().apply {
        moveTo(neckL, 0f); lineTo(neckL, bodyTop)
        lineTo(left + r, bodyTop); quadraticBezierTo(left, bodyTop, left, bodyTop + r)
        lineTo(left, bodyTop + bodyH - r); quadraticBezierTo(left, bodyTop + bodyH, left + r, bodyTop + bodyH)
        lineTo(left + bodyW - r, bodyTop + bodyH); quadraticBezierTo(left + bodyW, bodyTop + bodyH, left + bodyW, bodyTop + bodyH - r)
        lineTo(left + bodyW, bodyTop + r); quadraticBezierTo(left + bodyW, bodyTop, left + bodyW - r, bodyTop)
        lineTo(neckL + neckW, bodyTop); lineTo(neckL + neckW, 0f)
    }
    val inside = Path().apply { addPath(outline); close() }
    // The fuel: up into the neck when brimmed, else a share of the body.
    val top = if (brim) neckH * 0.35f else bodyTop + bodyH * (1 - level)
    clipPath(inside) {
        drawRect(empty, Offset(0f, 0f), Size(w, h))
        drawRect(fuel.copy(alpha = 0.85f), Offset(0f, top), Size(w, h - top))
    }
    drawPath(outline, ink, style = Stroke(2.dp.toPx()))
    // The line the next fill is measured from.
    drawLine(mark, Offset(left - 4.dp.toPx(), top), Offset(left + bodyW + 4.dp.toPx(), top), 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    if (!brim) drawRoundRect(mark.copy(alpha = 0.25f), Offset(left + 3.dp.toPx(), bodyTop + 2.dp.toPx()), Size(bodyW - 6.dp.toPx(), top - bodyTop - 2.dp.toPx()), CornerRadius(r * 0.6f))
}
