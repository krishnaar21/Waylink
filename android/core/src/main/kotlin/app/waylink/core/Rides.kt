package app.waylink.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

// ---------- Times (dur, clock, tShort in views.js) ----------

/** "24 min" or "1 h 21". */
fun dur(sec: Double): String {
    val s = Math.round(sec)
    val h = s / 3600
    val m = Math.round((s % 3600) / 60.0)
    return if (h > 0) "$h h ${m.toString().padStart(2, '0')}" else "$m min"
}

/** A running clock: "4:07" or "1:02:09". */
fun clock(sec: Double): String {
    val s = max(0.0, Math.floor(sec)).toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return (if (h > 0) "$h:" + m.toString().padStart(2, '0') else "$m") + ":" + r.toString().padStart(2, '0')
}

/** "7:02 pm" */
fun tShort(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(ts).atZone(zone)
    return "${(t.hour + 11) % 12 + 1}:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "am" else "pm"}"
}

// ---------- Saving a ride ----------

/** Adds a recorded ride: numbered, newest first, and its distance goes on the odometer and both trips. */
fun Vehicle.addRide(start: Long, end: Long, km: Double, top: Double, pts: List<List<Double>>, zone: ZoneId = ZoneId.systemDefault()): Vehicle {
    val n = (if (rideNo > 0) rideNo else trips.size) + 1
    val sec = max(1.0, (end - start) / 1000.0)
    val trip = Trip(
        n = n, start = start, end = end, km = (km * 100).roundToLong() / 100.0, sec = sec, top = top,
        avg = km / (sec / 3600), title = tripTitle(start, zone), pts = pts,
    )
    return copy(rideNo = n, trips = listOf(trip) + trips).addDistance(km)
}

/** The ride as GPX 1.1, for other map apps. */
fun toGPX(t: Trip): String {
    fun esc(x: String) = x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val pts = t.pts.filter { it.size >= 2 }.joinToString("\n") { p ->
        val time = if (p.size > 2 && p[2] > 0) "<time>${Instant.ofEpochMilli(p[2].toLong())}</time>" else ""
        "      <trkpt lat=\"${"%.6f".format(java.util.Locale.ROOT, p[0])}\" lon=\"${"%.6f".format(java.util.Locale.ROOT, p[1])}\">$time</trkpt>"
    }
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Waylink\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n" +
        "  <trk><name>${esc(t.title)}</name>\n    <trkseg>\n$pts\n    </trkseg>\n  </trk>\n</gpx>\n"
}

// ---------- The ride charts (ridePages in views.js) ----------

/** One day on the calendar. [band] is 0..4 by distance; [future] days are drawn empty. */
data class Day(val date: LocalDate, val km: Double, val rides: Int, val band: Int, val future: Boolean, val today: Boolean)
data class WeekBar(val start: LocalDate, val km: Double)
data class Weekday(val dow: DayOfWeek, val km: Double, val rides: Int)

/**
 * Twelve weeks of riding, Sunday first, ending this week. When there are no rides in that window,
 * [demo] is true and the numbers come from a fixed example pattern, drawn faintly.
 */
data class RideCharts(
    val weeks: List<List<Day>>,
    /** Month labels: week column and short month name. */
    val months: List<Pair<Int, String>>,
    val bars: List<WeekBar>,
    /** Axis top for the weekly bars: 20, 50 or the next hundred. */
    val nice: Int,
    /** Monday to Sunday. */
    val weekdays: List<Weekday>,
    val demo: Boolean,
)

private val MON3 = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
fun monShort(m: Int) = MON3[m - 1]

private fun band(km: Double) = when { km <= 0 -> 0; km < 15 -> 1; km < 40 -> 2; km < 80 -> 3; else -> 4 }

private data class Ride(val day: LocalDate, val dow: DayOfWeek, val km: Double)

private fun demoRides(start: LocalDate, today: LocalDate): List<Ride> {
    var seed = 7L
    fun rnd(): Double { seed = (seed * 9301 + 49297) % 233280; return seed / 233280.0 }
    val out = mutableListOf<Ride>()
    var d = start
    while (!d.isAfter(today)) {
        val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        if (rnd() < (if (weekend) 0.5 else 0.22)) out += Ride(d, d.dayOfWeek, Math.round(6 + rnd() * (if (d.dayOfWeek == DayOfWeek.SUNDAY) 70 else 28)).toDouble())
        d = d.plusDays(1)
    }
    return out
}

fun rideCharts(trips: List<Trip>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): RideCharts {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val sunIdx = today.dayOfWeek.value % 7 // Sunday = 0
    val start = today.minusDays((sunIdx + 7 * 11).toLong())
    val endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val real = trips.filter { it.start in 1 until endMs }.map {
        val d = Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()
        Ride(d, d.dayOfWeek, if (it.km.isFinite() && it.km > 0) it.km else 0.0)
    }
    val demo = real.none { !it.day.isBefore(start) }
    val rides = if (demo) demoRides(start, today) else real
    val kmBy = HashMap<LocalDate, Double>()
    val nBy = HashMap<LocalDate, Int>()
    for (r in rides) { kmBy[r.day] = (kmBy[r.day] ?: 0.0) + r.km; nBy[r.day] = (nBy[r.day] ?: 0) + 1 }

    val weeks = mutableListOf<List<Day>>()
    val months = mutableListOf<Pair<Int, String>>()
    var lastM = -1
    for (w in 0 until 12) {
        val col = mutableListOf<Day>()
        for (d in 0 until 7) {
            val k = start.plusDays((w * 7 + d).toLong())
            if (k.monthValue != lastM && ((w == 0 && d == 0 && k.dayOfMonth <= 18) || k.dayOfMonth == 1)) {
                months += w to MON3[k.monthValue - 1]; lastM = k.monthValue
            }
            val km = kmBy[k] ?: 0.0
            col += Day(k, km, nBy[k] ?: 0, band(km), k.isAfter(today), k == today)
        }
        weeks += col
    }
    val bars = (0 until 12).map { w -> val s0 = start.plusDays(w * 7L); WeekBar(s0, (0 until 7).sumOf { kmBy[s0.plusDays(it.toLong())] ?: 0.0 }) }
    val wMax = max(1.0, bars.maxOf { it.km })
    val nice = when { wMax <= 20 -> 20; wMax <= 50 -> 50; else -> (ceil(wMax / 100) * 100).toInt() }
    val wd = DayOfWeek.entries.map { dow -> rides.filter { it.dow == dow }.let { Weekday(dow, it.sumOf { r -> r.km }, it.size) } }
    return RideCharts(weeks, months, bars, nice, wd, demo)
}
