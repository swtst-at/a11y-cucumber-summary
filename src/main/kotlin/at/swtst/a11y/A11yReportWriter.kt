package at.swtst.a11y

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class SummaryEntry(
    val fingerprint: String,
    val status: A11yStatus,
    val rule: String,
    val impact: String?,
    val page: String,
    val selector: String,
    val key: String,
    val help: String?,
    val helpUrl: String?,
    val html: String?,
    val ticket: String?,
    val occurrences: Int,
    val scenarios: List<String>,
)

data class A11ySummary(
    val generatedAt: String,
    val totalFindings: Int,
    val newFindings: Int,
    val knownFindings: Int,
    val findings: List<SummaryEntry>,
    val staleBaselineEntries: List<BaselineEntry>,
)

class A11yReportWriter(private val outDir: Path) {

    fun write(
        registered: Collection<RegisteredFinding>,
        baseline: Map<String, BaselineEntry>
    ): A11ySummary {
        val entries = registered
            .sortedWith(compareBy<RegisteredFinding> { it.status }.thenByDescending { it.scenarios.size })
            .map { r ->
                val f = r.finding
                SummaryEntry(
                    fingerprint = f.fingerprint,
                    status = r.status,
                    rule = f.rule,
                    impact = f.impact,
                    page = f.page,
                    selector = f.selector,
                    key = f.key,
                    help = f.help,
                    helpUrl = f.helpUrl,
                    html = f.html,
                    ticket = baseline[f.fingerprint]?.ticket,
                    occurrences = r.scenarios.size,
                    scenarios = r.scenarios.sorted(),
                )
            }

        val seen = entries.mapTo(HashSet()) { it.fingerprint }
        val summary = A11ySummary(
            generatedAt = Instant.now().toString(),
            totalFindings = entries.size,
            newFindings = entries.count { it.status == A11yStatus.NEW },
            knownFindings = entries.count { it.status == A11yStatus.KNOWN },
            findings = entries,
            staleBaselineEntries = baseline.values.filter { it.fingerprint !in seen },
        )

        val candidates = entries
            .filter { it.status == A11yStatus.NEW }
            .map {
                BaselineEntry(
                    fingerprint = it.fingerprint,
                    key = it.key,
                    rule = it.rule,
                    page = it.page,
                    ticket = "",
                    reason = "TODO: fix or add an reason",
                )
            }

        Files.createDirectories(outDir)
        Json.mapper.writeValue(outDir.resolve(SUMMARY_FILE).toFile(), summary)
        Json.mapper.writeValue(outDir.resolve(CANDIDATES_FILE).toFile(), candidates)

        Json.mapper.writeValue(outDir.resolve(CANDIDATES_FILE).toFile(), candidates)
        Files.writeString(outDir.resolve(HTML_FILE), A11yHtmlReport.render(summary))   // neu

        return summary
    }

    companion object {
        const val SUMMARY_FILE = "a11y-summary.json"
        const val CANDIDATES_FILE = "a11y-baseline-candidates.json"
        const val HTML_FILE = "a11y-report.html"
    }
}
