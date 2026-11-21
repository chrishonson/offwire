@file:Suppress("MagicNumber")

package com.chrishonson.gemmavoice.router

private const val CLS = "[CLS]"
private const val SEP = "[SEP]"
private const val UNK = "[UNK]"
private const val CONTINUATION = "##"
private const val MAX_CHARS_PER_WORD = 100
private const val NUL = 0
private const val REPLACEMENT = 0xFFFD

/**
 * The uncased BERT tokenizer that all-MiniLM-L6-v2 was trained with, matching its tokenizer.json in the Python
 * tokenizers library (BertNormalizer, BertPreTokenizer, WordPiece, [CLS] ... [SEP], truncation to [maxTokens]).
 * scripts/eval/export_router_parity.py writes the Python ids that the tests hold this to.
 *
 * [decompose] is Unicode NFD. Accents are stripped by decomposing, then dropping the combining marks; common Kotlin
 * has no normalizer, so the platform passes one in (java.text.Normalizer on Android).
 */
class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val decompose: (String) -> String,
    private val maxTokens: Int = 128
) {
    private val clsId = checkNotNull(vocab[CLS]) { "vocab has no $CLS" }
    private val sepId = checkNotNull(vocab[SEP]) { "vocab has no $SEP" }
    private val unkId = checkNotNull(vocab[UNK]) { "vocab has no $UNK" }

    /** Token ids with [CLS] first and [SEP] last, cut to [maxTokens] in all. */
    fun encode(text: String): IntArray {
        val ids = ArrayList<Int>()
        ids += clsId
        for (word in preTokenize(normalize(text))) {
            ids += wordPiece(word)
            if (ids.size >= maxTokens - 1) break
        }
        val body = ids.take(maxTokens - 1)
        return (body + sepId).toIntArray()
    }

    /** BertNormalizer: clean text, space out CJK characters, strip accents, lowercase. */
    internal fun normalize(text: String): String {
        val cleaned = StringBuilder(text.length)
        forEachCodePoint(text) { cp, chars ->
            when {
                cp == NUL || cp == REPLACEMENT || isControl(cp, chars) -> Unit
                isWhitespace(cp, chars) -> cleaned.append(' ')
                isChinese(cp) -> cleaned.append(' ').append(chars).append(' ')
                else -> cleaned.append(chars)
            }
        }
        val stripped = decompose(cleaned.toString()).filterNot { it.category == CharCategory.NON_SPACING_MARK }
        return stripped.lowercase()
    }

    /** BertPreTokenizer: split on whitespace, and every punctuation character is a word of its own. */
    internal fun preTokenize(text: String): List<String> {
        val words = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) words += current.toString()
            current.clear()
        }
        forEachCodePoint(text) { cp, chars ->
            when {
                isWhitespace(cp, chars) -> flush()
                isPunctuation(cp, chars) -> {
                    flush()
                    words += chars
                }
                else -> current.append(chars)
            }
        }
        flush()
        return words
    }

    /** Greedy longest-match-first pieces, "##" on all but the first; one [UNK] when any part has no piece. */
    internal fun wordPiece(word: String): List<Int> {
        val pieces = ArrayList<Int>()
        var start = 0
        var unknown = codePointCount(word) > MAX_CHARS_PER_WORD
        while (!unknown && start < word.length) {
            var end = word.length
            var found: Int? = null
            while (found == null && start < end) {
                found = vocab[(if (start > 0) CONTINUATION else "") + word.substring(start, end)]
                if (found == null) end = previousBoundary(word, end)
            }
            if (found == null) unknown = true else pieces += found
            start = end
        }
        return if (unknown) listOf(unkId) else pieces
    }
}

/** Reads a WordPiece vocab.txt: one token per line, the line number is the id. */
fun parseVocab(text: String): Map<String, Int> =
    text.lineSequence().withIndex().filter { it.value.isNotEmpty() }.associate { it.value to it.index }

// ---- Characters, by code point, as the Rust tokenizers library sees them ----------------------------------------

/** Calls [action] with each code point and its UTF-16 chars. A lone surrogate is passed through as its own char. */
private inline fun forEachCodePoint(text: String, action: (Int, String) -> Unit) {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
            val cp = ((c.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) + 0x10000
            action(cp, text.substring(i, i + 2))
            i += 2
        } else {
            action(c.code, c.toString())
            i++
        }
    }
}

private fun codePointCount(word: String): Int = word.count { !it.isLowSurrogate() }

/** One code point back from [end], so a piece never splits a surrogate pair. */
private fun previousBoundary(word: String, end: Int): Int =
    if (end >= 2 && word[end - 1].isLowSurrogate() && word[end - 2].isHighSurrogate()) end - 2 else end - 1

private fun isSupplementary(chars: String) = chars.length == 2

// Supplementary code points (emoji and the like) are symbols, never whitespace, control or punctuation, here.
private val BREAKS = setOf(' '.code, '\t'.code, '\n'.code, '\r'.code)
private val CONTROLS = setOf(
    CharCategory.CONTROL, CharCategory.FORMAT, CharCategory.UNASSIGNED, CharCategory.PRIVATE_USE, CharCategory.SURROGATE
)
private val PUNCTUATION = setOf(
    CharCategory.CONNECTOR_PUNCTUATION, CharCategory.DASH_PUNCTUATION, CharCategory.START_PUNCTUATION,
    CharCategory.END_PUNCTUATION, CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION,
    CharCategory.OTHER_PUNCTUATION
)
private val ASCII_PUNCTUATION = listOf(33..47, 58..64, 91..96, 123..126)
private val CJK = listOf(
    0x4E00..0x9FFF, 0x3400..0x4DBF, 0x20000..0x2A6DF, 0x2A700..0x2B73F, 0x2B740..0x2B81F, 0x2B820..0x2CEAF,
    0xF900..0xFAFF, 0x2F800..0x2FA1F
)

private fun isWhitespace(cp: Int, chars: String): Boolean =
    !isSupplementary(chars) && (cp in BREAKS || chars[0].isWhitespace())

private fun isControl(cp: Int, chars: String): Boolean =
    !isSupplementary(chars) && cp !in BREAKS && chars[0].category in CONTROLS

private fun isPunctuation(cp: Int, chars: String): Boolean =
    ASCII_PUNCTUATION.any { cp in it } || (!isSupplementary(chars) && chars[0].category in PUNCTUATION)

private fun isChinese(cp: Int): Boolean = CJK.any { cp in it }
