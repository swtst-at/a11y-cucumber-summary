package at.swtst.a11y

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class A11yCheckTest {

    private val contrast = RawNode("color-contrast", "serious", "Kontrast", null, "[\"#submit\"]", "<button>")
    private val label = RawNode("label", "critical", "Label fehlt", null, "[\"#form\\\\:j_idt42\"]", "<input>")

    private fun fp(node: RawNode, page: String) =
        Fingerprint.hash(Fingerprint.key(node.rule, page, Fingerprint.normalizeSelector(node.target)))

    @BeforeEach
    fun setUp() {
        A11yRegistry.reset()
        Baseline.override(listOf(BaselineEntry(fingerprint = fp(contrast, "/request"), ticket = "SWTST-1234")))
    }

    @AfterEach
    fun tearDown() = Baseline.override(null)

    @Test
    fun `baseline entries are known, others are new`() {
        val result = A11yCheck.evaluate(listOf(contrast, label), "/request")

        assertEquals(1, result.knownFindings.size)
        assertEquals("SWTST-1234", result.knownFindings.single().ticket)
        assertEquals(1, result.newFindings.size)
        assertEquals("label", result.newFindings.single().finding.rule)
        assertTrue(result.newFindings.single().firstInRun)
    }

    @Test
    fun `second scenario with same finding is not first in run`() {
        A11yCheck.evaluate(listOf(label), "/request")
        val second = A11yCheck.evaluate(listOf(label), "/request")

        assertFalse(second.newFindings.single().firstInRun)
        assertTrue(second.newFirstInRun.isEmpty())
    }

    @Test
    fun `report writer produces summary, candidates and stale entries`(@TempDir dir: Path) {
        Baseline.override(
            listOf(
                BaselineEntry(fingerprint = fp(contrast, "/request"), ticket = "SWTST-1234"),
                BaselineEntry(fingerprint = "nichtmehrda1", ticket = "SWTST-1"),
            )
        )
        A11yCheck.evaluate(listOf(contrast, label), "/request")
        A11yCheck.evaluate(listOf(label), "/request")

        val summary = A11yReportWriter(dir).write(A11yRegistry.all(), Baseline.entries())

        assertEquals(2, summary.totalFindings)
        assertEquals(1, summary.newFindings)
        assertEquals(A11yStatus.NEW, summary.findings.first().status)
        assertEquals(1, summary.staleBaselineEntries.size)
        assertTrue(Files.exists(dir.resolve(A11yReportWriter.SUMMARY_FILE)))

        val candidates = Json.mapper.readValue<List<BaselineEntry>>(
            dir.resolve(A11yReportWriter.CANDIDATES_FILE).toFile()
        )
        assertEquals(listOf(fp(label, "/request")), candidates.map { it.fingerprint })
    }

    @Test
    fun `baseline loads from file`(@TempDir dir: Path) {
        val file = dir.resolve("baseline.json")
        Files.writeString(file, """[{"fingerprint":"abc","ticket":"T-1","unbekanntesFeld":1}]""")

        val entries = Baseline.load(file.toString())

        assertEquals("T-1", entries.getValue("abc").ticket)
    }
}
