package com.chrishonson.gemmavoice

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chrishonson.gemmavoice.console.resources.Res
import com.chrishonson.gemmavoice.console.resources.antonio_bold
import com.chrishonson.gemmavoice.console.resources.antonio_regular
import org.jetbrains.compose.resources.Font

// LCARS palette as ARGB values, shared with the control-plane board. Compose-free so contrast can be unit tested.
internal object Lcars {
    const val BLACK = 0xFF000000L
    const val PANEL = 0xFF0E0E16L
    const val RAISED = 0xFF191926L
    const val LINE = 0xFF2A2A3CL
    const val FRAME = 0xFF93683FL
    const val ORANGE = 0xFFD08A3CL
    const val SUNFLOWER = 0xFFD3B591L
    const val ICE = 0xFF8EACC8L
    const val VIOLET = 0xFFAC8DADL
    const val BLUEY = 0xFF7F8AD0L
    const val TOMATO = 0xFFCC6262L
    const val TEXT = 0xFFE4E2DCL
    const val TEXT_SECONDARY = 0xFFC4C2CCL
    const val TITLE = 0xFFE6DCCBL
}

private const val PILL_PERCENT = 50

private val LcarsColors = darkColorScheme(
    primary = Color(Lcars.ORANGE),
    onPrimary = Color(Lcars.BLACK),
    primaryContainer = Color(Lcars.ORANGE),
    onPrimaryContainer = Color(Lcars.BLACK),
    secondary = Color(Lcars.ICE),
    onSecondary = Color(Lcars.BLACK),
    secondaryContainer = Color(Lcars.PANEL),
    onSecondaryContainer = Color(Lcars.TEXT),
    tertiary = Color(Lcars.SUNFLOWER),
    onTertiary = Color(Lcars.BLACK),
    tertiaryContainer = Color(Lcars.PANEL),
    onTertiaryContainer = Color(Lcars.TEXT),
    background = Color(Lcars.BLACK),
    onBackground = Color(Lcars.TEXT),
    surface = Color(Lcars.BLACK),
    onSurface = Color(Lcars.TEXT),
    surfaceVariant = Color(Lcars.RAISED),
    onSurfaceVariant = Color(Lcars.TEXT_SECONDARY),
    surfaceContainerLowest = Color(Lcars.BLACK),
    surfaceContainerLow = Color(Lcars.BLACK),
    surfaceContainer = Color(Lcars.PANEL),
    surfaceContainerHigh = Color(Lcars.PANEL),
    surfaceContainerHighest = Color(Lcars.RAISED),
    outline = Color(Lcars.LINE),
    outlineVariant = Color(Lcars.RAISED),
    error = Color(Lcars.TOMATO),
    onError = Color(Lcars.BLACK),
    errorContainer = Color(Lcars.TOMATO),
    onErrorContainer = Color(Lcars.BLACK)
)

// LCARS controls are pills: solid blocks of color with fully rounded ends.
private val LcarsShapes = Shapes(
    small = RoundedCornerShape(PILL_PERCENT),
    medium = RoundedCornerShape(PILL_PERCENT)
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily? = null
) = TextStyle(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, fontFamily = family)

// Large scale for low vision. Everything is in sp so the phone's font-size setting still scales it.
// The display, headline, title and label roles are set in Antonio; body text stays on the default sans.
private fun typography(display: FontFamily) = Typography(
    displayLarge = style(size = 48, lineHeight = 56, weight = FontWeight.Bold, family = display),
    displayMedium = style(size = 40, lineHeight = 48, weight = FontWeight.Bold, family = display),
    displaySmall = style(size = 34, lineHeight = 42, weight = FontWeight.Bold, family = display),
    headlineLarge = style(size = 30, lineHeight = 38, weight = FontWeight.Bold, family = display),
    headlineMedium = style(size = 27, lineHeight = 34, weight = FontWeight.Bold, family = display),
    headlineSmall = style(size = 24, lineHeight = 32, weight = FontWeight.Bold, family = display),
    titleLarge = style(size = 24, lineHeight = 32, weight = FontWeight.Bold, family = display),
    titleMedium = style(size = 21, lineHeight = 28, weight = FontWeight.Bold, family = display),
    titleSmall = style(size = 20, lineHeight = 28, weight = FontWeight.Bold, family = display),
    bodyLarge = style(size = 20, lineHeight = 28),
    bodyMedium = style(size = 20, lineHeight = 28),
    bodySmall = style(size = 17, lineHeight = 24),
    labelLarge = style(size = 20, lineHeight = 26, weight = FontWeight.Bold, family = display),
    labelMedium = style(size = 17, lineHeight = 22, weight = FontWeight.Bold, family = display),
    labelSmall = style(size = 15, lineHeight = 20, weight = FontWeight.Bold, family = display)
)

@Composable
fun GemmaVoiceTheme(content: @Composable () -> Unit) {
    val antonio = FontFamily(
        Font(Res.font.antonio_regular, weight = FontWeight.Normal),
        Font(Res.font.antonio_bold, weight = FontWeight.Bold)
    )
    MaterialTheme(
        colorScheme = LcarsColors,
        typography = typography(antonio),
        shapes = LcarsShapes,
        content = content
    )
}
