package app.waylink.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.core.AppState
import app.waylink.core.CrashLogic
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Txt
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T

/**
 * The crash countdown: full screen, red, one huge "I'm OK" a gloved hand can hit, and a smaller "Call now".
 * When the count ends the emergency contact is called (see AppViewModel.callForHelp).
 */
@Composable
fun CrashOverlay(s: AppState, since: Long, test: Boolean, ok: () -> Unit, callNow: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(250) } }
    val left = (CrashLogic.COUNTDOWN_S - ((now - since) / 1000).toInt()).coerceAtLeast(0)
    BackHandler { ok() }
    val who = s.profile.iceName.ifBlank { null } ?: if (s.profile.icePhone.isNotBlank()) "your emergency contact" else null
    Column(
        Modifier.fillMaxSize().background(Color(0xFFB3261E)).padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Ic("siren", size = 36.dp, tint = Color.White)
            Spacer(Modifier.height(14.dp))
            Txt(if (test) "Crash detection test" else "Did you crash?", T.title.copy(fontSize = 30.sp, lineHeight = 34.sp, textAlign = TextAlign.Center), Color.White)
            Spacer(Modifier.height(8.dp))
            Txt(
                if (who != null) "Calling $who in" else "Opening the emergency number in",
                T.row.copy(fontSize = 17.sp, textAlign = TextAlign.Center), Color.White.copy(alpha = 0.85f),
            )
            Txt("$left", T.big(96f), Color.White, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Box(
            Modifier.size(220.dp).clip(CircleShape).background(Color.White).pressable(label = "I’m OK, cancel", scale = 0.94f, onClick = ok),
            contentAlignment = Alignment.Center,
        ) { Txt("I’m OK", T.title.copy(fontSize = 34.sp), Color(0xFFB3261E)) }
        Txt(
            "Call now", T.row.copy(fontSize = 17.sp), Color.White,
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).pressable(label = "Call for help now", onClick = callNow)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}
