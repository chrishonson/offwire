package com.chrishonson.gemmavoice.llm

import com.chrishonson.gemmavoice.router.RouteReadout
import com.chrishonson.gemmavoice.router.formatRoute
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private const val MS_PER_SECOND = 1000.0
private const val ONE_DECIMAL = 10.0
private const val TWO_DECIMALS = 100.0

/**
 * How fast one reply came back. [ttftMs] runs from the send to the first streamed message with text.
 * [tokensPerSecond] counts streamed messages, one per token, between the first and the last, as the
 * benchmark does (docs/spikes/x0-gpu-image.md). It stays null until the reply ends or when fewer than two
 * messages arrived. [route] is how the intent router sorted the turn, when it ran; [ttftMs] includes its time.
 */
data class ReplySpeed(
    val ttftMs: Long,
    val tokensPerSecond: Double? = null,
    val route: RouteReadout? = null
)

fun monotonicClockMs(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}

/** Times one reply from the stream of accumulated text the engine emits. Not thread-safe; one per turn. */
class ReplySpeedMeter(private val clockMs: () -> Long) {
    private var sendMs = 0L
    private var firstMs: Long? = null
    private var lastMs = 0L
    private var messages = 0
    private var lastLength = 0

    fun start() {
        sendMs = clockMs()
        firstMs = null
        lastMs = 0L
        messages = 0
        lastLength = 0
    }

    /** Returns the speed so far when this text is the reply's first, or null when nothing new is known yet. */
    fun onText(fullText: String): ReplySpeed? {
        if (fullText.isBlank() || fullText.length <= lastLength) return null
        lastLength = fullText.length
        val nowMs = clockMs()
        lastMs = nowMs
        messages++
        if (firstMs != null) return null
        firstMs = nowMs
        return ReplySpeed(ttftMs = nowMs - sendMs)
    }

    /** The reply's final speed, or null when no text ever arrived. */
    fun finish(): ReplySpeed? {
        val first = firstMs ?: return null
        val durationMs = lastMs - first
        val rate = if (messages >= 2 && durationMs > 0L) {
            (messages - 1) * MS_PER_SECOND / durationMs
        } else {
            null
        }
        return ReplySpeed(ttftMs = first - sendMs, tokensPerSecond = rate)
    }
}

/** For example "0.26 s to first token · 16.4 tok/s", then "· route NONE 76 ms" when the intent router ran. */
fun formatReplySpeed(speed: ReplySpeed): String {
    val seconds = (speed.ttftMs / MS_PER_SECOND * TWO_DECIMALS).roundToInt() / TWO_DECIMALS
    val ttft = "$seconds s to first token"
    val rate = speed.tokensPerSecond?.let { "${(it * ONE_DECIMAL).roundToInt() / ONE_DECIMAL} tok/s" }
    return listOfNotNull(ttft, rate, speed.route?.let(::formatRoute)).joinToString(" · ")
}
