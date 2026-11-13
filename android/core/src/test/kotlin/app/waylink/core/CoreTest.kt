package app.waylink.core

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports of the web app's store tests (web-v0/tests/unit.test.mjs and edge.test.mjs), so both apps agree. */
class CoreTest {
    private val sample = Sample.seed(freshState())

    @Test fun `sample garage keeps two bikes apart`() {
        assertEquals(2, sample.vehicles.size)
        assertEquals("Daily", vehicleTitle(sample))
        assertEquals(12261.0, sample.vehicle.odo)
        val tourer = sample.copy(active = sample.vehicles[1].id)
        assertEquals("Dominar 400", modelName(tourer, tourer.model))
        assertEquals(8420.0, tourer.vehicle.odo)
        assertNotEquals(sample.vehicle.trips.size, tourer.vehicle.trips.size)
        val rode = tourer.updateVehicle { it.addDistance(10.0) }
        assertEquals(8430.0, rode.vehicles[1].odo)
        assertEquals(12261.0, rode.vehicles[0].odo)
    }

    @Test fun `fuel is worked out full tank to full tank`() {
        val f = fuelStats(sample.vehicle)
        assertEquals(6, f.tanks.size)
        assertTrue(abs(f.avg!! - 29.58) < 0.05, "avg ${f.avg}")
        assertTrue(abs(f.litresLeft!! - 9.13) < 0.05)
        assertTrue(f.range!! > 260 && f.range!! < 280)
        assertEquals(4, f.bars)
        assertEquals(false, f.onReserve)
        assertEquals(true, fuelStats(sample.vehicle.copy(odo = 12176.0 + 300)).onReserve)
    }

    @Test fun `a top-up rolls into the next full tank`() {
        val v = newVehicle().copy(fuel = listOf(
            FuelEntry("a", 1, 1000.0, 10.0, 1.0, true), FuelEntry("b", 2, 1150.0, 3.0, 1.0, false), FuelEntry("c", 3, 1300.0, 7.0, 1.0, true),
        ))
        val f = fuelStats(v)
        assertEquals(1, f.tanks.size)
        assertEquals(30.0, f.tanks[0].kmpl)
    }

    @Test fun `fuel maths survives odd logs`() {
        val cases = listOf(
            emptyList(),
            listOf(FuelEntry("a", 1, 100.0, 10.0, 1000.0, true)),
            listOf(FuelEntry("a", 1, 100.0, 3.0, 300.0, false), FuelEntry("b", 2, 300.0, 4.0, 400.0, false)),
            listOf(FuelEntry("a", 1, 500.0, 10.0, 1000.0, true), FuelEntry("b", 2, 400.0, 10.0, 1000.0, true)),
            listOf(FuelEntry("a", 1, 100.0, 0.0, 0.0, true), FuelEntry("b", 2, 400.0, 0.0, 0.0, true)),
        )
        for (fuel in cases) {
            val f = fuelStats(newVehicle().copy(fuel = fuel, odo = 450.0))
            for (x in listOf(f.avg, f.lifetime, f.lastTank, f.range, f.litresLeft, f.costPerKm)) assertTrue(x == null || x.isFinite())
            assertTrue(f.bars == null || f.bars in 0..6)
        }
    }

    @Test fun `logbook statuses whichever comes first`() {
        val v = sample.vehicle
        fun by(k: String) = serviceStatus(v.odo, v.service.first { it.kind == k })
        assertEquals(Due.OVERDUE, by("chain").status)
        assertEquals(106, by("chain").overdueKm.roundToInt())
        assertEquals(Due.SOON, by("brakes").status)
        assertEquals(true, by("brakes").byTime)
        assertEquals(Due.OK, by("oil").status)
        assertEquals(613.0, by("oil").remKm)
        val verdict = verdictOf(sortedService(v))
        assertEquals("Needs care", verdict.word)
        assertEquals("One job is overdue.", verdict.line)
        assertEquals(Due.OVERDUE, sortedService(v).first().st.status)
    }

    @Test fun `service milestones advance 500, 5000, 10000, then plus 5000`() {
        var it = freshState().vehicle.service.first { it.milestone }
        assertEquals(500.0, it.targetKm)
        it = it.markDone(0.0); assertEquals(5000.0, it.targetKm)
        it = it.markDone(0.0); assertEquals(10000.0, it.targetKm)
        it = it.markDone(10020.0); assertEquals(15020.0, it.targetKm)
    }

    @Test fun `odd service items stay sane`() {
        val now = System.currentTimeMillis()
        val items = listOf(
            ServiceItem(kind = "x", name = "x", lastDate = now),
            ServiceItem(kind = "y", name = "y", everyKm = 500.0, lastKm = 20000.0, lastDate = now),
            ServiceItem(kind = "z", name = "z", everyDays = 30, lastDate = now + DAY_MS * 400),
            ServiceItem(kind = "m", name = "m", milestone = true, targetKm = 500.0, everyKm = 5000.0, everyDays = 365),
        )
        for (i in items) assertTrue(serviceStatus(12000.0, i, now).frac in 0.0..1.0)
    }

    @Test fun `backups round-trip and drop paired devices`() {
        val s = sample.updateVehicle(sample.vehicles[0].id) { it.copy(bike = PairedBike("dev1", "Cluster")) }
        val back = Vault.import(Vault.export(s))
        assertEquals(2, back.vehicles.size)
        assertEquals("Arjun", back.profile.name)
        assertNull(back.vehicles[0].bike.id)
        assertEquals(true, back.onboarded)
        assertEquals(s.vehicles[0].fuel, back.vehicles[0].fuel)
        for (bad in listOf("", "not json", "{\"hello\":1}", "{\"format\":\"kickstart-vault\",\"data\":null}", "[]")) {
            assertFailsWith<Vault.NotABackup> { Vault.import(bad) }
        }
    }

    @Test fun `reads a backup written by the web app`() {
        val web = """{"format":"waylink-vault","version":2,"exported":"2026-11-13T03:00:00.000Z","data":{"v":2,"onboarded":true,"sample":false,
            "profile":{"name":"Krishna","country":"IN","avatar":"a4","licenceExpiry":null,"fuelArea":{"state":"Karnataka"}},
            "settings":{"theme":"amoled","accent":"rust","font":"system","wineV1":true},
            "alerts":{"missed":[],"wa":[],"sent":0,"log":[]},
            "vehicles":[{"id":"v1","model":"n250","nickname":"","plate":"KA 01 AB 1234","colour":"#1F3A5F","photo":null,"addedAt":1,"odo":4321.5,
              "tripA":{"km":12.34,"since":1},"tripB":{"km":0,"since":1},"fuel":[{"id":"f","ts":1,"odo":4000,"litres":10,"cost":1029,"full":true}],
              "fuelPrice":null,"service":[{"id":"s","kind":"chain","name":"Chain clean & lube","icon":"chain","everyKm":500,"everyDays":0,"lastKm":4000,"lastDate":1}],
              "records":[],"checklist":[],"trips":[{"id":"t","n":1,"start":1,"end":2,"km":5.5,"sec":600,"top":60,"avg":33,"title":"Ride","pts":[[12.9,77.5,1]]}],
              "rideNo":1,"parking":null,"bike":{"id":null,"name":null}}],"active":"v1"}}"""
        val s = Vault.import(web)
        assertEquals("Krishna", s.profile.name)
        assertEquals("auto", s.settings.theme, "web-only themes fall back to Auto")
        assertEquals(4321.5, s.vehicle.odo)
        assertEquals("Pulsar N250 / F250", s.model.name)
        assertEquals(1, s.vehicle.trips.size)
    }

    @Test fun `numbers read like the web app`() {
        val f = Fmt("en-IN")
        assertEquals("1.99 L", f.short(199999.9))
        assertEquals("45.2K", f.short(45280.0))
        assertEquals("9,999", f.short(9999.9))
        assertEquals("1.23 Cr", f.short(12345678.0))
        assertEquals("0", f.short(0.0))
        assertEquals("—", f.short(null))
        assertEquals("9,999", f.tripv(9999.9))
        assertEquals("12.3", f.tripv(12.34))
        assertEquals("12,261", f.num(12261.0))
        assertEquals("1,00,000", f.num(100000.0))
        assertEquals("1.99L", f.big(199999.9))
        assertEquals("41,520", f.big(41520.0))
        assertEquals("12.2K", f.short(12261.0))
        assertEquals("1.99M", Fmt("en-GB").short(1999999.0))
        assertEquals("1,000,000", Fmt("en-GB").num(1e6))
    }

    @Test fun `week starts on Monday`() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val fri = java.time.ZonedDateTime.of(2026, 11, 13, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val mon = java.time.ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(mon, weekStart(fri, zone))
    }

    @Test fun `logbook wording matches the web`() {
        val f = Fmt("en-IN")
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val now = java.time.ZonedDateTime.of(2026, 11, 13, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val chain = ServiceItem(kind = "chain", name = "Chain", everyKm = 500.0, lastKm = 11_655.0, lastDate = now - 20 * DAY_MS)
        assertEquals("every 500 km", everyText(chain, f))
        assertEquals("every 3,000 km or 6 months", everyText(chain.copy(everyKm = 3000.0, everyDays = 182), f))
        assertEquals("every 2 weeks", everyText(chain.copy(everyKm = 0.0, everyDays = 14), f))
        assertEquals("next at 15,000 km", everyText(chain.copy(milestone = true, targetKm = 15_000.0), f))
        val over = serviceStatus(12_261.0, chain, now)
        assertEquals(RowValue("106", "km overdue", true), rowValue(over, f))
        assertEquals("Overdue by 106 km. Do it on your next stop.", dueSentence(over, f, zone))
        val ok = serviceStatus(11_700.0, chain, now)
        assertEquals("Due in 455 km, at 12,155\u00A0km.", dueSentence(ok, f, zone))
        assertEquals("16 Jul", dShort(java.time.ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), zone))
        assertEquals("Fri, 25 Sept", dMed(java.time.ZonedDateTime.of(2026, 9, 25, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), zone))
        assertEquals("14 Mar 2035", dFull(java.time.ZonedDateTime.of(2035, 3, 14, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), zone))
    }
}
