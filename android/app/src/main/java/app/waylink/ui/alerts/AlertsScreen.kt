package app.waylink.ui.alerts

import app.waylink.ui.components.swipeSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import app.waylink.ui.components.rowPress
import androidx.compose.foundation.layout.offset
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.InboxItem
import app.waylink.core.dShort
import app.waylink.core.inbox
import app.waylink.core.plural
import app.waylink.core.shortName
import app.waylink.core.tShort
import app.waylink.data.LinkState
import app.waylink.data.Live
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Chip
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.IconTile
import app.waylink.ui.components.Input
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Opill
import app.waylink.ui.components.Page
import app.waylink.ui.components.SectionGap
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.Switch
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface AlertsActions {
    fun go(target: String)
    fun sheet(key: String)
    fun help(key: String)
    fun toggle(key: String)
    fun relay(kind: String, name: String)
    fun press(kind: String)
    /** Sounds: a click for a tab tap, a swipe for a sideways swipe. */
    fun clickSound() {}
    fun swipeSound(dir: Int) {}
}

/** The Alerts tab, a port of alerts() in web-v0/js/views.js: Status (what needs you) and Notifications. */
@Composable
fun AlertsScreen(s: AppState, live: Live, act: AlertsActions, bottom: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val needs = remember(s) { inbox(s, f).filter { it.icon != "missed" && it.icon != "chat" } }
    var tab by rememberSaveable { mutableStateOf("needs") }
    val n = s.alerts.missed.size + s.alerts.wa.size
    // A swipe slides the new tab in from its side (.lt-body.in-l / in-r, .28 s).
    var from by remember { mutableStateOf(0) }
    val slide = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(tab) { if (from != 0) { slide.snapTo(0f); slide.animateTo(1f, androidx.compose.animation.core.tween(280)) } }
    fun pick(k: String, dir: Int) { if (tab == k) return; from = dir; if (dir == 0) act.clickSound() else act.swipeSound(dir); tab = k }

    Box(Modifier.fillMaxSize().swipeSides(50.dp) { dir -> pick(if (dir > 0) "recent" else "needs", dir) }) {
    Page(bottom) { androidx.compose.runtime.CompositionLocalProvider(app.waylink.ui.components.LocalBigRowIcons provides true) {
        Spacer(Modifier.height(7.dp))
        Txt("Alerts", T.title, c.ink)
        Txt(if (needs.isNotEmpty()) "${plural(needs.size, "thing needs", "things need")} you" else "Nothing needs you right now", T.subtitle, c.ink3)
        Spacer(Modifier.height(26.dp))
        // .ltabs: underlined tabs with counts and how many alerts reached the bike, set 10 dp in like the web app.
        // They stay pinned under the small title when the page scrolls.
        val tabs: @Composable () -> Unit = {
        Column(Modifier.offset(x = 10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                LTab("Status", needs.size, tab == "needs") { pick("needs", 0) }
                Spacer(Modifier.width(22.dp))
                LTab("Notifications", n, tab == "recent") { pick("recent", 0) }
                Spacer(Modifier.weight(1f))
                Txt("${f.short(s.alerts.sent.toDouble())} sent", T.eyebrow.copy(fontSize = 14.sp), c.ink3, Modifier.padding(bottom = 12.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        }
        }
        app.waylink.ui.components.StickyInfo("Alerts", below = tabs)
        tabs()
        Spacer(Modifier.height(24.dp))

        Column(Modifier.graphicsLayer { alpha = slide.value; translationX = (1f - slide.value) * 24.dp.toPx() * from }) {
        if (tab == "needs") {
            if (needs.isEmpty()) Txt(
                "All clear. Service, papers and fuel show up here when they need you.", T.row.copy(fontSize = 14.5.sp, textAlign = TextAlign.Center), c.ink3,
                Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 22.dp, vertical = 30.dp),
            ) else ListCard {
                needs.forEachIndexed { i, x ->
                    if (i > 0) Hairline(start = 66.dp)
                    NeedRow(x) { act.go(x.go) }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountCard("missed", "Missed calls", s.alerts.missed.size, f, Modifier.weight(1f), "clear:clear-missed", act, Intent(Intent.ACTION_DIAL), "Open the Phone app", "phone")
                CountCard("chat", "WhatsApp", s.alerts.wa.size, f, Modifier.weight(1f), "clear:clear-wa", act, Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send")), "Open WhatsApp", "chat")
            }
            Spacer(Modifier.height(20.dp))
            Feed(s)

            SectionGap()
            Row(
                Modifier.fillMaxWidth().border(1.5.dp, c.inkA(0.16f), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic("lock", size = 17.dp, tint = c.ink3)
                Spacer(Modifier.width(12.dp))
                Txt(buildAnnotatedString {
                    append("Only ")
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("names and counts") }
                    append(" reach your bike. Message text never leaves this phone.")
                }, T.row.copy(fontSize = 14.5.sp, lineHeight = 23.sp), c.ink2)
            }

            SectionGap()
            SectionHead("Show on the bike", help = { act.help("showbike") })
            val a = s.settings.alerts
            ListCard {
                ListRow("Phone calls", "Caller name and missed calls", "phone", trailing = { Switch(a.calls, "Phone calls", { act.toggle("calls") }) })
                Hairline(start = 66.dp)
                ListRow("WhatsApp calls", "Caller name while ringing", "waCall", trailing = { Switch(a.waCalls, "WhatsApp calls", { act.toggle("waCalls") }) })
                Hairline(start = 66.dp)
                ListRow("WhatsApp messages", "Sender name and count", "chat", trailing = { Switch(a.waMsgs, "WhatsApp messages", { act.toggle("waMsgs") }) })
                Hairline(start = 66.dp)
                Box(Modifier.alpha(0.5f)) { ListRow("SMS", "Coming soon", "mail", trailing = { Switch(false, "SMS", {}, enabled = false) }) }
            }

            SectionGap()
            SectionHead("Handlebar button", help = { act.help("handlebar") })
            val canPress = live.simulated && live.link == LinkState.CONNECTED
            ListCard {
                ListRow("Tap", "Answer, or play and pause", "hand", trailing = { if (canPress) Chip("Press", { act.press("tap") }) })
                Hairline(start = 66.dp)
                ListRow("Long press", "Decline, or next track", "hand", trailing = { if (canPress) Chip("Press", { act.press("long") }) })
            }

            SectionGap()
            SectionHead("Send to the bike", help = { act.help("send") }) {
                Txt(if (live.link == LinkState.CONNECTED) "Linked" else "Not linked", T.eyebrow.copy(fontSize = 14.sp), c.ink3)
            }
            var name by rememberSaveable { mutableStateOf("Rohan") }
            Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Txt("Name", T.eyebrow, c.ink3)
                Spacer(Modifier.height(8.dp))
                Input(name, { name = it }, placeholder = "Rohan", label = "Name", maxLength = 32)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Ring", { act.relay("ring", name) }, Modifier.weight(1f), kind = BtnKind.GHOST, icon = "phone", small = true)
                    Btn("Missed", { act.relay("missed", name) }, Modifier.weight(1f), kind = BtnKind.GHOST, icon = "missed", small = true)
                    Btn("Chat", { act.relay("wa", name) }, Modifier.weight(1f), kind = BtnKind.GHOST, icon = "chat", small = true)
                }
                Txt("Until the bike link reads your phone’s own calls and chats, send them here by hand to test.", T.row.copy(fontSize = 13.5.sp, lineHeight = 21.sp), c.ink3, Modifier.padding(top = 12.dp))
            }
        }
        }
    } }
    }
}

@Composable
private fun LTab(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    val c = W.c
    Column(Modifier.width(IntrinsicSize.Max).semantics { selected = on }.pressable(label = label, role = Role.Tab, onClick = onClick), horizontalAlignment = Alignment.Start) {
        Row(Modifier.padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, T.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp), if (on) c.ink else c.ink3)
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.height(18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(9.dp)).background(c.red).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Txt("$count", T.chip.copy(fontSize = 11.sp, lineHeight = 11.sp), androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        Box(Modifier.height(2.5.dp).then(if (on) Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(c.ink) else Modifier))
    }
}

@Composable
private fun NeedRow(x: InboxItem, onClick: () -> Unit) {
    val c = W.c
    val hot = x.tone == "red"
    Row(Modifier.fillMaxWidth().rowPress(label = x.title, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(x.icon, size = 44.dp, radius = 13.dp, iconSize = 24.dp, bg = c.inkA(0.06f), tint = c.ink2) // neutral tiles; the Overdue pill carries the colour
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(x.title, T.body.copy(lineHeight = 22.4.sp), c.ink, maxLines = 2)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                val late = hot && x.desc.endsWith(" overdue")
                if (late) { Opill(); Spacer(Modifier.width(6.dp)) }
                Txt(if (late) x.desc.removeSuffix(" overdue") + " late" else x.desc, T.row.copy(lineHeight = 20.3.sp), c.ink3, maxLines = 1)
            }
        }
        Ic("chev", size = 15.dp, tint = c.ink3)
    }
}

@Composable
private fun CountCard(icon: String, label: String, n: Int, f: Fmt, modifier: Modifier, clearKey: String, act: AlertsActions, open: Intent, openLabel: String, openIcon: String) {
    val c = W.c
    val ctx = LocalContext.current
    Column(modifier.fillMaxHeight().card(20.dp).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, size = 14.dp, tint = c.ink3)
            Spacer(Modifier.width(8.dp))
            Txt(label, T.eyebrow, c.ink3, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        Txt(f.short(n.toDouble()), T.big(56f), c.ink)
        Spacer(Modifier.height(8.dp))
        Txt(if (n > 0) "On your bike" else "None waiting", T.row, c.ink2, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c.raised).border(1.dp, c.line, CircleShape)
                    .pressable(label = openLabel) { try { ctx.startActivity(open) } catch (e: Exception) { /* No app for it. */ } },
                contentAlignment = Alignment.Center,
            ) { Ic(openIcon, size = 16.dp, tint = c.ink) }
            Spacer(Modifier.weight(1f))
            Txt(
                "Clear", T.buttonSm, if (n > 0) c.ink2 else c.ink4,
                Modifier.clip(RoundedCornerShape(12.dp)).pressable(enabled = n > 0, label = "Clear $label") { act.sheet(clearKey) }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

private val NT = mapOf(
    "wa" to Triple("chat", "WhatsApp message", "Shown on your bike"),
    "missed" to Triple("missed", "Missed call", "Count sent to your bike"),
    "call" to Triple("phone", "Incoming call", "Name shown on your bike"),
    "button" to Triple("hand", "Handlebar button", "Pressed on the bike"),
)

/** What reached the bike, newest first, grouped by day. */
@Composable
private fun Feed(s: AppState) {
    val c = W.c
    val log = s.alerts.log.take(30)
    if (log.isEmpty()) {
        Txt("No notifications yet.", T.row.copy(fontSize = 14.5.sp, textAlign = TextAlign.Center), c.ink3, Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 22.dp, vertical = 30.dp))
        return
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var lastDay = ""
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        log.forEach { x ->
            val d = Instant.ofEpochMilli(x.ts).atZone(zone).toLocalDate()
            val day = when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> dShort(x.ts) }
            if (day != lastDay) {
                Txt(day, T.rowSub.copy(fontWeight = FontWeight.SemiBold), c.ink3, Modifier.padding(start = 4.dp, end = 4.dp, top = if (lastDay.isEmpty()) 0.dp else 8.dp))
                lastDay = day
            }
            val (ic, what, sub) = NT[x.kind] ?: NT.getValue("call")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconTile(ic, size = 36.dp, radius = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Txt(if (x.kind == "button") "Handlebar" else shortName(x.name), T.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp), c.ink, Modifier.weight(1f), maxLines = 1)
                        Txt(tShort(x.ts), T.rowSub.copy(fontSize = 12.5.sp), c.ink3)
                    }
                    Txt("$what · $sub", T.row.copy(fontSize = 13.5.sp), c.ink3, Modifier.padding(top = 2.dp), maxLines = 1)
                }
            }
        }
    }
}
