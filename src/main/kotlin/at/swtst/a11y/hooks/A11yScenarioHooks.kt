package at.swtst.a11y.hooks

import at.swtst.a11y.CurrentScenario
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.Scenario

class A11yScenarioHooks {

    @Before(order = 0)
    fun rememberScenario(scenario: Scenario) {
        CurrentScenario.set(scenario)
    }

    @After(order = 0)
    fun forgetScenario() {
        CurrentScenario.clear()
    }
}
