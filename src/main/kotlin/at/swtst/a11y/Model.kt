package at.swtst.a11y

/** NEW steht vor KNOWN, damit neue Findings im Report oben landen. */
enum class A11yStatus { NEW, KNOWN }

/** Ein Node aus dem axe-Ergebnis, unabhängig von der axe-API. */
data class RawNode(
    val rule: String,
    val impact: String?,
    val help: String?,
    val helpUrl: String?,
    val target: String,
    val html: String?,
)

data class A11yFinding(
    val fingerprint: String,
    val key: String,
    val rule: String,
    val impact: String?,
    val help: String?,
    val helpUrl: String?,
    val page: String,
    val selector: String,
    val html: String?,
)

data class ClassifiedFinding(
    val finding: A11yFinding,
    val status: A11yStatus,
    val firstInRun: Boolean,
    val ticket: String? = null,
) {
    fun describe(): String = buildString {
        append("A11Y-").append(status)
        append(" [").append(finding.fingerprint).append("] ")
        append(finding.rule).append(" (").append(finding.impact ?: "-").append(")")
        append(" on ").append(finding.page).append(": ").append(finding.selector)
        if (!ticket.isNullOrBlank()) append(" -> ").append(ticket)
        if (status == A11yStatus.NEW && !firstInRun) append(" (already reported)")
    }
}

class A11yScanResult(val findings: List<ClassifiedFinding>) {
    val newFindings: List<ClassifiedFinding> get() = findings.filter { it.status == A11yStatus.NEW }
    val knownFindings: List<ClassifiedFinding> get() = findings.filter { it.status == A11yStatus.KNOWN }
    val newFirstInRun: List<ClassifiedFinding> get() = newFindings.filter { it.firstInRun }
    fun hasNew(): Boolean = newFindings.isNotEmpty()
}
