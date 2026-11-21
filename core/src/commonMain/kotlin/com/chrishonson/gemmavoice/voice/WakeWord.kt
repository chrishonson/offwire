package com.chrishonson.gemmavoice.voice

private val GREETINGS = setOf("hey", "hi", "hello", "ok", "okay", "yo", "uh", "um")

private val PROMPTING = Regex(
    "\\?|\\b(" +
        "please\\s+(specify|clarify|provide|state|confirm)" +
        "|specify\\s+(the|which|what|a|your)" +
        "|state\\s+your" +
        "|awaiting\\s+instruction" +
        "|standing\\s+by" +
        "|go\\s+ahead" +
        "|proceed" +
        ")\\b",
    RegexOption.IGNORE_CASE
)

private val WORD = Regex("\\w+")

/** True when any wake word appears as a whole word in the transcript, or when there are no wake words. */
fun addressed(transcript: String, wakeWords: List<String>): Boolean =
    wakeWords.isEmpty() || wakeWords.any { word ->
        Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(transcript)
    }

private const val MIN_CONSECUTIVE_SPEECH_CHARS = 2

/** True when the transcript has a wake word and nothing else except optional greeting/filler words. */
fun onlyWakeWord(transcript: String, wakeWords: List<String>): Boolean {
    if (wakeWords.isEmpty() || !addressed(transcript, wakeWords)) return false
    val ignore = wakeWords.map { it.lowercase() }.toSet() + GREETINGS
    return WORD.findAll(transcript).none { it.value.lowercase() !in ignore }
}

/** True when trimmed text contains at least one run of 2 or more consecutive letters or digits. */
fun isSpeechTranscript(text: String): Boolean {
    var run = 0
    for (ch in text.trim()) {
        if (ch.isLetterOrDigit()) {
            run++
            if (run >= MIN_CONSECUTIVE_SPEECH_CHARS) return true
        } else {
            run = 0
        }
    }
    return false
}

/** True when the assistant's reply asks a question or asks the user to specify parameters. */
fun expectingReply(text: String): Boolean = PROMPTING.containsMatchIn(text)

/**
 * Opens the speech heard since the last reply. Calling it background, not a request, stopped Gemma 4 E2B saving
 * it as a note when only the wake word followed (0 of 6 runs to 6 of 6, overheard cases in scripts/eval/notes.py).
 */
const val OVERHEARD_HEADER = "Said near you since your last reply, not to you. " +
    "It is background you can use to answer, not a request:"

/** Comes before the addressed transcript when overheard lines lead the message; the intent router reads after it. */
const val ADDRESSED_TO_YOU = "Now said to you: "

/** Prefixes the speech heard since the last reply so the reply can use the conversation it was not part of. */
fun withOverheard(overheard: List<String>, transcript: String): String {
    if (overheard.isEmpty()) return transcript
    val lines = overheard.joinToString("\n")
    return "$OVERHEARD_HEADER\n$lines\n\n$ADDRESSED_TO_YOU$transcript"
}
