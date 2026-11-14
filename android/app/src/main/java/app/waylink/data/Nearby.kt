package app.waylink.data

import app.waylink.core.haversine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Hospitals and petrol pumps near the phone, from OpenStreetMap through the free Overpass API (no key). One
 * query per tap, kept to a small radius and result count as its usage policy asks; a second public server is
 * tried if the first is busy. Only a rough position (3 decimals, about 100 m) leaves the phone.
 */
object Nearby {
    enum class Kind(val key: String, val title: String, val one: String, val search: String, val icon: String) {
        FUEL("fuel", "Petrol pumps", "petrol pump", "petrol pump", "fuel"),
        HOSPITAL("hospital", "Hospitals", "hospital", "hospital", "hospital");
        companion object { fun of(key: String) = entries.firstOrNull { it.key == key } }
    }

    data class Spot(
        val name: String, val detail: String, val lat: Double, val lng: Double, val meters: Double,
        val phone: String? = null, val emergency: Boolean = false, val open24: Boolean = false,
    )

    // Two public Overpass servers; either can be busy (429/504) or slow, so each gets a short leash.
    private val SERVERS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter")
    private const val UA = "Waylink/1.0 (Android; github.com/krishnaar21/Waylink)"

    fun query(kind: Kind, lat: Double, lng: Double, radius: Int): String {
        val at = "%.3f,%.3f".format(Locale.US, lat, lng)
        val tags = when (kind) {
            Kind.FUEL -> listOf("""nwr["amenity"="fuel"](around:$radius,$at);""")
            Kind.HOSPITAL -> listOf("""nwr["amenity"="hospital"](around:$radius,$at);""", """nwr["amenity"="clinic"]["emergency"="yes"](around:$radius,$at);""")
        }
        return "[out:json][timeout:10];(" + tags.joinToString("") + ");out center tags 60;"
    }

    /** The last answer per kind for ten minutes, so opening the list again is instant and the servers are spared. */
    private val cache = HashMap<String, Pair<Long, List<Spot>>>()

    /**
     * Nearest first, at most 25. Overpass first; when both its servers fail, OpenStreetMap's own search
     * (Nominatim) within the same box. Throws only when nothing answers, so the screen can offer a retry.
     */
    suspend fun find(kind: Kind, lat: Double, lng: Double, radius: Int = 5000): List<Spot> = withContext(Dispatchers.IO) {
        val ck = "${kind.key}@${"%.2f,%.2f".format(Locale.US, lat, lng)}/$radius"
        synchronized(cache) { cache[ck]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.let { return@withContext it.second } }
        val body = "data=" + URLEncoder.encode(query(kind, lat, lng, radius), "UTF-8")
        var last: Exception? = null
        var found: List<Spot>? = null
        for (server in SERVERS) {
            try {
                found = parse(kind, http(server, body), lat, lng)
                break
            } catch (e: Exception) { last = e; android.util.Log.w("Nearby", "$server: $e") }
        }
        if (found == null) found = try { nominatim(kind, lat, lng, radius) } catch (e: Exception) { android.util.Log.w("Nearby", "nominatim: $e"); null }
        val out = found ?: throw (last ?: Exception("No answer"))
        synchronized(cache) { cache[ck] = System.currentTimeMillis() to out }
        out
    }

    private fun http(url: String, post: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 7000; c.readTimeout = 14000
            c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept", "application/json")
            if (post != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(post.toByteArray()) }
            }
            val code = c.responseCode
            if (code != 200) throw Exception("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** OpenStreetMap search inside a box around the phone ("fuel", "hospital" are its own category words). */
    private fun nominatim(kind: Kind, lat: Double, lng: Double, radius: Int): List<Spot> {
        val dLat = radius / 111_000.0
        val dLng = radius / (111_000.0 * Math.cos(Math.toRadians(lat)).coerceAtLeast(0.2))
        val box = "%.4f,%.4f,%.4f,%.4f".format(Locale.US, lng - dLng, lat + dLat, lng + dLng, lat - dLat)
        val word = if (kind == Kind.FUEL) "fuel" else "hospital"
        val json = http("https://nominatim.openstreetmap.org/search?format=jsonv2&extratags=1&namedetails=1&accept-language=en&limit=30&bounded=1&viewbox=$box&q=$word")
        return parseNominatim(kind, json, lat, lng)
    }

    fun parseNominatim(kind: Kind, json: String, lat: Double, lng: Double): List<Spot> {
        val arr = org.json.JSONArray(json)
        val els = org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val want = if (kind == Kind.FUEL) listOf("fuel") else listOf("hospital", "clinic")
            if (o.optString("category") != "amenity" || o.optString("type") !in want) continue
            val tags = (o.optJSONObject("extratags") ?: JSONObject())
            val n = o.optJSONObject("namedetails")
            val name = n?.optString("name:en")?.ifBlank { null } ?: n?.optString("name")?.ifBlank { null } ?: o.optString("name")
            if (name.isNotBlank()) tags.put("name", name)
            els.put(JSONObject().put("lat", o.optString("lat").toDoubleOrNull() ?: continue).put("lon", o.optString("lon").toDoubleOrNull() ?: continue).put("tags", tags))
        }
        return parse(kind, JSONObject().put("elements", els).toString(), lat, lng)
    }

    fun parse(kind: Kind, json: String, lat: Double, lng: Double): List<Spot> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Spot>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val c = e.optJSONObject("center")
            val la = c?.optDouble("lat") ?: e.optDouble("lat"); val ln = c?.optDouble("lon") ?: e.optDouble("lon")
            if (la.isNaN() || ln.isNaN()) continue
            val t = e.optJSONObject("tags") ?: JSONObject()
            fun s(k: String) = t.optString(k).trim().takeIf { it.isNotEmpty() }
            val brand = s("brand") ?: s("operator")
            val name = s("name:en") ?: s("name") ?: brand ?: (if (kind == Kind.FUEL) "Petrol pump" else "Hospital")
            val hours = s("opening_hours")
            val open24 = hours == "24/7"
            val emergency = s("emergency") == "yes"
            val area = s("addr:suburb") ?: s("addr:street") ?: s("addr:city")
            val detail = listOfNotNull(
                brand?.takeIf { !name.contains(it, ignoreCase = true) },
                if (open24) "Open 24 hours" else null,
                if (kind == Kind.HOSPITAL && emergency) "Emergency" else null,
                area,
            ).joinToString(" · ")
            val phone = (s("phone") ?: s("contact:phone"))?.split(';')?.first()?.trim()?.filter { it.isDigit() || it == '+' }?.takeIf { it.length >= 6 }
            out += Spot(name.take(60), detail.take(90), la, ln, haversine(listOf(lat, lng), listOf(la, ln)), phone, emergency, open24)
        }
        // The same place mapped twice (a building and a point) shows once.
        return out.sortedBy { it.meters }.distinctBy { it.name.lowercase() + "@" + Math.round(it.lat * 500) + "," + Math.round(it.lng * 500) }.take(25)
    }
}
