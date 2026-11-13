package app.waylink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em
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
    if (app.waylink.ui.theme.Looks.progress != "gauge") { ProgressAlt(st, app.waylink.ui.theme.Looks.progress, showEnds = !inRow); return }
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

/**
 * The other progress styles (web progressBar): "road" (the road travelled since the last service, the flag is the
 * next one), "tread" (16 slanted blocks that wear flat) and "dipstick" (oil between MIN and MAX).
 * [showEnds] keeps the end labels of road and dipstick, which list rows hide.
 */
@Composable
fun ProgressAlt(st: ServiceStatus, style: String, showEnds: Boolean = true, labelSize: Float = 10.5f) {
    val c = W.c
    val over = st.status == Due.OVERDUE
    val col = when (st.status) { Due.OVERDUE -> c.red; Due.SOON -> c.amber; Due.OK -> c.inkA(0.72f) }
    val left = if (over) 0.0 else max(0.03, 1 - st.frac)
    val pct = (left * 100).roundToInt()
    val lab = T.eyebrow.copy(fontSize = labelSize.sp, fontWeight = FontWeight.SemiBold, lineHeight = labelSize.sp, letterSpacing = 0.01.em)
    val labCol = if (over) c.red else c.ink3
    val desc = if (over) "Overdue" else "$pct% left"
    Column(Modifier.fillMaxWidth().semantics { contentDescription = desc }) {
        when (style) {
            "road" -> {
                val at = if (over) 1f else (st.frac * 100).coerceIn(4.0, 96.0).toFloat() / 100f
                val card = c.card; val off = c.segOff
                Box(Modifier.fillMaxWidth().height(10.dp).drawBehind {
                    val h = 8.dp.toPx(); val top = (size.height - h) / 2; val r = CornerRadius(h / 2)
                    drawRoundRect(col, Offset(0f, top), Size(size.width, h), r)
                    // the centre line, dashed in the card colour
                    val dash = 5.dp.toPx(); val lh = 1.5.dp.toPx(); var x = 4.dp.toPx(); val end = size.width - 4.dp.toPx()
                    while (x < end) { drawRect(card, Offset(x, (size.height - lh) / 2), Size(minOf(dash, end - x), lh)); x += dash * 2 }
                    val w = size.width * at
                    drawPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, top, w, top + h, topLeftCornerRadius = r, bottomLeftCornerRadius = r)) }, off)
                })
                if (showEnds) {
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth()) { Txt("Last service", lab, labCol, Modifier.weight(1f)); Txt(if (over) "Overdue" else "Next", lab, labCol) }
                }
            }
            "tread" -> {
                val n = 16
                val f = if (over) 0 else max(1, (left * n).roundToInt())
                val offCol = if (over) c.red.copy(alpha = 0.28f) else c.segOff
                Box(Modifier.fillMaxWidth().height(7.dp).padding(horizontal = 3.dp).drawBehind {
                    val gap = 3.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
                    val skew = kotlin.math.tan(Math.toRadians(28.0)).toFloat()
                    for (i in 0 until n) {
                        val h = if (i < f) size.height else 2.dp.toPx()
                        val x = i * (w + gap); val y = size.height - h
                        // skewX(-28deg) about the block's centre: the top leans right
                        val dx = skew * h / 2
                        val p = Path().apply { moveTo(x + dx, y); lineTo(x + w + dx, y); lineTo(x + w - dx, size.height); lineTo(x - dx, size.height); close() }
                        drawPath(p, if (i < f) col else offCol)
                    }
                })
                Spacer(Modifier.height(6.dp))
                Txt(if (over) "Worn out. Time to service" else "$pct% tread left", lab, labCol)
            }
            else -> { // dipstick
                val lvl = if (over) 0.04f else (0.08 + left * 0.78).toFloat()
                val card = c.card; val off = c.segOff; val mark = c.ink3; val hatch = c.inkA(0.18f)
                Box(Modifier.fillMaxWidth().height(14.dp).drawBehind {
                    val W0 = size.width; val H = size.height; val th = 4.dp.toPx(); val ty = (H - th) / 2
                    val trackEnd = W0 - 18.dp.toPx()
                    val tip = W0 * 0.03f
                    drawPath(Path().apply { moveTo(0f, H / 2); lineTo(tip, ty); lineTo(trackEnd, ty); lineTo(trackEnd, ty + th); lineTo(tip, ty + th); close() }, off)
                    // the cross-hatched band between the marks
                    val hx = W0 * 0.06f; val hw = W0 * 0.74f; val hy = 3.dp.toPx(); val hh = 8.dp.toPx()
                    clipRect(hx, hy, hx + hw, hy + hh) {
                        val step = 5.dp.toPx(); val sw = 1.5.dp.toPx(); var x = hx - hh
                        while (x < hx + hw + hh) { drawLine(hatch, Offset(x, hy + hh), Offset(x + hh, hy), sw); x += step }
                    }
                    val ow = (W0 * lvl).coerceAtLeast(8.dp.toPx()); val n4 = 4.dp.toPx(); val n3 = 3.dp.toPx()
                    drawPath(Path().apply { moveTo(0f, H / 2); lineTo(n4, ty); lineTo(ow - n3, ty); lineTo(ow, H / 2); lineTo(ow - n3, ty + th); lineTo(n4, ty + th); close() }, col)
                    val mw = 2.dp.toPx(); val mh = 12.dp.toPx(); val my = 1.dp.toPx(); val ring = 1.5.dp.toPx()
                    listOf(0.06f, 0.80f).forEach { at ->
                        val mx = W0 * at
                        drawRoundRect(card, Offset(mx - ring, my - ring), Size(mw + ring * 2, mh + ring * 2), CornerRadius(ring + 1f))
                        drawRoundRect(mark, Offset(mx, my), Size(mw, mh), CornerRadius(1.dp.toPx()))
                    }
                    // the handle: a ring with a short stem
                    val d = 14.dp.toPx(); val cx = W0 - d / 2; val cy = H / 2
                    drawRoundRect(mark, Offset(W0 - d - 5.dp.toPx(), cy - 2.dp.toPx()), Size(7.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    drawCircle(card, d / 2, Offset(cx, cy))
                    drawCircle(mark, d / 2 - 1.25.dp.toPx(), Offset(cx, cy), style = Stroke(2.5.dp.toPx()))
                })
                if (showEnds) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.fillMaxWidth(0.06f))
                        Txt("MIN", lab, labCol)
                        Spacer(Modifier.weight(1f))
                        Txt("MAX", lab, labCol)
                        Spacer(Modifier.fillMaxWidth(0.12f))
                    }
                }
            }
        }
    }
}
