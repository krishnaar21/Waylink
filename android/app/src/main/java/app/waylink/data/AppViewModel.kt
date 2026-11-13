package app.waylink.data

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.waylink.core.AppState
import app.waylink.core.Catalog
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
