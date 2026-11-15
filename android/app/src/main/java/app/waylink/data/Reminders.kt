package app.waylink.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.util.AtomicFile
import app.waylink.R
import app.waylink.core.Vault
import app.waylink.core.dueReminders
import java.io.File
import java.util.Calendar

/**
 * Phone reminders for reserve fuel, service due and papers expiring. A daily check (an inexact alarm, so the
 * phone can batch it) and one each time the app opens. Each piece of news is told once: its key is kept until
 * the news changes. Nothing leaves the phone.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val PREFS = "reminders"

    fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Reads the saved state and posts what's new. Safe to call often. */
    fun check(ctx: Context) {
        val app = ctx.applicationContext
        val s = runCatching {
            val f = AtomicFile(File(app.filesDir, "waylink-state.json"))
            if (!f.baseFile.exists()) null else Vault.decode(String(f.readFully(), Charsets.UTF_8))
        }.getOrNull() ?: return
        if (!s.onboarded || s.sample || !s.settings.reminders) return
        val due = dueReminders(s)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val told = prefs.getStringSet("told", emptySet()) ?: emptySet()
        // Forget keys whose news is over, so a job that comes due again is told again.
        val now = due.map { it.key }.toSet()
        if (!allowed(app)) { prefs.edit().putStringSet("told", told.intersect(now)).apply(); return }
        channel(app)
        val nm = NotificationManagerCompat.from(app)
        val open = PendingIntent.getActivity(
            app, 0, app.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for (r in due) {
            if (r.key in told) continue
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_alerts)
                .setContentTitle(r.title).setContentText(r.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open).setAutoCancel(true)
                .build()
            // One slot per kind and item, so a newer piece of news replaces the older one.
            runCatching { nm.notify(r.key.substringBeforeLast(':').hashCode(), n) }
        }
        prefs.edit().putStringSet("told", now).apply()
    }

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reserve fuel, service due and papers expiring"
        })
    }

    /** A daily look around 9 in the morning. */
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(app, 1, Intent(app, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        runCatching { am.setInexactRepeating(AlarmManager.RTC, at, AlarmManager.INTERVAL_DAY, pi) }
    }
}

/** The daily alarm, and the phone restarting (alarms don't survive it). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(ctx)
        Reminders.check(ctx)
    }
}
