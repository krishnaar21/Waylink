package app.waylink.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.waylink.ui.theme.W
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

private const val TRIGGER = 72f
private const val HOLD = 58f

/** The tyre's tread blocks (web PTR_MARK), in a 100 × 100 box. */
private val TREAD = "83.5 50.0 91.9 57.2,82.1 59.4 88.2 68.7,78.2 68.1 81.4 78.7,71.9 75.3 72.0 86.4,63.9 80.5 60.9 91.1,54.8 83.2 48.8 92.5,45.2 83.2 36.9 90.4,36.1 80.5 26.1 85.1,28.1 75.3 17.1 76.9,21.8 68.1 10.9 66.6,17.9 59.4 7.8 54.9,16.5 50.0 8.1 42.8,17.9 40.6 11.8 31.3,21.8 31.9 18.6 21.3,28.1 24.7 28.0 13.6,36.1 19.5 39.1 8.9,45.2 16.8 51.2 7.5,54.8 16.8 63.1 9.6,63.9 19.5 73.9 14.9,71.9 24.7 82.9 23.1,78.2 31.9 89.1 33.4,82.1 40.6 92.2 45.1"
    .split(',').map { l -> l.trim().split(' ').map { it.toFloat() } }

/**
 * Pull down at the top of a tab: a motorcycle wheel draws its tyre and rolls as you pull, then spins while
 * [onRefresh] runs (at least .9 s), like the web app's #ptr. The page follows the finger with a rubber-band feel.
 */
@Composable
fun PullToRefresh(enabled: Boolean, onRefresh: suspend () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    var raw by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val shown = remember { Animatable(0f) } // dp the page sits down by
    val spin = remember { Animatable(0f) }
    val pull = { r: Float -> 120f * (1f - exp(-max(0f, r) / 190f)) }

    val conn = remember(enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushing back up while pulled: shrink the pull first.
                if (busy || raw <= 0f || available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y / density
                val used = max(dy, -raw)
                raw += used
                scope.launch { shown.snapTo(pull(raw)) }
                return Offset(0f, used * density)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || busy || available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                raw += available.y / density
                val d = pull(raw)
                if (d >= TRIGGER && !armed) { armed = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } else if (d < TRIGGER) armed = false
                scope.launch { shown.snapTo(d) }
                return available
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (raw <= 0f || busy) return Velocity.Zero
                val d = pull(raw)
                raw = 0f; armed = false
                val back = tween<Float>(420, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1.15f))
                if (d < TRIGGER) { scope.launch { shown.animateTo(0f, back) }; return available }
                busy = true
                scope.launch {
                    shown.animateTo(HOLD, back)
                }
                scope.launch {
                    val t0 = System.currentTimeMillis()
                    val spinner = launch { spin.snapTo(0f); spin.animateTo(1f, infiniteRepeatable(tween(700, easing = LinearEasing))) }
                    try { onRefresh() } catch (_: Exception) { }
                    delay(max(0L, 900 - (System.currentTimeMillis() - t0)))
                    spinner.cancel()
                    shown.animateTo(0f, back)
                    busy = false
                }
                return available
            }
        }
    }

    Box(Modifier.fillMaxSize().nestedScroll(conn)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = shown.value * density }, content = content)
        val d = shown.value
        if (d > 0.5f || busy) {
            val p = min(1f, d / TRIGGER)
            val draw = if (busy) 1f else p // how much of the tyre is drawn, 0..1
            val roll = d * 3f + spin.value * 360f
            val ink = W.c.ink
            Canvas(
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).size(46.dp)
                    .graphicsLayer { translationY = (d * 0.55f - 34f) * density; alpha = if (d > 0f) min(1f, p * 1.4f) else 0f },
            ) {
                val k = size.width / 100f
                scale(k, k, pivot = Offset.Zero) {
                    rotate(roll, pivot = Offset(50f, 50f)) {
                        drawCircle(ink.copy(alpha = 0.22f * draw), 38f, Offset(50f, 50f), style = Stroke(12f))
                        drawArc(ink, -90f, 360f * draw, false, Offset(5.5f, 5.5f), Size(89f, 89f), style = Stroke(4.5f))
                        drawCircle(ink, 31.5f, Offset(50f, 50f), style = Stroke(3.5f))
                        TREAD.forEach { t -> drawLine(ink.copy(alpha = draw), Offset(t[0], t[1]), Offset(t[2], t[3]), 3.6f, StrokeCap.Round) }
                    }
                }
            }
        }
    }
}
