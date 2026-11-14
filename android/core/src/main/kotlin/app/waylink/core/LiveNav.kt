package app.waylink.core

import kotlinx.serialization.Serializable

/**
 * What the phone's platform can do. Android fills every switch; the iOS build (later) turns off what iOS doesn't
 * allow, and every screen asks here instead of assuming, so the same UI hides or swaps those parts.
 */
data class Caps(
    /** Read another navigation app's turn notifications (Android notification access). iOS: never. */
    val readNavApps: Boolean = true,
    /** Talk to the bike over Bluetooth LE. */
    val bluetooth: Boolean = true,
    /** Change the launcher icon from inside the app. */
    val appIcons: Boolean = true,
) {
    companion object {
        val ANDROID = Caps()
        /** What an iPhone build can offer, for the day there is one. */
        val IOS = Caps(readNavApps = false, appIcons = true)
    }
}

object Platform {
    /** Set once by the app at start-up. */
    var caps: Caps = Caps.ANDROID
}

/**
 * A navigation app Waylink can follow. "waylink" is the built-in one (rehearse, test a turn); the others are read
 * from the turn notification each app shows while it navigates.
 */
data class NavSource(val key: String, val name: String, val packages: List<String>, val note: String, val readsNotifications: Boolean = true) {
    companion object {
        val ALL = listOf(
            NavSource("gmaps", "Google Maps", listOf("com.google.android.apps.maps"), "Turn alerts while Maps navigates"),
            NavSource("waze", "Waze", listOf("com.waze"), "Turn alerts while Waze navigates"),
            NavSource("osmand", "OsmAnd", listOf("net.osmand", "net.osmand.plus"), "Offline maps, turn alerts while it guides"),
            NavSource("organic", "Organic Maps", listOf("app.organicmaps"), "Offline maps, turn alerts while it guides"),
            NavSource("waylink", "Waylink only", emptyList(), "Rehearse and test turns, no other app", readsNotifications = false),
        )
        fun of(key: String?): NavSource = ALL.firstOrNull { it.key == key } ?: ALL[0]
        /** The sources this phone can use. */
        fun available(caps: Caps = Platform.caps): List<NavSource> = ALL.filter { caps.readNavApps || !it.readsNotifications }
        fun forPackage(pkg: String): NavSource? = ALL.firstOrNull { pkg in it.packages }
    }
}

/** One turn read from a navigation app's notification. */
data class LiveTurn(
    val maneuver: String,
    val stepMeters: Double,
    val street: String,
    val say: String,
    val exit: Int = 0,
    val totalMeters: Double? = null,
    /** Minutes to go, when the app shows it. */
    val etaMin: Int? = null,
    /** Clock time of arrival as hours and minutes (24 h), when the app shows it. */
    val etaClock: Pair<Int, Int>? = null,
)

/**
 * Reads a turn out of a navigation notification's title, text and sub-text. Apps word these differently and move
 * them between fields, so every field is searched: the first distance is the distance to the turn, a second one
 * is what's left; the words pick the arrow, and "onto/on/toward" gives the street. English wording only for now.
 */
object NavParse {
    private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\b(\d{1,2}):(\d{2})\s*([ap]\.?m\.?)?""", RegexOption.IGNORE_CASE)
    private val MINS = Regex("""\b(?:(\d+)\s*h(?:r|ours?)?\s*)?(\d+)\s*min""", RegexOption.IGNORE_CASE)
    private val EXIT = Regex("""\b(\d)(?:st|nd|rd|th)\s+exit|exit\s+(\d)\b""", RegexOption.IGNORE_CASE)
    private val ORD = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7)

    fun meters(n: Double, unit: String): Double = when (unit.lowercase()) {
        "km" -> n * 1000; "mi" -> n * 1609.344; "ft" -> n * 0.3048; "yd" -> n * 0.9144; else -> n
    }

    fun parse(title: String?, text: String?, sub: String? = null, big: String? = null): LiveTurn? {
        val fields = listOfNotNull(title, text, big, sub).map { it.trim() }.filter { it.isNotEmpty() }
        if (fields.isEmpty()) return null
        val all = fields.joinToString(" · ")
        val dists = fields.flatMap { f -> DIST.findAll(f).map { meters(it.groupValues[1].replace(',', '.').toDouble(), it.groupValues[2]) }.toList() }
        // The instruction is the field with turn words in it, else the longest that isn't just numbers.
        val say = fields.firstOrNull { WORDS.containsMatchIn(it) } ?: fields.filter { DIST.replace(it, "").trim().length > 3 }.maxByOrNull { it.length } ?: return null
        val step = dists.firstOrNull() ?: return null
        val total = dists.drop(1).maxOrNull()
        val (man, exit) = maneuverOf(say)
        val street = streetOf(say)
        val eta = sub?.let { CLOCK.find(it) }?.let { m ->
            var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            val ap = m.groupValues[3].lowercase().replace(".", "")
            if (ap == "pm" && h < 12) h += 12 else if (ap == "am" && h == 12) h = 0
            if (h in 0..23 && min in 0..59) h to min else null
        }
        val mins = (sub ?: all).let { MINS.find(it) }?.let { (it.groupValues[1].toIntOrNull() ?: 0) * 60 + it.groupValues[2].toInt() }
        return LiveTurn(man, step, street, cleanSay(say), exit, total, mins, eta)
    }

    private val WORDS = Regex("""\b(turn|left|right|straight|continue|head|keep|merge|fork|ramp|exit|roundabout|rotary|u-?turn|arriv|destination|ferry)""", RegexOption.IGNORE_CASE)

    fun maneuverOf(say: String): Pair<String, Int> {
        val s = say.lowercase()
        val left = Regex("""\bleft\b""").containsMatchIn(s); val right = Regex("""\bright\b""").containsMatchIn(s)
        val side = if (left && !right) "LEFT" else if (right) "RIGHT" else null
        return when {
            "arriv" in s || "destination" in s -> "DESTINATION_REACHED" to 0
            "ferry" in s -> "FERRY" to 0
            Regex("""u-?turn""").containsMatchIn(s) -> (if (side == "LEFT") "U_TURN_LEFT" else "U_TURN_RIGHT") to 0
            "roundabout" in s || "rotary" in s || "traffic circle" in s -> {
                val e = EXIT.find(s)?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
                    ?: ORD.entries.firstOrNull { (w, _) -> Regex("""\b$w exit""").containsMatchIn(s) }?.value ?: 1
                // Riding on the left (India, UK) roundabouts go clockwise: "left" ones.
                (if (side == "RIGHT") "ROUNDABOUT_RIGHT" else "ROUNDABOUT_LEFT") to e.coerceIn(1, 7)
            }
            "merge" in s -> "MERGE" to 0
            "fork" in s && side != null -> "FORK_$side" to 0
            ("ramp" in s || Regex("""\b(take|use) the exit|\bexit\b""").containsMatchIn(s)) && side != null -> "RAMP_$side" to 0
            "keep" in s && side != null -> "KEEP_$side" to 0
            "slight" in s && side != null -> "TURN_SLIGHT_$side" to 0
            "sharp" in s && side != null -> "TURN_SHARP_$side" to 0
            side != null -> "TURN_$side" to 0
            else -> "STRAIGHT" to 0
        }
    }

    fun streetOf(say: String): String {
        val m = Regex("""\b(?:onto|on to|on|toward|towards|into)\s+(.+)$""", RegexOption.IGNORE_CASE).find(say) ?: return ""
        return m.groupValues[1].substringBefore(" · ").substringBefore(" then ").trim().trimEnd('.', ',').take(31)
    }

    private fun cleanSay(s: String) = DIST.replace(s, "").replace(Regex("""^\W+|\W+$"""), "").replace(Regex("""\s{2,}"""), " ").trim().take(80)
}

/** A place the rider goes often: home, work, or anything else, with where it is. */
@Serializable
data class Place(
    val id: String = uid(),
    val name: String,
    /** home, work or pin */
    val kind: String = "pin",
    val lat: Double? = null,
    val lng: Double? = null,
    /** What to search for when there is no fix: an address or place name. */
    val address: String = "",
    val added: Long = 0,
)

/** The speed alert: off at 0, else the limit in km/h, between 30 and 200. */
object SpeedAlert {
    val PRESETS = listOf(0, 60, 80, 100, 120)
    fun clamp(k: Int) = if (k <= 0) 0 else k.coerceIn(30, 200)
    /** Over the limit by more than a little, so GPS jitter at the limit stays quiet. */
    fun over(speed: Int, limit: Int) = limit > 0 && speed >= limit + 2
    /** Back under, with room, before it can sound again. */
    fun under(speed: Int, limit: Int) = speed <= limit - 3
}
