package app.waylink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.Due
import app.waylink.core.Fmt
import app.waylink.core.ServiceRow
import app.waylink.core.ServiceStatus
import app.waylink.core.Verdict
import app.waylink.core.everyText
import app.waylink.core.rowValue
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlin.math.max
import kotlin.math.roundToInt

/* Service pieces shared by Today and the Logbook: the verdict hero, a reminder row, its gauge. */

/** How many of [n] gauge segments are still "fresh" for a job. */
fun freshSegs(st: ServiceStatus, n: Int): Int = if (st.status == Due.OVERDUE) 0 else max(1, ((1 - st.frac) * n).roundToInt())

/**
 * The service gauge with the job's tone (web .fgauge). In list rows it is grey; in a sheet it keeps the tone
 * (ink 30 % fine, amber due soon, every segment pale red overdue) and gets E and F at its ends.
 */
@Composable
fun JobGauge(st: ServiceStatus, n: Int = 10, height: androidx.compose.ui.unit.Dp = 5.dp, inRow: Boolean = false, labels: Boolean = false) {
    val c = W.c
    val over = st.status == Due.OVERDUE
    val on = if (inRow) (if (st.status == Due.OK) c.inkA(0.30f) else c.inkA(0.42f))
    else when (st.status) { Due.OVERDUE -> c.red; Due.SOON -> c.amber; Due.OK -> c.inkA(0.30f) }
    val off = if (over && !inRow) c.red.copy(alpha = 0.22f) else null
    if (!labels) { ServiceGauge(freshSegs(st, n), n, height = height, onColor = on, offColor = off); return }
    val ef = T.eyebrow.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp, lineHeight = 11.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Txt("E", ef, if (over) c.red else c.ink3)
        Spacer(Modifier.width(8.dp))
        ServiceGauge(freshSegs(st, n), n, Modifier.weight(1f), height = height, onColor = on, offColor = off)
        Spacer(Modifier.width(8.dp))
        Txt("F", ef, c.ink3)
    }
}

@Composable
fun Hero(v: Verdict, line: String, reserve: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = W.c
    val colors = when {
        reserve && v.over == 0 -> c.heroSoon
        v.tone == Due.OVERDUE -> c.heroBad
        v.tone == Due.SOON -> c.heroSoon
        else -> c.heroOk
    }
    Column(
        Modifier.fillMaxWidth().card(24.dp, Color.Transparent).background(c.hero(colors))
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier).padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp),
    ) {
        Txt("Verdict", T.eyebrow, c.ink3)
        Spacer(Modifier.height(10.dp))
        Txt(v.word, T.verdict, c.ink)
        Spacer(Modifier.height(8.dp))
        Txt(line, T.lead, c.ink2)
        Spacer(Modifier.height(18.dp))
        if (v.over + v.soon > 0) {
            Segs(v.bar.map { when (it) { Due.OVERDUE -> Tone.BAD; Due.SOON -> Tone.SOON; Due.OK -> Tone.OK } }, height = 7.dp, gap = 4.dp)
            Spacer(Modifier.height(10.dp))
            Txt("${v.ok} on track · ${v.soon} due soon · ${v.over} overdue", T.eyebrow.copy(fontSize = 14.sp), c.ink3)
        } else Txt("All ${v.ok} jobs on track", T.eyebrow.copy(fontSize = 14.sp), c.ink3)
    }
}


/** A reminder row in the Logbook (.row.has-ic): icon, name, value, cadence and, when close, the gauge. */
@Composable
fun ServiceRowItem(r: ServiceRow, f: Fmt, onClick: () -> Unit) {
    val c = W.c
    val st = r.st
    val v = rowValue(st, f)
    Column(Modifier.fillMaxWidth().pressable(label = r.item.name, onClick = onClick).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(r.item.icon, size = 42.dp, iconSize = 21.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(r.item.name, T.body.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.16).sp, lineHeight = 21.6.sp), c.ink, Modifier.weight(1f), maxLines = 2)
                    Spacer(Modifier.width(12.dp))
                    BigValue(v.v, v.u.removeSuffix(" overdue"), 26f)
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (st.status == Due.OVERDUE) { Opill(); Spacer(Modifier.width(6.dp)) }
                    Txt(everyText(r.item, f), T.row.copy(lineHeight = 20.3.sp), c.ink3, maxLines = 1)
                }
            }
        }
        if (st.status != Due.OK) {
            Spacer(Modifier.height(12.dp))
            JobGauge(st, inRow = true)
        }
    }
}
