package app.waylink.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.widget.RemoteViews
import app.waylink.MainActivity
import app.waylink.R
import app.waylink.core.Due
import app.waylink.core.Fmt
import app.waylink.core.Vault
import app.waylink.core.fuelStats
import app.waylink.core.sortedService
import app.waylink.core.vehicleTitle
import java.io.File

/**
 * Home screen widget: the bike, fuel range, the most urgent job, and Park here / SOS. It reads the saved state
 * file (the app refreshes it after every save) and opens the app at the right sheet when a button is tapped.
 */
class BikeWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, mgr, ids)

    companion object {
        const val EXTRA_OPEN = "app.waylink.OPEN"

        /** Redraws every placed widget. Cheap; safe to call after each save. */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(ctx, BikeWidget::class.java)) }.getOrNull() ?: return
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val s = runCatching {
                val f = AtomicFile(File(ctx.filesDir, "waylink-state.json"))
                if (!f.baseFile.exists()) null else Vault.decode(String(f.readFully(), Charsets.UTF_8))
            }.getOrNull()
            val rv = RemoteViews(ctx.packageName, R.layout.widget_bike)
            if (s == null || !s.onboarded) {
                rv.setTextViewText(R.id.w_bike, "Waylink")
                rv.setTextViewText(R.id.w_range, "Set up your bike")
                rv.setTextViewText(R.id.w_next, "Open Waylink to start")
            } else {
                val f = Fmt(s.country.locale)
                val v = s.vehicle
                val fs = fuelStats(v)
                rv.setTextViewText(R.id.w_bike, vehicleTitle(s))
                rv.setTextViewText(R.id.w_range, fs.range?.let { "${f.num(it)} km range" } ?: "${f.num(v.odo)} km")
                val top = sortedService(v).firstOrNull()
                rv.setTextViewText(R.id.w_next, when {
                    top == null -> "No service jobs yet"
                    top.st.status == Due.OVERDUE -> "${top.item.name} is overdue"
                    top.st.status == Due.SOON -> "${top.item.name} due soon"
                    else -> "All jobs on track"
                })
            }
            rv.setOnClickPendingIntent(R.id.w_root, open(ctx, null, 0))
            rv.setOnClickPendingIntent(R.id.w_park, open(ctx, "parking:new", 1))
            rv.setOnClickPendingIntent(R.id.w_sos, open(ctx, "sos", 2))
            mgr.updateAppWidget(ids, rv)
        }

        private fun open(ctx: Context, key: String?, code: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (key != null) i.putExtra(EXTRA_OPEN, key)
            return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
