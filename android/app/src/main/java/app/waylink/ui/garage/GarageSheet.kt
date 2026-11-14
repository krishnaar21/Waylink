package app.waylink.ui.garage

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.waylink.core.AppState
import app.waylink.core.Catalog
import app.waylink.core.Fmt
import app.waylink.core.modelName
import app.waylink.core.vehicleTitle
import app.waylink.ui.components.BikeArt
import app.waylink.ui.components.Btn
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** "Your bikes": switch between vehicles. Each keeps its own fuel, rides and service. */
@Composable
fun GarageList(s: AppState, onPick: (String) -> Unit, onAdd: () -> Unit) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    Spacer(Modifier.height(16.dp))
    Column(Modifier.fillMaxWidth().card(20.dp, c.raised)) {
        s.vehicles.forEachIndexed { i, v ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(c.line))
            val on = v.id == s.active
            Row(
                Modifier.fillMaxWidth().semantics { selected = on }.pressable(label = vehicleTitle(s, v)) { onPick(v.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BikeArt(Modifier.size(width = 92.dp, height = 60.dp), 12.dp, v.colour, v.photo)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(vehicleTitle(s, v), T.body.copy(lineHeight = T.row.lineHeight), c.ink, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Txt("${modelName(s, Catalog.model(v.model))} · ${f.num(v.odo)} km", T.row, c.ink3, maxLines = 1)
                }
                if (on) Box(Modifier.size(20.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) { Ic("check", size = 12.dp, tint = c.bg) }
                else Ic("chev", size = 17.dp, tint = c.ink4)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Btn("Add a vehicle", onAdd, Modifier.fillMaxWidth(), icon = "plus")
    Spacer(Modifier.height(12.dp))
    Txt("Each bike keeps its own fuel, rides and service.", T.note.copy(fontSize = T.rowSub.fontSize, textAlign = TextAlign.Center), c.ink3, Modifier.fillMaxWidth())
}
