package at.swtst.a11y

import io.cucumber.java.Scenario

object CurrentScenario {

    private val current = ThreadLocal<Scenario?>()

    fun set(scenario: Scenario) = current.set(scenario)

    fun get(): Scenario? = current.get()

    fun name(): String = current.get()?.let { "${it.uri}:${it.line} ${it.name}" } ?: "unbekannt"

    fun clear() = current.remove()
}
