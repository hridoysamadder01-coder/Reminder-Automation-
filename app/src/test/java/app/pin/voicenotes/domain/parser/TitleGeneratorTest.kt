package app.pin.voicenotes.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleGeneratorTest {

    @Test
    fun `short content is its own title`() {
        assertEquals("Krishna dada ke call", TitleGenerator.generate("Krishna dada ke call"))
    }

    @Test
    fun `first sentence wins`() {
        assertEquals(
            "Buy medicine",
            TitleGenerator.generate("Buy medicine. Also call the pharmacy about the order.")
        )
    }

    @Test
    fun `bengali danda breaks sentences`() {
        assertEquals(
            "কৃষ্ণ দাদাকে ফোন দিতে হবে",
            TitleGenerator.generate("কৃষ্ণ দাদাকে ফোন দিতে হবে। PharmacyOS নিয়ে কথা হবে।")
        )
    }

    @Test
    fun `long content truncates at word boundary`() {
        val title = TitleGenerator.generate(
            "this is a very long note about the inventory workflow idea for the pharmacy"
        )
        assertTrue(title.length <= 46)
        assertTrue(title.endsWith("…"))
        assertTrue(!title.contains("workflow idea for"))
    }

    @Test
    fun `empty content uses fallback`() {
        assertEquals("Reminder", TitleGenerator.generate("  ", fallback = "Reminder"))
    }
}
