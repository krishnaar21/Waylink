package app.waylink.data

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import android.view.KeyEvent

/**
 * The phone's own state for the cluster's status line, and the handlebar's music keys. None of this needs a
 * permission: battery and volume are open to every app, signal strength is a level 0–4 (Android 9+).
 */
object PhoneStatus {
    data class Now(val battery: Int?, val bars: Int?, val volume: Int, val model: String)

    fun read(ctx: Context): Now {
        val battery = runCatching {
            (ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        }.getOrNull()
        val bars = if (Build.VERSION.SDK_INT >= 28) runCatching {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm == null || tm.simState != TelephonyManager.SIM_STATE_READY) null else tm.signalStrength?.level?.coerceIn(0, 4)
        }.getOrNull() else null
        val volume = runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 15f / max)
        }.getOrDefault(8)
        return Now(battery, bars, volume, Build.MODEL ?: "Android")
    }

    /** Play/pause or next track for whatever app is playing (Spotify, YouTube Music…). False when nothing took it. */
    fun media(ctx: Context, next: Boolean): Boolean = runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val code = if (next) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        true
    }.getOrDefault(false)

    fun musicActive(ctx: Context): Boolean = runCatching { (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive }.getOrDefault(false)
}
