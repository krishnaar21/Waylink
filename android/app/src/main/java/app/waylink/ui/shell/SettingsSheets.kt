package app.waylink.ui.shell

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Catalog
import app.waylink.core.Fmt
import app.waylink.core.modelName
import app.waylink.core.vehicleTitle
import app.waylink.data.AppViewModel
import app.waylink.data.Live
import app.waylink.ui.components.BikeArt
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Chip
import app.waylink.ui.components.DateField
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Input
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Select
import app.waylink.ui.components.Two
import app.waylink.ui.components.Txt
import app.waylink.ui.components.avatarRes
import app.waylink.ui.components.card
import app.waylink.ui.components.parseNum
import app.waylink.ui.components.pressable
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import java.nio.charset.StandardCharsets

const val REPO_URL = "https://github.com/krishnaar21/Waylink"
const val SUPPORT_EMAIL = "support@krishnaar.work"

/** Settings and Profile sheets, or null. Keys follow the web app. */
fun settingsSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav): SheetSpec? {
    val arg = key.substringAfter(':', "")
    return when (key.substringBefore(':')) {
        "vehicle" -> {
            val isNew = arg == "new"
            if (!isNew && s.vehicles.none { it.id == arg }) return null
            SheetSpec(if (isNew) "Add a vehicle" else "Edit vehicle", "Garage") { VehicleBody(s, if (isNew) null else arg, vm, nav) }
        }
        "veh-del" -> {
            val v = s.vehicles.firstOrNull { it.id == arg } ?: return null
            SheetSpec("Remove ${vehicleTitle(s, v)}?", "Garage") {
                Confirm("Its fuel log, rides and service history go with it. Export a backup first if you might need them.", "Remove", "Keep it", { vm.deleteVehicle(v.id); nav.close() }, nav::back)
            }
        }
        "specs" -> {
            val m = s.model
            SheetSpec(modelName(s, m), m.series) {
                val c = W.c
                val f = Fmt(s.country.locale)
                Spacer(Modifier.height(8.dp))
                Txt(m.engine, T.sheetLead, c.ink2)
                Spacer(Modifier.height(16.dp))
                // Web: .card with box-shadow none on raised paper, so only the hairline ring shows.
                Column(Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(c.raised).padding(horizontal = 18.dp, vertical = 2.dp)) {
                    listOf(
                        "Fuel tank" to "${f.num(m.tank, 1)} L · ${f.num(m.reserve, 1)} L reserve",
                        "Engine oil" to m.oil,
                        "Coolant" to m.coolant,
                        "Tyres" to "${m.tyres[0]} front · ${m.tyres[1]} rear",
                        "Pressure, solo" to "${m.psi[0]} front · ${m.psi[2]} rear psi",
                        "With pillion" to "${m.psi[1]} front · ${m.psi[3]} rear psi",
                        "Chain" to "${m.chain}, lube every 500 km",
                        "Spark plug" to m.plug,
                    ).forEachIndexed { i, (k, v) ->
                        if (i > 0) Hairline()
                        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
                            Txt(k, T.row.copy(fontSize = 15.5.sp, lineHeight = 22.5.sp), c.ink2, Modifier.alignByBaseline())
                            Spacer(Modifier.width(16.dp))
                            Txt(v, T.row.copy(fontSize = 15.5.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End), c.ink, Modifier.weight(1f).alignByBaseline())
                        }
                    }
                }
                Hint("Cold tyre pressures. Check your owner’s manual for your variant.")
            }
        }
        "country" -> SheetSpec("Country", "Region") {
            val c = W.c
            Spacer(Modifier.height(8.dp))
            Txt("Sets currency, fuel price and emergency numbers.", T.sheetLead, c.ink2)
            Spacer(Modifier.height(16.dp))
            ListCard(flat = true) {
                Catalog.COUNTRIES.forEachIndexed { i, co ->
                    if (i > 0) Hairline(16.dp)
                    ListRow(co.name, "${co.cur} ${co.curCode} · SOS ${co.sos}", chevron = false, onClick = { vm.setCountry(co.code); nav.close() }, trailing = { Check(co.code == s.profile.country) })
                }
            }
        }
        "pick-ridechart" -> SheetSpec("Ride chart", "Appearance") {
            val c = W.c
            Spacer(Modifier.height(8.dp))
            val ch = remember(s.vehicle.trips) { app.waylink.core.rideCharts(s.vehicle.trips) }
            val f = remember(s.country.locale) { app.waylink.core.Fmt(s.country.locale) }
            Txt("How the Rides tab draws your riding. You can also swipe the chart there." + if (ch.demo) " Shown with example rides until you have your own." else "", T.sheetLead, c.ink2)
            Spacer(Modifier.height(16.dp))
            // .rcopts: each chart drawn in full as its own choice, the picked one ringed in ink
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RIDE_CHARTS.indices.forEach { i ->
                    app.waylink.ui.rides.RideChartOption(ch, i, s.settings.rideChart == i, f) { vm.setRideChart(i) }
                }
            }
        }
        "backup" -> SheetSpec("Backup & restore", "Data") { BackupBody(vm, nav) }
        "copy" -> SheetSpec("Your backup", "Backup") {
            val c = W.c
            val board = LocalClipboardManager.current
            val text = remember { vm.backupText() }
            Spacer(Modifier.height(8.dp))
            Txt("Copy it somewhere safe, or paste it into Waylink on your new phone.", T.sheetLead, c.ink2)
            Spacer(Modifier.height(16.dp))
            Txt(text.take(1200) + if (text.length > 1200) "…" else "", T.plate.copy(fontSize = 11.sp, lineHeight = 16.sp), c.ink2,
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(c.raised).padding(14.dp))
            Spacer(Modifier.height(12.dp))
            Btn("Copy", { board.setText(AnnotatedString(text)); vm.toast("Backup copied.") }, Modifier.fillMaxWidth(), icon = "copy")
            Hint("It holds your profile and every bike’s history. Keep it private.")
        }
        "paste" -> SheetSpec("Restore from text", "Backup") {
            val c = W.c
            var text by remember { mutableStateOf("") }
            Spacer(Modifier.height(8.dp))
            Txt("Paste a backup you copied from Waylink. It replaces everything on this phone.", T.sheetLead, c.ink2)
            FormField("Backup") { Input(text, { text = it }, placeholder = "{\"format\":\"waylink-vault\", …}", label = "Backup text", multiline = true, maxLength = 5_000_000, caps = KeyboardCapitalization.None) }
            Spacer(Modifier.height(16.dp))
            Btn("Restore", { if (text.isBlank()) vm.toast("Paste a backup first.") else if (vm.restore(text)) nav.close() }, Modifier.fillMaxWidth())
        }
        "erase" -> SheetSpec(if (s.sample) "Erase sample data?" else "Erase everything?", "Backup") {
            Confirm("Bikes, rides, fuel, service and alerts on this phone will go. Export a backup first if you want to keep them.", "Erase", "Keep my data", { nav.close(); vm.eraseAll() }, nav::close)
        }
        "reset" -> SheetSpec("Reset Waylink?", "Backup") {
            Confirm("Everything on this phone goes: bikes, rides, fuel, service, your profile, theme and every setting. The app starts again like a fresh install. Export a backup first if you might want any of it.", "Reset everything", "Cancel", { nav.close(); vm.resetAll() }, nav::close)
        }
        "faq" -> SheetSpec("Questions and answers", "Help") { Faq() }
        "report" -> SheetSpec("Report a problem", "Help") { ReportBody(s, live, vm) }
        "about" -> SheetSpec("About Waylink", "Turns, calls and bike care · v$APP_VERSION") { AboutBody(nav) }
        "models" -> SheetSpec("Bike models", "About") {
            Spacer(Modifier.height(16.dp))
            ListCard(flat = true) {
                Catalog.MODELS.forEachIndexed { i, m ->
                    if (i > 0) Hairline(16.dp)
                    ListRow(modelName(s, m), m.series, chevron = false)
                }
            }
        }
        "support" -> SheetSpec("Support Waylink", null) { SupportBody(vm, nav) }
        "avatar" -> SheetSpec("Your picture", "Profile") {
            // .av-grid: eleven riders and a Photo tile, four to a row. The picked one grows (1.12) with an accent ring,
            // the rest sit at .9 with their face at .8.
            val c = W.c
            val p = s.profile
            val cur = if (p.photo != null) "photo" else p.avatar
            val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) { vm.setPhoto(uri); nav.close() }
            }
            Spacer(Modifier.height(8.dp))
            Txt("Pick a rider, or add a photo.", T.sheetLead, c.ink2)
            Spacer(Modifier.height(16.dp))
            ((1..11).map { "a$it" } + "photo").chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        val on = cur == k
                        val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1.12f else 0.9f, androidx.compose.animation.core.tween(250, easing = app.waylink.ui.theme.Spring), label = "av")
                        val tap = {
                            if (k == "photo") pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else { vm.setAvatar(k); nav.close() }
                        }
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).zIndex(if (on) 1f else 0f).graphicsLayer { scaleX = sc; scaleY = sc }
                                .semantics { selected = on }.pressable(label = if (k == "photo") "Use a photo" else "Rider ${k.drop(1)}", role = Role.RadioButton, onClick = tap)
                                .then(if (on) Modifier.border(1.75.dp, c.accent.copy(alpha = 0.65f), CircleShape) else Modifier)
                                .clip(CircleShape)
                                .then(
                                    if (k == "photo" && p.photo == null) Modifier.background(c.raised).dashedCircle(c.inkA(0.45f))
                                    else Modifier.background(Color.White),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (k == "photo") {
                                val pic = app.waylink.ui.components.rememberPhoto(p.photo)
                                if (pic != null) Image(pic, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Ic("camera", size = 24.dp, tint = c.ink)
                                    Spacer(Modifier.height(3.dp))
                                    Txt("Photo", T.rowSub.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight(650)), c.ink)
                                }
                            } else Image(painterResource(avatarRes(k)), null, Modifier.fillMaxWidth(if (on) 0.95f else 0.8f).aspectRatio(1f))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        "prof" -> when (arg) {
            "about" -> SheetSpec("About you", "Profile") { ProfAbout(s, vm, nav) }
            "licence" -> SheetSpec("Driving licence", "Profile") { ProfLicence(s, vm, nav) }
            "medical" -> SheetSpec("Medical ID", "Profile") { ProfMedical(s, vm, nav) }
            else -> null
        }
        else -> null
    }
}

@Composable
private fun Check(on: Boolean) {
    val c = W.c
    if (on) Box(Modifier.size(20.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) { Ic("check", size = 12.dp, tint = c.bg) }
}

@Composable
private fun Confirm(text: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Txt(text, T.sheetLead, W.c.ink2)
    Spacer(Modifier.height(24.dp))
    Btn(yes, onYes, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    Spacer(Modifier.height(8.dp))
    Btn(no, onNo, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
}

@Composable
private fun VehicleBody(s: AppState, id: String?, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val v = id?.let { x -> s.vehicles.firstOrNull { it.id == x } }
    val key = id ?: "new"
    var model by rememberSaveable(key) { mutableStateOf(v?.model ?: "ns200") }
    var nick by rememberSaveable(key) { mutableStateOf(v?.nickname ?: "") }
    var plate by rememberSaveable(key) { mutableStateOf(v?.plate ?: "") }
    var odo by rememberSaveable(key) { mutableStateOf(v?.odo?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var colour by rememberSaveable(key) { mutableStateOf(v?.colour ?: Catalog.COLOURS[s.vehicles.size % Catalog.COLOURS.size].first) }
    var picking by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    BikeArt(Modifier.fillMaxWidth().height(150.dp), 20.dp)
    FormField("Model") { Select(modelName(s, Catalog.model(model)), { picking = !picking }, label = "Model") }
    AnimatedVisibility(picking) {
        Column(Modifier.padding(top = 8.dp).fillMaxWidth().card(20.dp, c.raised)) {
            Catalog.MODELS.map { it.series }.distinct().forEach { series ->
                Txt(series, T.eyebrow, c.ink3, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
                Catalog.MODELS.filter { it.series == series }.forEach { m ->
                    ListRow(modelName(s, m), chevron = false, onClick = { model = m.id; picking = false }, trailing = { Check(m.id == model) })
                }
            }
        }
    }
    Two(
        { m -> FormField("Nickname", m) { Input(nick, { nick = it }, placeholder = "Daily", label = "Nickname", caps = KeyboardCapitalization.Words, maxLength = 20) } },
        { m -> FormField("Number plate", m) { Input(plate, { plate = it.uppercase() }, placeholder = "KA 05 MX 2481", label = "Number plate", caps = KeyboardCapitalization.Characters, maxLength = 13) } },
    )
    FormField("Odometer") { Input(odo, { x -> odo = x.filter { it.isDigit() || it == '.' }.take(9) }, placeholder = "0", suffix = "km", label = "Odometer", keyboard = KeyboardType.Decimal) }
    FormField("Colour") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Catalog.COLOURS.forEach { (hex, label) ->
                val on = hex == colour
                val col = Color(android.graphics.Color.parseColor(hex))
                Box(
                    Modifier.size(40.dp).semantics { selected = on }.pressable(label = label, role = Role.RadioButton) { colour = hex }
                        .then(if (on) Modifier.border(2.dp, c.ink, CircleShape) else Modifier).padding(4.dp).clip(CircleShape).background(col)
                        .border(1.dp, c.line, CircleShape),
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Btn(if (id == null) "Add to garage" else "Save", {
        val err = vm.saveVehicle(id, model, nick, plate, parseNum(odo), colour)
        if (err != null) vm.toast(err) else nav.close()
    }, Modifier.fillMaxWidth())
    if (id != null && s.vehicles.size > 1) {
        Spacer(Modifier.height(8.dp))
        Btn("Remove from garage", { nav.open("veh-del:$id") }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    }
}

@Composable
private fun BackupBody(vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val ctx = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.backupText().toByteArray(StandardCharsets.UTF_8)) }
            vm.toast("Backup saved.")
            nav.close()
        } catch (e: Exception) { vm.toast("Couldn’t save the file.") }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(20 * 1024 * 1024 + 1) }
            when {
                bytes == null -> vm.toast("Couldn’t open the file.")
                bytes.size > 20 * 1024 * 1024 -> vm.toast("That file is too big to be a backup.")
                vm.restore(String(bytes, StandardCharsets.UTF_8)) -> nav.close()
            }
        } catch (e: Exception) { vm.toast("Couldn’t open the file.") }
    }
    Spacer(Modifier.height(8.dp))
    Txt("Everything stays on this phone. Keep a copy, or move it to a new phone.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(16.dp))
    ListCard(flat = true) {
        ListRow("Export backup", "Save one .json file", icon = "down", onClick = { save.launch("waylink-backup-${java.time.LocalDate.now()}.json") })
        Hairline(66.dp)
        ListRow("Restore a backup", "From an exported file", icon = "up", onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        Hairline(66.dp)
        ListRow("Copy as text", "Paste it on your new phone", icon = "copy", onClick = { nav.open("copy") })
        Hairline(66.dp)
        ListRow("Restore from text", "Paste a copied backup", icon = "doc", onClick = { nav.open("paste") })
    }
    Hint("Backups from the Waylink web app work here too. Restoring replaces what’s on this phone.")
}

/** Reads at most [max] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private val FAQ = listOf(
    "Which bikes work?" to "Pulsar and Dominar models with the Bluetooth digital cluster. Settings, About Waylink, Bike models lists every model Waylink knows. If your cluster can show calls from your phone, it should work.",
    "How do turn arrows reach the bike?" to "Plan your ride in Google Maps on the same phone, then keep Waylink open in Route. Each turn is sent to the cluster with the distance, and the arrow blinks in the last 100 m.",
    "How is mileage worked out?" to "From full tank to full tank: fill to the brim, ride, fill to the brim again. The litres of the second fill, plus any top-ups between, divided by the distance ridden.",
    "Where does the petrol price come from?" to "State rates that come with the app, from published city prices. Tap the price in Fuel to use your state’s rate or type your pump’s price.",
    "Why does weather say offline?" to "Weather needs the internet and your city. Add your city in Profile, then pull to refresh on Today.",
    "Is my data shared?" to "No. Everything stays on this phone. Weather sends only your city to Open-Meteo. Use Backup in Settings to move to a new phone.",
)

@Composable
private fun Faq() {
    val c = W.c
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Spacer(Modifier.height(16.dp))
    ListCard(flat = true) {
        FAQ.forEachIndexed { i, (q, a) ->
            if (i > 0) Hairline(16.dp)
            Column(Modifier.fillMaxWidth().pressable(label = q) { open = if (open == i) -1 else i }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(q, T.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium), c.ink, Modifier.weight(1f))
                    Ic("chev", Modifier.rotate(if (open == i) 90f else 0f), size = 14.dp, tint = c.ink3)
                }
                AnimatedVisibility(open == i) { Txt(a, T.sheetLead.copy(fontSize = 15.sp), c.ink2, Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
private fun ReportBody(s: AppState, live: Live, vm: AppViewModel) {
    val c = W.c
    val ctx = LocalContext.current
    val board = LocalClipboardManager.current
    var subj by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val info = listOf(
        "App: Waylink for Android $APP_VERSION",
        "Bike: ${s.model.name}",
        "Link: ${connText(s, live)}",
        "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
        "Theme: ${s.settings.theme}, ${s.settings.accent}",
    ).joinToString("\n")
    fun full() = (body.trim().ifEmpty { "(Describe what happened)" }) + "\n\n---\n" + info
    fun go(i: Intent) = try { ctx.startActivity(i) } catch (e: Exception) { vm.toast("No app can open that.") }
    Spacer(Modifier.height(8.dp))
    Txt("Say what you tapped, what you expected and what happened. Your app details are added for you. Nothing personal is included.", T.sheetLead, c.ink2)
    FormField("Subject") { Input(subj, { subj = it }, placeholder = "Turn arrows stop after a few minutes", label = "Subject", maxLength = 80) }
    FormField("What happened") { Input(body, { body = it }, placeholder = "Steps, what you expected, what you saw", label = "What happened", multiline = true, maxLength = 4000) }
    Spacer(Modifier.height(12.dp))
    Txt("Added to your report", T.eyebrow, c.ink3)
    Spacer(Modifier.height(6.dp))
    Txt(info, T.plate.copy(fontSize = 11.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp), c.ink2,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.raised).padding(14.dp))
    Spacer(Modifier.height(16.dp))
    Btn("Email support", {
        go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Waylink: " + subj.trim().ifEmpty { "Problem report" })
            putExtra(Intent.EXTRA_TEXT, full())
        })
    }, Modifier.fillMaxWidth(), icon = "mail")
    Spacer(Modifier.height(8.dp))
    Btn("Open on GitHub", {
        val url = "$REPO_URL/issues/new?title=" + Uri.encode(subj.trim().ifEmpty { "Problem report" }) + "&body=" + Uri.encode(full())
        go(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "code")
    Spacer(Modifier.height(8.dp))
    Btn("Copy report", { board.setText(AnnotatedString(subj.trim() + "\n\n" + full())); vm.toast("Report copied.") }, Modifier.fillMaxWidth(), kind = BtnKind.GHOST)
    Txt("$SUPPORT_EMAIL · ${REPO_URL.removePrefix("https://")}", T.rowSub.copy(textAlign = TextAlign.Center), c.ink3, Modifier.fillMaxWidth().padding(top = 10.dp))
}

@Composable
private fun AboutBody(nav: SheetNav) {
    val c = W.c
    val ctx = LocalContext.current
    fun open(url: String) = try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
    Spacer(Modifier.height(16.dp))
    ListCard(flat = true) {
        ListRow("Support Waylink", "Chip in towards development", icon = "love", onClick = { nav.open("support") })
        Hairline(66.dp)
        ListRow("Bike models", "${Catalog.MODELS.size} models work with Waylink", icon = "bike", onClick = { nav.open("models") })
        Hairline(66.dp)
        ListRow("Bike protocol", "PulsarNav on GitHub", icon = "bt", onClick = { open("https://github.com/nikhilmaity00/pulsarnav") })
        Hairline(66.dp)
        ListRow("Icons", "Hugeicons", icon = "grid", onClick = { open("https://hugeicons.com") })
        Hairline(66.dp)
        ListRow("Weather", "Open-Meteo, free and open data", icon = "wxPart", chevron = false)
        Hairline(66.dp)
        ListRow("Source code", "MIT licence, on GitHub", icon = "code", onClick = { open(REPO_URL) })
        Hairline(66.dp)
        ListRow("Privacy", "Your data stays on this phone", icon = "lock", chevron = false)
    }
    Hint("Not affiliated with Bajaj Auto, OTC, Google or WhatsApp. Names and marks belong to their owners.")
}

private val AMOUNTS = listOf(49, 99, 199, 499, 0)
private val AMT = mapOf(49 to ("tea" to "A cutting chai for the developer"), 99 to ("coffee" to "A coffee for a late-night fix"), 199 to ("fuel" to "Two litres of petrol for test rides"), 499 to ("wrench" to "Towards a month of upkeep"), 0 to ("love" to "You choose the amount in your UPI app"))

@Composable
private fun SupportBody(vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val ctx = LocalContext.current
    var amt by rememberSaveable { mutableIntStateOf(99) }
    fun go(i: Intent) = try { ctx.startActivity(i) } catch (e: Exception) { vm.toast("No app can open that.") }
    Spacer(Modifier.height(15.dp))
    Txt("Waylink is free, with no ads and no tracking. If it makes your rides easier, you can chip in towards new features and upkeep.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(20.dp))
    // Web .give: card paper with a hairline ring, neutral icon square, the amount in the display face, a segmented amount picker.
    Column(Modifier.fillMaxWidth().card(20.dp).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.inkA(0.06f)), contentAlignment = Alignment.Center) {
                Ic(AMT.getValue(amt).first, size = 26.dp, tint = c.ink)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(if (amt > 0) "₹$amt" else "Any amount", T.big(34f).copy(lineHeight = 35.7.sp), c.ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Txt(AMT.getValue(amt).second, T.row, c.ink3, maxLines = 1)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.inkA(0.05f)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AMOUNTS.forEach { a ->
                val on = amt == a
                Box(
                    Modifier.weight(1f).height(38.dp).clip(CircleShape).background(if (on) c.ink else Color.Transparent)
                        .semantics { selected = on }.pressable(label = if (a > 0) "₹$a" else "Other amount", role = Role.RadioButton) { amt = a },
                    contentAlignment = Alignment.Center,
                ) { Txt(if (a > 0) "₹$a" else "Other", T.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), if (on) c.bg else c.ink2, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(16.dp))
        // No UPI ID yet, so the pay button shows disabled like the web's (opacity .35).
        Btn("Pay ${if (amt > 0) "₹$amt " else ""}with any UPI app", {}, Modifier.fillMaxWidth().alpha(0.35f).semantics { disabled() })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Ic("info", size = 15.dp, tint = c.ink3)
            Spacer(Modifier.width(8.dp))
            Txt("Payments open once a UPI ID is added.", T.row.copy(fontSize = 13.sp), c.ink3)
        }
    }
    Spacer(Modifier.height(24.dp))
    Txt("Free ways to help", T.eyebrow, c.ink3)
    Spacer(Modifier.height(8.dp))
    ListCard {
        ListRow("Star it on GitHub", "Helps other riders find it", icon = "sparkles", onClick = { go(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) })
        Hairline(66.dp)
        ListRow("Share Waylink", "Send the link to a riding group", icon = "copy", onClick = {
            go(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Waylink: turns, calls and bike care for your Pulsar. $REPO_URL") }, "Share Waylink"))
        })
        Hairline(66.dp)
        ListRow("Report a problem or idea", "Every report makes it better", icon = "flag", onClick = { nav.open("report") })
    }
    Hint("Payments go straight to the developer through your UPI app. Waylink never sees your payment details.")
}

@Composable
private fun ProfAbout(s: AppState, vm: AppViewModel, nav: SheetNav) {
    val p = s.profile
    var name by rememberSaveable { mutableStateOf(p.name) }
    var phone by rememberSaveable { mutableStateOf(p.phone) }
    var email by rememberSaveable { mutableStateOf(p.email) }
    var city by rememberSaveable { mutableStateOf(p.city) }
    FormField("Full name") { Input(name, { name = it }, placeholder = "Arjun Rao", label = "Full name", caps = KeyboardCapitalization.Words, maxLength = 30) }
    FormField("Phone") { Input(phone, { x -> phone = x.filter { it.isDigit() || it in "+ -" } }, placeholder = "${s.country.dial} 98450 12345", label = "Phone", keyboard = KeyboardType.Phone, maxLength = 20) }
    FormField("Email") { Input(email, { email = it.trim() }, placeholder = "you@example.in", label = "Email", keyboard = KeyboardType.Email, caps = KeyboardCapitalization.None, maxLength = 60) }
    FormField("City") { Input(city, { city = it }, placeholder = "Bengaluru", label = "City", caps = KeyboardCapitalization.Words, maxLength = 24) }
    Hint("Your city sets the weather on Today and the petrol rate in Fuel.")
    Spacer(Modifier.height(24.dp))
    Btn("Done", {
        if (email.isNotBlank() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email)) vm.toast("That email doesn’t look right.")
        else { vm.saveProfileAbout(name, phone, email, city); nav.close() }
    }, Modifier.fillMaxWidth())
}

@Composable
private fun ProfLicence(s: AppState, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val p = s.profile
    var no by rememberSaveable { mutableStateOf(p.licenceNo) }
    var exp by rememberSaveable { mutableStateOf(p.licenceExpiry) }
    FormField("Licence number") { Input(no, { no = it.uppercase() }, placeholder = "KA05 20190012345", label = "Licence number", caps = KeyboardCapitalization.Characters, maxLength = 20) }
    FormField("Valid till") {
        val e = exp
        if (e == null) Btn("Add the date", { exp = System.currentTimeMillis() + 365L * 86_400_000 }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "plus")
        else Column {
            DateField(e, { exp = it }, label = "Valid till")
            Txt("Remove the date", T.link, c.ink3, Modifier.padding(top = 10.dp).pressable(label = "Remove the date") { exp = null })
        }
    }
    Hint("Waylink reminds you in Alerts two months before it runs out.")
    Spacer(Modifier.height(24.dp))
    Btn("Done", { vm.saveLicence(no, exp); nav.close() }, Modifier.fillMaxWidth())
}

@Composable
private fun ProfMedical(s: AppState, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val p = s.profile
    var blood by rememberSaveable { mutableStateOf(p.blood) }
    var medical by rememberSaveable { mutableStateOf(p.medical) }
    var iceName by rememberSaveable { mutableStateOf(p.iceName) }
    var icePhone by rememberSaveable { mutableStateOf(p.icePhone) }
    FormField("Blood group") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalog.BLOOD_GROUPS.forEach { b -> Chip(b.ifEmpty { "Not set" }, { blood = b }, on = blood == b) }
        }
    }
    FormField("Allergies and notes") { Input(medical, { medical = it }, placeholder = "Allergic to penicillin", label = "Allergies and notes", multiline = true, maxLength = 240) }
    Two(
        { m -> FormField("Emergency contact", m) { Input(iceName, { iceName = it }, placeholder = "Amma", label = "Emergency contact", caps = KeyboardCapitalization.Words, maxLength = 30) } },
        { m -> FormField("Their phone", m) { Input(icePhone, { x -> icePhone = x.filter { it.isDigit() || it in "+ -" } }, placeholder = "98860 55443", label = "Their phone", keyboard = KeyboardType.Phone, maxLength = 20) } },
    )
    Hint("Shown on the SOS screen, so whoever helps you can see it.")
    Spacer(Modifier.height(24.dp))
    Btn("Done", { vm.saveMedical(blood, medical, iceName, icePhone); nav.close() }, Modifier.fillMaxWidth())
}

/** border: 1.5px dashed, round. */
private fun Modifier.dashedCircle(color: Color): Modifier = this.drawBehind {
    val w = 1.5.dp.toPx()
    drawCircle(color, radius = size.minDimension / 2 - w / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
}
