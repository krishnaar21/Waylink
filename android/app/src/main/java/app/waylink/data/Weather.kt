package app.waylink.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Weather for the ride from Open-Meteo (free, no key), as on the web app. Only the rider's city name is
 * sent, to look up its coordinates; nothing else leaves the phone.
 */
object Weather {
    data class Hour(val ts: Long, val temp: Double?, val rain: Int, val code: Int, val day: Boolean)
    data class Data(val place: String, val ts: Long, val temp: Double, val feels: Double, val code: Int, val wind: Double, val day: Boolean, val hours: List<Hour>)

    sealed interface State {
        data object Idle : State
        data object Need : State
        data class Loading(val last: Data?) : State
        data class Ready(val data: Data) : State
        data class Error(val msg: String) : State
    }

    /** WMO weather code → label and icon name, same mapping as web-v0/js/store.js wmo(). */
    fun wmo(code: Int, day: Boolean = true): Pair<String, String> = when {
        code == 0 -> (if (day) "Clear" else "Clear night") to (if (day) "wxSun" else "wxMoon")
        code <= 2 -> "Partly cloudy" to (if (day) "wxPart" else "wxPartNight")
        code == 3 -> "Overcast" to "wxCloud"
        code == 45 || code == 48 -> "Fog" to "wxFog"
        code in 51..57 -> "Drizzle" to "wxDrizzle"
        code in 61..67 || code in 80..82 -> (if (code >= 65 || code == 82) "Heavy rain" else "Rain") to "wxRain"
        code in 71..77 || code == 85 || code == 86 -> "Snow" to "wxSnow"
        code in 95..99 -> "Thunderstorm" to "wxStorm"
        else -> "Cloudy" to "wxCloud"
    }

    fun fetch(city: String): State = try {
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=$q"))
        val res = geo.optJSONArray("results")?.optJSONObject(0) ?: return State.Error("Couldn’t find $city.")
        val lat = res.getDouble("latitude")
        val lon = res.getDouble("longitude")
        val j = JSONObject(get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timeformat=unixtime&forecast_hours=13" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,is_day",
        ))
        State.Ready(parse(j, res.optString("name", city)))
    } catch (e: Exception) {
        State.Error("Couldn’t reach the weather service.")
    }

    fun parse(j: JSONObject, place: String, now: Long = System.currentTimeMillis()): Data {
        val cur = j.getJSONObject("current")
        val h = j.optJSONObject("hourly")
        val hours = mutableListOf<Hour>()
        if (h != null) {
            val time = h.getJSONArray("time")
            for (i in 0 until time.length()) {
                val ts = time.getLong(i) * 1000
                if (ts < now - 3_600_000) continue
                hours += Hour(
                    ts, h.optJSONArray("temperature_2m")?.optDouble(i)?.takeIf { !it.isNaN() },
                    h.optJSONArray("precipitation_probability")?.optInt(i, 0) ?: 0,
                    h.optJSONArray("weather_code")?.optInt(i, 0) ?: 0,
                    (h.optJSONArray("is_day")?.optInt(i, 1) ?: 1) != 0,
                )
            }
        }
        return Data(
            place, now, cur.getDouble("temperature_2m"), cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            cur.optInt("weather_code", 0), cur.optDouble("wind_speed_10m", 0.0), cur.optInt("is_day", 1) != 0, hours.take(12),
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Waylink-Android")
        return try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
