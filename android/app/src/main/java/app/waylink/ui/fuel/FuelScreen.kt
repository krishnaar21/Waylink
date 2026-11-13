package app.waylink.ui.fuel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.FuelStats
import app.waylink.core.dMed
import app.waylink.core.dShort
import app.waylink.core.fuelPriceOf
import app.waylink.core.fuelStats
import app.waylink.core.plural
import app.waylink.core.vehicleTitle
import app.waylink.ui.components.BigValue
import app.waylink.ui.components.Btn
import app.waylink.ui.components.CircleBtn
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.Page
import app.waylink.ui.components.PageHead
import app.waylink.ui.components.SectionGap
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.Segs
import app.waylink.ui.components.Tone
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.Mono
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

interface FuelActions {
    fun back()
    fun sheet(key: String)
    fun help(key: String)
}

private val WORDS = listOf("no", "one", "two", "three")

/** The Fuel page, a port of fuel() in web-v0/js/views.js. */
@Composable
fun FuelScreen(s: AppState, act: FuelActions, bottom: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val v = s.vehicle
    val fs = remember(v) { fuelStats(v) }
    val cur = s.country.cur
    val entries = remember(v.fuel) { v.fuel.sortedWith(compareByDescending<app.waylink.core.FuelEntry> { it.odo }.thenByDescending { it.ts }) }
    val kmplById = remember(fs) { fs.tanks.associate { it.id to it.kmpl } }
    val tankL = f.num(fs.tank, if (fs.tank % 1 != 0.0) 1 else 0)
    val hasRange = fs.range != null

    Page(bottom) {
        PageHead("Fuel", "Fill-ups for ${clip(vehicleTitle(s), 24)}", onBack = act::back) {
            CircleBtn("plus", "Log fuel", { act.sheet("fuel:new") })
        }

        // Range hero
        val colors = when { !hasRange -> c.heroOff; fs.onReserve -> c.heroBad; else -> c.heroOk }
        Column(Modifier.fillMaxWidth().card(24.dp, Color.Transparent).background(c.hero(colors)).padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp)) {
            Txt("Range left", T.eyebrow, c.ink3)
            Spacer(Modifier.height(10.dp))
            if (hasRange) BigValue("≈" + f.num(fs.range), "km", 28f) else Txt("—", T.verdict, c.ink)
            Spacer(Modifier.height(8.dp))
            val n = fs.tanks.size
            Txt(
                when {
                    hasRange -> "Based on your last ${if (n >= 3) "three" else WORDS.getOrElse(n) { n.toString() }} full ${if (n == 1) "tank" else "tanks"}: ${f.num(fs.avg, 1)}\u00A0km\u2060/\u2060L." + if (fs.onReserve) " You’re on reserve." else ""
                    v.fuel.isNotEmpty() -> "Fill to the brim once more to see your range."
                    else -> "Fill to the brim and log it. Range appears after the next full tank."
                },
                T.lead, c.ink2,
            )
            Spacer(Modifier.height(18.dp))
            Segs(List(6) { i -> if (i < (fs.bars ?: 0)) (if (fs.onReserve) Tone.BAD else Tone.NEUTRAL) else null }, height = 7.dp, gap = 4.dp)
            Spacer(Modifier.height(10.dp))
            Txt(
                (if (fs.litresLeft != null) "≈${f.num(fs.litresLeft, 1)} of $tankL L" else "$tankL L tank · ${f.num(fs.reserve, 1)} L reserve") +
                    (if (fs.sinceFull != null) " · ${f.num(fs.sinceFull)} km since full" else ""),
                T.eyebrow.copy(fontSize = 14.sp), c.ink3,
            )
        }
        Spacer(Modifier.height(12.dp))
        Btn("Log fuel", { act.sheet("fuel:new") }, Modifier.fillMaxWidth(), icon = "fuel")

        SectionGap()
        SectionHead("Mileage", help = { act.help("mileage") }) { Txt("Full tank to full tank", T.eyebrow.copy(fontSize = 14.sp), c.ink3) }
        MileageScale(fs, f)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).card(20.dp).height(IntrinsicSize.Min)) {
            Stat("Average", f.num(fs.avg, 1), "km/L, last 3", Modifier.weight(1f))
            VLine()
            Stat("Last tank", f.num(fs.lastTank, 1), "km/L", Modifier.weight(1f))
            VLine()
            Stat("Lifetime", f.num(fs.lifetime, 1), plural(fs.tanks.size, "tank", "tanks"), Modifier.weight(1f))
        }
        if (fs.tanks.size >= 2) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(20.dp).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 12.dp)) {
                Txt("km per litre, by tank", T.eyebrow, c.ink3, Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp))
                MileageChart(fs, f)
            }
        }
        Txt("Mileage counts only between full tanks. Top-ups roll into the next full one.", T.row.copy(fontSize = 13.5.sp, lineHeight = 21.sp), c.ink3, Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp))

        SectionGap()
        SectionHead("Cost", help = { act.help("cost") })
        Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 20.dp, vertical = 2.dp)) {
            Kv("Cost per km", if (fs.costPerKm != null) cur + f.num(fs.costPerKm, 2) else "—")
            Hairline()
            Kv("Last 30 days", "${plural(fs.last30.count, "fill", "fills")} · ${f.num(fs.last30.litres, 1)} L · $cur${f.num(fs.last30.cost)}")
            Hairline()
            Kv("All fuel", cur + f.num(fs.totalSpent))
            Hairline()
            Kv("Price per litre", cur + f.num(fuelPriceOf(s), 2), edit = true) { act.sheet("fuelprice") }
        }

        SectionGap()
        SectionHead("Fill-ups") { Txt("${entries.size}", T.eyebrow.copy(fontSize = 14.sp), c.ink3) }
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 22.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("No fill-ups yet", T.tileTitle.copy(fontSize = 20.sp), c.ink)
                Spacer(Modifier.height(6.dp))
                Txt("Tap Log fuel at the pump.", T.row.copy(fontSize = 14.5.sp), c.ink3)
            }
        } else ListCard {
            entries.forEachIndexed { i, e ->
                if (i > 0) Hairline(start = 20.dp)
                Row(
                    Modifier.fillMaxWidth().pressable(label = "Fill-up, ${dMed(e.ts)}") { act.sheet("fuel:${e.id}") }.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(dMed(e.ts), T.body.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.16).sp, lineHeight = 21.6.sp), c.ink)
                        Spacer(Modifier.height(5.dp))
                        Txt("${if (e.full) "Full" else "Top-up"} · ${f.num(e.odo)} km · $cur${f.num(e.cost)}", T.row.copy(lineHeight = 20.3.sp), c.ink3, maxLines = 1)
                    }
                    val k = kmplById[e.id]
                    if (k != null) BigValue(f.num(k, 1), "km/L", 26f) else BigValue(f.num(e.litres, 1), "L", 26f)
                }
            }
        }
    }
}

@Composable
private fun VLine() = Box(Modifier.width(1.dp).fillMaxHeight().background(W.c.line))

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    val c = W.c
    Column(modifier.padding(16.dp)) {
        Txt(label, T.eyebrow, c.ink3, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Txt(value, T.big(30f), c.ink, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Txt(unit, T.unit, c.ink3, maxLines = 1)
    }
}

@Composable
private fun Kv(label: String, value: String, edit: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = W.c
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(label = "$label, $value", onClick = onClick) else Modifier).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, T.body.copy(fontSize = 15.5.sp, lineHeight = 22.5.sp), c.ink, Modifier.weight(1f), maxLines = 1)
        Txt(value, T.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium, lineHeight = 22.5.sp), c.ink, Modifier.padding(start = 12.dp), maxLines = 1)
        if (edit) {
            Spacer(Modifier.width(8.dp))
            Ic("edit", size = 16.dp, tint = c.ink4)
        }
    }
}

/** Where this tank sits between your thirstiest and thriftiest (mileageScale in views.js). */
@Composable
private fun MileageScale(fs: FuelStats, f: Fmt) {
    val last = fs.lastTank
    if (fs.tanks.size < 2 || last == null) return
    val c = W.c
    val vals = fs.tanks.map { it.kmpl }
    val lo = vals.min()
    val hi = vals.max()
    val pos = ((last - lo) / max(0.1, hi - lo)).coerceIn(0.0, 1.0).toFloat()
    val verdict = when { pos > 0.66f -> "One of your best tanks."; pos < 0.33f -> "Thirstier than usual. Check tyre pressure and chain."; else -> "About your usual." }
    Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Txt("This tank", T.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.5.sp), c.ink)
                Txt(verdict, T.row.copy(fontSize = 14.sp), c.ink2)
            }
            Spacer(Modifier.width(12.dp))
            BigValue(f.num(fs.lastTank, 1), "km/L", 40f)
        }
        Spacer(Modifier.height(36.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 10) {
                    val k = i / 9f
                    val shape = when (i) { 0 -> RoundedCornerShape(6.dp, 3.dp, 3.dp, 6.dp); 9 -> RoundedCornerShape(3.dp, 6.dp, 6.dp, 3.dp); else -> RoundedCornerShape(3.dp) }
                    Box(Modifier.weight(1f).fillMaxHeight().clip(shape).background(lerp(c.segOff, c.sage, k)))
                }
            }
            // The pin: a dark bubble with a little tail, centred over this tank's place on the scale.
            val label = f.num(fs.lastTank, 1)
            Column(
                Modifier.offset(x = (w * pos) - 21.dp, y = (-32).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt(label, T.chip, c.bg, Modifier.clip(RoundedCornerShape(999.dp)).background(c.ink).padding(horizontal = 8.dp, vertical = 3.dp))
                Canvas(Modifier.width(8.dp).height(4.dp)) {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, c.ink)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Txt("${f.num(lo, 1)} thirstiest", T.rowSub, c.ink3, Modifier.weight(1f))
            Txt("Usual", T.rowSub, c.ink3)
            Txt("${f.num(hi, 1)} best", T.rowSub.copy(fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End), c.sageInk, Modifier.weight(1f), maxLines = 1)
        }
    }
}

/** km per litre for the last eight full tanks, with the average as a dashed accent line. */
@Composable
private fun MileageChart(fs: FuelStats, f: Fmt) {
    val c = W.c
    val t = fs.tanks.takeLast(8)
    val measurer = rememberTextMeasurer()
    val mono = TextStyle(fontFamily = Mono, fontSize = 9.sp, color = c.ink3)
    val avgStyle = TextStyle(fontFamily = Mono, fontSize = 9.5.sp, color = c.accent)
    val valStyle = T.big(13f).copy(color = c.ink, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    val vals = t.map { it.kmpl }
    var lo = min(vals.min(), fs.avg ?: Double.POSITIVE_INFINITY)
    var hi = max(vals.max(), fs.avg ?: Double.NEGATIVE_INFINITY)
    val padv = max(0.6, (hi - lo) * 0.25)
    lo = floor(lo - padv); hi = ceil(hi + padv)
    val iMax = vals.indexOf(vals.max())
    val iMin = vals.indexOf(vals.min())
    val labelled = setOf(iMax, iMin, vals.lastIndex)
    val desc = t.joinToString("; ") { "${dShort(it.ts)} ${f.num(it.kmpl, 1)} km/L" }
    Canvas(
        Modifier.fillMaxWidth().height(168.dp).semantics { contentDescription = "Mileage per full tank: $desc" },
    ) {
        val sc = size.width / 340f
        val pl = 30 * sc; val pr = 16 * sc; val pt = 22 * sc; val pb = 30 * sc
        val wd = size.width; val h = size.height
        fun x(i: Int) = if (t.size == 1) pl + (wd - pl - pr) / 2 else pl + i * (wd - pl - pr) / (t.size - 1)
        fun y(v: Double) = (pt + ((hi - v) * (h - pt - pb)) / (if (hi - lo == 0.0) 1.0 else hi - lo)).toFloat()
        for (v in listOf(lo, (lo + hi) / 2, hi)) {
            drawLine(c.line, Offset(pl, y(v)), Offset(wd - pr, y(v)), 1.dp.toPx())
            val m = measurer.measure(f.num(v, 0), mono)
            drawText(m, topLeft = Offset(pl - 8.dp.toPx() - m.size.width, y(v) - m.size.height / 2))
        }
        fs.avg?.let { a ->
            drawLine(c.accent, Offset(pl, y(a)), Offset(wd - pr, y(a)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
            val m = measurer.measure("avg ${f.num(a, 1)}", avgStyle)
            drawRoundRect(c.card, Offset(pl + 2.dp.toPx(), y(a) - 17.dp.toPx()), androidx.compose.ui.geometry.Size(m.size.width + 8.dp.toPx(), 13.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawText(m, topLeft = Offset(pl + 6.dp.toPx(), y(a) - 17.dp.toPx() + (13.dp.toPx() - m.size.height) / 2))
        }
        val path = Path()
        t.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.kmpl)) else path.lineTo(x(i), y(p.kmpl)) }
        drawPath(path, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        t.forEachIndexed { i, p ->
            val o = Offset(x(i), y(p.kmpl))
            drawCircle(c.card, 4.5.dp.toPx(), o)
            drawCircle(c.ink, 4.5.dp.toPx(), o, style = Stroke(2.dp.toPx()))
            if (i in labelled) {
                val m = measurer.measure(f.num(p.kmpl, 1), valStyle)
                val below = i == iMin && i != iMax
                drawText(m, topLeft = Offset(o.x - m.size.width / 2, if (below) o.y + 8.dp.toPx() else o.y - 10.dp.toPx() - m.size.height))
            }
            val d = measurer.measure(dShort(p.ts), mono)
            drawText(d, topLeft = Offset(o.x - d.size.width / 2, h - 8.dp.toPx() - d.size.height))
        }
    }
}

private fun clip(t: String, n: Int) = if (t.length > n) t.take(n - 1).trimEnd() + "…" else t
