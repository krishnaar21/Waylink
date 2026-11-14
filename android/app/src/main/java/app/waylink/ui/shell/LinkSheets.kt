package app.waylink.ui.shell

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.vehicleTitle
import app.waylink.data.AppViewModel
import app.waylink.data.BikeLink
import app.waylink.data.Live
import app.waylink.data.LinkState
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Txt
import app.waylink.ui.components.pressable
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.theme.Motion
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** The link sheets: "pair" (web sheet 'bike', the link's state and what to do) and "pick" (choose a bike from a scan). */
internal fun linkSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav, allDevices: Boolean = false): SheetSpec? = when (key) {
    "pair" -> {
        val known = s.vehicle.bike.id != null
        val title = when (live.link) {
            LinkState.CONNECTED -> "Connected"
            LinkState.CONNECTING -> "Connecting"
            LinkState.SCANNING, LinkState.RECONNECTING -> "Searching"
            LinkState.IDLE -> if (known) "Paired" else "Not paired"
        }
        SheetSpec(title, "Bluetooth · ${vehicleTitle(s)}") { BikeSheet(s, live, vm, nav) }
    }
    "pick", "pick-all" -> SheetSpec("Choose your bike", "Bluetooth") { PickSheet(live, vm, nav, key == "pick-all" || allDevices) }
    else -> null
}

@Composable
private fun ColumnScope.BikeSheet(s: AppState, live: Live, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val known = s.vehicle.bike.id != null
    val st = live.link
    val lead = when {
        st == LinkState.CONNECTED -> "${if (live.simulated) "Simulated cluster" else live.linkName ?: s.vehicle.bike.name ?: "Bike cluster"}. ${app.waylink.core.Fmt(s.country.locale).num(live.frameCount.toDouble())} frames sent. Status every 5 s, turns every 0.8 s."
        st == LinkState.CONNECTING -> "Linking with ${live.linkName ?: s.vehicle.bike.name ?: "your bike"}. Keep the ignition on."
        st == LinkState.RECONNECTING -> "The bike went quiet. Waylink keeps trying, so switch on the ignition and it links by itself."
        !vm.btSupported() -> "This phone has no Bluetooth LE. Try the simulated bike to see how it works."
        else -> "Switch on the ignition first, so the display is awake. Then pick your bike from the list."
    }
    Spacer(Modifier.height(12.dp))
    Txt(lead, T.sheetLead, c.ink2)
    Spacer(Modifier.height(24.dp))
    when (st) {
        LinkState.CONNECTED -> Btn("Disconnect", { vm.disconnect(); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
        LinkState.CONNECTING, LinkState.SCANNING, LinkState.RECONNECTING ->
            Btn(if (st == LinkState.CONNECTING) "Cancel" else "Stop", { vm.disconnect() }, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
        LinkState.IDLE -> if (vm.btSupported()) {
            Btn("Pair a bike", { nav.open("pick") }, Modifier.fillMaxWidth(), icon = "bt")
            if (known) { Spacer(Modifier.height(8.dp)); Btn("Reconnect", { vm.reconnect(); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.LINE) }
        }
    }
    if (!live.simulated || st != LinkState.CONNECTED) {
        Spacer(Modifier.height(8.dp))
        Btn("Use the simulated bike", { vm.simOn(); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
    }
    if (known || live.simulated) {
        Spacer(Modifier.height(8.dp))
        Btn("Forget this bike", { vm.forgetBike(); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    }
    // .divider + .sheet-row: the connection log, one tap away
    Spacer(Modifier.height(20.dp))
    Hairline()
    Row(Modifier.fillMaxWidth().pressable(label = "Connection log", scale = 1f) { nav.open("log") }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("Connection log", T.body.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), c.ink, Modifier.weight(1f))
        Ic("chev", size = 16.dp, tint = c.ink3)
    }
}

/**
 * Android's take on the browser's Bluetooth chooser: asks for "Nearby devices" (or location on Android 11 and
 * below), asks to switch Bluetooth on, scans 15 s and lists what answers. Tap one to link.
 */
@Composable
private fun ColumnScope.PickSheet(live: Live, vm: AppViewModel, nav: SheetNav, startAll: Boolean) {
    val c = W.c
    val ctx = LocalContext.current
    var all by rememberSaveable { mutableStateOf(startAll) }
    var granted by remember { mutableStateOf(BikeLink.hasPermissions(ctx)) }
    var denied by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(vm.btEnabled()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r.values.all { it }; denied = !granted
    }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { on = vm.btEnabled() }
    LaunchedEffect(Unit) { if (!granted) ask.launch(BikeLink.PERMISSIONS) }
    LaunchedEffect(granted, on, all) {
        if (!granted) return@LaunchedEffect
        if (!on) { runCatching { enable.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) }; return@LaunchedEffect }
        vm.pair(all)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (vm.live.value.link == LinkState.SCANNING) vm.stopScan() } }

    Spacer(Modifier.height(12.dp))
    when {
        !granted -> {
            Txt(if (denied) "Waylink needs “Nearby devices” to find your bike. Nothing else is looked at, and your location is never used for this." else "Allow “Nearby devices” so Waylink can find your bike.", T.sheetLead, c.ink2)
            Spacer(Modifier.height(20.dp))
            Btn(if (denied) "Open app settings" else "Allow", {
                if (denied) runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
                else ask.launch(BikeLink.PERMISSIONS)
            }, Modifier.fillMaxWidth(), icon = "bt")
            return
        }
        !on -> {
            Txt("Bluetooth is off. Switch it on to look for your bike.", T.sheetLead, c.ink2)
            Spacer(Modifier.height(20.dp))
            Btn("Turn on Bluetooth", { runCatching { enable.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) } }, Modifier.fillMaxWidth(), icon = "bt")
            return
        }
    }
    val scanning = live.link == LinkState.SCANNING
    Txt(if (all) "Every Bluetooth device nearby. Your bike's cluster may show under a short code." else "Switch on the ignition, so the display is awake. Bikes appear here as they answer.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(16.dp))
    ListCard(flat = true) {
        live.found.forEachIndexed { i, b ->
            if (i > 0) Hairline(start = 66.dp)
            ListRow(b.name, "${b.address} · ${signal(b.rssi)}", "bt", onClick = { vm.connectTo(b); nav.close() })
        }
        if (live.found.isNotEmpty() && scanning) Hairline(start = 66.dp)
        if (scanning) SearchingRow()
        else if (live.found.isEmpty()) ListRow("No bike found", "Ignition on? Then search again.", "btSearch", chevron = false)
    }
    Spacer(Modifier.height(20.dp))
    if (!scanning) {
        Btn("Search again", { vm.pair(all) }, Modifier.fillMaxWidth(), icon = "btSearch")
        Spacer(Modifier.height(8.dp))
    }
    Btn(if (all) "Show bikes only" else "Show all devices", { vm.stopScan(); all = !all }, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
}

@Composable
private fun SearchingRow() {
    val c = W.c
    val a = if (Motion.still) 1f else rememberInfiniteTransition(label = "scan").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "scan").value
    ListRow("Looking for bikes…", "Up to 15 seconds", "btSearch", chevron = false, trailing = {
        Txt("●", T.rowSub, c.accent, Modifier.alpha(a).padding(start = 8.dp))
    })
    Spacer(Modifier.width(0.dp))
}

private fun signal(rssi: Int) = when {
    rssi >= -60 -> "strong signal"
    rssi >= -75 -> "good signal"
    else -> "weak signal"
}
