package at.swtst.a11y

import com.deque.html.axecore.results.Rule
import com.deque.html.axecore.selenium.AxeBuilder
import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory

object A11yCheck {

    private val log = LoggerFactory.getLogger(A11yCheck::class.java)

    /**
     *
     * @param page     fachlicher Seitenname. Ohne Angabe wird der URL-Pfad verwendet.
     * @param stateKey optional: wenn gesetzt, wird page + stateKey nur einmal pro Lauf gescannt.
     * @param axe      eigener AxeBuilder, z.B. mit withTags(...) oder exclude(...)
     */
    fun scan(
        driver: WebDriver,
        page: String? = null,
        stateKey: String? = null,
        axe: AxeBuilder = AxeBuilder(),
    ): A11yScanResult {
        val pageName = page ?: Fingerprint.normalizePath(driver.currentUrl.orEmpty())

        if (stateKey != null && !A11yRegistry.markScanned("$pageName#$stateKey")) {
            log.debug("A11Y-SKIP {} ({}) already checked in this run", pageName, stateKey)
            return A11yScanResult(emptyList())
        }

        val results = axe.analyze(driver)
        return evaluate(fromAxe(results.violations.orEmpty()), pageName)
    }

    fun evaluate(nodes: List<RawNode>, page: String): A11yScanResult {
        val scenario = CurrentScenario.name()
        val baseline = Baseline.entries()

        val classified = nodes.map { raw ->
            val selector = Fingerprint.normalizeSelector(raw.target)
            val key = Fingerprint.key(raw.rule, page, selector)
            val fingerprint = Fingerprint.hash(key)
            val finding = A11yFinding(
                fingerprint = fingerprint,
                key = key,
                rule = raw.rule,
                impact = raw.impact,
                help = raw.help,
                helpUrl = raw.helpUrl,
                page = page,
                selector = selector,
                html = raw.html,
            )
            val known = baseline[fingerprint]
            val status =
                if (known != null) A11yStatus.KNOWN else A11yStatus.NEW
            val first = A11yRegistry.record(finding, status, scenario)
            ClassifiedFinding(finding, status, first, known?.ticket)
        }.distinctBy { it.finding.fingerprint }

        val result = A11yScanResult(classified)
        logResult(result)
        return result
    }

    fun fromAxe(rules: List<Rule>): List<RawNode> = rules.flatMap { rule ->
        rule.nodes.orEmpty().map { node ->
            RawNode(
                rule = rule.id ?: "unknown",
                impact = rule.impact,
                help = rule.help,
                helpUrl = rule.helpUrl,
                target = "${node.target}",
                html = node.html,
            )
        }
    }

    private fun logResult(result: A11yScanResult) {
        if (result.findings.isEmpty()) return

        result.findings.forEach { cf ->
            if (cf.status == A11yStatus.NEW && cf.firstInRun) log.warn(cf.describe())
            else log.info(cf.describe())
        }

        CurrentScenario.get()
            ?.log(result.findings.joinToString("\n") { it.describe() })
    }


    fun filterNew(violations: List<Rule>, page: String): List<Rule> {
        if (violations.isEmpty()) return emptyList()

        val result = evaluate(fromAxe(violations), page)
        val newFingerprints = result.newFindings.mapTo(HashSet()) { it.finding.fingerprint }

        return violations.mapNotNull { rule ->
            val newNodes = rule.nodes.orEmpty().filter { node ->
                val selector = Fingerprint.normalizeSelector("${node.target}")
                Fingerprint.hash(Fingerprint.key(rule.id ?: "unknown", page, selector)) in newFingerprints
            }
            if (newNodes.isEmpty()) null else rule.also { it.nodes = newNodes }
        }
    }
}
