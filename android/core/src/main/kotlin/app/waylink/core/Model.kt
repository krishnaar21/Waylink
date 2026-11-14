package app.waylink.core

import kotlinx.serialization.Serializable

/*
 * The data model. Field names and shapes match the web app's saved state (web-v0/js/store.js, format v2),
 * so a web backup restores on Android, and later the web dashboard and iOS can read the same JSON.
 * Distances are km (Double); times are epoch milliseconds (Long).
 */

@Serializable
data class AppState(
    val v: Int = 2,
    val onboarded: Boolean = false,
    val sample: Boolean = false,
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val alerts: Alerts = Alerts(),
    val vehicles: List<Vehicle> = emptyList(),
    val active: String = "",
    /** Papers in the glovebox. The files stay in app storage, never in backups. */
    val glove: List<GloveDoc> = emptyList(),
    /** Saved places (home, work, favourites), newest last. */
    val places: List<Place> = emptyList(),
) {
    /** The vehicle being ridden now. */
    val vehicle: Vehicle get() = vehicles.firstOrNull { it.id == active } ?: vehicles.first()
    val country: Country get() = Catalog.country(profile.country)
    val model: BikeModel get() = Catalog.model(vehicle.model)

    fun updateVehicle(id: String = vehicle.id, f: (Vehicle) -> Vehicle): AppState =
        copy(vehicles = vehicles.map { if (it.id == id) f(it) else it })
}

@Serializable
data class Profile(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val city: String = "",
    val country: String = "IN",
    val licenceNo: String = "",
    val licenceExpiry: Long? = null,
    val blood: String = "",
    val medical: String = "",
    val iceName: String = "",
    val icePhone: String = "",
    /** A data: URL on the web; a file path on Android. */
    val photo: String? = null,
    /** a1 to a11. */
    val avatar: String = "a1",
    val since: Long = 0,
    /** Where the petrol rate comes from, set by "Use my location". */
    val fuelArea: FuelArea? = null,
    /** Weather from "Use my location": wins over [city] until the city changes. */
    val weatherAt: WeatherAt? = null,
)

/** A paper in the glovebox: [slot] is "<vehicle id>:rc|insurance|puc|other" or "rider:licence". */
@Serializable
data class GloveDoc(val slot: String, val name: String, val type: String, val added: Long, val expiry: Long? = null)

@Serializable
data class FuelArea(val state: String, val place: String = "", val ts: Long = 0)

@Serializable
data class WeatherAt(val lat: Double, val lon: Double, val place: String = "")

@Serializable
data class Settings(
    /** auto, sketch (Paper), sketchdark (Paper dark), or with "Show all themes": paper (Light), obsidian (Dark), amoled (Navy). */
    val theme: String = "auto",
    val accent: String = "wine",
    val sounds: Boolean = true,
    val autoRide: Boolean = false,
    val autoRecord: Boolean = false,
    /** The "Start rides by themselves?" question was asked after the first link. */
    val autoAsked: Boolean = false,
    val wakeLock: Boolean = true,
    val streetNames: Boolean = false,
    /** Handlebar tap plays or pauses the phone's music, a long press skips (when no call is showing). */
    val handlebarMedia: Boolean = true,
    val simulated: Boolean = false,
    val rideChart: Int = 0,
    val progress: String = "gauge",
    /** Title face (web FONTS key) and body face (BODY_FONTS key). */
    val font: String = "system",
    val bodyFont: String = "system",
    /** App icon style (web ICON_STYLES) and the icon look: line, duo (Glow) or fill (Duotone). */
    val iconStyle: String = "classic",
    val icons: String = "line",
    /** Developer: raw Bluetooth frames on Route, and every theme in the picker. */
    val devFrames: Boolean = false,
    val allThemes: Boolean = false,
    val alerts: AlertSettings = AlertSettings(),
    /** Where live turns come from: a NavSource key. */
    val navSource: String = "gmaps",
    /** Speed alert limit in km/h; 0 is off. */
    val speedAlert: Int = 0,
)

@Serializable
data class AlertSettings(val calls: Boolean = true, val waCalls: Boolean = true, val waMsgs: Boolean = true)

@Serializable
data class Alerts(
    val missed: List<NamedEvent> = emptyList(),
    val wa: List<NamedEvent> = emptyList(),
    val sent: Int = 0,
    val log: List<LogEvent> = emptyList(),
)

@Serializable
data class NamedEvent(val name: String, val ts: Long)

@Serializable
data class LogEvent(val kind: String, val name: String, val ts: Long)

@Serializable
data class Vehicle(
    val id: String = uid(),
    val model: String = "ns200",
    val nickname: String = "",
    val plate: String = "",
    val colour: String = Catalog.COLOURS[0].first,
    val photo: String? = null,
    val addedAt: Long = 0,
    val odo: Double = 0.0,
    val tripA: TripMeter = TripMeter(),
    val tripB: TripMeter = TripMeter(),
    val fuel: List<FuelEntry> = emptyList(),
    val fuelPrice: Double? = null,
    val service: List<ServiceItem> = emptyList(),
    val records: List<ServiceRecord> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val rideNo: Int = 0,
    val parking: Parking? = null,
    /** The paired Bluetooth cluster. Never exported in backups. */
    val bike: PairedBike = PairedBike(),
)

@Serializable
data class TripMeter(val km: Double = 0.0, val since: Long = 0)

@Serializable
data class FuelEntry(val id: String = uid(), val ts: Long, val odo: Double, val litres: Double, val cost: Double, val full: Boolean)

@Serializable
data class ServiceItem(
    val id: String = uid(),
    val kind: String,
    val name: String,
    val icon: String = "wrench",
    val everyKm: Double = 0.0,
    val everyDays: Int = 0,
    val lastKm: Double = 0.0,
    val lastDate: Long = 0,
    /** Periodic service follows Bajaj's milestones (500, 5,000, 10,000 km, then every 5,000). */
    val milestone: Boolean = false,
    val targetKm: Double? = null,
    /** A different first service: due at this odometer, or on this date, instead of one interval after [lastKm]/[lastDate]. Cleared once done. */
    val firstKm: Double? = null,
    val firstDate: Long? = null,
)

@Serializable
data class ServiceRecord(
    val id: String = uid(),
    val date: Long,
    val odo: Double,
    val title: String,
    val workshop: String = "",
    val cost: Double = 0.0,
    val notes: String = "",
)

@Serializable
data class ChecklistItem(val id: String = uid(), val text: String, val done: Boolean = false)

@Serializable
data class Trip(
    val id: String = uid(),
    val n: Int,
    val start: Long,
    val end: Long,
    val km: Double,
    val sec: Double,
    val top: Double,
    val avg: Double,
    val title: String,
    /** [lat, lng, epoch ms] per point. */
    val pts: List<List<Double>> = emptyList(),
)

@Serializable
data class Parking(val lat: Double, val lng: Double, val ts: Long)

@Serializable
data class PairedBike(val id: String? = null, val name: String? = null)

fun uid(): String = java.util.UUID.randomUUID().toString()
