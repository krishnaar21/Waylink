package app.waylink.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.dFull
import app.waylink.core.dMonthYear
import app.waylink.core.shortName
import app.waylink.ui.components.Avatar
import app.waylink.ui.components.CircleBtn
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Page
import app.waylink.ui.components.SectionGap
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** The Profile page, a port of profile() in web-v0/js/views.js. */
@Composable
fun ProfileScreen(s: AppState, back: () -> Unit, sheet: (String) -> Unit, help: (String) -> Unit, bottom: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val p = s.profile
    val totalKm = s.vehicles.sumOf { it.odo }
    val rides = s.vehicles.sumOf { it.trips.size }
    Page(bottom) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { CircleBtn("back", "Back", back) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pressable(label = "Change picture") { sheet("avatar") }) {
                Avatar(p.avatar, size = 76.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(30.dp).clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Ic("edit", size = 14.dp, tint = c.ink) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Txt(shortName(p.name).ifEmpty { "Your profile" }, T.title, c.ink, Modifier.semantics { heading() }, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Txt("Rider since ${dMonthYear(if (p.since > 0) p.since else System.currentTimeMillis())}", T.subtitle, c.ink3)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().card(20.dp).height(IntrinsicSize.Min)) {
            listOf(Triple("Bikes", "${s.vehicles.size}", ""), Triple("Ridden", f.short(totalKm), "km"), Triple("Rides", f.short(rides.toDouble()), "")).forEachIndexed { i, (k, v, u) ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                Column(Modifier.weight(1f).padding(16.dp)) {
                    Txt(k, T.eyebrow, c.ink3, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(v, T.big(28f), c.ink, maxLines = 1)
                        if (u.isNotEmpty()) Txt(" $u", T.unit, c.ink3, Modifier.padding(bottom = 3.dp))
                    }
                }
            }
        }

        ProfCard("About you", null, { sheet("prof:about") }, listOf("Name" to p.name, "Phone" to p.phone, "Email" to p.email, "City" to p.city), "Add")
        val exp = p.licenceExpiry
        ProfCard("Driving licence", null, { sheet("prof:licence") }, listOf("Licence number" to p.licenceNo, "Valid till" to (exp?.let { dFull(it) } ?: "")), "Add")
        if (exp != null && exp - System.currentTimeMillis() < 60L * 86_400_000) {
            Txt(
                (if (exp < System.currentTimeMillis()) "Your licence has expired." else "Your licence expires soon.") + if (s.country.code == "IN") " Renew it on Parivahan." else "",
                T.row.copy(fontSize = 13.5.sp), c.red, Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        ProfCard(
            "Medical ID", { help("medical") }, { sheet("prof:medical") },
            listOf("Blood group" to p.blood, "Allergies & notes" to p.medical, "Emergency contact" to listOf(p.iceName, p.icePhone).filter { it.isNotBlank() }.joinToString(" · ")),
            "Add", noneFor = "Allergies & notes",
        )

        SectionGap()
        Row(
            Modifier.fillMaxWidth().border(1.5.dp, c.inkA(0.16f), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic("lock", size = 17.dp, tint = c.ink3)
            Spacer(Modifier.width(12.dp))
            Txt("Your profile stays on this phone. It’s in your backup, and nowhere else.", T.row, c.ink2)
        }
    }
}

@Composable
private fun ProfCard(title: String, help: (() -> Unit)?, onEdit: () -> Unit, rows: List<Pair<String, String>>, empty: String, noneFor: String? = null) {
    val c = W.c
    SectionGap()
    SectionHead(title, help = help, right = { Txt("Edit", T.link, c.ink2, Modifier.pressable(label = "Edit $title", onClick = onEdit)) })
    Column(Modifier.fillMaxWidth().card(20.dp).pressable(label = title, onClick = onEdit).padding(horizontal = 18.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
                Txt(k, T.row.copy(fontSize = 15.sp), c.ink3)
                Spacer(Modifier.width(16.dp))
                Txt(
                    v.ifBlank { if (k == noneFor) "None" else empty },
                    T.row.copy(fontSize = 15.sp, textAlign = TextAlign.End), if (v.isBlank()) c.ink4 else c.ink, Modifier.weight(1f), maxLines = 3,
                )
            }
        }
    }
}
