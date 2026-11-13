package app.waylink.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/* Building blocks that mirror the web app's shared classes: .card, .btn, .circle, .eyebrow, .big + .unit, .segs. */

/** An icon from the web app's set, by its web name ("wrench", "chev", "bt"…). */
@Composable
fun Ic(name: String, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = W.c.ink2) {
    val id = IconMap.byName[name] ?: return
    Image(painterResource(id), contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Plain text with the app's defaults. */
@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = W.c.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(text, modifier, style.css(color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun Txt(text: AnnotatedString, style: TextStyle, color: Color = W.c.ink, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    BasicText(text, modifier, style.css(color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/**
 * CSS keeps half the line-height above and below every line; Compose trims it off the first and last line by
 * default, which made every row a few dp shorter than the web app's. Keep it, unless a style says otherwise.
 */
private val CssLeading = androidx.compose.ui.text.style.LineHeightStyle(
    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
)
private fun TextStyle.css(color: Color): TextStyle = copy(color = color, lineHeightStyle = lineHeightStyle ?: CssLeading)

/** A big reading with its small unit: "41,520 km". */
@Composable
fun BigValue(value: String, unit: String?, size: Float, color: Color = W.c.ink, modifier: Modifier = Modifier) {
    val unitColor = W.c.ink3
    val s = buildAnnotatedString {
        append(value)
        if (!unit.isNullOrEmpty()) {
            withStyle(SpanStyle(fontFamily = T.unit.fontFamily, fontSize = T.unit.fontSize, letterSpacing = T.unit.letterSpacing, color = unitColor, fontWeight = T.unit.fontWeight)) {
                append(" "); append(unit)
            }
        }
    }
    Txt(s, T.big(size), color, modifier, maxLines = 1)
}

/** The web app's Paper card: card colour, hairline ring, faint glow. */
fun Modifier.paperCard(radius: Dp = 20.dp, color: Color, line: Color, dark: Boolean): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .then(if (dark) Modifier else Modifier.shadow(3.dp, shape, ambientColor = Color(0x403C2D14), spotColor = Color(0x203C2D14)))
        .clip(shape)
        .background(color)
        .border(1.dp, line, shape)
}

@Composable
fun Modifier.card(radius: Dp = 20.dp, color: Color = W.c.card): Modifier = paperCard(radius, color, W.c.line, W.c.dark)

/** .li:active: list rows don't shrink, they tint (background: ink 3 %). */
@Composable
fun Modifier.rowPress(enabled: Boolean = true, label: String? = null, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return this
        .background(if (pressed) W.c.inkA(0.03f) else Color.Transparent)
        .clickable(src, indication = null, enabled = enabled, role = role, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/** Settings and Alerts draw their list icons larger: 44 dp tiles, radius 13, 24 dp glyphs. */
val LocalBigRowIcons = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Press feedback used everywhere instead of ripples: a slight shrink, like the web app's :active scale. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, label: String? = null, role: Role = Role.Button, scale: Float = 0.975f, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // :active { transform: scale(…) } with transition .18s var(--spring)
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed) scale else 1f, androidx.compose.animation.core.tween(180, easing = app.waylink.ui.theme.Spring), label = "press")
    return this
        .scale(k)
        .clickable(src, indication = null, enabled = enabled, role = role, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

enum class BtnKind { INK, LINE, ACCENT, GHOST, DANGER }

/** .btn: 52 tall, 16 radius. Small: 34 tall, 12 radius. */
@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.INK,
    icon: String? = null,
    small: Boolean = false,
) {
    val c = W.c
    val (bg, fg) = when (kind) {
        BtnKind.INK -> c.ink to c.bg
        BtnKind.ACCENT -> c.accent to c.onAccent
        BtnKind.LINE -> c.raised to c.ink
        BtnKind.GHOST -> Color.Transparent to c.ink2
        BtnKind.DANGER -> c.tintRed to c.red
    }
    val shape = RoundedCornerShape(if (small) 12.dp else 16.dp)
    Row(
        modifier
            .height(if (small) 34.dp else 52.dp)
            .then(if (kind == BtnKind.LINE) Modifier.border(1.dp, c.line, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .pressable(onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Ic(icon, size = if (small) 16.dp else 19.dp, tint = fg)
        Txt(text, if (small) T.buttonSm else T.button, fg, maxLines = 1)
    }
}

/** .circle: 42 round, raised, soft shadow. Used for back, SOS and sheet close. */
@Composable
fun CircleBtn(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = W.c.ink, size: Dp = 42.dp, flat: Boolean = false, iconSize: Dp? = null) {
    val c = W.c
    Box(
        modifier
            .size(size)
            .then(if (flat) Modifier else Modifier.shadow(4.dp, CircleShape, ambientColor = Color(0x380F1623), spotColor = Color(0x380F1623)))
            .clip(CircleShape)
            .background(if (flat) c.inkA(0.07f) else c.raised)
            .pressable(label = label, scale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ic(icon, size = iconSize ?: if (flat) 18.dp else 20.dp, tint = tint) }
}

/** Section header: "Readings" on the left, an optional link or tag on the right. */
@Composable
fun SectionHead(title: String, modifier: Modifier = Modifier, help: (() -> Unit)? = null, right: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Txt(title, T.section, W.c.ink, Modifier.weight(1f, fill = false), maxLines = 1)
            if (help != null) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(26.dp).clip(CircleShape).pressable(label = "About $title", onClick = help), contentAlignment = Alignment.Center) {
                    Ic("info", size = 15.dp, tint = W.c.ink4)
                }
            }
        }
        right()
    }
}

@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: TextStyle = T.link, color: Color = W.c.ink2) =
    Txt(text, style, color, modifier.pressable(onClick = onClick).padding(vertical = 4.dp))

/** A status tone shared by service jobs, fuel and alerts. */
enum class Tone { OK, SOON, BAD, NEUTRAL }

/** .segs: a row of short bars. [tones] gives each bar's tone; null means empty. */
@Composable
fun Segs(tones: List<Tone?>, modifier: Modifier = Modifier, height: Dp = 4.dp, gap: Dp = 3.dp) {
    val c = W.c
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
        tones.forEach { t ->
            val m = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp))
            when (t) {
                Tone.BAD -> Box(m.drawBehind { drawRect(stripes(c.red, 6.dp.toPx())) })
                Tone.SOON -> Box(m.background(c.amber))
                Tone.OK -> Box(m.background(c.inkA(0.3f)))
                Tone.NEUTRAL -> Box(m.background(c.inkA(0.24f)))
                null -> Box(m.background(c.segOff))
            }
        }
    }
}

/** repeating-linear-gradient(135deg, red 0 3px, red/55% 3px 6px) */
fun stripes(red: Color, period: Float): Brush = Brush.linearGradient(
    0f to red, 0.5f to red, 0.5f to red.copy(alpha = 0.55f), 1f to red.copy(alpha = 0.55f),
    start = Offset(0f, 0f), end = Offset(period * 0.7071f, period * 0.7071f), tileMode = TileMode.Repeated,
)

/** The fuel gauge as six rising bars, like the cluster. */
@Composable
fun FuelBars(bars: Int?, low: Boolean, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val c = W.c
    Row(modifier.height(height), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 6) {
            val on = bars != null && i < bars
            Box(
                Modifier.width(7.dp).fillMaxHeight(0.45f + i * 0.11f).clip(RoundedCornerShape(2.dp))
                    .background(if (on) (if (low) c.red else c.ink) else c.segOff),
            )
        }
        Spacer(Modifier.width(6.dp))
        Txt(if (bars != null) "$bars/6" else "—", T.rowSub, c.ink3, Modifier.align(Alignment.CenterVertically))
    }
}

/** Parallelogram segments for service progress (the web .fgauge.sm). */
@Composable
fun ServiceGauge(fresh: Int, total: Int = 8, modifier: Modifier = Modifier, height: Dp = 4.dp, onColor: Color? = null) {
    val c = W.c
    val on = onColor ?: c.inkA(0.42f)
    Box(
        modifier.fillMaxWidth().height(height).drawBehind {
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (total - 1)) / total
            for (i in 0 until total) {
                val x = i * (w + gap)
                val l = if (i == 0) 0f else w * 0.22f
                val r = if (i == total - 1) 0f else w * 0.22f
                val p = Path().apply {
                    moveTo(x + l, 0f); lineTo(x + w, 0f); lineTo(x + w - r, size.height); lineTo(x, size.height); close()
                }
                clipPath(p) { drawRect(if (i < fresh) on else c.segOff) }
            }
        },
    )
}

/** A small round status dot. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp) = Box(modifier.size(size).clip(CircleShape).background(color))

/** A rounded tinted square holding an icon (.row-ic, .tile-ic, .cc-ic). */
@Composable
fun IconTile(icon: String, modifier: Modifier = Modifier, size: Dp = 38.dp, radius: Dp = 12.dp, iconSize: Dp = 19.dp, bg: Color = W.c.inkA(0.06f), tint: Color = W.c.ink2, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) { Ic(icon, size = iconSize, tint = tint) }
        content()
    }
}

fun shapeOf(radius: Dp): Shape = RoundedCornerShape(radius)
