package app.waylink.ui.route

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.waylink.core.AppState
import app.waylink.core.NavSource
import app.waylink.core.Place
import app.waylink.core.Platform
import app.waylink.core.SpeedAlert
import app.waylink.data.AppViewModel
import app.waylink.data.BikeLink
import app.waylink.data.Live
import app.waylink.data.NavApps
import app.waylink.data.NavFeed
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Chip
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Ic
import app.waylink.ui.components.IconTile
import app.waylink.ui.components.Input
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.shell.ExtraHost
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * Live turns, saved places, the speed alert and the permissions hub. Every part asks [Platform.caps] first, so an
 * iPhone build hides what iOS can't do (reading another app's turns) instead of showing a dead switch.
 */

/** Ticks each time the app comes back to the front, so states that change in Android settings are read again. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return tick
}

/** One thing Waylink may need Android to allow. [required] ones count as "needed"; the rest are suggestions. */
data class Perm(val key: String, val title: String, val why: String, val icon: String, val ok: Boolean, val required: Boolean)

object Perms {
    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun list(ctx: Context, s: AppState): List<Perm> = buildList {
        val loc = granted(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Perm("location", "Location", "Records rides, saves where you parked, weather here", "pin", loc, true))
        if (Platform.caps.bluetooth) add(Perm("nearby", "Nearby devices", "Finds and links your bike over Bluetooth", "bt", BikeLink.hasPermissions(ctx), true))
        if (Platform.caps.readNavApps) {
            val src = NavSource.of(s.settings.navSource)
            add(Perm("navaccess", "Notification access", if (src.readsNotifications) "Reads ${src.name}’s turn card for the bike" else "Only for live turns from a maps app", "bell", NavFeed.allowed(ctx), src.readsNotifications))
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        add(Perm("battery", "Battery unrestricted", "Keeps the bike linked with the screen off", "battery", pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true, false))
    }

    fun needed(ctx: Context, s: AppState) = list(ctx, s).count { it.required && !it.ok }

    fun openAppSettings(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openBattery(ctx: Context) {
        val ok = runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok) openAppSettings(ctx)
    }
}

/** "nav-source", "place:<id|new>", "speed", "perms". */
fun navSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav, host: ExtraHost): SheetSpec? {
    val arg = key.substringAfter(':', "")
    return when (key.substringBefore(':')) {
        "nav-source" -> SheetSpec("Live turns from", "Route") { SourceBody(s, vm) }
        "place" -> {
            val p = s.places.firstOrNull { it.id == arg }
            if (arg != "new" && p == null) return null
            SheetSpec(if (p == null) "New place" else p.name, "Saved places") { PlaceBody(s, p, vm, nav, host) }
        }
        "speed" -> SheetSpec("Speed alert", "Riding") { SpeedBody(s, vm) }
        "perms" -> SheetSpec("Permissions", "What Waylink can use") { PermsBody(s) }
        "nearby" -> {
            val k = app.waylink.data.Nearby.Kind.of(arg) ?: return null
            SheetSpec("${k.title} nearby", "Nearest first") { NearbyBody(k, s, live, vm, host) }
        }
        else -> null
    }
}

@Composable
private fun SourceBody(s: AppState, vm: AppViewModel) {
    val c = W.c
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val cur = NavSource.of(s.settings.navSource)
    val allowed = remember(tick) { NavFeed.allowed(ctx) }
    Spacer(Modifier.height(8.dp))
    Txt("While your maps app guides, Waylink reads its turn card and sends the arrow and distance to the bike. Only that card is read, and nothing is kept.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(18.dp))
    ListCard(flat = true) {
        NavSource.available().forEachIndexed { i, src ->
            if (i > 0) Hairline(66.dp)
            val have = src.packages.isEmpty() || remember(tick, src.key) { NavApps.installed(ctx, src) }
            ListRow(
                src.name, if (have) src.note else "Not on this phone", icon = if (src.readsNotifications) "map" else "route",
                chevron = false, onClick = { vm.setNavSource(src.key) },
                trailing = { if (src.key == cur.key) Ic("check", size = 20.dp, tint = c.accent) },
            )
        }
    }
    if (cur.readsNotifications) {
        Spacer(Modifier.height(18.dp))
        if (allowed) StatusLine(true, "Notification access is on. Start navigating in ${cur.name}.")
        else {
            StatusLine(false, "Waylink needs notification access to read ${cur.name}.")
            Spacer(Modifier.height(14.dp))
            Btn("Allow notification access", { NavFeed.openAccess(ctx) }, Modifier.fillMaxWidth(), icon = "bell")
            Hint("Android opens its list. Switch Waylink on, then come back.")
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    val c = W.c
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) c.sage else c.amber))
        Spacer(Modifier.width(10.dp))
        Txt(text, T.row, c.ink2)
    }
}

@Composable
private fun PlaceBody(s: AppState, p: Place?, vm: AppViewModel, nav: SheetNav, host: ExtraHost) {
    val c = W.c
    val ctx = LocalContext.current
    val key = p?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(p?.name ?: "") }
    var kind by rememberSaveable(key) { mutableStateOf(p?.kind ?: "pin") }
    var address by rememberSaveable(key) { mutableStateOf(p?.address ?: "") }
    var lat by rememberSaveable(key) { mutableStateOf(p?.lat) }
    var lng by rememberSaveable(key) { mutableStateOf(p?.lng) }
    // The address text the saved spot belongs to; typing something else lets go of it.
    var spotFor by rememberSaveable(key) { mutableStateOf(if (p?.lat != null) p.address else null) }
    var busy by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<app.waylink.data.PlaceSearch.Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(busy) { if (busy) { kotlinx.coroutines.delay(20_000); busy = false } }
    // Suggestions while typing: a short pause first, so a search goes out per word, not per letter.
    androidx.compose.runtime.LaunchedEffect(address) {
        val q = address.trim()
        if (q.length < 3 || q == spotFor) { hits = emptyList(); searching = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(450)
        searching = true
        val near = s.profile.weatherAt
        hits = app.waylink.data.PlaceSearch.search(ctx, q, near?.lat ?: s.vehicle.parking?.lat, near?.lon ?: s.vehicle.parking?.lng)
        searching = false
    }

    FormField("Name") { Input(name, { name = it }, placeholder = "Chai stop", label = "Name", caps = KeyboardCapitalization.Words, maxLength = 30) }
    FormField("Kind") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("home" to "Home", "work" to "Work", "pin" to "Other").forEach { (k, t) ->
                Chip(t, {
                    kind = k
                    if (name.isBlank() || name == "Home" || name == "Work") name = if (k == "pin") "" else t
                }, Modifier.weight(1f), on = kind == k)
            }
        }
    }
    FormField("Address") {
        Input(address, { x ->
            address = x
            if (spotFor != null && x.trim() != spotFor) { lat = null; lng = null; spotFor = null }
        }, placeholder = "Search a place or address", label = "Address", caps = KeyboardCapitalization.Words, maxLength = 120)
    }
    if (hits.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        ListCard(flat = true) {
            hits.forEachIndexed { i, h ->
                if (i > 0) Hairline(66.dp)
                ListRow(h.title, h.detail.ifEmpty { null }, icon = "pin", chevron = false, onClick = {
                    address = h.label; lat = h.lat; lng = h.lng; spotFor = h.label; hits = emptyList()
                    if (name.isBlank()) name = h.title.take(30)
                })
            }
        }
    } else if (searching) {
        Txt("Searching…", T.rowSub, c.ink3, Modifier.padding(top = 8.dp, start = 4.dp))
    }
    Spacer(Modifier.height(12.dp))
    val pinned = lat != null && lng != null
    if (pinned) {
        Row(Modifier.fillMaxWidth().card(16.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic("check", size = 18.dp, tint = c.sageInk)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Txt("Exact spot saved", T.rowTitle.copy(fontSize = 14.sp), c.ink)
                Txt("%.5f, %.5f".format(java.util.Locale.ROOT, lat, lng), T.plate.copy(fontSize = 11.5.sp), c.ink3)
            }
            Txt("Clear", T.link, c.ink2, Modifier.pressable(label = "Clear exact spot") { lat = null; lng = null; spotFor = null })
        }
    } else {
        Btn(if (busy) "Finding you…" else "Use my current location", {
            if (!busy) host.withLocation {
                busy = true
                vm.placeHere { la, ln, label ->
                    busy = false; lat = la; lng = ln
                    if (address.isBlank() && label.isNotBlank()) address = label
                    spotFor = address.trim()
                }
            }
        }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "pin")
    }
    Hint(if (pinned) "Navigation goes straight to this spot." else "Pick a suggestion, or use where you are now, so navigation goes straight there. A typed address is searched in your maps app.")
    Spacer(Modifier.height(24.dp))
    Btn("Save", {
        val err = vm.savePlace(p?.id, name, kind, address, lat, lng)
        if (err != null) vm.toast(err) else nav.close()
    }, Modifier.fillMaxWidth())
    if (p != null) {
        Spacer(Modifier.height(8.dp))
        Btn("Delete place", { vm.deletePlace(p.id); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    }
}

@Composable
private fun SpeedBody(s: AppState, vm: AppViewModel) {
    val c = W.c
    val limit = s.settings.speedAlert
    Spacer(Modifier.height(8.dp))
    Txt("A firm double buzz and a horn note when you pass it. Then quiet until you’re back under.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Step(false, "Lower", enabled = limit > 0) { vm.setSpeedAlert(if (limit <= 30) 0 else limit - 5) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(if (limit == 0) "Off" else "$limit", T.big(40f), c.ink, maxLines = 1)
            Txt(if (limit == 0) "No alert" else "km/h", T.rowSub, c.ink3)
        }
        Step(true, "Higher", enabled = limit < 200) { vm.setSpeedAlert(if (limit == 0) 60 else limit + 5) }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeedAlert.PRESETS.forEach { k -> Chip(if (k == 0) "Off" else "$k", { vm.setSpeedAlert(k) }, Modifier.weight(1f), on = limit == k) }
    }
    Hint("Works while GPS runs: when a ride is recording. Speed from GPS can read a little off.")
}

@Composable
private fun Step(plus: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = W.c
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.raised).pressable(enabled = enabled, label = label, scale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) c.ink else c.ink4
        if (plus) Ic("plus", size = 18.dp, tint = tint)
        else Box(Modifier.size(14.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(tint))
    }
}

@Composable
private fun PermsBody(s: AppState) {
    val c = W.c
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var asked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var after by remember { mutableIntStateOf(0) }
    val perms = remember(tick, after, s.settings.navSource) { Perms.list(ctx, s) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { after++ }
    val need = perms.count { it.required && !it.ok }
    Spacer(Modifier.height(8.dp))
    Txt(if (need == 0) "Everything Waylink needs is allowed." else "$need ${if (need == 1) "thing needs" else "things need"} your OK. Each one only unlocks what it says.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(18.dp))
    ListCard(flat = true) {
        perms.forEachIndexed { i, p ->
            if (i > 0) Hairline(66.dp)
            val act: (() -> Unit)? = if (p.ok) null else ({
                when (p.key) {
                    "location", "nearby" -> {
                        // Android stops showing its prompt after a refusal; the second tap goes to settings.
                        if (p.key in asked) Perms.openAppSettings(ctx)
                        else {
                            asked = asked + p.key
                            launcher.launch(if (p.key == "location") arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION) else BikeLink.PERMISSIONS)
                        }
                    }
                    "navaccess" -> NavFeed.openAccess(ctx)
                    "battery" -> Perms.openBattery(ctx)
                }
            })
            ListRow(p.title, p.why, icon = p.icon, chevron = !p.ok, onClick = act, trailing = { PermPill(p) })
        }
    }
    Hint("Changes made in Android settings show here when you come back.")
}

@Composable
private fun PermPill(p: Perm) {
    val c = W.c
    val (t, fg, bg) = when {
        p.ok -> Triple("Allowed", c.sageInk, c.tintSage)
        p.required -> Triple("Needed", c.red, c.tintRed)
        else -> Triple("Suggested", c.ink3, c.raised)
    }
    Txt(t, T.rowSub.copy(fontSize = 12.sp), fg, Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp))
}

/** The Route tab's live-turns card: which app, and whether it's ready. Android only. */
@Composable
fun LiveTurnsCard(s: AppState, openSheet: (String) -> Unit) {
    if (!Platform.caps.readNavApps) return
    val c = W.c
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val src = NavSource.of(s.settings.navSource)
    val allowed = remember(tick) { NavFeed.allowed(ctx) }
    val (state, ok) = when {
        !src.readsNotifications -> "Off · Waylink only" to null
        !allowed -> "${src.name} · Needs access" to false
        else -> "${src.name} · Ready" to true
    }
    Column(Modifier.fillMaxWidth().card(20.dp).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile("nav", size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt("Live turns", T.rowTitle, c.ink, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ok != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) c.sage else c.amber)); Spacer(Modifier.width(7.dp)) }
                    Txt(state, T.rowSub, c.ink3, maxLines = 1)
                }
            }
            Chip("Change", { openSheet("nav-source") })
        }
        if (ok == false) {
            Spacer(Modifier.height(14.dp))
            Btn("Allow notification access", { NavFeed.openAccess(ctx) }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "bell", small = true)
        }
    }
}

/** Saved places on the Route tab: tap to navigate there in the chosen app, the pencil to edit. */
@Composable
fun PlacesCard(s: AppState, toast: (String) -> Unit, openSheet: (String) -> Unit) {
    val c = W.c
    val ctx = LocalContext.current
    val src = NavSource.of(s.settings.navSource)
    val order = mapOf("home" to 0, "work" to 1)
    val places = s.places.sortedWith(compareBy<Place>({ order[it.kind] ?: 2 }, { -it.added }))
    ListCard {
        places.forEachIndexed { i, p ->
            if (i > 0) Hairline(66.dp)
            val icon = when (p.kind) { "home" -> "home"; "work" -> "flag"; else -> "pin" }
            ListRow(
                p.name, p.address.ifBlank { "Pinned spot" }, icon = icon, chevron = false,
                onClick = { if (!NavApps.go(ctx, src, p.address.ifBlank { p.name }, p.lat, p.lng)) toast("No app can open maps.") },
                trailing = {
                    Box(Modifier.size(36.dp).clip(CircleShape).pressable(label = "Edit ${p.name}", scale = 0.9f) { openSheet("place:${p.id}") }, contentAlignment = Alignment.Center) {
                        Ic("edit", size = 18.dp, tint = c.ink3)
                    }
                },
            )
        }
        if (places.isNotEmpty()) Hairline(66.dp)
        ListRow(if (places.isEmpty()) "Add a place" else "Add another", if (places.isEmpty()) "Home, work, the chai stop" else null, icon = "plus", onClick = { openSheet("place:new") })
    }
}

@Composable
private fun NearbyBody(kind: app.waylink.data.Nearby.Kind, s: AppState, live: Live, vm: AppViewModel, host: ExtraHost) {
    val c = W.c
    val ctx = LocalContext.current
    // The chosen maps app, or Google Maps when Waylink only rehearses.
    val src = NavSource.of(s.settings.navSource).let { if (it.readsNotifications) it else NavSource.of("gmaps") }
    var spots by remember { mutableStateOf<List<app.waylink.data.Nearby.Spot>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val load = {
        if (!busy) { busy = true; err = null; host.withLocation { vm.findNearby(kind) { list, why -> spots = list; err = why; busy = false } } }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { load() }
    androidx.compose.runtime.LaunchedEffect(busy) { if (busy) { kotlinx.coroutines.delay(45_000); busy = false } }
    val list = spots
    val failed = err != null || (list != null && list.isEmpty())
    Spacer(Modifier.height(8.dp))
    when {
        list == null && err == null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.amber))
            Spacer(Modifier.width(10.dp))
            Txt(if (!busy) "Allow location to see what’s near you." else if (live.locating) "Finding where you are…" else "Looking for ${kind.one}s near you…", T.sheetLead, c.ink2)
        }
        err != null -> {
            Txt(err ?: "", T.sheetLead, c.ink2)
            Spacer(Modifier.height(14.dp))
            Btn("Try again", { load() }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "refresh")
        }
        list != null && list.isEmpty() -> Txt("No ${kind.one}s found within 15 km on OpenStreetMap. Your maps app may know more.", T.sheetLead, c.ink2)
        list != null -> {
            ListCard(flat = true) {
                list.forEachIndexed { i, p ->
                    if (i > 0) Hairline(66.dp)
                    val d = if (p.meters < 1000) "${(Math.round(p.meters / 10) * 10)} m" else "%.1f km".format(java.util.Locale.ROOT, p.meters / 1000)
                    ListRow(
                        p.name, listOf(d, p.detail).filter { it.isNotEmpty() }.joinToString(" · "), icon = kind.icon, chevron = false,
                        onClick = { if (!NavApps.go(ctx, src, p.name, p.lat, p.lng)) vm.toast("No app can open maps.") },
                        trailing = p.phone?.let { ph -> {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(c.raised).pressable(label = "Call ${p.name}", scale = 0.9f) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }, contentAlignment = Alignment.Center) { Ic("phone", size = 17.dp, tint = c.ink) }
                        } },
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    // When the list can't help, the maps app's own search is the main way on.
    Btn("Search in ${src.name}", { if (!NavApps.search(ctx, src, kind.search)) vm.toast("No app can open maps.") }, Modifier.fillMaxWidth(), kind = if (failed) BtnKind.INK else BtnKind.LINE, icon = "map")
    Hint("From OpenStreetMap, so a few may be missing. Tap one to navigate there${if (kind == app.waylink.data.Nearby.Kind.HOSPITAL) ". In an emergency, call 112 first" else ""}.")
}

/** What stops turns or calls reaching the bike, worst first; null when nothing does. Key is the sheet that fixes it. */
data class Attention(val text: String, val key: String)

fun attentionOf(ctx: Context, s: AppState, live: Live): Attention? {
    val v = s.vehicle
    if (!live.simulated && Platform.caps.bluetooth && v.bike.id != null) {
        val bt = runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter?.isEnabled }.getOrNull()
        if (bt == false) return Attention("Bluetooth is off", "pair")
    }
    val need = Perms.needed(ctx, s)
    if (need > 0) return Attention(if (need == 1) "1 permission needed" else "$need permissions needed", "perms")
    val online = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)
    if (!online) return Attention("Offline · weather and search wait", "")
    return null
}

/** A small amber chip under a page title when something needs the rider; tap to fix it. */
@Composable
fun AttentionChip(s: AppState, live: Live, open: (String) -> Unit) {
    val c = W.c
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var beat by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10_000); beat++ } }
    val a = remember(tick, beat, s.settings.navSource, live.simulated, s.vehicle.bike.id) { attentionOf(ctx, s, live) } ?: return
    Row(
        Modifier.padding(top = 14.dp).clip(RoundedCornerShape(999.dp)).background(c.tintAmber)
            .then(if (a.key.isEmpty()) Modifier else Modifier.pressable(label = "${a.text}. Fix it") { open(a.key) }).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic("alert", size = 15.dp, tint = c.ink)
        Spacer(Modifier.width(7.dp))
        Txt(a.text, T.rowSub.copy(fontSize = 13.sp), c.ink, maxLines = 1)
        if (a.key.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Ic("chev", size = 13.dp, tint = c.ink3) }
    }
}
