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
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> null } as T

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
                "pair-intro", "auto-ask", "pair", "parking", "glovebox", "doc:rc", "quick", "turn-test", "garage", "sos", "clear:clear-wa")
            v.service.take(3).forEach { keys += "svc:${it.id}"; keys += "svc-edit:${it.id}" }
            v.records.take(2).forEach { keys += "rec:${it.id}" }
            v.fuel.take(2).forEach { keys += "fuel:${it.id}" }
            v.trips.take(2).forEach { keys += "trip-detail:${it.id}"; keys += "trip-rename:${it.id}" }
            for (k in keys) {
                val spec = buildSheet(k, s, vm.live.value, vm, nav, host) ?: continue
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
