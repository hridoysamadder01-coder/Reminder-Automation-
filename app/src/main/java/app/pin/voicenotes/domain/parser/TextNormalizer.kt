package app.pin.voicenotes.domain.parser

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * কাঁচা কথা/লেখাকে শব্দে (token) ভাঙে, আর প্রতিটা শব্দের দুটো রূপ রাখে:
 *  - surface  = ব্যবহারকারী যেভাবে লিখেছে/বলেছে (নোট বানাতে ব্যবহার হয়)
 *  - canonical = Vocabulary দিয়ে normalized রূপ (বোঝার কাজে ব্যবহার হয়)
 * "১০টায়" → "১০ টায়" আলাদা হয়, "noteটা" → "note টা", "10.30" → "10:30"।
 * দুই লিস্টের দৈর্ঘ্য সবসময় সমান — তাই কোন শব্দ বাদ দিতে হবে তা index দিয়ে মেলে।
 */

/**
 * Tokenizes raw voice/typed input into parallel surface + canonical token
 * lists. Surface tokens preserve the user's script and casing (they are used
 * to rebuild note content); canonical tokens are what intent and date/time
 * matching run on.
 *
 * The two lists always have identical length, which lets the intent layer
 * mark canonical indices as "consumed" and drop the same surface tokens.
 */
data class Tokenized(
    val surface: List<String>,
    val canonical: List<String>,
) {
    val size: Int get() = surface.size
}

object TextNormalizer {

    // Punctuation that never carries meaning in this domain. The Bengali
    // danda "।" ends sentences the way "." does.
    private val PUNCTUATION = Regex("[,;!?()\\[\\]{}\"“”‘’।|—–-]")

    // "10.30" spoken as a time; rewrite to the canonical colon form before
    // the dot gets treated as sentence punctuation.
    private val DOTTED_TIME = Regex("([0-9০-৯]{1,2})\\.([0-9০-৯]{2})")

    // Split digit-runs from attached words: "১০টায়" -> "১০ টায়", "9pm" -> "9 pm".
    private val DIGIT_THEN_LETTER = Regex("([0-9০-৯])([\\p{L}])")

    // Split script boundaries inside one token: "noteটা" -> "note টা".
    private val LATIN_THEN_BENGALI = Regex("([a-zA-Z])([\\u0980-\\u09FF])")
    private val BENGALI_THEN_LATIN = Regex("([\\u0980-\\u09FF])([a-zA-Z])")

    private val TRAILING_DOTS = Regex("[.…:]+$")
    private val LEADING_DOTS = Regex("^[.…:]+")
    private val TIME_TOKEN = Regex("^\\d{1,2}:\\d{2}$")

    fun tokenize(raw: String): Tokenized {
        var text = raw
        text = DOTTED_TIME.replace(text) { m -> "${m.groupValues[1]}:${m.groupValues[2]}" }
        text = PUNCTUATION.replace(text, " ")
        text = DIGIT_THEN_LETTER.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }
        text = LATIN_THEN_BENGALI.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }
        text = BENGALI_THEN_LATIN.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }

        val surface = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val canonical = surface.map { canonicalize(it) }
        return Tokenized(surface, canonical)
    }

    private fun canonicalize(token: String): String {
        var s = BanglaDigits.toAscii(token).lowercase()
        s = LEADING_DOTS.replace(s, "")
        // Keep the colon inside "10:30" but strip a trailing "10:".
        if (!TIME_TOKEN.matches(s)) s = TRAILING_DOTS.replace(s, "")
        if (s.isEmpty()) return s
        return Vocabulary.canonicalOf(s)
    }
}
