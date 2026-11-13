package app.waylink.ui.shell

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.waylink.core.AppState
import app.waylink.data.AppViewModel
import app.waylink.data.LinkState
import app.waylink.data.Live
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Sheet
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.grain
import app.waylink.ui.components.pressable
import app.waylink.ui.fuel.FuelActions
import app.waylink.ui.fuel.FuelScreen
import app.waylink.ui.fuel.fuelSheet
import app.waylink.ui.alerts.AlertsActions
import app.waylink.ui.alerts.AlertsScreen
import app.waylink.ui.garage.GarageList
import app.waylink.ui.rides.RidesActions
import app.waylink.ui.rides.RidesScreen
import app.waylink.ui.rides.rideSheet
import app.waylink.ui.logbook.LogbookActions
import app.waylink.ui.logbook.LogbookScreen
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.logbook.logbookSheet
import app.waylink.ui.onboarding.Onboarding
import app.waylink.ui.onboarding.OnboardingActions
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import app.waylink.ui.theme.WaylinkTheme
import app.waylink.ui.today.TodayActions
import app.waylink.ui.today.TodayScreen
import kotlinx.coroutines.delay

enum class Tab(val key: String, val label: String, val icon: String) {
    TODAY("today", "Today", "today"), ROUTE("route", "Route", "route"), RIDES("trips", "Rides", "trips"),
    ALERTS("alerts", "Alerts", "alerts"), SETTINGS("settings", "Settings", "settings"),
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    WaylinkTheme(s.settings.theme, s.settings.accent) {
        val c = W.c
        Box(Modifier.fillMaxSize().background(c.bg).grain()) {
            Crossfade(s.onboarded, animationSpec = tween(260), label = "root") { onboarded ->
                if (!onboarded) Onboarding(s, object : OnboardingActions {
                    override fun sample() = vm.onboardSample()
                    override fun saveBike(model: String, plate: String, odo: Double, country: String) = vm.saveBike(model, plate, odo, country)
                    override fun saveAboutYou(avatar: String, name: String, city: String, blood: String, iceName: String, icePhone: String) =
                        vm.saveAboutYou(avatar, name, city, blood, iceName, icePhone)
                    override fun finish(simulated: Boolean) = vm.finishOnboarding(simulated)
                })
                else Main(s, live, vm)
            }
            ToastHost(live, vm::clearToast)
        }
    }
}

@Composable
private fun Main(s: AppState, live: Live, vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // An inner page over the tabs (logbook, fuel, profile), like the web's pushed screens.
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    // Open sheets, newest last, joined by newlines so the stack survives rotation.
    var stack by rememberSaveable { mutableStateOf("") }
    val sheets = stack.split('\n').filter { it.isNotEmpty() }
    val nav = object : SheetNav {
        override fun open(key: String) { stack = (sheets + key).joinToString("\n") }
        override fun back() { stack = sheets.dropLast(1).joinToString("\n") }
        override fun close() { stack = "" }
    }
    val openSheet = { key: String -> stack = key }
    val soon = { what: String -> vm.toast("$what comes in the next Android build.") }
    val tabBarHeight = 80.dp
    if (page != null) BackHandler { page = null }
    // Recording asks for location the first time; the simulated bike needs none.
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.startRecording()
        else vm.toast("Location is off. Allow it to record rides, or use the simulated bike.")
    }
    val startRec = {
        if (live.simulated || vm.hasLocation()) vm.startRecording()
        else askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // The screen stays on while a ride records, so GPS keeps flowing.
    val view = LocalView.current
    val recording = live.rec != null
    DisposableEffect(recording) { view.keepScreenOn = recording; onDispose { view.keepScreenOn = false } }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            page ?: tab.key,
            transitionSpec = {
                val push = targetState in PAGES && initialState !in PAGES
                val pop = initialState in PAGES && targetState !in PAGES
                when {
                    push -> (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
                    pop -> fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(200)))
                    else -> fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                }
            },
            label = "screen",
        ) { key ->
            when (key) {
                "logbook" -> LogbookScreen(s, object : LogbookActions {
                    override fun back() { page = null }
                    override fun sheet(key: String) = openSheet(key)
                    override fun help(key: String) = openSheet("help:$key")
                    override fun toggleCheck(id: String) = vm.toggleCheck(id)
                    override fun deleteCheck(id: String) = vm.deleteCheck(id)
                    override fun addCheck(text: String) = vm.addCheck(text)
                }, tabBarHeight)
                "fuel" -> FuelScreen(s, object : FuelActions {
                    override fun back() { page = null }
                    override fun sheet(key: String) = openSheet(key)
                    override fun help(key: String) = openSheet("help:$key")
                }, tabBarHeight)
                Tab.TODAY.key -> TodayScreen(s, live, object : TodayActions {
                    override fun garage() = openSheet("garage")
                    override fun sos() = openSheet("sos")
                    override fun pair() = openSheet("pair")
                    override fun ride() = soon("Ride mode")
                    override fun logbook() { page = "logbook" }
                    override fun service(id: String) = openSheet("svc:$id")
                    override fun alerts() { tab = Tab.ALERTS }
                    override fun fuel() { page = "fuel" }
                    override fun trip(which: String) = openSheet("trip:$which")
                    override fun rides() { tab = Tab.RIDES }
                    override fun glovebox() = soon("The glovebox")
                    override fun weather() = vm.refreshWeather()
                    override fun readingsHelp() = openSheet("help:readings")
                }, tabBarHeight)
                "profile" -> ProfileScreen(s, back = { page = null }, sheet = openSheet, help = { openSheet("help:$it") }, bottom = tabBarHeight)
                Tab.SETTINGS.key -> SettingsScreen(s, live, object : SettingsActions {
                    override fun sheet(key: String) = openSheet(key)
                    override fun page(key: String) { page = key }
                    override fun help(key: String) = openSheet("help:$key")
                    override fun toggle(key: String) = vm.toggleSetting(key)
                    override fun theme(key: String) = vm.setTheme(key)
                    override fun accent(key: String) = vm.setAccent(key)
                    override fun sim(on: Boolean) = if (on) vm.simOn() else vm.simOff()
                    override fun soon(what: String) = vm.toast("$what comes in the next Android build.")
                }, tabBarHeight)
                Tab.ROUTE.key -> Placeholder("Route", "Turns on your bike", "Plan in Google Maps and your turns show on the cluster. On Android, Waylink will read Maps’ own turn alerts, which the web app can’t.", tabBarHeight)
                Tab.RIDES.key -> RidesScreen(s, live, object : RidesActions {
                    override fun record() = startRec()
                    override fun endRide() = openSheet("rec-end")
                    override fun open(id: String) = openSheet("trip-detail:$id")
                    override fun chart(page: Int) = vm.setRideChart(page)
                }, tabBarHeight)
                else -> AlertsScreen(s, live, object : AlertsActions {
                    override fun go(target: String) {
                        val (kind, rest) = target.substringBefore(':') to target.substringAfter(':')
                        when (kind) {
                            "sheet" -> openSheet(rest)
                            "page" -> page = rest
                            "tab" -> { page = null; tab = Tab.entries.firstOrNull { it.key == rest } ?: tab }
                        }
                    }
                    override fun sheet(key: String) = openSheet(key)
                    override fun help(key: String) = openSheet("help:$key")
                    override fun toggle(key: String) = vm.toggleAlert(key)
                    override fun relay(kind: String, name: String) = vm.relay(kind, name)
                    override fun press(kind: String) = vm.press(kind)
                }, tabBarHeight)
            }
        }
        TabBar(if (page == "logbook" || page == "fuel") Tab.TODAY else if (page == "profile") Tab.SETTINGS else tab, s, live, Modifier.align(Alignment.BottomCenter)) {
            page = null; tab = it
        }

        // One sheet host. The top of the stack shows; the last one stays drawn while it slides away.
        val top = sheets.lastOrNull()
        val spec = top?.let { buildSheet(it, s, live, vm, nav) }
        var last by remember { mutableStateOf<SheetSpec?>(null) }
        if (spec != null) last = spec
        if (top != null && spec == null) LaunchedEffect(top) { nav.back() }
        val shown = spec ?: last
        Sheet(spec != null, nav::close, shown?.title ?: "", shown?.sub, onBack = if (sheets.size > 1) nav::back else null) {
            shown?.content?.invoke(this)
        }
    }
}

private val PAGES = setOf("logbook", "fuel", "profile")

/** Every sheet the app can show, by key ("svc:<id>", "help:reminders", "garage"). */
private fun buildSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav): SheetSpec? {
    logbookSheet(key, s, vm, nav)?.let { return it }
    fuelSheet(key, s, vm, nav)?.let { return it }
    rideSheet(key, s, live, vm, nav)?.let { return it }
    settingsSheet(key, s, live, vm, nav)?.let { return it }
    val arg = key.substringAfter(':', "")
    return when (key.substringBefore(':')) {
        "garage" -> SheetSpec("Your bikes", "Garage") {
            GarageList(s, onPick = { vm.switchVehicle(it); nav.close() }, onAdd = { nav.open("vehicle:new") })
        }
        "pair" -> SheetSpec("Link your bike", "Bluetooth") {
            PairSheet(live, onSim = { vm.simOn(); nav.close() }, onSimOff = { vm.simOff(); nav.close() })
        }
        "sos" -> SheetSpec("Emergency SOS", "Medical ID") { SosSheet(s) }
        "clear" -> {
            val wa = arg == "clear-wa"
            val n = if (wa) s.alerts.wa.size else s.alerts.missed.size
            SheetSpec(if (wa) "Clear WhatsApp alerts?" else "Clear missed calls?", "Alerts") {
                Spacer(Modifier.height(12.dp))
                Txt("${app.waylink.core.plural(n, if (wa) "chat" else "call", if (wa) "chats" else "calls")} will be removed here and the count on your bike goes to zero. Your phone keeps its own history.", T.lead, W.c.ink2)
                Spacer(Modifier.height(24.dp))
                Btn("Clear", { if (wa) vm.clearWa() else vm.clearMissed(); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
                Spacer(Modifier.height(8.dp))
                Btn("Keep them", nav::close, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
            }
        }
        "help" -> HELP[arg]?.let { h ->
            SheetSpec(h.title, h.eyebrow) {
                val c = W.c
                h.paras.forEachIndexed { i, p ->
                    Spacer(Modifier.height(if (i == 0) 12.dp else 10.dp))
                    Txt(p, if (i == 0) T.lead else T.lead.copy(fontSize = 15.sp), if (i == 0) c.ink2 else c.ink3)
                }
                Spacer(Modifier.height(24.dp))
                Btn("Got it", nav::close, Modifier.fillMaxWidth())
            }
        }
        else -> null
    }
}

@Composable
private fun TabBar(current: Tab, s: AppState, live: Live, modifier: Modifier, onTab: (Tab) -> Unit) {
    val c = W.c
    val v = s.vehicle
    val needs = app.waylink.core.needsYou(v) > 0 || app.waylink.core.fuelStats(v).onReserve
    val alerts = s.alerts.missed.size + s.alerts.wa.size > 0 || needs
    Column(modifier.fillMaxWidth().background(c.bg.copy(alpha = 0.96f))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.inkA(0.09f)))
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab.entries.forEach { t ->
                val on = t == current
                val pip = when (t) { Tab.TODAY -> needs; Tab.ALERTS -> alerts; else -> false }
                Column(
                    Modifier.width(66.dp).semantics { selected = on }.pressable(label = t.label, role = Role.Tab) { onTab(t) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Ic(t.icon, size = 26.dp, tint = if (on) c.ink else c.ink4)
                        if (pip) Box(Modifier.align(Alignment.TopEnd).offset(3.dp, (-1).dp).size(10.dp).clip(CircleShape).background(c.bg).padding(1.5.dp).clip(CircleShape).background(c.red))
                    }
                    Spacer(Modifier.height(4.dp))
                    Txt(t.label, T.tab, if (on) c.ink else c.ink4, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ToastHost(live: Live, clear: () -> Unit) {
    val t = live.toast
    LaunchedEffect(t?.id) { if (t != null) { delay(2600); clear() } }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 96.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(t != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            val c = W.c
            Txt(
                t?.text ?: "", T.row.copy(fontSize = 15.sp), c.bg,
                Modifier.clip(RoundedCornerShape(16.dp)).background(c.ink.copy(alpha = 0.94f)).padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

/** A tab that isn't on Android yet: same header as the web page, and what's coming. */
@Composable
private fun Placeholder(title: String, sub: String, what: String, bottom: androidx.compose.ui.unit.Dp) {
    val c = W.c
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = bottom),
    ) {
        Txt(title, T.title, c.ink)
        Spacer(Modifier.height(3.dp))
        Txt(sub, T.subtitle, c.ink3)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().card(20.dp).padding(20.dp)) {
            Txt("Coming next on Android", T.rowTitle, c.ink)
            Spacer(Modifier.height(6.dp))
            Txt(what, T.lead, c.ink2)
        }
    }
}

@Composable
private fun PairSheet(live: Live, onSim: () -> Unit, onSimOff: () -> Unit) {
    val c = W.c
    Spacer(Modifier.height(12.dp))
    if (live.link == LinkState.CONNECTED) {
        Txt("The simulated cluster is linked. Readings and the ride button behave as if a bike were there.", T.lead, c.ink2)
        Spacer(Modifier.height(20.dp))
        Btn("Unlink the simulated bike", onSimOff, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
    } else {
        Txt("Real pairing with the Bluetooth cluster arrives in the next Android build. Until then, the simulated bike shows how everything works.", T.lead, c.ink2)
        Spacer(Modifier.height(20.dp))
        Btn("Try the simulated bike", onSim, Modifier.fillMaxWidth(), icon = "bolt")
    }
}

@Composable
private fun SosSheet(s: AppState) {
    val c = W.c
    val ctx = LocalContext.current
    val country = s.country
    val p = s.profile
    fun dial(n: String) = ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().card(20.dp, c.raised).padding(horizontal = 18.dp, vertical = 6.dp)) {
        listOf("Blood group" to p.blood.ifEmpty { "Not set" }, "Allergies and notes" to p.medical.ifEmpty { "None noted" }, "Emergency contact" to (p.iceName.ifEmpty { "Not set" } + if (p.icePhone.isNotEmpty()) " · ${p.icePhone}" else "")).forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(Modifier.padding(vertical = 12.dp)) {
                Txt(k, T.eyebrow, c.ink3)
                Txt(v, T.body, c.ink)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Btn("Call ${country.amb} · Ambulance", { dial(country.amb) }, Modifier.fillMaxWidth(), kind = BtnKind.ACCENT, icon = "ambulance")
    Spacer(Modifier.height(8.dp))
    Btn("Call ${country.sos} · Emergency", { dial(country.sos) }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "siren")
    if (p.icePhone.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Btn("Call ${p.iceName.ifEmpty { "emergency contact" }}", { dial(p.icePhone) }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "phone")
    }
    Spacer(Modifier.height(12.dp))
    Txt("Opens your phone’s dialler. Waylink never calls by itself.", T.note.copy(textAlign = TextAlign.Center), c.ink3, Modifier.fillMaxWidth())
}
