package app.waylink.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waylink.core.AppState
import app.waylink.data.AppViewModel
import app.waylink.ui.components.Avatar
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.Ic
import app.waylink.ui.components.SectionHead
import app.waylink.ui.components.Txt
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.Accent
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** A first slice of Settings: profile header, look (theme and accent), and erasing data. The rest follows. */
@Composable
fun SettingsScreen(s: AppState, vm: AppViewModel, bottom: Dp) {
    val c = W.c
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = bottom + 24.dp),
    ) {
        Txt("Settings", T.title, c.ink)
        Spacer(Modifier.height(3.dp))
        Txt("Profile, bikes and look", T.subtitle, c.ink3)
        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth().card(20.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.profile.avatar)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(s.profile.name.ifBlank { "Rider" }, T.cardTitle, c.ink, maxLines = 1)
                Txt("Rider profile", T.row, c.ink3)
            }
        }

        Spacer(Modifier.height(30.dp))
        SectionHead("Look")
        Column(Modifier.fillMaxWidth().card(20.dp).padding(16.dp)) {
            Txt("Theme", T.eyebrow, c.ink3)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("auto", "Auto", listOf(Color(0xFFEEE7DA), Color(0xFF1E1B17))),
                    Triple("sketch", "Paper", listOf(Color(0xFFEEE7DA), Color(0xFFEEE7DA))),
                    Triple("sketchdark", "Paper dark", listOf(Color(0xFF1E1B17), Color(0xFF1E1B17))),
                ).forEach { (key, label, sw) ->
                    val on = s.settings.theme == key
                    Column(
                        Modifier.weight(1f).semantics { selected = on }.pressable(label = label, role = Role.RadioButton) { vm.setTheme(key) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                                .border(if (on) 2.dp else 1.dp, if (on) c.ink else c.line, RoundedCornerShape(14.dp)),
                        ) { sw.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it)) } }
                        Spacer(Modifier.height(6.dp))
                        Txt(label, T.rowSub, if (on) c.ink else c.ink3)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Txt("Accent", T.eyebrow, c.ink3)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Accent.entries.forEach { a ->
                    val on = s.settings.accent == a.key
                    val col = if (c.dark) a.dark else a.light
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { selected = on }.pressable(label = a.label, role = Role.RadioButton) { vm.setAccent(a.key) }) {
                        Box(
                            Modifier.size(38.dp).then(if (on) Modifier.border(2.dp, col, CircleShape) else Modifier).padding(4.dp).clip(CircleShape).background(col),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Ic("check", size = 14.dp, tint = Color.White) }
                        Spacer(Modifier.height(6.dp))
                        Txt(a.label, T.rowSub, if (on) c.ink else c.ink3)
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))
        SectionHead("Data")
        Column(Modifier.fillMaxWidth().card(20.dp).padding(16.dp)) {
            Txt(
                if (s.sample) "You’re looking at sample data. Erase it to set up your own bike." else "Everything lives on this phone only. Backup and restore arrive in the next build.",
                T.lead, c.ink2,
            )
            Spacer(Modifier.height(14.dp))
            if (!confirm) Btn(if (s.sample) "Erase sample data" else "Erase all data", { confirm = true }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "trash")
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Cancel", { confirm = false }, Modifier.weight(1f), kind = BtnKind.LINE)
                Btn("Erase", { confirm = false; vm.eraseAll() }, Modifier.weight(1f), kind = BtnKind.ACCENT)
            }
        }

        Spacer(Modifier.height(30.dp))
        Txt("Waylink for Android · 0.1 preview. The web app (v0) is the design reference.", T.note, c.ink3)
    }
}
