package app.waylink.ui.components

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

/** A scrolling page under the status bar with the tab bar's room at the bottom. */
@Composable
fun Page(bottom: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = bottom + 24.dp),
        content = content,
    )
}

/** .pagehead: optional back and action circles, then the title and its grey subtitle. */
@Composable
fun PageHead(title: String, sub: String?, onBack: (() -> Unit)? = null, action: (@Composable RowScope.() -> Unit)? = null) {
    val c = W.c
    if (onBack != null || action != null) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) CircleBtn("back", "Back", onBack)
            Spacer(Modifier.weight(1f))
            action?.invoke(this)
        }
    } else Spacer(Modifier.height(4.dp))
    Txt(title, T.title, c.ink, Modifier.semantics { heading() })
    if (sub != null) {
        Spacer(Modifier.height(3.dp))
        Txt(sub, T.subtitle, c.ink3, maxLines = 1)
    }
    Spacer(Modifier.height(18.dp))
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
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(label = title, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, size = 36.dp, radius = 10.dp, bg = c.bg, tint = if (danger) c.red else c.ink2)
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
            .pressable(label = text, onClick = onClick).padding(horizontal = 15.dp),
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
    val c = W.c
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.width(46.dp).height(28.dp).clip(shape).background(if (on) c.accent else c.inkA(0.12f))
            .semantics { stateDescription = if (on) "On" else "Off" }
            .pressable(enabled = enabled, label = label, role = Role.Switch, onClick = onToggle).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) Color.White else c.raised)) }
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
    }, modifier, label = "$label, ${dFull(value)}")
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
    }, modifier, label = "$label, $text")
}
