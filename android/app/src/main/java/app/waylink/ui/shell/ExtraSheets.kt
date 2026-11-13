package app.waylink.ui.shell

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.waylink.core.AppState
import app.waylink.core.Due
import app.waylink.core.Fmt
import app.waylink.core.dFull
import app.waylink.core.dShort
import app.waylink.core.fuelStats
import app.waylink.core.rowValue
import app.waylink.core.sortedService
import app.waylink.core.tShort
import app.waylink.core.vehicleTitle
import app.waylink.data.AppViewModel
import app.waylink.data.Live
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Chip
import app.waylink.ui.components.DateField
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.logbook.SheetNav
import app.waylink.ui.logbook.SheetSpec
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun clip(t: String, n: Int = 18) = if (t.length > n) t.take(n - 1).trimEnd() + "…" else t

/** What the extra sheets need from the screen around them. */
interface ExtraHost {
    /** Runs [then] once location is allowed (asks the first time). */
    fun withLocation(then: () -> Unit)
    fun startRec()
    fun rideMode()
}

private val DOC_NAMES = mapOf("rc" to "Registration (RC)", "insurance" to "Insurance", "puc" to "Pollution (PUC)", "licence" to "Driving licence", "other" to "Other")

/** "parking", "glovebox", "doc:<slot>", "quick". */
fun extraSheet(key: String, s: AppState, live: Live, vm: AppViewModel, nav: SheetNav, host: ExtraHost): SheetSpec? {
    val arg = key.substringAfter(':', "")
    val v = s.vehicle
    return when (key.substringBefore(':')) {
        "parking" -> SheetSpec("Parked bike", clip(vehicleTitle(s))) {
            val c = W.c
            val ctx = LocalContext.current
            val f = Fmt(s.country.locale)
            val p = v.parking
            Spacer(Modifier.height(8.dp))
            if (p != null) {
                Txt("Saved ${f.ago(p.ts)}, at ${tShort(p.ts)}.", T.lead, c.ink2)
                Txt("%.5f, %.5f".format(java.util.Locale.ROOT, p.lat, p.lng), T.plate.copy(fontSize = 12.sp), c.ink4, Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(20.dp))
                Btn("Walk back with Google Maps", {
                    val url = "https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}&travelmode=walking"
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { vm.toast("No app can open maps.") }
                }, Modifier.fillMaxWidth(), icon = "map")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Update", { host.withLocation { vm.saveParking() } }, Modifier.weight(1f), kind = BtnKind.LINE)
                    Btn("Clear", { vm.clearParking() }, Modifier.weight(1f), kind = BtnKind.LINE)
                }
            } else {
                Txt("Saves where you are now, so you can walk back to it.", T.lead, c.ink2)
                Spacer(Modifier.height(24.dp))
                Btn("Save where I parked", { host.withLocation { vm.saveParking() } }, Modifier.fillMaxWidth(), icon = "pin")
            }
        }
        "glovebox" -> SheetSpec("Digital glovebox", "Papers · ${clip(vehicleTitle(s))}") {
            val c = W.c
            val title = vehicleTitle(s)
            Spacer(Modifier.height(8.dp))
            Txt("Copies for when there’s no signal. Kept only on this phone, and never in backups.", T.lead, c.ink2)
            Spacer(Modifier.height(16.dp))
            ListCard(flat = true) {
                listOf("${v.id}:rc" to title, "${v.id}:insurance" to title, "${v.id}:puc" to title, "rider:licence" to "You", "${v.id}:other" to title).forEachIndexed { i, (slot, who) ->
                    if (i > 0) Hairline(66.dp)
                    val d = s.glove.firstOrNull { it.slot == slot }
                    val exp = d?.expiry
                    val now = System.currentTimeMillis()
                    val desc = when {
                        d == null -> "Not added · $who"
                        exp != null -> (if (exp < now) "Expired " else "Valid till ") + dFull(exp)
                        else -> "Added ${dShort(d.added)}"
                    }
                    ListRow(DOC_NAMES[slot.substringAfter(':')] ?: "Document", desc, icon = "doc", onClick = { nav.open("doc:$slot") }, trailing = {
                        if (d != null) Box(Modifier.size(8.dp).clip(CircleShape).background(if (exp != null && exp - now < 30L * 86_400_000) c.red else c.sage))
                    })
                }
            }
        }
        "doc" -> SheetSpec(DOC_NAMES[arg.substringAfter(':')] ?: "Document", "Glovebox") { DocBody(s, arg, vm, nav) }
        "quick" -> SheetSpec("What now?", "${clip(vehicleTitle(s))} · ${Fmt(s.country.locale).short(v.odo)} km") { QuickBody(s, live, vm, nav, host) }
        else -> null
    }
}

@Composable
private fun DocBody(s: AppState, slot: String, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val ctx = LocalContext.current
    val d = s.glove.firstOrNull { it.slot == slot }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.addDoc(slot, uri) }
    var img by remember(d?.added) { mutableStateOf<ImageBitmap?>(null) }
    if (d != null && d.type.startsWith("image/")) LaunchedEffect(d.added) {
        img = withContext(Dispatchers.IO) {
            try {
                val file = vm.docFile(slot)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                var sample = 1
                while (maxOf(o.outWidth, o.outHeight) / sample > 1600) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            } catch (e: Exception) { null }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (d == null) Txt("Add a clear photo or a PDF.", T.lead, c.ink2)
    else if (d.type.startsWith("image/")) {
        val b = img
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(16.dp)).background(c.raised), contentAlignment = Alignment.Center) {
            if (b != null) Image(b, DOC_NAMES[slot.substringAfter(':')], Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            else Txt("Loading…", T.row, c.ink3)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.raised).pressable(label = "Open ${d.name}") {
                try {
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", vm.docFile(slot))
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: Exception) { vm.toast("No app can open this file.") }
            }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic("doc", size = 20.dp, tint = c.ink2)
            Spacer(Modifier.width(12.dp))
            Txt(d.name, T.body, c.ink, Modifier.weight(1f), maxLines = 1)
            Txt("Open", T.link, c.ink2)
        }
    }
    Spacer(Modifier.height(16.dp))
    Btn(if (d != null) "Replace" else "Choose photo or PDF", { pick.launch(arrayOf("image/*", "application/pdf")) }, Modifier.fillMaxWidth(), kind = if (d != null) BtnKind.LINE else BtnKind.INK, icon = "up")
    if (d != null) {
        FormField("Valid till") {
            val e = d.expiry
            if (e == null) Btn("Add the date", { vm.setDocExpiry(slot, System.currentTimeMillis() + 365L * 86_400_000) }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "plus")
            else Column {
                DateField(e, { vm.setDocExpiry(slot, it) }, label = "Valid till")
                Txt("Remove the date", T.link, c.ink3, Modifier.padding(top = 10.dp).pressable(label = "Remove the date") { vm.setDocExpiry(slot, null) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Btn("Remove", { vm.deleteDoc(slot); nav.back() }, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
    }
}

@Composable
private fun QuickBody(s: AppState, live: Live, vm: AppViewModel, nav: SheetNav, host: ExtraHost) {
    val c = W.c
    val f = Fmt(s.country.locale)
    val v = s.vehicle
    val lastFill = v.fuel.maxByOrNull { it.ts }
    val due = sortedService(v).filter { it.st.status != Due.OK }.take(2)
    val rec = live.rec
    val park = v.parking
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QTile("fuel", c.amber, "Log fuel", lastFill?.let { "Last: ${dShort(it.ts)}" } ?: "At the pump", Modifier.weight(1f)) { nav.open("fuel:new") }
        if (rec != null) QTile("stop", c.red, "End ride", "${f.num(rec.km, 1)} km so far", Modifier.weight(1f)) { nav.open("rec-end") }
        else QTile("play", c.accent, "Record a ride", "Uses phone GPS", Modifier.weight(1f)) { nav.close(); host.startRec() }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QTile("speed", c.ink, "Ride mode", "Big speed, next turn", Modifier.weight(1f)) { nav.close(); host.rideMode() }
        QTile("pin", c.sageInk, "Save parking", park?.let { "Saved ${f.ago(it.ts)}" } ?: "Find it later", Modifier.weight(1f)) { host.withLocation { vm.saveParking() } }
    }
    if (due.isNotEmpty()) {
        Txt("Due on this bike", T.eyebrow, c.ink3, Modifier.padding(top = 24.dp, bottom = 10.dp))
        due.forEach { r ->
            val rv = rowValue(r.st, f)
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).card(16.dp, c.raised).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val red = r.st.status == Due.OVERDUE
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (red) c.tintRed else c.tintAmber), contentAlignment = Alignment.Center) {
                    Ic(r.item.icon.ifEmpty { "wrench" }, size = 17.dp, tint = if (red) c.red else c.amber)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(r.item.name, T.body.copy(fontSize = 15.sp), c.ink, maxLines = 1)
                    Txt("${rv.v} ${rv.u}", T.row, if (rv.red) c.red else c.ink3)
                }
                Chip("Done", { vm.serviceDone(r.item.id) }, icon = "check")
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.tintRed).pressable(label = "Emergency SOS") { nav.open("sos") }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic("sos", size = 20.dp, tint = c.red)
        Spacer(Modifier.width(12.dp))
        Txt("Emergency SOS", T.body.copy(fontWeight = FontWeight.SemiBold), c.red)
        Txt(" · ${s.country.amb} ambulance", T.row, c.red.copy(alpha = 0.75f), Modifier.weight(1f))
        Ic("chev", size = 14.dp, tint = c.red)
    }
}

@Composable
private fun QTile(icon: String, tone: Color, title: String, desc: String, modifier: Modifier, onClick: () -> Unit) {
    val c = W.c
    Column(modifier.card(18.dp, c.raised).pressable(label = title, onClick = onClick).padding(14.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Ic(icon, size = 18.dp, tint = tone)
        }
        Spacer(Modifier.height(12.dp))
        Txt(title, T.rowTitle, c.ink, maxLines = 1)
        Txt(desc, T.rowSub, c.ink3, maxLines = 1)
    }
}
