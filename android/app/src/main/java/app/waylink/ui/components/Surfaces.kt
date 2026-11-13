package app.waylink.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.waylink.R
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlin.math.roundToInt

/**
 * Paper grain over the whole app, like the web app's body::after overlay: a 160 px noise tile,
 * as is on Paper, inverted at 45% on Paper dark.
 */
@Composable
fun Modifier.grain(): Modifier {
    val c = W.c
    val density = LocalDensity.current
    val src = grainBitmap()
    val tile = remember(src, density) {
        val px = with(density) { 160.dp.roundToPx() }
        Bitmap.createScaledBitmap(src.asAndroidBitmap(), px, px, true).asImageBitmap()
    }
    val brush = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    val invert = remember {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
    }
    return drawWithContent {
        drawContent()
        if (c.dark) drawRect(brush, alpha = 0.45f, colorFilter = invert) else drawRect(brush)
    }
}

@Composable
private fun grainBitmap() = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.grain)

/**
 * A bottom sheet in the web app's style: scrim, 28 dp top corners, grab handle, title row with a round close.
 * Drag the handle or title down, tap the scrim, or press back to close.
 */
@Composable
fun Sheet(
    visible: Boolean,
    onClose: () -> Unit,
    title: String,
    sub: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = W.c
    AnimatedVisibility(visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
        Box(
            Modifier.fillMaxSize().background(c.backdrop)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .semantics { contentDescription = "Close" },
        )
    }
    if (visible) BackHandler(onBack = onBack ?: onClose)
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            // sheetUp .5s var(--spring); sheetDown .3s cubic-bezier(.4,0,1,1)
            enter = slideInVertically(tween(500, easing = app.waylink.ui.theme.Spring)) { it },
            exit = slideOutVertically(tween(300, easing = app.waylink.ui.theme.EaseIn)) { it },
        ) {
            var drag by remember { mutableFloatStateOf(0f) }
            val dragState = rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) }
            val density = LocalDensity.current
            Column(
                Modifier
                    .offset { IntOffset(0, drag.roundToInt()) }
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color(0x59000000), spotColor = Color(0x59000000))
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(c.sheet)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Column(
                    Modifier.fillMaxWidth().draggable(
                        dragState, Orientation.Vertical,
                        onDragStopped = { if (drag > with(density) { 120.dp.toPx() }) onClose(); drag = 0f },
                    ),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(c.inkA(0.18f)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 16.dp else 24.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            CircleBtn("back", "Back", onBack, size = 36.dp, flat = true)
                            Spacer(Modifier.width(8.dp))
                        }
                        Txt(title, T.sheetTitle, c.ink, Modifier.weight(1f).padding(top = 6.dp))
                        CircleBtn("x", "Close", onClose, size = 36.dp, flat = true)
                    }
                    if (sub != null) Txt(sub, T.lead.copy(fontSize = T.eyebrow.fontSize), c.ink3, Modifier.padding(start = 24.dp, top = 10.dp))
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    content = content,
                )
            }
        }
    }
}

/** The one Waylink bike picture, or the rider's own photo later. Sits on --bg with a soft ground shadow. */
@Composable
fun BikeArt(modifier: Modifier, radius: androidx.compose.ui.unit.Dp) {
    val c = W.c
    Box(modifier.clip(RoundedCornerShape(radius)).background(c.bg), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(0.64f).height(6.dp).align(Alignment.BottomCenter).offset(y = (-8).dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.Black.copy(alpha = if (c.dark) 0.35f else 0.22f), Color.Transparent)),
                    RoundedCornerShape(50),
                ),
        )
        Image(
            painterResource(R.drawable.rider_street), null,
            Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
        )
    }
}

private val AVATARS = listOf(
    R.drawable.avatar_a1, R.drawable.avatar_a2, R.drawable.avatar_a3, R.drawable.avatar_a4, R.drawable.avatar_a5, R.drawable.avatar_a6,
    R.drawable.avatar_a7, R.drawable.avatar_a8, R.drawable.avatar_a9, R.drawable.avatar_a10, R.drawable.avatar_a11,
)

/** a1..a11 → drawable. */
fun avatarRes(key: String): Int = AVATARS[((key.removePrefix("a").toIntOrNull() ?: 1) - 1).coerceIn(0, AVATARS.lastIndex)]

@Composable
fun Avatar(key: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 52.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Color.White)) {
        Image(painterResource(avatarRes(key)), null, Modifier.fillMaxSize())
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun Stack(modifier: Modifier = Modifier, gap: Int = 0, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.dp), content = content)
