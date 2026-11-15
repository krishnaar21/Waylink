package app.waylink.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** One result: [go] takes the same keys as Quick Actions ("tab:", "page:", a sheet key, or "place-go:<id>"). */
data class Found(val title: String, val sub: String?, val icon: String, val go: String)

data class FoundGroup(val name: String, val hits: List<Found>)

/**
 * Search on Today. Everything stays on the phone: no network, no AI service. A query is read in plain
 * language: filler words drop out ("when is my next service" → service), words are matched loosely
 * (plurals, a missing letter, one typo), and questions with a clear answer ("how far on this tank",
 * "where did I park") get the answer itself on top. The rest is grouped: service, fuel, rides, papers,
 * places, workshop visits, safety, and the app's own screens and settings.
 */
object Finder {
    /** Shown while the box is empty. */
    val SUGGESTIONS = listOf(
        "When is my next service?" to "wrench",
        "How far can I go on this tank?" to "fuel",
        "Where did I park?" to "parking",
        "When does my insurance expire?" to "folder",
        "My last ride" to "trips",
        "Petrol pumps nearby" to "fuel",
    )

    /** Settings worth one tap, shown under the questions while the box is empty. */
    fun settingsShortcuts(s: AppState): List<Found> = buildList {
        add(Found("Speed alert", if (s.settings.speedAlert > 0) "Buzz above ${s.settings.speedAlert} km/h" else "Off", "alert", "speed"))
        add(Found("Phone reminders", if (s.settings.reminders) "On · fuel, service, papers" else "Off", "bell", "tab:settings"))
        if (Platform.caps.readNavApps) add(Found("Live turns from", NavSource.of(s.settings.navSource).name, "nav", "nav-source"))
        add(Found("What the bike shows", "Sample display", "bike", "cluster:all"))
        add(Found("Permissions", "What Waylink can use", "shield", "perms"))
        add(Found("Backup & restore", "Save or move your data", "down", "backup"))
    }

    /** Shown when nothing matches. */
    val TRY = listOf("service", "fuel left", "papers", "last ride", "parked", "settings")

    private val GROUPS = listOf("Service", "Fuel", "Rides", "Papers", "Places", "Workshop visits", "Safety", "Settings & tools")

    private val STOP = setOf(
        "a", "an", "the", "is", "are", "am", "was", "were", "be", "been", "my", "me", "i", "im", "to", "of", "for", "on", "in", "at",
        "do", "does", "did", "can", "could", "how", "what", "whats", "when", "where", "which", "who", "show", "find", "open", "see",
        "get", "please", "there", "any", "some", "much", "many", "it", "its", "this", "that", "with", "and", "or", "you", "your",
        "have", "has", "need", "want", "tell", "about", "should", "will", "would", "go", "next", "last", "far", "long", "from", "by",
        "up", "ill", "till", "until", "left",
    )

    private val MONTHS = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val DAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val DOC_NAMES = mapOf("rc" to "Registration (RC)", "insurance" to "Insurance", "puc" to "Pollution (PUC)", "licence" to "Driving licence", "other" to "Other paper")
    private val DOC_WORDS = mapOf(
        "rc" to "rc registration certificate papers document",
        "insurance" to "insurance policy cover papers document",
        "puc" to "puc pollution emission certificate papers document",
        "licence" to "licence license driving dl papers document",
        "other" to "papers document",
    )

    private data class Item(val group: String, val hit: Found, val words: String) {
        val titleToks = toks(hit.title)
        val hay = titleToks + toks(words)
    }

    fun tokens(q: String): List<String> =
        q.lowercase().replace("km/l", "kmpl").replace('’', '\'').replace("'", "")
            .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    /** Plurals and -ing forms fold to one word: "services" → "service", "riding" → "rid". */
    fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes")) -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    private fun toks(t: String) = tokens(t).map(::stem)

    /** A typed word matches a word in the index by its start, by all but its last letter, or with one typo. */
    private fun match(q: String, h: String): Boolean {
        if (h.startsWith(q)) return true
        if (q.length >= 5 && h.startsWith(q.dropLast(1))) return true
        return q.length >= 5 && abs(q.length - h.length) <= 1 && near(q, h)
    }

    /** Edit distance of at most one. */
    private fun near(a: String, b: String): Boolean {
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    fun search(s: AppState, q: String, f: Fmt = Fmt(s.country.locale), now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<FoundGroup> {
        val plain = tokens(q)
        if (plain.isEmpty()) return emptyList()
        val answers = answers(s, (plain + plain.map(::stem)).toSet(), f, now, zone)
        val items = index(s, f, now, zone)
        // Filler drops out; if the query was only filler, its words are all we have.
        var words = (plain.filter { it !in STOP }.ifEmpty { plain }).map(::stem).distinct()
        // A word that matches nothing anywhere ("please", "bro") doesn't sink the rest.
        val known = words.filter { w -> items.any { it.hay.any { h -> match(w, h) } } }
        if (known.isNotEmpty()) words = known
        fun score(it: Item, need: Boolean): Int {
            var sc = 0; var hits = 0
            for (w in words) {
                val inTitle = it.titleToks.any { h -> match(w, h) }
                val inHay = inTitle || it.hay.any { h -> match(w, h) }
                if (inHay) hits++
                sc += when { it.titleToks.firstOrNull()?.startsWith(w) == true -> 4; inTitle -> 3; inHay -> 1; else -> 0 }
            }
            return if (hits == 0 || (need && hits < words.size)) 0 else sc + hits * 2
        }
        // Every word must match; when no single item has them all, the best partial matches show instead.
        var scored = items.map { it to score(it, true) }.filter { it.second > 0 }
        if (scored.isEmpty()) scored = items.map { it to score(it, false) }.filter { it.second > 0 }
        val answered = answers.map { it.go }.toSet()
        val seen = HashSet<String>()
        val groups = scored.sortedByDescending { it.second }
            .filter { it.first.hit.go !in answered && seen.add(it.first.hit.go + "|" + it.first.hit.title) }
            .groupBy { it.first.group }
        // The group with the strongest match comes first after the answer; ties keep the usual order.
        val rest = GROUPS.mapNotNull { g -> groups[g]?.let { list -> Triple(g, list.first().second, list) } }
            .sortedByDescending { it.second }
            .map { (g, _, list) -> FoundGroup(g, list.take(4).map { it.first.hit }) }
        return (if (answers.isEmpty()) emptyList() else listOf(FoundGroup("Answer", answers.take(2)))) + rest
    }

    /** Questions the phone can answer outright. */
    private fun answers(s: AppState, t: Set<String>, f: Fmt, now: Long, zone: ZoneId): List<Found> = buildList {
        val v = s.vehicle
        fun has(vararg w: String) = w.any { it in t }
        val cur = s.country.cur
        val wantsPumps = has("pump", "station", "bunk", "nearby", "near", "nearest")
        // Service: a named job if one was asked about, else the one due first.
        if (has("service", "servic", "maintenance", "due", "overdue", "oil", "chain", "brake", "tyre", "tire", "filter", "coolant", "spark")) {
            val rows = sortedService(v, now)
            val named = rows.firstOrNull { r -> toks(r.item.name).any { it in t && it !in setOf("service", "servic") } }
            (named ?: rows.firstOrNull())?.let { r ->
                add(Found(if (named != null) r.item.name else "Next up: ${r.item.name}", dueSentence(r.st, f, zone), r.item.icon, "svc:${r.item.id}"))
            }
        }
        val fs = fuelStats(v, now)
        if (!wantsPumps && has("range", "tank", "empty", "reserve", "fuel", "petrol", "gas", "litre", "liter")) {
            add(
                if (fs.range != null) Found("About ${f.num(fs.range)} km on this tank", listOfNotNull(
                    fs.litresLeft?.let { "≈${f.num(it, 1)} L left" }, if (fs.onReserve) "On reserve" else null, fs.avg?.let { "${f.num(it, 1)} km/L" },
                ).joinToString(" · ").ifBlank { null }, "fuel", "page:fuel")
                else Found("No full tank logged yet", "Log a fill-up to see the range", "fuel", "fuel:new"),
            )
        }
        if (has("mileage", "kmpl", "efficiency", "economy", "average", "avg")) fs.avg?.let {
            add(Found("${f.num(it, 1)} km/L", "Average over your last full tanks", "fuel", "page:fuel"))
        }
        if (has("spend", "spent", "cost", "money", "expense", "expens", "price", "paid", "pay")) {
            add(Found("$cur${f.num(fs.last30.cost)} on fuel in 30 days", "${plural(fs.last30.count, "fill-up", "fill-ups")} · $cur${f.num(fs.totalSpent)} all time", "fuel", "page:fuel"))
        }
        if (has("fill", "filled", "refuel", "refueled", "refuelled", "topped")) v.fuel.maxByOrNull { it.ts }?.let { e ->
            add(Found("Last fill-up, ${dMed(e.ts, zone)}", "${f.num(e.litres, 2)} L · $cur${f.num(e.cost)} · at ${f.num(e.odo)} km", "fuel", "fuel:${e.id}"))
        }
        if (has("park", "parked", "parking") || (has("bike") && "where" in t)) add(
            v.parking?.let { Found("Parked ${f.ago(it.ts, now)}", "Get walking directions back", "parking", "parking") }
                ?: Found("No parking spot saved", "Save it when you stop", "parking", "parking"),
        )
        if (has("odo", "odometer", "reading", "kilometre", "kilometer", "km") && !has("ride", "rode", "trip", "week", "month")) {
            add(Found("${f.num(Math.round(v.odo).toDouble())} km on the odometer", "Tap to fix the reading", "gauge", "odo"))
        }
        if (has("ride", "rode", "trip", "ridden", "rid")) {
            when {
                has("week") -> periodStats(v.trips, weekStart(now, zone)).let { p -> add(Found("${f.num(p.km, 1)} km this week", plural(p.rides, "ride", "rides"), "trips", "tab:trips")) }
                has("month") -> periodStats(v.trips, now - 30 * DAY_MS).let { p -> add(Found("${f.num(p.km, 1)} km in 30 days", plural(p.rides, "ride", "rides"), "trips", "tab:trips")) }
                else -> v.trips.maxByOrNull { it.start }?.let { tr -> add(Found("Last ride: ${tr.title}", "${f.num(tr.km, 1)} km · ${f.ago(tr.end, now)}", "trips", "trip-detail:${tr.id}")) }
            }
        }
        // Papers: the one asked about, or the next to expire when the question is about expiry.
        val kinds = mutableListOf<String>()
        if (has("insurance", "policy")) kinds += "insurance"
        if (has("puc", "pollution", "emission")) kinds += "puc"
        if (has("rc", "registration")) kinds += "rc"
        if (has("licence", "license", "dl")) kinds += "licence"
        val expiry = has("expire", "expir", "expiry", "renew", "renewal", "valid", "validity")
        val docs = s.glove.filter { it.slot.startsWith(v.id + ":") || it.slot.startsWith("rider:") }
        kinds.forEach { k ->
            val d = docs.firstOrNull { it.slot.substringAfter(':') == k }
            val name = DOC_NAMES[k] ?: "Paper"
            add(
                when {
                    d == null && k == "licence" && s.profile.licenceExpiry != null -> Found("Driving licence", expiryText(s.profile.licenceExpiry, now, zone), "folder", "page:profile")
                    d == null -> Found("No $name saved", "Add a photo to your papers", "folder", "glovebox")
                    else -> Found(name, d.expiry?.let { expiryText(it, now, zone) } ?: "Saved, no expiry date", "folder", "doc:${d.slot}")
                },
            )
        }
        if (kinds.isEmpty() && expiry) docs.filter { it.expiry != null }.minByOrNull { it.expiry!! }?.let { d ->
            add(Found(DOC_NAMES[d.slot.substringAfter(':')] ?: d.name, expiryText(d.expiry!!, now, zone), "folder", "doc:${d.slot}"))
        }
        if (has("blood")) add(
            if (s.profile.blood.isNotBlank()) Found("Blood group ${s.profile.blood}", "From your rider profile", "user", "page:profile")
            else Found("No blood group saved", "Add it to your rider profile", "user", "page:profile"),
        )
    }.distinctBy { it.go }

    private fun expiryText(ts: Long, now: Long, zone: ZoneId): String {
        val days = Math.floorDiv(ts - now, DAY_MS).toInt()
        return when {
            days < 0 -> "Expired on ${dFull(ts, zone)}"
            days == 0 -> "Expires today"
            days <= 45 -> "Expires in ${plural(days, "day", "days")}, ${dShort(ts, zone)}"
            else -> "Valid till ${dFull(ts, zone)}"
        }
    }

    private fun index(s: AppState, f: Fmt, now: Long, zone: ZoneId): List<Item> = buildList {
        val v = s.vehicle
        fun add(g: String, title: String, sub: String?, icon: String, go: String, words: String) = add(Item(g, Found(title, sub, icon, go), words))
        // The app's own screens and actions, with the words people use for them.
        add("Fuel", "Log fuel", "Add a fill-up", "fuel", "fuel:new", "petrol gas fill refuel tank litres add new")
        add("Fuel", "Fuel", "Range, mileage, cost", "fuel", "page:fuel", "petrol gas range mileage kmpl cost spend economy average tank history")
        add("Fuel", "Petrol pumps nearby", "Nearest first", "fuel", "nearby:fuel", "petrol pump bunk station gas nearest near fuel")
        add("Fuel", "Petrol price", "The rate you pay", "fuel", "fuelprice", "petrol price rate cost litre")
        add("Safety", "Hospitals nearby", "Nearest first", "hospital", "nearby:hospital", "hospital doctor emergency clinic nearest near medical")
        add("Safety", "SOS", "Emergency numbers", "sos", "sos", "emergency police ambulance help accident crash call")
        add("Safety", "Rider profile", "Blood group, contacts", "user", "page:profile", "profile blood group emergency contact medical allergy me")
        add("Rides", "Parked bike", "Save or find the spot", "parking", "parking", "park parking where spot")
        add("Service", "Logbook", "Service jobs and visits", "wrench", "page:logbook", "service maintenance due oil chain history jobs")
        add("Service", "Add a service item", "Custom reminder", "plus", "svc-edit:new", "new reminder job item custom service add")
        add("Service", "Log a workshop visit", "What was done, the bill", "doc", "rec:new", "workshop mechanic garage bill repair visit service add")
        add("Papers", "Papers", "RC, insurance, PUC, licence", "folder", "glovebox", "documents glovebox rc insurance puc pollution licence license papers expiry")
        add("Settings & tools", "Weather", "Next hours", "wxPart", "weather", "rain forecast temperature")
        add("Places", "Route", "Plan a ride, live turns", "route", "tab:route", "navigation directions maps turns plan")
        add("Places", "Saved places", "Home, work and more", "home", "tab:route", "places home work address saved")
        add("Places", "Add a place", "Home, work, a favourite", "plus", "place:new", "new place save address home work favourite")
        add("Rides", "Rides", "Your recorded trips", "trips", "tab:trips", "trips history rides recorded log")
        add("Rides", "Record a ride", "Start logging now", "play", "record", "record start track gps log")
        add("Rides", "Ride mode", "Big, glanceable screen", "speed", "ride", "ride mode dashboard")
        add("Settings & tools", "Alerts", "Calls and messages", "alerts", "tab:alerts", "calls missed whatsapp messages notifications")
        add("Settings & tools", "Speed alert", "Buzz above a speed", "alert", "speed", "speed limit overspeed alert warning")
        add("Settings & tools", "Phone reminders", "Fuel, service, papers", "bell", "tab:settings", "notifications reminders")
        add("Settings & tools", "Permissions", "What Waylink can use", "shield", "perms", "permission location bluetooth notification access allow")
        if (Platform.caps.bluetooth) add("Settings & tools", "Bike link", "Pair or connect", "bt", "pair", "bluetooth pair connect cluster link")
        add("Settings & tools", "What the bike shows", "Sample display", "bike", "cluster:all", "cluster display screen dash sample lcd")
        add("Settings & tools", "Garage", "Your vehicles", "garage", "garage", "vehicles bikes switch add")
        add("Settings & tools", "Odometer", "Fix the reading", "gauge", "odo", "odo odometer km reading")
        add("Settings & tools", "Trip A", "Reset or check", "progress", "trip:A", "trip meter reset")
        add("Settings & tools", "Settings", "Look, sound, data", "settings", "tab:settings", "theme dark light colour color sound settings")
        add("Settings & tools", "Backup & restore", "Save or move your data", "down", "backup", "backup restore export import data")
        if (Platform.caps.readNavApps) add("Settings & tools", "Live turns from", "Which maps app", "nav", "nav-source", "google maps waze osmand organic source")
        add("Settings & tools", "Test a turn", "Check the arrow", "turnRight", "turn-test", "test arrow turn")
        add("Settings & tools", "Help", "Questions and answers", "info", "faq", "help faq questions how")
        // The rider's own things.
        sortedService(v, now).forEach { r ->
            val rv = rowValue(r.st, f)
            val sub = if (rv.red) "${rv.v} ${rv.u}" else "Due in ${rv.v} ${rv.u}"
            add("Service", r.item.name, sub, r.item.icon, "svc:${r.item.id}", "service due job ${r.item.kind}")
        }
        v.records.sortedByDescending { it.date }.take(30).forEach { r ->
            val sub = listOfNotNull(dShort(r.date, zone), r.workshop.ifBlank { null }, if (r.cost > 0) "${s.country.cur}${f.num(r.cost)}" else null).joinToString(" · ")
            add("Workshop visits", r.title, sub, "doc", "rec:${r.id}", "visit workshop bill repair ${r.workshop} ${r.notes} ${monthWords(r.date, zone)}")
        }
        v.trips.sortedByDescending { it.start }.take(40).forEach { t ->
            add("Rides", t.title, "${dMed(t.start, zone)} · ${f.num(t.km, 1)} km", "trips", "trip-detail:${t.id}", "ride trip ${monthWords(t.start, zone)}")
        }
        s.glove.filter { it.slot.startsWith(v.id + ":") || it.slot.startsWith("rider:") }.forEach { d ->
            val k = d.slot.substringAfter(':')
            add("Papers", DOC_NAMES[k] ?: d.name, d.expiry?.let { expiryText(it, now, zone) } ?: "Saved", "folder", "doc:${d.slot}", "${DOC_WORDS[k] ?: ""} ${d.name} expiry expire renew")
        }
        s.places.forEach { p ->
            add("Places", p.name, p.address.ifBlank { "Saved place" }, if (p.kind == "home") "home" else "pin", "place-go:${p.id}", "place go navigate directions ${p.kind} ${p.address}")
        }
    }

    private fun monthWords(ts: Long, zone: ZoneId): String {
        val d = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        return "${MONTHS[d.monthValue - 1]} ${DAYS[d.dayOfWeek.value - 1]} ${d.year}"
    }
}
