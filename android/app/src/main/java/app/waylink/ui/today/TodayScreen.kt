package app.waylink.ui.today

import app.waylink.ui.components.fitScrollX
import app.waylink.ui.components.fitScrollY
import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.waylink.ui.components.StickyBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Due
import app.waylink.core.Fmt
import app.waylink.core.ServiceRow
import app.waylink.core.fuelStats
import app.waylink.core.modelName
import app.waylink.core.periodStats
import app.waylink.core.sortedService
import app.waylink.core.vehicleTitle
import app.waylink.core.verdictOf
import app.waylink.core.weekStart
import app.waylink.data.LinkState
import app.waylink.data.Live
import app.waylink.data.Weather
import app.waylink.ui.components.BigValue
import app.waylink.ui.components.BikeArt
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.CircleBtn
import app.waylink.ui.components.Dot
import app.waylink.ui.components.FuelBars
import app.waylink.ui.components.Hero
import app.waylink.ui.components.Ic
import app.waylink.ui.components.IconTile
import app.waylink.ui.components.LinkText
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.Segs
import app.waylink.ui.components.ServiceGauge
import app.waylink.ui.components.Tone
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.components.rise
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Things Today can open. Screens not yet built on Android show a short note instead. */
interface TodayActions {
    fun garage()
    fun sos()
    fun pair()
    /** Start pairing straight away: the pick list, or the "No bike linked" intro (web pair-intro). */
    fun pairNew() = pair()
    fun reconnect() = pair()
    fun cancelLink() = pair()
    fun ride()
    fun logbook()
    fun service(id: String)
    fun alerts()
    fun fuel()
    fun trip(which: String)
    fun rides()
    fun glovebox()
    fun weather()
    fun readingsHelp()
    /** Leaves a developer preview state. */
    fun exitPreview() {}
    /** A quick link: "tab:<key>", "page:<key>", "record", "ride", or a sheet key. */
    fun open(key: String) {}
}

/** The Today tab, a faithful port of today() in web-v0/js/views.js. */
@Composable
fun TodayScreen(s: AppState, live: Live, act: TodayActions, bottomPadding: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val v = s.vehicle
    val list = remember(v) { sortedService(v) }
    val verdict = remember(list) { verdictOf(list) }
    val fs = remember(v) { fuelStats(v) }
    val good = verdict.over == 0 && verdict.soon == 0 && !fs.onReserve
    var line = verdict.line
    if (fs.onReserve) line = "On reserve, about ${f.num(fs.range)} km left." + if (verdict.over + verdict.soon > 0) " $line" else ""
    val urgent = list.filter { it.st.status != Due.OK }.take(4)
    val missed = s.alerts.missed

    val scroll = rememberScrollState()
    val head = remember { app.waylink.ui.components.StickyHead() }
    val sos: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = { CircleBtn("sos", "Emergency SOS", act::sos, tint = c.red) }
    androidx.compose.runtime.SideEffect { head.title = LocalDate.now().dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.ENGLISH); head.end = sos }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxWidth().rise().verticalScroll(scroll, enabled = scroll.maxValue > app.waylink.ui.components.nearFitPx())
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = bottomPadding + 90.dp),
    ) {
        // Header: weekday, demo pill or date, SOS.
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 1.dp)) {
                val today = LocalDate.now()
                Txt(today.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.ENGLISH), T.title, c.ink, Modifier.semantics { heading() })
                if (s.sample || live.simulated || live.scenario != null) DemoPill()
                else Txt("${today.dayOfMonth} ${today.month.getDisplayName(JTextStyle.FULL, Locale.ENGLISH)}", T.subtitle, c.ink3)
            }
            CircleBtn("sos", "Emergency SOS", act::sos, Modifier.padding(top = 11.dp), tint = c.red)
        }
        app.waylink.ui.route.AttentionChip(s, live) { act.open(it) }
        // .preview-bar sits 26 under the header and 14 above the bike card
        live.scenario?.let { Spacer(Modifier.height(26.dp)); app.waylink.ui.components.PreviewBar(it, act::exitPreview) }

        Spacer(Modifier.height(if (live.scenario != null) 14.dp else 28.dp))
        BikeCard(s, live, f, good, verdict.ok, act)

        if (!good) {
            Spacer(Modifier.height(12.dp))
            Hero(verdict, line, fs.onReserve, act::logbook)
        }

        // Do these first / Coming up (.section after the hero: 22; after the bike card alone: 34)
        Spacer(Modifier.height(if (good) 34.dp else 22.dp))
        SectionHead(if (urgent.isNotEmpty()) "Do these first" else "Coming up") { LinkText("See all", act::logbook) }
        Row(
            Modifier.bleed(20).fitScrollX().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shown = (urgent.ifEmpty { list }).take(4)
            // cards in the row share a height: 160 when any shows its gauge, 140 when all are on track (web flex stretch)
            val tall = shown.any { it.st.status != Due.OK }
            shown.forEach { UpCard(it, f, tall) { act.service(it.item.id) } }
        }

        if (missed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MissedBanner(missed.size, missed.last().name, act::alerts)
        }

        // Readings
        Spacer(Modifier.height(34.dp))
        SectionHead("Readings", help = act::readingsHelp) {
            Txt(s.model.series, T.eyebrow, c.ink3)
        }
        Readings(s, live, f, fs, act)

        // Shortcuts
        Spacer(Modifier.height(34.dp))
        SectionHead("Shortcuts")
        Row(
            Modifier.bleed(20).fitScrollX().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val week = periodStats(v.trips, weekStart()).rides
            Tile("fuel", "Fuel", if (fs.range != null) (if (fs.onReserve) Tone.BAD else Tone.OK) else null,
                if (fs.range != null) "≈${f.num(fs.range)} km left" else "No tanks yet",
                List(6) { if (it < (fs.bars ?: 0)) Tone.NEUTRAL else null }, act::fuel)
            Tile("wrench", "Logbook", if (verdict.over > 0) Tone.BAD else if (verdict.soon > 0) Tone.SOON else Tone.OK,
                if (verdict.over + verdict.soon > 0) "${verdict.over + verdict.soon} need care" else "All in order",
                verdict.bar.map { when (it) { Due.OVERDUE -> Tone.BAD; Due.SOON -> Tone.SOON; Due.OK -> Tone.OK } }, act::logbook)
            Tile("trips", "Rides", Tone.OK, "${v.trips.size} logged", List(6) { if (it < minOf(6, week)) Tone.NEUTRAL else null }, act::rides)
            Tile("folder", "Glovebox", null, "Empty", List(4) { null }, act::glovebox)
        }

        Spacer(Modifier.height(18.dp))
        SectionHead("Quick links")
        QuickLinksGrid(s, live, f, s.settings.tileLayout, { act.open("layout:$it") }, act::open)

        Spacer(Modifier.height(24.dp))
        Txt("Everything stays on this phone. No account, no cloud.", T.note.copy(textAlign = TextAlign.Center), c.ink3, Modifier.fillMaxWidth())
    }
    StickyBar(scroll, head)
    }
}

/** Lets a scrolling row run to the screen edges past the page's [dp] side padding, like the web's margin: 0 -20px. */
private fun Modifier.bleed(dp: Int): Modifier = this.layout { measurable, constraints ->
    val extra = (dp * 2).dp.roundToPx()
    val w = constraints.maxWidth + extra
    val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, p.height) { p.place(-(extra / 2), 0) }
}

@Composable
private fun DemoPill() {
    val c = W.c
    val t = rememberInfiniteTransition(label = "demo")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Txt("Demo mode", T.subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), c.ink3)
        Dot(c.ink, Modifier.alpha(a), 8.dp)
    }
}

@Composable
private fun BikeCard(s: AppState, live: Live, f: Fmt, good: Boolean, okCount: Int, act: TodayActions) {
    val c = W.c
    val v = s.vehicle
    Column(Modifier.fillMaxWidth().card(24.dp)) {
        Row(
            Modifier.fillMaxWidth().pressable(label = "Switch vehicle", onClick = act::garage).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BikeArt(Modifier.size(96.dp), 16.dp, s.vehicle.colour, s.vehicle.photo)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(vehicleTitle(s), T.cardTitle, c.ink, Modifier.padding(end = 36.dp), maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Txt("${if (v.nickname.isNotBlank()) modelName(s, s.model) else s.model.series} · ${f.short(v.odo)} km", T.row, c.ink2, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Txt(v.plate.ifBlank { "No number plate" }.uppercase(), T.plate, c.ink3, maxLines = 1)
            }
            Box(Modifier.align(Alignment.Top).size(30.dp).clip(CircleShape).background(c.inkA(0.06f)), contentAlignment = Alignment.Center) {
                Ic("updown", size = 16.dp, tint = c.ink2)
            }
        }
        Hairline()
        ConnRow(s, live, good, okCount, act)
        Hairline()
        WeatherRow(live.weather, act::weather)
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(W.c.line))

/** The compact link row inside the bike card (connCard in the web app). */
@Composable
private fun ConnRow(s: AppState, live: Live, good: Boolean, okCount: Int, act: TodayActions) {
    // web connCard, compact: title and line by link state; the button is Ride, Pair, Connect, or Cancel/Stop.
    val c = W.c
    val st = live.link
    val connected = st == LinkState.CONNECTED
    val known = s.vehicle.bike.id != null
    val name = vehicleTitle(s)
    val (title, sub) = when (st) {
        LinkState.CONNECTED -> "Connected" to (if (live.simulated) "$name · simulated" else "$name · ${live.linkName ?: s.vehicle.bike.name ?: "Bike cluster"}")
        LinkState.CONNECTING -> "Connecting" to "Linking with $name"
        LinkState.SCANNING -> "Pick your bike" to "Choose it in the Bluetooth list"
        LinkState.RECONNECTING -> "Searching" to "Switch on the ignition. It links by itself."
        LinkState.IDLE -> if (known) "Not connected" to "$name · paired" else "No bike linked" to "Pair once, then it’s automatic"
    }
    val busy = st == LinkState.CONNECTING || st == LinkState.SCANNING || st == LinkState.RECONNECTING
    val t = if (good) "Road ready" else title
    val sb = if (good) (if (st == LinkState.IDLE) title else "$title · $sub") else sub
    // .dot.a.pulse while linking or searching
    val pulse = if (busy && !app.waylink.ui.theme.Motion.still) androidx.compose.animation.core.rememberInfiniteTransition(label = "cc")
        .animateFloat(1f, 0.35f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "cc").value else 1f
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).pressable(label = "Bike link details", onClick = act::pair), verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                if (good) "ok" else "signal", size = 36.dp, radius = 10.dp, iconSize = 19.dp,
                bg = if (good) c.tintSage else c.inkA(0.06f), tint = if (good) c.sageInk else c.ink2,
            ) {
                Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(15.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
                    Dot(if (connected) c.sage else if (busy) c.amber.copy(alpha = pulse) else c.ink4, size = 11.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(t, T.rowTitle, c.ink, maxLines = 1)
                Txt(sb, T.rowSub, c.ink3, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            connected -> Btn("Ride", act::ride, kind = BtnKind.ACCENT, icon = "speed", small = true, compact = true)
            busy -> Btn(if (st == LinkState.CONNECTING) "Cancel" else "Stop", act::cancelLink, kind = BtnKind.LINE, small = true, compact = true)
            known -> Btn("Connect", act::reconnect, kind = BtnKind.INK, icon = "bt", small = true, compact = true)
            else -> Btn("Pair", act::pairNew, kind = BtnKind.INK, icon = "bt", small = true, compact = true)
        }
    }
}

@Composable
private fun WeatherRow(w: Weather.State, onClick: () -> Unit) {
    val c = W.c
    val d = when (w) { is Weather.State.Ready -> w.data; is Weather.State.Loading -> w.last; else -> null }
    var ic = "wxPart"
    var temp = ""
    val sub: String
    if (d != null) {
        val rain = d.hours.maxOfOrNull { it.rain } ?: 0
        val temps = d.hours.mapNotNull { it.temp }
        ic = Weather.wmo(d.code, d.day).second
        temp = "${d.temp.roundToInt()}°"
        sub = "${d.place} · " + when {
            rain >= 20 -> "$rain% rain"
            temps.isNotEmpty() -> "${temps.min().roundToInt()}–${temps.max().roundToInt()}° today"
            else -> "feels ${d.feels.roundToInt()}°"
        }
    } else sub = when (w) {
        Weather.State.Need -> "Add weather for your rides"
        is Weather.State.Error -> "Weather offline"
        else -> "Checking the weather…"
    }
    Row(
        Modifier.fillMaxWidth().pressable(label = "Weather details", onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ic(ic, size = 15.dp, tint = c.ink2)
        if (temp.isNotEmpty()) Txt(temp, T.row.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), c.ink)
        Txt(sub, T.row, c.ink3, Modifier.weight(1f), maxLines = 1)
        Ic("chev", size = 17.dp, tint = c.ink4)
    }
}

@Composable
private fun UpCard(r: ServiceRow, f: Fmt, tall: Boolean, onClick: () -> Unit) {
    val c = W.c
    val st = r.st
    val over = st.status == Due.OVERDUE
    val (value, unit) = when {
        over && st.overdueKm > 0 -> f.short(st.overdueKm) to "km"
        over -> f.short(st.overdueDays.toDouble()) to (if (st.overdueDays == 1) "day" else "days")
        st.byTime || st.remKm == null -> f.short(st.remDays?.toDouble()) to (if (st.remDays == 1) "day" else "days")
        else -> f.short(st.remKm) to "km"
    }
    val (tag, tagFg, tagBg) = when (st.status) {
        Due.OVERDUE -> Triple("Overdue", c.red, c.tintRed)
        Due.SOON -> Triple("Due soon", c.ink2, c.inkA(0.06f))
        Due.OK -> Triple("On track", c.ink3, c.raised)
    }
    Column(
        Modifier.width(310.dp).height(if (tall) 160.dp else 140.dp).card(20.dp).pressable(label = r.item.name, onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(r.item.icon)
            Spacer(Modifier.weight(1f))
            Txt(tag, T.chip, tagFg, Modifier.clip(RoundedCornerShape(999.dp)).background(tagBg).padding(horizontal = 9.dp, vertical = 3.dp))
        }
        Spacer(Modifier.weight(1f))
        BigValue(value, unit + if (over) " late" else " left", 30f)
        Spacer(Modifier.height(6.dp))
        Txt(r.item.name, T.row.copy(fontSize = 14.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), c.ink2, maxLines = 1)
        if (st.status != Due.OK) {
            Spacer(Modifier.height(10.dp))
            if (app.waylink.ui.theme.Looks.progress != "gauge") app.waylink.ui.components.ProgressAlt(st, app.waylink.ui.theme.Looks.progress, showEnds = false, labelSize = 9.5f)
            else ServiceGauge(if (over) 0 else max(1, ((1 - st.frac) * 8).roundToInt()))
        }
    }
}

@Composable
private fun MissedBanner(count: Int, last: String, onClick: () -> Unit) {
    val c = W.c
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = T.big(20f).fontFamily, fontSize = 20.sp, fontWeight = T.big(20f).fontWeight, letterSpacing = T.big(20f).letterSpacing)) {
            append(if (count > 99) "99+" else count.toString())
        }
        append(" missed · $last")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tintRed).pressable(label = "$count missed calls", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ic("missed", size = 19.dp, tint = c.red)
        Txt(text, T.lead.copy(lineHeight = 21.7.sp), c.ink, Modifier.weight(1f), maxLines = 1)
        Ic("chev", size = 17.dp, tint = c.ink4)
    }
}

@Composable
private fun Readings(s: AppState, live: Live, f: Fmt, fs: app.waylink.core.FuelStats, act: TodayActions) {
    val c = W.c
    val v = s.vehicle
    val ctx = LocalContext.current
    val battery = remember { batteryPercent(ctx) }
    Column(Modifier.fillMaxWidth().card(20.dp).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Odometer", T.eyebrow, c.ink3, Modifier.weight(1f))
            BigValue(f.big(kotlin.math.floor(v.odo)), "km", 48f)
        }
        Hairline()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Cell("Trip A", Modifier.weight(1f), { act.trip("A") }, first = true) { BigValue(f.tripv(v.tripA.km), "km", 27f) }
            VLine()
            Cell("Trip B", Modifier.weight(1f), { act.trip("B") }) { BigValue(f.tripv(v.tripB.km), "km", 27f) }
            VLine()
            Cell("Speed", Modifier.weight(1f)) { BigValue(f.num(live.speed.toDouble()), "km/h", 27f) }
        }
        Hairline()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Cell("Fuel", Modifier.weight(1f), act::fuel, first = true) { FuelBars(fs.bars, fs.onReserve, Modifier.padding(top = 2.dp)) }
            VLine()
            Cell("Range", Modifier.weight(1f), act::fuel) { BigValue(if (fs.range != null) "≈" + f.num(fs.range) else "—", "km", 27f) }
            VLine()
            if (live.engine != null) Cell("Engine", Modifier.weight(1f)) { BigValue(live.engine.toString(), "°C", 27f) }
            else Cell("Phone", Modifier.weight(1f)) { BigValue(battery?.toString() ?: "—", "%", 27f) }
        }
    }
}

@Composable
private fun VLine() = Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 14.dp).background(W.c.line))

@Composable
private fun Cell(label: String, modifier: Modifier, onClick: (() -> Unit)? = null, first: Boolean = false, value: @Composable () -> Unit) {
    Column(
        modifier.then(if (onClick != null) Modifier.pressable(label = label, onClick = onClick) else Modifier)
            .padding(start = if (first) 0.dp else 14.dp, top = 15.dp, bottom = 14.dp),
    ) {
        Txt(label, T.eyebrow, W.c.ink3)
        Spacer(Modifier.height(8.dp))
        value()
    }
}

@Composable
private fun Tile(icon: String, name: String, dot: Tone?, status: String, bar: List<Tone?>, onClick: () -> Unit) {
    val c = W.c
    Column(Modifier.width(152.dp).height(160.dp).card(20.dp).pressable(label = name, onClick = onClick).padding(16.dp)) {
        IconTile(icon, size = 32.dp, radius = 12.dp, iconSize = 17.dp, bg = c.inkA(0.07f))
        Spacer(Modifier.height(10.dp))
        Txt(name, T.tileTitle, c.ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Dot(when (dot) { Tone.BAD -> c.red; Tone.SOON -> c.amber; Tone.OK -> c.sage; else -> c.ink4 })
            Txt(status, T.rowSub.copy(fontSize = 13.5.sp, lineHeight = 18.9.sp), c.ink2, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Segs(bar, height = 4.dp, gap = 3.dp)
    }
}

private fun batteryPercent(ctx: Context): Int? =
    (ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
