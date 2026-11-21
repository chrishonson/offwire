package com.chrishonson.gemmavoice.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import com.chrishonson.gemmavoice.Lcars
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
private val MeterFontSize = 9.sp
private val MeterLineHeight = 12.sp
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resourceMeter")
    ) {
        ResourceRow(
            label = "CPU",
            litCount = resourceBlockCount(animatedCpu),
            valueText = formatCpuValue(sample.cpuPercent, cores).uppercase(),
            accentColor = Color(Lcars.ORANGE),
            tag = "resourceRowCpu"
        )
        ResourceRow(
            label = "MEM",
            litCount = resourceBlockCount(animatedMem),
            valueText = formatMemoryValue(sample.pssMb).uppercase(),
            accentColor = Color(Lcars.ICE),
            tag = "resourceRowMem"
        )
        speed?.let {
            Text(
                text = formatReplySpeed(it).uppercase(),
                style = TextStyle(fontSize = MeterFontSize, lineHeight = MeterLineHeight),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val textStyle = TextStyle(fontSize = MeterFontSize, lineHeight = MeterLineHeight)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
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
            style = textStyle,
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
            style = textStyle,
            color = textColor,
            maxLines = 1
        )
    }
}
