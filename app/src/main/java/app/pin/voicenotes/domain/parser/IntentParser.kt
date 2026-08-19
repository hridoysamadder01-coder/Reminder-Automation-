package app.pin.voicenotes.domain.parser

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * অ্যাপের মগজ: normalized শব্দ দেখে ঠিক করে ব্যবহারকারী কী চায় —
 * নোট লেখা, রিমাইন্ডার, পিন/আনপিন, পিন-করা নোট দেখা, নোট খোলা/খোঁজা,
 * আজকের নোট/রিমাইন্ডার দেখা, নাকি ডিলিট। কোনো AI নেই — সব নিয়মভিত্তিক।
 * ক্রম গুরুত্বপূর্ণ: আগে পিন/আনপিন, তারপর দেখা/খোলা, তারপর রিমাইন্ডার/নোট।
 * নোটের লেখা বানানোর সময় কমান্ড শব্দ ("likhe rakh", "mone korais") আর
 * রিমাইন্ডারের সময়-শব্দ বাদ যায়, সাথে লেগে থাকা filler-ও ("eita", "ar", "amare") —
 * কিন্তু অর্থবহ কথা কখনো ফেলা হয় না; সন্দেহ হলে বেশি রাখাই নিয়ম।
 * কোনো কমান্ড না মিললে ডিফল্ট কাজ: নোট হিসেবে সেভ (এই অ্যাপের স্বভাবই মনে রাখা)।
 */

import java.time.Clock

/**
 * The deterministic local intent engine: normalized tokens in, [VoiceIntent]
 * out. Understands common Bangla, Banglish, English and mixed phrasing for
 * this focused domain. Never guesses an ambiguous reminder time.
 */
class IntentParser(clock: Clock = Clock.systemDefaultZone()) {

    private val dateTimeParser = DateTimeParser(clock)

    // ---- Phrase definitions (canonical tokens only) ------------------------

    private fun s(vararg alts: String) = Slot(alts.toSet())
    private fun opt(vararg alts: String) = Slot(alts.toSet(), optional = true)

    private val giveVerbs = arrayOf("dis", "dio", "de", "daw", "dibi")

    private val reminderCreatePhrases: List<List<Slot>> = listOf(
        listOf(s("mone"), s("korais")),
        listOf(s("mone"), s("koriye"), opt(*giveVerbs)),
        listOf(
            s("reminder", "alarm"), opt("ta"),
            s(*giveVerbs, "kor", "rakh", "set", "korais"),
            opt("kora"), opt(*giveVerbs, "kor", "rakh"),
        ),
        listOf(s("set"), opt("a", "ekta"), s("reminder", "alarm"), opt(*giveVerbs, "kor")),
        listOf(s("ekta", "a"), s("reminder", "alarm"), s(*giveVerbs, "kor", "set")),
        listOf(s("remind"), s("amake", "korais", "koriye", "kor", *giveVerbs)),
    )

    private val noteCreatePhrases: List<List<Slot>> = listOf(
        listOf(s("likhe"), opt("kora"), s("rakh")),
        listOf(s("tuke"), s("rakh")),
        listOf(s("save", "note"), opt("kora"), s("kor", "rakh")),
        listOf(s("write"), opt("eita"), s("down")),
        listOf(s("save"), s("eita", "oita")),
    )

    private val pinPhrases: List<List<Slot>> = listOf(
        listOf(s("pin"), opt("kora"), s("kor", "rakh", *giveVerbs)),
        listOf(s("pin"), s("eita", "oita")),
    )

    private val unpinPhrases: List<List<Slot>> = listOf(
        listOf(s("unpin")),
        listOf(s("pin"), s("khule", "sorai", "tule"), opt(*giveVerbs, "fel")),
        listOf(s("pin"), s("off")),
    )

    private val pinnedMarkerPhrases: List<List<Slot>> = listOf(
        listOf(s("pinned")),
        listOf(s("pin"), s("kora")),
    )

    private val deletePhrases: List<List<Slot>> = listOf(
        listOf(s("delete"), opt("kora"), opt("kor", "de", "fel", "dis", "daw")),
        listOf(s("muche"), s("fel", "daw", "de", "dis", "dio")),
    )

    // Tokens that never belong in a search query or note-open query.
    private val queryStopwords = setOf(
        "note", "ta", "ti", "gula", "er", "ei", "eita", "oi", "oita", "amar",
        "amake", "ar", "je", "to", "khol", "dekhaw", "khoj", "delete", "muche",
        "fel", "aj", "kal", "porshu", "shesh", "first", "pinned", "pin", "kora",
        "kor", "daw", "de", "dis", "dio", "dibi", "reminder", "alarm", "baje",
    )

    // Fillers glued to a command phrase ("eita likhe rakh", "ar amare mone korais").
    private val adjacentBefore = setOf("eita", "oita", "ta", "ti", "ar", "amake", "je", "to")
    private val adjacentAfter = setOf("je", "to", "for", "at")
    private val leadingTrim = setOf("ar", "to", "je", "ei", "eita", "oita", "oi", "amar", "amake")
    private val trailingTrim = setOf("ar", "to", "je")

    // ---- Entry point -------------------------------------------------------

    fun parse(raw: String): VoiceIntent {
        val tk = TextNormalizer.tokenize(raw)
        if (tk.size == 0) return VoiceIntent.Unknown(raw)
        val c = tk.canonical

        // Pin state changes first: they are short, explicit commands.
        PhraseMatcher.firstMatch(c, unpinPhrases)?.let {
            return VoiceIntent.UnpinNote(queryFrom(tk, it.indices))
        }
        PhraseMatcher.firstMatch(c, pinPhrases)?.let {
            return VoiceIntent.PinNote(queryFrom(tk, it.indices))
        }

        val showVerb = "dekhaw" in c
        val kholVerb = "khol" in c
        val reminderCreate = PhraseMatcher.firstMatch(c, reminderCreatePhrases)
        val noteCreate = PhraseMatcher.firstMatch(c, noteCreatePhrases)

        // "pinned note gula dekhaw" / "pinned first note ta khol"
        val pinnedMarker = PhraseMatcher.firstMatch(c, pinnedMarkerPhrases)
        if (pinnedMarker != null && (showVerb || kholVerb)) {
            return if (kholVerb && "gula" !in c) {
                VoiceIntent.OpenNote(
                    query = queryFrom(tk, pinnedMarker.indices),
                    pinnedOnly = true,
                    latest = "shesh" in c,
                )
            } else {
                VoiceIntent.ShowPinned
            }
        }

        // "ajker reminder dekhao", "reminder gula dekhaw"
        if (("reminder" in c || "alarm" in c) && (showVerb || kholVerb) && reminderCreate == null) {
            return VoiceIntent.ShowReminders(todayOnly = "aj" in c)
        }

        // "ajker note gula dekhao" / "ajker shesh note khol"
        if ("aj" in c && "note" in c && (showVerb || kholVerb)) {
            return if ("shesh" in c && kholVerb) {
                VoiceIntent.OpenNote(query = "", dayOffset = 0, latest = true)
            } else {
                VoiceIntent.ShowTodayNotes
            }
        }

        // "PharmacyOS note ta khol", "kalker note khol"
        if (kholVerb && reminderCreate == null && noteCreate == null) {
            val day = when {
                "kal" in c -> 1
                "porshu" in c -> 2
                "aj" in c -> 0
                else -> null
            }
            return VoiceIntent.OpenNote(
                query = queryFrom(tk, emptySet()),
                dayOffset = day,
                latest = "shesh" in c,
            )
        }

        PhraseMatcher.firstMatch(c, deletePhrases)?.let {
            return VoiceIntent.DeleteNote(queryFrom(tk, it.indices))
        }

        if ("khoj" in c) {
            return VoiceIntent.SearchNotes(queryFrom(tk, emptySet()))
        }

        // "note gula dekhaw" — a plain listing request.
        if (showVerb && "note" in c) {
            return VoiceIntent.SearchNotes(queryFrom(tk, emptySet()))
        }

        val dt = dateTimeParser.parse(c)
        val commandSpans = listOfNotNull(reminderCreate, noteCreate)

        if (reminderCreate != null) {
            val dtConsumed = when (dt) {
                is DateTimeResult.Resolved -> dt.consumed
                is DateTimeResult.NeedsTime -> dt.consumed
                is DateTimeResult.PastTime -> dt.consumed
                DateTimeResult.None -> emptySet()
            }
            val content = contentFrom(tk, commandSpans, dtConsumed)
            return when (dt) {
                is DateTimeResult.Resolved ->
                    VoiceIntent.CreateNoteWithReminder(content, dt.dateTime)
                is DateTimeResult.NeedsTime ->
                    VoiceIntent.NeedsTimeClarification(content, dt.date, dt.period)
                is DateTimeResult.PastTime ->
                    VoiceIntent.NeedsTimeClarification(
                        content, dt.dateTime.toLocalDate(), null, wasPast = true
                    )
                DateTimeResult.None ->
                    VoiceIntent.NeedsTimeClarification(content, null, null)
            }
        }

        if (noteCreate != null) {
            // A note keeps its time words — they are information, not schedule.
            val content = contentFrom(tk, commandSpans, emptySet())
            return VoiceIntent.CreateNote(content, Confidence.HIGH)
        }

        // No explicit command: the default act of this app is remembering.
        val content = contentFrom(tk, emptyList(), emptySet())
        if (content.isBlank()) return VoiceIntent.Unknown(raw)
        return VoiceIntent.CreateNote(content, Confidence.MEDIUM)
    }

    // ---- Content / query reconstruction ------------------------------------

    private fun queryFrom(tk: Tokenized, consumed: Set<Int>): String =
        tk.surface.filterIndexed { i, _ ->
            i !in consumed && tk.canonical[i] !in queryStopwords
        }.joinToString(" ").trim()

    private fun contentFrom(
        tk: Tokenized,
        spans: List<PhraseMatch>,
        dtConsumed: Set<Int>,
    ): String {
        val consumed = mutableSetOf<Int>()
        consumed += dtConsumed
        for (span in spans) {
            consumed += span.indices
            // Peel fillers glued to the command: "eita likhe rakh", "ar amare mone korais".
            var left = span.range.first - 1
            while (left >= 0 && left !in consumed && tk.canonical[left] in adjacentBefore) {
                consumed += left
                left--
            }
            var right = span.range.last + 1
            while (right < tk.size && right !in consumed && tk.canonical[right] in adjacentAfter) {
                consumed += right
                right++
            }
        }

        val kept = tk.surface.indices.filter { it !in consumed }.toMutableList()
        while (kept.isNotEmpty() && tk.canonical[kept.first()] in leadingTrim) kept.removeAt(0)
        while (kept.isNotEmpty() && tk.canonical[kept.last()] in trailingTrim) kept.removeAt(kept.size - 1)

        return kept.joinToString(" ") { tk.surface[it] }.trim()
    }
}
