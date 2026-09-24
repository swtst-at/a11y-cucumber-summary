package at.swtst.a11y

import java.util.concurrent.ConcurrentHashMap

class RegisteredFinding(val finding: A11yFinding, val status: A11yStatus) {
    val scenarios: MutableSet<String> = ConcurrentHashMap.newKeySet()
}

object A11yRegistry {

    private val findings = ConcurrentHashMap<String, RegisteredFinding>()
    private val scannedStates: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun record(finding: A11yFinding, status: A11yStatus, scenario: String): Boolean {
        val candidate = RegisteredFinding(finding, status)
        val existing = findings.putIfAbsent(finding.fingerprint, candidate)
        (existing ?: candidate).scenarios += scenario
        return existing == null
    }

    fun markScanned(stateKey: String): Boolean = scannedStates.add(stateKey)

    fun all(): List<RegisteredFinding> = findings.values.toList()

    fun reset() {
        findings.clear()
        scannedStates.clear()
    }
}
