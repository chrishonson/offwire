package com.chrishonson.gemmavoice.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

class IosMicPermission : MicPermission {
    override suspend fun ensureGranted(): Boolean {
        val session = AVAudioSession.sharedInstance()
        return when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> true
            AVAudioSessionRecordPermissionDenied -> false
            else -> suspendCancellableCoroutine { continuation ->
                session.requestRecordPermission { granted ->
                    dispatch_async(dispatch_get_main_queue()) { continuation.resume(granted) }
                }
            }
        }
    }
}
