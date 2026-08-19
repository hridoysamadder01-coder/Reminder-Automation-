package app.pin.voicenotes.domain.parser

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * বাংলা সংখ্যা (০-৯) কে ইংরেজি ডিজিটে (0-9) বদলায় — শুধু পার্সিংয়ের জন্য।
 * "১০টায়" বুঝতে হলে ভেতরে "10" লাগে, কিন্তু ব্যবহারকারীর নোটে
 * তার নিজের লেখা হরফই অক্ষত থাকে।
 */

/**
 * Bengali numerals appear routinely in both spoken transcripts and typed text
 * (e.g. "কাল সকাল ১০টায়"). Parsing happens on ASCII digits; the user's
 * surface text keeps whatever script they used.
 */
object BanglaDigits {

    private const val BENGALI_ZERO = '০'
    private const val BENGALI_NINE = '৯'

    fun toAscii(text: String): String {
        if (text.none { it in BENGALI_ZERO..BENGALI_NINE }) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(if (ch in BENGALI_ZERO..BENGALI_NINE) '0' + (ch - BENGALI_ZERO) else ch)
        }
        return sb.toString()
    }

    fun containsDigit(text: String): Boolean =
        text.any { it in '0'..'9' || it in BENGALI_ZERO..BENGALI_NINE }
}
