package app.waylink.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /** What a search came back with. [failed]: neither source could be reached (offline), so "no matches" would be wrong. */
    data class Result(val hits: List<Hit>, val failed: Boolean)

    /**
     * Both sources at once: Photon is good at half-typed names, the phone's geocoder (Google's on most phones) knows
     * more Indian shops and addresses. Photon's come first, the geocoder's fill in what it missed, nearest area first.
     */
    suspend fun search(ctx: Context, query: String, nearLat: Double? = null, nearLng: Double? = null): Result = withContext(Dispatchers.IO) {
        val q = query.trim().take(100)
        if (q.length < 3) return@withContext Result(emptyList(), false)
        val here = if (nearLat != null && nearLng != null) nearLat to nearLng else lastFix(ctx)
        coroutineScope {
            val a = async { runCatching { photon(q, here?.first, here?.second) } }
            val b = async { runCatching { geocoder(ctx, q, here) } }
            val pa = a.await(); val pb = b.await()
            val all = LinkedHashMap<String, Hit>()
            (pa.getOrNull().orEmpty() + pb.getOrNull().orEmpty()).forEach { h ->
                // The same spot from both (within about 150 m and the same name) shows once.
                val dupe = all.values.any { o -> o.title.equals(h.title, true) && kotlin.math.abs(o.lat - h.lat) < 0.0015 && kotlin.math.abs(o.lng - h.lng) < 0.0015 }
                if (!dupe) all.putIfAbsent("${h.title}|${h.detail}".lowercase(), h)
            }
            Result(all.values.take(6), failed = all.isEmpty() && pa.isFailure && pb.isFailure)
        }
    }

    /**
     * The spot in a pasted link or coordinates. A short Maps link (maps.app.goo.gl) is followed to the full one,
     * which carries the coordinates; only that link goes out. Null when nothing in it says where.
     */
    suspend fun spotFrom(text: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        app.waylink.core.GeoLink.parse(text)?.let { return@withContext it }
        if (!app.waylink.core.GeoLink.isShortLink(text)) return@withContext null
        var url = text.trim()
        repeat(5) {
            val c = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return@withContext null
            try {
                c.instanceFollowRedirects = false; c.connectTimeout = 6000; c.readTimeout = 6000; c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "Waylink-Android")
                val next = runCatching { if (c.responseCode in 300..399) c.getHeaderField("Location") else null }.getOrNull() ?: return@withContext null
                url = URL(URL(url), next).toString()
                if (!url.startsWith("https://")) return@withContext null
                app.waylink.core.GeoLink.parse(runCatching { java.net.URLDecoder.decode(url, "UTF-8") }.getOrDefault(url))?.let { return@withContext it }
            } finally { c.disconnect() }
        }
        null
    }

    /** The phone's last known position, when location is allowed. Only used to rank nearby places first. */
    @android.annotation.SuppressLint("MissingPermission")
    fun lastFix(ctx: Context): Pair<Double, Double>? = runCatching {
        val ok = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!ok) return@runCatching null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return@runCatching null
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }.getOrNull()

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

    private fun geocoder(ctx: Context, q: String, near: Pair<Double, Double>?): List<Hit> {
        if (!Geocoder.isPresent()) return emptyList()
        val g = Geocoder(ctx, Locale.ENGLISH)
        // Around the rider first (about 150 km each way), then anywhere.
        @Suppress("DEPRECATION")
        val list = near?.let { (la, ln) ->
            runCatching { g.getFromLocationName(q, 5, (la - 1.4).coerceAtLeast(-90.0), (ln - 1.4).coerceAtLeast(-180.0), (la + 1.4).coerceAtMost(90.0), (ln + 1.4).coerceAtMost(180.0)) }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: g.getFromLocationName(q, 5) ?: return emptyList()
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val title = a.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) } ?: a.thoroughfare ?: a.locality ?: q
            val detail = listOfNotNull(a.subLocality, a.locality, a.adminArea).distinct().filter { it != title }.joinToString(", ")
            Hit(title.take(60), detail.take(90), a.latitude, a.longitude)
        }
    }
}
