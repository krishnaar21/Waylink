package app.waylink.ui.rides

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.Trip
import app.waylink.core.clock
import app.waylink.core.dMed
import app.waylink.core.dur
import app.waylink.core.tShort
import app.waylink.core.toGPX
import app.waylink.core.vehicleTitle
import app.waylink.data.AppViewModel
import app.waylink.data.Live
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Input
import app.waylink.ui.components.Txt
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

private fun clip(t: String, n: Int = 18) = if (t.length > n) t.take(n - 1).trimEnd() + "…" else t

/** Ride sheets: "trip-detail:<id>", "trip-rename:<id>", "rec-end". */
fun rideSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav): SheetSpec? {
    val arg = key.substringAfter(':', "")
    val v = s.vehicle
    return when (key.substringBefore(':')) {
        "trip-detail" -> {
            val t = v.trips.firstOrNull { it.id == arg } ?: return null
            SheetSpec(t.title, "№${t.n.toString().padStart(3, '0')} · ${dMed(t.start)} · ${tShort(t.start)} to ${tShort(t.end)}") { TripBody(s, t, vm, nav) }
        }
        "trip-rename" -> {
            val t = v.trips.firstOrNull { it.id == arg } ?: return null
            SheetSpec("Name this ride", "Ride") { RenameBody(t, vm, nav) }
        }
        "rec-end" -> {
            val r = live.rec ?: return null
            SheetSpec("End this ride?", "${clip(vehicleTitle(s))} · recording") {
                val c = W.c
                val f = Fmt(s.country.locale)
                val short = r.km < 0.1
                Spacer(Modifier.height(16.dp))
                Stat3(
                    Triple("Distance", f.num(r.km, 1), "km"),
                    Triple("Time", clock((System.currentTimeMillis() - r.start) / 1000.0), ""),
                    Triple("Top", f.num(r.top), "km/h"),
                )
                Spacer(Modifier.height(14.dp))
                Txt(if (short) "This ride is under 100 m, so it won’t be kept." else "Saving adds ${f.num(r.km, 1)} km to the odometer and both trips.", T.lead, c.ink2)
                Spacer(Modifier.height(20.dp))
                Btn(if (short) "End ride" else "Save ride", { vm.stopRecording(true); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.ACCENT, icon = "check")
                Spacer(Modifier.height(8.dp))
                Btn("Keep riding", nav::close, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
                if (!short) {
                    Spacer(Modifier.height(8.dp))
                    Btn("Discard ride", { vm.stopRecording(false); nav.close() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
                }
            }
        }
        else -> null
    }
}

/** .stat3 on a raised background: three label / value / unit cells. */
@Composable
private fun Stat3(vararg cells: Triple<String, String, String>) {
    val c = W.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.raised).height(IntrinsicSize.Min)) {
        cells.forEachIndexed { i, (label, value, unit) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Txt(label, T.eyebrow, c.ink3, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Txt(value, T.big(if (value.length > 5) 26f else 30f), c.ink, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Txt(unit.ifEmpty { " " }, T.unit, c.ink3, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TripBody(s: AppState, t: Trip, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val ctx = LocalContext.current
    val f = Fmt(s.country.locale)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        if (uri != null) try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(toGPX(t).toByteArray(Charsets.UTF_8)) }
            vm.toast("Saved the GPX file.")
        } catch (e: Exception) { vm.toast("Couldn’t save the file.") }
    }
    Spacer(Modifier.height(16.dp))
    Sketch(t.pts, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)).background(c.bg), large = true)
    Spacer(Modifier.height(16.dp))
    Stat3(
        Triple("Distance", f.num(t.km, 1), "km"),
        Triple("Time", if (t.sec < 3600) "${Math.round(t.sec / 60)}" else dur(t.sec), if (t.sec < 3600) "min" else ""),
        Triple("Average", f.num(t.avg), "km/h"),
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("Top speed", T.body.copy(fontSize = 15.5.sp), c.ink, Modifier.weight(1f))
        Txt("${f.num(t.top)} km/h", T.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium), c.ink)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("GPX", { save.launch("waylink-ride-${t.n}.gpx") }, Modifier.weight(1f), kind = BtnKind.LINE, icon = "down", small = true)
        Btn("Rename", { nav.open("trip-rename:${t.id}") }, Modifier.weight(1f), kind = BtnKind.LINE, icon = "edit", small = true)
        Btn("", { vm.deleteTrip(t.id); nav.close() }, Modifier.width(48.dp), kind = BtnKind.DANGER, icon = "trash", small = true)
    }
}

@Composable
private fun RenameBody(t: Trip, vm: AppViewModel, nav: SheetNav) {
    var name by rememberSaveable(t.id) { mutableStateOf(t.title) }
    FormField("Name") { Input(name, { name = it }, placeholder = t.title, label = "Ride name", maxLength = 60) }
    Spacer(Modifier.height(24.dp))
    Btn("Save", { vm.renameTrip(t.id, name); nav.back() }, Modifier.fillMaxWidth())
}
