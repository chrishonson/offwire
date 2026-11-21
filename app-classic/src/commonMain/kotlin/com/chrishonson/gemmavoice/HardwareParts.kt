package com.chrishonson.gemmavoice

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keys and switches have hard corners, unlike Material's pill buttons. */
val KeyShape = RoundedCornerShape(4.dp)

private val HeaderMinHeight = 56.dp
private val HeaderPadding = 16.dp
private val HeaderSpacing = 12.dp
private val MarkSize = 36.dp
private val MarkCorner = 6.dp
private val SteelGrainSpacing = 3.dp
private val SteelEdge = 2.dp
private val TapePaddingHorizontal = 8.dp
private val TapePaddingVertical = 2.dp
private val TapeShape = RoundedCornerShape(2.dp)
private val PanelShape = RoundedCornerShape(6.dp)
private val BezelWidth = 3.dp
private val LcdRowSpacing = 3.dp
private const val STEEL_GRAIN_ALPHA = 0.07f
private const val LCD_ROW_ALPHA = 0.05f
private const val ROCKER_HIGHLIGHT = 0.35f
private const val ROCKER_SHADE = 0.22f
private const val ROCKER_PIVOT = 0.52f
private const val DISABLED_ALPHA = 0.38f
private const val MARK_VIEWBOX = 192f

/** Brushed steel: a vertical sheen, fine horizontal grain and a dark lower edge. */
fun Modifier.brushedSteel(colors: HardwareColors): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(colors.steel))
    val grain = Color.White.copy(alpha = STEEL_GRAIN_ALPHA)
    val step = SteelGrainSpacing.toPx()
    var y = step
    while (y < size.height) {
        drawLine(grain, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
    val edge = SteelEdge.toPx()
    drawRect(Color.Black.copy(alpha = ROCKER_HIGHLIGHT), topLeft = Offset(0f, size.height - edge))
}

/** A brushed-steel title bar that runs under the status bar. */
@Composable
fun SteelHeader(
    title: String,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalHardwareColors.current
    CompositionLocalProvider(LocalContentColor provides colors.onSteel) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .brushedSteel(colors)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .heightIn(min = HeaderMinHeight)
                .padding(horizontal = HeaderPadding, vertical = SteelEdge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigation()
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = HeaderSpacing / 2)
            )
            actions()
        }
    }
}

// The Offwire mark on its own 192 unit grid.
private val OffwireMarkVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "OffwireMark",
        defaultWidth = MARK_VIEWBOX.dp,
        defaultHeight = MARK_VIEWBOX.dp,
        viewportWidth = MARK_VIEWBOX,
        viewportHeight = MARK_VIEWBOX
    ).apply {
        addPath(addPathNodes("M0 0H192V192H0Z"), fill = SolidColor(Color(Hardware.PANEL)))
        addPath(
            addPathNodes("M35 97V53A18 18 0 0 1 53 35H88V77H146A10 10 0 0 1 146 97Z"),
            fill = SolidColor(Color(Hardware.LED_AMBER))
        )
        addPath(addPathNodes("M37 109H59V125H37Z"), fill = SolidColor(Color(Hardware.LED_GREEN)))
        addPath(addPathNodes("M37 136H59V155H37Z"), fill = SolidColor(Color(Hardware.LED_RED)))
        addPath(
            addPathNodes("M95 131A25 25 0 1 0 145 131A25 25 0 1 0 95 131Z"),
            fill = SolidColor(Color(Hardware.PUTTY))
        )
    }.build()
}

@Composable
fun OffwireMark(modifier: Modifier = Modifier, size: Dp = MarkSize) {
    Image(
        painter = rememberVectorPainter(OffwireMarkVector),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(MarkCorner))
    )
}

/** An embossed label-maker strip used as a section tag. */
@Composable
fun LabelTape(text: String, modifier: Modifier = Modifier) {
    val colors = LocalHardwareColors.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onTape,
        modifier = modifier
            .semantics { heading() }
            .background(colors.tape, TapeShape)
            .padding(horizontal = TapePaddingHorizontal, vertical = TapePaddingVertical)
    )
}

/** A recessed screen: a green-grey LCD in light, a black VFD glass in dark, with faint pixel rows. */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalHardwareColors.current
    Box(
        modifier = modifier
            .border(BezelWidth, colors.bezel, PanelShape)
            .padding(BezelWidth)
            .clip(PanelShape)
            .background(colors.lcd)
            .drawBehind {
                val rows = colors.lcdInk.copy(alpha = LCD_ROW_ALPHA)
                val step = LcdRowSpacing.toPx()
                var y = step
                while (y < size.height) {
                    drawLine(rows, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.lcdInk) {
            content()
        }
    }
}

/** An always-dark readout panel for LED and VFD numbers. */
@Composable
fun LedPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalHardwareColors.current
    Column(
        modifier = modifier
            .border(BezelWidth / 2, colors.bezel, PanelShape)
            .clip(PanelShape)
            .background(colors.ledPanel)
            .padding(contentPadding)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.readout) {
            content()
        }
    }
}

/** Colours for a lit red LED key, used for Stop and Limits. */
@Composable
fun ledRedKeyColors(): ButtonColors {
    val colors = LocalHardwareColors.current
    return ButtonDefaults.buttonColors(containerColor = colors.ledRed, contentColor = colors.onLed)
}

/** Colours for an outlined key sitting on the steel header. */
@Composable
fun steelKeyColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(contentColor = LocalHardwareColors.current.onSteel)

/** Colours for a plain panel key, used for secondary actions in sheets. */
@Composable
fun panelKeyColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurface
)

/** The primary action: an amber rocker switch, lit from above, with a pivot line across its face. */
@Composable
fun RockerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalHardwareColors.current
    val amber = if (enabled) colors.switchAmber else colors.switchAmber.copy(alpha = DISABLED_ALPHA)
    val face = Brush.verticalGradient(
        0f to lerp(amber, Color.White, ROCKER_HIGHLIGHT),
        ROCKER_PIVOT to amber,
        ROCKER_PIVOT to lerp(amber, Color.Black, ROCKER_SHADE / 2),
        1f to lerp(amber, Color.Black, ROCKER_SHADE)
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .border(1.dp, lerp(amber, Color.Black, ROCKER_HIGHLIGHT), KeyShape)
            .background(face, KeyShape),
        shape = KeyShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSwitch,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onSwitch.copy(alpha = DISABLED_ALPHA)
        ),
        elevation = null,
        contentPadding = contentPadding,
        content = content
    )
}
