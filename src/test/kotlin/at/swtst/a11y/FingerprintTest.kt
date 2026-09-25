package at.swtst.a11y

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class FingerprintTest {

    @Test
    fun `path drops host and query and replaces ids`() {
        assertEquals("/antrag/{id}/edit", Fingerprint.normalizePath("https://wbis.example/antrag/4711/edit?tab=2"))
        assertEquals(
            "/doc/{uuid}",
            Fingerprint.normalizePath("https://x/doc/3f2a9c1e-1111-2222-3333-444455556666"),
        )
    }

    @Test
    fun `selector replaces generated jsf ids and long numbers`() {
        assertEquals("#form\\:j_idt{n} > input", Fingerprint.normalizeSelector("#form\\:j_idt123 > input"))
        assertEquals("#row-{n}", Fingerprint.normalizeSelector("#row-12345"))
        assertEquals("li:nth-child(12)", Fingerprint.normalizeSelector("li:nth-child(12)"))
    }

    @Test
    fun `hash is stable and shortened`() {
        val a = Fingerprint.hash(Fingerprint.key("color-contrast", "/start", "#btn"))
        val b = Fingerprint.hash(Fingerprint.key("color-contrast", "/start", "#btn"))
        val c = Fingerprint.hash(Fingerprint.key("color-contrast", "/start", "#other"))
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(A11yConfig.hashLength, a.length)
    }
}
