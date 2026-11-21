package com.chrishonson.gemmavoice.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrishonson.gemmavoice.LedPanel
import com.chrishonson.gemmavoice.LocalHardwareColors
import com.chrishonson.gemmavoice.LocalHardwareFonts
import com.chrishonson.gemmavoice.llm.ReplySpeed
import com.chrishonson.gemmavoice.llm.formatReplySpeed
import com.chrishonson.gemmavoice.resource.RESOURCE_BLOCK_COUNT
import com.chrishonson.gemmavoice.resource.ResourceSample
import com.chrishonson.gemmavoice.resource.cpuCoreCount
import com.chrishonson.gemmavoice.resource.cpuFraction
import com.chrishonson.gemmavoice.resource.formatCpuValue
import com.chrishonson.gemmavoice.resource.formatMemoryValue
import com.chrishonson.gemmavoice.resource.memoryFraction
import com.chrishonson.gemmavoice.resource.resourceBlockCount

private val RowHeight = 16.dp
private val LabelWidth = 28.dp
private val RowSpacing = 6.dp
private val BlockWidth = 16.dp
private val BlockHeight = 12.dp
private val BlockSpacing = 4.dp
private val MeterFontSize = 9.sp
private val MeterLineHeight = 12.sp
// VT323 draws small for its size; this sets the numbers at about the label height.
private val ReadoutFontSize = 13.sp
// No vertical padding: the panel keeps the meter at its two 16 dp rows.
private val PanelPadding = PaddingValues(horizontal = 8.dp)
private const val UNLIT_ALPHA = 0.25f
private const val RAMP_DURATION_MS = 300

@Composable
internal fun ResourceMeter(
    sample: ResourceSample?,
    modifier: Modifier = Modifier,
    speed: ReplySpeed? = null,
    cores: Int = cpuCoreCount()
) {
    if (sample == null) return
    val animatedCpu by animateFloatAsState(
        targetValue = cpuFraction(sample.cpuPercent, cores),
        animationSpec = tween(durationMillis = RAMP_DURATION_MS),
        label = "cpuFraction"
    )
    val animatedMem by animateFloatAsState(
        targetValue = memoryFraction(sample.pssMb, sample.availMb),
        animationSpec = tween(durationMillis = RAMP_DURATION_MS),
        label = "memFraction"
    )
    val hardware = LocalHardwareColors.current
    LedPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resourceMeter"),
        contentPadding = PanelPadding
    ) {
        ResourceRow(
            label = "CPU",
            litCount = resourceBlockCount(animatedCpu),
            valueText = formatCpuValue(sample.cpuPercent, cores),
            accentColor = hardware.ledGreen,
            tag = "resourceRowCpu"
        )
        ResourceRow(
            label = "MEM",
            litCount = resourceBlockCount(animatedMem),
            valueText = formatMemoryValue(sample.pssMb),
            accentColor = hardware.ledAmber,
            tag = "resourceRowMem"
        )
        speed?.let {
            Text(
                text = formatReplySpeed(it),
                style = TextStyle(
                    fontFamily = LocalHardwareFonts.current.readout,
                    fontSize = MeterFontSize,
                    lineHeight = MeterLineHeight
                ),
                color = hardware.readoutDim,
                maxLines = 1,
                modifier = Modifier.testTag("replySpeed")
            )
        }
    }
}

@Composable
private fun ResourceRow(
    label: String,
    litCount: Int,
    valueText: String,
    accentColor: Color,
    tag: String
) {
    val fonts = LocalHardwareFonts.current
    val labelStyle = TextStyle(fontFamily = fonts.readout, fontSize = MeterFontSize, lineHeight = MeterLineHeight)
    val valueStyle = TextStyle(fontFamily = fonts.lcd, fontSize = ReadoutFontSize, lineHeight = MeterLineHeight)
    val textColor = LocalHardwareColors.current.readout
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .testTag(tag)
            .semantics { stateDescription = "$litCount of $RESOURCE_BLOCK_COUNT" },
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.width(LabelWidth)
        )
        BlockRow(
            litCount = litCount,
            litColor = accentColor,
            unlitColor = accentColor.copy(alpha = UNLIT_ALPHA)
        )
        Text(
            text = valueText,
            style = valueStyle,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun BlockRow(
    litCount: Int,
    litColor: Color,
    unlitColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BlockSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until RESOURCE_BLOCK_COUNT) {
            val color = if (i < litCount) litColor else unlitColor
            Box(
                modifier = Modifier
                    .width(BlockWidth)
                    .height(BlockHeight)
                    .background(color)
            )
        }
    }
}
