package com.chrishonson.gemmavoice

import com.chrishonson.gemmavoice.voice.SettingsStore
import platform.Foundation.NSUserDefaults

private const val KEY_ASSISTANT_NAME = "assistant_name"
private const val KEY_ALLOW_CELLULAR_DOWNLOAD = "allow_cellular_download"
private const val KEY_WAKE_WORD = "wake_word"
private const val KEY_AUTO_LISTEN = "auto_listen"
private const val KEY_SHOW_RESOURCE_METER = "show_resource_meter"
private const val KEY_THINKING = "thinking"
private const val KEY_MODEL_ID = "model_id"

class IosSettingsStore : SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun loadAssistantName(): String? = defaults.stringForKey(KEY_ASSISTANT_NAME)

    override fun saveAssistantName(name: String) {
        defaults.setObject(name, forKey = KEY_ASSISTANT_NAME)
    }

    override fun loadAllowCellularDownload(): Boolean = defaults.boolForKey(KEY_ALLOW_CELLULAR_DOWNLOAD)

    override fun saveAllowCellularDownload(allow: Boolean) {
        defaults.setBool(allow, forKey = KEY_ALLOW_CELLULAR_DOWNLOAD)
    }

    override fun loadWakeWord(): Boolean? =
        if (defaults.objectForKey(KEY_WAKE_WORD) != null) defaults.boolForKey(KEY_WAKE_WORD) else null

    override fun saveWakeWord(enabled: Boolean) {
        defaults.setBool(enabled, forKey = KEY_WAKE_WORD)
    }

    override fun loadAutoListen(): Boolean? =
        if (defaults.objectForKey(KEY_AUTO_LISTEN) != null) defaults.boolForKey(KEY_AUTO_LISTEN) else null

    override fun saveAutoListen(enabled: Boolean) {
        defaults.setBool(enabled, forKey = KEY_AUTO_LISTEN)
    }

    override fun loadShowResourceMeter(): Boolean = defaults.boolForKey(KEY_SHOW_RESOURCE_METER)

    override fun saveShowResourceMeter(show: Boolean) {
        defaults.setBool(show, forKey = KEY_SHOW_RESOURCE_METER)
    }

    override fun loadThinking(): Boolean = defaults.boolForKey(KEY_THINKING)

    override fun saveThinking(enabled: Boolean) {
        defaults.setBool(enabled, forKey = KEY_THINKING)
    }

    override fun loadModelId(): String? = defaults.stringForKey(KEY_MODEL_ID)

    override fun saveModelId(id: String) {
        defaults.setObject(id, forKey = KEY_MODEL_ID)
    }
}
