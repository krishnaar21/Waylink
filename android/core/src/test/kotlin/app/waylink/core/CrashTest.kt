package app.waylink.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashTest {
    /** Runs [ms] of samples every 50 ms with the given g, and a GPS fix every second at [kmh]. */
    private fun CrashLogic.run(from: Long, ms: Long, g: Double, kmh: Double): Pair<Long, Boolean> {
        var t = from; var hit = false
        while (t < from + ms) {
            if (t % 1000 == 0L) speed(t, kmh)
            if (accel(t, g)) hit = true
            t += 50
        }
        return t to hit
    }

    @Test fun fallThenStillIsACrash() {
        val c = CrashLogic()
        var (t, _) = c.run(0, 10_000, 1.1, 45.0)
        assertFalse(c.accel(t, 6.5)); t += 50
        val (_, hit) = c.run(t, 12_000, 1.0, 0.0)
        assertTrue(hit, "a 6.5 g hit, then lying still and stopped")
    }

    @Test fun potholeAndRidingOnIsNot() {
        val c = CrashLogic()
        var (t, _) = c.run(0, 10_000, 1.1, 45.0)
        c.accel(t, 4.5); t += 50
        val (_, hit) = c.run(t, 20_000, 1.3, 40.0)
        assertFalse(hit, "rode on at speed")
    }

    @Test fun droppedPhoneAtRestIsNot() {
        val c = CrashLogic()
        var (t, _) = c.run(0, 60_000, 1.0, 0.0)
        c.accel(t, 8.0); t += 50
        val (_, hit) = c.run(t, 20_000, 1.0, 0.0)
        assertFalse(hit, "not moving before the jolt")
    }

    @Test fun shakingAfterIsNotYet() {
        val c = CrashLogic()
        var (t, _) = c.run(0, 10_000, 1.1, 40.0)
        c.accel(t, 5.0); t += 50
        // Walking about with the phone (the rider got up): never still long enough.
        var hit = false
        repeat(300) { i -> if (i % 20 == 0) c.speed(t, 3.0); if (c.accel(t, if (i % 4 < 2) 1.6 else 0.5)) hit = true; t += 50 }
        assertFalse(hit)
    }
}
