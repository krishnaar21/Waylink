package app.waylink.data

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.waylink.core.AppState
import app.waylink.core.Catalog
import app.waylink.core.ChecklistItem
import app.waylink.core.DAY_MS
import app.waylink.core.Fmt
import app.waylink.core.ServiceItem
import app.waylink.core.ServiceRecord
import app.waylink.core.TripMeter
import app.waylink.core.Vehicle
import app.waylink.core.kmPerDay
import app.waylink.core.markDone
import app.waylink.core.uid
import app.waylink.core.Sample
import app.waylink.core.Vault
import app.waylink.core.freshState
import app.waylink.core.newVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the bike link is doing. Real Bluetooth arrives in a later build; the simulated bike works now. */
enum class LinkState { IDLE, CONNECTED }

data class Live(
    val link: LinkState = LinkState.IDLE,
    val simulated: Boolean = false,
    val speed: Int = 0,
    val engine: Int? = null,
    val battery: Int? = null,
    val weather: Weather.State = Weather.State.Idle,
    val toast: Toast? = null,
)

data class Toast(val text: String, val id: Long = System.nanoTime())

/**
 * Holds the app's state and saves it to a JSON file on this phone (same shape as the web app's
 * localStorage), with a short delay so quick taps write once.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val file = AtomicFile(File(app.filesDir, "waylink-state.json"))
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live.asStateFlow()
    private var saveJob: Job? = null

    init { refreshWeather() }

    private fun load(): AppState = try {
        if (file.baseFile.exists()) Vault.decode(String(file.readFully(), Charsets.UTF_8)) else freshState()
    } catch (e: Exception) { freshState() }

    private fun commit(f: (AppState) -> AppState) {
        _state.update(f)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(150)
            val text = Vault.encode(_state.value)
            withContext(Dispatchers.IO) {
                val out = file.startWrite()
                try { out.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out) }
            }
        }
    }

    fun toast(text: String) = _live.update { it.copy(toast = Toast(text)) }
    fun clearToast() = _live.update { it.copy(toast = null) }

    // ---------- Onboarding ----------
    fun onboardSample() {
        commit { Sample.seed(freshState()) }
        toast("Sample data loaded. Erase it any time in Settings.")
        refreshWeather()
    }

    fun saveBike(model: String, plate: String, odo: Double, country: String) = commit { s ->
        val v = newVehicle(model = model, plate = plate.trim().uppercase().take(13), odo = odo.coerceIn(0.0, 999_999.0))
        s.copy(vehicles = listOf(v), active = v.id, profile = s.profile.copy(country = Catalog.country(country).code))
    }

    fun saveAboutYou(avatar: String, name: String, city: String, blood: String, iceName: String, icePhone: String) {
        commit { s ->
            s.copy(profile = s.profile.copy(
                avatar = avatar, name = name.trim().take(30), city = city.trim().take(24), blood = blood,
                iceName = iceName.trim().take(30), icePhone = icePhone.trim().take(20),
            ))
        }
        refreshWeather()
    }

    fun finishOnboarding(simulated: Boolean) {
        commit { it.copy(onboarded = true) }
        if (simulated) simOn()
    }

    // ---------- Garage ----------
    fun switchVehicle(id: String) {
        val s = _state.value
        if (s.active == id || s.vehicles.none { it.id == id }) return
        commit { it.copy(active = id) }
        toast("Now riding ${app.waylink.core.vehicleTitle(_state.value)}.")
    }

    // ---------- Logbook ----------
    private fun veh(f: (Vehicle) -> Vehicle) = commit { it.updateVehicle(f = f) }
    private fun item(id: String) = _state.value.vehicle.service.firstOrNull { it.id == id }

    /** Marks a job done now, or back-dated to [at] (and [km], when known). */
    fun serviceDone(id: String, at: Long? = null, km: Double? = null) {
        val it0 = item(id) ?: return
        val now = System.currentTimeMillis()
        veh { v ->
            v.copy(service = v.service.map { x ->
                if (x.id != id) x else x.markDone(v.odo, now).let { d ->
                    d.copy(lastDate = at ?: d.lastDate, lastKm = (km ?: d.lastKm).coerceAtLeast(0.0))
                }
            })
        }
        if (at == null) toast("${it0.name} · done at ${fmt().num(_state.value.vehicle.odo)} km")
    }

    /** "Yesterday" and "Last week": back-dates the job and guesses the odometer from recent riding. */
    fun serviceEarlier(id: String, days: Int) {
        val v = _state.value.vehicle
        val at = System.currentTimeMillis() - days * DAY_MS
        serviceDone(id, at, Math.round(v.odo - kmPerDay(v) * days).toDouble().coerceAtLeast(0.0))
        item(id)?.let { toast("${it.name} · logged ${if (days == 1) "yesterday" else "last week"}") }
    }

    /** Adds a reminder (null id) or saves an edited one. Returns an error to show, or null. */
    fun saveService(id: String?, name: String, everyKm: Double, everyMonths: Double, targetKm: Double?, lastKm: Double, lastDate: Long): String? {
        val old = id?.let(::item)
        val milestone = old?.milestone == true
        val every = Math.round(everyMonths.coerceAtLeast(0.0) * 30.4).toInt()
        if (!milestone && everyKm <= 0 && every <= 0) return "Set a distance or a time."
        val base = old ?: ServiceItem(kind = "custom", name = "", icon = "wrench")
        val it = base.copy(
            name = name.trim().take(40).ifEmpty { "Reminder" },
            everyKm = if (milestone) base.everyKm else everyKm.coerceAtLeast(0.0),
            everyDays = if (milestone) base.everyDays else every,
            targetKm = if (milestone) (targetKm ?: 0.0).coerceAtLeast(0.0) else base.targetKm,
            lastKm = lastKm.coerceAtLeast(0.0), lastDate = lastDate,
        )
        veh { v -> v.copy(service = if (old == null) v.service + it else v.service.map { x -> if (x.id == it.id) it else x }) }
        toast(if (old == null) "Reminder added." else "Saved.")
        return null
    }

    fun deleteService(id: String) { veh { v -> v.copy(service = v.service.filter { it.id != id }) }; toast("Reminder removed.") }

    fun saveRecord(id: String?, title: String, date: Long, odo: Double, workshop: String, cost: Double, notes: String) {
        val r = ServiceRecord(
            id = id ?: uid(), date = date, odo = odo.coerceAtLeast(0.0), title = title.trim().take(60),
            workshop = workshop.trim().take(40), cost = cost.coerceAtLeast(0.0), notes = notes.trim().take(500),
        )
        veh { v -> v.copy(records = if (v.records.any { it.id == r.id }) v.records.map { if (it.id == r.id) r else it } else v.records + r) }
    }

    fun deleteRecord(id: String) = veh { v -> v.copy(records = v.records.filter { it.id != id }) }

    fun addCheck(text: String) { val t = text.trim().take(80); if (t.isNotEmpty()) veh { v -> v.copy(checklist = v.checklist + ChecklistItem(text = t)) } }
    fun toggleCheck(id: String) = veh { v -> v.copy(checklist = v.checklist.map { if (it.id == id) it.copy(done = !it.done) else it }) }
    fun deleteCheck(id: String) = veh { v -> v.copy(checklist = v.checklist.filter { it.id != id }) }

    fun setOdometer(km: Double) { if (km >= 0 && km < 10_000_000) veh { it.copy(odo = Math.round(km * 10) / 10.0) } }

    fun resetTrip(which: String) {
        val t = TripMeter(0.0, System.currentTimeMillis())
        veh { if (which == "A") it.copy(tripA = t) else it.copy(tripB = t) }
        toast("Trip $which reset.")
    }

    fun fmt() = Fmt(_state.value.country.locale)

    // ---------- Link ----------
    fun simOn() {
        _live.update { it.copy(link = LinkState.CONNECTED, simulated = true, engine = 83, speed = 0) }
        toast("Simulated cluster linked.")
    }

    fun simOff() = _live.update { it.copy(link = LinkState.IDLE, simulated = false, engine = null) }

    // ---------- Settings ----------
    fun setTheme(key: String) = commit { it.copy(settings = it.settings.copy(theme = key)) }
    fun setAccent(key: String) = commit { it.copy(settings = it.settings.copy(accent = key)) }

    /** Wipes everything on this phone and returns to onboarding. */
    fun eraseAll() {
        simOff()
        commit { s -> freshState().copy(settings = s.settings) }
        toast("Erased. Waylink starts fresh.")
    }

    // ---------- Weather ----------
    fun refreshWeather() {
        val city = _state.value.profile.city
        if (city.isBlank()) { _live.update { it.copy(weather = Weather.State.Need) }; return }
        _live.update { it.copy(weather = Weather.State.Loading((it.weather as? Weather.State.Ready)?.data)) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Weather.fetch(city) }
            _live.update { it.copy(weather = r) }
        }
    }
}
