package app.waylink.ui.route

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.Maneuvers
import app.waylink.core.Rehearsal
import app.waylink.core.distText
import app.waylink.core.tShort
import app.waylink.data.AppViewModel
import app.waylink.data.LinkState
import app.waylink.data.Live
import app.waylink.data.Nav
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Chip
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Input
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
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.theme.Serif
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** The Route tab, a port of route() in web-v0/js/views.js. */
@Composable
fun RouteScreen(s: AppState, live: Live, vm: AppViewModel, openSheet: (String) -> Unit, bottom: Dp) {
    val n = live.nav
    if (n != null) EnRoute(s, live, n, vm, bottom) else Planner(s, live, vm, openSheet, bottom)
}

@Composable
private fun Planner(s: AppState, live: Live, vm: AppViewModel, openSheet: (String) -> Unit, bottom: Dp) {
    val c = W.c
    val ctx = LocalContext.current
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    var dest by rememberSaveable { mutableStateOf("") }
    val openMaps = {
        val d = dest.trim()
        if (d.isEmpty()) vm.toast("Where to?")
        else {
            // Google's cross-platform directions link; it opens the Maps app when installed.
            val url = "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(d) + "&travelmode=two-wheeler&dir_action=navigate"
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { vm.toast("No app can open maps.") }
        }
    }
    Page(bottom) {
        PageHead("Route", "Turns on your bike")
        SectionHead("Where to?")
        Txt("Plan the ride in Google Maps. Its turns show on your bike.", T.row, c.ink3, Modifier.padding(bottom = 10.dp))
        Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Txt("Destination", T.eyebrow, c.ink3)
            Spacer(Modifier.height(8.dp))
            Input(dest, { dest = it }, placeholder = "Nandi Hills", label = "Destination", caps = KeyboardCapitalization.Words, maxLength = 120, imeAction = ImeAction.Go, onDone = openMaps)
            Spacer(Modifier.height(12.dp))
            Btn("Open in Google Maps", openMaps, Modifier.fillMaxWidth(), icon = "map")
        }

        SectionGap()
        SectionHead("How this works")
        Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 18.dp, vertical = 6.dp)) {
            listOf(
                "Plan the ride in Google Maps." to " It draws the map and gives voice directions on your phone.",
                "Rehearse a route" to " to see how turns look on your bike’s display before you ride.",
                "Test one turn" to " to check a single arrow and distance on the display.",
            ).forEachIndexed { i, (b, rest) ->
                if (i > 0) Hairline()
                Row(Modifier.padding(vertical = 12.dp)) {
                    Txt("${i + 1}", T.plate.copy(fontSize = 12.sp), c.ink3, Modifier.width(22.dp).padding(top = 3.dp))
                    Txt(androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)); append(b); pop()
                        append(rest)
                    }, T.row.copy(fontSize = 14.5.sp), c.ink2)
                }
            }
        }
        Hint("Reading Google Maps’ turn alerts, so turns reach the bike by themselves, comes with Bluetooth pairing in a later build.")

        SectionGap()
        SectionHead("Rehearse a route", help = { openSheet("help:rehearse") })
        Column(Modifier.fillMaxWidth().card(20.dp).padding(18.dp)) {
            Txt(Rehearsal.NAME, Serif.copy(fontSize = 24.sp, lineHeight = 30.sp), c.ink)
            Spacer(Modifier.height(6.dp))
            Txt("${f.num(Rehearsal.totalMeters / 1000, 1)} km · ${Rehearsal.STEPS.size} turns · NH 44", T.row, c.ink3)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rehearsal.STEPS.forEach { st -> TurnArrow(Maneuvers.of(st.maneuver).glyph, Modifier.size(24.dp, 30.dp), faint = 0.25f, sw = 9f) }
            }
            Spacer(Modifier.height(16.dp))
            Btn("Start rehearsal", vm::startRehearsal, Modifier.fillMaxWidth(), kind = BtnKind.ACCENT, icon = "play")
        }

        SectionGap()
        SectionHead("Test one turn", help = { openSheet("help:testturn") })
        val t = live.turn
        val sel = Maneuvers.of(t.maneuver)
        val rb = sel.glyph.startsWith("roundabout")
        Column(Modifier.fillMaxWidth().card(20.dp)) {
            Row(
                Modifier.fillMaxWidth().pressable(label = "Change the turn") { openSheet("turn-test") }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.raised), contentAlignment = Alignment.Center) {
                    TurnArrow(sel.glyph, Modifier.size(36.dp, 44.dp), exit = if (rb) t.exit else 0, faint = 0.3f, sw = 9f)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(sel.label, T.rowTitle, c.ink)
                    Txt("${f.num(t.m)} m" + (if (rb) " · exit ${t.exit}" else "") + (if (t.street.isNotBlank()) " · ${t.street}" else ""), T.row, c.ink3, maxLines = 1)
                }
                Txt("Change", T.link, c.ink2)
                Ic("chev", size = 14.dp, tint = c.ink3)
            }
            Btn("Send to bike", vm::sendTurn, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), kind = BtnKind.ACCENT)
        }

        SectionGap()
        SectionHead("Display", help = { openSheet("help:display") })
        ListCard {
            ListRow("Street names on the bike", "Road name beside the arrow", icon = "eye", trailing = { Switch(s.settings.streetNames, "Street names on the bike", { vm.toggleSetting("streetNames") }) })
        }
    }
}

@Composable
private fun EnRoute(s: AppState, live: Live, n: Nav, vm: AppViewModel, bottom: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val man = Maneuvers.of(n.maneuver)
    val (dv, du) = distText(n.stepMeters, f)
    val (tv, tu) = distText(n.totalMeters, f)
    val linked = live.link == LinkState.CONNECTED
    // Under 100 m the bike blinks the arrow; the phone does the same.
    val blink = n.stepMeters < 100
    val pulse = if (blink) {
        val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "blink")
        tr.animateFloat(1f, 0.25f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(450), androidx.compose.animation.core.RepeatMode.Reverse), label = "a").value
    } else 1f
    Page(bottom) {
        PageHead("En route", if (n.mode == "rehearse") "Rehearsing a route" else "Testing one turn", action = {
            Txt(if (linked) "On the bike" else "Phone only", T.rowSub.copy(fontWeight = FontWeight.Medium), if (linked) c.sageInk else c.ink3,
                Modifier.clip(RoundedCornerShape(999.dp)).background(if (linked) c.tintSage else c.raised).padding(horizontal = 10.dp, vertical = 4.dp))
        })
        Column(Modifier.fillMaxWidth().card(24.dp).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).background(c.raised), contentAlignment = Alignment.Center) {
                    TurnArrow(man.glyph, Modifier.size(64.dp, 78.dp).alpha(pulse), exit = n.exit)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(dv, T.big(44f), c.ink, maxLines = 1)
                        Txt(" $du", Serif.copy(fontSize = 22.sp), c.ink2, Modifier.padding(bottom = 4.dp))
                    }
                    Txt(n.say.ifEmpty { man.label }, T.body.copy(fontSize = 16.5.sp, lineHeight = 22.sp), c.ink, maxLines = 2)
                }
            }
            Hairline()
            Spacer(Modifier.height(4.dp))
            Kv("Arrive at", tShort(n.eta))
            Kv("Distance left", "$tv $tu")
            Txt(
                if (s.settings.streetNames) "The bike shows the arrow, distances, arrival time and street." else "Street names stay on your phone. The bike shows the arrow, distances and arrival time.",
                T.row.copy(fontSize = 13.5.sp), c.ink3, Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (n.mode == "rehearse") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 4, 16).forEach { x -> Chip("×$x", { vm.setNavSpeed(x) }, on = live.navSpeed == x) }
                }
                Chip("Next turn", vm::nextTurn, icon = "chev")
            }
        } else {
            Column(Modifier.fillMaxWidth().card(20.dp).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Distance to turn", T.eyebrow, c.ink3, Modifier.weight(1f))
                    Txt("${f.num(Math.round(n.stepMeters).toDouble())} m", T.plate.copy(fontSize = 13.sp), c.ink2)
                }
                Spacer(Modifier.height(12.dp))
                Slider(n.stepMeters.toFloat() / 3000f, { vm.setNavDistance(Math.round(it * 300) * 10.0) }, "Distance to turn")
                Scale()
                Hint("Under 100 m, the bike blinks the arrow.")
            }
        }
        Spacer(Modifier.height(16.dp))
        Btn("End navigation", { vm.endNav(); vm.toast("Navigation ended.") }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "x")
        if (n.mode == "rehearse") {
            SectionGap()
            SectionHead("Turns")
            ListCard {
                Rehearsal.STEPS.forEachIndexed { i, st ->
                    if (i > 0) Hairline(66.dp)
                    val done = i < n.idx
                    val now = i == n.idx
                    Row(Modifier.fillMaxWidth().alpha(if (done) 0.45f else 1f).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (now) c.accent.copy(alpha = 0.12f) else c.bg), contentAlignment = Alignment.Center) {
                            TurnArrow(Maneuvers.of(st.maneuver).glyph, Modifier.size(22.dp, 28.dp), exit = 0, faint = 0.25f, sw = 10f)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(st.say, T.body.copy(fontSize = 15.sp, fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal), c.ink, maxLines = 2)
                            val (v, u) = distText(st.m, f)
                            Txt("$v $u", T.row, c.ink3)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Kv(k: String, v: String) {
    val c = W.c
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Txt(k, T.row.copy(fontSize = 15.sp), c.ink3, Modifier.weight(1f))
        Txt(v, T.row.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), c.ink)
    }
}

@Composable
private fun Scale() {
    val c = W.c
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Txt("0", T.rowSub, c.ink4)
        Spacer(Modifier.weight(1f))
        Txt("1.5 km", T.rowSub, c.ink4)
        Spacer(Modifier.weight(1f))
        Txt("3 km", T.rowSub, c.ink4)
    }
}

/** .range: a 4 dp track filled to [value] (0..1) with a round thumb. Drag or tap anywhere on it. */
@Composable
fun Slider(value: Float, onChange: (Float) -> Unit, label: String) {
    val c = W.c
    val cb by rememberUpdatedState(onChange)
    val v = value.coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(32.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(v, 0f..1f, 300)
                setProgress { cb(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) { detectTapGestures { cb((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> cb((ch.position.x / size.width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.inkA(0.12f)))
        Box(Modifier.fillMaxWidth(v).height(4.dp).clip(CircleShape).background(c.ink))
        Box(Modifier.offset(x = (w - 24.dp) * v).size(24.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(c.card))
    }
}

/** "turn-test": pick a turn, slide its distance, add a street, send it. */
fun routeSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav): SheetSpec? {
    if (key != "turn-test") return null
    return SheetSpec("Test one turn", "Route") {
        val c = W.c
        val f = Fmt(s.country.locale)
        val t = live.turn
        val sel = Maneuvers.of(t.maneuver)
        Maneuvers.GROUPS.forEach { (g, keys) ->
            Txt(g, T.rowTitle, c.ink, Modifier.padding(top = 20.dp, bottom = 8.dp))
            keys.map(Maneuvers::of).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val on = m.key == sel.key
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) c.ink.copy(alpha = 0.08f) else c.raised)
                                .then(if (on) Modifier.background(c.accent.copy(alpha = 0.06f)) else Modifier)
                                .pressable(label = m.label) { vm.setTurn(t.copy(maneuver = m.key)) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TurnArrow(m.glyph, Modifier.size(28.dp, 34.dp), faint = 0.3f, sw = 9f)
                            Spacer(Modifier.height(6.dp))
                            Txt(m.label, T.rowSub.copy(fontSize = 11.5.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), if (on) c.ink else c.ink2, maxLines = 2)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().card(20.dp, c.raised).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Distance", T.eyebrow, c.ink3, Modifier.weight(1f))
                Txt("${f.num(t.m)} m", T.plate.copy(fontSize = 13.sp), c.ink2)
            }
            Spacer(Modifier.height(12.dp))
            Slider(t.m / 3000f, { vm.setTurn(t.copy(m = Math.round(it * 300) * 10)) }, "Distance")
            Scale()
            if (sel.glyph.startsWith("roundabout")) {
                Txt("Roundabout exit", T.eyebrow, c.ink3, Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { e -> Chip("$e", { vm.setTurn(t.copy(exit = e)) }, on = t.exit == e) }
                }
            }
            FormField("Street · ${t.street.length}/31") {
                Input(t.street, { vm.setTurn(t.copy(street = it)) }, placeholder = "Outer Ring Road", label = "Street", caps = KeyboardCapitalization.Words, maxLength = 31)
            }
        }
        Spacer(Modifier.height(20.dp))
        Btn("Send to bike", { nav.close(); vm.sendTurn() }, Modifier.fillMaxWidth(), kind = BtnKind.ACCENT)
    }
}
