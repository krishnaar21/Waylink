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

    /** A slider move: a short throttle blip, pitched by the value (0..1), at most every 90 ms. */
    private var lastTick = 0L
    fun tick(p: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 90) return
        lastTick = now
        play(Fx.BLIP, 0.75f + p.coerceIn(0f, 1f) * 1.0f)
    }

    fun release() = pool.release()
}
