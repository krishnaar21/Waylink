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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Txt
import app.waylink.ui.theme.Motion
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * A drawing of the left handlebar: the grip, the switch cube, and the button the bike's Bluetooth display uses,
 * pulsing so it's easy to spot. An illustration, not any one model's exact layout.
 */
@Composable
fun HandlebarSample(modifier: Modifier = Modifier) {
    val c = W.c
    val pulse = if (Motion.still) 0.5f else {
        val t = rememberInfiniteTransition(label = "button")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulse")
        v
    }
    val accent = c.accent
    Box(modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).background(c.inkA(0.04f))
        .semantics { contentDescription = "Drawing of the left handlebar with the display button highlighted" }) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val w = size.width; val h = size.height
            val cy = h * 0.56f
            val bar = 18.dp.toPx()
            // The bar, from the middle of the bike out to the left end.
            drawRoundRect(Color(0xFF9AA1A8), Offset(w * 0.34f, cy - bar / 2), Size(w * 0.66f, bar), CornerRadius(bar / 2))
            // The rubber grip with ridges, on the left.
            val gx = w * 0.04f; val gw = w * 0.30f; val gh = 30.dp.toPx()
            drawRoundRect(Color(0xFF2A2D31), Offset(gx, cy - gh / 2), Size(gw, gh), CornerRadius(gh / 2))
            var x = gx + 14.dp.toPx()
            while (x < gx + gw - 10.dp.toPx()) { drawLine(Color(0xFF3A3E43), Offset(x, cy - gh / 2 + 4.dp.toPx()), Offset(x, cy + gh / 2 - 4.dp.toPx()), 2.dp.toPx()); x += 7.dp.toPx() }
            // The switch cube next to the grip.
            val bx = gx + gw + 4.dp.toPx(); val bw = 92.dp.toPx(); val bh = 96.dp.toPx()
            drawRoundRect(Color(0xFF1E2124), Offset(bx, cy - bh / 2), Size(bw, bh), CornerRadius(16.dp.toPx()))
            drawRoundRect(Color(0xFF34383D), Offset(bx, cy - bh / 2), Size(bw, bh), CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            // Horn and indicator switches, in grey.
            drawRoundRect(Color(0xFF4A4F55), Offset(bx + 14.dp.toPx(), cy + 14.dp.toPx()), Size(44.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()))
            drawRoundRect(Color(0xFF4A4F55), Offset(bx + 24.dp.toPx(), cy - 6.dp.toPx()), Size(24.dp.toPx(), 12.dp.toPx()), CornerRadius(6.dp.toPx()))
            // The display button, in the accent, with a ring that grows and fades.
            val b = Offset(bx + bw - 22.dp.toPx(), cy - bh / 2 + 24.dp.toPx())
            val r = 11.dp.toPx()
            drawCircle(accent.copy(alpha = 0.35f * (1f - pulse)), r + 14.dp.toPx() * pulse, b)
            drawCircle(accent, r, b)
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.32f, b)
            // A leader line out to the label.
            drawLine(accent, Offset(b.x + r, b.y), Offset(w * 0.80f, h * 0.18f), 1.5.dp.toPx())
        }
        Column(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp), horizontalAlignment = Alignment.End) {
            Txt("Display button", T.rowSub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), c.ink)
            Txt("Often M, SET or a phone icon", T.rowSub.copy(fontSize = 11.sp, lineHeight = 14.sp), c.ink3)
        }
        Txt("Left handlebar · illustration", T.rowSub.copy(fontSize = 11.sp, lineHeight = 14.sp), c.ink3, Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

/** Where the handlebar button is, whether it's built in, and what a tap and a long press do. */
@Composable
fun HandlebarBody() {
    val c = W.c
    Spacer(Modifier.height(8.dp))
    HandlebarSample()
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.Top) {
        Ic("check", size = 16.dp, tint = c.sageInk)
        Spacer(Modifier.width(10.dp))
        Txt("Built in. It’s the button on the left switch cube that your bike’s Bluetooth display already uses. Nothing to buy or fit. Bikes without the Bluetooth display don’t have it.",
            T.row.copy(lineHeight = 20.sp), c.ink2)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.Top) {
        Ic("info", size = 16.dp, tint = c.ink3)
        Spacer(Modifier.width(10.dp))
        Txt("Which button differs by model. Your owner’s manual shows it under the Bluetooth or connectivity section.", T.row.copy(lineHeight = 20.sp), c.ink3)
    }
    Spacer(Modifier.height(18.dp))
    ListCard(flat = true) {
        ListRow("Tap", "Answer a call, or play and pause music", icon = "hand", chevron = false, compact = true)
        Hairline(66.dp)
        ListRow("Long press", "Decline or end a call, or next track", icon = "hand", chevron = false, compact = true)
    }
}
