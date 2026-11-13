package app.waylink.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Sample data, clearly labelled in the app and erasable from Settings. Same garage as the web app's
 * seedSample(): "Daily" (Pulsar NS200) and "Tourer" (Dominar 400), riding around Bengaluru.
 */
object Sample {
    fun seed(s: AppState, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): AppState {
        fun d(n: Int) = now - n * DAY_MS
        val price = 102.9
        val blr = 12.9716 to 77.5946

        val profile = s.profile.copy(
            name = s.profile.name.ifBlank { "Arjun" }, phone = "+91 98450 12345", email = "arjun.rao@example.in", city = "Bengaluru", country = "IN",
            licenceNo = "KA05 20190012345",
            licenceExpiry = LocalDate.now(zone).withMonth(3).withDayOfMonth(14).plusYears(9).atStartOfDay(zone).toInstant().toEpochMilli(),
            blood = "B+", medical = "No known allergies", iceName = "Amma", icePhone = "+91 98860 55443", since = d(410),
        )

        fun fuel(rows: List<List<Any>>) = rows.map { (dd, odo, l, full) ->
            val litres = (l as Number).toDouble()
            FuelEntry(ts = now + (dd as Int) * DAY_MS, odo = (odo as Number).toDouble(), litres = litres, cost = (litres * price).roundToLong().toDouble(), full = full as Boolean)
        }

        // Today at 19:02, relative to now, so rides land on sensible hours.
        val t0 = LocalDate.now(zone).atTime(19, 2).atZone(zone).toInstant().toEpochMilli() - now
        fun mk(n: Int, off: Long, km: Double, min: Int, top: Double, seed: Int, title: String? = null, start: Pair<Double, Double> = blr): Trip {
            val st = now + off
            return Trip(
                n = n, start = st, end = st + min * 60_000L, km = km, sec = min * 60.0, top = top, avg = km / (min / 60.0),
                title = title ?: tripTitle(st, zone), pts = squiggle(start, km, seed, st, min),
            )
        }
        val m = 60_000L
        val day = DAY_MS

        val a = newVehicle(model = "ns200", nickname = "Daily", plate = "KA 05 MX 2481", odo = 12261.0, colour = "#B3261E", now = now).copy(
            addedAt = d(400),
            tripA = TripMeter(84.6, d(5)), tripB = TripMeter(462.3, d(24)),
            fuel = fuel(listOf(
                listOf(-63, 10474, 11.2, true), listOf(-56, 10795, 11.18, true), listOf(-49, 11113, 11.52, true), listOf(-35, 11441, 11.12, true),
                listOf(-28, 11552, 3.0, false), listOf(-21, 11764, 7.98, true), listOf(-11, 11995, 7.8, true), listOf(-5, 12176, 6.07, true),
            )),
            fuelPrice = price,
            service = listOf(
                ServiceItem(kind = "chain", name = "Chain clean & lube", icon = "chain", everyKm = 500.0, everyDays = 0, lastKm = 11655.0, lastDate = d(19)),
                ServiceItem(kind = "brakes", name = "Brake pads check", icon = "brake", everyKm = 5000.0, everyDays = 182, lastKm = 8402.0, lastDate = d(166)),
                ServiceItem(kind = "oil", name = "Engine oil", icon = "drop", everyKm = 3000.0, everyDays = 182, lastKm = 9874.0, lastDate = d(102)),
                ServiceItem(kind = "tyres", name = "Tyre pressure", icon = "gauge", everyKm = 0.0, everyDays = 14, lastKm = 12010.0, lastDate = d(4)),
                ServiceItem(kind = "service", name = "Periodic service", icon = "wrench", everyKm = 5000.0, everyDays = 365, lastKm = 10020.0, lastDate = d(120), milestone = true, targetKm = 15000.0),
                ServiceItem(kind = "air", name = "Air filter", icon = "wind", everyKm = 10000.0, everyDays = 365, lastKm = 10020.0, lastDate = d(120)),
            ),
            records = listOf(
                ServiceRecord(date = d(120), odo = 10020.0, title = "3rd paid service", workshop = "Sri Ganesh Bajaj, Jayanagar", cost = 2850.0, notes = "Oil and oil filter, air filter, chain adjusted"),
                ServiceRecord(date = d(300), odo = 5040.0, title = "2nd service", workshop = "Sri Ganesh Bajaj, Jayanagar", cost = 1980.0, notes = "Oil and filter, brake fluid top-up"),
            ),
            checklist = listOf(
                ChecklistItem(text = "Horn sounds weak in the rain"),
                ChecklistItem(text = "Ask for the chain-sprocket kit price"),
                ChecklistItem(text = "Replace the left mirror boot", done = true),
            ),
            trips = listOf(
                mk(16, t0 - 1 * day, 10.0, 24, 62.0, 1, "Office to home"), mk(15, t0 - 2 * day - 38 * m, 10.0, 26, 61.0, 1, "Office to home"),
                mk(14, t0 - 2 * day - 610 * m, 9.5, 31, 58.0, 2, "Home to office"), mk(13, t0 - 3 * day - 25 * m, 31.4, 52, 88.0, 3, "Kanakapura Road loop"),
                mk(12, t0 - 4 * day - 640 * m, 9.7, 29, 57.0, 2, "Home to office"), mk(11, t0 - 5 * day, 10.2, 25, 60.0, 1, "Office to home"),
                mk(10, t0 - 7 * day - 300 * m, 121.6, 168, 104.0, 4, "Nandi Hills sunrise"), mk(9, t0 - 10 * day, 9.9, 27, 59.0, 1, "Office to home"),
                mk(8, t0 - 12 * day - 610 * m, 9.4, 30, 55.0, 2, "Home to office"), mk(7, t0 - 14 * day, 48.0, 81, 96.0, 5, "Bannerghatta Road run"),
                mk(6, t0 - 17 * day, 10.1, 25, 61.0, 1, "Office to home"), mk(5, t0 - 19 * day - 610 * m, 9.6, 30, 57.0, 2, "Home to office"),
                mk(4, t0 - 21 * day, 22.3, 44, 79.0, 3), mk(3, t0 - 23 * day, 10.0, 24, 63.0, 1, "Office to home"),
                mk(2, t0 - 26 * day, 41.7, 70, 92.0, 5, "Hosur Road errands"), mk(1, t0 - 33 * day, 18.2, 40, 74.0, 3),
            ).sortedByDescending { it.start },
            rideNo = 16,
        )

        val b = newVehicle(model = "dominar400", nickname = "Tourer", plate = "KA 03 NB 7719", odo = 8420.0, colour = "#2F4F3E", now = now).let { v ->
            v.copy(
                addedAt = d(220),
                tripA = TripMeter(268.4, d(12)), tripB = TripMeter(1140.2, d(60)),
                fuel = fuel(listOf(listOf(-40, 7480, 12.6, true), listOf(-26, 7840, 13.0, true), listOf(-12, 8195, 12.4, true), listOf(-4, 8395, 7.1, true))),
                fuelPrice = price,
                service = defaultServiceItems(8420.0, now).map {
                    it.copy(
                        lastKm = when (it.kind) { "chain" -> 8120.0; "tyres" -> 8395.0; else -> 6050.0 },
                        lastDate = when (it.kind) { "tyres" -> d(9); "chain" -> d(10); else -> d(88) },
                        targetKm = if (it.milestone) 10000.0 else it.targetKm,
                    )
                },
                records = listOf(ServiceRecord(date = d(88), odo = 6050.0, title = "2nd service", workshop = "Bajaj Probiking, Indiranagar", cost = 3420.0, notes = "Oil, coolant check, chain set")),
                trips = listOf(
                    mk(3, t0 - 12 * day - 700 * m, 268.4, 330, 118.0, 6, "Coorg weekend, day 1", 12.95 to 77.4),
                    mk(2, t0 - 11 * day - 200 * m, 271.0, 345, 121.0, 7, "Coorg weekend, day 2", 12.4 to 75.8),
                    mk(1, t0 - 30 * day - 500 * m, 64.2, 96, 102.0, 4, "Nandi Hills with the gang"),
                ),
                rideNo = 3,
            )
        }

        return s.copy(
            sample = true, onboarded = true, profile = profile, vehicles = listOf(a, b), active = a.id,
            alerts = Alerts(
                missed = listOf(NamedEvent("Rohan", now - 3_600_000), NamedEvent("Rohan", now - 3_000_000)),
                sent = 6,
                log = listOf(
                    LogEvent("missed", "Rohan", now - 3_000_000), LogEvent("missed", "Rohan", now - 3_600_000),
                    LogEvent("wa", "Priya", now - 2 * 3_600_000), LogEvent("call", "Amma", now - DAY_MS),
                ),
            ),
        )
    }

    /** A wandering GPS track, the same pseudo-random walk as the web app. */
    private fun squiggle(start: Pair<Double, Double>, km: Double, seed: Int, startTs: Long, min: Int): List<List<Double>> {
        var r = seed * 9301L + 49297L
        fun rnd(): Double { r = (r * 9301 + 49297) % 233280; return r / 233280.0 }
        val n = 40
        val pts = mutableListOf<List<Double>>()
        var a = rnd() * PI * 2
        var lat = start.first
        var lng = start.second
        val step = km / n / 111
        for (i in 0..n) {
            pts += listOf(round6(lat), round6(lng), (startTs + (i.toDouble() / n) * min * 60_000).roundToLong().toDouble())
            a += (rnd() - 0.5) * 0.9
            lat += cos(a) * step
            lng += sin(a) * step
        }
        return pts
    }

    private fun round6(x: Double) = (x * 1e6).roundToLong() / 1e6
}

/**
 * Developer preview states (web store.js scenario): "empty" is a brand-new rider; "edge" has long names,
 * huge and tiny numbers and everything due at once. Built fresh each time and never saved.
 */
fun scenarioState(kind: String, base: AppState, now: Long = System.currentTimeMillis()): AppState {
    val day = 86_400_000L
    val f = freshState(now).copy(onboarded = true, settings = base.settings)
    if (kind == "empty") return f.copy(profile = f.profile.copy(name = ""))
    val profile = f.profile.copy(
        name = "Venkataraghavan Subramaniam", city = "Thiruvananthapuram", blood = "AB−",
        medical = "Allergic to penicillin, sulfa drugs and bee stings. Carries an EpiPen in the left jacket pocket.",
        iceName = "Lakshmi Narayanan (Amma)", icePhone = "+91 98450 00000", licenceNo = "KA05 20190012345", licenceExpiry = now - 3 * day,
    )
    val v0 = newVehicle(model = "dominar400", nickname = "Long-distance tourer", plate = "22 BH 1234 AA", odo = 199999.9, colour = "#E9E6DF", now = now)
    val v = v0.copy(
        tripA = TripMeter(9999.9, now - 400 * day), tripB = TripMeter(0.0, now),
        fuel = listOf(
            FuelEntry(ts = now - 40 * day, odo = 199400.0, litres = 13.0, cost = 1338.0, full = true),
            FuelEntry(ts = now - 30 * day, odo = 199402.0, litres = 0.4, cost = 41.0, full = false),
            FuelEntry(ts = now - 20 * day, odo = 199760.0, litres = 12.6, cost = 99999.0, full = true),
            FuelEntry(ts = now - 2 * day, odo = 199995.0, litres = 1.1, cost = 113.0, full = false),
        ),
        service = defaultServiceItems(150000.0, now - 900 * day) + ServiceItem(kind = "custom", name = "Steering bearings and swingarm re-grease", icon = "wrench", everyKm = 1.0, everyDays = 1, lastKm = 0.0, lastDate = now - 999 * day),
        records = listOf(ServiceRecord(date = now - 10 * day, odo = 199900.0, title = "Engine rebuild after a long Western Ghats road trip", workshop = "Sri Venkateshwara Motorcycle Works", cost = 1234567.0)),
        checklist = List(6) { i -> ChecklistItem(text = if (i == 0) "A very long mechanic note that keeps going to check how the list wraps on small screens without breaking the layout" else "Item ${i + 1}", done = i % 2 == 0) },
        trips = listOf(
            Trip(n = 9999, start = now - 3_600_000, end = now - 3_500_000, km = 0.1, sec = 60.0, top = 3.0, avg = 6.0, title = "A", pts = listOf(listOf(12.97, 77.59, (now - 3_600_000).toDouble()))),
            Trip(n = 9998, start = now - 5 * day, end = now - 4 * day, km = 1240.6, sec = 86000.0, top = 162.0, avg = 52.0, title = "Bengaluru to Leh in one go, a ride with a title long enough to wrap twice"),
        ),
        rideNo = 9999, parking = Parking(34.1526, 77.5771, now - 40 * day),
    )
    return f.copy(
        profile = profile, vehicles = listOf(v), active = v.id,
        alerts = Alerts(
            missed = List(128) { i -> NamedEvent(if (i == 127) "Dr. Venkataraghavan Subramaniam" else "Rohan", now - i * 60_000L) },
            wa = List(12) { NamedEvent("Priya 🌸", now) }, sent = 99999,
        ),
    )
}
