package app.waylink.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import app.waylink.R

/**
 * The web app's sound effects (js/sound.js). The web synthesises them live with Web Audio; these are the same
 * patches rendered offline from that code, so they sound the same, and play through one small SoundPool.
 * Everything is silent when the "Sound effects" setting is off.
 */
class Sound(ctx: Context) {
    enum class Fx(val res: Int) {
        ENGINE_START(R.raw.snd_engine_start), REV_UP(R.raw.snd_rev_up), ENGINE_OFF(R.raw.snd_engine_off),
        CLICK_ON(R.raw.snd_click_on), CLICK_OFF(R.raw.snd_click_off), HORN(R.raw.snd_horn),
        SWIPE_L(R.raw.snd_swipe_l), SWIPE_R(R.raw.snd_swipe_r), BLIP(R.raw.snd_blip),
    }

    var enabled = true
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val ids = HashMap<Fx, Int>()
    private val loaded = HashSet<Int>()
    private val pending = HashMap<Int, Pair<Float, Long>>()

    init {
        pool.setOnLoadCompleteListener { p, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            loaded += id
            // A sound asked for while still loading plays only if it is still timely (the web drops late sounds too).
            pending.remove(id)?.let { (rate, at) -> if (SystemClock.uptimeMillis() - at < 600) p.play(id, 1f, 1f, 1, 0, rate) }
        }
        try { Fx.entries.forEach { ids[it] = pool.load(ctx, it.res, 1) } } catch (e: Exception) { }
    }

    fun play(fx: Fx, rate: Float = 1f) {
        if (!enabled) return
        val id = ids[fx] ?: return
        if (id in loaded) pool.play(id, 1f, 1f, 1, 0, rate.coerceIn(0.5f, 2f)) else pending[id] = rate to SystemClock.uptimeMillis()
    }

    fun click(on: Boolean = true) = play(if (on) Fx.CLICK_ON else Fx.CLICK_OFF)
    fun swipe(dir: Int) = play(if (dir > 0) Fx.SWIPE_L else Fx.SWIPE_R)

    /** A slider move revs the engine to the value (0..1), as the web's revTo. */
    fun tick(p: Float) { if (enabled) rev.to(p) }

    private val rev = Rev()

    fun release() { pool.release(); rev.release() }
}

/**
 * The web's _revTo, synthesised live: one running engine whose revs follow a slider. A pulse wave at the firing
 * rate (1300–7500 rpm / 120), a tanh shaper, a 140 Hz body peak and a lowpass that opens with the revs. Quiet when
 * nudged, fuller when dragged fast; 260 ms after the last move it drops to idle and fades out.
 */
private class Rev {
    private val rate = 44100
    private val table = FloatArray(2048).also { t ->
        // The web's pulseWave: 16 % duty, harmonics rolled off by exp(-k / 26), normalised to a peak of 1
        for (i in t.indices) { var v = 0.0; val ph = i * 2 * Math.PI / t.size
            for (k in 1 until 48) v += (2.0 / (k * Math.PI)) * Math.sin(k * Math.PI * 0.16) * Math.exp(-k / 26.0) * Math.sin(k * ph)
            t[i] = v.toFloat() }
        val peak = t.maxOf { kotlin.math.abs(it) }; for (i in t.indices) t[i] /= peak
    }
    @Volatile private var target = 0f
    @Volatile private var level = 0f
    @Volatile private var lastAt = 0L
    private var lastP = -1f
    private var thread: Thread? = null

    @Synchronized fun to(p0: Float) {
        val p = p0.coerceIn(0f, 1f)
        val now = SystemClock.uptimeMillis()
        val dt = maxOf(0.016f, (now - lastAt) / 1000f)
        val speed = if (lastP < 0 || now - lastAt > 260) 0f else kotlin.math.abs(p - lastP) / dt
        lastP = p; lastAt = now
        target = p; level = 0.08f + minOf(1f, speed / 3f) * 0.17f
        if (thread?.isAlive != true) thread = Thread(::run, "rev").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun run() {
        val min = android.media.AudioTrack.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            android.media.AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(rate).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 2048)).setTransferMode(android.media.AudioTrack.MODE_STREAM).build()
        } catch (e: Exception) { return }
        if (track.state != android.media.AudioTrack.STATE_INITIALIZED) { track.release(); return }
        track.play()
        val n = 256; val buf = ShortArray(n)
        var phase = 0.0; var f = (1300 + target * 6200) / 120.0; var g = 0.0; var lp = 260 + (1300 + target * 6200) * 0.42
        // biquad states: body peak (a) and lowpass (b), direct form I
        var ax1 = 0.0; var ax2 = 0.0; var ay1 = 0.0; var ay2 = 0.0; var bx1 = 0.0; var bx2 = 0.0; var by1 = 0.0; var by2 = 0.0
        val peak = biquadPeak(140.0, 1.4, 9.0)
        var stoppedAt = 0L
        try {
            while (true) {
                val now = SystemClock.uptimeMillis()
                val idle = now - lastAt > 260
                if (idle && stoppedAt == 0L) stoppedAt = now
                if (!idle) stoppedAt = 0L
                if (stoppedAt != 0L && now - stoppedAt > 800) break
                val rpm = if (idle) 1300.0 else 1300.0 + target * 6200.0
                val fT = rpm / 120.0
                val gT = if (idle && now - stoppedAt > 150) 0.0 else level.toDouble()
                val lpT = minOf(5200.0, 260 + rpm * 0.42)
                val bs = n.toDouble() / rate
                f += (fT - f) * (1 - Math.exp(-bs / (if (idle) 0.12 else 0.03)))
                lp += (lpT - lp) * (1 - Math.exp(-bs / 0.06))
                val lpc = biquadLow(lp, 2.2)
                val g0 = g; g += (gT - g) * (1 - Math.exp(-bs / (if (idle) 0.1 else 0.05)))
                for (i in 0 until n) {
                    phase += f / rate; if (phase >= 1) phase -= 1
                    val x = Math.tanh(3.0 * table[(phase * table.size).toInt() and (table.size - 1)]) * 0.9
                    val a = peak[0] * x + peak[1] * ax1 + peak[2] * ax2 - peak[3] * ay1 - peak[4] * ay2
                    ax2 = ax1; ax1 = x; ay2 = ay1; ay1 = a
                    val b = lpc[0] * a + lpc[1] * bx1 + lpc[2] * bx2 - lpc[3] * by1 - lpc[4] * by2
                    bx2 = bx1; bx1 = a; by2 = by1; by1 = b
                    // master 0.25 as on the web, with a touch more so a phone speaker carries it
                    val gi = g0 + (g - g0) * i / n
                    buf[i] = (b * gi * 0.25 * 2.2 * 32767).coerceIn(-32767.0, 32767.0).toInt().toShort()
                }
                track.write(buf, 0, n)
            }
        } catch (e: Exception) { } finally { runCatching { track.stop() }; track.release() }
    }

    /** RBJ cookbook coefficients, normalised: b0, b1, b2, a1, a2. */
    private fun biquadPeak(fc: Double, q: Double, db: Double): DoubleArray {
        val a = Math.pow(10.0, db / 40); val w = 2 * Math.PI * fc / rate; val al = Math.sin(w) / (2 * q); val cs = Math.cos(w)
        val a0 = 1 + al / a
        return doubleArrayOf((1 + al * a) / a0, -2 * cs / a0, (1 - al * a) / a0, -2 * cs / a0, (1 - al / a) / a0)
    }
    private fun biquadLow(fc: Double, q: Double): DoubleArray {
        val w = 2 * Math.PI * fc / rate; val al = Math.sin(w) / (2 * q); val cs = Math.cos(w); val a0 = 1 + al
        return doubleArrayOf((1 - cs) / 2 / a0, (1 - cs) / a0, (1 - cs) / 2 / a0, -2 * cs / a0, (1 - al) / a0)
    }

    fun release() { lastAt = 0L }
}

