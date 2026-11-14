package app.waylink

import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.waylink.core.Catalog
import app.waylink.data.AppViewModel
import app.waylink.data.LinkState
import app.waylink.ui.components.Sheet
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.shell.AppRoot
import app.waylink.ui.shell.ExtraHost
import app.waylink.ui.shell.buildSheet
import app.waylink.ui.theme.W
import app.waylink.ui.theme.WaylinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration

/**
 * Real-world runs through every flow on the real view model: onboarding, the simulated bike, a recorded ride,
 * a rehearsed route, fuel, service, alerts and calls, garage, settings, backup, previews and erase. Then the
 * whole app is driven by taps across the tabs, and every sheet is opened on normal and edge-case data.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
class ScenarioTest {
    @get:Rule val compose = createComposeRule()

    init { app.waylink.ui.theme.Motion.still = true }

    private fun vm() = AppViewModel(ApplicationProvider.getApplicationContext())
    /** Runs what's queued on the main thread, moving the clock on by [ms]. */
    private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private inline fun <reified T> noop(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, m, a ->
            // Compose compares parameters, so the stand-in answers equals/hashCode like a real object.
            when (m.name) { "equals" -> self === a?.getOrNull(0); "hashCode" -> System.identityHashCode(self); "toString" -> "noop"; else -> null }
        } as T

    @Test fun onboardingWithSimulatedBike() {
        val vm = vm()
        assertTrue("fresh install starts in onboarding", !vm.state.value.onboarded)
        vm.saveBike("dominar400", " ka 01 ab 1234 ", 15234.0, "IN")
        vm.saveAboutYou("a4", "  Asha  ", "Pune", "O+", "Ravi", "+91 98450 12345")
        vm.finishOnboarding(simulated = true)
        idle(100)
        val s = vm.state.value
        assertTrue(s.onboarded)
        assertEquals("dominar400", s.vehicle.model)
        assertEquals("KA 01 AB 1234", s.vehicle.plate)
        assertEquals(15234.0, s.vehicle.odo, 0.01)
        assertEquals("Asha", s.profile.name)
        assertEquals("a4", s.profile.avatar)
        assertEquals(LinkState.CONNECTED, vm.live.value.link)
        assertTrue(vm.live.value.simulated)
        idle(6000)
        assertTrue("status frames stream to the simulated cluster", vm.live.value.frameCount > 0)
    }

    @Test fun onboardingThenPair() {
        val vm = vm()
        vm.finishOnboarding(simulated = false, pair = true)
        assertEquals("pick", vm.live.value.ask)
        vm.clearAsk()
        assertNull(vm.live.value.ask)
    }

    @Test fun recordARideOnTheSimulatedBike() {
        val vm = vm()
        vm.onboardSample()
        vm.simOn()
        val before = vm.state.value.vehicle.trips.size
        vm.startRecording()
        assertNotNull(vm.live.value.rec)
        // The simulated bike moves on wall-clock time, so let it ride for real.
        repeat(24) { Thread.sleep(1000); idle(1000) }
        val rec = vm.live.value.rec!!
        assertTrue("distance builds up: ${rec.km}", rec.km > 0.1)
        assertTrue("top speed builds up: ${rec.top}", rec.top > 10)
        assertTrue("speed is under the dial's top", vm.live.value.speed in 0..180)
        vm.openRideMode(true)
        assertTrue(vm.live.value.rideMode)
        vm.openRideMode(false)
        vm.stopRecording(keep = true)
        assertNull(vm.live.value.rec)
        assertEquals(before + 1, vm.state.value.vehicle.trips.size)
        val t = vm.state.value.vehicle.trips.first()
        assertTrue(t.km > 0.1)
        assertTrue(t.pts.isNotEmpty())
        vm.renameTrip(t.id, "Evening loop")
        assertEquals("Evening loop", vm.state.value.vehicle.trips.first { it.id == t.id }.title)
        vm.deleteTrip(t.id)
        assertEquals(before, vm.state.value.vehicle.trips.size)
    }

    @Test fun ridesRecordThemselvesAndTheBikeParks() {
        val vm = vm()
        vm.onboardSample()
        vm.toggleSetting("autoRecord")
        vm.simOn()
        assertTrue("linking starts a ride by itself", vm.live.value.rec?.auto == true)
        repeat(3) { Thread.sleep(1000); idle(1000) }
        vm.simOff()
        idle(100)
        assertNull("switching the bike off ends the ride", vm.live.value.rec)
        assertNotNull("the bike's spot is saved as it goes quiet", vm.state.value.vehicle.parking)
    }

    @Test fun vehiclePhotoSavesAndClears() {
        val vm = vm()
        vm.onboardSample()
        val v = vm.state.value.vehicle
        assertNull(vm.saveVehicle(v.id, v.model, v.nickname, v.plate, v.odo, v.colour, "/nonexistent/bike.jpg"))
        assertEquals("/nonexistent/bike.jpg", vm.state.value.vehicle.photo)
        assertNull(vm.saveVehicle(v.id, v.model, v.nickname, v.plate, v.odo, v.colour, null))
        assertNull(vm.state.value.vehicle.photo)
    }

    @Test fun tooShortARideIsNotKept() {
        val vm = vm()
        vm.onboardSample(); vm.simOn()
        val before = vm.state.value.vehicle.trips.size
        vm.startRecording(); vm.stopRecording(keep = true)
        assertEquals(before, vm.state.value.vehicle.trips.size)
    }

    @Test fun rehearseTheRouteToTheEnd() {
        val vm = vm()
        vm.onboardSample(); vm.simOn()
        vm.startRehearsal()
        assertNotNull(vm.live.value.nav)
        idle(2000)
        assertTrue("turn frames stream while a route runs", vm.live.value.frames.any { it.startsWith("Turn") })
        repeat(app.waylink.core.Rehearsal.STEPS.size) { vm.nextTurn() }
        assertNull("the route ends on arrival", vm.live.value.nav)
        vm.sendTurn()
        assertNotNull(vm.live.value.nav)
        vm.setNavDistance(250.0)
        assertEquals(250.0, vm.live.value.nav!!.stepMeters, 0.1)
        vm.endNav()
        assertNull(vm.live.value.nav)
    }

    @Test fun fuelFillUps() {
        val vm = vm()
        vm.onboardSample()
        val v = vm.state.value.vehicle
        val n = v.fuel.size
        assertNotNull("missing litres is refused", vm.saveFuel(null, v.odo + 10, null, null, true, System.currentTimeMillis(), 105.0))
        assertNull(vm.saveFuel(null, v.odo + 250, 6.2, 650.0, true, System.currentTimeMillis(), 105.0))
        val after = vm.state.value.vehicle
        assertEquals(n + 1, after.fuel.size)
        assertEquals("a fill-up past the odometer moves it on", v.odo + 250, after.odo, 0.1)
        vm.deleteFuel(after.fuel.last().id)
        assertEquals(n, vm.state.value.vehicle.fuel.size)
    }

    @Test fun serviceReminders() {
        val vm = vm()
        vm.onboardSample()
        val now = System.currentTimeMillis()
        assertNotNull("a reminder needs a distance or a time", vm.saveService(null, "Chain", 0.0, 0.0, null, 0.0, now))
        assertNull(vm.saveService(null, "Chain lube", 500.0, 0.0, null, vm.state.value.vehicle.odo, now))
        val it = vm.state.value.vehicle.service.last()
        assertEquals("Chain lube", it.name)
        vm.serviceDone(it.id)
        vm.saveRecord(null, "Chain lube", now, vm.state.value.vehicle.odo, "Home", 0.0, "")
        vm.deleteService(it.id)
        assertTrue(vm.state.value.vehicle.service.none { x -> x.id == it.id })
        vm.addCheck("  Tyre pressure  ")
        val c = vm.state.value.vehicle.checklist.last()
        assertEquals("Tyre pressure", c.text)
        vm.toggleCheck(c.id); assertTrue(vm.state.value.vehicle.checklist.last().done)
        vm.setOdometer(-5.0); vm.setOdometer(20000.0)
        assertEquals(20000.0, vm.state.value.vehicle.odo, 0.1)
    }

    @Test fun customServiceItemWithAFirstService() {
        val vm = vm()
        vm.onboardSample()
        val now = System.currentTimeMillis()
        val odo = vm.state.value.vehicle.odo
        assertNull(vm.saveService(null, "Valve check", 6000.0, 12.0, null, odo, now, icon = "spec", firstKm = odo + 1000, firstDate = now + 30L * 86_400_000))
        val it = vm.state.value.vehicle.service.last()
        assertEquals("spec", it.icon)
        assertEquals(odo + 1000, it.firstKm!!, 0.5)
        assertEquals(odo + 1000, app.waylink.core.serviceStatus(odo, it).dueKm!!, 0.5)
        // A first service behind the odometer means nothing, and an unknown icon keeps the old one.
        assertNull(vm.saveService(it.id, "Valve check", 6000.0, 0.0, null, odo, now, icon = "nope", firstKm = odo - 10))
        val edited = vm.state.value.vehicle.service.last()
        assertNull(edited.firstKm); assertEquals("spec", edited.icon)
        vm.serviceDone(it.id)
        assertNull(vm.state.value.vehicle.service.last().firstDate)
    }

    @Test fun liveTurnsPlacesAndSpeed() {
        val vm = vm()
        vm.onboardSample()
        // A turn from Google Maps becomes the live route; another app's is ignored until it's the chosen one.
        app.waylink.data.NavFeed.turn.value = "waze" to app.waylink.core.LiveTurn("TURN_LEFT", 300.0, "MG Road", "Turn left onto MG Road")
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(vm.live.value.nav)
        app.waylink.data.NavFeed.turn.value = "gmaps" to app.waylink.core.LiveTurn("TURN_RIGHT", 250.0, "Brigade Rd", "Turn right onto Brigade Rd", totalMeters = 4200.0, etaMin = 12)
        shadowOf(Looper.getMainLooper()).idle()
        val n = vm.live.value.nav
        assertEquals("maps", n?.mode); assertEquals("TURN_RIGHT", n?.maneuver); assertEquals(4200.0, n!!.totalMeters, 0.1)
        vm.endNav()
        app.waylink.data.NavFeed.turn.value = "gmaps" to app.waylink.core.LiveTurn("TURN_LEFT", 120.0, "", "Turn left")
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("stopped following stays stopped", vm.live.value.nav)
        app.waylink.data.NavFeed.turn.value = null
        shadowOf(Looper.getMainLooper()).idle()
        vm.setNavSource("waze"); assertEquals("waze", vm.state.value.settings.navSource)
        vm.setNavSource("nope"); assertEquals("waze", vm.state.value.settings.navSource)
        vm.setNavSource("gmaps")

        assertNotNull(vm.savePlace(null, " ", "home", "Somewhere", null, null))
        assertNotNull(vm.savePlace(null, "Home", "home", "", null, null))
        assertNull(vm.savePlace(null, "Home", "home", "12 Lavelle Rd", null, null))
        assertNull(vm.savePlace(null, "New flat", "home", "", 12.97, 77.59))
        val ps = vm.state.value.places
        assertEquals(2, ps.size)
        assertEquals("only one Home", 1, ps.count { it.kind == "home" })
        vm.deletePlace(ps.first().id)
        assertEquals(1, vm.state.value.places.size)

        // Place suggestions: named places first, then the area; a street address reads as one; bad coordinates go.
        val hits = app.waylink.data.PlaceSearch.parsePhoton("""{"features":[
            {"geometry":{"coordinates":[77.6101,12.9756]},"properties":{"name":"Brigade Road","city":"Bengaluru","state":"Karnataka"}},
            {"geometry":{"coordinates":[77.6101,12.9756]},"properties":{"name":"Brigade Road","city":"Bengaluru","state":"Karnataka"}},
            {"geometry":{"coordinates":[77.59,12.97]},"properties":{"street":"MG Road","housenumber":"12","city":"Bengaluru"}},
            {"geometry":{"coordinates":[200,12.97]},"properties":{"name":"Nowhere"}}]}""")
        assertEquals(2, hits.size)
        assertEquals("Brigade Road, Bengaluru, Karnataka", hits[0].label)
        assertEquals("MG Road 12", hits[1].title); assertEquals(12.97, hits[1].lat, 1e-6)

        // Nearby: nearest first, ways use their centre, one place mapped twice shows once, phones are cleaned.
        val spots = app.waylink.data.Nearby.parse(app.waylink.data.Nearby.Kind.HOSPITAL, """{"elements":[
            {"type":"way","center":{"lat":12.99,"lon":77.60},"tags":{"amenity":"hospital","name":"Far Hospital"}},
            {"type":"node","lat":12.971,"lon":77.594,"tags":{"amenity":"hospital","name":"Near Hospital","emergency":"yes","phone":"+91 80 1234 5678; 080 999"}},
            {"type":"way","center":{"lat":12.9711,"lon":77.5941},"tags":{"amenity":"hospital","name":"Near Hospital"}},
            {"type":"node","tags":{"name":"No place"}}]}""", 12.97, 77.59)
        assertEquals(listOf("Near Hospital", "Far Hospital"), spots.map { it.name })
        assertEquals("+918012345678", spots[0].phone); assertTrue(spots[0].emergency)
        assertTrue(app.waylink.data.Nearby.query(app.waylink.data.Nearby.Kind.FUEL, 12.97123, 77.59, 5000).contains("around:5000,12.971,77.590"))

        // Every quick link builds, with a subtitle.
        val f = app.waylink.core.Fmt(vm.state.value.country.locale)
        val links = app.waylink.ui.today.QuickLinks.ORDER.mapNotNull { app.waylink.ui.today.QuickLinks.build(it, vm.state.value, vm.live.value, f, 0) }
        assertEquals(app.waylink.ui.today.QuickLinks.ORDER.size, links.size)
        assertTrue(links.all { it.sub.isNotBlank() && it.go.isNotBlank() })

        // OpenStreetMap search as the stand-in: only real petrol pumps, with English names when there are some.
        val nom = app.waylink.data.Nearby.parseNominatim(app.waylink.data.Nearby.Kind.FUEL, """[
            {"lat":"12.975","lon":"77.60","category":"amenity","type":"fuel","name":"HP","namedetails":{"name":"HP Petrol"},"extratags":{"brand":"HPCL","opening_hours":"24/7"}},
            {"lat":"12.98","lon":"77.61","category":"shop","type":"car","name":"Fuel Motors"}]""", 12.97, 77.59)
        assertEquals(1, nom.size); assertEquals("HP Petrol", nom[0].name); assertTrue(nom[0].open24)

        // Handlebar music: on by default, a press goes to the phone's player; off, it's only logged.
        assertTrue(vm.state.value.settings.handlebarMedia)
        vm.press("tap"); vm.press("long")
        vm.toggleSetting("handlebarMedia"); assertTrue(!vm.state.value.settings.handlebarMedia)
        vm.press("tap")
        val ph = app.waylink.data.PhoneStatus.read(ApplicationProvider.getApplicationContext())
        assertTrue(ph.volume in 0..15)
        // Location isn't granted in the test, so the chip asks for it.
        assertEquals("perms", app.waylink.ui.route.attentionOf(ApplicationProvider.getApplicationContext(), vm.state.value, vm.live.value)?.key)

        vm.setSpeedAlert(10); assertEquals(30, vm.state.value.settings.speedAlert)
        vm.setSpeedAlert(80); assertEquals(80, vm.state.value.settings.speedAlert)
        vm.setSpeedAlert(0); assertEquals(0, vm.state.value.settings.speedAlert)
    }

    @Test fun alertsAndCalls() {
        val vm = vm()
        vm.onboardSample(); vm.simOn()
        vm.relay("missed", "Mom")
        assertTrue(vm.state.value.alerts.missed.any { it.name == "Mom" })
        vm.relay("wa", "Ravi")
        assertTrue(vm.state.value.alerts.wa.any { it.name == "Ravi" })
        vm.relay("ring", "Boss")
        assertTrue(vm.live.value.call?.ringing == true)
        vm.press("tap")
        assertTrue("a tap answers", vm.live.value.call?.ringing == false)
        vm.press("long")
        assertNull("a long press ends it", vm.live.value.call)
        vm.relay("ring", "Unknown")
        idle(26_000)
        assertNull("unanswered, it turns into a missed call", vm.live.value.call)
        assertTrue(vm.state.value.alerts.missed.any { it.name == "Unknown" })
        vm.toggleAlert("calls")
        vm.relay("missed", "Nobody")
        assertTrue("calls switched off are not logged", vm.state.value.alerts.missed.none { it.name == "Nobody" })
        vm.clearMissed(); vm.clearWa()
        assertTrue(vm.state.value.alerts.missed.isEmpty() && vm.state.value.alerts.wa.isEmpty())
    }

    @Test fun garageAndBikeColour() {
        val vm = vm()
        vm.onboardSample()
        val v = vm.state.value.vehicle
        val green = Catalog.COLOURS[2].first
        assertNull(vm.saveVehicle(v.id, v.model, v.nickname, v.plate, v.odo, green))
        assertEquals("the picked colour is saved", green, vm.state.value.vehicle.colour)
        assertNull("a very high odometer still saves", vm.saveVehicle(v.id, v.model, v.nickname, v.plate, 1_234_567.8, green))
        assertNull(vm.saveVehicle(null, "ns400z", "Weekend", "ka 02 zz 1", 300.0, Catalog.COLOURS[4].first))
        val added = vm.state.value.vehicle
        assertEquals("a new bike becomes the active one", "ns400z", added.model)
        vm.switchVehicle(v.id)
        assertEquals(v.id, vm.state.value.vehicle.id)
        vm.deleteVehicle(added.id)
        assertTrue(vm.state.value.vehicles.none { it.id == added.id })
    }

    @Test fun settingsBackupAndReset() {
        val vm = vm()
        vm.onboardSample()
        vm.setTheme("obsidian"); vm.setAccent("blue")
        assertEquals("obsidian", vm.state.value.settings.theme)
        val backup = vm.backupText()
        vm.setTheme("auto")
        assertTrue(vm.restore(backup))
        assertEquals("obsidian", vm.state.value.settings.theme)
        assertTrue("a broken file is refused", !vm.restore("{not json"))
        vm.setScenario("edge")
        assertTrue(vm.state.value.vehicle.trips.size >= 400)
        vm.setScenario(null)
        assertEquals("leaving a preview brings the real data back", "obsidian", vm.state.value.settings.theme)
        vm.eraseAll()
        assertEquals("erase keeps settings", "obsidian", vm.state.value.settings.theme)
        assertTrue(!vm.state.value.onboarded)
        vm.resetAll()
        assertEquals("auto", vm.state.value.settings.theme)
    }

    /** The whole app, driven by taps: every tab, back and forth. */
    @Test fun tapThroughTheTabs() {
        val vm = vm()
        vm.onboardSample(); vm.finishOnboarding(simulated = true); vm.hideSplash()
        compose.setContent { AppRoot(vm) }
        compose.waitForIdle()
        for (tab in listOf("Rides", "Route", "Alerts", "Settings", "Today", "Rides", "Today")) {
            compose.onAllNodesWithContentDescription(tab).onFirst().performClick()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
        }
        assertTrue(compose.onAllNodesWithText("Today", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    /** Every sheet opens without a crash, on the sample data and on the edge-case data. */
    @Test fun everySheetOpens() {
        val vm = vm()
        vm.onboardSample()
        val keys = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val nav = noop<SheetNav>(); val host = noop<ExtraHost>()
        // A compose test sets its content once, so the sheet shown is swapped through state.
        var shown by androidx.compose.runtime.mutableStateOf<Pair<String, app.waylink.ui.logbook.SheetSpec>?>(null)
        compose.setContent {
            WaylinkTheme("auto", "rust") {
                Box(Modifier.fillMaxSize().background(W.c.bg)) {
                    shown?.let { (k, spec) -> androidx.compose.runtime.key(k) { Sheet(true, {}, spec.title, spec.sub) { spec.content(this) } } }
                }
            }
        }
        for (kind in listOf<String?>(null, "edge")) {
            vm.setScenario(kind)
            val s = vm.state.value; val v = s.vehicle
            keys.clear()
            keys += listOf("odo", "trip:A", "trip:B", "svc-edit:new", "rec:new", "fuel:new", "fuelprice", "vehicle:new", "vehicle:${v.id}",
                "specs", "country", "pick-ridechart", "backup", "erase", "reset", "faq", "report", "about", "models", "support", "avatar",
                "prof:about", "prof:licence", "prof:medical", "weather", "pick-brand", "pick-font", "pick-progress", "log", "protocol",
                "pair-intro", "auto-ask", "pair", "parking", "glovebox", "doc:rc", "quick", "turn-test", "garage", "sos", "clear:clear-wa",
                "nav-source", "place:new", "speed", "perms", "nearby:fuel", "nearby:hospital")
            if (s.places.isEmpty()) vm.savePlace(null, "Home", "home", "12 Lavelle Rd", 12.97, 77.59)
            vm.state.value.places.forEach { keys += "place:${it.id}" }
            v.service.take(3).forEach { keys += "svc:${it.id}"; keys += "svc-edit:${it.id}" }
            v.records.take(2).forEach { keys += "rec:${it.id}" }
            v.fuel.take(2).forEach { keys += "fuel:${it.id}" }
            v.trips.take(2).forEach { keys += "trip-detail:${it.id}"; keys += "trip-rename:${it.id}" }
            for (k in keys) {
                val spec = buildSheet(k, vm.state.value, vm.live.value, vm, nav, host) ?: continue
                shown = "${kind ?: "sample"}/$k" to spec
                try { compose.waitForIdle() } catch (e: Throwable) {
                    failures += "$k (${kind ?: "sample"}): $e @ ${e.stackTrace.take(6).joinToString(" < ")}"
                    shown = null; runCatching { compose.waitForIdle() }
                }
            }
        }
        assertTrue("sheets that failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
