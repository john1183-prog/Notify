package app.notify.ui

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun GapW(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun hueOf(index: Int): Color {
    val hues = Look.c.hues
    return hues[index.mod(hues.size)]
}

/** Grey at first, the folder's pigment once the word has been delivered often enough. */
@Composable
fun soakColor(hue: Color, shown: Int): Color =
    lerp(Look.c.dim.copy(alpha = 0.3f), hue, soakOf(shown).coerceAtLeast(0.1f))

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Look.c.dim) {
    Text(text, style = Type.small, color = color, modifier = modifier)
}

/**
 * A chip. Choices (isChoice) behave like radio buttons for screen readers; the rest are plain buttons.
 * Selected is filled with ink, so colour stays reserved for folders and words.
 * The tappable area is at least 48dp tall even though the chip itself is slimmer.
 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isChoice: Boolean = true,
) {
    val c = Look.c
    val tap = if (isChoice) {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Box(
        modifier.defaultMinSize(minHeight = 48.dp).clip(CircleShape).then(tap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) c.ink else Color.Transparent)
                .border(1.dp, if (selected) c.ink else c.line, CircleShape)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(text, style = Type.body, color = if (selected) c.bg else c.ink)
        }
    }
}

@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Look.c
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (enabled) c.ink else c.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.action, color = if (enabled) c.bg else c.dim)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Look.c.ink) {
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.action, color = color)
    }
}

/** Underline-only text field: quieter than a boxed field, and the words stay the focus. */
@Composable
fun LineField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val c = Look.c
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = style.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            singleLine = singleLine,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = 8.dp)) {
                    if (value.isEmpty()) Text(hint, style = style, color = c.dim.copy(alpha = 0.7f))
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
fun SheetDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Look.c.raised)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            content = content,
        )
    }
}

@Composable
fun Banner(text: String, action: String, onClick: () -> Unit) {
    val c = Look.c
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.raised)
            .padding(16.dp),
    ) {
        Text(text, style = Type.body, color = c.ink)
        TextAction(action, onClick, Modifier.offset(x = (-10).dp))
    }
}

/** Platform time picker: familiar, accessible, and adds no dependency. */
fun pickTime(ctx: Context, initial: Int, is24: Boolean, onPick: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, initial / 60, initial % 60, is24).show()
}

/** Settings screens differ between phone makers; never crash if one is missing. */
fun safeStart(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}
