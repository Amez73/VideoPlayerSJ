package com.shareef.videoplayersj.util

private val defaultStopwords = setOf("a", "an", "the", "and", "of", "&")

/**
 * Lowercases, splits on non-alphanumeric runs, drops stopwords/blanks, and dedupes
 * while preserving first-seen order (order matters for building a readable display title).
 */
fun normalizeToTokens(text: String, stopwords: Set<String> = defaultStopwords): List<String> =
    text.lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() && it !in stopwords }
        .distinct()

/** Intersection size over the smaller set's size — forgiving when one side has a stray extra token. */
fun overlapCoefficient(a: List<String>, b: List<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val setA = a.toSet()
    val setB = b.toSet()
    val intersection = setA.count { it in setB }
    return intersection.toDouble() / minOf(setA.size, setB.size)
}
