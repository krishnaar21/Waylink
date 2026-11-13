package app.waylink.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waylink.ui.theme.Body
import app.waylink.ui.theme.W
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOOP_PATH = "M2.5 44C3 64 12 80 25 80C37 80 42 64 46 50C48.8 40.5 54.5 31 54 22C53.6 13.5 39 12.5 38.6 22C38.2 31 43.4 40.5 46.8 50C51 64 58 80 71 80C84 80 90 56 91.5 26L91.5 14"

/**
 * The web's "loop" wordmark (.wl-loop): an accent W drawn as one road, then "aylink" typed in.
 * With [animate] it plays the splash version: the W draws in 0.8 s, then each letter rises in (tyRise).
 * [key] restarts the animation when it changes (the logo replay button).
 */
@Composable
fun LoopWordmark(fontSize: Float = 38f, modifier: Modifier = Modifier, animate: Boolean = false, key: Any = Unit) {
    val c = W.c
    val path = remember { PathParser().parsePathString(LOOP_PATH).toPath() }
    val draw = remember(key) { Animatable(if (animate) 0f else 1f) }
    val letters = "aylink"
    val rise = remember(key) { List(letters.length) { Animatable(if (animate) 0f else 1f) } }
    if (animate) LaunchedEffect(key) {
        launch { draw.animateTo(1f, tween(800, 100, CubicBezierEasing(.6f, 0f, .3f, 1f))) }
        rise.forEachIndexed { i, a -> launch { a.animateTo(1f, tween(550, 620 + i * 100, LinearEasing)) } }
    }
    val style = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = fontSize.sp, letterSpacing = 0.em, lineHeight = fontSize.sp)
    val overshoot = CubicBezierEasing(.2f, 1.3f, .4f, 1f)
    Row(modifier.semantics { contentDescription = "Waylink" }, verticalAlignment = Alignment.Bottom) {
        Canvas(Modifier.size((fontSize * 0.98f).sp.toDp(), fontSize.sp.toDp()).offset(y = (fontSize * 0.2f).sp.toDp())) {
            val s = size.height / 100f
            val p = draw.value
            val drawn = if (p >= 1f) path else Path().also { out -> PathMeasure().apply { setPath(path, false) }.let { it.getSegment(0f, it.length * p, out, true) } }
            if (p > 0f) scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(drawn, c.accent, style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Spacer(Modifier.width((fontSize * 0.035f).sp.toDp()))
        letters.forEachIndexed { i, ch ->
            val t = rise[i].value
            val e = overshoot.transform(t)
            Txt(
                ch.toString(), style, c.ink,
                Modifier.graphicsLayer {
                    // tyRise: from .45em lower, squashed to (.85, .6) and transparent; opaque by 45 %.
                    alpha = (t / 0.45f).coerceIn(0f, 1f)
                    translationY = (1f - e) * 0.45f * fontSize * density * fontScale
                    scaleX = 0.85f + 0.15f * e; scaleY = 0.6f + 0.4f * e
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            )
        }
    }
}

/** The splash (#splash): the page colour with the wordmark drawing in. Tap to close. */
@Composable
fun Splash(replay: Any, onClose: () -> Unit) {
    val c = W.c
    Box(
        Modifier.fillMaxSize().background(c.bg).grain().clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Close", onClick = onClose),
        contentAlignment = Alignment.Center,
    ) { LoopWordmark(38f, animate = true, key = replay) }
}

private val TURN_SIGNS = listOf("turnRight" to Color(0xFFF28A1E), "turnLeft" to Color(0xFF3E8EF7), "straight" to Color(0xFF2BB673), "turnU" to Color(0xFF7C5CFF), "bearRight" to Color(0xFFE5484D), "pinSimple" to Color(0xFF0EA5A5))

/** The loading screen (.wl-loader): turn signs take turns in a raised disc, with "Loading…" below. */
@Composable
fun LoadingScreen(text: String) {
    val c = W.c
    val inf = rememberInfiniteTransition(label = "loader")
    Box(Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.92f)).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.raised).border(1.dp, c.line, CircleShape), contentAlignment = Alignment.Center) {
                TURN_SIGNS.forEachIndexed { i, (name, _) ->
                    // ldSwap: 3 s loop, each sign 0.5 s after the last; in from below, hold, out upwards.
                    val ph = if (app.waylink.ui.theme.Motion.still) (if (i == 0) 0.1f else 0.5f) else inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart, androidx.compose.animation.core.StartOffset(i * 500)), label = "ld$i").value
                    val (a, y, k) = when {
                        ph < 0.06f -> { val f = CubicBezierEasing(.4f, 0f, .2f, 1f).transform(ph / 0.06f); Triple(f, 10f * (1 - f), 0.85f + 0.15f * f) }
                        ph < 0.15f -> Triple(1f, 0f, 1f)
                        ph < 0.21f -> { val f = CubicBezierEasing(.4f, 0f, .2f, 1f).transform((ph - 0.15f) / 0.06f); Triple(1 - f, -10f * f, 1f - 0.15f * f) }
                        else -> Triple(0f, -10f, 0.85f)
                    }
                    Ic(name, Modifier.graphicsLayer { alpha = a; translationY = y * density; scaleX = k; scaleY = k }, size = 30.dp, tint = c.ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                val ts = TextStyle(fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.005).em, lineHeight = 22.5.sp)
                Txt(text, ts, c.ink2)
                repeat(3) { i ->
                    val o = if (app.waylink.ui.theme.Motion.still) 1f else inf.animateFloat(0.2f, 1f, infiniteRepeatable(keyframes { durationMillis = 1200; 0.2f at 0; 1f at 480; 0.2f at 1200 }, RepeatMode.Restart, androidx.compose.animation.core.StartOffset(i * 200)), label = "dot$i").value
                    Txt(".", ts, c.ink2, Modifier.alpha(o))
                }
            }
        }
    }
}

/**
 * The "turns" wordmark (.wl-turns) from the About footer: "way", a slot where the turn signs take turns
 * in their own colours (tgCycle, 4.8 s), and "link".
 */
@Composable
fun TurnsWordmark(fontSize: Float = 24f, modifier: Modifier = Modifier) {
    val c = W.c
    val inf = rememberInfiniteTransition(label = "turns")
    val style = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = fontSize.sp, letterSpacing = (-0.035).em, lineHeight = fontSize.sp)
    Row(modifier.semantics { contentDescription = "waylink" }, verticalAlignment = Alignment.CenterVertically) {
        Txt("way", style, c.ink)
        Box(Modifier.size((fontSize * 0.64f).sp.toDp()).offset(y = (fontSize * 0.03f).sp.toDp()), contentAlignment = Alignment.Center) {
            TURN_SIGNS.forEachIndexed { i, (name, col) ->
                val ph = if (app.waylink.ui.theme.Motion.still) (if (i == 0) 0.1f else 0.5f) else inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart, androidx.compose.animation.core.StartOffset(i * 800)), label = "tg$i").value
                // tgCycle: pop in over 2.5 % with a springy overshoot, hold to 16 %, fade out by 18.5 %.
                val (a, k) = when {
                    ph < 0.025f -> { val f = CubicBezierEasing(.3f, 1.5f, .5f, 1f).transform(ph / 0.025f); f.coerceIn(0f, 1f) to (0.4f + 0.6f * f) }
                    ph < 0.16f -> 1f to 1f
                    ph < 0.185f -> { val f = (ph - 0.16f) / 0.025f; (1 - f) to (1f - 0.6f * f) }
                    else -> 0f to 0.4f
                }
                Ic(name, Modifier.graphicsLayer { alpha = a; scaleX = k; scaleY = k }, size = (fontSize * 0.64f).sp.toDp(), tint = col)
            }
        }
        Spacer(Modifier.width((fontSize * 0.14f).sp.toDp()))
        Txt("link", style, c.ink)
    }
}

@Composable
private fun androidx.compose.ui.unit.TextUnit.toDp() = with(androidx.compose.ui.platform.LocalDensity.current) { this@toDp.toDp() }

/** Keeps a timed overlay up for [ms], then calls [done]. */
@Composable
fun AutoClose(key: Any, ms: Long, done: () -> Unit) { LaunchedEffect(key) { delay(ms); done() } }

/** .preview-bar: shown while a developer preview state is on, with a way out. */
@Composable
fun PreviewBar(kind: String, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val c = W.c
    Row(
        modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(c.tintAmber)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic("flask", size = 17.dp, tint = c.amber)
        Spacer(Modifier.width(10.dp))
        Txt("Previewing ${if (kind == "empty") "the empty state" else "edge cases"}. Nothing is saved.", app.waylink.ui.theme.T.row, c.ink, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Chip("Exit", onExit)
    }
}
