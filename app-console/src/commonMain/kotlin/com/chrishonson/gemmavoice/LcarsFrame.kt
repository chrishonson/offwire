package com.chrishonson.gemmavoice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ElbowWidth = 72.dp
private val ElbowHeight = 56.dp
private val ElbowRadius = 28.dp
private val RailWidth = 16.dp
private val BarHeight = 14.dp
private val RailGap = 4.dp
private val ContentPaddingHorizontal = 12.dp
private val HeaderEndPadding = 12.dp
private val TitleStartPadding = 12.dp
private val ActionsBottomPadding = 8.dp
private val BlockStartRadius = 14.dp
private val BlockEndRadius = 4.dp
val BlockBarWidth = 6.dp
private val TopBarSpacing = 8.dp
private val TopBarButtonHeight = 48.dp
private val TopBarButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
private val LimitsButtonMinHeight = 48.dp
private val LimitsButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

// Relative heights of the colored rail segments below the elbow; the frame-colored tail fills what is left.
private const val RAIL_ICE_WEIGHT = 2f
private const val RAIL_VIOLET_WEIGHT = 1f
private const val RAIL_SUNFLOWER_WEIGHT = 3f
private const val RAIL_FRAME_WEIGHT = 4f

/**
 * Layout decision for LCARS console: wide when available width exceeds available height.
 */
internal fun isLandscape(width: Dp, height: Dp): Boolean = width > height

/**
 * The LCARS console frame: an elbow in the top-left corner that turns into a left rail, a header bar
 * across the top, and the screen content to the right of the rail. The title has a row of its own
 * above the actions so a long assistant name is never squeezed by the buttons.
 */
@Composable
fun LcarsFrame(
    title: String,
    modifier: Modifier = Modifier,
    railTopColor: Color = Color(Lcars.ICE),
    onOpenLimits: () -> Unit = {},
    showLimitsPill: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = isLandscape(maxWidth, maxHeight)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                FrameHeader(
                    title = title,
                    onOpenLimits = onOpenLimits,
                    showLimitsPill = showLimitsPill,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    landscape = landscape
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    FrameRail(
                        topColor = railTopColor,
                        modifier = Modifier.width(RailWidth).fillMaxHeight()
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        content(PaddingValues(horizontal = ContentPaddingHorizontal))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrameHeader(
    title: String,
    onOpenLimits: () -> Unit,
    showLimitsPill: Boolean,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable () -> Unit,
    landscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val frame = Color(Lcars.FRAME)
    Column(modifier = modifier.fillMaxWidth().testTag("frameHeader")) {
        if (landscape) {
            Row(modifier = Modifier.fillMaxWidth().height(ElbowHeight)) {
                Box(
                    modifier = Modifier
                        .width(ElbowWidth)
                        .fillMaxHeight()
                        .background(frame, RoundedCornerShape(topStart = ElbowRadius))
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = TitleStartPadding, end = HeaderEndPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TopBarSpacing)
                ) {
                    FrameTitle(
                        title = title,
                        navigationIcon = navigationIcon,
                        modifier = Modifier.weight(1f)
                    )
                    actions()
                    if (showLimitsPill) {
                        LimitsPill(onClick = onOpenLimits)
                    }
                }
            }
        } else {
            // The elbow stretches to whatever height the title and action rows need, so the frame stays one piece.
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(ElbowWidth)
                        .fillMaxHeight()
                        .heightIn(min = ElbowHeight)
                        .background(frame, RoundedCornerShape(topStart = ElbowRadius))
                )
                Column(modifier = Modifier.weight(1f).padding(start = TitleStartPadding, end = HeaderEndPadding)) {
                    FrameTitle(title = title, navigationIcon = navigationIcon, modifier = Modifier.fillMaxWidth())
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = ActionsBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(TopBarSpacing),
                        verticalArrangement = Arrangement.spacedBy(TopBarSpacing),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        actions()
                        if (showLimitsPill) {
                            LimitsPill(onClick = onOpenLimits)
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(BarHeight)) {
            Box(modifier = Modifier.width(ElbowWidth).fillMaxHeight().background(frame))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(frame, RoundedCornerShape(topEnd = BarHeight, bottomEnd = BarHeight))
            )
        }
    }
}

@Composable
private fun FrameTitle(
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    modifier: Modifier
) {
    Box(modifier = modifier.heightIn(min = ElbowHeight), contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(TopBarSpacing))
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FrameRail(
    topColor: Color = Color(Lcars.ICE),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(top = RailGap),
        verticalArrangement = Arrangement.spacedBy(RailGap)
    ) {
        RailBlock(topColor, RAIL_ICE_WEIGHT)
        RailBlock(Color(Lcars.VIOLET), RAIL_VIOLET_WEIGHT)
        RailBlock(Color(Lcars.SUNFLOWER), RAIL_SUNFLOWER_WEIGHT)
        RailBlock(Color(Lcars.FRAME), RAIL_FRAME_WEIGHT)
    }
}

@Composable
private fun ColumnScope.RailBlock(color: Color, weight: Float) {
    Box(modifier = Modifier.fillMaxWidth().weight(weight).background(color))
}

/** The secondary control: an ice pill with black text, since nothing in LCARS is drawn as an outline. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.secondary,
    contentColor: Color = MaterialTheme.colorScheme.onSecondary,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun NavPill(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SecondaryButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = TopBarButtonHeight),
        containerColor = color,
        contentColor = Color(Lcars.BLACK),
        contentPadding = TopBarButtonPadding
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

/** Corner shape of LCARS bubbles and cards: rounded on the start edge, nearly square on the end edge. */
fun lcarsBlockShape() = RoundedCornerShape(
    topStart = BlockStartRadius,
    bottomStart = BlockStartRadius,
    topEnd = BlockEndRadius,
    bottomEnd = BlockEndRadius
)

/** The colored bar on the start edge of an LCARS block. */
@Composable
fun RowScope.LcarsBar(color: Color) {
    Box(modifier = Modifier.width(BlockBarWidth).fillMaxHeight().background(color))
}

/** The Limits button in LCARS styling: a tomato pill with black text. */
@Composable
fun LimitsPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = LimitsButtonMinHeight)
            .testTag("limits-button")
            .semantics { contentDescription = "Limits" },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(Lcars.TOMATO),
            contentColor = Color(Lcars.BLACK)
        ),
        contentPadding = LimitsButtonPadding
    ) {
        Text(
            text = "LIMITS",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}
