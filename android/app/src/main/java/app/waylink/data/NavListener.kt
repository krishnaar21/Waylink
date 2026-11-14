package app.waylink.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import app.waylink.core.LiveTurn
import app.waylink.core.NavParse
import app.waylink.core.NavSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live turns from another navigation app. While Google Maps (or Waze, OsmAnd, Organic Maps) guides, it keeps an
 * ongoing notification with the next turn; Android lets an app the rider has allowed read it. Only those apps'
 * navigation notifications are looked at; nothing is stored or sent anywhere but the bike. Android only: iOS has no
 * such access, so [app.waylink.core.Caps.readNavApps] hides all of this there.
 */
class NavListener : NotificationListenerService() {
    override fun onListenerConnected() {
        NavFeed.connected.value = true
        runCatching { activeNotifications?.forEach(::read) }
    }

    override fun onListenerDisconnected() { NavFeed.connected.value = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) = read(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val src = NavSource.forPackage(sbn.packageName) ?: return
        if (NavFeed.turn.value?.first == src.key && isGuidance(sbn.notification, sbn.packageName)) NavFeed.turn.value = null
    }

    private fun read(sbn: StatusBarNotification) {
        val src = NavSource.forPackage(sbn.packageName) ?: return
        val n = sbn.notification ?: return
        if (!isGuidance(n, sbn.packageName)) return
        val ex = n.extras ?: return
        fun cs(k: String) = ex.getCharSequence(k)?.toString()
        fun int(k: String) = if (ex.containsKey(k)) ex.getInt(k) else null
        // The last turn from this app fills in what a card leaves out (a street-only title, "Rerouting…").
        val prev = NavFeed.turn.value?.takeIf { it.first == src.key }?.second
        val turn = NavParse.parse(
            cs(Notification.EXTRA_TITLE), cs(Notification.EXTRA_TEXT), cs(Notification.EXTRA_SUB_TEXT), cs(Notification.EXTRA_BIG_TEXT),
            // Read by key: the short critical text is new in Android 16, progress is metres of the route.
            short = cs("android.shortCriticalText"), progress = int(Notification.EXTRA_PROGRESS), progressMax = int(Notification.EXTRA_PROGRESS_MAX), prev = prev,
        ) ?: return
        NavFeed.turn.value = src.key to turn
    }

    /** The turn-by-turn card: marked as navigation, or at least ongoing (older versions of the other apps). Google Maps always marks it. */
    private fun isGuidance(n: Notification?, pkg: String): Boolean =
        n != null && (n.category == Notification.CATEGORY_NAVIGATION || (pkg != "com.google.android.apps.maps" && (n.flags and Notification.FLAG_ONGOING_EVENT) != 0))
}

/** The latest turn per source, and whether Android has the listener running. */
object NavFeed {
    val turn = MutableStateFlow<Pair<String, LiveTurn>?>(null)
    val connected = MutableStateFlow(false)
    val turns: StateFlow<Pair<String, LiveTurn>?> get() = turn.asStateFlow()

    /** Has the rider allowed Waylink to read notifications? */
    fun allowed(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Opens Android's notification access screen, on Waylink's own switch where the phone supports it. */
    fun openAccess(ctx: Context) {
        val detail = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, NavListener::class.java).flattenToString()) else null
        val ok = detail?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } ?: false
        if (!ok) runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Starting navigation in the chosen app, for a typed destination or a saved place. */
object NavApps {
    fun installed(ctx: Context, src: NavSource): Boolean = src.packages.any { p ->
        runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess
    }

    /** A search in the chosen app around where the phone is ("petrol pump"), else Google's search page. */
    fun search(ctx: Context, src: NavSource, what: String): Boolean {
        val pkg = src.packages.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess }
        val tries = buildList {
            if (pkg != null && src.key != "waze") add(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(what))).setPackage(pkg))
            add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/" + Uri.encode("$what near me"))))
        }
        return tries.any { i -> runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }

    /** Where to: [lat]/[lng] when known, else [query]. Two-wheeler mode in Google Maps. Returns false if nothing could open it. */
    fun go(ctx: Context, src: NavSource, query: String, lat: Double? = null, lng: Double? = null): Boolean {
        val where = if (lat != null && lng != null) "$lat,$lng" else query
        val pkg = src.packages.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess }
        val tries = buildList {
            when (src.key) {
                "gmaps" -> add(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(where) + "&mode=l")).setPackage(pkg))
                "waze" -> add(Intent(Intent.ACTION_VIEW, Uri.parse(if (lat != null && lng != null) "https://waze.com/ul?ll=$lat,$lng&navigate=yes" else "https://waze.com/ul?q=" + Uri.encode(query) + "&navigate=yes")).setPackage(pkg))
                else -> if (pkg != null) add(Intent(Intent.ACTION_VIEW, Uri.parse(if (lat != null && lng != null) "geo:$lat,$lng?q=$lat,$lng" else "geo:0,0?q=" + Uri.encode(query))).setPackage(pkg))
            }
            // Google's cross-platform directions link opens Maps, or a browser.
            add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(where) + "&travelmode=two-wheeler&dir_action=navigate")))
        }
        return tries.any { i -> runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }
}
