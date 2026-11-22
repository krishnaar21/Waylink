package app.waylink.ui.logbook

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.Fmt
import app.waylink.core.dShort
import app.waylink.core.sortedService
import app.waylink.core.vehicleTitle
import app.waylink.core.verdictOf
import app.waylink.ui.components.BigValue
import app.waylink.ui.components.CheckCircle
import app.waylink.ui.components.CircleBtn
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hero
import app.waylink.ui.components.Ic
import app.waylink.ui.components.IconTile
import app.waylink.ui.components.LinkText
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.Page
import app.waylink.ui.components.PageHead
import app.waylink.ui.components.SectionGap
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.ServiceRowItem
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** What the Logbook can do. Sheet keys follow the web app: "svc:<id>", "svc-edit:new", "rec:<id>", "odo". */
interface LogbookActions {
    fun back()
    fun sheet(key: String)
    fun help(key: String)
    fun toggleCheck(id: String)
    fun deleteCheck(id: String)
    fun addCheck(text: String)
}

/** The Logbook page, a port of logbook() in web-v0/js/views.js. */
@Composable
fun LogbookScreen(s: AppState, act: LogbookActions, bottom: Dp) {
    val c = W.c
    val f = remember(s.country.locale) { Fmt(s.country.locale) }
    val v = s.vehicle
    val list = remember(v) { sortedService(v) }
    val verdict = remember(list) { verdictOf(list) }
    val cur = s.country.cur

    Page(bottom) {
        PageHead("Service", "Jobs and visits for ${clip(vehicleTitle(s), 24)}", onBack = act::back) {
            CircleBtn("plus", "Add reminder", { act.sheet("svc-edit:new") })
        }
        Hero(verdict, verdict.line)

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().card(20.dp).pressable(label = "Odometer, edit") { act.sheet("odo") }.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Odometer now", T.eyebrow, c.ink3, Modifier.weight(1f))
            BigValue(f.num(Math.floor(v.odo)), "km", 30f)
            Spacer(Modifier.width(8.dp))
            Ic("edit", size = 18.dp, tint = c.ink4)
        }
        if (app.waylink.ui.components.DummyAds.top(s)) app.waylink.ui.components.AdBanner(2, Modifier.padding(top = 12.dp)) { act.sheet(app.waylink.ui.components.DummyAds.REMOVE) }

        SectionGap()
        SectionHead("Reminders · most urgent first", help = { act.help("reminders") })
        ListCard {
            if (list.isEmpty()) Empty("No reminders yet. Tap + to add one.")
            list.forEachIndexed { i, r ->
                if (i > 0) Hairline(start = 20.dp)
                ServiceRowItem(r, f) { act.sheet("svc:${r.item.id}") }
            }
        }

        SectionGap()
        SectionHead("Workshop visits", help = { act.help("visits") }) { LinkText("Add visit", { act.sheet("rec:new") }) }
        if (v.records.isEmpty()) ListCard { Empty("Log each visit. A full history helps at resale.") }
        else {
            ListCard {
                val ads = app.waylink.ui.components.DummyAds.lists(s)
                v.records.sortedByDescending { it.date }.forEachIndexed { i, r ->
                    if (ads && i == 3) { Hairline(start = 0.dp); app.waylink.ui.components.AdRow(2, start = 16.dp) }
                    if (i > 0) Hairline(start = 16.dp)
                    Row(
                        Modifier.fillMaxWidth().pressable(label = r.title) { act.sheet("rec:${r.id}") }.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile("wrench", size = 40.dp, iconSize = 19.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Txt(r.title, T.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Medium, lineHeight = 20.15.sp), c.ink, Modifier.weight(1f), maxLines = 1)
                                if (r.cost > 0) Txt(cur + f.short(r.cost), T.rowTitle, c.ink, Modifier.padding(start = 12.dp))
                            }
                            Txt("${dShort(r.date)} · ${f.short(r.odo)} km", T.rowSub, c.ink3, maxLines = 1)
                        }
                    }
                }
                if (ads && v.records.size <= 3) { Hairline(start = 0.dp); app.waylink.ui.components.AdRow(2, start = 16.dp) }
            }
            app.waylink.core.serviceSpend(v.records)?.let { sp -> SpendChart(sp, cur, f) }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp)) {
                Txt("Spent on service", T.row, c.ink3, Modifier.weight(1f))
                Txt(cur + f.num(v.records.sumOf { it.cost }), T.row.copy(fontWeight = FontWeight.Medium), c.ink)
            }
        }

        SectionGap()
        SectionHead("For the mechanic", help = { act.help("mechanic") }) {
            Txt("${v.checklist.count { !it.done }} open", T.eyebrow.copy(fontSize = 14.sp), c.ink3)
        }
        ListCard {
            v.checklist.forEachIndexed { i, x ->
                if (i > 0) Hairline(start = 16.dp)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(x.done, x.text) { act.toggleCheck(x.id) }
                    Spacer(Modifier.width(14.dp))
                    Txt(
                        x.text, T.body.copy(lineHeight = 22.4.sp, textDecoration = if (x.done) TextDecoration.LineThrough else null),
                        if (x.done) c.ink3 else c.ink, Modifier.weight(1f),
                    )
                    CircleBtn("x", "Remove ${x.text}", { act.deleteCheck(x.id) }, size = 36.dp, flat = true, tint = c.ink2)
                }
            }
            if (v.checklist.isNotEmpty()) Hairline(start = 16.dp)
            AddCheck(act::addCheck)
        }
    }
}

@Composable
private fun Empty(text: String) =
    Txt(text, T.row.copy(fontSize = 14.5.sp, lineHeight = 21.75.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), W.c.ink3, Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 30.dp))

/** The last checklist row: a faint tick and an inline field. Return adds the note and keeps focus. */
@Composable
private fun AddCheck(onAdd: (String) -> Unit) {
    val c = W.c
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckCircleFaint()
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Txt("A rattle, a squeak, a part to fix…", T.body.copy(fontSize = 15.5.sp), c.ink4, maxLines = 1)
            BasicTextField(
                value = text, onValueChange = { text = it.take(80) }, singleLine = true,
                textStyle = T.body.copy(fontSize = 15.5.sp, color = c.ink), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text); text = "" } }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Add a note for the mechanic" },
            )
        }
    }
}

@Composable
private fun CheckCircleFaint() =
    Box(Modifier.size(24.dp).border(1.5.dp, W.c.ink4.copy(alpha = 0.6f), CircleShape))

private fun clip(t: String, n: Int) = if (t.length > n) t.take(n - 1).trimEnd() + "…" else t


/** Service spend as bars (by month or year), with the total and cost per km. */
@Composable
private fun SpendChart(sp: app.waylink.core.Spend, cur: String, f: app.waylink.core.Fmt) {
    val c = W.c
    val max = sp.buckets.maxOf { it.second }.coerceAtLeast(1.0)
    val accent = c.accent; val track = c.inkA(0.06f)
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(c.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Txt(if (sp.monthly) "Spend by month" else "Spend by year", T.eyebrow, c.ink3)
                Txt(cur + f.num(sp.total), T.big(28f), c.ink)
            }
            sp.perKm?.let { Txt("$cur${f.num(it, 2)} per km", T.rowSub, c.ink3) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            sp.buckets.forEach { (_, v) ->
                androidx.compose.foundation.Canvas(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = cur + f.num(v) }) {
                    val r = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    drawRoundRect(track, cornerRadius = r)
                    val h = (size.height * (v / max)).toFloat()
                    if (v > 0) drawRoundRect(accent, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = r)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sp.buckets.forEach { (k, _) -> Txt(k, T.rowSub.copy(fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), c.ink3, Modifier.weight(1f), maxLines = 1) }
        }
    }
}
