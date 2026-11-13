package app.waylink.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.Maneuvers
import app.waylink.core.clock
import app.waylink.core.distText
import app.waylink.core.fuelStats
import app.waylink.data.LinkState
import app.waylink.data.Live
import app.waylink.ui.components.FuelBars
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Txt
import app.waylink.ui.route.TurnArrow
import app.waylink.ui.theme.Serif
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** The dial's top speed: bigger engines read further. Mirrors maxKmh in views.js. */
private fun maxKmh(engine: String): Int {
    val cc = Regex("([\\d.]+)(?=cc)").find(engine)?.value?.toDoubleOrNull() ?: 200.0
    return if (cc >= 350) 180 else if (cc >= 190) 160 else 140
}

/** Ride mode: big speed, the next turn, time, temperature, fuel and the trip. Hold the ring to leave. */
@Composable
fun RideMode(s: AppState, live: Live, onLeave: () -> Unit) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val v = s.vehicle
    val fs = remember(v) { fuelStats(v) }
    val max = maxKmh(s.model.engine)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    BackHandler { }
    val rec = live.rec
    Column(
        Modifier.fillMaxSize().background(c.bg).clickable(remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The dial
        Box(Modifier.fillMaxWidth().widthIn(max = 380.dp).aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
            val frac = (live.speed / max.toFloat()).coerceIn(0f, 1f)
            val accent = c.accent
            val base = c.inkA(0.10f)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.minDimension
                val sw = w * 0.07f
                val inset = sw / 2 + w * 0.08f
                val tl = Offset(inset, inset)
                val sz = Size(w - inset * 2, w - inset * 2)
                drawCircle(accent.copy(alpha = 0.10f), radius = w * 0.33f)
                drawArc(base, 150f, 240f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                if (frac > 0) drawArc(accent, 150f, 240f * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("km/h", T.unit, c.ink3)
                Txt(f.num(live.speed), T.big(96f), c.ink, maxLines = 1)
                Txt(if (rec != null) "${f.num(rec.km, 1)} km" else "${f.num(v.tripA.km, 1)} km", T.row, c.ink3)
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.dp, vertical = 30.dp)) {
                Txt("0", T.rowSub, c.ink4)
                Spacer(Modifier.weight(1f))
                Txt("$max", T.rowSub, c.ink4)
            }
        }
        // Tags
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rec != null) Tag("Recording", c.red)
            if (live.link != LinkState.CONNECTED) Tag("Bike not linked", c.ink3)
            if (live.gpsErr) Tag("No GPS", c.amber)
        }
        Spacer(Modifier.height(14.dp))
        val n = live.nav
        if (n != null) {
            val (dv, du) = distText(n.stepMeters, f)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TurnArrow(Maneuvers.of(n.maneuver).glyph, Modifier.size(48.dp, 58.dp), exit = n.exit)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(dv, T.big(32f), c.ink)
                        Txt(" $du", T.unit, c.ink3, Modifier.padding(bottom = 4.dp))
                    }
                    Txt(n.say, T.row, c.ink2, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        val t = LocalDateTime.now()
        val time = "%02d:%02d".format(t.hour, t.minute).also { now.hashCode() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box2("${t.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, ${t.dayOfMonth} ${app.waylink.core.monShort(t.monthValue)}", Modifier.weight(1f)) { Txt(time, T.big(28f), c.ink) }
            val eng = live.engine
            Box2(if (eng != null) "Engine" else "Phone", Modifier.weight(1f)) {
                Txt(if (eng != null) "$eng°C" else live.battery?.let { "$it%" } ?: "—", T.big(28f), c.ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val range = fs.range
            Box2("Fuel · " + (if (range != null) "≈${f.num(range)} km" else "—"), Modifier.weight(1f)) { FuelBars(fs.bars, fs.onReserve, height = 28.dp) }
            if (rec != null) Box2("This ride", Modifier.weight(1f)) { Txt("${f.num(rec.km, 1)} km · ${clock((now - rec.start) / 1000.0)}", T.big(22f), c.ink, maxLines = 1) }
            else Box2("Trip A", Modifier.weight(1f)) { Txt("${f.num(v.tripA.km, 1)} km", T.big(28f), c.ink, maxLines = 1) }
        }
        Spacer(Modifier.weight(1f))
        HoldToLeave(onLeave)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Txt(text, T.rowSub.copy(fontWeight = FontWeight.Medium), color, Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun Box2(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    val c = W.c
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(c.card).padding(14.dp)) {
        Txt(label, T.eyebrow, c.ink3, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** Press and hold for a second to leave, so a bump on the road doesn't close it. */
@Composable
private fun HoldToLeave(onLeave: () -> Unit) {
    val c = W.c
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.semantics {
            role = Role.Button
            contentDescription = "Leave ride mode"
            onClick { onLeave(); true }
        }.pointerInput(Unit) {
            detectTapGestures(onPress = {
                val j = scope.launch {
                    p.animateTo(1f, tween(1000, easing = LinearEasing))
                    onLeave()
                }
                tryAwaitRelease()
                j.cancel()
                scope.launch { p.animateTo(0f, tween(200)) }
            })
        }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ink = c.ink
        val off = c.inkA(0.15f)
        Canvas(Modifier.size(34.dp)) {
            val sw = 2.5.dp.toPx()
            drawCircle(off, style = Stroke(sw), radius = size.minDimension / 2 - sw)
            drawArc(ink, -90f, 360f * p.value, false, Offset(sw, sw), Size(size.width - sw * 2, size.height - sw * 2), style = Stroke(sw, cap = StrokeCap.Round))
            drawCircle(ink.copy(alpha = 0.5f), radius = size.minDimension * 0.15f)
        }
        Spacer(Modifier.height(6.dp))
        Txt("Hold to leave", T.eyebrow, c.ink3)
    }
}

/** A call on the bike: ringing (answer or decline) or active (end). */
@Composable
fun CallOverlay(live: Live, onAnswer: () -> Unit, onDecline: () -> Unit, onEnd: () -> Unit) {
    val k = live.call ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(k.since) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    BackHandler { if (k.ringing) onDecline() else onEnd() }
    Column(
        Modifier.fillMaxSize().background(Color(0xF2141210)).clickable(remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        Txt(if (k.ringing) "Ringing on your bike" else "On a call", T.eyebrow, Color(0xFF9C968C))
        Spacer(Modifier.height(10.dp))
        Txt(k.name, Serif.copy(fontSize = 38.sp, lineHeight = 44.sp), Color(0xFFF4EFE6), maxLines = 2)
        Spacer(Modifier.height(16.dp))
        Txt(if (k.ringing) "Tap the handlebar button to answer" else clock((now - k.since) / 1000.0), T.plate.copy(fontSize = 13.sp, letterSpacing = 0.sp), Color(0xFF9C968C))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (k.ringing) {
                CallBtn("x", Color(0xFFC2412D), "Decline", onDecline)
                CallBtn("phone", Color(0xFF4E7A4A), "Answer", onAnswer)
            } else CallBtn("x", Color(0xFFC2412D), "End", onEnd)
        }
    }
}

@Composable
private fun CallBtn(icon: String, bg: Color, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClickLabel = label, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Ic(icon, size = 26.dp, tint = Color.White) }
        Spacer(Modifier.height(8.dp))
        Txt(label, T.row, Color(0xFFF4EFE6))
    }
}
