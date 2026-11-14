package app.waylink.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Place suggestions while typing an address. Photon (komoot's free OpenStreetMap search, no key) is made for
 * type-ahead; the phone's own geocoder steps in when it can't be reached. Only the typed text and, when known,
 * a rough position for nearby results (3 decimals, about 100 m) leave the phone.
 */
object PlaceSearch {
    data class Hit(val title: String, val detail: String, val lat: Double, val lng: Double) {
        /** What goes in the address field. */
        val label: String get() = if (detail.isEmpty()) title else "$title, $detail"
    }

    suspend fun search(ctx: Context, query: String, nearLat: Double? = null, nearLng: Double? = null): List<Hit> = withContext(Dispatchers.IO) {
        val q = query.trim().take(100)
        if (q.length < 3) return@withContext emptyList()
        runCatching { photon(q, nearLat, nearLng) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: runCatching { geocoder(ctx, q) }.getOrDefault(emptyList())
    }

    private fun photon(q: String, lat: Double?, lng: Double?): List<Hit> {
        val near = if (lat != null && lng != null) "&lat=${"%.3f".format(Locale.US, lat)}&lon=${"%.3f".format(Locale.US, lng)}" else ""
        val c = URL("https://photon.komoot.io/api/?limit=6&lang=en&q=" + URLEncoder.encode(q, "UTF-8") + near).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 6000
        c.setRequestProperty("User-Agent", "Waylink-Android"); c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode != 200) return emptyList()
            return parsePhoton(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }

    /** GeoJSON features to hits: the name (or street and number) first, then the area it's in. Duplicates go. */
    fun parsePhoton(json: String): List<Hit> {
        val feats = JSONObject(json).optJSONArray("features") ?: return emptyList()
        val out = LinkedHashMap<String, Hit>()
        for (i in 0 until feats.length()) {
            val f = feats.optJSONObject(i) ?: continue
            val xy = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val lng = xy.optDouble(0); val lat = xy.optDouble(1)
            if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0) continue
            val p = f.optJSONObject("properties") ?: continue
            fun s(k: String) = p.optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }
            val street = listOfNotNull(s("street"), s("housenumber")).joinToString(" ").ifEmpty { null }
            val title = s("name") ?: street ?: continue
            val detail = listOfNotNull(street.takeIf { s("name") != null }, s("district"), s("city") ?: s("county"), s("state"))
                .distinct().filter { it != title }.joinToString(", ")
            out.putIfAbsent("$title|$detail".lowercase(), Hit(title.take(60), detail.take(90), lat, lng))
        }
        return out.values.take(5)
    }

    private fun geocoder(ctx: Context, q: String): List<Hit> {
        if (!Geocoder.isPresent()) return emptyList()
        @Suppress("DEPRECATION")
        val list = Geocoder(ctx, Locale.ENGLISH).getFromLocationName(q, 5) ?: return emptyList()
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val title = a.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) } ?: a.thoroughfare ?: a.locality ?: q
            val detail = listOfNotNull(a.subLocality, a.locality, a.adminArea).distinct().filter { it != title }.joinToString(", ")
            Hit(title, detail, a.latitude, a.longitude)
        }
    }
}
