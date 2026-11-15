package app.waylink.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.Maneuvers
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Txt
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * A sample of the bike's display: the dark LCD with its status strip (time, signal, battery, link) and one
 * screen — a turn, a call, or missed calls. It's a picture of the idea, not the exact cluster artwork,
 * which differs by model.
 */
@Composable
fun ClusterSample(kind: String, modifier: Modifier = Modifier, street: Boolean = true) {
    val lcd = Color(0xFF0E1114); val ink = Color(0xFFE9EEF2); val dim = Color(0xFF7C8791); val amber = Color(0xFFFFB547)
    val mono = TextStyle(fontFamily = FontFamily.Monospace)
    val what = when (kind) { "call" -> "an incoming call"; "missed" -> "missed calls"; else -> "the next turn" }
    Column(
        modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF26292D)).padding(7.dp)
            .clip(RoundedCornerShape(16.dp)).background(lcd).border(1.dp, Color(0xFF30363C), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "Sample of the bike display showing $what" },
    ) {
        // Status strip: what the phone sends every few seconds.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("10:42", mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), ink)
            Spacer(Modifier.weight(1f))
            Bars(4, 4, ink, dim)
            Spacer(Modifier.width(8.dp))
            Battery(3, ink, dim)
            Spacer(Modifier.width(8.dp))
            Ic("bt", size = 12.dp, tint = Color(0xFF59A8FF))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (kind) {
                "call" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Ic("phone", size = 22.dp, tint = Color(0xFF4CD27A))
                    Spacer(Modifier.height(4.dp))
                    Txt("AMMA", mono.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp), ink)
                    Txt("INCOMING", mono.copy(fontSize = 10.sp, letterSpacing = 2.sp), dim)
                }
                "missed" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic("missed", size = 22.dp, tint = amber)
                    Spacer(Modifier.width(8.dp))
                    Txt("2 MISSED", mono.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), ink)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TurnArrow(Maneuvers.of("TURN_RIGHT").glyph, Modifier.size(44.dp, 54.dp), sw = 14f)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Txt("250", mono.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), ink)
                            Txt(" m", mono.copy(fontSize = 13.sp), dim, Modifier.padding(bottom = 4.dp))
                        }
                        if (street) Txt("BRIGADE RD", mono.copy(fontSize = 11.sp, letterSpacing = 1.sp), dim, maxLines = 1)
                    }
                }
            }
        }
        if (kind == "turn") Row {
            Txt("4.2 km", mono.copy(fontSize = 11.sp), dim)
            Spacer(Modifier.weight(1f))
            Txt("ETA 10:56", mono.copy(fontSize = 11.sp), dim)
        }
    }
}

@Composable
private fun Bars(n: Int, of: Int, on: Color, off: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        repeat(of) { i -> Box(Modifier.width(3.dp).height((4 + i * 2.5f).dp).background(if (i < n) on else off)) }
    }
}

@Composable
private fun Battery(bars: Int, on: Color, off: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.border(1.dp, on, RoundedCornerShape(2.dp)).padding(1.5.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            repeat(3) { i -> Box(Modifier.size(3.dp, 6.dp).background(if (i < bars) on else off)) }
        }
        Box(Modifier.size(1.5.dp, 3.dp).background(on))
    }
}

/** The sheet behind the bike-display samples: [kind] turn, call or all. */
@Composable
fun ClusterSheetBody(kind: String, streetNames: Boolean? = null, onStreetNames: () -> Unit = {}) {
    val c = W.c
    Spacer(Modifier.height(8.dp))
    Txt("A sample of what your bike’s display shows. The exact look differs by model.", T.sheetLead, c.ink2)
    val parts = if (kind == "all") listOf("turn", "call", "missed") else listOf(kind)
    parts.forEach { k ->
        Spacer(Modifier.height(16.dp))
        Txt(when (k) { "call" -> "Incoming call"; "missed" -> "Missed calls"; else -> "Next turn" }, T.eyebrow, c.ink3, Modifier.padding(start = 4.dp, bottom = 8.dp))
        ClusterSample(k, street = streetNames ?: true)
        if (k == "turn" && streetNames != null) {
            Spacer(Modifier.height(8.dp))
            app.waylink.ui.components.ListCard(flat = true) {
                app.waylink.ui.components.ListRow("Street names on the bike", "Road name under the distance", icon = "eye", trailing = {
                    app.waylink.ui.components.Switch(streetNames, "Street names on the bike", onStreetNames)
                })
            }
        }
        Txt(
            when (k) {
                "call" -> "The caller’s name from your contacts. A handlebar tap answers, a long press declines."
                "missed" -> "A count only. Names and messages stay on the phone."
                else -> "The arrow, the distance to it, what’s left and when you’ll arrive. The street shows if Street names is on."
            },
            T.row.copy(fontSize = 13.5.sp, lineHeight = 21.sp), c.ink3, Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Txt("Along the top: time, your phone’s signal and battery, and the Bluetooth link.", T.row.copy(fontSize = 13.5.sp, lineHeight = 21.sp), c.ink3, Modifier.padding(horizontal = 4.dp))
}
