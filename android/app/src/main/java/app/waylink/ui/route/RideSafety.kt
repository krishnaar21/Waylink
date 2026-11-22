package app.waylink.ui.route

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.waylink.core.AUTO_REPLIES
import app.waylink.core.AppState
import app.waylink.data.AppViewModel
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.FormField
import app.waylink.ui.components.Hairline
import app.waylink.ui.components.Hint
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Input
import app.waylink.ui.components.ListCard
import app.waylink.ui.components.ListRow
import app.waylink.ui.components.Switch
import app.waylink.ui.components.Txt
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import app.waylink.ui.logbook.SheetNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip

@Composable
private fun Check(on: Boolean) {
    val c = W.c
    if (on) Box(Modifier.size(20.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) { Ic("check", size = 12.dp, tint = c.bg) }
}

/** Auto-reply: on/off, when, and the message (a ready one or the rider's own). */
@Composable
fun AutoReplyBody(s: AppState, vm: AppViewModel, nav: SheetNav) {
    val c = W.c
    val ctx = LocalContext.current
    val st = s.settings
    var own by rememberSaveable { mutableStateOf(if (st.autoReplyText in AUTO_REPLIES) "" else st.autoReplyText) }
    Spacer(Modifier.height(8.dp))
    Txt("When someone messages you on WhatsApp or by text while you ride, Waylink replies once for you. Each person gets one reply per half hour, and groups are left alone.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(16.dp))
    ListCard(flat = true) {
        ListRow("Auto-reply", if (st.autoReply) "On" else "Off", "chat", chevron = false, compact = true, trailing = {
            Switch(st.autoReply, "Auto-reply", {
                vm.toggleSetting("autoReply")
                if (!st.autoReply && !app.waylink.data.NavFeed.allowed(ctx)) nav.open("navaccess")
            })
        })
        Hairline(66.dp)
        ListRow("While riding", "A ride recording, navigating or the bike linked", "bike", chevron = false, compact = true,
            onClick = { vm.setAutoReply(whenKey = "riding") }, trailing = { Check(st.autoReplyWhen == "riding") })
        Hairline(66.dp)
        ListRow("Only with the bike linked", "Never when you’re off the bike", "bt", chevron = false, compact = true,
            onClick = { vm.setAutoReply(whenKey = "linked") }, trailing = { Check(st.autoReplyWhen == "linked") })
    }
    FormField("Message") {
        ListCard(flat = true) {
            AUTO_REPLIES.forEachIndexed { i, t ->
                if (i > 0) Hairline(20.dp)
                ListRow(t, null, null, chevron = false, compact = true, onClick = { own = ""; vm.setAutoReply(text = t) }, trailing = { Check(st.autoReplyText == t) })
            }
        }
    }
    FormField("Or write your own") {
        Input(own, { own = it }, placeholder = "I’m riding, will call back", label = "Your own reply", maxLength = 200)
    }
    if (own.isNotBlank() && own.trim() != st.autoReplyText) {
        Spacer(Modifier.height(10.dp))
        Btn("Use my message", { vm.setAutoReply(text = own) }, Modifier.fillMaxWidth(), kind = BtnKind.LINE)
    }
    Hint("The reply goes out from WhatsApp or your messages app, like replying from the notification. Waylink needs notification access for this and never reads the message itself.")
}

/** Crash detection: what it does, the switch (which asks to place calls), and a safe try-out of the countdown. */
@Composable
fun CrashBody(s: AppState, vm: AppViewModel, nav: SheetNav, host: app.waylink.ui.shell.ExtraHost) {
    val c = W.c
    val ctx = LocalContext.current
    val st = s.settings
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* Works either way: without it, the dialer opens instead. */ }
    Spacer(Modifier.height(8.dp))
    Txt("If you fall while riding, Waylink sounds an alarm and counts down 30 seconds. Tap I’m OK to stop it. If you don’t, it calls your emergency contact and opens SOS.", T.sheetLead, c.ink2)
    Spacer(Modifier.height(16.dp))
    ListCard(flat = true) {
        ListRow("Crash detection", if (st.crashDetect) "On while you ride" else "Off", "siren", chevron = false, compact = true, trailing = {
            Switch(st.crashDetect, "Crash detection", {
                vm.toggleSetting("crashDetect")
                if (!st.crashDetect && ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) ask.launch(Manifest.permission.CALL_PHONE)
            })
        })
        Hairline(66.dp)
        val p = s.profile
        ListRow("Who gets called", if (p.icePhone.isNotBlank()) "${p.iceName.ifBlank { "Emergency contact" }} · ${p.icePhone}" else "Add an emergency contact first",
            "heart", onClick = { nav.close(); host.page("profile") }, compact = true)
    }
    Spacer(Modifier.height(16.dp))
    Row {
        Ic("info", size = 16.dp, tint = c.ink3)
        Spacer(Modifier.width(10.dp))
        Txt("It needs a hard hit while moving, then the bike stopped and the phone lying still. Potholes, speed breakers and a dropped phone at rest don’t set it off. Keep the phone on you or on the bike, with Waylink open or recording.",
            T.row, c.ink3)
    }
    Spacer(Modifier.height(16.dp))
    Btn("Try the countdown", { nav.close(); vm.crashDetected(test = true) }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, icon = "timer")
    Hint("The try-out is silent and calls no one.")
}
