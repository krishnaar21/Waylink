package app.waylink.ui.shell

import androidx.compose.animation.AnimatedVisibility
import app.waylink.ui.components.rowPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Catalog
import app.waylink.core.Fmt
import app.waylink.core.modelName
import app.waylink.core.needsYou
import app.waylink.core.shortName
import app.waylink.core.vehicleTitle
import app.waylink.data.LinkState
import app.waylink.data.Live
import app.waylink.ui.components.Avatar
import app.waylink.ui.components.BikeArt
import app.waylink.ui.components.CircleBtn
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Page
import app.waylink.ui.components.PageHead
import app.waylink.ui.components.SectionGap
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.Switch
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.Accent
import app.waylink.ui.theme.Serif
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** What Settings can do. Sheet keys follow the web app ("vehicle:<id>", "specs", "backup"). */
interface SettingsActions {
    fun sheet(key: String)
    fun page(key: String)
    fun help(key: String)
    fun toggle(key: String)
    fun theme(key: String)
    fun accent(key: String)
    fun sim(on: Boolean)
    fun soon(what: String)
}

val RIDE_CHARTS = listOf("Calendar", "Km per week", "Km by weekday")

/** Bluetooth state in a word, as on the vehicle card. */
fun connText(s: AppState, live: Live) = when {
    live.link == LinkState.CONNECTED -> if (live.simulated) "Simulated" else "Connected"
    s.vehicle.bike.id != null -> "Paired"
    else -> "Not paired"
}

/** The Settings tab, a port of settings() in web-v0/js/views.js. */
@Composable
fun SettingsScreen(s: AppState, live: Live, act: SettingsActions, bottom: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val v = s.vehicle
    val st = s.settings
    val conn = connText(s, live)
    Page(bottom) { androidx.compose.runtime.CompositionLocalProvider(app.waylink.ui.components.LocalBigRowIcons provides true) {
        PageHead("Settings", "Profile, bikes and look")
        ProfileCard(s) { act.page("profile") }

        SectionGap()
        SectionHead("Garage", right = { if (s.vehicles.size > 1) Txt("Switch", T.link, c.ink2, Modifier.pressable(label = "Switch vehicle") { act.sheet("garage") }) })
        Column(Modifier.fillMaxWidth().card(20.dp)) {
            Box {
                Row(
                    Modifier.fillMaxWidth().pressable(label = "Switch vehicle") { act.sheet("garage") }.padding(start = 12.dp, end = 58.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // .art.card-art 96 × 96, radius 16; .vc-name 20/23 semibold; .vc-line 14 ink-2; eyebrow 13.5 ink-3
                    BikeArt(Modifier.size(96.dp), 16.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(vehicleTitle(s), T.cardTitle.copy(fontSize = 20.sp, lineHeight = 23.sp), c.ink, maxLines = 2)
                        Spacer(Modifier.height(3.dp))
                        Txt((if (v.nickname.isNotBlank()) modelName(s, s.model) + " · " else "") + v.plate.ifBlank { "No plate" }, T.row, c.ink2, maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        Txt("${f.short(v.odo)} km · $conn", T.eyebrow, c.ink3, maxLines = 1)
                    }
                }
                // .vcard .edit: 36 dp, flat ink 6 %, 42 down and 14 in from the corner
                CircleBtn("edit", "Edit vehicle", { act.sheet("vehicle:${v.id}") }, Modifier.align(Alignment.TopEnd).padding(top = 42.dp, end = 14.dp), size = 36.dp, flat = true, iconSize = 15.dp)
            }
            Hairline()
            ListRow("Spec sheet", "Oil, tyres, chain, plugs", icon = "spec", onClick = { act.sheet("specs") })
            Hairline(66.dp)
            ListRow("Odometer", "Correct it if it drifts", icon = "gauge", end = "${f.num(Math.floor(v.odo))} km", onClick = { act.sheet("odo") })
            Hairline(66.dp)
            ListRow("Bluetooth", "Pair and check the link", icon = "bt", end = conn, onClick = { act.sheet("pair") })
        }

        // .support-card: card, 44 dp heart tile in ink 7 %, bold title, ink-2 line.
        Spacer(Modifier.height(30.dp))
        Row(
            Modifier.fillMaxWidth().card(20.dp).pressable(label = "Support Waylink") { act.sheet("support") }.padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.inkA(0.07f)), contentAlignment = Alignment.Center) {
                Ic("love", size = 22.dp, tint = c.ink)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt("Support Waylink", T.body.copy(fontSize = 16.5.sp, lineHeight = 24.75.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), c.ink)
                Spacer(Modifier.height(2.dp))
                Txt("Chip in if it helps", T.row, c.ink2)
            }
            Spacer(Modifier.width(14.dp))
            Ic("chev", size = 18.dp, tint = c.ink3)
        }

        SectionGap()
        SectionHead("Records & safety", help = { act.help("papers") })
        ListCard {
            val due = needsYou(v)
            ListRow("Logbook", "Reminders and visits", icon = "wrench", end = if (due > 0) "$due due" else "All good", onClick = { act.page("logbook") })
            Hairline(66.dp)
            ListRow("Fuel", "Fill-ups and petrol price", icon = "fuel", end = "${v.fuel.size} fills", onClick = { act.page("fuel") })
            Hairline(66.dp)
            val docs = s.glove.count { it.slot.startsWith(v.id + ":") || it.slot.startsWith("rider:") }
            ListRow("Digital glovebox", "RC, insurance, PUC, licence", icon = "folder", end = "$docs", onClick = { act.sheet("glovebox") })
            Hairline(66.dp)
            val park = v.parking
            ListRow("Parked bike", "Find your way back to it", icon = "pin", end = if (park != null) f.ago(park.ts) else "Not saved", onClick = { act.sheet("parking") })
        }

        SectionGap()
        SectionHead("Riding", help = { act.help("riding") })
        ListCard {
            ListRow("Open ride mode by itself", "Above 15 km/h", icon = "speed", trailing = { Switch(st.autoRide, "Open ride mode by itself", { act.toggle("autoRide") }) })
            Hairline(66.dp)
            ListRow("Record rides by themselves", "When the bike links", icon = "route", trailing = { Switch(st.autoRecord, "Record rides by themselves", { act.toggle("autoRecord") }) })
            Hairline(66.dp)
            ListRow("Keep the screen on", "While in ride mode", icon = "sparkles", trailing = { Switch(st.wakeLock, "Keep the screen on", { act.toggle("wakeLock") }) })
            Hairline(66.dp)
            ListRow("Street names on the bike", "Road name beside the arrow", icon = "eye", trailing = { Switch(st.streetNames, "Street names on the bike", { act.toggle("streetNames") }) })
        }

        SectionGap()
        SectionHead("Look")
        LookCard(s, act)

        SectionGap()
        SectionHead("Data & region", help = { act.help("backup") })
        ListCard {
            ListRow("Country", "Currency and SOS", icon = "globe", end = s.country.name, onClick = { act.sheet("country") })
            Hairline(66.dp)
            ListRow("Backup & restore", "Save, move or bring back your data", icon = "down", onClick = { act.sheet("backup") })
            Hairline(66.dp)
            ListRow(if (s.sample) "Erase sample data" else "Erase everything", icon = "trash", danger = true, onClick = { act.sheet("erase") })
            Hairline(66.dp)
            ListRow("Reset the app", "Like a fresh install", icon = "refresh", danger = true, onClick = { act.sheet("reset") })
        }

        SectionGap()
        SectionHead("Help & about")
        ListCard {
            ListRow("Questions and answers", "Pairing, fuel, weather, privacy", icon = "info", onClick = { act.sheet("faq") })
            Hairline(66.dp)
            ListRow("Report a problem", "Copy your app details and tell us", icon = "flag", onClick = { act.sheet("report") })
            Hairline(66.dp)
            ListRow("About Waylink", "Version $APP_VERSION, credits and licences", icon = "heart", onClick = { act.sheet("about") })
        }

        SectionGap()
        SectionHead("Developer", help = { act.help("developer") })
        ListCard {
            val simOn = live.simulated && live.link == LinkState.CONNECTED
            ListRow("Simulated bike", "Try it all without a bike", icon = "bolt", trailing = { Switch(simOn, "Simulated bike", { act.sim(!simOn) }) })
        }

        Spacer(Modifier.height(36.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt("waylink", T.title.copy(fontSize = 24.sp), c.ink2)
            Spacer(Modifier.height(6.dp))
            Txt("Version $APP_VERSION · MIT licence", T.rowSub, c.ink3)
            Spacer(Modifier.height(4.dp))
            Txt("Not affiliated with Bajaj Auto, OTC, Google or WhatsApp.", T.rowSub.copy(textAlign = TextAlign.Center), c.ink4)
        }
    } }
}

const val APP_VERSION = "1.0"

/** The profile card (.profile-card): avatar, name, and the emergency info folded underneath (.pc-more). */
@Composable
private fun ProfileCard(s: AppState, onOpen: () -> Unit) {
    val c = W.c
    val p = s.profile
    val rows = listOfNotNull(
        p.blood.takeIf { it.isNotBlank() }?.let { Triple("drop", "Blood", it) },
        p.iceName.takeIf { it.isNotBlank() }?.let { Triple("phone", "Emergency contact", it) },
        p.medical.takeIf { it.isNotBlank() }?.let { Triple("hospital", "Medical notes", it) },
    )
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card(20.dp)) {
        Row(Modifier.fillMaxWidth().pressable(label = "Your profile", onClick = onOpen).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatar)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(shortName(p.name).ifEmpty { "Add your name" }, T.cardTitle.copy(lineHeight = 24.15.sp), c.ink, maxLines = 1)
                Txt(if (rows.isNotEmpty()) "Rider profile" else "Licence, blood group, emergency contact", T.row, c.ink3, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Ic("chev", size = 16.dp, tint = c.ink3)
        }
        if (rows.isNotEmpty()) {
            val sum = listOfNotNull(
                p.blood.takeIf { it.isNotBlank() }?.let { "Blood $it" },
                "ICE".takeIf { p.iceName.isNotBlank() },
                "Medical".takeIf { p.medical.isNotBlank() },
            ).joinToString(" · ")
            // summary chevron points down, and up when open (rotate 90 → -90 over .2s)
            val turn by animateFloatAsState(if (open) -90f else 90f, androidx.compose.animation.core.tween(200), label = "pc")
            Hairline()
            Row(
                Modifier.fillMaxWidth().rowPress(label = "Emergency info") { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic("shield", size = 17.dp, tint = c.ink3)
                Spacer(Modifier.width(10.dp))
                Txt("Emergency info", T.row.copy(fontSize = 14.5.sp, lineHeight = 21.75.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), c.ink)
                Spacer(Modifier.width(10.dp))
                Txt(sum, T.rowSub, c.ink3, Modifier.weight(1f), maxLines = 1)
                Spacer(Modifier.width(10.dp))
                Ic("chev", Modifier.rotate(turn), size = 17.dp, tint = c.ink3)
            }
            // .pc-grid: two columns of soft cells, medical notes full width
            AnimatedVisibility(open) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val (wide, half) = rows.partition { it.second == "Medical notes" }
                    half.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { PcCell(it, Modifier.weight(1f).fillMaxHeight()) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    wide.forEach { PcCell(it, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun PcCell(row: Triple<String, String, String>, modifier: Modifier) {
    val c = W.c
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.inkA(0.04f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(row.first, size = 14.dp, tint = c.ink3)
            Spacer(Modifier.width(6.dp))
            Txt(row.second, T.rowSub.copy(fontSize = 12.sp, lineHeight = 18.sp), c.ink3, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Txt(row.third, T.row.copy(fontSize = 14.5.sp, lineHeight = 19.6.sp), c.ink)
    }
}

/** Themes, accents and the ride chart (.themecard). */
@Composable
private fun LookCard(s: AppState, act: SettingsActions) {
    val c = W.c
    Column(Modifier.fillMaxWidth().card(20.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple("auto", "Auto", "Follows phone"),
                    Triple("sketch", "Paper", "Light"),
                    Triple("sketchdark", "Paper dark", "Dark"),
                ).forEach { (key, label, sub) ->
                    val on = s.settings.theme == key
                    Column(
                        Modifier.weight(1f).semantics { selected = on }.pressable(label = label, role = Role.RadioButton, scale = 0.95f) { act.theme(key) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ThemePreview(key, on, Modifier.fillMaxWidth().aspectRatio(1.25f))
                        Spacer(Modifier.height(8.dp))
                        Txt(label, T.row.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), if (on) c.ink else c.ink2, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Txt(sub, T.rowSub.copy(fontSize = 12.sp, lineHeight = 18.sp), c.ink3, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Txt("Accent", T.eyebrow, c.ink3)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Accent.entries.forEach { a ->
                    val on = s.settings.accent == a.key
                    val col = if (c.dark) a.dark else a.light
                    // .accentopt .dot: 32 dp glossy dot with a white inner ring; on, it shrinks to .92 inside a 1.5 dp accent ring
                    val k by animateFloatAsState(if (on) 0.92f else 1f, androidx.compose.animation.core.tween(200, easing = app.waylink.ui.theme.Spring), label = "acc")
                    val density = androidx.compose.ui.platform.LocalDensity.current.density
                    Box(
                        Modifier.size(46.dp).semantics { selected = on }.pressable(label = a.label, role = Role.RadioButton, scale = 0.9f) { act.accent(a.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Box(Modifier.size(36.dp).border(1.5.dp, col, CircleShape))
                        Box(
                            Modifier.size(32.dp).graphicsLayer { scaleX = k; scaleY = k }
                                .shadow(if (on) 0.dp else 1.5.dp, CircleShape).clip(CircleShape)
                                .background(col)
                                .background(androidx.compose.ui.graphics.Brush.radialGradient(
                                    0f to Color.White.copy(alpha = 0.45f), 0.55f to Color.White.copy(alpha = 0f),
                                    center = androidx.compose.ui.geometry.Offset(32f * 0.32f * density, 32f * 0.28f * density), radius = 32f * density,
                                ))
                                .border(2.dp, Color.White.copy(alpha = if (on) 0.7f else 0.55f), CircleShape),
                        )
                    }
                }
            }
        }
        Hairline()
        ListRow("Ride chart", icon = "trips", end = RIDE_CHARTS[s.settings.rideChart.coerceIn(0, 2)], onClick = { act.sheet("pick-ridechart") })
    }
}

/** .themeopt .tp: a tiny page in the theme's colours, a title bar, two cards and an accent pill. */
@Composable
private fun ThemePreview(key: String, on: Boolean, modifier: Modifier) {
    val c = W.c
    val (bg, card, ink) = when (key) {
        "sketchdark" -> Triple(Color(0xFF1E1B17), Color(0xFF2A2621), Color(0xFFEDE5D6))
        else -> Triple(Color(0xFFEEE7DA), Color(0xFFF7F1E6), Color(0xFF2A2620))
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .then(if (on) Modifier.border(2.dp, c.accent, RoundedCornerShape(18.dp)).padding(4.dp) else Modifier.shadow(3.dp, shape, ambientColor = Color(0x59000000), spotColor = Color(0x59000000)))
            .clip(shape).background(bg).border(1.dp, c.line, shape),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth; val h = maxHeight
            Box(Modifier.offset(w * 0.14f, h * 0.13f).size(w * 0.34f, h * 0.07f).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = 0.7f)))
            Box(Modifier.offset(w * 0.14f, h * 0.30f).size(w * 0.72f, h * 0.17f).clip(RoundedCornerShape(6.dp)).background(card))
            Box(Modifier.offset(w * 0.14f, h * 0.53f).size(w * 0.72f, h * 0.17f).clip(RoundedCornerShape(6.dp)).background(card))
            Box(Modifier.offset(w * 0.14f, h * 0.81f).size(w * 0.40f, h * 0.09f).clip(RoundedCornerShape(99.dp)).background(c.accent))
        }
    }
}
