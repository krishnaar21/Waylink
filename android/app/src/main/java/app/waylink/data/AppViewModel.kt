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

/** What the bike link is doing (web link.state): idle, looking for bikes, linking, linked, or retrying after a drop. */
enum class LinkState { IDLE, SCANNING, CONNECTING, CONNECTED, RECONNECTING }

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
    /** Turn-by-turn in progress (rehearsal or one test turn), or null. */
    val nav: Nav? = null,
    val navSpeed: Int = 1,
    /** The turn picked on "Test one turn". */
    val turn: TurnTest = TurnTest(),
    /** A call shown on the bike (ringing or active), or null. */
    val call: Call? = null,
    /** Ride mode is open. */
    val rideMode: Boolean = false,
    /** The splash is up (at launch, or from Developer); the number restarts its animation. */
    val splash: Int = 1,
    /** The loading screen's line, while it shows. */
    val loading: String? = null,
    /** Developer: onboarding shown over the app as a preview. */
    val onbTest: Boolean = false,
    /** Developer preview states: null (my data), "empty" or "edge". Nothing is saved while previewing. */
    val scenario: String? = null,
    /** Each step of the Bluetooth link, newest last (time, text). */
    val linkLog: List<Pair<Long, String>> = emptyList(),
    /** Recent frames sent to the cluster, as hex, for "Show Bluetooth messages". */
    val frames: List<String> = emptyList(),
    /** The last frame built for the bike: its name and bytes (web link.lastFrame), and how many went out. */
    val lastFrame: Pair<String, ByteArray>? = null,
    val frameCount: Int = 0,
    /** Bikes seen by the last Bluetooth scan, strongest signal first. */
    val found: List<FoundBike> = emptyList(),
    /** The cluster's Bluetooth name while a real bike is linked or linking. */
    val linkName: String? = null,
    /** A sheet to open as soon as the main screens are up (e.g. "pair" after onboarding). */
    val ask: String? = null,
    /** A Settings section to scroll to and flash (web goto-riding), or null. */
    val focus: String? = null,
)

data class Call(val name: String, val ringing: Boolean, val since: Long)

data class Nav(
    val mode: String, val idx: Int, val maneuver: String, val stepMeters: Double, val totalMeters: Double,
    val street: String, val say: String, val exit: Int, val eta: Long,
)

data class TurnTest(val maneuver: String = "TURN_RIGHT", val m: Int = 250, val exit: Int = 2, val street: String = "")

/** A ride in progress: [pts] are [lat, lng, epoch ms]. */
data class Rec(val start: Long, val km: Double = 0.0, val top: Int = 0, val pts: List<List<Double>> = emptyList())

data class Toast(val text: String, val id: Long = System.nanoTime())

/**
 * Holds the app's state and saves it to a JSON file on this phone (same shape as the web app's
 * localStorage), with a short delay so quick taps write once.
 */
class AppViewModel(app: Application) : AndroidViewModel(app), app.waylink.ui.route.RouteActions {
    private val file = AtomicFile(File(app.filesDir, "waylink-state.json"))
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live.asStateFlow()
    private var saveJob: Job? = null

    /** The web's sound effects; follows the "Sound effects" switch. */
    val sound = Sound(app).also { it.enabled = _state.value.settings.sounds }

    /** The real Bluetooth link. Its states only show while the simulated bike is off. */
    private val link: BikeLink by lazy {
        BikeLink(
            app, viewModelScope,
            onState = { st, _ ->
                if (!_live.value.simulated) {
                    val was = _live.value.link
                    _live.update { it.copy(link = st, linkName = link.deviceName, engine = if (st == LinkState.CONNECTED) it.engine else null) }
                    if (st == LinkState.CONNECTED && was != LinkState.CONNECTED) onRealLinked()
                    if (was == LinkState.CONNECTED && st == LinkState.RECONNECTING) { sound.click(false); toast("Bike went quiet. Retrying.") }
                }
            },
            onLog = ::logLink,
            onFound = { f -> _live.update { it.copy(found = f) } },
            onButton = { kind -> press(kind) },
        )
    }

    init {
        refreshWeather(); sound.play(Sound.Fx.ENGINE_START)
        // A bike paired before links again by itself when the app opens, as the web app's restore().
        _state.value.vehicle.bike.id?.let { id ->
            if (BikeLink.hasPermissions(app) && link.enabled) viewModelScope.launch { delay(1200); if (_live.value.link == LinkState.IDLE) link.connect(id, _state.value.vehicle.bike.name) }
        }
    }

    private fun load(): AppState = try {
        if (file.baseFile.exists()) Vault.decode(String(file.readFully(), Charsets.UTF_8)) else freshState()
    } catch (e: Exception) { freshState() }

    /** The rider's real data, put aside while a developer preview state is shown (web realState). */
    private var realState: AppState? = null

    private fun commit(f: (AppState) -> AppState) {
        _state.update(f)
        if (realState != null) return // previewing: nothing is saved
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

    override fun toast(text: String) = _live.update { it.copy(toast = Toast(text)) }

    /** The web app's buzz(): a short vibration (or a pattern, off/on in ms) on toggles and events. Silent if unavailable. */
    private fun buzz(vararg ms: Long) {
        try {
            val ctx = getApplication<Application>()
            val vib = if (android.os.Build.VERSION.SDK_INT >= 31)
                (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            else @Suppress("DEPRECATION") (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
            if (vib == null || !vib.hasVibrator()) return
            val effect = if (ms.size <= 1) android.os.VibrationEffect.createOneShot(if (ms.isEmpty()) 8L else ms[0], android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            else android.os.VibrationEffect.createWaveform(longArrayOf(0L) + ms, -1)
            vib.vibrate(effect)
        } catch (_: Exception) { }
    }
    fun clearToast() = _live.update { it.copy(toast = null) }

    // ---------- Developer ----------
    fun showSplash() { _live.update { it.copy(splash = it.splash + 1) }; sound.play(Sound.Fx.ENGINE_START) }
    fun hideSplash() = _live.update { it.copy(splash = 0) }
    fun showLoading(text: String = "Loading your garage") = _live.update { it.copy(loading = text) }
    fun hideLoading() = _live.update { it.copy(loading = null) }
    fun showOnboarding(on: Boolean) { sound.click(on); _live.update { it.copy(onbTest = on) } }
    fun setScenario(k: String?) {
        val kind = k?.takeIf { x -> x == "empty" || x == "edge" }
        sound.click(true)
        if (kind == null) { realState?.let { _state.value = it }; realState = null }
        else { val real = realState ?: _state.value; realState = real; _state.value = app.waylink.core.scenarioState(kind, real) }
        _live.update { it.copy(scenario = kind) }
    }
    fun logLink(text: String) = _live.update { it.copy(linkLog = (it.linkLog + (System.currentTimeMillis() to text)).takeLast(200)) }

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

    fun finishOnboarding(simulated: Boolean, pair: Boolean = false) {
        commit { it.copy(onboarded = true) }
        if (simulated) simOn()
        if (pair) _live.update { it.copy(ask = "pick") }
    }

    fun clearAsk() = _live.update { it.copy(ask = null) }

    /** The ride mode hint "Auto-open · Settings › Riding": leave ride mode and show that section. */
    fun gotoRiding() = _live.update { it.copy(rideMode = false, focus = "riding") }

    fun clearFocus() = _live.update { it.copy(focus = null) }

    // ---------- Garage ----------
    fun switchVehicle(id: String) {
        buzz(10)
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
        buzz(12)
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
        sound.play(Sound.Fx.REV_UP)
        _live.update { it.copy(rec = Rec(System.currentTimeMillis())) }
        ensureGeo()
        toast("Recording. Ride safe.")
    }

    fun stopRecording(keep: Boolean) {
        val r = _live.value.rec ?: return
        _live.update { it.copy(rec = null) }
        sound.play(Sound.Fx.ENGINE_OFF)
        if (keep && r.km >= 0.1) {
            veh { it.addRide(r.start, System.currentTimeMillis(), r.km, r.top.toDouble(), r.pts) }
            toast("Saved · ${fmt().num(r.km, 1)} km")
        } else toast(if (keep) "Too short to keep." else "Ride discarded.")
        ensureGeo()
    }

    fun renameTrip(id: String, title: String) = veh { v -> v.copy(trips = v.trips.map { if (it.id == id) it.copy(title = title.trim().take(60).ifEmpty { it.title }) else it }) }
    fun deleteTrip(id: String) { veh { v -> v.copy(trips = v.trips.filter { it.id != id }) }; toast("Ride deleted. The odometer is unchanged.") }

    override fun onCleared() { stopGps(); sound.release(); runCatching { link.stopScan() }; super.onCleared() }

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
                ring(name)
                buzz(30, 60, 30)
                sound.play(Sound.Fx.HORN)
            }
            "missed" -> {
                if (!a.calls) { toast("Phone calls are switched off above."); return }
                commit { it.copy(alerts = logAlert(it.alerts, "missed", name).let { x -> x.copy(missed = x.missed + NamedEvent(name, now)) }) }
                if (linked) pushFrame("Missed call", app.waylink.core.Protocol.missedCall(name))
                toast(if (linked) "Missed call from $name sent." else "Saved. Link the bike to send it.")
            }
            "wa" -> {
                if (!a.waMsgs) { toast("WhatsApp messages are switched off above."); return }
                commit { it.copy(alerts = logAlert(it.alerts, "wa", name).let { x -> x.copy(wa = x.wa + NamedEvent(name, now)) }) }
                if (linked) pushFrame("WhatsApp", app.waylink.core.Protocol.whatsAppAlert(name))
                toast(if (linked) "$name on the cluster for 6 s." else "Saved. Link the bike to send it.")
            }
        }
    }

    fun clearMissed() { commit { it.copy(alerts = it.alerts.copy(missed = emptyList())) }; toast("Missed calls cleared.") }
    fun clearWa() { commit { it.copy(alerts = it.alerts.copy(wa = emptyList())) }; toast("WhatsApp alerts cleared.") }

    fun toggleAlert(key: String) { buzz(6); commit { s ->
        val a = s.settings.alerts
        s.copy(settings = s.settings.copy(alerts = when (key) {
            "calls" -> a.copy(calls = !a.calls)
            "waCalls" -> a.copy(waCalls = !a.waCalls)
            else -> a.copy(waMsgs = !a.waMsgs)
        }))
    }
        val a = _state.value.settings.alerts
        sound.click(when (key) { "calls" -> a.calls; "waCalls" -> a.waCalls; else -> a.waMsgs })
    }

    private var ringJob: Job? = null

    /** Shows an incoming call; unanswered after 25 s it becomes a missed call. */
    private fun ring(name: String) {
        _live.update { it.copy(call = Call(name, true, System.currentTimeMillis())) }
        ringJob?.cancel()
        ringJob = viewModelScope.launch {
            delay(25_000)
            val c = _live.value.call
            if (c != null && c.ringing) {
                _live.update { it.copy(call = null) }
                commit { it.copy(alerts = logAlert(it.alerts, "missed", c.name).let { x -> x.copy(missed = x.missed + NamedEvent(c.name, System.currentTimeMillis())) }) }
            }
        }
    }

    fun answerCall() { ringJob?.cancel(); _live.update { l -> l.copy(call = l.call?.copy(ringing = false, since = System.currentTimeMillis())) } }
    fun declineCall() { ringJob?.cancel(); _live.update { it.copy(call = null) } }
    fun endCall() { ringJob?.cancel(); _live.update { it.copy(call = null) }; toast("Call ended.") }

    fun openRideMode(open: Boolean) { if (open) buzz(12); _live.update { it.copy(rideMode = open) } }

    /** A handlebar press from the simulated bike. */
    fun press(kind: String) {
        // With a call showing, a tap answers and a long press declines or ends it.
        val call = _live.value.call
        if (call != null) {
            if (kind == "tap" && call.ringing) answerCall() else if (call.ringing) declineCall() else endCall()
            commit { it.copy(alerts = logAlert(it.alerts, "button", if (kind == "tap") "Tap · Answer" else "Long press · ${if (call.ringing) "Decline" else "End call"}", count = false)) }
            return
        }
        val label = if (kind == "tap") "Play / pause" else "Next track"
        commit { it.copy(alerts = logAlert(it.alerts, "button", "${if (kind == "tap") "Tap" else "Long press"} · $label", count = false)) }
        toast("Handlebar · $label")
    }

    fun fmt() = Fmt(_state.value.country.locale)

    // ---------- Link ----------
    /** Can this phone pair at all, and is Bluetooth on? */
    fun btSupported() = link.supported
    fun btEnabled() = link.enabled

    /** Look for bikes (web pair()); [showAll] lists every device nearby, for clusters that don't advertise the service. */
    fun pair(showAll: Boolean = false) {
        if (!link.supported) { toast("This phone has no Bluetooth LE. Try the simulated bike."); return }
        if (!link.enabled) { toast("Turn on Bluetooth first."); return }
        if (_live.value.simulated) quietSimOff()
        link.scan(showAll)
    }

    fun stopScan() { link.stopScan(); if (_live.value.link == LinkState.SCANNING) _live.update { it.copy(link = LinkState.IDLE) } }

    /** The rider picked a bike from the list: remember it on this vehicle and link. */
    fun connectTo(b: FoundBike) {
        commit { it.updateVehicle { v -> v.copy(bike = app.waylink.core.PairedBike(b.address, b.name)) } }
        link.connect(b.address, b.name)
    }

    fun reconnect() {
        val bike = _state.value.vehicle.bike
        val id = bike.id ?: run { pair(false); return }
        if (!link.enabled) { toast("Turn on Bluetooth first."); return }
        if (_live.value.simulated) quietSimOff()
        link.connect(id, bike.name)
    }

    /** Cancel, Stop or Disconnect (web cancel-link / disconnect). */
    fun disconnect() {
        if (_live.value.simulated) { simOff(); return }
        streamJob?.cancel()
        link.disconnect()
        sound.click(false)
        ensureGeo()
    }

    /** Forget this bike: unlink and drop its address from the vehicle. */
    fun forgetBike() {
        if (_live.value.simulated) quietSimOff() else link.forget()
        streamJob?.cancel()
        commit { it.updateVehicle { v -> v.copy(bike = app.waylink.core.PairedBike()) } }
        _live.update { it.copy(link = LinkState.IDLE, linkName = null) }
        toast("Bike forgotten. Pair it again any time.")
    }

    private fun onRealLinked() {
        buzz(15); sound.click(true)
        logLink("Linked. Status every 5 s, turns every 0.8 s")
        startStream()
        toast("Linked to ${link.deviceName}.")
        ensureGeo()
    }

    private fun quietSimOff() { streamJob?.cancel(); _live.update { it.copy(link = LinkState.IDLE, simulated = false, engine = null) } }

    fun simOn() {
        if (!_live.value.simulated && _live.value.link != LinkState.IDLE) link.disconnect()
        if (_live.value.link != LinkState.CONNECTED) { buzz(15); sound.click(true) }
        logLink("Simulated cluster: started"); logLink("Linked. Status every 5 s, turns every 0.8 s")
        _live.update { it.copy(link = LinkState.CONNECTED, simulated = true, engine = 83, speed = 0) }
        startStream()
        toast("Simulated cluster linked.")
        ensureGeo()
    }

    // The frames the cluster would get, built by the real protocol code, so "Show Bluetooth messages" has something to show.
    private var streamJob: Job? = null
    private fun pushFrame(label: String, bytes: ByteArray) {
        // A real cluster gets the bytes too; the simulated one only shows them.
        if (!_live.value.simulated) link.write(when (label) { "Turn" -> "tbt"; "Missed call" -> "missedCall"; "WhatsApp" -> "alerts"; else -> "general" }, bytes)
        _live.update { it.copy(frames = (it.frames + "$label · ${app.waylink.core.Protocol.hex(bytes)}").takeLast(6), lastFrame = label to bytes, frameCount = it.frameCount + 1) }
    }
    private fun startStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var beat = 0L
            while (_live.value.link == LinkState.CONNECTED) {
                val l = _live.value
                val now = System.currentTimeMillis()
                if (now - beat >= app.waylink.core.Protocol.HEARTBEAT_MS) {
                    beat = now
                    val call = l.call
                    pushFrame("Status", app.waylink.core.Protocol.generalV2(l.battery ?: 80,
                        if (call == null) 0 else if (call.ringing) app.waylink.core.Protocol.Call.INCOMING else app.waylink.core.Protocol.Call.ACTIVE,
                        call?.name, _state.value.alerts.missed.size))
                }
                l.nav?.let { n ->
                    val eta = java.time.Instant.ofEpochMilli(n.eta).atZone(java.time.ZoneId.systemDefault())
                    pushFrame("Turn", app.waylink.core.Protocol.tbtFrame(app.waylink.core.Maneuvers.of(n.maneuver).op, n.stepMeters, n.totalMeters,
                        (eta.hour + 11) % 12 + 1, eta.minute, eta.hour < 12, n.exit, if (_state.value.settings.streetNames) n.street else ""))
                }
                delay(app.waylink.core.Protocol.TBT_STREAM_MS)
            }
        }
    }

    fun simOff() { streamJob?.cancel(); sound.click(false); logLink("Disconnected"); _live.update { it.copy(link = LinkState.IDLE, simulated = false, engine = null) }; ensureGeo() }

    // ---------- Route ----------
    private var navJob: Job? = null

    private fun etaFor(total: Double): Long {
        val sp = _live.value.speed
        val v = if (sp > 5) sp / 3.6 else 11.1
        return System.currentTimeMillis() + (total / v * 1000).toLong()
    }

    override fun startRehearsal() {
        val st = app.waylink.core.Rehearsal.STEPS[0]
        val total = app.waylink.core.Rehearsal.totalMeters
        _live.update { it.copy(nav = Nav("rehearse", 0, st.maneuver, st.m, total, st.street, st.say, st.exit, etaFor(total)), navSpeed = 1) }
        toast(if (_live.value.link == LinkState.CONNECTED) "Turns are streaming to your bike." else "Rehearsing on the phone. Link the bike to see it on the cluster.")
        navJob?.cancel()
        navJob = viewModelScope.launch {
            var last = System.currentTimeMillis()
            while (true) {
                delay(250)
                val now = System.currentTimeMillis()
                val dt = (now - last) / 1000.0; last = now
                val n = _live.value.nav ?: break
                if (n.mode != "rehearse") break
                val sp = _live.value.speed
                val v = (if (sp > 5) sp / 3.6 else 11.1) * _live.value.navSpeed
                val step = n.stepMeters - v * dt
                val total = maxOf(0.0, n.totalMeters - v * dt)
                if (step <= 0) nextTurn() else _live.update { it.copy(nav = n.copy(stepMeters = step, totalMeters = total, eta = etaFor(total))) }
            }
        }
    }

    override fun nextTurn() {
        val n = _live.value.nav ?: return
        buzz(10)
        if (n.mode != "rehearse") return
        val steps = app.waylink.core.Rehearsal.STEPS
        val total = maxOf(0.0, n.totalMeters - maxOf(0.0, n.stepMeters))
        val i = n.idx + 1
        if (i >= steps.size) { endNav(); toast("You’ve arrived."); return }
        val st = steps[i]
        _live.update { it.copy(nav = n.copy(idx = i, maneuver = st.maneuver, stepMeters = st.m, totalMeters = total, street = st.street, say = st.say, exit = st.exit, eta = etaFor(total))) }
    }

    override fun setNavSpeed(x: Int) = _live.update { it.copy(navSpeed = x.coerceIn(1, 16)) }

    fun setTurn(t: TurnTest) = _live.update { it.copy(turn = t.copy(m = t.m.coerceIn(0, 3000), exit = t.exit.coerceIn(1, 7), street = t.street.take(31))) }

    override fun sendTurn() {
        val t = _live.value.turn
        val m = app.waylink.core.Maneuvers.of(t.maneuver)
        val street = t.street.trim()
        navJob?.cancel()
        _live.update {
            it.copy(nav = Nav(
                "single", 0, t.maneuver, t.m.toDouble(), t.m + 1200.0, street, if (street.isNotEmpty()) "${m.label} · $street" else m.label,
                if (m.glyph.startsWith("roundabout")) t.exit else 0, System.currentTimeMillis() + 6 * 60_000,
            ))
        }
        if (_live.value.link != LinkState.CONNECTED) toast("Showing on the phone. Link the bike to see it on the cluster.")
    }

    /** Drags the single test turn's distance. */
    override fun setNavDistance(m: Double) {
        val n = _live.value.nav ?: return
        val d = m - n.stepMeters
        _live.update { it.copy(nav = n.copy(stepMeters = m, totalMeters = maxOf(m, n.totalMeters + d))) }
    }

    override fun endNav() { navJob?.cancel(); navJob = null; _live.update { it.copy(nav = null) } }

    // ---------- Parked bike ----------
    /** Saves where the phone is now as the bike's parking spot. Location permission is asked by the screen first. */
    @android.annotation.SuppressLint("MissingPermission")
    fun saveParking() {
        if (_live.value.simulated) { setParking(sim[0], sim[1]); return }
        if (!hasLocation()) { toast("Allow location to save where you parked."); return }
        val provider = when {
            locations.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) -> android.location.LocationManager.GPS_PROVIDER
            locations.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) -> android.location.LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) { toast("Location is off. Turn it on in quick settings."); return }
        toast("Finding where you are…")
        try {
            androidx.core.location.LocationManagerCompat.getCurrentLocation(
                locations, provider, null as android.os.CancellationSignal?, androidx.core.content.ContextCompat.getMainExecutor(getApplication()),
                androidx.core.util.Consumer<android.location.Location?> { loc ->
                val l = loc ?: locations.getLastKnownLocation(provider)
                if (l == null) toast("Couldn’t get a location fix. Try again outside.") else setParking(l.latitude, l.longitude)
            })
        } catch (e: SecurityException) { toast("Allow location to save where you parked.") }
    }

    private fun setParking(lat: Double, lng: Double) {
        veh { it.copy(parking = app.waylink.core.Parking(r6(lat), r6(lng), System.currentTimeMillis())) }
        toast("Parking saved.")
    }

    fun clearParking() { veh { it.copy(parking = null) }; toast("Parking cleared.") }

    // ---------- Glovebox ----------
    private val gloveDir get() = File(getApplication<Application>().filesDir, "glove")
    fun docFile(slot: String) = File(gloveDir, slot.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    /** Copies a picked photo or PDF into app storage. Up to 15 MB. */
    fun addDoc(slot: String, uri: android.net.Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    val cr = ctx.contentResolver
                    val type = cr.getType(uri) ?: "application/octet-stream"
                    if (!type.startsWith("image/") && type != "application/pdf") return@withContext "Choose a photo or a PDF."
                    var name = "Document"
                    cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
                    gloveDir.mkdirs()
                    val tmp = File(gloveDir, ".incoming")
                    var total = 0L
                    cr.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                total += n
                                if (total > 15L * 1024 * 1024) { out.close(); tmp.delete(); return@withContext "That file is over 15 MB. Try a smaller photo." }
                                out.write(buf, 0, n)
                            }
                        }
                    } ?: return@withContext "Couldn’t open the file."
                    val dest = docFile(slot)
                    dest.delete()
                    if (!tmp.renameTo(dest)) return@withContext "Couldn’t save the file."
                    commit { s ->
                        val old = s.glove.firstOrNull { it.slot == slot }
                        s.copy(glove = s.glove.filter { it.slot != slot } + app.waylink.core.GloveDoc(slot, name.take(80), type, System.currentTimeMillis(), old?.expiry))
                    }
                    null
                } catch (e: Exception) { "Couldn’t save the file." }
            }
            toast(res ?: "Saved in your glovebox.")
        }
    }

    fun setDocExpiry(slot: String, ts: Long?) = commit { s -> s.copy(glove = s.glove.map { if (it.slot == slot) it.copy(expiry = ts) else it }) }

    fun deleteDoc(slot: String) {
        docFile(slot).delete()
        commit { s -> s.copy(glove = s.glove.filter { it.slot != slot }) }
        toast("Removed from your glovebox.")
    }

    private fun wipeGlove() { try { gloveDir.deleteRecursively() } catch (e: Exception) { } }

    // ---------- Settings ----------
    fun setTheme(key: String) = commit { it.copy(settings = it.settings.copy(theme = key)) }
    fun setRideChart(i: Int) { sound.click(true); commit { it.copy(settings = it.settings.copy(rideChart = i.coerceIn(0, 2))) } }
    fun setProgress(k: String) { sound.click(true); commit { it.copy(settings = it.settings.copy(progress = if (k in listOf("gauge", "road", "tread", "dipstick")) k else "gauge")) } }
    fun setIconLook(k: String) { sound.click(true); commit { it.copy(settings = it.settings.copy(icons = if (k == "duo" || k == "fill") k else "line")) } }
    fun setAppIcon(k: String) { sound.click(true); commit { it.copy(settings = it.settings.copy(iconStyle = k)) }; AppIcon.apply(getApplication(), k, _state.value.settings.accent) }
    fun setFont(k: String) { sound.click(true); commit { it.copy(settings = it.settings.copy(font = k)) } }
    fun setBodyFont(k: String) { sound.click(true); commit { it.copy(settings = it.settings.copy(bodyFont = k)) } }
    /** UI-only sounds the screens ask for (tab swipes, open/close disclosure, slider). */
    fun uiClick(on: Boolean = true) = sound.click(on)
    fun uiSwipe(dir: Int) = sound.swipe(dir)
    override fun uiTick(p: Float) = sound.tick(p)
    /** Remembers that the auto-ride question was asked (web settings.autoAsked); true if this is the first time. */
    fun takeAutoAsk(): Boolean {
        if (_state.value.settings.autoAsked) return false
        commit { it.copy(settings = it.settings.copy(autoAsked = true)) }
        return true
    }
    fun playSplashSound() = sound.play(Sound.Fx.ENGINE_START)
    // The launcher icon changes only when picked in Settings > Icons: switching it can unpin a Home Screen shortcut.
    fun setAccent(key: String) = commit { it.copy(settings = it.settings.copy(accent = key)) }

    /** Riding and sound switches by their web key. */
    override fun toggleSetting(key: String) { buzz(6); commit { s ->
        val st = s.settings
        s.copy(settings = when (key) {
            "autoRide" -> st.copy(autoRide = !st.autoRide)
            "autoRecord" -> st.copy(autoRecord = !st.autoRecord)
            "wakeLock" -> st.copy(wakeLock = !st.wakeLock)
            "streetNames" -> st.copy(streetNames = !st.streetNames)
            "sounds" -> st.copy(sounds = !st.sounds)
            "devFrames" -> st.copy(devFrames = !st.devFrames)
            "allThemes" -> st.copy(allThemes = !st.allThemes)
            else -> st
        })
    }
        val st = _state.value.settings
        sound.enabled = st.sounds
        sound.click(when (key) { "autoRide" -> st.autoRide; "autoRecord" -> st.autoRecord; "wakeLock" -> st.wakeLock; "streetNames" -> st.streetNames; "sounds" -> st.sounds; "devFrames" -> st.devFrames; "allThemes" -> st.allThemes; else -> true })
    }

    fun setCountry(code: String) {
        val co = Catalog.country(code)
        commit { it.copy(profile = it.profile.copy(country = co.code)) }
        toast("Country set to ${co.name}.")
    }

    // ---------- Profile ----------
    /**
     * Profile photo (web av-photo): read the picked image, centre-crop it to a 320 px square JPEG in app storage and
     * point the profile at it. A new file name each time, so the picture refreshes; the old file is removed.
     */
    fun setPhoto(uri: android.net.Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val out = runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 320) sample *= 2
                val src = ctx.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null
                val side = minOf(src.width, src.height)
                val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                val small = android.graphics.Bitmap.createScaledBitmap(sq, 320, 320, true)
                val f = File(ctx.filesDir, "profile-${System.currentTimeMillis()}.jpg")
                f.outputStream().use { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it) }
                f.absolutePath
            }.getOrNull()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (out == null) { toast("Couldn’t open that picture"); return@withContext }
                val old = _state.value.profile.photo
                commit { it.copy(profile = it.profile.copy(photo = out)) }
                if (old != null && old != out && old.startsWith(ctx.filesDir.absolutePath)) runCatching { File(old).delete() }
            }
        }
    }

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
        commit { cur -> s.copy(glove = cur.glove) }
        toast("Restored ${app.waylink.core.plural(s.vehicles.size, "bike", "bikes")} from your backup.")
        refreshWeather()
        true
    } catch (e: Vault.NotABackup) { toast(e.message ?: "That isn’t a backup."); false }

    /** Wipes everything on this phone and returns to onboarding. */
    fun eraseAll() {
        simOff()
        commit { s -> freshState().copy(settings = s.settings) }
        wipeGlove()
        toast("Erased. Waylink starts fresh.")
    }

    /** Like a fresh install: data and every setting go. */
    fun resetAll() {
        simOff()
        commit { freshState() }
        wipeGlove()
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
