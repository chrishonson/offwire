package com.chrishonson.gemmavoice.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.Lcars
import com.chrishonson.gemmavoice.resource.RESOURCE_BLOCK_COUNT
import com.chrishonson.gemmavoice.resource.resourceBlockCount

internal enum class RailColor {
    FRAME,
    TOMATO,
    ICE,
    SUNFLOWER,
    ORANGE_PULSE
}

internal enum class LevelBlockMode {
    UNLIT,
    LEVEL
}

internal data class PhaseReadoutState(
    val word: String,
    val hint: String?,
    val railColor: RailColor,
    val blockMode: LevelBlockMode
)

internal const val LISTENING_WORD = "LISTENING"
internal const val VOICE_WORD = "VOICE"
private const val LEVEL_BLOCK_COUNT = RESOURCE_BLOCK_COUNT
internal const val UNLIT_ALPHA = 0.25f
private val LevelBlockWidth = 16.dp
private val LevelBlockHeight = 12.dp
private val LevelBlockSpacing = 4.dp
private val ReadoutPaddingHorizontal = 16.dp
private val ReadoutPaddingVertical = 12.dp
private val ReadoutSpacing = 8.dp

internal fun litBlockCount(liveLevel: Float): Int =
    resourceBlockCount(liveLevel, LEVEL_BLOCK_COUNT)

internal fun showsVoiceTag(readout: PhaseReadoutState, voiceDetected: Boolean): Boolean =
    voiceDetected && readout.word == LISTENING_WORD

internal fun sentenceCasePhase(word: String): String = when (word) {
    "MIC BLOCKED" -> "Mic blocked"
    "MIC OFF" -> "Mic off"
    else -> word.lowercase().replaceFirstChar { it.uppercase() }
}

internal fun formatPhaseContentDescription(word: String, hint: String?): String {
    val sentenceWord = sentenceCasePhase(word)
    if (hint.isNullOrBlank()) {
        return "$sentenceWord."
    }
    val cleanHint = hint.replace("\"", "").trim()
    val dot = if (cleanHint.endsWith(".")) "" else "."
    return "$sentenceWord. $cleanHint$dot"
}

internal fun phaseWordColor(readout: PhaseReadoutState): Color = when {
    readout.railColor == RailColor.TOMATO && (
        readout.word == "STANDBY" ||
            readout.word == "MIC BLOCKED" ||
            readout.word == "MIC OFF"
    ) -> Color(Lcars.TOMATO)
    readout.word == LISTENING_WORD -> Color(Lcars.ICE)
    readout.word == "WORKING" -> Color(Lcars.ORANGE)
    readout.word == "SPEAKING" -> Color(Lcars.SUNFLOWER)
    else -> Color(Lcars.ORANGE)
}

internal fun phaseReadout(
    modelStatus: ModelStatus,
    micDenied: Boolean,
    livePhase: LivePhase,
    micMuted: Boolean,
    wakeWordEnabled: Boolean,
    autoListen: Boolean,
    assistantName: String = "Computer"
): PhaseReadoutState {
    if (micDenied) {
        return PhaseReadoutState(
            word = "MIC BLOCKED",
            hint = "Allow the microphone in Settings",
            railColor = RailColor.TOMATO,
            blockMode = LevelBlockMode.UNLIT
        )
    }
    val autoReadout = autoListenReadout(modelStatus, micDenied = false)
    if (autoReadout != null) {
        return PhaseReadoutState(
            word = autoReadout.phase,
            hint = autoReadout.hint,
            railColor = if (modelStatus is ModelStatus.Error) RailColor.TOMATO else RailColor.FRAME,
            blockMode = LevelBlockMode.UNLIT
        )
    }
    return when (livePhase) {
        LivePhase.LISTENING -> if (micMuted) {
            PhaseReadoutState(
                word = "MIC OFF",
                hint = null,
                railColor = RailColor.TOMATO,
                blockMode = LevelBlockMode.UNLIT
            )
        } else {
            PhaseReadoutState(
                word = LISTENING_WORD,
                hint = if (wakeWordEnabled) "Say \"${displayName(assistantName)}\" to ask" else null,
                railColor = RailColor.ICE,
                blockMode = LevelBlockMode.LEVEL
            )
        }
        LivePhase.THINKING -> PhaseReadoutState(
            word = "WORKING",
            hint = "Tap anywhere to interrupt",
            railColor = if (micMuted) RailColor.TOMATO else RailColor.ORANGE_PULSE,
            blockMode = LevelBlockMode.UNLIT
        )
        LivePhase.SPEAKING -> PhaseReadoutState(
            word = "SPEAKING",
            hint = if (micMuted) "Tap anywhere to interrupt" else "Start talking to interrupt",
            railColor = if (micMuted) RailColor.TOMATO else RailColor.SUNFLOWER,
            blockMode = LevelBlockMode.LEVEL
        )
        LivePhase.IDLE -> PhaseReadoutState(
            word = "STANDBY",
            hint = if (!autoListen) "Tap TALK to start" else null,
            railColor = RailColor.FRAME,
            blockMode = LevelBlockMode.UNLIT
        )
    }
}

@Composable
internal fun PhaseReadout(
    readout: PhaseReadoutState,
    liveLevel: Float,
    onTap: () -> Unit,
    voiceDetected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val contentDesc = formatPhaseContentDescription(readout.word, readout.hint)
    val wordColor = phaseWordColor(readout)

    val litCount = when (readout.blockMode) {
        LevelBlockMode.LEVEL -> litBlockCount(liveLevel)
        LevelBlockMode.UNLIT -> 0
    }

    val litColor = if (readout.word == "SPEAKING") Color(Lcars.SUNFLOWER) else Color(Lcars.ICE)
    val unlitColor = litColor.copy(alpha = UNLIT_ALPHA)

    Column(
        modifier = modifier
            .testTag("phaseReadout")
            .clickable(onClick = onTap)
            .semantics(mergeDescendants = true) {
                contentDescription = contentDesc
            }
            .padding(horizontal = ReadoutPaddingHorizontal, vertical = ReadoutPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(ReadoutSpacing)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ReadoutSpacing),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = readout.word.uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = wordColor
            )
            if (showsVoiceTag(readout, voiceDetected)) {
                Text(
                    text = VOICE_WORD,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(Lcars.SUNFLOWER),
                    modifier = Modifier.testTag("voiceDetected")
                )
            }
        }
        if (readout.hint != null) {
            Text(
                text = readout.hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LevelBlocksRow(
            litCount = litCount,
            litColor = litColor,
            unlitColor = unlitColor
        )
    }
}

@Composable
private fun LevelBlocksRow(
    litCount: Int,
    litColor: Color,
    unlitColor: Color,
    modifier: Modifier = Modifier
) {
    BlockRow(
        litCount = litCount,
        litColor = litColor,
        unlitColor = unlitColor,
        modifier = modifier
    )
}

@Composable
internal fun BlockRow(
    litCount: Int,
    litColor: Color,
    unlitColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LevelBlockSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until LEVEL_BLOCK_COUNT) {
            val color = if (i < litCount) litColor else unlitColor
            Box(
                modifier = Modifier
                    .width(LevelBlockWidth)
                    .height(LevelBlockHeight)
                    .background(color)
            )
        }
    }
}
