package app.waylink.ui.route

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Txt
import app.waylink.ui.theme.Motion
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * A generic left switch of a Bluetooth Pulsar as the rider sees it: dip switch, the M button, indicator, horn and
 * the pass trigger at the front. The M button pulses, since it's the one that answers. Simplified, not to scale.
 */
@Composable
fun HandlebarSample(modifier: Modifier = Modifier) {
    val c = W.c
    val pulse = if (Motion.still) 0.5f else {
        val t = rememberInfiniteTransition(label = "button")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulse")
        v
    }
    val accent = c.accent; val ink3 = c.ink3; val bg = c.card
    val tm = rememberTextMeasurer()
    val tag = TextStyle(fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Color(0xFFB8BEC5))
    Box(modifier.fillMaxWidth().height(206.dp).clip(RoundedCornerShape(20.dp)).background(c.inkA(0.04f))
        .semantics { contentDescription = "Drawing of the left handlebar switch with the M button highlighted" }) {
        Canvas(Modifier.fillMaxWidth().height(206.dp)) {
            fun d(v: Float) = v.dp.toPx()
            fun rr(col: Color, x: Float, y: Float, w: Float, h: Float, r: Float, stroke: Float = 0f) =
                drawRoundRect(col, Offset(d(x), d(y)), Size(d(w), d(h)), CornerRadius(d(r)), style = if (stroke > 0f) Stroke(d(stroke)) else androidx.compose.ui.graphics.drawscope.Fill)
            // The bar runs under the switch; the rubber grip sticks out on the left.
            rr(Color(0xFF8F969D), 150f, 94f, 40f, 18f, 9f)
            rr(Color(0xFF26292D), -20f, 86f, 64f, 34f, 14f)
            var gx = 4f; while (gx < 40f) { drawLine(Color(0xFF383C41), Offset(d(gx), d(90f)), Offset(d(gx), d(116f)), d(2f)); gx += 6f }
            // The housing: a rounded pod, lit from above.
            val pod = Path().apply {
                moveTo(d(60f), d(40f)); cubicTo(d(60f), d(22f), d(80f), d(18f), d(108f), d(18f))
                cubicTo(d(140f), d(18f), d(160f), d(24f), d(162f), d(44f))
                lineTo(d(166f), d(160f)); cubicTo(d(166f), d(184f), d(146f), d(190f), d(112f), d(190f))
                cubicTo(d(78f), d(190f), d(56f), d(184f), d(56f), d(160f)); close()
            }
            drawPath(pod, Brush.verticalGradient(listOf(Color(0xFF2B2F34), Color(0xFF17191C)), d(18f), d(190f)))
            drawPath(pod, Color(0xFF3B4046), style = Stroke(d(1.5f)))
            // Pass trigger at the front edge, under the index finger.
            rr(Color(0xFF41464C), 162f, 34f, 9f, 26f, 4f)
            // Headlamp high / low rocker.
            rr(Color(0xFF3A3F45), 80f, 30f, 60f, 20f, 10f)
            drawLine(Color(0xFF9AA1A8), Offset(d(92f), d(40f)), Offset(d(102f), d(40f)), d(1.5f), StrokeCap.Round)
            drawLine(Color(0xFF9AA1A8), Offset(d(118f), d(35f)), Offset(d(128f), d(35f)), d(1.5f), StrokeCap.Round)
            drawLine(Color(0xFF9AA1A8), Offset(d(118f), d(40f)), Offset(d(128f), d(40f)), d(1.5f), StrokeCap.Round)
            drawLine(Color(0xFF9AA1A8), Offset(d(118f), d(45f)), Offset(d(128f), d(45f)), d(1.5f), StrokeCap.Round)
            // The M button, in the accent, with a ring that grows and fades. Most Bluetooth Pulsars mark it M;
            // some show a phone. A small phone badge says it handles calls.
            val call = Offset(d(110f), d(78f)); val r = d(17f)
            drawCircle(accent.copy(alpha = 0.35f * (1f - pulse)), r + d(14f) * pulse, call)
            drawCircle(accent, r, call)
            val mt = tm.measure("M", tag.copy(fontSize = 15.sp, color = Color.White, letterSpacing = 0.sp))
            drawText(mt, topLeft = Offset(call.x - mt.size.width / 2f, call.y - mt.size.height / 2f))
            val badge = Offset(call.x + r * 0.78f, call.y - r * 0.78f)
            drawCircle(Color(0xFF1E2124), d(8f), badge); drawCircle(Color.White, d(6.5f), badge)
            handset(badge, d(3.6f), accent)
            // Indicator slider with its arrows.
            rr(Color(0xFF34383D), 72f, 104f, 80f, 22f, 11f)
            rr(Color(0xFF555B62), 101f, 106f, 22f, 18f, 9f)
            arrow(Offset(d(82f), d(115f)), d(4f), true); arrow(Offset(d(142f), d(115f)), d(4f), false)
            // Horn.
            rr(Color(0xFF3F444A), 76f, 140f, 72f, 30f, 12f)
            val ht = tm.measure("HORN", tag)
            drawText(ht, topLeft = Offset(d(112f) - ht.size.width / 2f, d(155f) - ht.size.height / 2f))
            // A leader out to the label, with a halo so it reads over the switch.
            val end = Offset(d(196f), d(58f))
            drawLine(bg, Offset(call.x + r, call.y), end, d(4f)); drawLine(accent, Offset(call.x + r, call.y), end, d(1.5f))
            drawCircle(accent, d(2.5f), end)
        }
        Column(Modifier.padding(start = 204.dp, end = 12.dp).offset(y = 40.dp)) {
            Txt("Mode (M) button", T.rowSub.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp), c.ink)
            Txt("Marked M or with a phone. Answers and declines calls", T.rowSub.copy(fontSize = 11.sp, lineHeight = 14.sp), c.ink3)
        }
        Txt("Left switch, simplified. Layout varies by model.", T.rowSub.copy(fontSize = 10.sp, lineHeight = 13.sp), c.ink3,
            Modifier.padding(start = 204.dp, end = 12.dp).offset(y = 150.dp))
    }
}

/** A phone handset glyph centred on [o]. */
private fun DrawScope.handset(o: Offset, s: Float, col: Color) {
    val p = Path().apply {
        moveTo(o.x - s * 0.95f, o.y - s * 0.55f)
        cubicTo(o.x - s * 0.95f, o.y + s * 0.4f, o.x - s * 0.4f, o.y + s * 0.95f, o.x + s * 0.55f, o.y + s * 0.95f)
        lineTo(o.x + s * 0.95f, o.y + s * 0.55f); lineTo(o.x + s * 0.45f, o.y + s * 0.15f); lineTo(o.x + s * 0.1f, o.y + s * 0.45f)
        cubicTo(o.x - s * 0.2f, o.y + s * 0.3f, o.x - s * 0.3f, o.y + s * 0.2f, o.x - s * 0.45f, o.y - s * 0.1f)
        lineTo(o.x - s * 0.15f, o.y - s * 0.45f); lineTo(o.x - s * 0.55f, o.y - s * 0.95f); close()
    }
    drawPath(p, col)
}

private fun DrawScope.arrow(o: Offset, s: Float, left: Boolean) {
    val k = if (left) -1f else 1f
    val p = Path().apply { moveTo(o.x + k * s, o.y); lineTo(o.x - k * s * 0.6f, o.y - s); lineTo(o.x - k * s * 0.6f, o.y + s); close() }
    drawPath(p, Color(0xFF9AA1A8))
}

/** Where the M button is, what it does outside a call, and what a press and a hold do, as the owner's manual has it. */
@Composable
fun HandlebarBody(model: app.waylink.core.BikeModel = app.waylink.core.Catalog.MODELS[0]) {
    val c = W.c
    val cx = model.connect
    Spacer(Modifier.height(8.dp))
    HandlebarSample()
    Spacer(Modifier.height(16.dp))
    Row {
        Ic("check", size = 16.dp, tint = c.sageInk)
        Spacer(Modifier.width(10.dp))
        Txt(if (cx != null) "Built in on the ${model.name} from ${cx.since}: the ${cx.button} on the left switch. Nothing to buy or fit."
            else "Bajaj doesn’t list a Bluetooth display for the ${model.name}, so it may not have this button. On Bluetooth Pulsars it’s the Mode (M) button on the left switch.",
            T.row.copy(lineHeight = 20.sp), c.ink2)
    }
    Spacer(Modifier.height(10.dp))
    Row {
        Ic("info", size = 16.dp, tint = c.ink3)
        Spacer(Modifier.width(10.dp))
        Txt("With no call, M just flips the display between ODO and trips, and the phone never hears it. It talks to Waylink only while a call rings or is on.",
            T.row.copy(lineHeight = 20.sp), c.ink3)
    }
    Spacer(Modifier.height(18.dp))
    ListCard(flat = true) {
        ListRow("Press, under 1 second", "Answers a ringing call", icon = "hand", chevron = false, compact = true)
        Hairline(66.dp)
        ListRow("Hold, over 3 seconds", "Declines or ends the call", icon = "hand", chevron = false, compact = true)
    }
}
