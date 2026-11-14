package app.waylink.ui.shell

import android.content.Intent
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.rowPress
import app.waylink.ui.components.swipeSides
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
import app.waylink.ui.components.AutoClose
import app.waylink.ui.components.LoadingScreen
import app.waylink.ui.components.Splash
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
    WaylinkTheme(s.settings.theme, s.settings.accent, s.settings.font, s.settings.bodyFont, s.settings.progress, s.settings.icons) {
        val c = W.c
        Box(Modifier.fillMaxSize().background(c.bg).grain()) {
            Crossfade(s.onboarded, animationSpec = tween(260), label = "root") { onboarded ->
                if (!onboarded) Onboarding(s, object : OnboardingActions {
                    override fun sample() = vm.onboardSample()
                    override fun saveBike(model: String, plate: String, odo: Double, country: String) = vm.saveBike(model, plate, odo, country)
                    override fun saveAboutYou(avatar: String, name: String, city: String, blood: String, iceName: String, icePhone: String) =
                        vm.saveAboutYou(avatar, name, city, blood, iceName, icePhone)
                    override fun finish(simulated: Boolean) = vm.finishOnboarding(simulated)
                    override fun finishAndPair() = vm.finishOnboarding(false, pair = true)
                })
                else {
                    Main(s, live, vm)
                }
            }
            // Developer: onboarding as a preview over the app. Nothing it asks is saved.
            androidx.compose.animation.AnimatedVisibility(live.onbTest && s.onboarded, enter = fadeIn(tween(260)), exit = fadeOut(tween(260))) {
                BackHandler { vm.showOnboarding(false) }
                Box(Modifier.fillMaxSize().background(c.bg).grain()) {
                    Onboarding(app.waylink.core.freshState(), object : OnboardingActions {
                        override fun sample() = vm.showOnboarding(false)
                        override fun saveBike(model: String, plate: String, odo: Double, country: String) {}
                        override fun saveAboutYou(avatar: String, name: String, city: String, blood: String, iceName: String, icePhone: String) {}
                        override fun finish(simulated: Boolean) = vm.showOnboarding(false)
                    })
                }
            }
            androidx.compose.animation.AnimatedVisibility(live.loading != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
                LoadingScreen(live.loading ?: "Loading")
                AutoClose(live.loading ?: "", 3200) { vm.hideLoading() }
            }
            // The splash: 2 s at launch, 6 s (or a tap) from Developer; fades out over .35 s.
            androidx.compose.animation.AnimatedVisibility(live.splash > 0, enter = fadeIn(tween(1)), exit = fadeOut(tween(350))) {
                var shown by remember { mutableStateOf(live.splash) }
                if (live.splash > 0) shown = live.splash
                Splash(shown) { vm.hideSplash() }
                AutoClose(shown, if (shown == 1) 2050 else 6000) { vm.hideSplash() }
            }
            ToastHost(live, vm::clearToast)
        }
    }
}

@Composable
private fun Main(s: AppState, live: Live, vm: AppViewModel) {
    val c = W.c
    val hostActivity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
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
    // What to do once location is allowed: record a ride, or save where you parked.
    var pendingLoc by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) pendingLoc?.invoke()
        else vm.toast("Location is off. Allow it in Android settings, or use the simulated bike.")
        pendingLoc = null
    }
    val needLocation = { then: () -> Unit ->
        if (live.simulated || vm.hasLocation()) then()
        else { pendingLoc = then; askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }
    val beginRec = { needLocation { vm.startRecording() } }
    val host = object : app.waylink.ui.shell.ExtraHost {
        override fun withLocation(then: () -> Unit) = needLocation(then)
        override fun startRec() = beginRec()
        override fun rideMode() = vm.openRideMode(true)
        override fun page(key: String) { stack = ""; page = key }
    }
    // Ride mode opens by itself when you pass 15 km/h, if that's switched on.
    var wasSlow by remember { mutableStateOf(true) }
    LaunchedEffect(live.speed) {
        val fast = live.speed > 15
        if (fast && wasSlow && s.settings.autoRide && !live.rideMode) vm.openRideMode(true)
        wasSlow = !fast
    }
    // After the first link, ask once whether rides should start by themselves (web auto-ask).
    LaunchedEffect(live.link) {
        if (live.link == app.waylink.data.LinkState.CONNECTED && !s.settings.autoAsked && vm.takeAutoAsk()) {
            kotlinx.coroutines.delay(900)
            if (stack.isEmpty()) openSheet("auto-ask")
        }
    }
    // Once a day, with no bike linked or paired, Today offers to pair (web pair-intro nudge).
    val prefCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(tab, s.onboarded) {
        if (tab != Tab.TODAY || !s.onboarded || live.scenario != null) return@LaunchedEffect
        if (s.vehicle.bike.id != null || live.link != app.waylink.data.LinkState.IDLE) return@LaunchedEffect
        val prefs = prefCtx.getSharedPreferences("waylink-ui", android.content.Context.MODE_PRIVATE)
        val day = java.time.LocalDate.now().toString()
        if (prefs.getString("pair-nudge", null) == day) return@LaunchedEffect
        kotlinx.coroutines.delay(2600) // after the launch splash
        if (stack.isEmpty() && page == null) { prefs.edit().putString("pair-nudge", day).apply(); openSheet("pair-intro") }
    }
    // A sheet asked for from outside the tabs, e.g. "Pair your bike" at the end of onboarding.
    LaunchedEffect(live.ask) { live.ask?.let { kotlinx.coroutines.delay(350); openSheet(it); vm.clearAsk() } }
    // The screen stays on while a ride records, so GPS keeps flowing.
    val view = LocalView.current
    val awake = live.rec != null || (live.rideMode && s.settings.wakeLock)
    DisposableEffect(awake) { view.keepScreenOn = awake; onDispose { view.keepScreenOn = false } }
    Box(Modifier.fillMaxSize()) {
        // Pull down at the top of any tab: the wheel rolls and spins; Today fetches fresh weather.
        app.waylink.ui.components.PullToRefresh(stack.isEmpty() && !live.rideMode, onRefresh = { if (page == null && tab == Tab.TODAY) vm.refreshWeather() }) {
        AnimatedContent(
            page ?: tab.key,
            transitionSpec = {
                val push = targetState in PAGES && initialState !in PAGES
                val pop = initialState in PAGES && targetState !in PAGES
                when {
                    // .view.push-in: pushIn .45s spring, from 26 % right and 20 % opacity
                    push -> (slideInHorizontally(tween(450, easing = app.waylink.ui.theme.Spring)) { (it * 0.26f).toInt() } +
                        fadeIn(tween(450, easing = app.waylink.ui.theme.Spring), initialAlpha = 0.2f)) togetherWith fadeOut(tween(90))
                    // .view.fade-in: fadeIn .3s ease, up from 6 px (tabs, and back from a page)
                    // A touch quicker than web's .3 s and starting part-visible, so a tab tap reads as instant.
                    else -> (fadeIn(tween(200, easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)), initialAlpha = 0.25f) +
                        slideInVertically(tween(200, easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))) { it / 140 }) togetherWith fadeOut(tween(60))
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
                    override fun exitPreview() = vm.setScenario(null)
                    override fun garage() = openSheet("garage")
                    override fun sos() = openSheet("sos")
                    override fun pair() = openSheet("pair")
                    override fun pairNew() = openSheet("pair-intro")
                    override fun reconnect() = vm.reconnect()
                    override fun cancelLink() = vm.disconnect()
                    override fun ride() = vm.openRideMode(true)
                    override fun logbook() { page = "logbook" }
                    override fun service(id: String) = openSheet("svc:$id")
                    override fun alerts() { tab = Tab.ALERTS }
                    override fun fuel() { page = "fuel" }
                    override fun trip(which: String) = openSheet("trip:$which")
                    override fun rides() { tab = Tab.RIDES }
                    override fun glovebox() = openSheet("glovebox")
                    override fun weather() { if (live.weather !is app.waylink.data.Weather.State.Ready) vm.refreshWeather(); openSheet("weather") }
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
                    override fun dev(key: String) = when (key) {
                        "onboarding" -> vm.showOnboarding(true)
                        "splash" -> vm.showSplash()
                        "loading" -> vm.showLoading()
                        "pair-all" -> openSheet("pick-all")
                        // Android has no cached web build to fetch; reloading the screen keeps every bit of data.
                        "refresh" -> { vm.showLoading("Refreshing"); hostActivity?.recreate(); Unit }
                        else -> Unit
                    }
                    override fun scenario(key: String?) = vm.setScenario(key)
                    override fun click(on: Boolean) = vm.uiClick(on)
                }, tabBarHeight, onFocused = vm::clearFocus)
                Tab.ROUTE.key -> app.waylink.ui.route.RouteScreen(s, live, vm, openSheet, tabBarHeight)
                Tab.RIDES.key -> RidesScreen(s, live, object : RidesActions {
                    override fun record() = beginRec()
                    override fun endRide() = openSheet("rec-end")
                    override fun open(id: String) = openSheet("trip-detail:$id")
                    override fun chart(page: Int) = vm.setRideChart(page)
                }, tabBarHeight)
                else -> AlertsScreen(s, live, object : AlertsActions {
                    override fun clickSound() = vm.uiClick(true)
                    override fun swipeSound(dir: Int) = vm.uiSwipe(dir)
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
        }
        val shownTab = if (page == "logbook" || page == "fuel") Tab.TODAY else if (page == "profile") Tab.SETTINGS else tab
        var skipTap by remember { mutableStateOf(0L) }
        // The tapped tab lights up on the very next frame; the screen swaps one frame later, so a heavy
        // screen never holds back the feedback under the finger.
        var tapTab by remember { mutableStateOf<Tab?>(null) }
        LaunchedEffect(tapTab) { tapTab?.let { androidx.compose.runtime.withFrameNanos { }; page = null; tab = it; tapTab = null } }
        TabBar(tapTab ?: shownTab, s, live, Modifier.align(Alignment.BottomCenter), onSwipe = { dir ->
            skipTap = android.os.SystemClock.uptimeMillis()
            Tab.entries.getOrNull(shownTab.ordinal + dir)?.let { vm.uiSwipe(dir); tapTab = it }
        }) {
            if (android.os.SystemClock.uptimeMillis() - skipTap > 400 && it != shownTab) tapTab = it
            else if (page != null && it == shownTab) page = null
        }
        // .live-pill: while a ride records (or a route runs), a dark pill in the page header shows km and time;
        // tap it to go to Rides (or Route). Hidden on the tab that already shows it, on inner pages and in ride mode.
        val pRec = live.rec; val pNav = live.nav
        val pillOn = (pNav != null || pRec != null) && !live.rideMode && page == null && stack.isEmpty() &&
            !(if (pNav != null) tab == Tab.ROUTE else tab == Tab.RIDES || tab == Tab.TODAY)
        androidx.compose.animation.AnimatedVisibility(
            pillOn, Modifier.align(Alignment.TopEnd),
            enter = fadeIn(tween(220)) + androidx.compose.animation.scaleIn(tween(350, easing = app.waylink.ui.theme.Spring), initialScale = 0.8f),
            exit = fadeOut(tween(160)),
        ) {
            LivePill(live, Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 22.dp, end = if (tab == Tab.TODAY) 72.dp else 20.dp)) {
                page = null; tab = if (pNav != null) Tab.ROUTE else Tab.RIDES
            }
        }
        // Quick actions on Today, like the web's + button.
        androidx.compose.animation.AnimatedVisibility(
            page == null && tab == Tab.TODAY, Modifier.align(Alignment.BottomEnd),
            // fabIn: from scale .6 and transparent; #fab.away: down 24 and to .82 over .32s spring
            enter = fadeIn(tween(220)) + androidx.compose.animation.scaleIn(tween(320, easing = app.waylink.ui.theme.Spring), initialScale = 0.6f),
            exit = fadeOut(tween(220)) + androidx.compose.animation.scaleOut(tween(320, easing = app.waylink.ui.theme.Spring), targetScale = 0.82f) +
                slideOutVertically(tween(320, easing = app.waylink.ui.theme.Spring)) { (it * 0.42f).toInt() },
        ) {
            // .fab: 57 dp ink disc, 20 in and 26 above the tab bar, a soft ring of the page colour and a faint inner line
            Box(
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(end = 16.dp, bottom = tabBarHeight + 22.dp)
                    .size(57.dp + 8.dp).border(1.dp, c.inkA(0.07f), CircleShape).padding(1.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.5f)).padding(3.dp)
                    .shadow(8.dp, CircleShape, ambientColor = c.ink.copy(alpha = 0.3f), spotColor = c.ink.copy(alpha = 0.3f)).clip(CircleShape).background(c.ink)
                    .pressable(label = "Quick actions", scale = 0.9f) { openSheet("quick") },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.matchParentSize().padding(2.dp).border(1.dp, c.bg.copy(alpha = 0.18f), CircleShape))
                Ic("plus", size = 25.5.dp, tint = c.bg)
            }
        }

        // One sheet host. The top of the stack shows; the last one stays drawn while it slides away.
        val top = sheets.lastOrNull()
        val spec = top?.let { buildSheet(it, s, live, vm, nav, host) }
        var last by remember { mutableStateOf<SheetSpec?>(null) }
        if (spec != null) last = spec
        if (top != null && spec == null) LaunchedEffect(top) { nav.back() }
        val shown = spec ?: last
        Sheet(spec != null, nav::close, shown?.title ?: "", shown?.sub, onBack = if (sheets.size > 1) nav::back else null) {
            shown?.content?.invoke(this)
        }

        androidx.compose.animation.AnimatedVisibility(live.rideMode, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            RideMode(s, live, onSettings = { vm.gotoRiding(); page = null; tab = Tab.SETTINGS }) { vm.openRideMode(false) }
        }
        androidx.compose.animation.AnimatedVisibility(live.call != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            CallOverlay(live, vm::answerCall, vm::declineCall, vm::endCall)
        }
    }
}

private val PAGES = setOf("logbook", "fuel", "profile")

/** Every sheet the app can show, by key ("svc:<id>", "help:reminders", "garage"). */
internal fun buildSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav, host: ExtraHost): SheetSpec? {
    logbookSheet(key, s, vm, nav)?.let { return it }
    fuelSheet(key, s, vm, nav)?.let { return it }
    rideSheet(key, s, live, vm, nav)?.let { return it }
    settingsSheet(key, s, live, vm, nav)?.let { return it }
    devSheet(key, s, live, vm, nav, host)?.let { return it }
    linkSheet(key, s, live, vm, nav)?.let { return it }
    extraSheet(key, s, live, vm, nav, host)?.let { return it }
    app.waylink.ui.route.routeSheet(key, s, live, vm, nav)?.let { return it }
    app.waylink.ui.route.navSheet(key, s, live, vm, nav, host)?.let { return it }
    val arg = key.substringAfter(':', "")
    return when (key.substringBefore(':')) {
        "garage" -> SheetSpec("Your bikes", "Garage") {
            GarageList(s, onPick = { vm.switchVehicle(it); nav.close() }, onAdd = { nav.open("vehicle:new") })
        }
        "sos" -> SheetSpec("SOS", "Emergency · ${s.country.name}") { SosSheet(s, live) { nav.close(); host.page("profile") } }
        "clear" -> {
            val wa = arg == "clear-wa"
            val n = if (wa) s.alerts.wa.size else s.alerts.missed.size
            SheetSpec(if (wa) "Clear WhatsApp alerts?" else "Clear missed calls?", "Alerts") {
                Spacer(Modifier.height(12.dp))
                Txt("${app.waylink.core.plural(n, if (wa) "chat" else "call", if (wa) "chats" else "calls")} will be removed here and the count on your bike goes to zero. Your phone keeps its own history.", T.sheetLead, W.c.ink2)
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
                    Txt(p, if (i == 0) T.sheetLead else T.sheetLead.copy(fontSize = 15.sp), if (i == 0) c.ink2 else c.ink3)
                }
                Spacer(Modifier.height(24.dp))
                Btn("Got it", nav::close, Modifier.fillMaxWidth())
            }
        }
        else -> null
    }
}

@Composable
internal fun TabBar(current: Tab, s: AppState, live: Live, modifier: Modifier, onSwipe: (Int) -> Unit = {}, onTab: (Tab) -> Unit) {
    // .tabbar: bg 84 % + a 20 px backdrop blur, hairline, a soft fade above it, padding 12 22 10, tabs up to 76 dp wide.
    // Compose can't blur what's behind a view, so the bar is near-opaque instead: text never shows through sharp.
    val c = W.c
    val v = s.vehicle
    val needs = app.waylink.core.needsYou(v) > 0 || app.waylink.core.fuelStats(v).onReserve
    val alerts = s.alerts.missed.size + s.alerts.wa.size > 0 || needs
    Column(modifier.fillMaxWidth()) {
        // .tabbar::after: content fades out above the bar instead of running into the icons
        Box(Modifier.fillMaxWidth().height(36.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.bg.copy(alpha = 0f), c.bg.copy(alpha = 0.85f)))))
        Column(Modifier.fillMaxWidth().background(c.bg.copy(alpha = 0.985f))) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.inkA(0.09f)))
            Row(
                // Swipe along the bar to page between tabs, one step per swipe (web #tabbar touch handlers).
                Modifier.fillMaxWidth().swipeSides(36.dp, ratio = 1.4f, maxMs = 700, onSwipe = onSwipe)
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                Tab.entries.forEach { t ->
                    val on = t == current
                    val pip: androidx.compose.ui.graphics.Color? = when (t) {
                        Tab.TODAY -> if (needs) c.red else null
                        Tab.ROUTE -> if (live.nav != null) c.sageInk else null
                        Tab.RIDES -> if (live.rec != null) c.accent else null
                        Tab.ALERTS -> if (alerts) c.red else null
                        else -> null
                    }
                    TabItem(t, on, pip, Modifier.weight(1f).widthIn(max = 76.dp)) { onTab(t) }
                }
            }
        }
    }
}

@Composable
private fun TabItem(t: Tab, on: Boolean, pip: androidx.compose.ui.graphics.Color?, modifier: Modifier, onClick: () -> Unit) {
    val c = W.c
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // .tab:active svg { scale(.88) } over .25s spring
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.88f else 1f, tween(250, easing = app.waylink.ui.theme.Spring), label = "tabk")
    // .tab::before: 22 × 3 bar at the bar's top edge, fading in and widening from 30 %
    val bar by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, tween(300, easing = app.waylink.ui.theme.Spring), label = "tabbar")
    val tint by androidx.compose.animation.animateColorAsState(if (on) c.ink else c.ink4, tween(200), label = "tabc")
    Box(
        modifier.semantics { selected = on }
            .clickable(src, indication = null, role = Role.Tab, onClickLabel = null, onClick = onClick)
            .semantics { contentDescription = t.label },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.offset(y = (-12).dp).width(22.dp).height(3.dp)
                .graphicsLayer { alpha = bar; scaleX = 0.3f + 0.7f * bar }
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)).background(c.ink),
        )
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Ic(t.icon, size = 29.dp, tint = tint, modifier = Modifier.graphicsLayer { scaleX = k; scaleY = k })
                // .pip: 7 dp dot with a 2 dp ring of the page colour, top 1, left 50 % + 8
                if (pip != null) Box(
                    Modifier.offset(x = 22.5.dp, y = 1.dp).size(11.dp).clip(CircleShape).background(c.bg).padding(2.dp).clip(CircleShape).background(pip),
                )
            }
            Spacer(Modifier.height(4.dp))
            Txt(t.label, T.tab.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium), tint, maxLines = 1)
        }
    }
}

@Composable
private fun ToastHost(live: Live, clear: () -> Unit) {
    val t = live.toast
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(t?.id) { if (t != null) { delay(2600); clear() } }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 96.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.BottomCenter) {
        // toastIn .4s spring: up 14 px from 96 %; out: fadeOut .25s
        AnimatedVisibility(
            t != null,
            enter = fadeIn(tween(400, easing = app.waylink.ui.theme.Spring)) + androidx.compose.animation.scaleIn(tween(400, easing = app.waylink.ui.theme.Spring), initialScale = 0.96f) +
                slideInVertically(tween(400, easing = app.waylink.ui.theme.Spring)) { (14 * density).toInt() },
            exit = fadeOut(tween(250)),
        ) {
            val c = W.c
            Txt(
                t?.text ?: "", T.row.copy(fontSize = 15.sp, lineHeight = 21.75.sp), c.bg,
                Modifier.shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
                    .clip(RoundedCornerShape(16.dp)).background(c.ink).padding(horizontal = 18.dp, vertical = 12.dp),
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
            Txt(what, T.sheetLead, c.ink2)
        }
    }
}

@Composable
private fun SosSheet(s: AppState, @Suppress("UNUSED_PARAMETER") live: Live, profile: () -> Unit) {
    // web sheet 'sos': a flat list of numbers to tap (the number big on the right), the emergency contact,
    // nearest hospitals, then the blood-group card that opens the profile.
    val c = W.c
    val ctx = LocalContext.current
    val co = s.country
    val p = s.profile
    fun open(i: Intent) = runCatching { ctx.startActivity(i) }
    fun dial(n: String) = open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n.replace(" ", "")))))
    Spacer(Modifier.height(8.dp))
    Txt("Tap to call. If it doesn’t dial, the number is right there.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(16.dp))
    @Composable
    fun tel(n: String, t: String, d: String, ic: String) = ListRow(t, d, ic, chevron = false, onClick = { dial(n) }, trailing = {
        Txt(n, T.big(24f), c.ink, Modifier.padding(start = 10.dp), maxLines = 1)
    })
    ListCard(flat = true) {
        tel(co.amb, "Ambulance", "Medical emergency", "ambulance"); Hairline(start = 66.dp)
        tel(co.sos, "Emergency", "All services", "siren"); Hairline(start = 66.dp)
        tel(co.police, "Police", "Police help", "shield"); Hairline(start = 66.dp)
        tel(co.rsa, "Highway help", "Roadside assistance", "wrench"); Hairline(start = 66.dp)
        // A contact's number is long: it goes under the name instead of crowding it, with a call button at the end.
        if (p.icePhone.isNotEmpty()) ListRow(p.iceName.ifEmpty { "Emergency contact" }, p.icePhone + " · your emergency contact", "heart", chevron = false, onClick = { dial(p.icePhone) }, trailing = {
            Box(Modifier.padding(start = 10.dp).size(36.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) { Ic("phone", size = 17.dp, tint = c.bg) }
        })
        else ListRow("Add an emergency contact", "Someone to call first", "heart", onClick = profile)
        Hairline(start = 66.dp)
        val near = "https://www.google.com/maps/search/hospitals+near+me"
        ListRow("Nearest hospitals", "Opens Google Maps", "hospital", onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(near))) })
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.raised).rowPress(label = "Medical ID", onClick = profile).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt("Blood group ${p.blood.ifEmpty { "—" }}", T.rowSub.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), c.red,
                Modifier.clip(RoundedCornerShape(8.dp)).background(c.tintRed).padding(horizontal = 10.dp, vertical = 6.dp))
            Spacer(Modifier.height(8.dp))
            Txt(p.medical.ifEmpty { "No medical notes yet. Tap to add." }, T.rowSub.copy(fontSize = 14.sp, lineHeight = 20.sp), c.ink3)
        }
        Spacer(Modifier.width(12.dp))
        Ic("chev", size = 16.dp, tint = c.ink4)
    }
}

@Composable
private fun LivePill(live: Live, modifier: Modifier, onClick: () -> Unit) {
    val c = W.c
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); now = System.currentTimeMillis() } }
    val f = remember { app.waylink.core.Fmt() }
    val rec = live.rec; val nav = live.nav
    val blink = androidx.compose.animation.core.rememberInfiniteTransition(label = "pill")
    val a by blink.animateFloat(initialValue = 1f, targetValue = 0.25f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "dot")
    Row(
        modifier.height(32.dp).shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x73000000), spotColor = Color(0x73000000))
            .clip(RoundedCornerShape(16.dp)).background(c.ink).pressable(label = if (nav != null) "Back to the route" else "Back to the ride", onClick = onClick)
            .padding(start = 9.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rec != null) Box(Modifier.size(8.dp).graphicsLayer { alpha = if (app.waylink.ui.theme.Motion.still) 1f else a }.clip(CircleShape).background(c.accent))
        val text = if (nav != null) { val (v, u) = app.waylink.core.distText(nav.stepMeters, f); "$v $u" }
            else if (rec != null) "${f.num(rec.km, 1)} km · ${app.waylink.core.clock((now - rec.start) / 1000.0)}" else ""
        if (nav != null) app.waylink.ui.route.TurnArrow(app.waylink.core.Maneuvers.of(nav.maneuver).glyph, Modifier.size(16.dp, 20.dp), exit = nav.exit)
        Txt(text, T.rowSub.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), c.bg, maxLines = 1)
    }
}
