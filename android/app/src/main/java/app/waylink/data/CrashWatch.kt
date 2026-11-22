package app.waylink.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.waylink.core.CrashLogic
import kotlin.math.sqrt

/**
 * Listens to the accelerometer while a ride is on and crash detection is switched on, and calls [onCrash] when
 * [CrashLogic] confirms one. GPS speed comes in through [speed]. Nothing is recorded or sent anywhere.
 */
class CrashWatch(ctx: Context, private val onCrash: () -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val logic = CrashLogic()
    var running = false; private set

    fun start() {
        if (running) return
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        // About 20 readings a second: enough to catch a hit, light on the battery.
        running = sm.registerListener(this, acc, 50_000)
        logic.reset()
    }

    fun stop() { if (running) { sm?.unregisterListener(this); running = false } }

    fun speed(kmh: Double) = logic.speed(System.currentTimeMillis(), kmh)

    override fun onSensorChanged(e: SensorEvent) {
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val g = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        if (logic.accel(System.currentTimeMillis(), g)) onCrash()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
