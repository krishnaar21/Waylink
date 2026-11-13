package app.waylink.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W

/** A labelled field (.field): grey label, then the input. */
@Composable
fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Txt(label, T.eyebrow, W.c.ink3)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** .input: 52 tall, 16 radius, raised paper with a hairline ring that darkens on focus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Input(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String? = null,
    label: String = placeholder,
    keyboard: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    maxLength: Int = 60,
    multiline: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    val c = W.c
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    // Keep the field above the keyboard: when it gains focus, and again once the keyboard has opened.
    val inView = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, ime) { if (focused) { kotlinx.coroutines.delay(60); inView.bringIntoView() } }
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(maxLength)) },
        singleLine = !multiline,
        textStyle = T.input.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = keyboard, imeAction = if (multiline) ImeAction.Default else imeAction),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(inView).then(if (multiline) Modifier.heightIn(min = 96.dp) else Modifier.height(52.dp)).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().then(if (multiline) Modifier.heightIn(min = 96.dp) else Modifier.height(52.dp)).clip(shape).background(c.raised)
                    .border(if (focused) 1.5.dp else 1.dp, if (focused) c.ink.copy(alpha = 0.55f) else c.line, shape)
                    .padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 0.dp),
                verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Txt(placeholder, T.input, c.ink4, maxLines = 1)
                    inner()
                }
                if (suffix != null) Txt(suffix, T.row, c.ink3)
            }
        },
    )
}

/** A select (.input with a chevron). Tapping it opens [onOpen], usually a picker sheet. */
@Composable
fun Select(text: String, onOpen: () -> Unit, modifier: Modifier = Modifier, label: String = text) {
    val c = W.c
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(shape).background(c.raised).border(1.dp, c.line, shape)
            .pressable(label = label, onClick = onOpen).padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, T.input, c.ink, Modifier.weight(1f), maxLines = 1)
        Ic("down", size = 16.dp, tint = c.ink2)
    }
}

/** Rows for a picker sheet; the chosen one carries a check. */
@Composable
fun PickerList(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val c = W.c
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().card(20.dp, c.raised)) {
        options.forEachIndexed { i, (key, text) ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(c.line))
            Row(
                Modifier.fillMaxWidth().pressable(label = text) { onPick(key) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(text, T.body, c.ink, Modifier.weight(1f), maxLines = 1)
                if (key == selected) Box(Modifier.size(20.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                    Ic("check", size = 12.dp, tint = c.bg)
                } else Spacer(Modifier.width(20.dp))
            }
        }
    }
}
