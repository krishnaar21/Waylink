package app.waylink.core

/**
 * Something that needs the rider: overdue service, reserve fuel, missed calls, an expiring licence.
 * [go] says where tapping it leads: "sheet:<key>", "page:<name>" or "tab:<name>".
 */
data class InboxItem(val icon: String, val tone: String, val title: String, val desc: String, val go: String)

/** A port of inbox() in web-v0/js/views.js. Glovebox papers join when the glovebox does. */
fun inbox(s: AppState, f: Fmt, now: Long = System.currentTimeMillis()): List<InboxItem> {
    val v = s.vehicle
    val out = mutableListOf<InboxItem>()
    for (r in sortedService(v, now)) if (r.st.status != Due.OK) {
        val rv = rowValue(r.st, f)
        val over = r.st.status == Due.OVERDUE
        out += InboxItem(r.item.icon, if (over) "red" else "amber", "${r.item.name} ${if (over) "is overdue" else "is due soon"}", "${rv.v} ${rv.u}", "sheet:svc:${r.item.id}")
    }
    val fs = fuelStats(v, now)
    if (fs.onReserve) out += InboxItem("fuel", "red", "You’re on reserve", "About ${f.num(fs.range)} km left", "page:fuel")
    val m = s.alerts.missed
    if (m.isNotEmpty()) out += InboxItem("missed", "red", plural(m.size, "missed call", "missed calls"), "Last from ${m.last().name}", "tab:alerts")
    val w = s.alerts.wa
    if (w.isNotEmpty()) out += InboxItem("chat", "sage", plural(w.size, "WhatsApp message", "WhatsApp messages"), "Last from ${w.last().name}", "tab:alerts")
    val exp = s.profile.licenceExpiry
    if (exp != null && exp - now < 30 * DAY_MS) out += InboxItem("idcard", "amber", "Your licence needs renewing", dFull(exp), "page:profile")
    return out
}
