package app.pin.voicenotes.domain.parser

/**
 * Tiny contiguous phrase matcher over canonical tokens.
 * A phrase is an ordered list of slots; each slot accepts a set of canonical
 * alternatives and may be optional. All matching is exact-token, in order,
 * with no gaps — small and auditable by design.
 */
data class Slot(val alts: Set<String>, val optional: Boolean = false)

data class PhraseMatch(val range: IntRange) {
    val indices: Set<Int> get() = range.toSet()
}

object PhraseMatcher {

    fun firstMatch(tokens: List<String>, phrases: List<List<Slot>>): PhraseMatch? {
        for (phrase in phrases) {
            matchPhrase(tokens, phrase)?.let { return it }
        }
        return null
    }

    private fun matchPhrase(tokens: List<String>, slots: List<Slot>): PhraseMatch? {
        outer@ for (start in tokens.indices) {
            var pos = start
            for (slot in slots) {
                val t = tokens.getOrNull(pos)
                if (t != null && t in slot.alts) {
                    pos++
                } else if (!slot.optional) {
                    continue@outer
                }
            }
            if (pos > start) return PhraseMatch(start until pos)
        }
        return null
    }
}
