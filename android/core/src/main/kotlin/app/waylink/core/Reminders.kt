package app.waylink.core

/**
 * Phone reminders: what to tell the rider about, each with a [key] that changes only when the news does, so the
 * phone says it once (a new tank, a job done, a renewed paper starts it over). Platform-free: Android posts them
 * as notifications, an iPhone build can schedule the same list as local notifications.
 */
data class Reminder(val key: String, val kind: String, val title: String, val text: String)

private val PAPERS = mapOf("rc" to "Registration (RC)", "insurance" to "Insurance", "puc" to "Pollution (PUC)", "licence" to "Driving licence", "other" to "Your document")

fun dueReminders(s: AppState, now: Long = System.currentTimeMillis()): List<Reminder> {
    if (s.vehicles.isEmpty()) return emptyList()
    val v = s.vehicle
    val f = Fmt(s.country.locale)
    val out = ArrayList<Reminder>()

    val fs = fuelStats(v)
    if (fs.onReserve && fs.range != null) {
        val last = v.fuel.maxOfOrNull { it.ts } ?: 0
        out += Reminder("fuel:${v.id}:$last", "fuel", "Fuel on reserve", "About ${f.num(fs.range)} km left. Petrol pumps nearby are one tap away in Waylink.")
    }

    for (r in sortedService(v, now)) {
        val st = r.st
        if (st.status == Due.OK) continue
        val over = st.status == Due.OVERDUE
        out += Reminder(
            "svc:${r.item.id}:${r.item.lastDate}:${st.status}", "service",
            "${r.item.name} ${if (over) "is overdue" else "is due soon"}", dueSentence(st, f),
        )
    }

    for (d in s.glove) {
        val exp = d.expiry ?: continue
        val mine = d.slot.startsWith(v.id + ":") || d.slot.startsWith("rider:")
        if (!mine) continue
        val days = Math.floorDiv(exp - now, DAY_MS).toInt()
        val bucket = when { days < 0 -> "expired"; days <= 7 -> "week"; days <= 30 -> "month"; else -> continue }
        val name = PAPERS[d.slot.substringAfter(':')] ?: "Your document"
        out += Reminder(
            "doc:${d.slot}:$exp:$bucket", "papers",
            if (days < 0) "$name has expired" else if (days == 0) "$name expires today" else "$name expires in ${plural(days, "day", "days")}",
            "Valid till ${dFull(exp)}. Renew it, then update the copy in your glovebox.",
        )
    }
    return out
}
