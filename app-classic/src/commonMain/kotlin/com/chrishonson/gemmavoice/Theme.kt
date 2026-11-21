package com.chrishonson.gemmavoice

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrishonson.gemmavoice.classic.resources.Res
import com.chrishonson.gemmavoice.classic.resources.archivo_bold
import com.chrishonson.gemmavoice.classic.resources.archivo_regular
import com.chrishonson.gemmavoice.classic.resources.archivo_semibold
import com.chrishonson.gemmavoice.classic.resources.share_tech_mono_regular
import com.chrishonson.gemmavoice.classic.resources.vt323_regular
import org.jetbrains.compose.resources.Font

/** 90s hardware palette as ARGB values, kept Compose-free so contrast can be unit tested. */
internal object Hardware {
    const val PUTTY = 0xFFE2DAC6L
    const val PANEL = 0xFF1A1712L
    const val LED_RED = 0xFFFF5A45L
    const val LED_GREEN = 0xFF52E06FL
    const val LED_AMBER = 0xFFFFB62EL
    const val VFD_CYAN = 0xFF62F0D4L
    const val LCD = 0xFFB9C2A3L
    const val SWITCH_AMBER = 0xFFF5A623L

    // Light: putty case with recessed panels. Ink is the dark panel colour.
    const val INK = PANEL
    const val PUTTY_RECESS = 0xFFD6CDB5L
    const val PUTTY_DEEP = 0xFFCBC1A7L
    const val INK_DIM = 0xFF35322BL
    const val LCD_INK_DIM = 0xFF2E3A26L

    // LED red and amber are too light for text on putty, so light text uses darker inks of each.
    const val RED_INK = 0xFFA3241AL
    const val AMBER_INK = 0xFF7A4B00L

    // Dark: the dark panel colour with lighter recesses, and a black VFD glass for the chat.
    const val PANEL_RAISED = 0xFF26221BL
    const val PANEL_HIGH = 0xFF2F2A22L
    const val PUTTY_DIM = 0xFFC9C1ABL
    const val VFD_GLASS = 0xFF0E0D0AL
    const val VFD_DIM = 0xFF3A9FA0L

    const val STEEL_LIGHT_TOP = 0xFFEEECE6L
    const val STEEL_LIGHT_MID = 0xFFC8C4BAL
    const val STEEL_LIGHT_BOTTOM = 0xFFA9A59BL
    const val STEEL_DARK_TOP = 0xFF4A463EL
    const val STEEL_DARK_MID = 0xFF3A362FL
    const val STEEL_DARK_BOTTOM = 0xFF2A2721L
}

/** Hardware roles Material has no slot for: steel, LCD, LEDs, tape and the rocker switch. */
@Immutable
@Suppress("LongParameterList")
data class HardwareColors(
    val isDark: Boolean,
    val steel: List<Color>,
    val onSteel: Color,
    val lcd: Color,
    val lcdInk: Color,
    val lcdInkDim: Color,
    val lcdGlow: Boolean,
    val ledPanel: Color,
    val readout: Color,
    val readoutDim: Color,
    val ledRed: Color,
    val ledGreen: Color,
    val ledAmber: Color,
    val onLed: Color,
    val switchAmber: Color,
    val onSwitch: Color,
    val tape: Color,
    val onTape: Color,
    val bezel: Color
)

internal val LightHardware = HardwareColors(
    isDark = false,
    steel = listOf(
        Color(Hardware.STEEL_LIGHT_TOP),
        Color(Hardware.STEEL_LIGHT_MID),
        Color(Hardware.STEEL_LIGHT_BOTTOM)
    ),
    onSteel = Color(Hardware.INK),
    lcd = Color(Hardware.LCD),
    lcdInk = Color(Hardware.INK),
    lcdInkDim = Color(Hardware.LCD_INK_DIM),
    lcdGlow = false,
    ledPanel = Color(Hardware.PANEL),
    readout = Color(Hardware.VFD_CYAN),
    readoutDim = Color(Hardware.VFD_DIM),
    ledRed = Color(Hardware.LED_RED),
    ledGreen = Color(Hardware.LED_GREEN),
    ledAmber = Color(Hardware.LED_AMBER),
    onLed = Color(Hardware.INK),
    switchAmber = Color(Hardware.SWITCH_AMBER),
    onSwitch = Color(Hardware.INK),
    tape = Color(Hardware.INK),
    onTape = Color(Hardware.PUTTY),
    bezel = Color(Hardware.STEEL_LIGHT_BOTTOM)
)

internal val DarkHardware = HardwareColors(
    isDark = true,
    steel = listOf(
        Color(Hardware.STEEL_DARK_TOP),
        Color(Hardware.STEEL_DARK_MID),
        Color(Hardware.STEEL_DARK_BOTTOM)
    ),
    onSteel = Color(Hardware.PUTTY),
    lcd = Color(Hardware.VFD_GLASS),
    lcdInk = Color(Hardware.VFD_CYAN),
    lcdInkDim = Color(Hardware.VFD_DIM),
    lcdGlow = true,
    ledPanel = Color(Hardware.VFD_GLASS),
    readout = Color(Hardware.VFD_CYAN),
    readoutDim = Color(Hardware.VFD_DIM),
    ledRed = Color(Hardware.LED_RED),
    ledGreen = Color(Hardware.LED_GREEN),
    ledAmber = Color(Hardware.LED_AMBER),
    onLed = Color(Hardware.INK),
    switchAmber = Color(Hardware.SWITCH_AMBER),
    onSwitch = Color(Hardware.INK),
    tape = Color(Hardware.PUTTY),
    onTape = Color(Hardware.INK),
    bezel = Color(Hardware.STEEL_DARK_TOP)
)

/** Readout and LCD faces. Defaults stand in for screens composed outside the theme, as in UI tests. */
@Immutable
data class HardwareFonts(val readout: FontFamily, val lcd: FontFamily)

val LocalHardwareColors = staticCompositionLocalOf { DarkHardware }
val LocalHardwareFonts = staticCompositionLocalOf { HardwareFonts(FontFamily.Monospace, FontFamily.Monospace) }

// Switch amber is too light to mark controls on putty, so light Material controls (switches, radios,
// fields, outlined keys) use amber ink. Rocker switches still use switch amber.
private val PuttyColors = lightColorScheme(
    primary = Color(Hardware.AMBER_INK),
    onPrimary = Color(Hardware.PUTTY),
    primaryContainer = Color(Hardware.SWITCH_AMBER),
    onPrimaryContainer = Color(Hardware.INK),
    secondary = Color(Hardware.INK_DIM),
    onSecondary = Color(Hardware.PUTTY),
    secondaryContainer = Color(Hardware.PUTTY_DEEP),
    onSecondaryContainer = Color(Hardware.INK),
    tertiary = Color(Hardware.AMBER_INK),
    onTertiary = Color(Hardware.PUTTY),
    tertiaryContainer = Color(Hardware.PUTTY_DEEP),
    onTertiaryContainer = Color(Hardware.INK),
    background = Color(Hardware.PUTTY),
    onBackground = Color(Hardware.INK),
    surface = Color(Hardware.PUTTY),
    onSurface = Color(Hardware.INK),
    surfaceVariant = Color(Hardware.PUTTY_DEEP),
    onSurfaceVariant = Color(Hardware.INK_DIM),
    surfaceContainerLowest = Color(Hardware.PUTTY),
    surfaceContainerLow = Color(Hardware.PUTTY),
    surfaceContainer = Color(Hardware.PUTTY_RECESS),
    surfaceContainerHigh = Color(Hardware.PUTTY_RECESS),
    surfaceContainerHighest = Color(Hardware.PUTTY_DEEP),
    outline = Color(Hardware.INK_DIM),
    outlineVariant = Color(Hardware.STEEL_LIGHT_BOTTOM),
    error = Color(Hardware.RED_INK),
    onError = Color(Hardware.PUTTY),
    errorContainer = Color(Hardware.LED_RED),
    onErrorContainer = Color(Hardware.INK)
)

private val PanelColors = darkColorScheme(
    primary = Color(Hardware.SWITCH_AMBER),
    onPrimary = Color(Hardware.INK),
    primaryContainer = Color(Hardware.SWITCH_AMBER),
    onPrimaryContainer = Color(Hardware.INK),
    secondary = Color(Hardware.PUTTY_DIM),
    onSecondary = Color(Hardware.PANEL),
    secondaryContainer = Color(Hardware.PANEL_HIGH),
    onSecondaryContainer = Color(Hardware.PUTTY),
    tertiary = Color(Hardware.LED_AMBER),
    onTertiary = Color(Hardware.INK),
    tertiaryContainer = Color(Hardware.PANEL_HIGH),
    onTertiaryContainer = Color(Hardware.PUTTY),
    background = Color(Hardware.PANEL),
    onBackground = Color(Hardware.PUTTY),
    surface = Color(Hardware.PANEL),
    onSurface = Color(Hardware.PUTTY),
    surfaceVariant = Color(Hardware.PANEL_HIGH),
    onSurfaceVariant = Color(Hardware.PUTTY_DIM),
    surfaceContainerLowest = Color(Hardware.PANEL),
    surfaceContainerLow = Color(Hardware.PANEL),
    surfaceContainer = Color(Hardware.PANEL_RAISED),
    surfaceContainerHigh = Color(Hardware.PANEL_RAISED),
    surfaceContainerHighest = Color(Hardware.PANEL_HIGH),
    outline = Color(Hardware.PUTTY_DIM),
    outlineVariant = Color(Hardware.STEEL_DARK_TOP),
    error = Color(Hardware.LED_RED),
    onError = Color(Hardware.INK),
    errorContainer = Color(Hardware.LED_RED),
    onErrorContainer = Color(Hardware.INK)
)

// Hardware has hard edges: small radii everywhere.
private val HardwareShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

private fun style(
    family: FontFamily,
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal
) = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)

// Large scale for low vision. Everything is in sp so the phone's font-size setting still scales it.
// UI text is Archivo; labels are Share Tech Mono, which has one weight, so labels stay regular.
private fun typography(ui: FontFamily, labels: FontFamily) = Typography(
    displayLarge = style(ui, size = 48, lineHeight = 56, weight = FontWeight.Bold),
    displayMedium = style(ui, size = 40, lineHeight = 48, weight = FontWeight.Bold),
    displaySmall = style(ui, size = 34, lineHeight = 42, weight = FontWeight.Bold),
    headlineLarge = style(ui, size = 30, lineHeight = 38, weight = FontWeight.Bold),
    headlineMedium = style(ui, size = 27, lineHeight = 34, weight = FontWeight.Bold),
    headlineSmall = style(ui, size = 24, lineHeight = 32, weight = FontWeight.Bold),
    titleLarge = style(ui, size = 24, lineHeight = 32, weight = FontWeight.Bold),
    titleMedium = style(ui, size = 21, lineHeight = 28, weight = FontWeight.SemiBold),
    titleSmall = style(ui, size = 20, lineHeight = 28, weight = FontWeight.SemiBold),
    bodyLarge = style(ui, size = 20, lineHeight = 28),
    bodyMedium = style(ui, size = 20, lineHeight = 28),
    bodySmall = style(ui, size = 17, lineHeight = 24),
    labelLarge = style(labels, size = 20, lineHeight = 26),
    labelMedium = style(labels, size = 17, lineHeight = 22),
    labelSmall = style(labels, size = 15, lineHeight = 20)
)

@Composable
fun GemmaVoiceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val archivo = FontFamily(
        Font(Res.font.archivo_regular, weight = FontWeight.Normal),
        Font(Res.font.archivo_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.archivo_bold, weight = FontWeight.Bold)
    )
    val fonts = HardwareFonts(
        readout = FontFamily(Font(Res.font.share_tech_mono_regular)),
        lcd = FontFamily(Font(Res.font.vt323_regular))
    )
    CompositionLocalProvider(
        LocalHardwareColors provides if (darkTheme) DarkHardware else LightHardware,
        LocalHardwareFonts provides fonts
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) PanelColors else PuttyColors,
            typography = typography(ui = archivo, labels = fonts.readout),
            shapes = HardwareShapes,
            content = content
        )
    }
}
