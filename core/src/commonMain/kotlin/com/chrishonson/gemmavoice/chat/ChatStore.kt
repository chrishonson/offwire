@file:Suppress("TooManyFunctions")

package com.chrishonson.gemmavoice.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.llm.ReplySpeedMeter
import com.chrishonson.gemmavoice.llm.monotonicClockMs
import com.chrishonson.gemmavoice.model.AssumeUnmetered
import com.chrishonson.gemmavoice.model.ModelRepository
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.model.NetworkMonitor
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.resource.ResourceMonitor
import com.chrishonson.gemmavoice.resource.ResourceSampler
import com.chrishonson.gemmavoice.voice.AudioStream
import com.chrishonson.gemmavoice.voice.ClauseChunker
import com.chrishonson.gemmavoice.voice.EndpointResult
import com.chrishonson.gemmavoice.voice.Endpointer
import com.chrishonson.gemmavoice.voice.EnergyVad
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.MicPermission
import com.chrishonson.gemmavoice.voice.OVERHEARD_HEADER
import com.chrishonson.gemmavoice.voice.SettingsStore
import com.chrishonson.gemmavoice.voice.SpeechSynthesizer
import com.chrishonson.gemmavoice.voice.SpeechToText
import com.chrishonson.gemmavoice.voice.VoiceActivityDetector
import com.chrishonson.gemmavoice.voice.VoiceConfig
import com.chrishonson.gemmavoice.voice.WavEncoder
import com.chrishonson.gemmavoice.voice.addressed
import com.chrishonson.gemmavoice.voice.expectingReply
import com.chrishonson.gemmavoice.voice.isSpeechTranscript
import com.chrishonson.gemmavoice.voice.onlyWakeWord
import com.chrishonson.gemmavoice.voice.rms16
import com.chrishonson.gemmavoice.voice.startingAssistantName
import com.chrishonson.gemmavoice.voice.validateAssistantName
import com.chrishonson.gemmavoice.voice.withOverheard
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val VOICE_INSTRUCTION =
    "Listen to the user's spoken message and reply to it directly and conversationally. " +
        "Do not describe or transcribe the audio."

// A reply cut off before its first word leaves nothing worth showing, so its empty bubble goes.
internal fun finishStreaming(messages: List<ChatMessage>): List<ChatMessage> = messages.mapNotNull { msg ->
    when {
        !msg.isStreaming -> msg
        msg.text.isBlank() -> null
        else -> msg.copy(isStreaming = false)
    }
}

const val WAKE_ACK_TEXT = "Yes?"

const val RENAME_ERROR_TEXT = "Use one or two words, letters only, 2 to 20 letters long."

private const val OVERHEARD_FOOTER = "The attached audio is addressed to you."
private const val WAV_BYTES_PER_SECOND = 32000.0
private const val TENTHS_MULTIPLIER = 10.0
private const val TENTHS_DIVISOR = 10
private const val LEVEL_UPDATE_INTERVAL_MS = 100
private const val LEVEL_SCALE = 3.0
private const val RESOURCE_SAMPLE_INTERVAL_MS = 500L
private const val RESOURCE_IDLE_SAMPLE_INTERVAL_MS = 2000L
private const val RESOURCE_TAIL_MS = 2000L

@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
class ChatStore(
    private val engine: LlmEngine,
    private val repo: ModelRepository,
    private val audioStream: AudioStream,
    private val synthesizer: SpeechSynthesizer,
    private val micPermission: MicPermission,
    private val stt: SpeechToText,
    private val config: VoiceConfig = VoiceConfig(),
    private val vad: VoiceActivityDetector = EnergyVad(config),
    private val models: List<ModelSpec> = Models.GEMMA,
    private val settings: SettingsStore = InMemorySettingsStore(),
    wakeWordDefault: Boolean = false,
    autoListen: Boolean = false,
    private val voiceSpec: ModelSpec? = Models.POCKET_TTS,
    private val networkActivity: NetworkActivity,
    private val network: NetworkMonitor = AssumeUnmetered,
    private val resourceMonitor: ResourceSampler = ResourceMonitor(),
    private val resourceDispatcher: CoroutineDispatcher = Dispatchers.Default,
    clockMs: () -> Long = monotonicClockMs()
) : ViewModel() {
    private val replySpeedMeter = ReplySpeedMeter(clockMs)
    private var spec: ModelSpec = models.firstOrNull { it.id == settings.loadModelId() } ?: models.first()


    // The language model and the spoken voice are one download step: the app is ready when both are here.
    private val specs: List<ModelSpec>
        get() = listOfNotNull(spec, voiceSpec)
    private val initialName = startingAssistantName(settings, config.assistantName)
    private val _state = MutableStateFlow(
        ChatState(
            models = models,
            modelId = spec.id,
            modelName = spec.displayName,
            assistantName = initialName,
            allowCellularDownload = settings.loadAllowCellularDownload(),
            wakeWordEnabled = settings.loadWakeWord() ?: wakeWordDefault,
            autoListen = settings.loadAutoListen() ?: autoListen,
            networkActive = networkActivity.isActive.value,
            offlineVoiceMissing = !synthesizer.offlineVoiceAvailable.value,
            showResourceMeter = settings.loadShowResourceMeter(),
            thinking = settings.loadThinking()
        )
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        engine.setThinking(_state.value.thinking)
        viewModelScope.launch {
            networkActivity.isActive.collect { active ->
                _state.update { it.copy(networkActive = active) }
            }
        }
        viewModelScope.launch {
            synthesizer.offlineVoiceAvailable.collect { available ->
                _state.update { it.copy(offlineVoiceMissing = !available) }
            }
        }
        viewModelScope.launch {
            synthesizer.outputLevel.collect { level ->
                _state.update { current ->
                    if (current.livePhase == LivePhase.SPEAKING) {
                        current.copy(liveLevel = level)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private val endpointer = Endpointer(config, vad)
    private val levelThrottleFrames = (LEVEL_UPDATE_INTERVAL_MS / config.frameMs).coerceAtLeast(1)
    private var nextMessageId = 0L
    private var generationJob: Job? = null
    private var micJob: Job? = null

    // True until a prompt has gone to the engine since its last reset; the saved exchanges ride on that prompt.
    private var framesSinceLevelUpdate = 0
    private var lastSentPcm: ByteArray? = null
    private var pendingMergePcm: ByteArray? = null
    private var mergeableAssistantId: Long? = null
    private var currentTurnSpoke = false
    private var currentTurnAssistantId: Long? = null
    private var msSinceLastSpokenReply: Long? = null
    private var utteranceStartedInFollowUp = false
    // Set when the user's voice cut a reply short: that utterance is addressed to the assistant, wake word or not.
    private var bargeInUtterance = false
    private var msSinceReplySpeech = Long.MAX_VALUE
    private var msSinceInterruptOrReply = 0L
    private val overheard = ArrayDeque<String>()
    private var woken = false
    private var expectingReplyFromLastReply = false
    private var transcribing = false
    // Speech keeps being captured while an utterance is transcribed; an endpoint reached meanwhile waits here.
    private var deferredEndpoint = false
    private var liveSession = 0
    private var currentTurnCanBargeIn = false
    private var currentTurnUserId: Long? = null
    private var currentTurnUserText: String? = null
    private var mergeableUserId: Long? = null
    private var mergeableCanBargeIn = false
    private var pendingMergeTranscript: String? = null
    private var resourceSamplingJob: Job? = null
    private var resourceGraceJob: Job? = null
    private var resourceTurnActive = false

    /** A turn has gone inactive and its peaks are not logged yet: the tail or the next turn logs them. */
    private var resourceTurnEnded = false
    private var peakCpuPercent: Double? = null
    private var peakPssMb: Double? = null
    private var wakeWords: List<String> =
        if (initialName == config.assistantName) config.wakeWords else listOf(initialName.lowercase())

    init {
        viewModelScope.launch {
            // One state flow, not one per field: a single update that changes two fields must not
            // be seen as two steps with a false "inactive" in between.
            combine(state, synthesizer.isSpeaking) { s, isSpeaking ->
                val liveActive = s.livePhase == LivePhase.THINKING || s.livePhase == LivePhase.SPEAKING
                s.showResourceMeter to (liveActive || s.isGenerating || isSpeaking)
            }
                .distinctUntilChanged()
                .collect { (enabled, active) -> updateResourceSampling(enabled, active) }
        }
    }

    // A flat intent dispatcher: each entry is a single delegation.
    @Suppress("CyclomaticComplexMethod")
    fun dispatch(intent: ChatIntent) {
        when (intent) {
            ChatIntent.Start -> handleStart()
            ChatIntent.DownloadModel -> handleDownloadModel()
            ChatIntent.ConfirmDownload -> handleConfirmDownload()
            ChatIntent.CancelDownload -> handleCancelDownload()
            ChatIntent.ToggleCellularDownload -> handleToggleCellularDownload()
            is ChatIntent.InputChanged -> _state.update { it.copy(input = intent.text) }
            ChatIntent.Send -> handleSend()
            ChatIntent.Stop -> handleStop()
            ChatIntent.NewChat -> handleNewChat()
            ChatIntent.EnterLiveMode -> handleEnterLiveMode()
            ChatIntent.ExitLiveMode -> handleExitLiveMode()
            ChatIntent.LiveTap -> handleLiveTap()
            ChatIntent.ToggleMute -> handleToggleMute()
            ChatIntent.ToggleWakeWord -> handleToggleWakeWord()
            ChatIntent.ToggleAutoListen -> handleToggleAutoListen()
            ChatIntent.ToggleResourceMeter -> handleToggleResourceMeter()
            ChatIntent.ToggleThinking -> handleToggleThinking()
            is ChatIntent.SelectModel -> handleSelectModel(intent.id)
            ChatIntent.ReloadModel -> reloadModel()
            ChatIntent.AppForegrounded -> handleAppForegrounded()
            ChatIntent.OpenRename -> updateRenameDraft(_state.value.assistantName)
            is ChatIntent.RenameInputChanged -> updateRenameDraft(intent.text)
            ChatIntent.SaveRename -> handleSaveRename()
            ChatIntent.CancelRename -> _state.update { it.copy(renameDraft = null, renameError = null) }
        }
    }

    private fun handleStart() {
        val status = _state.value.modelStatus
        if (status is ModelStatus.Loading || status is ModelStatus.Ready || status is ModelStatus.Downloading) {
            return
        }
        if (specs.all { repo.isDownloaded(it) }) {
            viewModelScope.launch {
                loadModel()
            }
        } else {
            _state.update { it.copy(modelStatus = ModelStatus.Missing) }
        }
    }

    private fun missingBytes(): Long = specs.filterNot { repo.isDownloaded(it) }.sumOf { it.sizeBytes }

    private fun handleDownloadModel() {
        val current = _state.value
        val status = current.modelStatus
        if (status is ModelStatus.Downloading || status is ModelStatus.Loading) {
            return
        }
        val bytes = missingBytes()
        val nextStatus = if (!network.isUnmetered() && !current.allowCellularDownload) {
            ModelStatus.WifiRequired(bytes)
        } else {
            ModelStatus.ConfirmDownload(bytes)
        }
        _state.update { it.copy(modelStatus = nextStatus) }
    }

    private fun handleConfirmDownload() {
        val current = _state.value
        if (current.modelStatus !is ModelStatus.ConfirmDownload) {
            return
        }
        if (!network.isUnmetered() && !current.allowCellularDownload) {
            _state.update { it.copy(modelStatus = ModelStatus.WifiRequired(missingBytes())) }
            return
        }
        _state.update { it.copy(modelStatus = ModelStatus.Downloading(0f)) }
        viewModelScope.launch {
            try {
                downloadMissing()
                loadModel()
            } catch (e: CancellationException) {
                throw e
            } catch (expected: Exception) {
                _state.update {
                    it.copy(modelStatus = ModelStatus.Error(expected.message ?: "Download failed"))
                }
            }
        }
    }

    private fun handleCancelDownload() {
        val status = _state.value.modelStatus
        if (status is ModelStatus.ConfirmDownload || status is ModelStatus.WifiRequired) {
            _state.update { it.copy(modelStatus = ModelStatus.Missing) }
        }
    }

    private fun handleToggleCellularDownload() {
        val nextAllow = !_state.value.allowCellularDownload
        settings.saveAllowCellularDownload(nextAllow)
        _state.update { current ->
            val nextStatus = if (current.modelStatus is ModelStatus.WifiRequired && nextAllow) {
                val bytes = current.modelStatus.totalBytes
                ModelStatus.ConfirmDownload(bytes)
            } else {
                current.modelStatus
            }
            current.copy(
                allowCellularDownload = nextAllow,
                modelStatus = nextStatus
            )
        }
    }

    // One progress bar across every missing file, weighted by size.
    private suspend fun downloadMissing() {
        val missing = specs.filterNot { repo.isDownloaded(it) }
        val totalBytes = missing.sumOf { it.sizeBytes }
        var doneBytes = 0L
        for (item in missing) {
            repo.download(item).collect { bytesDownloaded ->
                val fraction = if (totalBytes > 0L) {
                    ((doneBytes + bytesDownloaded).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                _state.update { it.copy(modelStatus = ModelStatus.Downloading(fraction)) }
            }
            doneBytes += item.sizeBytes
        }
    }

    private suspend fun loadModel() {
        _state.update { it.copy(modelStatus = ModelStatus.Loading) }
        // The mic and speech-to-text do not need the model: open listening at once and let the first question
        // wait for the model inside generation (card 216).
        if (_state.value.autoListen) {
            handleEnterLiveMode()
        }
        try {
            engine.load(repo.modelPath(spec))
            _state.update { it.copy(modelStatus = ModelStatus.Ready) }
        } catch (e: CancellationException) {
            throw e
        } catch (expected: Exception) {
            _state.update {
                it.copy(modelStatus = ModelStatus.Error(expected.message ?: "Failed to load model"))
            }
            // Listening opened ahead of the load; a failed model has nothing to answer with.
            if (_state.value.livePhase != LivePhase.IDLE) handleExitLiveMode()
        }
    }

    private fun handleSend() {
        val current = _state.value
        if (current.modelStatus != ModelStatus.Ready || current.isGenerating || current.input.isBlank()) {
            return
        }
        runGeneration(LlmPrompt.Text(current.input))
    }

    private fun handleEnterLiveMode() {
        val current = _state.value
        if (!current.modelStatus.allowsListening() ||
            current.isGenerating ||
            current.livePhase != LivePhase.IDLE
        ) {
            return
        }
        synthesizer.stop()
        viewModelScope.launch {
            if (!micPermission.ensureGranted()) {
                _state.update { it.copy(micDenied = true) }
                return@launch
            }
            val latest = _state.value
            if (!latest.modelStatus.allowsListening() ||
                latest.isGenerating ||
                latest.livePhase != LivePhase.IDLE
            ) {
                return@launch
            }
            resetLiveTurnTracking()
            resetEndpointerAndVoice()
            framesSinceLevelUpdate = 0
            _state.update {
                it.copy(
                    livePhase = LivePhase.LISTENING,
                    liveLevel = 0f,
                    micDenied = false,
                    micMuted = false
                )
            }
            startMicCollection()
        }
    }

    private fun startMicCollection() {
        micJob?.cancel()
        micJob = viewModelScope.launch {
            audioStream.frames().collect { frame ->
                onAudioFrame(frame)
            }
        }
    }

    private fun handleToggleMute() {
        if (_state.value.livePhase == LivePhase.IDLE) return
        if (_state.value.micMuted) {
            _state.update { it.copy(micMuted = false, liveLevel = 0f) }
            resetEndpointerAndVoice()
            framesSinceLevelUpdate = 0
            startMicCollection()
        } else {
            micJob?.cancel()
            micJob = null
            resetEndpointerAndVoice()
            framesSinceLevelUpdate = 0
            bargeInUtterance = false
            deferredEndpoint = false
            pendingMergePcm = null
            pendingMergeTranscript = null
            mergeableAssistantId = null
            mergeableUserId = null
            mergeableCanBargeIn = false
            transcribing = false
            _state.update { it.copy(micMuted = true, liveLevel = 0f, voiceDetected = false) }
        }
    }

    private fun onAudioFrame(frame: ByteArray) {
        val frameDurationMs = (frame.size / config.bytesPerMs).toLong()
        if (hearsOwnReply(frameDurationMs)) return
        when (_state.value.livePhase) {
            LivePhase.IDLE -> Unit
            LivePhase.LISTENING -> handleListeningFrame(frame, frameDurationMs)
            LivePhase.THINKING, LivePhase.SPEAKING -> {
                if (!config.halfDuplex && config.bargeInEnabled && canBargeIn()) {
                    endpointer.push(frame)
                    if (endpointer.bargeInTriggered) {
                        interruptLiveTurn(resetEndpointer = false)
                    }
                }
            }
        }
    }

    private fun hearsOwnReply(frameDurationMs: Long): Boolean {
        if (!config.ignoreMicWhileSpeaking) return false
        if (synthesizer.isSpeaking.value) {
            msSinceReplySpeech = 0L
            return true
        }
        if (msSinceReplySpeech >= config.echoTailMs) return false
        msSinceReplySpeech += frameDurationMs
        return true
    }

    // Frames are dropped while an utterance is being transcribed and while the wake acknowledgement plays.
    private fun canBargeIn(): Boolean = !_state.value.wakeWordEnabled || currentTurnCanBargeIn

    private fun handleListeningFrame(frame: ByteArray, frameDurationMs: Long) {
        if (framesSinceLevelUpdate % levelThrottleFrames == 0) {
            val level = (rms16(frame) * LEVEL_SCALE).toFloat().coerceIn(0f, 1f)
            _state.update { it.copy(liveLevel = level) }
        }
        framesSinceLevelUpdate++
        val wasVoiced = endpointer.voicedMs > 0
        val result = endpointer.push(frame)
        val nowVoiced = endpointer.voicedMs > 0
        if (wasVoiced != nowVoiced) {
            _state.update { it.copy(voiceDetected = nowVoiced) }
        }
        if (!wasVoiced && nowVoiced) {
            val elapsedSinceReply = msSinceLastSpokenReply
            utteranceStartedInFollowUp =
                elapsedSinceReply != null && elapsedSinceReply <= config.followUpMs
            bargeInUtterance = false
            if (msSinceInterruptOrReply > config.mergeMs) {
                pendingMergePcm = null
                pendingMergeTranscript = null
                mergeableAssistantId = null
                mergeableUserId = null
                mergeableCanBargeIn = false
            }
        } else if (!nowVoiced) {
            utteranceStartedInFollowUp = false
            msSinceInterruptOrReply += frameDurationMs
        }
        msSinceLastSpokenReply = msSinceLastSpokenReply?.plus(frameDurationMs)
        if (result == EndpointResult.ENDPOINT) {
            if (transcribing) deferredEndpoint = true else triggerEndpoint()
        }
    }

    // The flag mirrors the endpointer, so every reset of the endpointer clears it too.
    private fun resetEndpointerAndVoice() {
        endpointer.reset()
        _state.update { it.copy(voiceDetected = false) }
    }

    private fun triggerEndpoint() {
        val newPcm = endpointer.take()
        val previousPcm = pendingMergePcm
        val previousTranscript = pendingMergeTranscript
        pendingMergePcm = null
        pendingMergeTranscript = null
        val droppedIds = setOfNotNull(mergeableAssistantId, mergeableUserId)
        val mergedCanBargeIn = mergeableCanBargeIn
        mergeableAssistantId = null
        mergeableUserId = null
        mergeableCanBargeIn = false
        val pcm = if (previousPcm != null) {
            previousPcm + newPcm
        } else {
            newPcm
        }
        val inFollowUp = utteranceStartedInFollowUp
        utteranceStartedInFollowUp = false
        msSinceLastSpokenReply = null
        framesSinceLevelUpdate = 0
        _state.update { it.copy(voiceDetected = false) }
        val expecting = expectingReplyFromLastReply
        expectingReplyFromLastReply = false
        val bargedIn = bargeInUtterance
        bargeInUtterance = false
        val utterance = Utterance(
            pcm = pcm,
            newPcm = newPcm,
            previousTranscript = previousTranscript,
            droppedIds = droppedIds,
            bypassesWakeWord = inFollowUp || expecting || mergedCanBargeIn || bargedIn
        )
        if (!_state.value.wakeWordEnabled && !stt.isAvailable) {
            sendToModel(utterance, VOICE_INSTRUCTION, userText = null, canBargeIn = true)
        } else {
            transcribeThenRoute(utterance)
        }
    }

    private fun transcribeThenRoute(utterance: Utterance) {
        val session = liveSession
        transcribing = true
        val pcmToTranscribe = if (_state.value.wakeWordEnabled) {
            utterance.pcm
        } else {
            utterance.newPcm
        }
        viewModelScope.launch {
            var sttFailed = false
            val transcript = try {
                stt.transcribe(pcmToTranscribe, config.sampleRateHz).trim()
            } catch (ignored: TimeoutCancellationException) {
                sttFailed = true
                ""
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                sttFailed = true
                ""
            }
            transcribing = false
            if (session != liveSession ||
                _state.value.livePhase != LivePhase.LISTENING ||
                _state.value.micMuted
            ) {
                deferredEndpoint = false
                resetEndpointerAndVoice()
                return@launch
            }
            if (_state.value.wakeWordEnabled) {
                routeWakeUtterance(utterance, transcript)
            } else {
                routeNonWakeUtterance(utterance, transcript, sttFailed)
            }
            if (_state.value.livePhase != LivePhase.LISTENING) {
                // The utterance just routed became a turn; what was buffered meanwhile must not leak into the next.
                deferredEndpoint = false
                resetEndpointerAndVoice()
            } else if (deferredEndpoint) {
                deferredEndpoint = false
                if (!_state.value.micMuted && endpointer.hasSpeech) triggerEndpoint()
            }
        }
    }

    private suspend fun routeNonWakeUtterance(utterance: Utterance, transcript: String, sttFailed: Boolean) {
        if (sttFailed) {
            sendToModel(utterance, VOICE_INSTRUCTION, userText = null, canBargeIn = true)
            return
        }
        if (!isSpeechTranscript(transcript)) {
            dropMessages(utterance.droppedIds)
            resumeListening()
            return
        }
        val userText = listOfNotNull(utterance.previousTranscript, transcript)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }
        dropMessages(utterance.droppedIds)
        sendToModel(
            utterance = utterance,
            instruction = VOICE_INSTRUCTION,
            userText = userText,
            canBargeIn = true,
            modelText = userText.takeIf { sendsTranscript() }
        )
    }

    // Silence or noise that reached the endpointer is not a question, even inside the follow-up window.
    private fun isNoise(transcript: String): Boolean = !isSpeechTranscript(transcript)

    private suspend fun routeWakeUtterance(utterance: Utterance, transcript: String) {
        dropMessages(utterance.droppedIds)
        if (isNoise(transcript)) {
            resumeListening()
            return
        }
        val wasWoken = woken
        val isAddressed = wasWoken || utterance.bypassesWakeWord || addressed(transcript, wakeWords)
        when {
            !isAddressed -> recordOverheard(transcript)
            // The acknowledgement is never spoken: with the speaker path warm it talks over a question that
            // starts within a second of the wake word and garbles it (measured on the Pixel 9 Pro, card 220).
            // The YOU entry and the readout carry the acknowledgement instead.
            onlyWakeWord(transcript, wakeWords) && overheard.isEmpty() -> ackWakeWord(transcript)
            else -> dispatchWakeUtterance(utterance, transcript)
        }
    }

    private fun recordOverheard(transcript: String) {
        if (transcript.isNotBlank()) {
            val line = ChatMessage(++nextMessageId, Role.OVERHEARD, transcript, isVoice = true)
            _state.update { it.copy(messages = it.messages + line) }
            overheard.addLast(transcript)
            while (overheard.size > config.overheardLines) overheard.removeFirst()
        }
        resumeListening()
    }

    // The wake word is acknowledged by its YOU entry and the readout only. A spoken "Yes?" talked over any
    // question that started within a second of the name and garbled it (card 220, Pixel 9 Pro).
    private fun ackWakeWord(transcript: String) {
        woken = true
        addVoiceUserBubble(transcript)
        resumeListening()
    }

    private suspend fun dispatchWakeUtterance(
        utterance: Utterance,
        transcript: String
    ) {
        woken = false
        val context = overheard.toList()
        overheard.clear()
        // Only an addressed utterance reaches here, so the assistant's name in the same breath counts as a wake.
        val canBargeIn = true
        sendToModel(
            utterance = utterance,
            instruction = voiceInstruction(context),
            userText = transcript.ifBlank { null },
            canBargeIn = canBargeIn,
            modelText = withOverheard(context, transcript).takeIf {
                sendsTranscript() && isSpeechTranscript(transcript)
            }
        )
    }

    // Gemma answers the transcript unless audio is forced or there is no transcript to send.
    private fun sendsTranscript(): Boolean = !config.sendAudioToModel && stt.isAvailable

    private fun voiceInstruction(overheardLines: List<String>): String =
        if (overheardLines.isEmpty()) {
            VOICE_INSTRUCTION
        } else {
            "$VOICE_INSTRUCTION\n\n$OVERHEARD_HEADER\n${overheardLines.joinToString("\n")}\n\n$OVERHEARD_FOOTER"
        }

    private fun resumeListening() {
        // Speech captured while the last utterance was transcribed stays in the endpointer.
        if (endpointer.voicedMs == 0) resetEndpointerAndVoice()
        framesSinceLevelUpdate = 0
        _state.update { it.copy(liveLevel = 0f) }
    }

    private fun addVoiceUserBubble(text: String) {
        val message = ChatMessage(++nextMessageId, Role.USER, text, isVoice = true)
        _state.update { it.copy(messages = it.messages + message) }
    }

    private fun dropMessages(ids: Set<Long>) {
        if (ids.isEmpty()) return
        _state.update { current -> current.copy(messages = current.messages.filterNot { it.id in ids }) }
    }

    private fun sendToModel(
        utterance: Utterance,
        instruction: String,
        userText: String?,
        canBargeIn: Boolean,
        modelText: String? = null
    ) {
        lastSentPcm = utterance.pcm
        currentTurnCanBargeIn = canBargeIn
        _state.update { current ->
            current.copy(
                messages = current.messages.filterNot { it.id in utterance.droppedIds },
                livePhase = LivePhase.THINKING,
                liveLevel = 0f
            )
        }
        val prompt = if (modelText != null) {
            LlmPrompt.Text(modelText)
        } else {
            val wav = WavEncoder.encodePcm16Mono(pcm = utterance.pcm, sampleRate = config.sampleRateHz)
            LlmPrompt.Audio(wav, instruction)
        }
        runGeneration(prompt, userText)
    }

    private fun interruptLiveTurn(resetEndpointer: Boolean) {
        val isBargeInBeforeSpeech = !resetEndpointer && !currentTurnSpoke
        // Voice barge-in keeps capturing the utterance that interrupted; STOP and taps reset it instead.
        bargeInUtterance = !resetEndpointer
        // A long previous utterance is not merged again, so continuous speech cannot grow the prompt without bound.
        val mergeable = lastSentPcm?.let { it.size <= config.maxMergeMs * config.bytesPerMs } == true
        val shouldMerge = isBargeInBeforeSpeech && config.mergeMs > 0 && mergeable
        val assistantToDrop = if (shouldMerge) currentTurnAssistantId else null
        if (shouldMerge) {
            pendingMergePcm = lastSentPcm
            pendingMergeTranscript = currentTurnUserText
            mergeableAssistantId = currentTurnAssistantId
            mergeableUserId = currentTurnUserId
            mergeableCanBargeIn = currentTurnCanBargeIn
            msSinceInterruptOrReply = 0L
        } else {
            pendingMergePcm = null
            pendingMergeTranscript = null
            mergeableAssistantId = null
            mergeableUserId = null
            mergeableCanBargeIn = false
        }
        currentTurnAssistantId = null
        currentTurnUserId = null
        currentTurnUserText = null
        currentTurnSpoke = false
        generationJob?.cancel()
        generationJob = null
        if (shouldMerge) engine.cancelForResend() else engine.cancel()
        synthesizer.stop()
        if (resetEndpointer) {
            resetEndpointerAndVoice()
        }
        framesSinceLevelUpdate = 0
        _state.update { current ->
            val updatedMessages = if (assistantToDrop != null) {
                current.messages.filterNot { it.id == assistantToDrop }
            } else {
                finishStreaming(current.messages)
            }
            current.copy(
                messages = updatedMessages,
                isGenerating = false,
                livePhase = LivePhase.LISTENING,
                liveLevel = 0f
            )
        }
    }

    private fun handleLiveTap() {
        when (_state.value.livePhase) {
            LivePhase.IDLE -> Unit
            LivePhase.LISTENING -> {
                if (!_state.value.micMuted && endpointer.hasSpeech && !transcribing) {
                    triggerEndpoint()
                }
            }
            LivePhase.THINKING, LivePhase.SPEAKING -> {
                interruptLiveTurn(resetEndpointer = true)
            }
        }
    }

    private fun handleExitLiveMode() {
        val current = _state.value
        if (current.livePhase == LivePhase.IDLE && micJob == null) {
            if (current.micMuted) {
                _state.update { it.copy(micMuted = false) }
            }
            return
        }
        micJob?.cancel()
        micJob = null
        generationJob?.cancel()
        generationJob = null
        if (current.isGenerating ||
            current.livePhase == LivePhase.THINKING ||
            current.livePhase == LivePhase.SPEAKING
        ) {
            engine.cancel()
        }
        synthesizer.stop()
        resetLiveTurnTracking()
        resetEndpointerAndVoice()
        framesSinceLevelUpdate = 0
        _state.update { state ->
            state.copy(
                messages = finishStreaming(state.messages),
                isGenerating = false,
                livePhase = LivePhase.IDLE,
                liveLevel = 0f,
                voiceDetected = false,
                micMuted = false
            )
        }
    }

    private fun handleToggleWakeWord() {
        val enabled = !_state.value.wakeWordEnabled
        settings.saveWakeWord(enabled)
        setWakeWordEnabled(enabled)
    }

    private fun handleToggleAutoListen() {
        val enabled = !_state.value.autoListen
        settings.saveAutoListen(enabled)
        _state.update { it.copy(autoListen = enabled) }
    }

    // The other model stays on disk, so switching back needs no download. A different model starts a new chat.
    private fun handleSelectModel(id: String) {
        val next = models.firstOrNull { it.id == id } ?: return
        if (next == spec || !_state.value.modelStatus.allowsModelSwitch()) return
        settings.saveModelId(next.id)
        spec = next
        _state.update { it.copy(modelId = next.id, modelName = next.displayName) }
        reloadModel()
    }

    private fun reloadModel() {
        if (!_state.value.modelStatus.allowsModelSwitch()) return
        handleExitLiveMode()
        handleStop()
        _state.update { it.copy(modelStatus = ModelStatus.Missing, messages = emptyList()) }
        viewModelScope.launch {
            engine.close()
            engine.resetConversation()
            handleStart()
        }
    }

    private fun handleToggleResourceMeter() {
        val show = !_state.value.showResourceMeter
        settings.saveShowResourceMeter(show)
        _state.update { it.copy(showResourceMeter = show) }
    }

    private fun handleToggleThinking() {
        val thinking = !_state.value.thinking
        settings.saveThinking(thinking)
        engine.setThinking(thinking)
        _state.update { it.copy(thinking = thinking) }
    }

    private fun updateResourceSampling(enabled: Boolean, active: Boolean) {
        if (!enabled) {
            stopResourceSampling(logPeaks = resourceTurnActive || resourceGraceJob != null)
            return
        }
        if (active) {
            if (!resourceTurnActive) {
                // Activity inside the tail is the same turn going on (speech starts a moment after
                // generation ends), so the tail is dropped without logging. A new turn is a new
                // question, which runGeneration closes the previous turn for.
                val wasInTail = resourceGraceJob != null
                resourceGraceJob?.cancel()
                resourceGraceJob = null
                resourceTurnActive = true
                if (!wasInTail) {
                    resourceSamplingJob?.cancel()
                    resourceSamplingJob = null
                }
            }
        } else if (resourceTurnActive) {
            resourceTurnActive = false
            resourceTurnEnded = true
            resourceGraceJob?.cancel()
            resourceGraceJob = viewModelScope.launch {
                delay(RESOURCE_TAIL_MS)
                finishResourceTurn()
                resourceGraceJob = null
            }
        }
        startResourceSampling()
    }

    private fun startResourceSampling() {
        if (resourceSamplingJob?.isActive == true) return
        resourceSamplingJob = viewModelScope.launch {
            while (isActive) {
                val sample = withContext(resourceDispatcher) { runCatching(resourceMonitor::sample).getOrNull() }
                if (sample != null && _state.value.showResourceMeter) {
                    if (resourceTurnActive || resourceGraceJob != null) {
                        peakCpuPercent = maxOfNullable(peakCpuPercent, sample.cpuPercent)
                        peakPssMb = maxOfNullable(peakPssMb, sample.pssMb)
                    }
                    _state.update { it.copy(resources = sample) }
                }
                val intervalMs = if (resourceTurnActive || resourceGraceJob != null) {
                    RESOURCE_SAMPLE_INTERVAL_MS
                } else {
                    RESOURCE_IDLE_SAMPLE_INTERVAL_MS
                }
                delay(intervalMs)
            }
        }
    }

    private fun stopResourceSampling(logPeaks: Boolean) {
        resourceGraceJob?.cancel()
        resourceGraceJob = null
        resourceSamplingJob?.cancel()
        resourceSamplingJob = null
        resourceTurnActive = false
        if (logPeaks) finishResourceTurn() else resetResourcePeaks()
        resourceTurnEnded = false
        _state.update { it.copy(resources = null) }
    }

    private fun finishResourceTurn() {
        resourceMonitor.logTurnPeaks(peakCpuPercent, peakPssMb)
        resetResourcePeaks()
        resourceTurnEnded = false
    }

    /** A new question inside the previous turn's tail closes that turn's log first (one line per turn). */
    private fun closeEndedResourceTurn() {
        if (!resourceTurnEnded) return
        resourceGraceJob?.cancel()
        resourceGraceJob = null
        finishResourceTurn()
    }

    private fun resetResourcePeaks() {
        peakCpuPercent = null
        peakPssMb = null
    }

    private fun handleAppForegrounded() {
        val current = _state.value
        if (current.autoListen && current.livePhase == LivePhase.IDLE && current.modelStatus == ModelStatus.Ready) {
            handleEnterLiveMode()
        }
    }

    private fun setWakeWordEnabled(enabled: Boolean) {
        if (_state.value.wakeWordEnabled == enabled) return
        _state.update { it.copy(wakeWordEnabled = enabled) }
        msSinceLastSpokenReply = null
        utteranceStartedInFollowUp = false
        clearWakeState()
    }

    private fun createUserMessage(
        prompt: LlmPrompt,
        userText: String?
    ): ChatMessage = when (prompt) {
        is LlmPrompt.Text -> if (userText != null) {
            ChatMessage(++nextMessageId, Role.USER, userText, isVoice = true)
        } else {
            ChatMessage(++nextMessageId, Role.USER, prompt.text)
        }
        is LlmPrompt.Audio -> ChatMessage(
            id = ++nextMessageId,
            role = Role.USER,
            text = userText ?: formatVoiceDuration(prompt.wavBytes),
            isVoice = true
        )
        is LlmPrompt.Image -> ChatMessage(
            id = ++nextMessageId,
            role = Role.USER,
            text = userText ?: prompt.instruction,
            isVoice = true
        )
    }

    private fun runGeneration(
        prompt: LlmPrompt,
        userText: String? = null,
        clearInput: Boolean = prompt is LlmPrompt.Text
    ) {
        closeEndedResourceTurn()
        val userMessage = createUserMessage(prompt, userText)
        val assistantId = ++nextMessageId
        val assistantMessage = ChatMessage(assistantId, Role.ASSISTANT, "", isStreaming = true)
        val isLive = _state.value.livePhase != LivePhase.IDLE
        currentTurnAssistantId = if (isLive) assistantId else null
        currentTurnUserId = if (isLive) userMessage.id else null
        currentTurnUserText = if (isLive) userText else null
        currentTurnSpoke = false
        _state.update {
            it.copy(
                messages = it.messages + userMessage + assistantMessage,
                input = if (clearInput) "" else it.input,
                isGenerating = true
            )
        }
        if (isLive && !_state.value.offlineVoiceMissing) synthesizer.warmUp()
        generationJob = viewModelScope.launch {
            try {
                awaitModelReady()
                if (isLive) {
                    streamLiveGeneration(prompt, assistantId)
                } else {
                    streamTypedGeneration(prompt, assistantId)
                }
            } catch (e: CancellationException) {
                // Our own Stop or barge-in cancels this job. A cancel the engine reports while the job is still
                // active would otherwise end the turn silently and leave it stuck on Thinking with an empty bubble.
                if (!isActive) throw e
                handleGenerationFailure(assistantId, null)
            } catch (expected: Exception) {
                handleGenerationFailure(assistantId, expected)
            }
        }
    }

    // A null cause means the engine dropped the turn: whatever text arrived stays, an empty bubble goes.
    private fun handleGenerationFailure(assistantId: Long, expected: Exception?) {
        currentTurnAssistantId = null
        currentTurnUserId = null
        currentTurnUserText = null
        currentTurnSpoke = false
        if (expected == null) {
            _state.update { it.copy(messages = finishStreaming(it.messages)) }
        } else {
            updateAssistantMessage(
                assistantId = assistantId,
                text = "Error: ${expected.message ?: "Generation failed"}",
                isStreaming = false
            )
        }
        if (_state.value.livePhase != LivePhase.IDLE) {
            resetEndpointerAndVoice()
            framesSinceLevelUpdate = 0
            _state.update {
                it.copy(isGenerating = false, livePhase = LivePhase.LISTENING, liveLevel = 0f)
            }
        } else {
            _state.update { it.copy(isGenerating = false) }
        }
    }

    // Listening may start while the model still loads; a question asked then waits here for it.
    private suspend fun awaitModelReady() {
        val status = state.map { it.modelStatus }.first { it is ModelStatus.Ready || it is ModelStatus.Error }
        if (status is ModelStatus.Error) throw IllegalStateException(status.message)
    }

    private suspend fun streamTypedGeneration(
        prompt: LlmPrompt,
        assistantId: Long
    ) {
        var finalText = ""
        replySpeedMeter.start()
        engine.generate(prompt).collect { fullText ->
            finalText = fullText
            recordReplySpeed(fullText)
            updateAssistantMessage(assistantId, text = fullText, isStreaming = true)
        }
        finishReplySpeed()
        finalizeAssistantMessage(assistantId)
    }

    private suspend fun streamLiveGeneration(
        prompt: LlmPrompt,
        assistantId: Long
    ) {
        val chunker = ClauseChunker(config)
        var latestText = ""
        replySpeedMeter.start()
        engine.generate(prompt).collect { fullText ->
            latestText = fullText
            recordReplySpeed(fullText)
            updateAssistantMessage(assistantId, text = fullText, isStreaming = true)
            enqueueClauses(chunker.feed(fullText))
        }
        finishReplySpeed()
        updateAssistantMessage(assistantId, text = latestText, isStreaming = true)
        enqueueClauses(chunker.feed(latestText))
        chunker.flush()?.let { remainder ->
            enqueueClauses(listOf(remainder))
        }
        finalizeAssistantMessage(assistantId)
        synthesizer.isSpeaking.first { !it }
        msSinceLastSpokenReply = 0L
        msSinceInterruptOrReply = 0L
        expectingReplyFromLastReply = expectingReply(latestText)
        currentTurnAssistantId = null
        currentTurnUserId = null
        currentTurnUserText = null
        currentTurnSpoke = false
        resetEndpointerAndVoice()
        framesSinceLevelUpdate = 0
        _state.update { it.copy(livePhase = LivePhase.LISTENING, liveLevel = 0f) }
    }

    private fun recordReplySpeed(fullText: String) {
        replySpeedMeter.onText(fullText)?.let { speed ->
            _state.update { it.copy(replySpeed = speed.copy(route = engine.lastRoute)) }
        }
    }

    private fun finishReplySpeed() {
        replySpeedMeter.finish()?.let { speed ->
            _state.update { it.copy(replySpeed = speed.copy(route = engine.lastRoute)) }
        }
    }

    private fun enqueueClauses(clauses: List<String>) {
        if (_state.value.offlineVoiceMissing) return
        for (clause in clauses) {
            currentTurnSpoke = true
            synthesizer.enqueue(clause)
            if (_state.value.livePhase == LivePhase.THINKING) {
                _state.update { it.copy(livePhase = LivePhase.SPEAKING, liveLevel = synthesizer.outputLevel.value) }
            }
        }
    }

    private fun handleStop() {
        synthesizer.stop()
        if (!_state.value.isGenerating) return
        engine.cancel()
        generationJob?.cancel()
        generationJob = null
        _state.update { current ->
            current.copy(
                messages = finishStreaming(current.messages),
                isGenerating = false
            )
        }
    }

    private fun handleNewChat() {
        if (_state.value.autoListen && _state.value.livePhase != LivePhase.IDLE) {
            resetChat(keepLive = true)
        } else {
            micJob?.cancel()
            micJob = null
            resetChat(keepLive = false)
        }
    }

    private fun updateRenameDraft(text: String) {
        val error = if (validateAssistantName(text) == null) RENAME_ERROR_TEXT else null
        _state.update { it.copy(renameDraft = text, renameError = error) }
    }

    // The system instruction only applies at conversation start, so a new name starts a new chat.
    private fun handleSaveRename() {
        val name = _state.value.renameDraft?.let(::validateAssistantName) ?: return
        settings.saveAssistantName(name)
        wakeWords = listOf(name.lowercase())
        engine.setAssistantName(name)
        _state.update { it.copy(assistantName = name, renameDraft = null, renameError = null) }
        resetChat(keepLive = true)
    }

    private fun resetChat(keepLive: Boolean) {
        synthesizer.stop()
        resetLiveTurnTracking()
        resetEndpointerAndVoice()
        framesSinceLevelUpdate = 0
        if (_state.value.isGenerating ||
            _state.value.livePhase == LivePhase.THINKING ||
            _state.value.livePhase == LivePhase.SPEAKING
        ) {
            engine.cancel()
        }
        generationJob?.cancel()
        generationJob = null
        _state.update {
            val live = keepLive && it.livePhase != LivePhase.IDLE
            it.copy(
                messages = emptyList(),
                isGenerating = false,
                livePhase = if (live) LivePhase.LISTENING else LivePhase.IDLE,
                liveLevel = 0f,
                micMuted = live && it.micMuted
            )
        }
        viewModelScope.launch {
            engine.resetConversation()
        }
    }

    private fun resetLiveTurnTracking() {
        lastSentPcm = null
        pendingMergePcm = null
        pendingMergeTranscript = null
        mergeableAssistantId = null
        currentTurnSpoke = false
        currentTurnAssistantId = null
        msSinceLastSpokenReply = null
        utteranceStartedInFollowUp = false
        msSinceInterruptOrReply = 0L
        mergeableUserId = null
        mergeableCanBargeIn = false
        currentTurnUserId = null
        currentTurnUserText = null
        currentTurnCanBargeIn = false
        liveSession++
        clearWakeState()
    }

    private fun clearWakeState() {
        overheard.clear()
        woken = false
        expectingReplyFromLastReply = false
        transcribing = false
        deferredEndpoint = false
    }

    private fun updateAssistantMessage(assistantId: Long, text: String, isStreaming: Boolean) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { msg ->
                    if (msg.id == assistantId) {
                        msg.copy(text = text, isStreaming = isStreaming)
                    } else {
                        msg
                    }
                }
            )
        }
    }

    private fun finalizeAssistantMessage(assistantId: Long) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { msg ->
                    if (msg.id == assistantId) msg.copy(isStreaming = false) else msg
                },
                isGenerating = false
            )
        }
    }

    public override fun onCleared() {
        super.onCleared()
        micJob?.cancel()
        micJob = null
        generationJob?.cancel()
        generationJob = null
        stopResourceSampling(logPeaks = resourceTurnActive || resourceGraceJob != null)
        synthesizer.stop()
        engine.close()
        _state.update { it.copy(micMuted = false) }
    }
}

private fun maxOfNullable(first: Double?, second: Double?): Double? = when {
    first == null -> second
    second == null -> first
    else -> maxOf(first, second)
}

private class Utterance(
    val pcm: ByteArray,
    val newPcm: ByteArray,
    val previousTranscript: String?,
    val droppedIds: Set<Long>,
    val bypassesWakeWord: Boolean
)

private fun formatVoiceDuration(wavBytes: ByteArray): String {
    val pcmBytes = (wavBytes.size - WavEncoder.HEADER_SIZE).coerceAtLeast(0)
    val durationSeconds = pcmBytes / WAV_BYTES_PER_SECOND
    val roundedTenths = (durationSeconds * TENTHS_MULTIPLIER).roundToInt()
    val whole = roundedTenths / TENTHS_DIVISOR
    val tenths = roundedTenths % TENTHS_DIVISOR
    return "Voice message ($whole.${tenths}s)"
}

private fun ModelStatus.allowsListening(): Boolean = this is ModelStatus.Ready || this is ModelStatus.Loading
