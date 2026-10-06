package app.notify.ui

import android.text.format.DateFormat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Design idea: the interface is grey, and colour is information.
 * Every folder owns a pigment. A word's colour deepens each time it is delivered,
 * so a glance at the bank shows what is soaked in and what is still pale.
 * Buttons and chrome stay neutral so colour never means anything else.
 */
@Immutable
class Palette(
    val bg: Color,
    val raised: Color,
    val ink: Color,
    val dim: Color,
    val line: Color,
    val hues: List<Color>,
)

private val Pigments = listOf(
    Color(0xFF3F62E0), // cobalt
    Color(0xFFE0A100), // saffron
    Color(0xFFD94679), // rose
    Color(0xFF1B9C76), // viridian
    Color(0xFF8A5CD6), // violet
    Color(0xFFE8682B), // tangerine
)

private val Slate = Palette(
    bg = Color(0xFF15171B), raised = Color(0xFF1F2227), ink = Color(0xFFE8EAED),
    dim = Color(0xFF8E949E), line = Color(0xFF2C3037), hues = Pigments,
)
private val Fog = Palette(
    bg = Color(0xFFEBEDEF), raised = Color(0xFFF8F9FA), ink = Color(0xFF1B1E24),
    dim = Color(0xFF5E646F), line = Color(0xFFD3D7DC), hues = Pigments,
)

private val LocalPalette = staticCompositionLocalOf { Slate }
val LocalIs24 = staticCompositionLocalOf { true }

val HueNames = listOf("Cobalt", "Saffron", "Rose", "Viridian", "Violet", "Tangerine")

object Look {
    val c: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    /** Follows the phone's 12 or 24 hour clock setting. */
    val is24: Boolean
        @Composable @ReadOnlyComposable get() = LocalIs24.current
}

/** Serif is reserved for the words themselves; everything else is sans. */
object Type {
    private val sans = FontFamily.Default
    private val serif = FontFamily.Serif

    val title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp)
    val heading = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp)
    val hero = TextStyle(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 43.sp, letterSpacing = (-0.2).sp)
    val word = TextStyle(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 33.sp)
    val body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val action = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
fun NotifyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Slate else Fog
    val scheme = if (dark) {
        darkColorScheme(primary = p.ink, onPrimary = p.bg, background = p.bg, onBackground = p.ink, surface = p.raised, onSurface = p.ink)
    } else {
        lightColorScheme(primary = p.ink, onPrimary = p.bg, background = p.bg, onBackground = p.ink, surface = p.raised, onSurface = p.ink)
    }
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    CompositionLocalProvider(LocalPalette provides p, LocalIs24 provides is24) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
