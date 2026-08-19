package app.pin.voicenotes.domain.parser

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * নোটের লেখা থেকে ছোট্ট শিরোনাম বানায়: প্রথম বাক্য নেয় (দাঁড়ি "।"-ও চেনে),
 * বেশি লম্বা হলে শব্দের মাঝে না কেটে সীমানায় কেটে "…" দেয়। কোনো AI নেই।
 */

/**
 * Local, rule-based title from note content: first sentence/clause, cleanly
 * truncated at a word boundary. No model involved; the user can edit later.
 */
object TitleGenerator {

    private const val MAX_LENGTH = 44
    private val SENTENCE_BREAK = Regex("[।.!?\\n]")

    fun generate(content: String, fallback: String = "Note"): String {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return fallback

        val firstSentence = SENTENCE_BREAK.split(trimmed)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return fallback

        if (firstSentence.length <= MAX_LENGTH) return firstSentence

        val cut = firstSentence.take(MAX_LENGTH + 1)
        val lastSpace = cut.lastIndexOf(' ')
        val head = if (lastSpace > MAX_LENGTH / 2) cut.take(lastSpace) else cut.take(MAX_LENGTH)
        return head.trimEnd() + "…"
    }
}
