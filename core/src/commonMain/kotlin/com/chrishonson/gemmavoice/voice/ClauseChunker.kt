package com.chrishonson.gemmavoice.voice

private const val MIN_WORDS = 2
private const val MISC_TECH_START = '\u2300'
private const val MISC_TECH_END = '\u23FF'
private const val MISC_SYMBOLS_START = '\u2600'
private const val DINGBATS_END = '\u27BF'
private const val STARS_START = '\u2B50'
private const val STARS_END = '\u2B55'
private const val VARIATION_SELECTOR_START = '\uFE00'
private const val VARIATION_SELECTOR_END = '\uFE0F'
private const val ZERO_WIDTH_JOINER = '\u200D'
private const val COMBINING_KEYCAP = '\u20E3'

private val ABBREVIATIONS = setOf(
    "mr",
    "mrs",
    "ms",
    "dr",
    "prof",
    "sr",
    "jr",
    "st",
    "vs",
    "etc",
    "e.g",
    "i.e"
)

private val WHITESPACE_REGEX = Regex("\\s+")

private enum class BoundaryKind {
    SENTENCE,
    CLAUSE
}

private data class Boundary(
    val endIndex: Int,
    val kind: BoundaryKind
)

typealias SentenceSplitter = ClauseChunker

class ClauseChunker(
    private val config: VoiceConfig = VoiceConfig()
) {
    private var currentText: String = ""
    private var consumedIndex: Int = 0
    private var emittedFirstChunk: Boolean = false

    fun feed(fullTextSoFar: String): List<String> {
        currentText = if (fullTextSoFar.startsWith(currentText)) {
            fullTextSoFar
        } else {
            currentText + fullTextSoFar
        }

        val emitted = mutableListOf<String>()
        while (consumedIndex < currentText.length) {
            val next = if (!emittedFirstChunk) {
                takeFirstChunk()
            } else {
                takeLaterChunk()
            } ?: break
            emitted.add(next)
            emittedFirstChunk = true
        }
        return emitted
    }

    fun flush(): String? {
        if (consumedIndex >= currentText.length) return null
        val remainder = currentText.substring(consumedIndex)
        consumedIndex = currentText.length
        val cleaned = cleanForSpeech(remainder)
        return cleaned.ifEmpty { null }
    }

    private fun takeFirstChunk(): String? {
        var searchIndex = consumedIndex
        var result: String? = null
        var searching = true
        while (searchIndex < currentText.length && searching) {
            val boundary = findNextBoundary(currentText, searchIndex)
            if (boundary == null) {
                searching = false
            } else {
                val candidate = currentText.substring(consumedIndex, boundary.endIndex)
                val isAbbrev = candidate.endsWith(".") && endsWithAbbreviation(candidate)
                val cleaned = cleanForSpeech(candidate)
                when {
                    cleaned.isEmpty() -> {
                        consumedIndex = skipHorizontalWhitespace(currentText, boundary.endIndex)
                        searchIndex = consumedIndex
                    }
                    cleaned.length > config.firstMaxChars -> {
                        searching = false
                    }
                    !isAbbrev && cleaned.length >= config.firstMinChars -> {
                        consumedIndex = skipHorizontalWhitespace(currentText, boundary.endIndex)
                        result = cleaned
                        searching = false
                    }
                    else -> {
                        searchIndex = boundary.endIndex
                    }
                }
            }
        }
        return result ?: takeHardCutFirstChunk()
    }

    private fun takeHardCutFirstChunk(): String? {
        val unconsumed = currentText.substring(consumedIndex)
        if (cleanForSpeech(unconsumed).length < config.firstMaxChars) return null
        var cutIndex = -1
        for (i in 1 until unconsumed.length) {
            if (unconsumed[i].isWhitespace()) {
                val cleaned = cleanForSpeech(unconsumed.substring(0, i))
                if (cleaned.length <= config.firstMaxChars) {
                    cutIndex = i
                } else {
                    break
                }
            }
        }
        val cleaned = if (cutIndex >= 0) {
            cleanForSpeech(unconsumed.substring(0, cutIndex))
        } else {
            ""
        }
        return if (cleaned.length >= config.firstMinChars) {
            consumedIndex = skipHorizontalWhitespace(currentText, consumedIndex + cutIndex)
            cleaned
        } else {
            null
        }
    }

    private fun takeLaterChunk(): String? {
        var searchIndex = consumedIndex
        var result: String? = null
        while (searchIndex < currentText.length && result == null) {
            val boundary = findNextBoundary(currentText, searchIndex) ?: break
            val candidate = currentText.substring(consumedIndex, boundary.endIndex)
            val isAbbrev = candidate.endsWith(".") && endsWithAbbreviation(candidate)
            val cleaned = cleanForSpeech(candidate)
            val canEmitSentence = boundary.kind == BoundaryKind.SENTENCE &&
                !isAbbrev &&
                countWords(cleaned) >= MIN_WORDS
            val canEmitClause = boundary.kind == BoundaryKind.CLAUSE &&
                cleaned.length >= config.laterMinChars

            if (cleaned.isEmpty()) {
                consumedIndex = skipHorizontalWhitespace(currentText, boundary.endIndex)
                searchIndex = consumedIndex
            } else if (canEmitSentence || canEmitClause) {
                consumedIndex = skipHorizontalWhitespace(currentText, boundary.endIndex)
                result = cleaned
            } else {
                searchIndex = boundary.endIndex
            }
        }
        return result
    }
}

private fun findNextBoundary(text: String, startIndex: Int): Boundary? {
    var i = startIndex
    var found: Boundary? = null
    while (i < text.length && found == null) {
        val ch = text[i]
        val kind = boundaryKindOf(ch)
        if (ch == '\n') {
            val end = skipMatching(text, i + 1) { it == '\n' }
            found = Boundary(end, BoundaryKind.SENTENCE)
        } else if (kind != null) {
            val end = skipMatching(text, i + 1) { boundaryKindOf(it) != null }
            if (end == text.length || text[end].isWhitespace()) {
                found = Boundary(end, kind)
            } else {
                i = end
                continue
            }
        }
        i++
    }
    return found
}

private inline fun skipMatching(text: String, startIndex: Int, predicate: (Char) -> Boolean): Int {
    var i = startIndex
    while (i < text.length && predicate(text[i])) {
        i++
    }
    return i
}

private fun boundaryKindOf(ch: Char): BoundaryKind? = when (ch) {
    '.', '!', '?' -> BoundaryKind.SENTENCE
    ',', ';', ':' -> BoundaryKind.CLAUSE
    else -> null
}

private fun skipHorizontalWhitespace(text: String, index: Int): Int =
    skipMatching(text, index) { it == ' ' || it == '\t' }

private fun endsWithAbbreviation(segment: String): Boolean {
    val beforeDot = segment.dropLast(1).trimEnd()
    val lastWord = beforeDot.takeLastWhile { it.isLetter() || it == '.' }.lowercase()
    return lastWord in ABBREVIATIONS
}

private fun countWords(cleaned: String): Int =
    cleaned.split(' ').count { token -> token.any { it.isLetterOrDigit() } }

private fun cleanForSpeech(text: String): String {
    val filtered = buildString(text.length) {
        for (ch in text) {
            if (!isMarkdownOrEmoji(ch)) {
                append(ch)
            }
        }
    }
    return WHITESPACE_REGEX.replace(filtered, " ").trim()
}

private fun isMarkdownOrEmoji(ch: Char): Boolean =
    ch == '*' ||
        ch == '#' ||
        ch == '`' ||
        ch == '_' ||
        ch == '~' ||
        ch == '>' ||
        ch.isSurrogate() ||
        ch in MISC_TECH_START..MISC_TECH_END ||
        ch in MISC_SYMBOLS_START..DINGBATS_END ||
        ch in STARS_START..STARS_END ||
        ch in VARIATION_SELECTOR_START..VARIATION_SELECTOR_END ||
        ch == ZERO_WIDTH_JOINER ||
        ch == COMBINING_KEYCAP
