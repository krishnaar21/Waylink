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

    override fun onListenerDisconnected() {
        NavFeed.connected.value = false
        // Android can unbind the listener (an app update, a battery saver); ask for it back so turns keep coming.
        runCatching { requestRebind(ComponentName(this, NavListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { read(sbn); runCatching { PhoneFeed.read(sbn, applicationContext) } }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runCatching { PhoneFeed.removed(sbn) }
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
        val title = cs(Notification.EXTRA_TITLE); val text = cs(Notification.EXTRA_TEXT); val sub = cs(Notification.EXTRA_SUB_TEXT)
        val big = cs(Notification.EXTRA_BIG_TEXT); val short = cs("android.shortCriticalText")
        val p = int(Notification.EXTRA_PROGRESS); val pm = int(Notification.EXTRA_PROGRESS_MAX)
        val turn = NavParse.parse(
            title, text, sub, big,
            // Read by key: the short critical text is new in Android 16, progress is metres of the route.
            short = short, progress = p, progressMax = pm, prev = prev,
        )
        NavFeed.note(src.name, listOfNotNull(title, text, big?.takeIf { it != text }, sub, short?.let { "short: $it" }, pm?.let { "progress: $p / $it" }), turn)
        turn ?: return
        NavFeed.turn.value = src.key to turn
    }

    /** The turn-by-turn card: marked as navigation, or at least ongoing (older versions of the other apps). Google Maps always marks it. */
    private fun isGuidance(n: Notification?, pkg: String): Boolean =
        n != null && (n.category == Notification.CATEGORY_NAVIGATION || (pkg != "com.google.android.apps.maps" && (n.flags and Notification.FLAG_ONGOING_EVENT) != 0))
}

/** A call or chat seen in another app's notification. Only the person's name is kept, never the message. */
data class PhoneEvent(val kind: Kind, val name: String, val key: String) {
    enum class Kind { CALL, WA_CALL, CALL_ANSWERED, CALL_GONE, MISSED, WA, SMS }
}

/**
 * Calls and WhatsApp from the same notification access as live turns: the phone app's incoming-call card, its
 * missed-call card, and WhatsApp's call and message cards. This needs no phone, call-log, contacts or SMS
 * permission. Message text is never read; the title (the person or group) is all that's used.
 */
object PhoneFeed {
    val events = kotlinx.coroutines.flow.MutableSharedFlow<PhoneEvent>(extraBufferCapacity = 32)
    private val WA = setOf("com.whatsapp", "com.whatsapp.w4b")
    /** Phone apps on common Android phones (Google, Samsung, stock, Xiaomi, OnePlus, Oppo, Vivo). */
    private val DIALERS = setOf(
        "com.google.android.dialer", "com.android.dialer", "com.android.incallui", "com.android.server.telecom",
        "com.samsung.android.dialer", "com.samsung.android.incallui", "com.android.contacts", "com.miui.voip",
        "com.oneplus.dialer", "com.coloros.phonemanager", "com.vivo.incallui",
    )
    /** Common SMS apps; the phone's default SMS app counts too. */
    private val SMS_APPS = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging",
        "com.oneplus.mms", "com.coloros.mms", "com.vivo.message", "com.miui.mms",
    )
    /** The newest chat card per sender key with a Reply box, for auto-reply. One-to-one chats only. */
    private val REPLY = object : LinkedHashMap<String, Notification.Action>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Notification.Action>?) = size > 40
    }
    private val CALL_KEYS = mutableSetOf<String>()
    /** The newest card per call, so the handlebar can press its Answer or Decline button. */
    private val CALLS = mutableMapOf<String, Notification>()
    private val ANSWERED = mutableSetOf<String>()
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    fun read(sbn: StatusBarNotification, ctx: Context? = null) {
        val pkg = sbn.packageName
        val wa = pkg in WA
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val missedCat = Build.VERSION.SDK_INT >= 31 && n.category == Notification.CATEGORY_MISSED_CALL
        val dialer = pkg in DIALERS
        when {
            // Ringing (or on a call): one event per card, however often the app updates it.
            n.category == Notification.CATEGORY_CALL && (wa || dialer || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0) -> {
                synchronized(CALLS) { CALLS[sbn.key] = n }
                // Answered on the phone (or by us): the card turns into an ongoing call with a timer.
                if (!CALL_KEYS.add(sbn.key)) {
                    if (ongoing(n) && ANSWERED.add(sbn.key)) events.tryEmit(PhoneEvent(PhoneEvent.Kind.CALL_ANSWERED, "", sbn.key))
                    return
                }
                val name = clean(title).ifEmpty { clean(text) }.ifEmpty { "Unknown caller" }
                events.tryEmit(PhoneEvent(if (wa) PhoneEvent.Kind.WA_CALL else PhoneEvent.Kind.CALL, name, sbn.key))
            }
            missedCat || (dialer && (title.contains("missed", true) || text.contains("missed call", true))) -> {
                if (!fresh(sbn)) return
                // "Missed call" / "Rohan" or "Rohan" / "Missed call": the name is whichever line isn't the label.
                val name = if (title.contains("missed", true)) text else title
                events.tryEmit(PhoneEvent(PhoneEvent.Kind.MISSED, clean(name).ifEmpty { "Unknown caller" }, sbn.key))
            }
            wa && (n.category == Notification.CATEGORY_MESSAGE || ex.containsKey(Notification.EXTRA_MESSAGES)) -> {
                if (title.isEmpty() || title.equals("WhatsApp", true) || !fresh(sbn)) return
                keepReply(sbn.key, n)
                events.tryEmit(PhoneEvent(PhoneEvent.Kind.WA, clean(title), sbn.key))
            }
            // A text message: from a known SMS app or the phone's default one.
            (pkg in SMS_APPS || (ctx != null && pkg == runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull())) &&
                (n.category == Notification.CATEGORY_MESSAGE || ex.containsKey(Notification.EXTRA_MESSAGES)) -> {
                if (title.isEmpty() || !fresh(sbn)) return
                keepReply(sbn.key, n)
                events.tryEmit(PhoneEvent(PhoneEvent.Kind.SMS, clean(title), sbn.key))
            }
        }
    }

    fun removed(sbn: StatusBarNotification) {
        synchronized(CALLS) { CALLS.remove(sbn.key) }; ANSWERED.remove(sbn.key)
        if (CALL_KEYS.remove(sbn.key)) events.tryEmit(PhoneEvent(PhoneEvent.Kind.CALL_GONE, "", sbn.key))
    }

    private fun keepReply(key: String, n: Notification) {
        // Groups never get an auto-reply.
        if (n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) { synchronized(REPLY) { REPLY.remove(key) }; return }
        val a = n.actions?.firstOrNull { act -> act.remoteInputs?.any { it.allowFreeFormInput } == true } ?: return
        synchronized(REPLY) { REPLY[key] = a }
    }

    /** Can this chat card be answered from here (a one-to-one chat with a Reply box)? */
    fun canReply(key: String) = synchronized(REPLY) { REPLY.containsKey(key) }

    /**
     * Types [text] into the chat card's own Reply box and sends it, as a reply from the notification shade would.
     * No SMS permission is needed: the messaging app sends it.
     */
    fun reply(ctx: Context, key: String, text: String): Boolean {
        val a = synchronized(REPLY) { REPLY[key] } ?: return false
        val inputs = a.remoteInputs ?: return false
        val pi = a.actionIntent ?: return false
        return runCatching {
            val fill = Intent()
            val res = android.os.Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            android.app.RemoteInput.addResultsToIntent(inputs, fill, res)
            pi.send(ctx, 0, fill)
            true
        }.getOrDefault(false)
    }

    /** An incoming card has Decline and Answer; an ongoing one has a timer and Hang up. */
    private fun ongoing(n: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            val type = n.extras.getInt(Notification.EXTRA_CALL_TYPE, 0)
            if (type != 0) return type == 2 // CallStyle.CALL_TYPE_ONGOING
        }
        return n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
    }

    /**
     * Presses the call card's own Answer, or Decline / Hang up, the same as tapping it in the shade. Works for the
     * phone app and WhatsApp alike, with no extra permission. False when the card has no such button.
     */
    fun act(key: String, answer: Boolean): Boolean {
        val n = synchronized(CALLS) { CALLS[key] } ?: return false
        val acts = n.actions?.toList().orEmpty()
        if (acts.isEmpty()) return false
        val ans = Regex("answer|accept|pick up|उत्तर|स्वीकार", RegexOption.IGNORE_CASE)
        val end = Regex("decline|reject|hang ?up|\\bend\\b|dismiss|अस्वीकार|काटें", RegexOption.IGNORE_CASE)
        val pick = acts.firstOrNull { (if (answer) ans else end).containsMatchIn(it.title?.toString().orEmpty()) }
            // Untitled or another language: an incoming CallStyle card lists Decline first and Answer last.
            ?: if (ongoing(n)) (if (answer) null else acts.first()) else if (acts.size >= 2) (if (answer) acts.last() else acts.first()) else null
        pick ?: return false
        return runCatching { pick.actionIntent?.send(); pick.actionIntent != null }.getOrDefault(false)
    }

    /** Apps re-post the same card for small changes; count it once per post time. */
    private fun fresh(sbn: StatusBarNotification): Boolean = synchronized(seen) { seen.put("${sbn.key}@${sbn.notification.`when`}", true) == null }

    /** "Rohan (3 messages)" or "Family: Rohan" stays a short name the cluster can show. */
    private fun clean(t: String) = t.replace(Regex("""\s*\(\d+ (new )?messages?\)$"""), "").replace('\u202A', ' ').replace('\u202C', ' ').trim().take(32)
}

/** The latest turn per source, and whether Android has the listener running. */
object NavFeed {
    val turn = MutableStateFlow<Pair<String, LiveTurn>?>(null)
    val connected = MutableStateFlow(false)

    /** One turn card as it arrived, and what Waylink read from it. In memory only, for "Check live turns". */
    data class Seen(val at: Long, val app: String, val fields: List<String>, val read: String?)

    /** The newest cards, newest first: shows whether the maps app's updates reach Waylink and how each was read. */
    val log = MutableStateFlow<List<Seen>>(emptyList())
    /** Cards received since the listener started, and when the last one came. */
    val received = MutableStateFlow(0)
    val lastAt = MutableStateFlow(0L)

    fun note(app: String, fields: List<String>, t: LiveTurn?) {
        val now = System.currentTimeMillis()
        val read = t?.let { "${it.maneuver.lowercase().replace('_', ' ')} · ${it.stepMeters.toInt()} m" + (it.totalMeters?.let { m -> " · ${m.toInt()} m left" } ?: "") + (if (it.street.isNotEmpty()) " · ${it.street}" else "") }
        log.value = (listOf(Seen(now, app, fields.map { it.take(120) }, read)) + log.value).take(20)
        received.value += 1
        lastAt.value = now
    }
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

    /**
     * Where to. With [lat]/[lng] (a picked suggestion or a saved spot) navigation starts straight away, in two-wheeler
     * mode in Google Maps. With only typed words it opens the maps app's search for them instead, so the rider picks
     * the right place and taps Start there: handing words to "navigate" made Maps guess one, often the wrong one.
     * [near] ranks results around the rider. Returns false if nothing could open it.
     */
    fun go(ctx: Context, src: NavSource, query: String, lat: Double? = null, lng: Double? = null, near: Pair<Double, Double>? = null): Boolean {
        if (lat == null || lng == null) return find(ctx, src, query, near)
        val where = "$lat,$lng"
        val pkg = src.packages.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess }
        val tries = buildList {
            when (src.key) {
                "gmaps" -> add(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$where&mode=l")).setPackage(pkg))
                "waze" -> add(Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$where&navigate=yes")).setPackage(pkg))
                else -> if (pkg != null) add(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$where?q=$where")).setPackage(pkg))
            }
            // Google's cross-platform directions link opens Maps, or a browser.
            add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$where&travelmode=two-wheeler&dir_action=navigate")))
        }
        return tries.any { i -> runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }

    /** The maps app's own search for [query], around [near] when known: the rider picks a result and starts there. */
    fun find(ctx: Context, src: NavSource, query: String, near: Pair<Double, Double>? = null): Boolean {
        val q = query.trim().take(120)
        if (q.isEmpty()) return false
        val pkg = src.packages.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess }
        val at = near?.let { (la, ln) -> "%.4f,%.4f".format(java.util.Locale.US, la, ln) } ?: "0,0"
        val tries = buildList {
            when (src.key) {
                "waze" -> add(Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=" + Uri.encode(q))).setPackage(pkg))
                else -> if (pkg != null) add(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$at?q=" + Uri.encode(q))).setPackage(pkg))
            }
            add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))))
        }
        return tries.any { i -> runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    }

    /** Just opens the maps app, to start navigation there. */
    fun open(ctx: Context, src: NavSource): Boolean {
        val pkg = src.packages.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess } ?: return false
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}
