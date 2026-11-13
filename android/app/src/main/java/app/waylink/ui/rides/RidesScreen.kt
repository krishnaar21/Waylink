package app.waylink.ui.rides

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.PeriodStats
import app.waylink.core.RideCharts
import app.waylink.core.clock
import app.waylink.core.dMed
import app.waylink.core.dur
import app.waylink.core.monShort
import app.waylink.core.periodStats
import app.waylink.core.plural
import app.waylink.core.rideCharts
import app.waylink.core.tShort
import app.waylink.core.vehicleTitle
import app.waylink.core.weekStart
import app.waylink.data.Live
import app.waylink.ui.components.BigValue
import app.waylink.ui.components.Btn
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Page
import app.waylink.ui.components.PageHead
import app.waylink.ui.components.SectionGap
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.Serif
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import java.time.DayOfWeek
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

interface RidesActions {
    fun record()
    fun endRide()
    fun open(id: String)
    fun chart(page: Int)
}

private val RC_NAMES = listOf("Calendar", "Km per week", "Km by weekday")

/** The Rides tab, a port of trips() in web-v0/js/views.js. */
@Composable
fun RidesScreen(s: AppState, live: Live, act: RidesActions, bottom: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val v = s.vehicle
    val title = clip(vehicleTitle(s), 20)
    val wk = remember(v.trips) { periodStats(v.trips, weekStart()) }
    val mo = remember(v.trips) { periodStats(v.trips, System.currentTimeMillis() - 30L * 86_400_000) }
    val charts = remember(v.trips) { rideCharts(v.trips) }
    val r = live.rec

    Page(bottom) {
        PageHead("Rides", "${plural(v.trips.size, "ride", "rides")} on $title")
        if (r != null) {
            Column(Modifier.fillMaxWidth().padding(top = 2.dp).card(20.dp).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecTag()
                    Spacer(Modifier.weight(1f))
                    Ticker(r.start)
                }
                Spacer(Modifier.height(12.dp))
                Txt("Today’s ride, in progress.", T.cardTitle.copy(fontSize = 24.sp, lineHeight = 30.sp), c.ink)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    BigValue(f.num(r.km, 2), "km", 46f)
                    Spacer(Modifier.width(12.dp))
                    Txt("${f.num(live.speed)} km/h now · top ${f.num(r.top)}", T.row, c.ink2, Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
                Btn("End ride", act::endRide, Modifier.fillMaxWidth(), icon = "stop")
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp).card(20.dp).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Ready when you are", T.cardTitle.copy(fontSize = 22.sp, lineHeight = 33.sp), c.ink)
                    Spacer(Modifier.height(8.dp))
                    Txt(if (s.settings.autoRecord) "Rides record by themselves once the bike links." else "Tap play to record with your phone’s GPS.", T.row, c.ink2)
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(c.ink).pressable(label = "Record a ride", onClick = act::record),
                    contentAlignment = Alignment.Center,
                ) { Ic("play", size = 20.dp, tint = c.bg) }
            }
        }

        // All rides, with the swipeable charts.
        val tot = v.trips.sumOf { it.km }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(20.dp).padding(top = 18.dp, bottom = 18.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Txt("All rides", T.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.5.sp), c.ink)
                Spacer(Modifier.height(2.dp))
                Txt("${v.trips.size}", T.big(64f), c.ink)
                Spacer(Modifier.height(2.dp))
                // .rh-line u: the distance underlined in the accent colour, 1.5 dp thick, 4 dp below the text
                var uStart = -1; var uEnd = -1
                val line = buildAnnotatedString {
                    if (v.trips.isEmpty()) append("Your rides will fill this in, one square a day.")
                    else {
                        append("You’ve ridden ")
                        uStart = length
                        append("${f.num(tot, if (tot < 100) 1 else 0)} km")
                        uEnd = length
                        append(" on $title")
                        if (v.trips.size > 1) append(", about ${f.num(tot / v.trips.size, 0)} km a ride")
                        append(".")
                    }
                }
                var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                val accent = c.accent
                androidx.compose.foundation.text.BasicText(
                    line,
                    Modifier.drawBehind {
                        val l = layout ?: return@drawBehind
                        if (uStart < 0 || uEnd > l.layoutInput.text.length) return@drawBehind
                        val th = 1.5.dp.toPx(); val off = 4.dp.toPx()
                        // one stroke per line the range covers
                        val first = l.getLineForOffset(uStart); val last = l.getLineForOffset(uEnd - 1)
                        for (ln in first..last) {
                            val x0 = if (ln == first) l.getHorizontalPosition(uStart, true) else l.getLineLeft(ln)
                            val x1 = if (ln == last) l.getHorizontalPosition(uEnd, true) else l.getLineRight(ln)
                            val y = l.getLineBaseline(ln) + off
                            drawRect(accent, androidx.compose.ui.geometry.Offset(minOf(x0, x1), y), androidx.compose.ui.geometry.Size(kotlin.math.abs(x1 - x0), th))
                        }
                    },
                    Serif.copy(color = c.ink, lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None)),
                    onTextLayout = { layout = it },
                )
            }
            Spacer(Modifier.height(18.dp))
            Charts(charts, s.settings.rideChart, act::chart, f)
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp).card(20.dp).height(IntrinsicSize.Min)) {
            Stat2("This week", wk, f, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Stat2("Last 30 days", mo, f, Modifier.weight(1f))
        }

        SectionGap()
        SectionHead("Rides") { Txt("Newest first", T.eyebrow.copy(fontSize = 14.sp), c.ink3) }
        if (v.trips.isEmpty()) {
            Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 22.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("No rides yet", T.tileTitle.copy(fontSize = 20.sp), c.ink)
                Spacer(Modifier.height(6.dp))
                Txt("Your first ride shows up here, route and all.", T.row.copy(fontSize = 14.5.sp, textAlign = TextAlign.Center), c.ink3)
            }
        } else Column(Modifier.fillMaxWidth().card(20.dp).padding(4.dp)) {
            v.trips.forEachIndexed { i, t ->
                if (i > 0) Hairline(start = 96.dp)
                Row(
                    Modifier.fillMaxWidth().pressable(label = "${t.title}, ${dMed(t.start)}") { act.open(t.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sketch(t.pts, Modifier.size(74.dp).clip(RoundedCornerShape(12.dp)).background(c.bg), large = false)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("${dMed(t.start)} · ${tShort(t.start)}", T.row.copy(fontSize = 13.5.sp, lineHeight = 20.25.sp), c.ink3, maxLines = 1)
                        Txt(t.title, T.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium, lineHeight = 23.25.sp), c.ink, Modifier.padding(top = 4.dp), maxLines = 1)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigValue(f.num(t.km, 1), "km", 22f)
                            Txt(dur(t.sec), T.row, c.ink2, maxLines = 1)
                            Txt("top ${f.num(t.top)}", T.row, c.ink2, maxLines = 1)
                        }
                    }
                    Txt("№" + t.n.toString().padStart(3, '0'), T.plate.copy(fontSize = 10.sp, letterSpacing = 0.sp), c.ink3, Modifier.align(Alignment.Top).padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Stat2(label: String, p: PeriodStats, f: Fmt, modifier: Modifier) {
    val c = W.c
    Column(modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Txt(label, T.eyebrow, c.ink3)
        Spacer(Modifier.height(10.dp))
        BigValue(f.num(p.km, if (p.km < 100) 1 else 0), "km", 42f)
        Spacer(Modifier.height(8.dp))
        Txt("${plural(p.rides, "ride", "rides")} · ${dur(p.sec)}", T.row, c.ink2, maxLines = 1)
        if (p.top > 0) Txt("top ${f.num(p.top)} km/h", T.row, c.ink3, maxLines = 1)
    }
}

/** The pulsing "Recording" tag. */
@Composable
fun RecTag() {
    val c = W.c
    val a by rememberInfiniteTransition(label = "rec").animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(6.dp))
        Txt("Recording", T.chip.copy(fontSize = 13.sp), c.accent)
    }
}

/** A ticking ride clock. */
@Composable
fun Ticker(start: Long) {
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    Txt(clock((now - start) / 1000.0), T.plate.copy(fontSize = 13.sp, letterSpacing = 0.sp), W.c.ink3)
}

/** Calendar, km per week and km by weekday, swiped sideways. The page you leave on is remembered. */
@Composable
private fun Charts(ch: RideCharts, at: Int, onPage: (Int) -> Unit, f: Fmt) {
    val c = W.c
    val pager = rememberPagerState(initialPage = at.coerceIn(0, 2)) { 3 }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it != at) onPage(it) } }
    HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { p ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(RC_NAMES[p], T.rowSub.copy(fontWeight = FontWeight.SemiBold), c.ink3, Modifier.weight(1f))
                if (p == 0) Legend()
            }
            Box {
                Box(Modifier.alpha(if (ch.demo) 0.38f else 1f)) {
                    when (p) {
                        0 -> Calendar(ch)
                        1 -> Weekly(ch, f)
                        else -> Weekdays(ch, f)
                    }
                }
                if (ch.demo) Txt(
                    "An example. Your rides fill this in.", T.rowSub.copy(fontWeight = FontWeight.SemiBold), c.ink2,
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(999.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
    // Page dots
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
        repeat(3) { i ->
            Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 16.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == pager.currentPage) c.ink2 else c.inkA(0.18f)))
        }
    }
}

/** One chart as a choice in Settings > Ride chart (.rcopt): name and legend, then the chart, dimmed when it's an example. */
@Composable
fun RideChartOption(ch: RideCharts, i: Int, on: Boolean, f: Fmt, onClick: () -> Unit) {
    val c = W.c
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)
            .border(if (on) 2.dp else 1.dp, if (on) c.ink else c.line, RoundedCornerShape(20.dp))
            .semantics { selected = on }.pressable(label = RC_NAMES[i], role = androidx.compose.ui.semantics.Role.RadioButton, scale = 0.985f, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(RC_NAMES[i], T.row.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), c.ink, Modifier.weight(1f))
            if (i == 0) Legend()
        }
        Box(Modifier.alpha(if (ch.demo) 0.38f else 1f)) {
            when (i) { 0 -> Calendar(ch); 1 -> Weekly(ch, f); else -> Weekdays(ch, f) }
        }
    }
}

@Composable
private fun heat(band: Int): Color {
    val c = W.c
    return when (band) { 1 -> lerp(c.segOff, c.accent, 0.35f); 2 -> lerp(c.segOff, c.accent, 0.6f); 3 -> lerp(c.segOff, c.accent, 0.82f); 4 -> c.accent; else -> c.segOff }
}

@Composable
private fun Legend() {
    val c = W.c
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt("0", T.rowSub.copy(fontSize = 11.5.sp), c.ink3, Modifier.padding(end = 2.dp))
        for (b in 0..4) Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(heat(b)))
        Txt("80+ km a day", T.rowSub.copy(fontSize = 11.5.sp), c.ink3, Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Calendar(ch: RideCharts) {
    val c = W.c
    val desc = "${ch.weeks.flatten().count { it.km > 0 && !it.future }} riding days in the last 12 weeks"
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = desc }) {
        // 14 dp of day letters, an 8 dp gap, then 12 columns with 4 dp gaps.
        val cell = (maxWidth - 22.dp - 44.dp) / 12
        Column {
            Box(Modifier.fillMaxWidth().height(18.dp)) {
                ch.months.forEach { (w, m) ->
                    Txt(m, T.rowSub.copy(fontSize = 12.sp, lineHeight = 14.sp), c.ink3, Modifier.offset(x = 22.dp + (cell + 4.dp) * w), maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.width(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("S", "", "T", "", "T", "", "S").forEach { d ->
                        Box(Modifier.size(14.dp, cell), contentAlignment = Alignment.Center) { Txt(d, T.rowSub.copy(fontSize = 11.sp, lineHeight = 11.sp), c.ink3) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ch.weeks.forEach { col ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            col.forEach { d ->
                                val shape = RoundedCornerShape(4.dp)
                                val m = Modifier.size(cell).clip(shape)
                                Box(
                                    if (d.future) m.border(1.dp, c.line, shape)
                                    else m.background(heat(d.band)).then(if (d.today) Modifier.border(1.5.dp, c.ink, shape) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Weekly(ch: RideCharts, f: Fmt) {
    val c = W.c
    val wMax = ch.bars.maxOf { it.km }
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(28.dp).fillMaxHeight().padding(top = 8.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            listOf(ch.nice, ch.nice / 2, 0).forEach { Txt("$it", T.rowSub.copy(fontSize = 11.sp, lineHeight = 12.sp), c.ink3) }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val top = 14.dp.toPx()
                drawLine(c.line, Offset(0f, top), Offset(size.width, top), 1.dp.toPx())
                drawLine(c.line, Offset(0f, top + (size.height - top) / 2), Offset(size.width, top + (size.height - top) / 2), 1.dp.toPx())
                drawLine(c.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            }
            Row(Modifier.fillMaxSize().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                ch.bars.forEachIndexed { i, b ->
                    val now = i == 11
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (b.km > 0 && (b.km == wMax || now)) Txt(f.num(b.km, if (b.km < 100) 1 else 0), T.rowSub.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp), c.ink2, Modifier.padding(bottom = 3.dp), maxLines = 1)
                        if (b.km > 0) Box(
                            Modifier.fillMaxWidth(0.72f).widthIn(max = 16.dp).fillMaxHeight(max(0.03f, min(1f, (b.km / ch.nice).toFloat())))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (now) c.accent else lerp(c.segOff, c.accent, 0.45f)),
                        )
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 6.dp)) {
        ch.bars.forEachIndexed { i, b ->
            Box(Modifier.weight(1f)) {
                if (i % 3 == 0 || i == 11) Txt(if (i == 11) "Now" else "${b.start.dayOfMonth} ${monShort(b.start.monthValue)}", T.rowSub.copy(fontSize = 11.sp, lineHeight = 13.sp), c.ink3, maxLines = 1, modifier = Modifier.widthIn(min = 44.dp))
            }
        }
    }
}

@Composable
private fun Weekdays(ch: RideCharts, f: Fmt) {
    val c = W.c
    val wdMax = max(1.0, ch.weekdays.maxOf { it.km })
    val fav = ch.weekdays.maxByOrNull { it.km }?.takeIf { it.km > 0 }?.dow
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ch.weekdays.forEach { d ->
            val top = d.dow == fav
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(d.dow.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, T.rowSub, if (top) c.ink else c.ink3, Modifier.width(34.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(6.dp)).background(c.segOff)) {
                    if (d.km > 0) Box(Modifier.fillMaxHeight().fillMaxWidth(max(0.04f, (d.km / wdMax).toFloat())).clip(RoundedCornerShape(6.dp)).background(if (top) c.accent else lerp(c.segOff, c.accent, 0.5f)))
                }
                Spacer(Modifier.width(10.dp))
                Txt(if (d.km > 0) f.num(d.km, if (d.km < 100) 1 else 0) else "–", T.rowSub.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End), if (top) c.ink else c.ink2, Modifier.width(44.dp), maxLines = 1)
            }
        }
    }
}

/** The ride's track on squared paper: green dot at the start, accent diamond at the end. */
@Composable
fun Sketch(pts: List<List<Double>>, modifier: Modifier, large: Boolean) {
    val c = W.c
    Canvas(modifier) {
        val step = (if (large) 24 else 12).dp.toPx()
        var gx = step
        while (gx < size.width) { drawLine(c.line, Offset(gx, 0f), Offset(gx, size.height), 1.dp.toPx()); gx += step }
        var gy = step
        while (gy < size.height) { drawLine(c.line, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()); gy += step }
        val p = pts.filter { it.size >= 2 }
        if (p.size < 2) return@Canvas
        val pad = (if (large) 26 else 12).dp.toPx()
        val lat0 = p[0][0] * Math.PI / 180
        val xy = p.map { (it[1] * cos(lat0)) to -it[0] }
        val minx = xy.minOf { it.first }; val maxx = xy.maxOf { it.first }
        val miny = xy.minOf { it.second }; val maxy = xy.maxOf { it.second }
        val sc = min((size.width - pad * 2) / max(maxx - minx, 1e-9), (size.height - pad * 2) / max(maxy - miny, 1e-9))
        val ox = (size.width - (maxx - minx) * sc) / 2
        val oy = (size.height - (maxy - miny) * sc) / 2
        val pp = xy.map { Offset((ox + (it.first - minx) * sc).toFloat(), (oy + (it.second - miny) * sc).toFloat()) }
        val path = Path().apply { pp.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        drawPath(path, c.ink, style = Stroke((if (large) 2.6f else 2f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val r = (if (large) 6 else 4).dp.toPx()
        val a = pp.first(); val b = pp.last()
        drawCircle(c.bg, r + 2.dp.toPx(), a); drawCircle(c.sageInk, r, a)
        rotate(45f, b) {
            drawRect(c.bg, Offset(b.x - r - 2.dp.toPx(), b.y - r - 2.dp.toPx()), Size(r * 2 + 4.dp.toPx(), r * 2 + 4.dp.toPx()))
            drawRect(c.accent, Offset(b.x - r, b.y - r), Size(r * 2, r * 2))
        }
    }
}

private fun clip(t: String, n: Int) = if (t.length > n) t.take(n - 1).trimEnd() + "…" else t
