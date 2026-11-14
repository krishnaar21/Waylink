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

    private val SERVERS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")

    fun query(kind: Kind, lat: Double, lng: Double, radius: Int): String {
        val at = "%.3f,%.3f".format(Locale.US, lat, lng)
        val tags = when (kind) {
            Kind.FUEL -> listOf("""nwr["amenity"="fuel"](around:$radius,$at);""")
            Kind.HOSPITAL -> listOf("""nwr["amenity"="hospital"](around:$radius,$at);""", """nwr["amenity"="clinic"]["emergency"="yes"](around:$radius,$at);""")
        }
        return "[out:json][timeout:15];(" + tags.joinToString("") + ");out center tags 60;"
    }

    /** Nearest first, at most 25. Throws when no server answers, so the screen can offer to retry. */
    suspend fun find(kind: Kind, lat: Double, lng: Double, radius: Int = 5000): List<Spot> = withContext(Dispatchers.IO) {
        val body = "data=" + URLEncoder.encode(query(kind, lat, lng, radius), "UTF-8")
        var last: Exception? = null
        for (server in SERVERS) {
            try {
                val c = URL(server).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.connectTimeout = 8000; c.readTimeout = 20000
                c.setRequestProperty("User-Agent", "Waylink-Android")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                try {
                    c.outputStream.use { it.write(body.toByteArray()) }
                    if (c.responseCode != 200) { last = Exception("HTTP ${c.responseCode}"); continue }
                    return@withContext parse(kind, c.inputStream.bufferedReader().use { it.readText() }, lat, lng)
                } finally { c.disconnect() }
            } catch (e: Exception) { last = e }
        }
        throw last ?: Exception("No answer")
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
