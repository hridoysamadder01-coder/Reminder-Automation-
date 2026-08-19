package app.pin.voicenotes.domain.parser

/**
 * Maintainable normalization dictionary for the focused voice domain.
 *
 * Every entry maps a surface token — Bengali script, Banglish/romanized
 * Bangla, or English — to one canonical token. All downstream matching
 * (intent phrases, date/time) happens on canonical tokens only, so adding a
 * new spelling variant is a one-line change here.
 */
object Vocabulary {

    val canonicalMap: Map<String, String> = buildMap {
        // ---- Relative days -------------------------------------------------
        put(listOf("aj", "aaj", "ajke", "aajke", "ajker", "আজ", "আজকে", "আজকের", "আইজ", "today"), "aj")
        put(
            listOf(
                "kal", "kaal", "kalke", "kaalke", "kalker", "kalker", "agamikal", "agami",
                "কাল", "কালকে", "কালকের", "আগামীকাল", "আগামিকাল", "আগামী", "tomorrow"
            ),
            "kal"
        )
        put(listOf("porshu", "poroshu", "porsu", "পরশু", "পরশুদিন", "porshudin"), "porshu")

        // ---- Day periods ---------------------------------------------------
        put(
            listOf(
                "shokal", "sokal", "shokale", "sokale", "shokal e", "shokhale", "sakal", "sakale",
                "সকাল", "সকালে", "ভোর", "ভোরে", "bhor", "bhore", "morning"
            ),
            "shokal"
        )
        put(listOf("dupur", "dupure", "dupurbela", "দুপুর", "দুপুরে", "noon"), "dupur")
        put(
            listOf("bikal", "bikale", "bikel", "bikele", "বিকাল", "বিকালে", "বিকেল", "বিকেলে", "afternoon"),
            "bikal"
        )
        put(
            listOf("shondha", "sondha", "shondhay", "sondhay", "shondhaye", "সন্ধ্যা", "সন্ধ্যায়", "সন্ধা", "evening"),
            "shondha"
        )
        put(
            listOf("rat", "raat", "rate", "raate", "ratre", "রাত", "রাতে", "রাত্রে", "night", "tonight"),
            "rat"
        )

        // ---- Time words ----------------------------------------------------
        // Digit-attached suffixes ("১০টায়", "10tay") are split off before
        // tokenization, so these arrive as standalone tokens.
        put(listOf("ta", "tay", "tai", "tey", "টা", "টায়", "টায", "টাই", "টারদিকে"), "ta")
        put(listOf("টার"), "ta") // "১০টার দিকে" — Bengali only; Latin "tar" stays ambiguous
        put(listOf("baje", "bajey", "bajhe", "বাজে", "বাজেয়", "oclock"), "baje")
        put(listOf("minute", "min", "মিনিট", "minit"), "minute")
        put(listOf("ghonta", "ghanta", "hour", "hours", "ঘণ্টা", "ঘন্টা"), "ghonta")
        put(listOf("por", "pore", "পর", "পরে", "later"), "por")
        put(listOf("dike", "দিকে", "nagad", "নাগাদ", "somoy", "সময়", "around"), "dike")
        put(listOf("am", "a.m"), "am")
        put(listOf("pm", "p.m"), "pm")

        // ---- Reminder verbs ------------------------------------------------
        put(listOf("mone", "মনে", "mne"), "mone")
        put(listOf("korais", "korash", "koris", "korish", "করাইস", "করিস", "করাস"), "korais")
        put(
            listOf(
                "koriye", "korie", "koraiye", "korai", "koray", "koraye",
                "করিয়ে", "করাই", "করায়ে", "করাইয়া", "কইরে"
            ),
            "koriye"
        )
        put(listOf("dis", "diss", "দিস"), "dis")
        put(listOf("dio", "diyo", "diyen", "দিও", "দিয়ো", "দিয়েন"), "dio")
        put(listOf("de", "দে"), "de")
        put(listOf("dibi", "dibe", "diba", "দিবি", "দিবে", "দিবা"), "dibi")
        put(listOf("daw", "dao", "দাও"), "daw")
        put(listOf("reminder", "remainder", "রিমাইন্ডার", "রিমাইন্ডর"), "reminder")
        put(listOf("remind", "রিমাইন্ড"), "remind")
        put(listOf("alarm", "এলার্ম", "অ্যালার্ম", "alam"), "alarm")

        // ---- Note verbs ----------------------------------------------------
        put(listOf("likhe", "lekhe", "likh", "lekh", "লিখে", "লেখ", "লিখ", "লেখে"), "likhe")
        put(listOf("tuke", "টুকে", "tuka"), "tuke")
        put(listOf("rakh", "rakho", "rakhis", "rakhbi", "rak", "রাখ", "রাখো", "রাখিস", "রাখবি"), "rakh")
        put(listOf("save", "সেভ", "sev"), "save")
        put(listOf("kor", "koro", "কর", "করো"), "kor")
        put(listOf("kora", "koira", "kore", "করা", "কইরা", "করে"), "kora")
        put(listOf("write"), "write")
        put(listOf("down"), "down")

        // ---- Pin -----------------------------------------------------------
        put(listOf("pin", "পিন", "pinned"), mappedPinned = true)
        put(listOf("khule", "খুলে", "khula"), "khule")
        put(listOf("sorai", "sora", "soraiya", "সরাই", "সরা", "সরায়ে", "remove"), "sorai")
        put(listOf("tule", "তুলে"), "tule")
        put(listOf("unpin", "আনপিন"), "unpin")

        // ---- Open / show / search / delete --------------------------------
        put(listOf("khol", "kholo", "khulo", "খোল", "খোলো", "খুলো", "open"), "khol")
        put(
            listOf(
                "dekhaw", "dekhao", "dekha", "dekhas", "dekhabi", "dekhaish", "dakha", "dakhao",
                "দেখাও", "দেখা", "দেখাস", "দেখাইস", "show", "list"
            ),
            "dekhaw"
        )
        put(listOf("khoj", "khuje", "khujba", "খোঁজ", "খুঁজে", "search", "find"), "khoj")
        put(listOf("delete", "ডিলিট", "ডিলেট", "dilit"), "delete")
        put(listOf("muche", "মুছে", "mucha", "মোছ"), "muche")
        put(listOf("fel", "fele", "fela", "ফেল", "ফেলে", "ফালা", "ফেলো"), "fel")

        // ---- Nouns / structure --------------------------------------------
        put(listOf("note", "notes", "নোট", "নোটস", "নোটটা"), "note")
        put(listOf("gula", "gulo", "guli", "গুলা", "গুলো", "গুলি", "sob", "সব", "all"), "gula")
        put(listOf("shesh", "শেষ", "last", "latest"), "shesh")
        put(listOf("first", "prothom", "প্রথম"), "first")

        // ---- Fillers / connectors -----------------------------------------
        put(listOf("eita", "eta", "ita", "এইটা", "এটা", "ইটা", "this"), "eita")
        put(listOf("oita", "ota", "ওইটা", "ওটা", "that"), "oita")
        put(listOf("ei", "এই"), "ei")
        put(listOf("oi", "ওই", "ঐ"), "oi")
        put(listOf("ar", "আর", "and"), "ar")
        put(listOf("amake", "amare", "আমাকে", "আমারে", "me"), "amake")
        put(listOf("amar", "আমার", "my"), "amar")
        put(listOf("je", "যে", "jei", "যেই"), "je")
        put(listOf("ti", "টি"), "ti")
        put(listOf("er", "এর"), "er")
        put(listOf("to", "তো"), "to")
        put(listOf("taka", "টাকা", "tk"), "taka")
    }

    /** Small helper so variant lists stay one-per-concept and greppable. */
    private fun MutableMap<String, String>.put(
        variants: List<String>,
        canonical: String? = null,
        mappedPinned: Boolean = false,
    ) {
        for (v in variants) {
            // "pinned" keeps its own identity; plain "pin" stays "pin".
            val target = when {
                mappedPinned && v == "pinned" -> "pinned"
                mappedPinned -> "pin"
                else -> canonical!!
            }
            this[v] = target
        }
    }

    fun canonicalOf(token: String): String = canonicalMap[token] ?: token
}
