package com.chrishonson.gemmavoice.voice

import android.content.Context

private const val PREFS_NAME = "settings"
private const val KEY_ASSISTANT_NAME = "assistant_name"
private const val KEY_ALLOW_CELLULAR_DOWNLOAD = "allow_cellular_download"
private const val KEY_WAKE_WORD = "wake_word"
private const val KEY_AUTO_LISTEN = "auto_listen"
private const val KEY_SHOW_RESOURCE_METER = "show_resource_meter"
private const val KEY_THINKING = "thinking"
private const val KEY_MODEL_ID = "model_id"

class AndroidSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadAssistantName(): String? = prefs.getString(KEY_ASSISTANT_NAME, null)

    override fun saveAssistantName(name: String) {
        prefs.edit().putString(KEY_ASSISTANT_NAME, name).apply()
    }

    override fun loadAllowCellularDownload(): Boolean = prefs.getBoolean(KEY_ALLOW_CELLULAR_DOWNLOAD, false)

    override fun saveAllowCellularDownload(allow: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_CELLULAR_DOWNLOAD, allow).apply()
    }

    override fun loadWakeWord(): Boolean? =
        if (prefs.contains(KEY_WAKE_WORD)) prefs.getBoolean(KEY_WAKE_WORD, false) else null

    override fun saveWakeWord(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD, enabled).apply()
    }

    override fun loadAutoListen(): Boolean? =
        if (prefs.contains(KEY_AUTO_LISTEN)) prefs.getBoolean(KEY_AUTO_LISTEN, false) else null

    override fun saveAutoListen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LISTEN, enabled).apply()
    }

    override fun loadShowResourceMeter(): Boolean = prefs.getBoolean(KEY_SHOW_RESOURCE_METER, false)

    override fun saveShowResourceMeter(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RESOURCE_METER, show).apply()
    }

    override fun loadThinking(): Boolean = prefs.getBoolean(KEY_THINKING, false)

    override fun saveThinking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THINKING, enabled).apply()
    }

    override fun loadModelId(): String? = prefs.getString(KEY_MODEL_ID, null)

    override fun saveModelId(id: String) {
        prefs.edit().putString(KEY_MODEL_ID, id).apply()
    }
}
