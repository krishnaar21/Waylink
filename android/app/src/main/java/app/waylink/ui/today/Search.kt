package app.waylink.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.NavSource
import app.waylink.core.Platform
import app.waylink.data.Live
import app.waylink.data.NavApps
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Txt
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * Search on Today: finds the app's own screens and actions, your saved places and service jobs by name or by
 * everyday words ("petrol", "insurance", "chain"). Everything is on the phone; nothing is sent anywhere.
 */
data class Hit(val title: String, val sub: String?, val icon: String, val go: String)

private data class Entry(val title: String, val sub: String?, val icon: String, val words: String, val go: String)

/** Screens and actions, with the words people use for them. */
private fun entries(s: AppState): List<Entry> = buildList {
    add(Entry("Log fuel", "Add a fill-up", "fuel", "petrol fill refuel tank litres mileage", "fuel:new"))
    add(Entry("Fuel", "Range, mileage, cost", "fuel", "petrol range mileage km/l cost spend", "page:fuel"))
    add(Entry("Petrol pumps nearby", "Nearest first", "fuel", "petrol pump bunk station gas", "nearby:fuel"))
    add(Entry("Hospitals nearby", "Nearest first", "hospital", "hospital doctor emergency clinic", "nearby:hospital"))
    add(Entry("SOS", "Emergency numbers", "sos", "emergency police ambulance help accident", "sos"))
    add(Entry("Parked bike", "Save or find the spot", "parking", "park parking where", "parking"))
    add(Entry("Logbook", "Service jobs and visits", "wrench", "service maintenance due oil chain", "page:logbook"))
    add(Entry("Add a service item", "Custom reminder", "plus", "new reminder job item", "svc-edit:new"))
    add(Entry("Log a workshop visit", "What was done, the bill", "doc", "workshop mechanic garage bill repair visit", "rec:new"))
    add(Entry("Papers", "RC, insurance, PUC, licence", "folder", "documents glovebox rc insurance puc pollution licence license", "glovebox"))
    add(Entry("Weather", "Next hours", "wxPart", "rain forecast temperature", "weather"))
    add(Entry("Route", "Plan a ride, live turns", "route", "navigation directions maps turns", "tab:route"))
    add(Entry("Saved places", "Home, work and more", "home", "places home work address", "tab:route"))
    add(Entry("Rides", "Your recorded trips", "trips", "trips history rides recorded", "tab:trips"))
    add(Entry("Record a ride", "Start logging now", "play", "record start track gps", "record"))
    add(Entry("Ride mode", "Big, glanceable screen", "speed", "ride mode dashboard", "ride"))
    add(Entry("Alerts", "Calls and messages", "alerts", "calls missed whatsapp messages notifications", "tab:alerts"))
    add(Entry("Speed alert", "Buzz above a speed", "alert", "speed limit overspeed alert", "speed"))
    add(Entry("Phone reminders", "Fuel, service, papers", "bell", "notifications reminders", "tab:settings"))
    add(Entry("Permissions", "What Waylink can use", "shield", "permission location bluetooth notification access", "perms"))
    add(Entry("Bike link", "Pair or connect", "bt", "bluetooth pair connect cluster link", "pair"))
    add(Entry("What the bike shows", "Sample display", "bike", "cluster display screen dash", "cluster:all"))
    add(Entry("Garage", "Your vehicles", "garage", "vehicles bikes switch add", "garage"))
    add(Entry("Odometer", "Fix the reading", "gauge", "odo odometer km reading", "odo"))
    add(Entry("Trip A", "Reset or check", "progress", "trip meter reset", "trip:A"))
    add(Entry("Rider profile", "Blood group, contacts", "user", "profile blood group emergency contact medical", "page:profile"))
    add(Entry("Settings", "Look, sound, data", "settings", "theme dark colour sound backup restore", "tab:settings"))
    add(Entry("Backup & restore", "Save or move your data", "down", "backup restore export import", "backup"))
    if (Platform.caps.readNavApps) add(Entry("Live turns from", "Which maps app", "nav", "google maps waze osmand organic source", "nav-source"))
    add(Entry("Test a turn", "Check the arrow", "turnRight", "test arrow turn", "turn-test"))
    // The rider's own things.
    s.places.forEach { p -> add(Entry(p.name, p.address.ifBlank { "Saved place" }, if (p.kind == "home") "home" else "pin", "place go navigate ${p.kind} ${p.address}", "place-go:${p.id}")) }
    s.vehicle.service.forEach { j -> add(Entry(j.name, "Service job", j.icon, "service due ${j.kind}", "svc:${j.id}")) }
}

fun searchHits(s: AppState, q: String): List<Hit> {
    val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return entries(s).map { e ->
        val hay = (e.title + " " + e.words).lowercase()
        val t = e.title.lowercase()
        // Title hits first, then word hits; every typed word must match somewhere.
        val ok = words.all { w -> hay.contains(w) }
        val score = if (!ok) -1 else words.sumOf { w -> if (t.startsWith(w)) 3 else if (t.contains(w)) 2 else 1 }
        e to score
    }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8).map { (e, _) -> Hit(e.title, e.sub, e.icon, e.go) }
}

/** The bar and, while there's a query, what it finds. [go] takes the same keys as Quick Actions. */
@Composable
fun SearchBar(s: AppState, go: (String) -> Unit) {
    val c = W.c
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var q by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val hits = remember(q, s.places, s.vehicle.service) { searchHits(s, q) }
    val open = { h: Hit ->
        focus.clearFocus(); q = ""
        if (h.go.startsWith("place-go:")) {
            val p = s.places.firstOrNull { it.id == h.go.drop(9) }
            val src = NavSource.of(s.settings.navSource).let { if (it.readsNotifications) it else NavSource.of("gmaps") }
            if (p != null) NavApps.go(ctx, src, p.address.ifBlank { p.name }, p.lat, p.lng)
        } else go(h.go)
    }
    val shape = RoundedCornerShape(16.dp)
    Column {
        BasicTextField(
            q, { q = it.take(60) }, singleLine = true,
            textStyle = T.input.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { hits.firstOrNull()?.let(open) }),
            modifier = Modifier.fillMaxWidth().height(48.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = "Search Waylink" },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(shape).background(c.card)
                        .border(if (focused) 1.5.dp else 1.dp, if (focused) c.ink.copy(alpha = 0.5f) else c.line, shape).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        val r = size.minDimension * 0.34f; val cx = size.width * 0.42f; val cy = size.height * 0.42f
                        drawCircle(c.ink3, r, Offset(cx, cy), style = Stroke(1.8.dp.toPx()))
                        drawLine(c.ink3, Offset(cx + r * 0.72f, cy + r * 0.72f), Offset(size.width * 0.92f, size.height * 0.92f), 1.8.dp.toPx(), StrokeCap.Round)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Txt("Search fuel, papers, places…", T.input, c.ink4, maxLines = 1)
                        inner()
                    }
                    if (q.isNotEmpty()) Box(Modifier.size(30.dp).clip(CircleShape).pressable(label = "Clear search", scale = 0.9f) { q = "" }, contentAlignment = Alignment.Center) {
                        Ic("x", size = 16.dp, tint = c.ink3)
                    }
                }
            },
        )
        if (q.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            if (hits.isEmpty()) Txt("Nothing for “${q.trim()}”.", T.row, c.ink3, Modifier.padding(start = 6.dp, top = 4.dp))
            else ListCard {
                hits.forEachIndexed { i, h ->
                    if (i > 0) Hairline(66.dp)
                    ListRow(h.title, h.sub, icon = h.icon, onClick = { open(h) })
                }
            }
        }
    }
}
