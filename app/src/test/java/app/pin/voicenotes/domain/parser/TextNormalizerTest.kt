package app.pin.voicenotes.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `bengali digits convert for parsing only`() {
        assertEquals("1030", BanglaDigits.toAscii("১০৩০"))
        assertEquals("mixed 42", BanglaDigits.toAscii("mixed ৪২"))
    }

    @Test
    fun `attached time suffix splits into two tokens`() {
        val tk = TextNormalizer.tokenize("কাল ১০টায় মনে করাইস")
        assertEquals(listOf("kal", "10", "ta", "mone", "korais"), tk.canonical)
    }

    @Test
    fun `latin attached suffix splits`() {
        val tk = TextNormalizer.tokenize("10tay dekha hobe")
        assertEquals("10", tk.canonical[0])
        assertEquals("ta", tk.canonical[1])
    }

    @Test
    fun `spelling variants collapse`() {
        val a = TextNormalizer.tokenize("kal sokale mone korai dio").canonical
        val b = TextNormalizer.tokenize("kaal shokale mone koriye dio").canonical
        assertEquals(a, b)
    }

    @Test
    fun `script boundaries split`() {
        val tk = TextNormalizer.tokenize("noteটা pin কর")
        assertEquals(listOf("note", "ta", "pin", "kor"), tk.canonical)
    }

    @Test
    fun `surface tokens preserve script and casing`() {
        val tk = TextNormalizer.tokenize("PharmacyOS note ta khol")
        assertEquals("PharmacyOS", tk.surface[0])
    }

    @Test
    fun `dotted time becomes colon time`() {
        val tk = TextNormalizer.tokenize("kal 10.30 tay")
        assertEquals("10:30", tk.canonical[1])
    }
}
