package app.waylink.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/* The maths behind fuel, the logbook and trips. A line-for-line port of web-v0/js/store.js. */

const val DAY_MS = 86_400_000L

// ---------- Vehicles ----------

fun vehicleTitle(s: AppState, v: Vehicle = s.vehicle): String =
    v.nickname.trim().ifEmpty { modelName(s, Catalog.model(v.model)) }

fun modelName(s: AppState, m: BikeModel): String =
    if (s.country.rouser) m.name.replace("Pulsar", "Rouser") else m.name

fun defaultServiceItems(odo: Double = 0.0, now: Long = System.currentTimeMillis()): List<ServiceItem> = listOf(
    ServiceItem(kind = "chain", name = "Chain clean & lube", icon = "chain", everyKm = 500.0, everyDays = 0, lastKm = odo, lastDate = now),
    ServiceItem(
        kind = "service", name = "Periodic service", icon = "wrench", everyKm = 5000.0, everyDays = 365, lastKm = odo, lastDate = now,
        milestone = true,
        targetKm = when {
            odo < 750 -> 500.0
            odo < 5000 -> 5000.0
            odo < 10000 -> 10000.0
            else -> (floor(odo / 5000) + 1) * 5000
        },
    ),
    ServiceItem(kind = "oil", name = "Engine oil", icon = "drop", everyKm = 3000.0, everyDays = 182, lastKm = odo, lastDate = now),
    ServiceItem(kind = "brakes", name = "Brake pads check", icon = "brake", everyKm = 5000.0, everyDays = 182, lastKm = odo, lastDate = now),
    ServiceItem(kind = "tyres", name = "Tyre pressure", icon = "gauge", everyKm = 0.0, everyDays = 14, lastKm = odo, lastDate = now),
    ServiceItem(kind = "air", name = "Air filter", icon = "wind", everyKm = 10000.0, everyDays = 365, lastKm = odo, lastDate = now),
)

fun newVehicle(
    model: String = "ns200",
    nickname: String = "",
    plate: String = "",
    odo: Double = 0.0,
    colour: String = Catalog.COLOURS[0].first,
    now: Long = System.currentTimeMillis(),
): Vehicle {
    val o = max(0.0, odo)
    return Vehicle(
        model = model, nickname = nickname, plate = plate, colour = colour, addedAt = now, odo = o,
        tripA = TripMeter(0.0, now), tripB = TripMeter(0.0, now), service = defaultServiceItems(o, now),
    )
}

fun freshState(now: Long = System.currentTimeMillis()): AppState {
    val v = newVehicle(now = now)
    return AppState(
        profile = Profile(avatar = "a" + (1 + (now % 11)), since = now),
        vehicles = listOf(v), active = v.id,
    )
}

// ---------- Odometer ----------

private fun round1(x: Double) = (x * 10).roundToLong() / 10.0
private fun round2(x: Double) = (x * 100).roundToLong() / 100.0

fun Vehicle.addDistance(km: Double): Vehicle {
    if (!(km > 0)) return this
    return copy(
        odo = round1(odo + km),
        tripA = tripA.copy(km = round2(tripA.km + km)),
        tripB = tripB.copy(km = round2(tripB.km + km)),
    )
}

// ---------- Fuel (full tank to full tank) ----------

data class Tank(val ts: Long, val id: String, val dist: Double, val litres: Double, val kmpl: Double)

data class FuelStats(
    val tank: Double,
    val reserve: Double,
    val tanks: List<Tank>,
    /** km/L over the last three full tanks. */
    val avg: Double?,
    val lifetime: Double?,
    val lastTank: Double?,
    val litresLeft: Double?,
    val sinceFull: Double?,
    val range: Double?,
    /** 0..6, like the cluster's fuel gauge. */
    val bars: Int?,
    val costPerKm: Double?,
    val totalSpent: Double,
    val onReserve: Boolean,
)

fun fuelStats(v: Vehicle, now: Long = System.currentTimeMillis()): FuelStats {
    val m = Catalog.model(v.model)
    val tank = m.tank
    val list = v.fuel.sortedWith(compareBy<FuelEntry> { it.odo }.thenBy { it.ts })
    val tanks = mutableListOf<Tank>()
    var lastFullIdx = -1
    list.forEachIndexed { i, e ->
        if (!e.full) return@forEachIndexed
        if (lastFullIdx >= 0) {
            val prev = list[lastFullIdx]
            val dist = e.odo - prev.odo
            var litres = 0.0
            for (j in lastFullIdx + 1..i) litres += list[j].litres
            if (dist > 1 && litres > 0) {
                val kmpl = dist / litres
                if (kmpl > 3 && kmpl < 120) tanks += Tank(e.ts, e.id, dist, litres, kmpl)
            }
        }
        lastFullIdx = i
    }
    val last3 = tanks.takeLast(3)
    val avg = if (last3.isNotEmpty()) last3.sumOf { it.dist } / last3.sumOf { it.litres } else null
    val lifetime = if (tanks.isNotEmpty()) tanks.sumOf { it.dist } / tanks.sumOf { it.litres } else null
    val lastTank = tanks.lastOrNull()?.kmpl

    var litresLeft: Double? = null
    var sinceFull: Double? = null
    val lastFull = list.lastOrNull { it.full }
    if (lastFull != null) {
        sinceFull = max(0.0, v.odo - lastFull.odo)
        if (avg != null) {
            val topUps = list.filter { !it.full && it.odo > lastFull.odo }.sumOf { it.litres }
            litresLeft = min(tank, max(0.0, tank + topUps - sinceFull / avg))
        }
    }
    val range = if (litresLeft != null && avg != null) litresLeft * avg else null
    val bars = litresLeft?.let { if (it <= 0) 0 else max(1, floor((it / tank) * 6 + 1e-9).toInt()) }

    var costPerKm: Double? = null
    if (list.size > 1) {
        val span = list.last().odo - list.first().odo
        val spent = list.drop(1).sumOf { it.cost }
        if (span > 0) costPerKm = spent / span
    }
    return FuelStats(
        tank = tank, reserve = m.reserve, tanks = tanks, avg = avg, lifetime = lifetime, lastTank = lastTank,
        litresLeft = litresLeft, sinceFull = sinceFull, range = range, bars = bars, costPerKm = costPerKm,
        totalSpent = list.sumOf { it.cost }, onReserve = litresLeft != null && litresLeft <= m.reserve,
    )
}

// ---------- Logbook ----------

enum class Due { OK, SOON, OVERDUE }

data class ServiceStatus(
    val dueKm: Double?,
    val remKm: Double?,
    val dueDate: Long?,
    val remDays: Int?,
    val overdueKm: Double,
    val overdueDays: Int,
    val status: Due,
    /** 0..1, how much of the interval is used up. */
    val frac: Double,
    /** True when time, not distance, is the closer limit. */
    val byTime: Boolean,
)

fun serviceStatus(odo: Double, item: ServiceItem, now: Long = System.currentTimeMillis()): ServiceStatus {
    var dueKm: Double? = null
    var remKm: Double? = null
    var fracKm = 0.0
    if (item.milestone && item.targetKm != null) {
        dueKm = item.targetKm
        val span = if (dueKm <= 750) 500.0 else 5000.0
        remKm = dueKm - odo
        fracKm = 1 - remKm / span
    } else if (item.everyKm > 0) {
        dueKm = item.lastKm + item.everyKm
        remKm = dueKm - odo
        fracKm = (odo - item.lastKm) / item.everyKm
    }
    var dueDate: Long? = null
    var remDays: Int? = null
    var fracT = 0.0
    if (item.everyDays > 0 && !item.milestone) {
        dueDate = item.lastDate + item.everyDays * DAY_MS
        remDays = ceil((dueDate - now).toDouble() / DAY_MS).toInt()
        fracT = (now - item.lastDate).toDouble() / (item.everyDays * DAY_MS)
    }
    val overdueKm = if (remKm != null && remKm < 0) -remKm else 0.0
    val overdueDays = if (remDays != null && remDays < 0) -remDays else 0
    val overdue = overdueKm > 0 || overdueDays > 0
    val soonKm = remKm != null && item.everyKm > 0 && remKm <= max(50.0, if (item.milestone) 200.0 else 0.15 * item.everyKm)
    val soonDays = remDays != null && remDays <= min(21.0, max(2.0, item.everyDays * 0.15))
    val status = when {
        overdue -> Due.OVERDUE
        soonKm || soonDays -> Due.SOON
        else -> Due.OK
    }
    return ServiceStatus(
        dueKm, remKm, dueDate, remDays, overdueKm, overdueDays, status,
        frac = max(0.0, min(1.0, max(fracKm, fracT))), byTime = fracT > fracKm,
    )
}

fun ServiceItem.markDone(odo: Double, now: Long = System.currentTimeMillis()): ServiceItem {
    val next = if (milestone) {
        val t = targetKm ?: 0.0
        when {
            t <= 750 -> 5000.0
            t <= 5000 -> 10000.0
            else -> odo + 5000
        }
    } else targetKm
    return copy(lastKm = odo, lastDate = now, targetKm = next)
}

data class ServiceRow(val item: ServiceItem, val st: ServiceStatus)

/** Most urgent first: overdue, then due soon, then the most used-up. */
fun sortedService(v: Vehicle, now: Long = System.currentTimeMillis()): List<ServiceRow> =
    v.service.map { ServiceRow(it, serviceStatus(v.odo, it, now)) }
        .sortedWith(compareBy<ServiceRow> { it.st.status.rank }.thenByDescending { it.st.frac })

private val Due.rank get() = when (this) { Due.OVERDUE -> 0; Due.SOON -> 1; Due.OK -> 2 }

fun needsYou(v: Vehicle, now: Long = System.currentTimeMillis()): Int =
    v.service.count { serviceStatus(v.odo, it, now).status != Due.OK }

/** The Today card's one-word verdict on the bike's care. */
data class Verdict(val word: String, val line: String, val tone: Due?, val over: Int, val soon: Int, val ok: Int, val bar: List<Due>)

private val WORDS = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")
private fun word(n: Int) = WORDS.getOrElse(n) { n.toString() }

fun verdictOf(list: List<ServiceRow>): Verdict {
    val over = list.count { it.st.status == Due.OVERDUE }
    val soon = list.count { it.st.status == Due.SOON }
    val ok = list.size - over - soon
    val bar = list.map { it.st.status }
    return when {
        over > 0 -> Verdict("Needs care", if (over == 1) "One job is overdue." else "${word(over)} jobs are overdue.", Due.OVERDUE, over, soon, ok, bar)
        soon > 0 -> Verdict("Almost due", if (soon == 1) "One job is due soon. The rest look good." else "${word(soon)} jobs are due soon.", Due.SOON, over, soon, ok, bar)
        else -> Verdict("Road-ready", "Everything is in good shape.", null, over, soon, ok, bar)
    }
}

// ---------- Trips ----------

fun haversine(a: List<Double>, b: List<Double>): Double {
    val r = 6371e3
    val toR = Math.PI / 180
    val dLat = (b[0] - a[0]) * toR
    val dLng = (b[1] - a[1]) * toR
    val x = sin(dLat / 2).let { it * it } + cos(a[0] * toR) * cos(b[0] * toR) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(x))
}

fun tripTitle(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val h = Instant.ofEpochMilli(ts).atZone(zone).hour
    return when {
        h < 5 -> "Night ride"
        h < 12 -> "Morning ride"
        h < 17 -> "Afternoon ride"
        h < 21 -> "Evening ride"
        else -> "Night ride"
    }
}

data class PeriodStats(val km: Double, val rides: Int, val sec: Double, val top: Double)

fun periodStats(trips: List<Trip>, from: Long): PeriodStats {
    val t = trips.filter { it.start >= from }
    return PeriodStats(t.sumOf { it.km }, t.size, t.sumOf { it.sec }, t.maxOfOrNull { it.top } ?: 0.0)
}

/** Monday 00:00 of the week containing [now]. */
fun weekStart(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
    val d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return d.minusDays((d.dayOfWeek.value - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
}
