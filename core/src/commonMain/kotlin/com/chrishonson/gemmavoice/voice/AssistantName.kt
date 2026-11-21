package com.chrishonson.gemmavoice.voice

private const val MAX_WORDS = 2
private val NAME_LENGTH = 2..20
private val WHITESPACE = Regex("\\s+")
private val INNER_PUNCTUATION = setOf('\'', '-')

/**
 * Returns the name normalized for display and wake-word use, or null when it is not one or two words of letters.
 * An apostrophe or hyphen may join letters inside a word. The first letter of each word is capitalized.
 */
fun validateAssistantName(raw: String): String? {
    val words = raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (words.size !in 1..MAX_WORDS || !words.all(::isNameWord)) return null
    val name = words.joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
    return name.takeIf { it.length in NAME_LENGTH }
}

private fun isNameWord(word: String): Boolean =
    word.first().isLetter() &&
        word.last().isLetter() &&
        word.all { it.isLetter() || it in INNER_PUNCTUATION } &&
        word.zipWithNext().none { (a, b) -> !a.isLetter() && !b.isLetter() }
