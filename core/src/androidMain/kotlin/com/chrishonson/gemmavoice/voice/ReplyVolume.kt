package com.chrishonson.gemmavoice.voice

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

// Live replies play as call audio and typed ones as media, and Android gives each its own volume. Media is the
// one the user sets, in any mode; call volume follows it. The call volume only reaches the loudspeaker while live
// voice has it routed there (otherwise Android applies it to the earpiece), so it is matched when live voice starts
// and after each button press while live.
class ReplyVolume(context: Context, private val callAudio: CommunicationAudio) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun adjust(raise: Boolean) {
        val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        if (callAudio.isActive) matchCallToMedia()
    }

    fun matchCallToMedia() = setMatching(from = AudioManager.STREAM_MUSIC, to = AudioManager.STREAM_VOICE_CALL)

    private fun setMatching(from: Int, to: Int) {
        val fromMin = audioManager.getStreamMinVolume(from)
        val fraction = (audioManager.getStreamVolume(from) - fromMin).toFloat() /
            (audioManager.getStreamMaxVolume(from) - fromMin)
        val toMin = audioManager.getStreamMinVolume(to)
        val index = toMin + (fraction * (audioManager.getStreamMaxVolume(to) - toMin)).roundToInt()
        audioManager.setStreamVolume(to, index, 0)
    }
}
