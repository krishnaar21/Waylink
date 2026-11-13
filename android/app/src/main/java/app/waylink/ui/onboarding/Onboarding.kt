package app.waylink.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.waylink.R
import app.waylink.core.AppState
import app.waylink.core.Catalog
import app.waylink.ui.components.Btn
import app.waylink.ui.components.BtnKind
import app.waylink.ui.components.CircleBtn
import app.waylink.ui.components.Field
import app.waylink.ui.components.Ic
import app.waylink.ui.components.Input
import app.waylink.ui.components.LinkText
import app.waylink.ui.components.Logo
import app.waylink.ui.components.PickerList
import app.waylink.ui.components.Select
import app.waylink.ui.components.Sheet
import app.waylink.ui.components.Txt
import app.waylink.ui.components.avatarRes
import app.waylink.ui.components.card
import app.waylink.ui.components.pressable
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlinx.coroutines.launch

interface OnboardingActions {
    fun sample()
    fun saveBike(model: String, plate: String, odo: Double, country: String)
    fun saveAboutYou(avatar: String, name: String, city: String, blood: String, iceName: String, icePhone: String)
    fun finish(simulated: Boolean)
}

private val SLIDES = listOf(
    Triple("Navigate", "Turns on your display", "Plan in Google Maps. Arrows show on the cluster."),
    Triple("Stay reachable", "Calls, without your phone", "Caller names and missed calls, on the bike."),
    Triple("Ride", "Everything at a glance", "Speed, time, fuel and the next turn."),
    Triple("Look after it", "Service and fuel, sorted", "A nudge before anything is due."),
    Triple("Yours alone", "Private by design", "No account, no cloud. It stays on this phone."),
)

/** Intro story, then Your bike, About you and Link your bike. Every step can be skipped, as on the web. */
@Composable
fun Onboarding(s: AppState, act: OnboardingActions) {
    var step by rememberSaveable { mutableStateOf(0) }
    // Web order: 0 intro → 1 bike → 3 about you → 2 link.
    BackHandler(enabled = step != 0) { step = when (step) { 1 -> 0; 3 -> 1; else -> 3 } }
    AnimatedContent(step, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "onb") { st ->
        when (st) {
            0 -> Intro(onSkip = { step = 1 }, onDone = { step = 1 }, onSample = act::sample)
            1 -> YourBike(s, onBack = { step = 0 }, onSkip = { step = 3 }, onSample = act::sample) { m, p, o, c -> act.saveBike(m, p, o, c); step = 3 }
            3 -> AboutYou(s, onBack = { step = 1 }, onSkip = { step = 2 }) { a, n, ci, b, iN, iP -> act.saveAboutYou(a, n, ci, b, iN, iP); step = 2 }
            else -> LinkBike(onBack = { step = 3 }, onFinish = act::finish)
        }
    }
}

@Composable
private fun Top(left: @Composable () -> Unit, right: String, onRight: () -> Unit) {
    // Web .onb: padding-top 36, .onb-top at least 44 tall.
    Row(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 10.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        left()
        Spacer(Modifier.weight(1f))
        LinkText(right, onRight, style = T.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = W.c.ink2)
    }
}

@Composable
private fun Intro(onSkip: () -> Unit, onDone: () -> Unit, onSample: () -> Unit) {
    val c = W.c
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        // The painted road: one wide picture panned a fifth per slide, faded at top and bottom.
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(520.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Transparent, 0.42f to Color.Black, 0.72f to Color.Black, 0.98f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            val w = maxWidth
            val p = pager.currentPage + pager.currentPageOffsetFraction
            Image(
                painterResource(if (c.dark) R.drawable.scene_dark else R.drawable.scene_light), null,
                Modifier.align(Alignment.BottomStart).requiredWidth(w * 5).height(372.dp).offset(x = -(w * p) + w * 2),
                contentScale = ContentScale.FillBounds,
            )
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp)) {
            Top({ Logo(27f) }, "Skip", onSkip)
            Spacer(Modifier.weight(1f))
            HorizontalPager(pager, Modifier.fillMaxWidth().height(130.dp)) { i ->
                val (e, t, d) = SLIDES[i]
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(e, T.eyebrow.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em), c.accent)
                    Spacer(Modifier.height(8.dp))
                    Txt(t, T.slideTitle.copy(textAlign = TextAlign.Center), c.ink, Modifier.semantics { heading() })
                    Spacer(Modifier.height(8.dp))
                    Txt(d, T.onbP.copy(textAlign = TextAlign.Center), c.ink2, Modifier.padding(horizontal = 11.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                SLIDES.indices.forEach { i ->
                    val on = pager.currentPage == i
                    val width by animateDpAsState(if (on) 22.dp else 7.dp, label = "dot")
                    Box(
                        Modifier.semantics { contentDescription = "Slide ${i + 1}"; selected = on }
                            .pressable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 2.dp, vertical = 8.dp),
                    ) { Box(Modifier.width(width).height(7.dp).clip(RoundedCornerShape(4.dp)).background(if (on) c.ink else c.segOff)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            val last = pager.currentPage == SLIDES.lastIndex
            Btn(if (last) "Set up my bike" else "Next", { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            LinkText("Explore with sample data", onSample, Modifier.align(Alignment.CenterHorizontally), style = T.body.copy(fontSize = 14.5.sp), color = c.ink3)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepPage(
    eyebrow: String, title: String, lead: String, onBack: () -> Unit, onSkip: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = W.c
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 24.dp, end = 24.dp, bottom = 42.dp),
    ) {
        Top({ CircleBtn("back", "Back", onBack) }, "Skip", onSkip)
        Spacer(Modifier.height(28.dp))
        Txt(eyebrow, T.eyebrow, c.ink3)
        Spacer(Modifier.height(6.dp))
        Txt(title, T.onbTitle, c.ink, Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Txt(lead, T.onbP, c.ink2)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun YourBike(s: AppState, onBack: () -> Unit, onSkip: () -> Unit, onSample: () -> Unit, onSave: (String, String, Double, String) -> Unit) {
    val c = W.c
    var model by rememberSaveable { mutableStateOf(s.vehicle.model) }
    var plate by rememberSaveable { mutableStateOf(s.vehicle.plate) }
    var odo by rememberSaveable { mutableStateOf(if (s.vehicle.odo > 0) s.vehicle.odo.toLong().toString() else "") }
    var country by rememberSaveable { mutableStateOf(s.profile.country) }
    var picker by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        StepPage("Step 1 of 3", "Your bike", "So reminders and the display fit it.", onBack, onSkip) {
            Field("Model", Modifier.padding(top = 18.dp)) { Select(Catalog.model(model).name, { picker = "model" }, label = "Model") }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Number plate", Modifier.weight(1f)) {
                    Input(plate, { plate = it.uppercase() }, placeholder = "KA 05 MX 2481", label = "Number plate", caps = KeyboardCapitalization.Characters, maxLength = 13)
                }
                Field("Odometer", Modifier.weight(1f)) {
                    Input(odo, { v -> odo = v.filter { it.isDigit() } }, placeholder = "0", suffix = "km", label = "Odometer in km", keyboard = KeyboardType.Number, maxLength = 6)
                }
            }
            Field("Country", Modifier.padding(top = 18.dp)) { Select(Catalog.country(country).name, { picker = "country" }, label = "Country") }
            Spacer(Modifier.height(24.dp))
            Btn("Continue", { onSave(model, plate, odo.toDoubleOrNull() ?: 0.0, country) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            LinkText("Explore with sample data", onSample, Modifier.align(Alignment.CenterHorizontally), style = T.body.copy(fontSize = 14.5.sp), color = c.ink3)
        }
        Sheet(picker == "model", { picker = null }, "Model", "Your bike") {
            PickerList(Catalog.MODELS.map { it.id to it.name }, model) { model = it; picker = null }
        }
        Sheet(picker == "country", { picker = null }, "Country", "Sets currency, fuel price and emergency numbers") {
            PickerList(Catalog.COUNTRIES.map { it.code to it.name }, country) { country = it; picker = null }
        }
    }
}

@Composable
private fun AboutYou(s: AppState, onBack: () -> Unit, onSkip: () -> Unit, onSave: (String, String, String, String, String, String) -> Unit) {
    val c = W.c
    val p = s.profile
    var avatar by rememberSaveable { mutableStateOf(p.avatar) }
    var name by rememberSaveable { mutableStateOf(p.name) }
    var city by rememberSaveable { mutableStateOf(p.city) }
    var blood by rememberSaveable { mutableStateOf(p.blood) }
    var iceName by rememberSaveable { mutableStateOf(p.iceName) }
    var icePhone by rememberSaveable { mutableStateOf(p.icePhone) }
    var picker by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        StepPage("Step 2 of 3", "About you", "For your profile and the SOS screen. All optional, and it stays on this phone.", onBack, onSkip) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                (1..11).map { "a$it" }.forEach { k ->
                    val on = avatar == k
                    Box(
                        Modifier.size(56.dp).semantics { selected = on }.pressable(label = "Rider ${k.drop(1)}") { avatar = k },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Color.White)
                                .then(if (on) Modifier.border(2.5.dp, c.ink, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { Image(painterResource(avatarRes(k)), null, Modifier.size(43.dp)) }
                    }
                }
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Your name", Modifier.weight(1f)) { Input(name, { name = it }, placeholder = "Arjun", label = "Your name", caps = KeyboardCapitalization.Words, maxLength = 30) }
                Field("City", Modifier.weight(1f)) { Input(city, { city = it }, placeholder = "Bengaluru", label = "City", caps = KeyboardCapitalization.Words, maxLength = 24) }
            }
            Field("Blood group", Modifier.padding(top = 18.dp)) { Select(blood.ifEmpty { "Not sure" }, { picker = true }, label = "Blood group") }
            Txt("Emergency contact", T.eyebrow, c.ink3, Modifier.padding(top = 16.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name", Modifier.weight(1f)) { Input(iceName, { iceName = it }, placeholder = "Amma", label = "Emergency contact name", caps = KeyboardCapitalization.Words, maxLength = 30) }
                Field("Phone", Modifier.weight(1f)) {
                    Input(icePhone, { v -> icePhone = v.filter { it.isDigit() || it in "+ -" } }, placeholder = "98450 00000", label = "Emergency contact phone", keyboard = KeyboardType.Phone, maxLength = 20)
                }
            }
            Spacer(Modifier.height(24.dp))
            Btn("Continue", { onSave(avatar, name, city, blood, iceName, icePhone) }, Modifier.fillMaxWidth())
        }
        Sheet(picker, { picker = false }, "Blood group", "Shown on your SOS screen") {
            PickerList(Catalog.BLOOD_GROUPS.map { it to it.ifEmpty { "Not sure" } }, blood) { blood = it; picker = false }
        }
    }
}

@Composable
private fun LinkBike(onBack: () -> Unit, onFinish: (Boolean) -> Unit) {
    val c = W.c
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Top({ CircleBtn("back", "Back", onBack) }, "Skip") { onFinish(false) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(width = 240.dp, height = 210.dp)) {
            Image(painterResource(R.drawable.rider_street), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = 6.dp).size(32.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) { Ic("bt", size = 17.dp, tint = c.onAccent) }
        }
        Spacer(Modifier.height(24.dp))
        Txt("Step 3 of 3", T.eyebrow, c.ink3)
        Spacer(Modifier.height(6.dp))
        Txt("Link your bike", T.title.copy(fontSize = 26.sp, lineHeight = 29.9.sp), c.ink, Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Txt("Pair once. It reconnects by itself.", T.onbP.copy(textAlign = TextAlign.Center), c.ink2)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("turnRight" to "Turns", "phone" to "Callers", "clock" to "Time", "fuel" to "Fuel").forEach { (ic, t) ->
                Row(
                    Modifier.card(999.dp).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Ic(ic, size = 15.dp, tint = c.accent)
                    Txt(t, T.rowSub.copy(fontWeight = FontWeight.SemiBold), c.ink2)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Btn("Try the simulated bike", { onFinish(true) }, Modifier.fillMaxWidth(), icon = "bolt")
        Spacer(Modifier.height(12.dp))
        LinkText("I’ll do this later", { onFinish(false) }, style = T.body.copy(fontSize = 14.5.sp), color = c.ink3)
        Spacer(Modifier.height(30.dp))
    }
}
