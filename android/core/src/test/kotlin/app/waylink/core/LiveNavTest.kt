package app.waylink.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LiveNavTest {
    @Test fun googleMapsTurn() {
        val t = assertNotNull(NavParse.parse("350 m", "Turn right onto MG Road", "14 min · 5.2 km · 6:42 pm ETA"))
        assertEquals("TURN_RIGHT", t.maneuver)
        assertEquals(350.0, t.stepMeters)
        assertEquals("MG Road", t.street)
        assertEquals(5200.0, t.totalMeters)
        assertEquals(14, t.etaMin)
        assertEquals(18 to 42, t.etaClock)
    }

    @Test fun instructionInTheTitle() {
        val t = assertNotNull(NavParse.parse("Slight left toward Hosur Road", "1.2 km"))
        assertEquals("TURN_SLIGHT_LEFT", t.maneuver)
        assertEquals(1200.0, t.stepMeters)
        assertEquals("Hosur Road", t.street)
    }

    @Test fun roundaboutsKeepTheirExit() {
        val t = assertNotNull(NavParse.parse("200 m", "At the roundabout, take the 3rd exit onto NH 44"))
        assertEquals("ROUNDABOUT_LEFT", t.maneuver)
        assertEquals(3, t.exit)
        assertEquals("NH 44", t.street)
        assertEquals(2, NavParse.maneuverOf("At the roundabout, take the second exit").second)
    }

    @Test fun otherWordings() {
        assertEquals("KEEP_LEFT", NavParse.maneuverOf("Keep left to stay on Outer Ring Road").first)
        assertEquals("U_TURN_RIGHT", NavParse.maneuverOf("Make a U-turn").first)
        assertEquals("DESTINATION_REACHED", NavParse.maneuverOf("Arrive at Nandi Hills").first)
        assertEquals("MERGE", NavParse.maneuverOf("Merge onto NH 44").first)
        assertEquals("RAMP_LEFT", NavParse.maneuverOf("Take the exit on the left").first)
        assertEquals("STRAIGHT", NavParse.maneuverOf("Continue on Ballari Road").first)
        assertEquals(0.3 * 1609.344, NavParse.parse("0.3 mi", "Turn left")!!.stepMeters, 0.01)
    }

    @Test fun nothingToReadGivesNothing() {
        assertNull(NavParse.parse("Google Maps", "Navigation is running"))
        assertNull(NavParse.parse(null, null))
    }

    @Test fun sourcesFollowThePlatform() {
        assertEquals(5, NavSource.available(Caps.ANDROID).size)
        assertEquals(listOf("waylink"), NavSource.available(Caps.IOS).map { it.key })
        assertEquals("osmand", NavSource.forPackage("net.osmand.plus")?.key)
    }

    @Test fun speedAlertHasRoom() {
        assertEquals(false, SpeedAlert.over(81, 80))
        assertEquals(true, SpeedAlert.over(82, 80))
        assertEquals(true, SpeedAlert.under(77, 80))
        assertEquals(0, SpeedAlert.clamp(-4)); assertEquals(30, SpeedAlert.clamp(10))
    }

    @Test fun firstServiceComesFirstThenTheInterval() {
        val now = 1_700_000_000_000L
        val it = ServiceItem(kind = "custom", name = "Valve check", everyKm = 6000.0, lastKm = 0.0, lastDate = now, firstKm = 1000.0)
        val st = serviceStatus(900.0, it, now)
        assertEquals(1000.0, st.dueKm); assertEquals(Due.SOON, st.status)
        val done = it.markDone(1000.0, now)
        assertNull(done.firstKm)
        assertEquals(7000.0, serviceStatus(1000.0, done, now).dueKm)
        val byDate = ServiceItem(kind = "custom", name = "Insurance", everyDays = 365, lastDate = now, firstDate = now + 10 * DAY_MS)
        assertEquals(10, serviceStatus(0.0, byDate, now).remDays)
    }

    @Test fun newerGoogleMapsCards() {
        // Distance and instruction share the title; the short text has the distance; progress is the route in metres.
        val t = assertNotNull(NavParse.parse("200 m · Turn right toward NH 48", null, "Arrive 11:56 am", short = "200 m", progress = 338, progressMax = 1723))
        assertEquals("TURN_RIGHT", t.maneuver); assertEquals(200.0, t.stepMeters); assertEquals(1385.0, t.totalMeters)
        assertEquals("NH 48", t.street); assertEquals(11 to 56, t.etaClock); assertEquals("Turn right toward NH 48", t.say)
        // Only a street: the arrow carries over from the last card.
        val n = assertNotNull(NavParse.parse("150 m · Rua Principal", null, short = null, prev = t))
        assertEquals("TURN_RIGHT", n.maneuver); assertEquals(150.0, n.stepMeters); assertEquals("Rua Principal", n.street)
        assertEquals("Rerouting…", NavParse.parse("Rerouting…", null, prev = t)?.say)
        assertNull(NavParse.parse("Rerouting…", null))
        // Setting off: no distance to a turn yet, so the whole route stands in.
        val d = assertNotNull(NavParse.parse("Head southwest on Main Rd", null, "Arrive 12:55 pm", progress = 0, progressMax = 23708))
        assertEquals("STRAIGHT", d.maneuver); assertEquals(23708.0, d.stepMeters)
        assertEquals("DESTINATION_REACHED", NavParse.parse("You have arrived", null, prev = t)?.maneuver)
    }
}
