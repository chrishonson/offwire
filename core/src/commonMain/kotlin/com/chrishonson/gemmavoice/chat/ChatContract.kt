package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.llm.ReplySpeed
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.resource.ResourceSample

enum class Role {
    USER,
    ASSISTANT,
    // Speech heard while waiting for the wake word: shown and kept as context, never answered.
    OVERHEARD
}

enum class LivePhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val isVoice: Boolean = false
)

sealed interface ModelStatus {
    data object Missing : ModelStatus
    data class ConfirmDownload(val totalBytes: Long) : ModelStatus
    data class WifiRequired(val totalBytes: Long) : ModelStatus
    data class Downloading(val fraction: Float) : ModelStatus
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Error(val message: String) : ModelStatus
}

/** A download or load in flight holds the current model until it finishes. */
fun ModelStatus.allowsModelSwitch(): Boolean = this !is ModelStatus.Downloading && this !is ModelStatus.Loading

data class ChatState(
    /** The language models this device can run, in the order the picker shows them. */
    val models: List<ModelSpec> = Models.GEMMA,
    val modelId: String = Models.GEMMA_4_E2B.id,
    val modelName: String = Models.GEMMA_4_E2B.displayName,
    val modelStatus: ModelStatus = ModelStatus.Missing,
    val allowCellularDownload: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val livePhase: LivePhase = LivePhase.IDLE,
    val liveLevel: Float = 0f,
    val voiceDetected: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val autoListen: Boolean = false,
    val assistantName: String = "",
    val micDenied: Boolean = false,
    val micMuted: Boolean = false,
    // Null while the rename dialog is closed.
    val renameDraft: String? = null,
    val renameError: String? = null,
    val networkActive: Boolean = false,
    val offlineVoiceMissing: Boolean = false,
    val showResourceMeter: Boolean = false,
    val thinking: Boolean = false,
    val resources: ResourceSample? = null,
    // The latest reply's speed, shown with the resource meter. Null until a reply streams its first text.
    val replySpeed: ReplySpeed? = null
)

sealed interface ChatIntent {
    data object Start : ChatIntent
    data object DownloadModel : ChatIntent
    data object ConfirmDownload : ChatIntent
    data object CancelDownload : ChatIntent
    data object ToggleCellularDownload : ChatIntent
    data class InputChanged(val text: String) : ChatIntent
    data object Send : ChatIntent
    data object Stop : ChatIntent
    data object NewChat : ChatIntent
    data object EnterLiveMode : ChatIntent
    data object ExitLiveMode : ChatIntent
    data object LiveTap : ChatIntent
    data object ToggleMute : ChatIntent
    data object ToggleWakeWord : ChatIntent
    data object ToggleAutoListen : ChatIntent
    data object ToggleResourceMeter : ChatIntent
    data object ToggleThinking : ChatIntent
    data class SelectModel(val id: String) : ChatIntent
    /** Something else used the engine, such as the benchmark: load the current model again in a new chat. */
    data object ReloadModel : ChatIntent
    data object AppForegrounded : ChatIntent
    data object OpenRename : ChatIntent
    data class RenameInputChanged(val text: String) : ChatIntent
    data object SaveRename : ChatIntent
    data object CancelRename : ChatIntent
}
