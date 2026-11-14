package app.waylink.ui.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.FuelPrices
import app.waylink.core.dShort
import app.waylink.core.fuelPriceOf
import app.waylink.core.vehicleTitle
import app.waylink.data.AppViewModel
import app.waylink.ui.components.BigValue
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Chip
import app.waylink.ui.components.DateTimeField
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Input
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Switch
import app.waylink.ui.components.Two
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.parseNum
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import java.time.LocalDate

private fun clip(t: String, n: Int = 18) = if (t.length > n) t.take(n - 1).trimEnd() + "…" else t
private fun two(n: Double) = String.format(java.util.Locale.ENGLISH, "%.2f", n)
private fun decimal(x: String, max: Int = 9) = x.filter { it.isDigit() || it == '.' }.take(max)

/** Builds the Fuel sheets ("fuel:<id|new>", "fuelprice"), or null. */
fun fuelSheet(key: String, s: AppState, vm: AppViewModel, nav: SheetNav): SheetSpec? {
    val arg = key.substringAfter(':', "")
    return when (key.substringBefore(':')) {
        "fuel" -> {
            val isNew = arg == "new"
            if (!isNew && s.vehicle.fuel.none { it.id == arg }) return null
            SheetSpec(if (isNew) "Log fuel" else "Fill-up", "Fuel · ${clip(vehicleTitle(s))}") { FuelBody(s, if (isNew) null else arg, vm, nav) }
        }
        "fuelprice" -> SheetSpec("Price per litre", "Fuel") { PriceBody(s, vm, nav) }
        else -> null
    }
}

@Composable
private fun FuelBody(s: AppState, id: String?, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val v = s.vehicle
    val e = id?.let { x -> v.fuel.firstOrNull { it.id == x } }
    val key = id ?: "new"
    val price = fuelPriceOf(s)
    val f = Fmt(s.country.locale)
    val cur = s.country.cur
    var odo by rememberSaveable(key) { mutableStateOf((e?.odo ?: v.odo).let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() }) }
    var litres by rememberSaveable(key) { mutableStateOf(e?.litres?.toString() ?: "") }
    var cost by rememberSaveable(key) { mutableStateOf(e?.cost?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var full by rememberSaveable(key) { mutableStateOf(e?.full ?: true) }
    var ts by rememberSaveable(key) { mutableLongStateOf(e?.ts ?: System.currentTimeMillis()) }

    FormField("Odometer") { Input(odo, { odo = decimal(it) }, placeholder = "0", suffix = "km", label = "Odometer", keyboard = KeyboardType.Decimal) }
    Two(
        { m ->
            FormField("Litres", m) {
                Input(litres, { x ->
                    litres = decimal(x, 6)
                    val l = parseNum(litres)
                    if (l != null && l > 0 && price > 0) cost = two(l * price)
                }, placeholder = "0.00", suffix = "L", label = "Litres", keyboard = KeyboardType.Decimal)
            }
        },
        { m ->
            FormField("Paid", m) {
                Input(cost, { x ->
                    cost = decimal(x)
                    val p = parseNum(cost)
                    if (p != null && p > 0 && price > 0) litres = two(p / price)
                }, placeholder = "0", suffix = cur, label = "Paid", keyboard = KeyboardType.Decimal)
            }
        },
    )
    Hint("$cur${f.num(price, 2)} a litre. Fill either one; the other follows.")
    Spacer(Modifier.height(16.dp))
    ListCard(flat = true) {
        ListRow("Filled to the brim", "Needed to work out mileage", trailing = { Switch(full, "Filled to the brim", { full = !full }) })
    }
    FormField("Date and time") { DateTimeField(ts, { ts = it }, max = System.currentTimeMillis() + 3_600_000) }
    Spacer(Modifier.height(24.dp))
    Btn("Save", {
        val err = vm.saveFuel(id, parseNum(odo), parseNum(litres), parseNum(cost), full, ts, price)
        if (err != null) vm.toast(err) else nav.close()
    }, Modifier.fillMaxWidth(), icon = "check")
    if (id != null) {
        Spacer(Modifier.height(8.dp))
        Btn("Delete fill-up", { vm.deleteFuel(id); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    }
}

@Composable
private fun PriceBody(s: AppState, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val f = Fmt(s.country.locale)
    val cur = s.country.cur
    val price = fuelPriceOf(s)
    var text by rememberSaveable { mutableStateOf(two(price).removeSuffix(".00")) }
    Spacer(Modifier.height(12.dp))
    Txt("Fills in the cost when you log fuel.", T.sheetLead, c.ink2)
    if (s.country.code == "IN") {
        val area = s.profile.fuelArea
        val found = FuelPrices.lookup(area?.state, area?.place, s.profile.city)
        val d = LocalDate.parse(FuelPrices.AS_OF)
        val asOf = dShort(d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        Column(Modifier.fillMaxWidth().padding(top = 16.dp).card(20.dp).padding(horizontal = 20.dp, vertical = 18.dp)) {
            val fp = found?.price
            if (found != null && fp != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("Petrol in ${found.state}", T.eyebrow, c.ink3)
                        BigValue(cur + f.num(fp, 2), "a litre", 28f)
                        Txt("${area?.place?.ifEmpty { null } ?: s.profile.city.ifEmpty { found.state }} · rate on $asOf", T.rowSub, c.ink4)
                    }
                    if (kotlin.math.abs(fp - price) < 0.005) Chip("In use", {}, icon = "check", on = true)
                    else Chip("Use", { vm.useFuelPrice(fp); text = two(fp) })
                }
            } else Txt(
                if (found != null) "No rate for ${found.state} yet. Enter the pump price below." else "Add your city in Profile, or use your location, to see the petrol rate where you ride.",
                T.row.copy(fontSize = 14.sp), c.ink2,
            )
            // web fuel-locate: find the state from where the phone is, and use its rate
            val locating = vm.live.collectAsState().value.locating
            val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
                if (r.values.any { it }) vm.fuelHere() else vm.toast("Location is off. Allow it, or add your city in Profile.")
            }
            Spacer(Modifier.height(12.dp))
            Btn(if (locating) "Finding you…" else if (found != null) "Update from my location" else "Use my location", {
                if (locating) return@Btn
                if (vm.hasLocation()) vm.fuelHere()
                else ask.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, small = true, icon = "pin")
        }
    }
    FormField("Your price") { Input(text, { text = decimal(it, 8) }, placeholder = "0", suffix = cur, label = "Your price", keyboard = KeyboardType.Decimal) }
    if (s.country.code == "IN") Hint("Rates are state averages that come with the app. Your pump may differ by a rupee or two, and logging a fill-up with litres and cost updates this for you.")
    Spacer(Modifier.height(24.dp))
    Btn("Save", {
        val n = parseNum(text)
        if (n == null || n <= 0) vm.toast("Enter a price.") else { vm.setFuelPrice(n); nav.close() }
    }, Modifier.fillMaxWidth())
}
