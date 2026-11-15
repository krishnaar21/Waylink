package app.waylink.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.waylink.core.AppState
import app.waylink.core.Finder
import app.waylink.core.Found
import app.waylink.core.NavSource
import app.waylink.data.NavApps
import app.waylink.ui.components.Chip
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Ic
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Txt
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/**
 * The search box on Today. Empty and focused, it offers questions to ask; typing shows results grouped by
 * kind, with a direct answer on top when the question has one; nothing found offers words to try. The
 * matching is [Finder], all on the phone. [go] takes the same keys as Quick Actions.
 */
@Composable
fun SearchBar(s: AppState, go: (String) -> Unit) {
    val c = W.c
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var q by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val groups = remember(q, s) { Finder.search(s, q) }
    val close = { focus.clearFocus(); q = "" }
    val open = { h: Found ->
        close()
        if (h.go.startsWith("place-go:")) {
            val p = s.places.firstOrNull { it.id == h.go.substringAfter(':') }
            val src = NavSource.of(s.settings.navSource).let { if (it.readsNotifications) it else NavSource.of("gmaps") }
            if (p != null) NavApps.go(ctx, src, p.address.ifBlank { p.name }, p.lat, p.lng)
        } else go(h.go)
    }
    BackHandler(enabled = focused || q.isNotEmpty()) { close() }
    val shape = RoundedCornerShape(16.dp)
    Column {
        BasicTextField(
            q, { q = it.take(80) }, singleLine = true,
            textStyle = T.input.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                val first = groups.firstOrNull()?.hits?.firstOrNull()
                if (first != null) open(first) else focus.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().height(48.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = "Search Waylink" },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(shape).background(c.card)
                        .border(if (focused) 1.5.dp else 1.dp, if (focused) c.ink.copy(alpha = 0.5f) else c.line, shape).padding(start = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        val r = size.minDimension * 0.34f; val cx = size.width * 0.42f; val cy = size.height * 0.42f
                        drawCircle(c.ink3, r, Offset(cx, cy), style = Stroke(1.8.dp.toPx()))
                        drawLine(c.ink3, Offset(cx + r * 0.72f, cy + r * 0.72f), Offset(size.width * 0.92f, size.height * 0.92f), 1.8.dp.toPx(), StrokeCap.Round)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Txt("Ask or search: service, fuel, papers…", T.input, c.ink4, maxLines = 1)
                        inner()
                    }
                    if (q.isNotEmpty()) Box(Modifier.size(32.dp).clip(CircleShape).pressable(label = "Clear search", scale = 0.9f) { q = "" }, contentAlignment = Alignment.Center) {
                        Ic("x", size = 16.dp, tint = c.ink3)
                    }
                }
            },
        )
        when {
            q.isBlank() && focused -> {
                Label("Try asking")
                ListCard {
                    Finder.SUGGESTIONS.forEachIndexed { i, (text, icon) ->
                        if (i > 0) Hairline(66.dp)
                        ListRow(text, icon = icon, chevron = false, onClick = { q = text })
                    }
                }
            }
            q.isNotBlank() && groups.isEmpty() -> {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(16.dp)) {
                    Txt("No results for “${q.trim()}”", T.rowTitle, c.ink)
                    Txt("Search looks through Waylink’s screens, settings and what you’ve saved. Try a simpler word:", T.rowSub, c.ink3, Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Finder.TRY.forEach { w -> Chip(w, { q = w }) }
                    }
                }
            }
            q.isNotBlank() -> groups.forEach { g ->
                Label(g.name)
                ListCard {
                    g.hits.forEachIndexed { i, h ->
                        if (i > 0) Hairline(66.dp)
                        ListRow(h.title, h.sub, icon = h.icon, onClick = { open(h) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Txt(text, T.eyebrow, W.c.ink3, Modifier.padding(start = 6.dp, top = 16.dp, bottom = 8.dp).semantics { heading() })
}
