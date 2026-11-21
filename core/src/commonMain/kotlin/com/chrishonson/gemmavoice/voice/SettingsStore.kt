package com.chrishonson.gemmavoice.voice

interface SettingsStore {
    fun loadAssistantName(): String?
    fun saveAssistantName(name: String)
    fun loadAllowCellularDownload(): Boolean = false
    fun saveAllowCellularDownload(allow: Boolean)
    fun loadWakeWord(): Boolean? = null
    fun saveWakeWord(enabled: Boolean)
    fun loadAutoListen(): Boolean? = null
    fun saveAutoListen(enabled: Boolean)
    fun loadShowResourceMeter(): Boolean = false
    fun saveShowResourceMeter(show: Boolean)
    fun loadThinking(): Boolean = false
    fun saveThinking(enabled: Boolean)
    fun loadModelId(): String? = null
    fun saveModelId(id: String)
}

class InMemorySettingsStore(
    private var assistantName: String? = null,
    private var allowCellularDownload: Boolean = false,
    private var wakeWord: Boolean? = null,
    private var autoListen: Boolean? = null,
    private var showResourceMeter: Boolean = false,
    private var thinking: Boolean = false,
    private var modelId: String? = null
) : SettingsStore {
    override fun loadAssistantName(): String? = assistantName

    override fun saveAssistantName(name: String) {
        assistantName = name
    }

    override fun loadAllowCellularDownload(): Boolean = allowCellularDownload

    override fun saveAllowCellularDownload(allow: Boolean) {
        allowCellularDownload = allow
    }

    override fun loadWakeWord(): Boolean? = wakeWord

    override fun saveWakeWord(enabled: Boolean) {
        wakeWord = enabled
    }

    override fun loadAutoListen(): Boolean? = autoListen

    override fun saveAutoListen(enabled: Boolean) {
        autoListen = enabled
    }

    override fun loadShowResourceMeter(): Boolean = showResourceMeter

    override fun saveShowResourceMeter(show: Boolean) {
        showResourceMeter = show
    }

    override fun loadThinking(): Boolean = thinking

    override fun saveThinking(enabled: Boolean) {
        thinking = enabled
    }

    override fun loadModelId(): String? = modelId

    override fun saveModelId(id: String) {
        modelId = id
    }
}

/** The saved name when it is still valid, otherwise [fallback]. */
fun startingAssistantName(settings: SettingsStore, fallback: String): String =
    settings.loadAssistantName()?.let(::validateAssistantName) ?: fallback
