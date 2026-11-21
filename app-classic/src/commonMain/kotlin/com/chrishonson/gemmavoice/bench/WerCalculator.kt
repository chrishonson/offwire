package com.chrishonson.gemmavoice.bench

import kotlin.math.ceil

private const val P90_PERCENTILE_RATIO = 0.9
private const val CURLY_APOSTROPHE = '’'
private const val STRAIGHT_APOSTROPHE = '\''

object WerCalculator {

    fun normalize(text: String): String {
        val filtered = buildString {
            for (ch in text.lowercase()) {
                when {
                    ch in 'a'..'z' || ch in '0'..'9' || ch == STRAIGHT_APOSTROPHE -> append(ch)
                    ch == CURLY_APOSTROPHE -> append(STRAIGHT_APOSTROPHE)
                    ch.isWhitespace() -> append(' ')
                }
            }
        }
        return filtered.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun tokenize(text: String): List<String> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(" ")
    }

    fun wordLevenshteinDistance(referenceWords: List<String>, hypothesisWords: List<String>): Int {
        if (referenceWords.isEmpty()) return hypothesisWords.size
        if (hypothesisWords.isEmpty()) return referenceWords.size

        val refSize = referenceWords.size
        val hypSize = hypothesisWords.size
        var previousRow = IntArray(hypSize + 1) { it }
        var currentRow = IntArray(hypSize + 1)

        for (i in 1..refSize) {
            currentRow[0] = i
            val refWord = referenceWords[i - 1]
            for (j in 1..hypSize) {
                val hypWord = hypothesisWords[j - 1]
                val cost = if (refWord == hypWord) 0 else 1
                currentRow[j] = minOf(
                    previousRow[j] + 1,
                    currentRow[j - 1] + 1,
                    previousRow[j - 1] + cost
                )
            }
            val temp = previousRow
            previousRow = currentRow
            currentRow = temp
        }
        return previousRow[hypSize]
    }

    fun sentenceWer(reference: String, hypothesis: String): Double {
        val refWords = tokenize(reference)
        val hypWords = tokenize(hypothesis)
        if (refWords.isEmpty()) {
            return if (hypWords.isEmpty()) 0.0 else hypWords.size.toDouble()
        }
        val edits = wordLevenshteinDistance(refWords, hypWords)
        return edits.toDouble() / refWords.size
    }

    fun overallWer(pairs: List<Pair<String, String>>): Double {
        var totalEdits = 0
        var totalRefWords = 0
        for ((ref, hyp) in pairs) {
            val refWords = tokenize(ref)
            val hypWords = tokenize(hyp)
            totalEdits += wordLevenshteinDistance(refWords, hypWords)
            totalRefWords += refWords.size
        }
        if (totalRefWords == 0) return 0.0
        return totalEdits.toDouble() / totalRefWords
    }

    fun calculateMean(values: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        return values.average()
    }

    fun calculateMax(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        return values.maxOrNull() ?: 0L
    }

    fun calculateP90(values: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = (ceil(P90_PERCENTILE_RATIO * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
        return sorted[index].toDouble()
    }
}
