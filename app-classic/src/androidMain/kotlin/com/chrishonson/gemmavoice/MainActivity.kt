package com.chrishonson.gemmavoice

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.chrishonson.gemmavoice.bench.AndroidBenchmarkHost
import com.chrishonson.gemmavoice.chat.ChatIntent
import com.chrishonson.gemmavoice.core.BuildConfig
import com.chrishonson.gemmavoice.chat.ChatStore
import com.chrishonson.gemmavoice.voice.ActivityMicPermission

private val APP_CONFIG = AppConfig(clonedVoice = false, renameAssistant = true)

class MainActivity : ComponentActivity() {
    private val voice: VoiceResources by viewModels { VoiceResources.factory(application, APP_CONFIG) }
    private var chatStore: ChatStore? = null
    private var foreground = false
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        voice.activityPermission = ActivityMicPermission(this)

        setContent {
            App(
                engine = voice.engine,
                repo = voice.repo,
                audioStream = voice.audioStream,
                synthesizer = voice.synthesizer,
                micPermission = voice.micPermission,
                vad = voice.vad,
                stt = voice.stt,
                config = voice.config,
                settings = voice.settings,
                network = voice.network,
                notes = voice.notes,
                acknowledgementStore = voice.acknowledgementStore,
                deviceInfo = voice.deviceInfo,
                deviceCheckCompletionStore = voice.deviceCheckCompletionStore,
                appConfig = APP_CONFIG,
                networkActivity = voice.networkActivity,
                resourceMonitor = voice.resourceMonitor,
                appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(),
                benchmarkHost = if (BuildConfig.DEBUG) AndroidBenchmarkHost(this, voice.config.llmBackend) else null,
                onListeningChanged = { active ->
                    listening = active
                    applyKeepScreenOn()
                },
                onStoreReady = { chatStore = it }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        applyKeepScreenOn()
    }

    override fun onPause() {
        super.onPause()
        foreground = false
        applyKeepScreenOn()
    }

    private fun applyKeepScreenOn() {
        if (KeepAwakePolicy.keepScreenOn(APP_CONFIG, foreground, listening)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Intercepted before the UI so a focused control cannot take the volume buttons; both up and down events are
    // consumed so the system never adjusts a stream on its own.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val raise = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) voice.replyVolume.adjust(raise)
        return true
    }

    // Leaving the app ends live voice; rotating does not.
    override fun onStop() {
        if (!isChangingConfigurations) chatStore?.dispatch(ChatIntent.ExitLiveMode)
        super.onStop()
    }

    override fun onDestroy() {
        voice.activityPermission = null
        chatStore = null
        super.onDestroy()
    }
}
