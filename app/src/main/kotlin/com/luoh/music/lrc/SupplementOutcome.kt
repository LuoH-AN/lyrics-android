package com.luoh.music.lrc

/** Skipping Chinese lyrics is a successful decision, not an empty translation response. */
enum class SupplementOutcome { COMPLETE, NOT_NEEDED, DISABLED }

internal object TranslationText {
    fun hasForeignLetters(text: String): Boolean = text.codePoints().anyMatch { codePoint ->
        Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN
    }

    private fun foreignScripts(text: String): Set<Character.UnicodeScript> = text.codePoints().toArray()
        .filter { Character.isLetter(it) }
        .map { Character.UnicodeScript.of(it) }
        .filter { it != Character.UnicodeScript.HAN }
        .toSet()

    private fun languageSample(text: String, scripts: Set<Character.UnicodeScript>): String {
        // Keep kanji/hanja as context for Japanese and Korean. In other mixed lines,
        // Chinese must not outweigh the foreign words whose language we need to identify.
        if (scripts.any { it in setOf(Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL) }) return text
        return buildString {
            text.codePoints().forEach { codePoint ->
                if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) append(' ')
                else appendCodePoint(codePoint)
            }
        }.trim().replace(Regex("\\s+"), " ")
    }

    /** Resolve each distinct foreign line, using same-script context only for ambiguous short lines. */
    suspend fun identifyLanguages(texts: Collection<String>, identify: suspend (String) -> String): Map<String, String?> {
        val candidates = texts.distinct().filter(::hasForeignLetters)
        val scripts = candidates.associateWith(::foreignScripts)
        val samples = candidates.associateWith { languageSample(it, scripts.getValue(it)) }
        val detected = mutableMapOf<String, String?>()
        suspend fun detect(sample: String) = identify(sample).takeUnless { it == "und" || it == "zh" }
        for (text in candidates) detected[text] = detect(samples.getValue(text))
        for (sameScript in candidates.groupBy { scripts.getValue(it) }.values) {
            val unknown = sameScript.filter { detected[it] == null }
            if (unknown.isEmpty()) continue
            val known = sameScript.mapNotNull { detected[it] }.distinct()
            val fallback = when {
                known.size == 1 -> known.single()
                known.isEmpty() && sameScript.size > 1 ->
                    detect(sameScript.joinToString("\n") { samples.getValue(it) }.take(4000))
                else -> null
            }
            if (fallback != null) unknown.forEach { detected[it] = fallback }
        }
        return detected
    }
}
