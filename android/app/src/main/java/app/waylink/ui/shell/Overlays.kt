package app.waylink.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import app.waylink.ui.components.card
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

/**
 * Ride mode, as the web app's rideMode() / layoutA(): the segmented speed dial with the trip under it, status tags,
 * then four boxes (time, engine or phone, fuel, trip or this ride), a link to the auto-open setting and the hold ring.
 */
@Composable
fun RideMode(s: AppState, live: Live, onSettings: () -> Unit = {}, onLeave: () -> Unit) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val v = s.vehicle
    val fs = remember(v) { fuelStats(v) }
    val max = maxKmh(s.model.engine)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    BackHandler { }
    val rec = live.rec
    val trip = if (rec != null) f.num(rec.km, 1) else f.num(v.tripA.km, 1)
    Box(Modifier.fillMaxSize().background(c.bg).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 42.dp, bottom = 156.dp), // .rm-page: --top + 2, 24, --bottom + 168
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // .speed: the dial, centred in the room left above the boxes, tags 18 below it
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                SpeedDial(live.speed.toFloat(), max, "$trip km", f.num(live.speed))
                val linked = live.link == LinkState.CONNECTED
                if (rec != null || !linked || live.gpsErr) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (rec != null) Tag("Recording", c.accent.copy(alpha = 0.14f), c.accent, dot = true)
                        if (!linked) Tag("Bike not linked", c.inkA(0.07f), c.ink2)
                        if (live.gpsErr) Tag("No GPS", c.tintAmber, c.amber)
                    }
                }
            }
            val n = live.nav
            if (n != null) {
                // .rm-nav2: arrow 54 × 66, distance at 40, the instruction under it
                val (dv, du) = distText(n.stepMeters, f)
                Row(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TurnArrow(Maneuvers.of(n.maneuver).glyph, Modifier.size(54.dp, 66.dp), exit = n.exit)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        RmVal(dv, du, 40f)
                        Spacer(Modifier.height(4.dp))
                        Txt(n.say, T.rowSub, c.ink3, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            val t = LocalDateTime.now()
            val time = "%02d:%02d".format(t.hour, t.minute).also { now.hashCode() }
            val date = "${t.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, ${t.dayOfMonth} ${app.waylink.core.monShort(t.monthValue)}"
            val eng = live.engine
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RmBox(date, Modifier.weight(1f)) { RmVal(time, null) }
                RmBox(if (eng != null) "Engine" else "Phone", Modifier.weight(1f)) {
                    if (eng != null) RmVal("$eng", "°C") else RmVal(live.battery?.toString() ?: "—", "%")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val range = fs.range
                RmBox("Fuel · " + (if (range != null) "≈${f.num(range)} km" else "—"), Modifier.weight(1f)) { RmBars(fs.bars, fs.onReserve) }
                RmBox(if (rec != null) "This ride" else "Trip A", Modifier.weight(1f)) { RmVal(trip, "km") }
            }
        }
        // .rm-note: an underlined link to the setting, 140 above the bottom
        val note = if (s.settings.autoRide) "Auto-opens · Settings › Riding" else "Auto-open · Settings › Riding"
        Txt(
            note, T.rowSub.copy(fontSize = 12.5.sp, textDecoration = TextDecoration.Underline), c.ink3,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 128.dp)
                .semantics { role = Role.Button }.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onSettings)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 16.dp)) { HoldToLeave(onLeave) }
    }
}

/** .rm-dial: a 240° band cut into 16 segments, its lit part with a glow, an outer rim and needle, 0 and top speed at the ends. */
@Composable
private fun SpeedDial(speed: Float, max: Int, sub: String, big: String) {
    val c = W.c
    val frac = (speed / max.toFloat()).coerceIn(0f, 1f)
    val shown by androidx.compose.animation.core.animateFloatAsState(frac, tween(350), label = "dial")
    val accent = c.accent
    val ink = c.ink
    val bg = c.bg
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val sans = app.waylink.ui.theme.Body
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(300.dp, maxWidth * 0.76f)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 200f
                val ctr = Offset(100f * k, 100f * k)
                fun arc(r: Float) = Pair(Offset(ctr.x - r * k, ctr.y - r * k), Size(2 * r * k, 2 * r * k))
                drawCircle(Brush.radialGradient(0f to accent.copy(alpha = 0.38f), 0.55f to accent.copy(alpha = 0.12f), 1f to accent.copy(alpha = 0f), center = ctr, radius = 66f * k), 66f * k, ctr)
                val (tl, sz) = arc(83f)
                drawArc(Brush.radialGradient(0.66f to accent.copy(alpha = 0.05f), 1f to accent.copy(alpha = 0.55f), center = ctr, radius = 100f * k), 150f, 240f, false, tl, sz, style = Stroke(30f * k))
                if (shown > 0f) {
                    drawArc(accent.copy(alpha = 0.22f), 150f, 240f * shown, false, tl, sz, style = Stroke(36f * k)) // drop-shadow glow
                    drawArc(accent, 150f, 240f * shown, false, tl, sz, style = Stroke(30f * k))
                    val (rt, rs) = arc(97f)
                    drawArc(ink.copy(alpha = 0.9f), 150f, 240f * shown, false, rt, rs, style = Stroke(2.2f * k, cap = StrokeCap.Round))
                }
                for (i in 0 until 15) {
                    val a = Math.toRadians(165.0 + i * 15.0)
                    val cs = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
                    drawLine(bg, Offset(ctr.x + cs * 68f * k, ctr.y + sn * 68f * k), Offset(ctr.x + cs * 99f * k, ctr.y + sn * 99f * k), 1.6f * k)
                }
                val na = Math.toRadians(150.0 + 240.0 * shown)
                val nc = kotlin.math.cos(na).toFloat(); val ns = kotlin.math.sin(na).toFloat()
                drawLine(ink, Offset(ctr.x + nc * 83f * k, ctr.y + ns * 83f * k), Offset(ctr.x + nc * 97f * k, ctr.y + ns * 97f * k), 2.6f * k, StrokeCap.Round)
                // SVG text sits on its baseline: x, y in the 200 box
                fun label(t: String, x: Float, y: Float, px: Float, w: FontWeight, col: Color, anchor: Int) {
                    val r = tm.measure(t, androidx.compose.ui.text.TextStyle(fontFamily = sans, fontWeight = w, fontSize = (px * k).toSp()))
                    val left = when (anchor) { 0 -> x * k; 1 -> x * k - r.size.width / 2f; else -> x * k - r.size.width }
                    drawText(r, col, Offset(left, y * k - r.firstBaseline))
                }
                label("km/h", 100f, 62f, 9f, FontWeight.SemiBold, c.ink2, 1)
                label("0", 44f, 146f, 11f, FontWeight.Bold, ink, 0)
                label("$max", 156f, 146f, 11f, FontWeight.Bold, ink, 2)
                label(sub, 100f, 146f, 9.5f, FontWeight.Medium, c.ink3, 1)
            }
            val px = minOf(84.dp, maxWidth * 0.21f)
            val sp = with(androidx.compose.ui.platform.LocalDensity.current) { px.toSp() }
            Txt(big, T.big(sp.value).copy(letterSpacing = (-0.04).em, fontFeatureSettings = "tnum"), c.ink, Modifier.graphicsLayer { translationY = size.height * 0.02f }, maxLines = 1)
        }
    }
}

/** .tag: 13 semibold, 6 × 10 padding, radius 8; Recording carries a blinking dot. */
@Composable
private fun Tag(text: String, bg: Color, fg: Color, dot: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            val blink = androidx.compose.animation.core.rememberInfiniteTransition(label = "blink")
            val a by blink.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
            Box(Modifier.size(7.dp).graphicsLayer { alpha = if (app.waylink.ui.theme.Motion.still) 1f else a }.clip(CircleShape).background(fg))
            Spacer(Modifier.width(6.dp))
        }
        Txt(text, T.rowSub.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), fg, maxLines = 1)
    }
}

/** .rm-box: card, radius 20, padding 16 × 18; the label in the body face at 14. */
@Composable
private fun RmBox(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    val c = W.c
    Column(modifier.fillMaxHeight().card(20.dp).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Txt(label, T.rowSub.copy(fontSize = 14.sp, lineHeight = 18.sp), c.ink3, maxLines = 1)
        content()
    }
}

/** .rm-val: the title face at 30, tabular, 8 below the label; the unit small in the body face. */
@Composable
private fun RmVal(v: String, unit: String?, size: Float = 30f) {
    val c = W.c
    Row(Modifier.padding(top = if (size == 30f) 8.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
        Txt(v, T.big(size).copy(fontFeatureSettings = "tnum"), c.ink, maxLines = 1)
        if (unit != null) Txt(unit, T.rowSub.copy(fontSize = 12.sp, lineHeight = 14.sp), c.ink3, Modifier.padding(start = 3.dp, bottom = 1.dp), maxLines = 1)
    }
}

/** .rm-fuelbars: six bars 8 wide, 3 apart, 22 tall, rising from 40 %; red on reserve. */
@Composable
private fun RmBars(bars: Int?, low: Boolean) {
    val c = W.c
    Row(Modifier.padding(top = 10.dp).height(22.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 6) {
            val on = bars != null && i < bars
            Box(Modifier.width(8.dp).fillMaxHeight(0.40f + i * 0.12f).clip(RoundedCornerShape(2.dp)).background(if (on) (if (low) c.red else c.ink) else c.segOff))
        }
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
        val off = c.segOff
        val dot = c.ink3
        Canvas(Modifier.size(52.dp)) {
            // viewBox 26: ring r 11, stroke 2; centre dot r 4
            val k = size.width / 26f
            val tl = Offset(2f * k, 2f * k); val sz = Size(22f * k, 22f * k)
            drawCircle(off, radius = 11f * k, style = Stroke(2f * k))
            drawArc(ink, -90f, 360f * p.value, false, tl, sz, style = Stroke(2f * k))
            drawCircle(dot, radius = 4f * k)
        }
        Spacer(Modifier.height(8.dp))
        Txt("Hold to leave", T.rowSub.copy(fontSize = 13.5.sp), c.ink3)
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
