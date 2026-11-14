package app.waylink.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Due
import app.waylink.core.Fmt
import app.waylink.core.NavSource
import app.waylink.core.Platform
import app.waylink.core.fuelStats
import app.waylink.core.sortedService
import app.waylink.core.verdictOf
import app.waylink.data.LinkState
import app.waylink.data.Live
import app.waylink.data.Weather
import app.waylink.ui.components.IconTile
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlin.math.roundToInt

/**
 * Quick links on Today, a grid of every place worth one tap. To drop a tile, take its key out of [ORDER];
 * to move one, move its key. [go] targets: "tab:<key>", "page:<key>", "record", "ride", else a sheet key.
 */
object QuickLinks {
    // Most used first: fuel and service every week, parking and pumps most rides, then the planning
    // tiles, the paperwork, and the safety pair kept together at the end.
    val ORDER = listOf(
        "log-fuel", "service", "parking",
        "pumps", "weather", "places",
        "speed", "papers", "visit",
        "hospitals", "sos", "profile",
    )

    data class Link(val key: String, val icon: String, val title: String, val sub: String, val go: String, val dot: Boolean? = null)

    /** The tile for [key] with its live subtitle, or null when it doesn't apply on this phone. */
    fun build(key: String, s: AppState, live: Live, f: Fmt, needPerms: Int): Link? {
        val v = s.vehicle
        return when (key) {
            "ride" -> Link(key, "speed", "Ride mode", if (live.link == LinkState.CONNECTED) "Bike linked" else "Big, glanceable", "ride")
            "record" -> live.rec?.let { Link(key, "stop", "Recording", "${f.num(it.km, 1)} km so far", "tab:trips", true) }
                ?: Link(key, "play", "Record ride", "Log this ride", "record")
            "route" -> Link(key, "route", "Route", when {
                live.nav != null -> "En route"
                Platform.caps.readNavApps && NavSource.of(s.settings.navSource).readsNotifications -> "Turns from ${NavSource.of(s.settings.navSource).name}"
                else -> "Plan a ride"
            }, "tab:route", live.nav?.let { true })
            "places" -> Link(key, "home", "Saved places", if (s.places.isEmpty()) "Add home, work" else "${s.places.size} saved", "tab:route")
            "log-fuel" -> {
                val fs = fuelStats(v)
                Link(key, "fuel", "Log fuel", fs.range?.let { "≈${f.num(it)} km left" } ?: "Add a fill-up", "fuel:new", if (fs.range != null && fs.onReserve) false else null)
            }
            "pumps" -> Link(key, "fuel", "Petrol pumps", "Nearest first", "nearby:fuel")
            "hospitals" -> Link(key, "hospital", "Hospitals", "Nearest first", "nearby:hospital")
            "sos" -> Link(key, "sos", "SOS", "Emergency · ${s.country.name}", "sos")
            "parking" -> Link(key, "parking", "Parked bike", v.parking?.let { "Saved ${f.ago(it.ts)}" } ?: "Save the spot", "parking")
            "service" -> {
                val vd = verdictOf(sortedService(v))
                Link(key, "wrench", "Service", if (vd.over + vd.soon > 0) "${vd.over + vd.soon} need care" else "All in order", "page:logbook",
                    if (vd.over > 0) false else if (vd.soon > 0) null else true)
            }
            "add-item" -> Link(key, "plus", "Service item", "Add a reminder", "svc-edit:new")
            "visit" -> Link(key, "doc", "Workshop visit", "Log what was done", "rec:new")
            "odo" -> Link(key, "gauge", "Odometer", "${f.num(Math.round(v.odo).toDouble())} km", "odo")
            "trip-a" -> Link(key, "progress", "Trip A", "${f.num(v.tripA.km, 1)} km", "trip:A")
            "rides" -> Link(key, "trips", "Rides", "${v.trips.size} logged", "tab:trips")
            "alerts" -> {
                val n = s.alerts.missed.size + s.alerts.wa.size
                Link(key, "alerts", "Alerts", if (n > 0) "$n new" else "All clear", "tab:alerts", if (n > 0) false else null)
            }
            "papers" -> {
                val n = s.glove.count { it.slot.startsWith(v.id) || it.slot.startsWith("rider:") }
                Link(key, "folder", "Papers", if (n > 0) "$n saved" else "RC, insurance, PUC", "glovebox")
            }
            "weather" -> Link(key, "wxPart", "Weather", (live.weather as? Weather.State.Ready)?.data?.let { "${it.temp.roundToInt()}° · ${it.place}" } ?: "Next hours", "weather")
            "garage" -> Link(key, "garage", "Garage", if (s.vehicles.size == 1) "1 vehicle" else "${s.vehicles.size} vehicles", "garage")
            "bike-link" -> if (!Platform.caps.bluetooth) null else Link(key, "bt", "Bike link", when (live.link) {
                LinkState.CONNECTED -> "Connected"; LinkState.IDLE -> if (v.bike.id != null) "Paired" else "Pair once"; else -> "Linking…"
            }, "pair", if (live.link == LinkState.CONNECTED) true else null)
            "speed" -> Link(key, "alert", "Speed alert", if (s.settings.speedAlert > 0) "${s.settings.speedAlert} km/h" else "Off", "speed")
            "turn-test" -> Link(key, "turnRight", "Test a turn", "Check the arrow", "turn-test")
            "profile" -> Link(key, "user", "Rider", "Blood group, contacts", "page:profile")
            "perms" -> Link(key, "shield", "Permissions", if (needPerms == 0) "All set" else "$needPerms needed", "perms", if (needPerms > 0) false else null)
            else -> null
        }
    }
}

/** Three to a row. */
@Composable
fun QuickLinksGrid(s: AppState, live: Live, f: Fmt, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val tick = app.waylink.ui.route.rememberResumeTick()
    val need = remember(tick, s.settings.navSource) { app.waylink.ui.route.Perms.needed(ctx, s) }
    val links = QuickLinks.ORDER.mapNotNull { QuickLinks.build(it, s, live, f, need) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        links.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { l -> QuickTile(l, Modifier.weight(1f)) { go(l.go) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickTile(l: QuickLinks.Link, modifier: Modifier, onClick: () -> Unit) {
    val c = W.c
    Column(modifier.height(104.dp).card(18.dp).pressable(label = "${l.title}, ${l.sub}", onClick = onClick).padding(12.dp)) {
        Box {
            IconTile(l.icon, size = 34.dp, radius = 11.dp, iconSize = 18.dp, bg = c.inkA(0.07f))
            if (l.dot != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 1.dp).size(9.dp).clip(CircleShape).background(if (l.dot) c.sage else c.red))
        }
        Spacer(Modifier.weight(1f))
        Txt(l.title, T.rowTitle.copy(fontSize = 14.sp, lineHeight = 18.sp), c.ink, maxLines = 1)
        Txt(l.sub, T.rowSub.copy(fontSize = 12.sp, lineHeight = 16.sp), c.ink3, maxLines = 1)
    }
}
