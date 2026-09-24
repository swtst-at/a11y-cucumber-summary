package at.swtst.a11y

import io.cucumber.plugin.ConcurrentEventListener
import io.cucumber.plugin.event.EventPublisher
import io.cucumber.plugin.event.TestRunFinished
import java.nio.file.Path

/**
 * Usage:  cucumber.plugin=io.secugrow.a11y.A11ySummaryPlugin:target/a11y-summary
 */
class Summary @JvmOverloads constructor(outDir: String = "target/a11y-summary") : ConcurrentEventListener {

    private val outPath = Path.of(outDir)
    private val writer = A11yReportWriter(outPath)

    override fun setEventPublisher(publisher: EventPublisher) {
        publisher.registerHandlerFor(TestRunFinished::class.java) { onRunFinished() }
    }

    private fun onRunFinished() {
        try {
            val s = writer.write(
                A11yRegistry.all(),
                Baseline.entries()
            )
            println(
                "A11y-Summary: ${s.totalFindings} unique findings (${s.newFindings} new, ${s.knownFindings} already known), " +
                        "${s.staleBaselineEntries.size} Baseline-entries -> ${outPath.toAbsolutePath()}"
            )
        } catch (e: Exception) {
            System.err.println("Error while writing A11y-Summary: $e")
        }
    }
}
