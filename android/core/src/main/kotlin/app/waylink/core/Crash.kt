package app.waylink.core

import kotlin.math.abs

/**
 * Crash detection from the phone's accelerometer and GPS speed, tuned to miss potholes, speed breakers and a
 * dropped phone. A crash needs all three, in order:
 *  1. moving: at least [MOVING_KMH] within the last 15 s;
 *  2. an impact: [IMPACT_G] or more (about 4 g; a hard pothole on a mount reads 2–3 g);
 *  3. then stopped: within [SETTLE_MS], GPS under [STOPPED_KMH] and the phone lying still (near 1 g, little
 *     shake) for [STILL_MS]. Riding on at speed after the jolt cancels it.
 * Feed it with [accel] (g, the length of the acceleration vector divided by 9.81) and [speed]; [accel] returns
 * true once, when a crash is confirmed.
 */
class CrashLogic {
    private var fastAt = -1L
    private var impactAt = -1L
    private var stillSince = -1L
    private var lastKmh = 0.0
    private var lastSpeedAt = -1L

    fun speed(t: Long, kmh: Double) {
        lastKmh = kmh; lastSpeedAt = t
        if (kmh >= MOVING_KMH) fastAt = t
        // Still riding well after the jolt: it was the road, not a fall.
        if (impactAt >= 0 && t - impactAt > 1500 && kmh >= RIDING_ON_KMH) reset()
    }

    fun accel(t: Long, g: Double): Boolean {
        if (impactAt < 0) {
            if (g >= IMPACT_G && fastAt >= 0 && t - fastAt <= 15_000) { impactAt = t; stillSince = -1 }
            return false
        }
        if (t - impactAt > SETTLE_MS + STILL_MS) { reset(); return false }
        // Let the tumble finish before looking for stillness.
        if (t - impactAt < 1500) return false
        val still = abs(g - 1.0) < STILL_BAND
        if (!still) { stillSince = -1; return false }
        if (stillSince < 0) stillSince = t
        val stopped = lastSpeedAt >= impactAt && lastKmh < STOPPED_KMH || lastSpeedAt < impactAt && t - impactAt > 4000
        if (stopped && t - stillSince >= STILL_MS) { reset(); return true }
        return false
    }

    fun reset() { impactAt = -1; stillSince = -1 }

    companion object {
        const val MOVING_KMH = 20.0
        const val RIDING_ON_KMH = 12.0
        const val STOPPED_KMH = 5.0
        const val IMPACT_G = 4.0
        const val STILL_BAND = 0.25
        const val SETTLE_MS = 10_000L
        const val STILL_MS = 5_000L
        /** How long the rider has to say "I'm OK" before Waylink calls for help. */
        const val COUNTDOWN_S = 30
    }
}
