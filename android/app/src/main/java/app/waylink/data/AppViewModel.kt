package app.waylink.data

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.waylink.core.AppState
import app.waylink.core.Catalog
import app.waylink.core.ChecklistItem
import app.waylink.core.DAY_MS
import app.waylink.core.Alerts
import app.waylink.core.Fmt
import app.waylink.core.LogEvent
import app.waylink.core.NamedEvent
import app.waylink.core.FuelEntry
import app.waylink.core.fuelStats
import app.waylink.core.addRide
import app.waylink.core.haversine
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
    /** A ride being recorded, or null. */
    val rec: Rec? = null,
    val gpsErr: Boolean = false,
)

/** A ride in progress: [pts] are [lat, lng, epoch ms]. */
data class Rec(val start: Long, val km: Double = 0.0, val top: Int = 0, val pts: List<List<Double>> = emptyList())

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

    // ---------- Fuel ----------
    /** Logs or edits a fill-up. Returns an error to show, or null. */
    fun saveFuel(id: String?, odo: Double?, litres: Double?, costIn: Double?, full: Boolean, ts: Long, price: Double): String? {
        if (odo == null || odo <= 0 || litres == null || litres <= 0) return "Odometer and litres, please."
        val cost = if (costIn != null && costIn > 0) costIn else Math.round(litres * price * 100) / 100.0
        val e = FuelEntry(id = id ?: uid(), ts = ts, odo = odo, litres = litres, cost = cost, full = full)
        veh { v ->
            val list = if (v.fuel.any { it.id == e.id }) v.fuel.map { if (it.id == e.id) e else it } else v.fuel + e
            v.copy(
                fuel = list,
                fuelPrice = if (cost > 0) Math.round(cost / litres * 100) / 100.0 else v.fuelPrice,
                odo = if (odo > v.odo) Math.round(odo * 10) / 10.0 else v.odo,
            )
        }
        val t = fuelStats(_state.value.vehicle).tanks.firstOrNull { it.id == e.id }
        toast(if (t != null) "${fmt().num(t.kmpl, 1)} km/L on that tank." else if (full) "Logged. Next full tank gives you mileage." else "Top-up logged.")
        return null
    }

    fun deleteFuel(id: String) { veh { v -> v.copy(fuel = v.fuel.filter { it.id != id }) }; toast("Fill-up removed.") }

    /** Your pump price for this bike. */
    fun setFuelPrice(n: Double) { if (n > 0 && n < 100_000) veh { it.copy(fuelPrice = n) } }

    /** Uses a state rate for every bike. */
    fun useFuelPrice(n: Double) {
        if (!(n > 0)) return
        commit { s -> s.copy(vehicles = s.vehicles.map { it.copy(fuelPrice = n) }) }
        toast("Petrol set to ${_state.value.country.cur}${fmt().num(n, 2)} a litre.")
    }

    // ---------- Rides ----------
    private val locations = app.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    private var lastFix: Triple<Double, Double, Long>? = null
    private var simJob: Job? = null
    private val sim = doubleArrayOf(12.9716, 77.5946, 0.6, 0.0, 0.0) // lat, lng, heading, speed, t
    private val listener = android.location.LocationListener { l ->
        onFix(l.latitude, l.longitude, if (l.hasSpeed()) l.speed.toDouble() else null, if (l.hasAccuracy()) l.accuracy.toDouble() else 999.0, l.time)
    }
    private var watching = false

    fun hasLocation(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        getApplication(), android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Starts or stops GPS (or the simulated bike's route) to match what needs it. */
    private fun ensureGeo() {
        val want = _live.value.rec != null
        val simulated = _live.value.simulated
        if (want && simulated) {
            stopGps()
            if (simJob == null) simJob = viewModelScope.launch { while (true) { simStep(); delay(1000) } }
        } else if (want) {
            simJob?.cancel(); simJob = null
            if (!watching && hasLocation()) {
                try {
                    val provider = if (locations.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) android.location.LocationManager.GPS_PROVIDER
                        else android.location.LocationManager.NETWORK_PROVIDER
                    locations.requestLocationUpdates(provider, 1000L, 0f, listener, android.os.Looper.getMainLooper())
                    watching = true
                } catch (e: SecurityException) { _live.update { it.copy(gpsErr = true) } }
                  catch (e: IllegalArgumentException) { _live.update { it.copy(gpsErr = true) }; toast("No GPS on this phone. Try the simulated bike.") }
            }
        } else {
            stopGps(); simJob?.cancel(); simJob = null
            _live.update { it.copy(speed = 0) }
        }
    }

    private fun stopGps() { if (watching) { locations.removeUpdates(listener); watching = false } }

    private fun simStep() {
        sim[4] += 1
        val t = sim[4]
        val target = maxOf(0.0, 42 + 18 * Math.sin(t / 23) + 8 * Math.sin(t / 7) - (if (t % 90 > 82) 60 else 0))
        sim[3] += (target - sim[3]) * 0.25
        sim[2] += Math.sin(t / 11) * 0.12
        val m = sim[3] / 3.6
        sim[0] += Math.cos(sim[2]) * m / 111320
        sim[1] += Math.sin(sim[2]) * m / (111320 * Math.cos(sim[0] * Math.PI / 180))
        _live.update { it.copy(engine = Math.round(80 + 6 * Math.sin(t / 40) + sim[3] / 25).toInt()) }
        onFix(sim[0], sim[1], m, 5.0, System.currentTimeMillis())
    }

    private fun onFix(lat: Double, lng: Double, speedMs: Double?, acc: Double, ts: Long) {
        var kmh = speedMs?.takeIf { !it.isNaN() }?.let { it * 3.6 }
        var rec = _live.value.rec
        val lf = lastFix
        if (lf != null) {
            val d = haversine(listOf(lf.first, lf.second), listOf(lat, lng))
            val dt = (ts - lf.third) / 1000.0
            if (kmh == null && dt > 0) kmh = d / dt * 3.6
            if (rec != null && acc <= 40 && dt > 0 && d / dt * 3.6 < 220 && d > 2) {
                val lp = rec.pts.lastOrNull()
                val pts = if (lp == null || haversine(lp, listOf(lat, lng)) >= 12) rec.pts + listOf(listOf(r6(lat), r6(lng), ts.toDouble())) else rec.pts
                rec = rec.copy(km = rec.km + d / 1000, pts = pts)
            }
        } else if (rec != null && rec.pts.isEmpty()) rec = rec.copy(pts = listOf(listOf(r6(lat), r6(lng), ts.toDouble())))
        lastFix = Triple(lat, lng, ts)
        val speed = maxOf(0, Math.round(kmh ?: 0.0).toInt())
        if (rec != null) rec = rec.copy(top = maxOf(rec.top, speed))
        _live.update { it.copy(speed = speed, rec = rec, gpsErr = false) }
    }

    private fun r6(x: Double) = Math.round(x * 1e6) / 1e6

    fun startRecording() {
        if (_live.value.rec != null) return
        if (!_live.value.simulated && !hasLocation()) { toast("Allow location to record rides, or use the simulated bike."); return }
        lastFix = null
        _live.update { it.copy(rec = Rec(System.currentTimeMillis())) }
        ensureGeo()
        toast("Recording. Ride safe.")
    }

    fun stopRecording(keep: Boolean) {
        val r = _live.value.rec ?: return
        _live.update { it.copy(rec = null) }
        if (keep && r.km >= 0.1) {
            veh { it.addRide(r.start, System.currentTimeMillis(), r.km, r.top.toDouble(), r.pts) }
            toast("Saved · ${fmt().num(r.km, 1)} km")
        } else toast(if (keep) "Too short to keep." else "Ride discarded.")
        ensureGeo()
    }

    fun renameTrip(id: String, title: String) = veh { v -> v.copy(trips = v.trips.map { if (it.id == id) it.copy(title = title.trim().take(60).ifEmpty { it.title }) else it }) }
    fun deleteTrip(id: String) { veh { v -> v.copy(trips = v.trips.filter { it.id != id }) }; toast("Ride deleted. The odometer is unchanged.") }

    override fun onCleared() { stopGps(); super.onCleared() }

    // ---------- Alerts ----------
    private fun logAlert(a: Alerts, kind: String, name: String, count: Boolean = true): Alerts {
        val connected = count && _live.value.link == LinkState.CONNECTED
        return a.copy(log = (listOf(LogEvent(kind, name, System.currentTimeMillis())) + a.log).take(50), sent = a.sent + if (connected) 1 else 0)
    }

    /** Sends a call or chat to the bike by hand, as a browser can't see the phone's own. */
    fun relay(kind: String, nameIn: String) {
        val name = nameIn.trim().take(32)
        if (name.isEmpty()) { toast("Add a name first."); return }
        val a = _state.value.settings.alerts
        val linked = _live.value.link == LinkState.CONNECTED
        val now = System.currentTimeMillis()
        when (kind) {
            "ring" -> {
                if (!a.calls) { toast("Phone calls are switched off above."); return }
                commit { it.copy(alerts = logAlert(it.alerts, "call", name)) }
                toast(if (linked) "$name is ringing on the cluster." else "Saved. Link the bike to send it.")
            }
            "missed" -> {
                if (!a.calls) { toast("Phone calls are switched off above."); return }
                commit { it.copy(alerts = logAlert(it.alerts, "missed", name).let { x -> x.copy(missed = x.missed + NamedEvent(name, now)) }) }
                toast(if (linked) "Missed call from $name sent." else "Saved. Link the bike to send it.")
            }
            "wa" -> {
                if (!a.waMsgs) { toast("WhatsApp messages are switched off above."); return }
                commit { it.copy(alerts = logAlert(it.alerts, "wa", name).let { x -> x.copy(wa = x.wa + NamedEvent(name, now)) }) }
                toast(if (linked) "$name on the cluster for 6 s." else "Saved. Link the bike to send it.")
            }
        }
    }

    fun clearMissed() { commit { it.copy(alerts = it.alerts.copy(missed = emptyList())) }; toast("Missed calls cleared.") }
    fun clearWa() { commit { it.copy(alerts = it.alerts.copy(wa = emptyList())) }; toast("WhatsApp alerts cleared.") }

    fun toggleAlert(key: String) = commit { s ->
        val a = s.settings.alerts
        s.copy(settings = s.settings.copy(alerts = when (key) {
            "calls" -> a.copy(calls = !a.calls)
            "waCalls" -> a.copy(waCalls = !a.waCalls)
            else -> a.copy(waMsgs = !a.waMsgs)
        }))
    }

    /** A handlebar press from the simulated bike. */
    fun press(kind: String) {
        val label = if (kind == "tap") "Play / pause" else "Next track"
        commit { it.copy(alerts = logAlert(it.alerts, "button", "${if (kind == "tap") "Tap" else "Long press"} · $label", count = false)) }
        toast("Handlebar · $label")
    }

    fun fmt() = Fmt(_state.value.country.locale)

    // ---------- Link ----------
    fun simOn() {
        _live.update { it.copy(link = LinkState.CONNECTED, simulated = true, engine = 83, speed = 0) }
        toast("Simulated cluster linked.")
        ensureGeo()
    }

    fun simOff() { _live.update { it.copy(link = LinkState.IDLE, simulated = false, engine = null) }; ensureGeo() }

    // ---------- Settings ----------
    fun setTheme(key: String) = commit { it.copy(settings = it.settings.copy(theme = key)) }
    fun setRideChart(i: Int) = commit { it.copy(settings = it.settings.copy(rideChart = i.coerceIn(0, 2))) }
    fun setAccent(key: String) = commit { it.copy(settings = it.settings.copy(accent = key)) }

    /** Riding and sound switches by their web key. */
    fun toggleSetting(key: String) = commit { s ->
        val st = s.settings
        s.copy(settings = when (key) {
            "autoRide" -> st.copy(autoRide = !st.autoRide)
            "autoRecord" -> st.copy(autoRecord = !st.autoRecord)
            "wakeLock" -> st.copy(wakeLock = !st.wakeLock)
            "streetNames" -> st.copy(streetNames = !st.streetNames)
            "sounds" -> st.copy(sounds = !st.sounds)
            else -> st
        })
    }

    fun setCountry(code: String) {
        val co = Catalog.country(code)
        commit { it.copy(profile = it.profile.copy(country = co.code)) }
        toast("Country set to ${co.name}.")
    }

    // ---------- Profile ----------
    fun setAvatar(key: String) { if (Regex("^a([1-9]|1[01])$").matches(key)) commit { it.copy(profile = it.profile.copy(avatar = key, photo = null)) } }

    fun saveProfileAbout(name: String, phone: String, email: String, city: String) {
        val oldCity = _state.value.profile.city
        commit { it.copy(profile = it.profile.copy(name = name.trim().take(30), phone = phone.trim().take(20), email = email.trim().take(60), city = city.trim().take(24))) }
        if (city.trim() != oldCity) refreshWeather()
    }

    fun saveLicence(no: String, expiry: Long?) =
        commit { it.copy(profile = it.profile.copy(licenceNo = no.trim().uppercase().take(20), licenceExpiry = expiry)) }

    fun saveMedical(blood: String, medical: String, iceName: String, icePhone: String) = commit {
        it.copy(profile = it.profile.copy(
            blood = blood.takeIf { b -> b in Catalog.BLOOD_GROUPS } ?: "", medical = medical.trim().take(240),
            iceName = iceName.trim().take(30), icePhone = icePhone.trim().take(20),
        ))
    }

    // ---------- Vehicles ----------
    /** Adds a vehicle ([id] null) or edits one. Returns an error to show, or null. */
    fun saveVehicle(id: String?, model: String, nickname: String, plate: String, odo: Double?, colour: String): String? {
        if (odo == null || odo < 0 || odo >= 1_000_000) return "Enter the odometer in km."
        val m = Catalog.model(model).id
        val col = Catalog.COLOURS.firstOrNull { it.first == colour }?.first ?: Catalog.COLOURS[0].first
        val nick = nickname.trim().take(20)
        val pl = plate.trim().uppercase().take(13)
        if (id == null) {
            val v = newVehicle(model = m, nickname = nick, plate = pl, odo = odo, colour = col)
            commit { it.copy(vehicles = it.vehicles + v, active = v.id) }
            toast("Added to your garage.")
        } else {
            commit { s -> s.updateVehicle(id) { it.copy(model = m, nickname = nick, plate = pl, odo = Math.round(odo * 10) / 10.0, colour = col) } }
            toast("Saved.")
        }
        return null
    }

    fun deleteVehicle(id: String) {
        val s = _state.value
        if (s.vehicles.size < 2 || s.vehicles.none { it.id == id }) return
        commit { st -> val rest = st.vehicles.filter { it.id != id }; st.copy(vehicles = rest, active = if (st.active == id) rest.first().id else st.active) }
        toast("Removed from your garage.")
    }

    // ---------- Backup ----------
    fun backupText(): String = Vault.export(_state.value)

    /** Replaces everything with a backup. Returns false (and says why) when it isn't one. */
    fun restore(text: String): Boolean = try {
        val s = Vault.import(text)
        simOff()
        commit { s }
        toast("Restored ${app.waylink.core.plural(s.vehicles.size, "bike", "bikes")} from your backup.")
        refreshWeather()
        true
    } catch (e: Vault.NotABackup) { toast(e.message ?: "That isn’t a backup."); false }

    /** Wipes everything on this phone and returns to onboarding. */
    fun eraseAll() {
        simOff()
        commit { s -> freshState().copy(settings = s.settings) }
        toast("Erased. Waylink starts fresh.")
    }

    /** Like a fresh install: data and every setting go. */
    fun resetAll() {
        simOff()
        commit { freshState() }
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
