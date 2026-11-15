package app.waylink.ui.shell

import app.waylink.ui.components.fitScrollX
import app.waylink.ui.components.fitScrollY
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.R
import app.waylink.core.AppState
import app.waylink.core.Due
import app.waylink.core.Protocol
import app.waylink.core.ServiceStatus
import app.waylink.data.AppIcon
import app.waylink.data.AppViewModel
import app.waylink.data.Live
import app.waylink.data.Weather
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.LoopWordmark
import app.waylink.ui.components.ProgressAlt
import app.waylink.ui.components.ServiceGauge
import app.waylink.ui.components.Switch
import app.waylink.ui.components.Txt
import app.waylink.ui.components.freshSegs
import app.waylink.ui.components.pressable
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.theme.Fonts
import app.waylink.ui.theme.Looks
import app.waylink.ui.theme.Mono
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The appearance pickers and developer sheets from Settings (web views.js: pick-brand, pick-font, pick-progress,
 * log, protocol) and the two link prompts (pair-intro, auto-ask). Null if [key] isn't one of them.
 */
fun devSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav, host: ExtraHost): SheetSpec? = when (key) {
    "weather" -> {
        val w = live.weather
        val d = when (w) { is Weather.State.Ready -> w.data; is Weather.State.Loading -> w.last; else -> null }
        val title = when { d != null -> null; w is Weather.State.Error -> "Weather offline"; w is Weather.State.Need -> "Weather for your ride"; else -> "Weather" }
        SheetSpec(title ?: "Weather", if (d != null) d.place.ifBlank { "Near you" } else null) { WeatherBody(w, d, vm, host) }
    }
    "pick-brand", "pick-icons" -> SheetSpec("Icons", "Appearance") { BrandPicker(s, vm) }
    "pick-font", "pick-body" -> SheetSpec("Fonts", "Appearance") { FontPicker(s, vm) }
    "pick-progress" -> SheetSpec("Progress style", "Appearance") { ProgressPicker(s, vm) }
    "log" -> SheetSpec("Connection log", "Developer") { LogBody(live) }
    "protocol" -> SheetSpec("Protocol", "Developer") { ProtocolBody() }
    "pair-intro" -> SheetSpec("No bike linked", "Your bike") {
        Spacer(Modifier.height(8.dp))
        Txt("Pair once and Waylink reconnects by itself whenever the ignition is on. Turns, callers and the time then show on your display.", T.sheetLead, W.c.ink2)
        Spacer(Modifier.height(20.dp))
        Btn("Pair your bike", { nav.open("pick") }, Modifier.fillMaxWidth(), icon = "bt")
        Spacer(Modifier.height(8.dp))
        Btn("Try the simulated bike", { vm.simOn(); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "bolt")
        Spacer(Modifier.height(8.dp))
        Btn("Not now", nav::close, Modifier.fillMaxWidth(), kind = BtnKind.GHOST)
    }
    "auto-ask" -> SheetSpec("Start rides by themselves?", "Linked") {
        val st = s.settings
        Spacer(Modifier.height(8.dp))
        Txt("Waylink can record a ride as soon as the bike links, and open ride mode once you're moving. Both are off unless you turn them on.", T.sheetLead, W.c.ink2)
        Spacer(Modifier.height(16.dp))
        ListCard {
            ListRow("Record rides by themselves", "When the bike links", icon = "route", trailing = { Switch(st.autoRecord, "Record rides by themselves", { vm.toggleSetting("autoRecord") }) })
            Hairline(66.dp)
            ListRow("Open ride mode by itself", "Above 15 km/h", icon = "speed", trailing = { Switch(st.autoRide, "Open ride mode by itself", { vm.toggleSetting("autoRide") }) })
        }
        Spacer(Modifier.height(20.dp))
        Btn("Done", nav::close, Modifier.fillMaxWidth())
        Txt("You can change these later in Settings, under Riding.", T.row.copy(fontSize = 13.5.sp, lineHeight = 21.sp, textAlign = TextAlign.Center), W.c.ink3, Modifier.fillMaxWidth().padding(top = 10.dp))
    }
    else -> null
}

/** The rendered app icon for a style and accent (img/logos), as shown in the picker. */
fun appIconRes(key: String): Int = when (key) {
    "chrome" -> R.drawable.appicon_chrome
    "retro" -> R.drawable.appicon_retro
    "leather" -> R.drawable.appicon_leather
    "ascii" -> R.drawable.appicon_ascii
    "wool" -> R.drawable.appicon_wool
    "oil" -> R.drawable.appicon_oil
    "stitch" -> R.drawable.appicon_stitch
    "classic_rust" -> R.drawable.appicon_classic_rust
    "classic_blue" -> R.drawable.appicon_classic_blue
    "classic_slate" -> R.drawable.appicon_classic_slate
    "classic_amber" -> R.drawable.appicon_classic_amber
    "classic_mint" -> R.drawable.appicon_classic_mint
    else -> R.drawable.appicon_classic_wine
}

@Composable
private fun BrandPicker(s: AppState, vm: AppViewModel) {
    val c = W.c
    var replay by remember { mutableIntStateOf(0) }
    Spacer(Modifier.height(12.dp))
    // .brand-final: the LOOP wordmark in a card; tap to play it again
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(20.dp))
            .pressable(label = "Play the logo animation", scale = 0.98f) { replay++; vm.playSplashSound() }.padding(vertical = 26.dp),
        contentAlignment = Alignment.Center,
    ) { LoopWordmark(40f, animate = true, key = replay) }

    Spacer(Modifier.height(24.dp))
    Txt("App icon", T.msT, c.ink)
    Spacer(Modifier.height(8.dp))
    Txt("On your Home Screen. Android can take a moment to show the new one.", T.rowSub.copy(fontSize = 14.sp, lineHeight = 21.sp), c.ink3)
    Spacer(Modifier.height(12.dp))
    // .appicons: two rows of four, 64 px tiles, the chosen one ringed in ink
    val cur = s.settings.iconStyle.takeIf { k -> AppIcon.STYLES.any { it.first == k } } ?: "classic"
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AppIcon.STYLES.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (k, nm) ->
                    val on = cur == k
                    Column(
                        Modifier.weight(1f).semantics { selected = on }.pressable(label = "$nm app icon", role = Role.RadioButton, scale = 0.94f) { vm.setAppIcon(k) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val shape = RoundedCornerShape(15.dp)
                        Box(Modifier.size(64.dp + 11.dp), contentAlignment = Alignment.Center) {
                            if (on) Box(Modifier.size(64.dp + 11.dp).border(2.5.dp, c.ink, RoundedCornerShape(19.dp)))
                            Image(
                                painterResource(appIconRes(AppIcon.key(k, s.settings.accent))), null,
                                Modifier.size(64.dp).shadow(5.dp, shape, ambientColor = Color(0x66000000), spotColor = Color(0x66000000)).clip(shape),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Txt(nm, T.rowSub.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), if (on) c.ink else c.ink2, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Txt("Icon style", T.msT, c.ink)
    Spacer(Modifier.height(12.dp))
    // .icoopts: one card of rows, a radio and the name on the left, four sample icons on the right
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(20.dp))) {
        Looks.ICONS.forEachIndexed { i, (k, nm) ->
            if (i > 0) Hairline()
            val on = (s.settings.icons.takeIf { it == "duo" || it == "fill" } ?: "line") == k
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { selected = on }.pressable(label = "$nm icons", role = Role.RadioButton, scale = 1f) { vm.setIconLook(k) }
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Radio(on)
                Spacer(Modifier.width(10.dp))
                Txt(nm, T.body.copy(fontSize = 15.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.SemiBold), c.ink, Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("fuel", "wrench", "bell", "bike").forEach { Ic(it, size = 22.dp, tint = c.ink, look = k) }
                }
            }
        }
    }
}

/** A radio dot: an ink-4 ring, filled to a thick ink ring when on (.icoopt .nm::before). */
@Composable
private fun Radio(on: Boolean) {
    val c = W.c
    Box(Modifier.size(18.dp).clip(CircleShape).border(if (on) 5.5.dp else 1.5.dp, if (on) c.ink else c.ink4, CircleShape))
}

private val TITLE_WEIGHT = mapOf("system" to 700, "onest" to 600, "geist" to 600, "garamond" to 500, "crimson" to 500, "fraunces" to 600)

@Composable
private fun FontPicker(s: AppState, vm: AppViewModel) {
    val c = W.c
    val st = s.settings
    Spacer(Modifier.height(20.dp))
    Txt("Titles", T.msT, c.ink)
    Spacer(Modifier.height(12.dp))
    Txt("Sans serif", T.eyebrow, c.ink3)
    Spacer(Modifier.height(8.dp))
    FontTiles(listOf("system", "onest", "geist"), st.font, { Fonts.titleFamily(it) }, { FontWeight(TITLE_WEIGHT[it] ?: 600) }, "Ag", { Fonts.TITLE_NAMES[it] ?: it }) { vm.setFont(it) }
    Spacer(Modifier.height(16.dp))
    Txt("Serif", T.eyebrow, c.ink3)
    Spacer(Modifier.height(8.dp))
    FontTiles(listOf("garamond", "crimson", "fraunces"), st.font, { Fonts.titleFamily(it) }, { FontWeight(TITLE_WEIGHT[it] ?: 500) }, "Ag", { Fonts.TITLE_NAMES[it] ?: it }) { vm.setFont(it) }
    Spacer(Modifier.height(32.dp))
    Txt("Body text", T.msT, c.ink)
    Spacer(Modifier.height(12.dp))
    FontTiles(Fonts.BODIES.map { it.first }, st.bodyFont, { Fonts.bodyFamily(it) }, { FontWeight(if (it == "system") 400 else 450) }, "Aa", { k -> Fonts.BODIES.first { it.first == k }.second }) { vm.setBodyFont(it) }
}

/** .ftiles: three tiles across, a big sample in the face and its name; the chosen one is inked. */
@Composable
private fun FontTiles(
    keys: List<String>, cur: String, family: (String) -> androidx.compose.ui.text.font.FontFamily, weight: (String) -> FontWeight,
    sample: String, name: (String) -> String, onPick: (String) -> Unit,
) {
    val c = W.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { k ->
            val on = cur == k
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).height(92.dp).clip(shape).background(if (on) c.ink else c.raised)
                    .then(if (on) Modifier else Modifier.border(1.dp, c.line, shape))
                    .semantics { selected = on }.pressable(label = name(k), role = Role.RadioButton, scale = 0.97f) { onPick(k) }
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt(sample, TextStyle(fontFamily = family(k), fontWeight = weight(k), fontSize = 34.sp, lineHeight = 34.sp), if (on) c.bg else c.ink)
                Txt(name(k), T.rowSub.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), if (on) c.bg.copy(alpha = 0.7f) else c.ink3, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProgressPicker(s: AppState, vm: AppViewModel) {
    val c = W.c
    val now = System.currentTimeMillis()
    val ok = ServiceStatus(null, null, now + 40 * 86_400_000L, 40, 0.0, 0, Due.OK, 0.35, false)
    val soon = ServiceStatus(null, null, now + 6 * 86_400_000L, 6, 0.0, 0, Due.SOON, 0.86, false)
    val cur = s.settings.progress
    Spacer(Modifier.height(8.dp))
    Txt("How service jobs show what’s left.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(16.dp))
    // .progs: two-up tiles, each previewing an on-track and a due-soon job
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Looks.PROGRESS.chunked(2).forEach { row ->
            // Both tiles in a row share one height, the previews filling it and the names lined up along the bottom.
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, nm) ->
                    val on = cur == k
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(shape).background(c.raised).border(if (on) 1.5.dp else 1.dp, if (on) c.ink else c.line, shape)
                            .semantics { selected = on }.pressable(label = nm, role = Role.RadioButton, scale = 0.97f) { vm.setProgress(k) }.padding(14.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        ) {
                            StylePreview(ok, k)
                            StylePreview(soon, k)
                        }
                        Spacer(Modifier.height(12.dp))
                        Txt(nm, T.body.copy(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold), c.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePreview(st: ServiceStatus, style: String) {
    val c = W.c
    if (style != "gauge") { ProgressAlt(st, style, showEnds = true, labelSize = 9f); return }
    val ef = T.eyebrow.copy(fontSize = 9f.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp, lineHeight = 9.sp)
    val on = if (st.status == Due.SOON) c.amber else c.inkA(0.30f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Txt("E", ef, c.ink3)
        Spacer(Modifier.width(6.dp))
        ServiceGauge(freshSegs(st, 10), 10, Modifier.weight(1f), height = 5.dp, onColor = on)
        Spacer(Modifier.width(6.dp))
        Txt("F", ef, c.ink3)
    }
}

@Composable
private fun LogBody(live: Live) {
    val c = W.c
    if (live.linkLog.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Txt("Nothing yet.", T.sheetLead, c.ink2)
        return
    }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.UK) }
    Spacer(Modifier.height(16.dp))
    Column {
        live.linkLog.asReversed().take(80).forEachIndexed { i, (t, msg) ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(fmt.format(Date(t)), T.plate.copy(letterSpacing = 0.sp), c.ink4)
                Spacer(Modifier.width(12.dp))
                val warn = msg.startsWith("!")
                Txt(msg.removePrefix("!"), T.row.copy(fontSize = 13.sp, lineHeight = 19.5.sp), if (warn) c.amber else c.ink, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProtocolBody() {
    val c = W.c
    Spacer(Modifier.height(8.dp))
    Txt("A byte-for-byte port of PulsarNav’s packet builder. Writes go out one at a time.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(16.dp))
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.raised).border(1.dp, c.line, shape).padding(horizontal = 18.dp, vertical = 2.dp)) {
        listOf(
            "Service" to Protocol.Uuid.SERVICE,
            "Turn-by-turn · ${Protocol.TBT} B" to Protocol.Uuid.TBT,
            "Status · ${Protocol.GENERAL} B, +${Protocol.LEGACY} B legacy" to Protocol.Uuid.GENERAL,
            "Missed call · ${Protocol.MISSED} B" to Protocol.Uuid.MISSED_CALL,
            "Alerts · ${Protocol.ALERT} B" to Protocol.Uuid.ALERTS,
            "Handlebar · read every 0.7 s" to Protocol.Uuid.BUTTONS,
        ).forEachIndexed { i, (a, b) ->
            if (i > 0) Hairline()
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Txt(a, T.rowSub.copy(fontSize = 14.sp, lineHeight = 21.sp), c.ink3)
                Spacer(Modifier.height(4.dp))
                Txt(b, TextStyle(fontFamily = Mono, fontSize = 11.5.sp, lineHeight = 17.sp), c.ink)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Txt("Turn frame", T.eyebrow, c.ink3)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().clip(shape).background(c.raised).border(1.dp, c.line, shape).padding(horizontal = 18.dp, vertical = 12.dp)) {
        listOf(
            "0" to "type, step unit, PM flag", "1" to "arrow id, +32 to blink under 100 m", "2–5" to "step distance",
            "6–7" to "ETA minute, hour and exit", "8–11" to "total distance", "12" to "total unit, GPS on", "14–45" to "street name", "47" to "checksum",
        ).forEach { (b, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(b, TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 24.3.sp), c.ink)
                Spacer(Modifier.width(6.dp))
                Txt(t, T.row.copy(fontSize = 13.5.sp, lineHeight = 24.3.sp), c.ink)
            }
        }
    }
}

/** The weather sheet (web weatherSheet): now, a riding hint, three readings, the next 12 hours. */
@Composable
private fun WeatherBody(w: Weather.State, d: Weather.Data?, vm: AppViewModel, host: ExtraHost) {
    val c = W.c
    if (d == null && w !is Weather.State.Need && w !is Weather.State.Error) { WeatherSkeleton(); return }
    if (d == null) {
        Spacer(Modifier.height(8.dp))
        Txt(when (w) {
            is Weather.State.Need -> "Add your city in Profile, or share your location, to see rain, heat and wind for your ride."
            is Weather.State.Error -> w.msg.ifBlank { "Couldn’t reach the weather service." }
            else -> "Checking the sky…"
        }, T.sheetLead, c.ink2)
        if (w is Weather.State.Error) { Spacer(Modifier.height(16.dp)); Btn("Try again", vm::refreshWeather, Modifier.fillMaxWidth(), icon = "refresh") }
        if (w is Weather.State.Need || w is Weather.State.Error) {
            // .btns: "Use my location" beside "Set city"
            val locating = vm.live.collectAsState().value.locating
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (locating) "Finding you…" else "Use my location", { if (!locating) host.withLocation { vm.weatherHere() } }, Modifier.weight(1f), kind = BtnKind.LINE, small = true, icon = "pin")
                Btn("Set city", { host.page("profile") }, Modifier.weight(1f), kind = BtnKind.LINE, small = true, icon = "profile")
            }
        }
        return
    }
    val (cond, ic) = Weather.wmo(d.code, d.day)
    val hint = Weather.rideHint(d)
    val warn = d.code >= 61 || Regex("Rain likely|Thunder|Fog|Gusty|Hot").containsMatchIn(hint)
    // .wx-big: a sage icon tile, the temperature at 52, the condition under it
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.tintSage), contentAlignment = Alignment.Center) { Ic(ic, size = 22.dp, tint = c.sageInk) }
        Spacer(Modifier.width(16.dp))
        Column {
            Txt("${Math.round(d.temp)}°", T.big(52f), c.ink)
            Spacer(Modifier.height(4.dp))
            Txt(cond, T.body.copy(fontSize = 17.sp, lineHeight = 25.5.sp, fontWeight = FontWeight.Medium), c.ink2)
        }
    }
    // .wx-hint: amber when it matters, a soft ink tint when all is well
    Row(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (warn) c.tintAmber else c.inkA(0.06f)).padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        val fg = if (warn) androidx.compose.ui.graphics.lerp(c.amber, c.ink, 0.2f) else c.ink2
        Ic(if (warn) "drops" else "check", Modifier.padding(top = 2.dp), size = 16.dp, tint = fg)
        Spacer(Modifier.width(8.dp))
        Txt(hint, T.row.copy(fontSize = 14.5.sp, lineHeight = 20.3.sp), fg)
    }
    // .stat3: feels like, wind, rain chance
    val rainMax = d.hours.maxOfOrNull { it.rain } ?: 0
    Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(20.dp))) {
        listOf(Triple("Feels like", "${Math.round(d.feels)}°", ""), Triple("Wind", "${Math.round(d.wind)}", "km/h"), Triple("Rain, 12 h", "$rainMax", "%")).forEachIndexed { i, (k, v, u) ->
            if (i > 0) Box(Modifier.width(0.5.dp).fillMaxHeight().background(c.line))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Txt(k, T.eyebrow, c.ink3, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                app.waylink.ui.components.BigValue(v, u.ifEmpty { null }, 30f)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Txt("Next 12 hours", T.eyebrow, c.ink3)
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().fitScrollX().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val hf = remember { SimpleDateFormat("h a", Locale.ENGLISH) }
        d.hours.take(12).forEachIndexed { i, h ->
            Column(
                Modifier.width(58.dp).clip(RoundedCornerShape(16.dp)).background(c.raised).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Txt(if (i == 0) "Now" else hf.format(Date(h.ts)).lowercase(), T.eyebrow.copy(fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.04.em), c.ink3, maxLines = 1)
                Ic(Weather.wmo(h.code, h.day).second, size = 20.dp, tint = c.ink2)
                Txt(h.temp?.let { "${Math.round(it)}°" } ?: "–", T.body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), c.ink)
                Txt(if (h.rain >= 20) "${h.rain}%" else "", TextStyle(fontFamily = Mono, fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = if (h.rain >= 50) FontWeight.SemiBold else FontWeight.Normal), if (h.rain >= 50) Color(0xFF3B6FD8) else c.ink3)
            }
        }
    }
    Hint("Updated ${app.waylink.core.Fmt().ago(d.ts)} · Open-Meteo")
    Spacer(Modifier.height(12.dp))
    Btn(if (w is Weather.State.Loading) "Refreshing…" else "Refresh", vm::refreshWeather, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "refresh")
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().pressable(label = "Change place", scale = 1f) { host.page("profile") }.padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic("pin", size = 15.dp, tint = c.ink3)
        Spacer(Modifier.width(8.dp))
        Txt("Showing ${d.place.ifBlank { "your location" }}", T.row.copy(fontSize = 13.5.sp), c.ink3, Modifier.weight(1f), maxLines = 1)
        Txt("Change", T.row.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), c.ink)
    }
}

/** The weather sheet's shape while the first reading loads: the same blocks, softly pulsing, so the sheet opens full size. */
@Composable
private fun WeatherSkeleton() {
    val c = W.c
    val a = if (app.waylink.ui.theme.Motion.still) 1f else androidx.compose.animation.core.rememberInfiniteTransition(label = "sk")
        .animateFloat(1f, 0.45f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "sk").value
    val fill = c.inkA(0.07f * a)
    @Composable fun bar(w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, r: androidx.compose.ui.unit.Dp = 8.dp, m: Modifier = Modifier) =
        Box(m.width(w).height(h).clip(RoundedCornerShape(r)).background(fill))
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Loading the weather" }) {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            bar(44.dp, 44.dp, 12.dp)
            Spacer(Modifier.width(16.dp))
            Column { bar(96.dp, 52.dp, 10.dp); Spacer(Modifier.height(8.dp)); bar(130.dp, 18.dp) }
        }
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(fill))
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(104.dp).clip(RoundedCornerShape(20.dp)).background(fill))
        Spacer(Modifier.height(20.dp))
        bar(110.dp, 14.dp, 4.dp)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) { bar(58.dp, 112.dp, 16.dp) }
        }
        Box(Modifier.padding(top = 24.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(fill))
        Spacer(Modifier.height(30.dp))
    }
}
