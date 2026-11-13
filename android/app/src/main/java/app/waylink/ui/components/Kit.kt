package app.waylink.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.dFull
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* Shared pieces for the inner pages and sheets: page scaffold, lists, chips, checks, date fields. */

/** A scrolling page under the status bar with the tab bar's room at the bottom. Its [PageHead] sticks, small, once it scrolls. */
@Composable
fun Page(bottom: Dp, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val head = remember { StickyHead() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSticky provides head) {
            Column(
                Modifier.fillMaxSize().imePadding().rise().verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = bottom + 90.dp), // .view: 150 below, less the tab bar
                content = content,
            )
        }
        StickyBar(scroll, head)
    }
}

/** What the stuck header shows: the page's title, its back button, its end action and anything pinned below (tabs). */
class StickyHead {
    var title by mutableStateOf<String?>(null)
    var onBack by mutableStateOf<(() -> Unit)?>(null)
    var end by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
    var below by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalSticky = staticCompositionLocalOf<StickyHead?> { null }

/** Lets a page with its own header fill the stuck header (Today, Alerts). */
@Composable
fun StickyInfo(title: String?, onBack: (() -> Unit)? = null, end: (@Composable RowScope.() -> Unit)? = null, below: (@Composable () -> Unit)? = null) {
    val h = LocalSticky.current ?: return
    SideEffect { h.title = title; h.onBack = onBack; h.end = end; h.below = below }
}

/**
 * .pagehead.stuck: once the page scrolls 6 px, the header sits over it with the page colour behind it, fading out
 * over its last 30 px, the title down to 22 and the subtitle gone. The status bar stays readable over a scrolled page.
 */
@Composable
fun BoxScope.StickyBar(scroll: ScrollState, head: StickyHead) {
    val c = W.c
    val density = LocalDensity.current
    val stuck by remember { derivedStateOf { scroll.value > with(density) { 6.dp.toPx() } } }
    val a by animateFloatAsState(if (stuck) 1f else 0f, tween(250), label = "stuck")
    if (a <= 0f) return
    val bg = c.bg
    Column(
        Modifier.align(Alignment.TopStart).fillMaxWidth().graphicsLayer { alpha = a }
            .drawBehind {
                val fade = 30.dp.toPx()
                drawRect(Brush.verticalGradient(0f to bg, (size.height - fade) / size.height to bg, 1f to bg.copy(alpha = 0f)))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 30.dp + 8.dp),
    ) {
        val back = head.onBack
        val title = head.title
        if (back != null) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn("back", "Back", back)
                Spacer(Modifier.weight(1f))
                head.end?.invoke(this)
            }
            if (title != null) { Spacer(Modifier.height(6.dp)); Txt(title, T.title.copy(fontSize = 22.sp, lineHeight = 26.sp), c.ink, maxLines = 1) }
        } else if (title != null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(title, T.title.copy(fontSize = 22.sp, lineHeight = 26.sp), c.ink, Modifier.weight(1f), maxLines = 1)
                head.end?.invoke(this)
            }
        }
        head.below?.let { Spacer(Modifier.height(8.dp)); it() }
    }
}

/**
 * .stagger > * { animation: rise .55s var(--spring) }: content settles up 10 dp as a page opens.
 * (The web staggers each block by 35 ms; here the page rises as one, which reads the same at this distance.)
 */
@Composable
fun Modifier.rise(): Modifier {
    val p = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(550, easing = app.waylink.ui.theme.Spring)) }
    return this.then(Modifier.graphicsLayer { translationY = (1f - p.value) * 10.dp.toPx() })
}

/** .pagehead: optional back and action circles, then the title and its grey subtitle. */
@Composable
fun PageHead(title: String, sub: String?, onBack: (() -> Unit)? = null, action: (@Composable RowScope.() -> Unit)? = null) {
    val c = W.c
    StickyInfo(title, onBack, action)
    if (onBack != null || action != null) {
        // .topbar: 44 tall from the page top (24), 14 below it
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) CircleBtn("back", "Back", onBack)
            Spacer(Modifier.weight(1f))
            action?.invoke(this)
        }
    } else Spacer(Modifier.height(7.dp)) // .title margin 6; Geist's tall line box takes the other 3
    Txt(title, T.title, c.ink, Modifier.semantics { heading() })
    if (sub != null) {
        Spacer(Modifier.height(3.dp))
        Txt(sub, T.subtitle, c.ink3, maxLines = 1)
    }
    Spacer(Modifier.height(28.dp)) // padding 14 + margin 18, less what the taller title lines already add
}

/** Space between page sections (.section margin-top: 30px). */
@Composable
fun SectionGap() = Spacer(Modifier.height(30.dp))

/** A hairline divider, inset from the left like .row + .row::before. */
@Composable
fun Hairline(start: Dp = 0.dp) = Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(W.c.line))

/** .list / .rows: a card whose children are separated by inset hairlines. */
@Composable
fun ListCard(modifier: Modifier = Modifier, flat: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = W.c
    Column(modifier.fillMaxWidth().then(if (flat) Modifier.clip(RoundedCornerShape(20.dp)).background(c.raised) else Modifier.card(20.dp)), content = content)
}

/** .li: icon tile, title and description, and an end value with a chevron. */
@Composable
fun ListRow(
    title: String,
    desc: String? = null,
    icon: String? = null,
    end: String? = null,
    danger: Boolean = false,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = W.c
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.rowPress(label = title, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val big = LocalBigRowIcons.current
            IconTile(
                icon, size = if (big) 44.dp else 36.dp, radius = if (big) 13.dp else 10.dp, iconSize = if (big) 24.dp else 19.dp,
                bg = if (danger) c.tintRed else c.bg, tint = if (danger) c.red else c.ink2,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(title, T.body.copy(lineHeight = 22.4.sp), if (danger) c.red else c.ink, maxLines = 2)
            if (desc != null) Txt(desc, T.row.copy(lineHeight = 20.3.sp), c.ink3, Modifier.padding(top = 3.dp), maxLines = 2)
        }
        if (end != null) Txt(end, T.row.copy(fontSize = 15.sp), c.ink3, Modifier.padding(start = 8.dp), maxLines = 1)
        trailing?.invoke()
        if (onClick != null && chevron) {
            Spacer(Modifier.width(6.dp))
            Ic("chev", size = 15.dp, tint = c.ink3)
        }
    }
}

/** .chip: a 38 tall pill. [on] fills it with ink. */
@Composable
fun Chip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, on: Boolean = false) {
    val c = W.c
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.height(38.dp).clip(shape).background(if (on) c.ink else c.raised)
            .then(if (on) Modifier else Modifier.border(1.dp, c.line, shape))
            .pressable(label = text, scale = 0.95f, onClick = onClick).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Ic(icon, size = 14.dp, tint = if (on) c.bg else c.ink)
        Txt(text, T.row.copy(fontSize = 14.5.sp), if (on) c.bg else c.ink, maxLines = 1)
    }
}

/** .opill: the small red "Overdue" tag. */
@Composable
fun Opill(text: String = "Overdue") {
    val c = W.c
    Txt(text, T.chip.copy(fontSize = 11.5.sp, lineHeight = 17.sp), c.red, Modifier.clip(RoundedCornerShape(999.dp)).background(c.tintRed).padding(horizontal = 7.dp, vertical = 1.dp))
}

/** .check: a round tick box. */
@Composable
fun CheckCircle(on: Boolean, label: String, onClick: () -> Unit) {
    val c = W.c
    Box(
        Modifier.size(24.dp).clip(CircleShape).then(if (on) Modifier.background(c.ink) else Modifier.border(1.5.dp, c.ink4, CircleShape))
            .semantics { stateDescription = if (on) "Done" else "Not done" }
            .pressable(label = label, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (on) Ic("check", size = 13.dp, tint = c.bg) }
}

/** .switch: 46 × 28 track with a sliding knob. */
@Composable
fun Switch(on: Boolean, label: String, onToggle: () -> Unit, enabled: Boolean = true) {
    // .switch: 50 × 30 at 95 %, radius 10; a white 24 dp thumb with radius 8 slides 20 dp.
    // Off, the thumb carries a small grey ring; on, a short accent bar.
    val c = W.c
    val x by androidx.compose.animation.core.animateDpAsState(if (on) 20.dp else 0.dp, androidx.compose.animation.core.tween(320, easing = app.waylink.ui.theme.Spring), label = "sw")
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.accent else c.segOff, androidx.compose.animation.core.tween(250), label = "swbg")
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.scale(0.95f).width(50.dp).height(30.dp).clip(shape).background(bg)
            .semantics { stateDescription = if (on) "On" else "Off" }
            .pressable(enabled = enabled, label = label, role = Role.Switch, onClick = onToggle)
            .then(if (enabled) Modifier else Modifier.alpha(0.4f)),
    ) {
        Box(
            Modifier.offset(x = 3.dp + x, y = 3.dp).size(24.dp)
                .shadow(1.5.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.width(2.5.dp).height(9.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
            else Box(Modifier.size(8.dp).border(2.dp, Color(0xFFA29C92), CircleShape))
        }
    }
}

/** Two fields side by side (.two). */
@Composable
fun Two(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

/** A labelled field with the web's spacing (.field margin-top: 18px). */
@Composable
fun FormField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Field(label, modifier.padding(top = 18.dp), content)

/** .hint: small grey help under a field. */
@Composable
fun Hint(text: String) = Txt(text, T.row.copy(fontSize = 13.5.sp, lineHeight = 21.sp), W.c.ink3, Modifier.padding(top = 10.dp))

/** A date input: looks like the other inputs and opens the system date picker. */
@Composable
fun DateField(value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier, label: String = "Date", max: Long? = null) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    Select(dFull(value), {
        val d = Instant.ofEpochMilli(value).atZone(zone).toLocalDate()
        DatePickerDialog(ctx, { _, y, m, day ->
            // Keep the time of day; only the date changes.
            val t = Instant.ofEpochMilli(value).atZone(zone).toLocalTime()
            onChange(LocalDate.of(y, m + 1, day).atTime(t).atZone(zone).toInstant().toEpochMilli())
        }, d.year, d.monthValue - 1, d.dayOfMonth).apply { if (max != null) datePicker.maxDate = max }.show()
    }, modifier, label = "$label, ${dFull(value)}", icon = "cal")
}

/** Parses a number typed by hand; commas and spaces are ignored. */
fun parseNum(s: String): Double? = s.replace(",", "").replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }

/** A whole number for an input, without ".0". */
fun numText(n: Double?): String = when {
    n == null -> ""
    n == Math.floor(n) && kotlin.math.abs(n) < 1e15 -> n.toLong().toString()
    else -> n.toString()
}

/** A date-and-time input (datetime-local): picks the date, then the time. */
@Composable
fun DateTimeField(value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier, label: String = "Date and time", max: Long? = null) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(value).atZone(zone)
    val text = "${dFull(value)}, ${String.format(java.util.Locale.ENGLISH, "%d:%02d %s", (at.hour + 11) % 12 + 1, at.minute, if (at.hour < 12) "am" else "pm")}"
    Select(text, {
        val d = at.toLocalDate()
        DatePickerDialog(ctx, { _, y, m, day ->
            android.app.TimePickerDialog(ctx, { _, h, min ->
                onChange(LocalDate.of(y, m + 1, day).atTime(h, min).atZone(zone).toInstant().toEpochMilli())
            }, at.hour, at.minute, android.text.format.DateFormat.is24HourFormat(ctx)).show()
        }, d.year, d.monthValue - 1, d.dayOfMonth).apply { if (max != null) datePicker.maxDate = max }.show()
    }, modifier, label = "$label, $text", icon = "cal")
}
