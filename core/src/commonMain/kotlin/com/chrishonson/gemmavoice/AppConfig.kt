package com.chrishonson.gemmavoice

/** Behaviour switches an app module chooses at build time. The look belongs to the app module, not here. */
data class AppConfig(
    /** Speak replies in the baked Pocket TTS voice; off, the system voice speaks. */
    val clonedVoice: Boolean,
    /** Lets the user rename the assistant, which also changes the wake word; off, it stays "Computer". */
    val renameAssistant: Boolean,
    /** Keep the screen awake while the activity is in the foreground. */
    val keepScreenOnInForeground: Boolean = true,
    /**
     * Sort each typed or transcribed turn with the embedding intent router (router/IntentRouter.kt) before Gemma
     * replies: chat turns get no tool schemas, save requests get save_note alone. Off, every turn offers save_note,
     * as before.
     */
    val intentRouter: Boolean = true
)
