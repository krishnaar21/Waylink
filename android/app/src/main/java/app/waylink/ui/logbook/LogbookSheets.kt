package app.waylink.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Due
import app.waylink.core.Fmt
import app.waylink.core.dFull
import app.waylink.core.dMed
import app.waylink.core.dueSentence
import app.waylink.core.everyText
import app.waylink.core.rowValue
import app.waylink.core.serviceStatus
import app.waylink.core.vehicleTitle
import app.waylink.data.AppViewModel
import app.waylink.ui.components.BigValue
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Chip
import app.waylink.ui.components.DateField
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Input
import app.waylink.ui.components.JobGauge
import app.waylink.ui.components.Two
import app.waylink.ui.components.Txt
import app.waylink.ui.components.numText
import app.waylink.ui.components.parseNum
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** A sheet's title, the small line under it, and its body. */
class SheetSpec(val title: String, val sub: String?, val content: @Composable ColumnScope.() -> Unit)

/** Navigation a sheet can ask for: open another sheet (pushed), go back, or close. */
interface SheetNav {
    fun open(key: String)
    fun back()
    fun close()
}

private fun clip(t: String, n: Int = 18) = if (t.length > n) t.take(n - 1).trimEnd() + "…" else t

/** Builds the Logbook sheets (svc, svc-edit, rec, odo, trip), or null if [key] isn't one of them. */
fun logbookSheet(key: String, s: AppState, vm: AppViewModel, nav: SheetNav): SheetSpec? {
    val k = key.substringBefore(':')
    val arg = key.substringAfter(':', "")
    val v = s.vehicle
    val f = Fmt(s.country.locale)
    return when (k) {
        "svc" -> {
            val it = v.service.firstOrNull { x -> x.id == arg } ?: return null
            SheetSpec(it.name, "${clip(vehicleTitle(s))} · ${everyText(it, f)}") { SvcBody(s, it.id, vm, nav) }
        }
        "svc-edit" -> {
            val isNew = arg == "new"
            if (!isNew && v.service.none { x -> x.id == arg }) return null
            SheetSpec(if (isNew) "New reminder" else "Edit reminder", "Logbook") { SvcEditBody(s, if (isNew) null else arg, vm, nav) }
        }
        "rec" -> {
            val isNew = arg == "new"
            val r = v.records.firstOrNull { x -> x.id == arg }
            if (!isNew && r == null) return null
            SheetSpec(if (isNew) "Log a visit" else r!!.title, "Workshop visit") { RecBody(s, if (isNew) null else arg, vm, nav) }
        }
        "odo" -> SheetSpec("Odometer", vehicleTitle(s)) { OdoBody(s, vm, nav) }
        "trip" -> {
            val t = if (arg == "A") v.tripA else v.tripB
            SheetSpec("${f.num(t.km, 1)} km", "Trip $arg") {
                Spacer(Modifier.height(12.dp))
                Txt("Since ${dFull(t.since)}. Every ride you record adds to it.", T.sheetLead, W.c.ink2)
                Spacer(Modifier.height(24.dp))
                Btn("Reset trip $arg", { vm.resetTrip(arg); nav.close() }, Modifier.fillMaxWidth())
            }
        }
        else -> null
    }
}

@Composable
private fun SvcBody(s: AppState, id: String, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val v = s.vehicle
    val it = v.service.firstOrNull { x -> x.id == id } ?: return
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val st = serviceStatus(v.odo, it)
    val rv = rowValue(st, f)
    val md = s.model
    val hint = when (it.kind) {
        "oil", "service" -> md.oil
        "tyres" -> "Cold: ${md.psi[0]} front, ${md.psi[2]} rear psi solo. ${md.psi[1]} / ${md.psi[3]} with a pillion."
        "chain" -> "Slack ${md.chain.replace(" slack", "")}."
        "coolant" -> md.coolant
        else -> null
    }
    var picking by rememberSaveable(id) { mutableStateOf(false) }
    var pickDate by rememberSaveable(id) { mutableLongStateOf(System.currentTimeMillis()) }
    var pickKm by rememberSaveable(id) { mutableStateOf(numText(Math.round(v.odo).toDouble())) }

    // Web: .flex.between with align-items: baseline, "Last done" as .small.muted (14 / ink-2).
    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        val col = if (rv.red) c.red else c.ink
        BigValue(rv.v, if (st.status == Due.OVERDUE) rv.u else rv.u + " left", 52f, col, Modifier.weight(1f).alignByBaseline(), unitSize = 14f, unitColor = if (rv.red) c.red else c.ink3)
        Txt("Last done ${dMed(it.lastDate)}", T.row, c.ink2, Modifier.alignByBaseline())
    }
    Spacer(Modifier.height(12.dp))
    JobGauge(st, 10, 6.dp, labels = true)
    Spacer(Modifier.height(14.dp))
    Txt(dueSentence(st, f), T.sheetLead, c.ink2)
    if (hint != null) Hint(hint)
    Spacer(Modifier.height(20.dp))
    Btn("Mark as done", { vm.serviceDone(id); nav.close() }, Modifier.fillMaxWidth(), icon = "check")
    Spacer(Modifier.height(24.dp))
    Txt("Done earlier?", T.eyebrow, c.ink3)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Yesterday", { vm.serviceEarlier(id, 1); nav.close() })
        Chip("Last week", { vm.serviceEarlier(id, 7); nav.close() })
        Chip("Pick a date", { picking = true }, icon = "cal", on = picking)
    }
    if (picking) {
        Two(
            { m -> FormField("Date", m) { DateField(pickDate, { d -> pickDate = d }, max = System.currentTimeMillis()) } },
            { m -> FormField("At", m) { Input(pickKm, { x -> pickKm = x.filter { ch -> ch.isDigit() } }, placeholder = "0", suffix = "km", label = "Odometer when done", keyboard = KeyboardType.Number) } },
        )
        Spacer(Modifier.height(12.dp))
        Btn("Save", {
            vm.serviceDone(id, pickDate, parseNum(pickKm) ?: v.odo)
            vm.toast("${it.name} · logged ${dFull(pickDate)}")
            nav.close()
        }, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(20.dp))
    Hairline()
    Row(
        Modifier.fillMaxWidth().pressable(label = "Edit reminder") { nav.open("svc-edit:$id") }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic("edit", size = 16.dp, tint = c.ink2)
        Spacer(Modifier.padding(start = 8.dp))
        Txt("Edit reminder", T.body.copy(fontSize = 15.5.sp), c.ink, Modifier.weight(1f))
        Ic("chev", size = 16.dp, tint = c.ink3)
    }
}

@Composable
private fun SvcEditBody(s: AppState, id: String?, vm: AppViewModel, nav: SheetNav) {
    val v = s.vehicle
    val c = W.c
    val it = id?.let { x -> v.service.firstOrNull { y -> y.id == x } }
    val key = id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(it?.name ?: "") }
    var icon by rememberSaveable(key) { mutableStateOf(it?.icon ?: "wrench") }
    // Due by distance, time, or whichever comes first.
    var by by rememberSaveable(key) {
        mutableStateOf(when { it == null -> "km"; it.everyKm > 0 && it.everyDays > 0 -> "both"; it.everyDays > 0 -> "time"; else -> "km" })
    }
    var everyKm by rememberSaveable(key) { mutableStateOf(if (it != null && it.everyKm > 0) numText(it.everyKm) else "1000") }
    var months by rememberSaveable(key) {
        mutableStateOf(if (it != null && it.everyDays > 0) numText(Math.round(it.everyDays / 30.4 * 10) / 10.0) else "6")
    }
    var target by rememberSaveable(key) { mutableStateOf(numText(it?.targetKm)) }
    var lastKm by rememberSaveable(key) { mutableStateOf(numText(Math.round(it?.lastKm ?: v.odo).toDouble())) }
    var lastDate by rememberSaveable(key) { mutableLongStateOf(it?.lastDate ?: System.currentTimeMillis()) }
    var first by rememberSaveable(key) { mutableStateOf(it?.firstKm != null || it?.firstDate != null) }
    var firstKm by rememberSaveable(key) { mutableStateOf(numText(it?.firstKm ?: (Math.round(v.odo / 100.0) * 100.0 + 500))) }
    var firstDate by rememberSaveable(key) { mutableLongStateOf(it?.firstDate ?: (System.currentTimeMillis() + 30L * 86_400_000)) }
    val digits = { x: String -> x.filter { ch -> ch.isDigit() }.take(7) }
    val decimal = { x: String -> x.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }
    val milestone = it?.milestone == true
    val km = by != "time"
    val time = by != "km"

    FormField("Name") { Input(name, { x -> name = x }, placeholder = "Clutch cable lube", label = "Name", maxLength = 40) }
    FormField("Icon") { IconGrid(icon) { x -> icon = x } }
    if (milestone) {
        FormField("Next service at") { Input(target, { x -> target = digits(x) }, placeholder = "0", suffix = "km", label = "Next service at", keyboard = KeyboardType.Number) }
        Hint("Bajaj schedule: 500 km, 5,000 km, 10,000 km, then every 5,000.")
    } else {
        FormField("Due by") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("km" to "Distance", "time" to "Time", "both" to "Both").forEach { (k, t) -> Chip(t, { by = k }, Modifier.weight(1f), on = by == k) }
            }
        }
        if (km) FormField("Every") {
            Stepper(everyKm, { x -> everyKm = digits(x) }, "km", "Every, km", step = 500.0, keyboard = KeyboardType.Number)
        }
        if (time) FormField(if (km) "Or every" else "Every") {
            Stepper(months, { x -> months = decimal(x) }, "months", "Every, months", step = 1.0, keyboard = KeyboardType.Decimal)
        }
        if (km && time) Hint("Due at whichever comes first.")
    }
    Two(
        { m -> FormField("Last done at", m) { Input(lastKm, { x -> lastKm = digits(x) }, placeholder = "0", suffix = "km", label = "Last done at, km", keyboard = KeyboardType.Number) } },
        { m -> FormField("On", m) { DateField(lastDate, { d -> lastDate = d }, label = "Last done on", max = System.currentTimeMillis()) } },
    )
    if (!milestone) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Txt("Different first service", T.rowTitle, c.ink)
                Txt("New parts often get an early first check.", T.rowSub, c.ink3)
            }
            app.waylink.ui.components.Switch(first, "Different first service", { first = !first })
        }
        if (first) {
            if (km) FormField("First due at") {
                Input(firstKm, { x -> firstKm = digits(x) }, placeholder = "0", suffix = "km", label = "First due at, km", keyboard = KeyboardType.Number)
            }
            if (time) FormField(if (km) "Or first due on" else "First due on") { DateField(firstDate, { d -> firstDate = d }, label = "First due on") }
            Hint("After that, every interval as set above.")
        }
    }
    Spacer(Modifier.height(24.dp))
    Btn("Save", {
        val ek = if (km) parseNum(everyKm) ?: 0.0 else 0.0
        val em = if (time) parseNum(months) ?: 0.0 else 0.0
        val err = vm.saveService(
            id, name, ek, em, parseNum(target), parseNum(lastKm) ?: 0.0, lastDate, icon = icon,
            firstKm = if (first && km) parseNum(firstKm) else null, firstDate = if (first && time) firstDate else null,
        )
        if (err != null) vm.toast(err) else nav.close()
    }, Modifier.fillMaxWidth())
    if (id != null) {
        Spacer(Modifier.height(8.dp))
        Btn("Delete reminder", { vm.deleteService(id); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    }
}

/** Five to a row; the picked one turns ink. */
@Composable
private fun IconGrid(picked: String, onPick: (String) -> Unit) {
    val c = W.c
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        app.waylink.core.SERVICE_ICONS.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ic ->
                    val on = ic == picked
                    Box(
                        Modifier.weight(1f).height(48.dp)
                            .then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)))
                            .background(if (on) c.ink else c.raised)
                            .semantics { selected = on }
                            .pressable(label = "Icon ${ic}", scale = 0.92f) { onPick(ic) },
                        contentAlignment = Alignment.Center,
                    ) { Ic(ic, size = 21.dp, tint = if (on) c.bg else c.ink2) }
                }
            }
        }
    }
}

/** A number with − and + either side. */
@Composable
private fun Stepper(value: String, onChange: (String) -> Unit, suffix: String, label: String, step: Double, keyboard: KeyboardType) {
    val bump = { d: Double ->
        val n = ((parseNum(value) ?: 0.0) + d).coerceAtLeast(0.0)
        onChange(numText(Math.round(n / step) * step))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepBtn(false, "Less, $label") { bump(-step) }
        Box(Modifier.weight(1f)) { Input(value, onChange, placeholder = "0", suffix = suffix, label = label, keyboard = keyboard) }
        StepBtn(true, "More, $label") { bump(step) }
    }
}

@Composable
private fun StepBtn(plus: Boolean, label: String, onClick: () -> Unit) {
    val c = W.c
    Box(
        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.raised).pressable(label = label, scale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (plus) Ic("plus", size = 18.dp, tint = c.ink)
        else Box(Modifier.size(14.dp, 2.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp)).background(c.ink))
    }
}

@Composable
private fun RecBody(s: AppState, id: String?, vm: AppViewModel, nav: SheetNav) {
    val v = s.vehicle
    val r = id?.let { x -> v.records.firstOrNull { y -> y.id == x } }
    val key = id ?: "new"
    var title by rememberSaveable(key) { mutableStateOf(r?.title ?: "") }
    var date by rememberSaveable(key) { mutableLongStateOf(r?.date ?: System.currentTimeMillis()) }
    var odo by rememberSaveable(key) { mutableStateOf(numText(Math.round(r?.odo ?: v.odo).toDouble())) }
    var workshop by rememberSaveable(key) { mutableStateOf(r?.workshop ?: "") }
    var cost by rememberSaveable(key) { mutableStateOf(if (r != null && r.cost > 0) numText(r.cost) else "") }
    var notes by rememberSaveable(key) { mutableStateOf(r?.notes ?: "") }

    FormField("What was done") { Input(title, { x -> title = x }, placeholder = "4th paid service", label = "What was done") }
    Two(
        { m -> FormField("Date", m) { DateField(date, { d -> date = d }, max = System.currentTimeMillis()) } },
        { m -> FormField("Odometer", m) { Input(odo, { x -> odo = x.filter { ch -> ch.isDigit() }.take(7) }, placeholder = "0", suffix = "km", label = "Odometer", keyboard = KeyboardType.Number) } },
    )
    Two(
        { m -> FormField("Workshop", m) { Input(workshop, { x -> workshop = x }, placeholder = "Sri Ganesh Bajaj", label = "Workshop", maxLength = 40) } },
        { m -> FormField("Cost", m) { Input(cost, { x -> cost = x.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, placeholder = "0", suffix = s.country.cur, label = "Cost", keyboard = KeyboardType.Decimal) } },
    )
    FormField("Notes") { Input(notes, { x -> notes = x }, placeholder = "Oil, filter, chain set…", label = "Notes", maxLength = 500, multiline = true) }
    Spacer(Modifier.height(24.dp))
    Btn("Save", {
        if (title.isBlank()) vm.toast("What was done?")
        else { vm.saveRecord(id, title, date, parseNum(odo) ?: 0.0, workshop, parseNum(cost) ?: 0.0, notes); nav.close() }
    }, Modifier.fillMaxWidth())
    if (id != null) {
        Spacer(Modifier.height(8.dp))
        Btn("Delete visit", { vm.deleteRecord(id); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    }
}

@Composable
private fun OdoBody(s: AppState, vm: AppViewModel, nav: SheetNav) {
    var odo by rememberSaveable { mutableStateOf(numText(s.vehicle.odo)) }
    // Web fieldForm: the sheet title names the field, so the input has no label of its own.
    Box(Modifier.padding(top = 18.dp)) {
        Input(odo, { x -> odo = x.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, placeholder = "0", suffix = "km", label = "Odometer", keyboard = KeyboardType.Decimal)
    }
    Hint("Recorded rides add to this. Fix it here if it drifts.")
    Spacer(Modifier.height(24.dp))
    Btn("Save", {
        val n = parseNum(odo)
        if (n == null || n < 0) vm.toast("Enter a number.") else { vm.setOdometer(n); nav.close() }
    }, Modifier.fillMaxWidth())
}
