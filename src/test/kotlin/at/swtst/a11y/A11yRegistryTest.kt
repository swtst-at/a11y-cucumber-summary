package at.swtst.a11y

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class A11yRegistryTest {

    @BeforeEach
    fun reset() = A11yRegistry.reset()

    @Test
    fun `exactly one of 12 parallel threads wins the first occurrence`() {
        val finding = A11yFinding("abc123", "rule|/p|#x", "rule", null, null, null, "/p", "#x", null)
        val pool = Executors.newFixedThreadPool(12)
        val start = CountDownLatch(1)

        val futures = (1..500).map { i ->
            pool.submit(Callable {
                start.await()
                A11yRegistry.record(finding, A11yStatus.NEW, "scenario-$i")
            })
        }
        start.countDown()
        val results = futures.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, results.count { it })
        assertEquals(1, A11yRegistry.all().size)
        assertEquals(500, A11yRegistry.all().single().scenarios.size)
    }

    @Test
    fun `scanned state is reported only once`() {
        assertEquals(true, A11yRegistry.markScanned("/start#initial"))
        assertEquals(false, A11yRegistry.markScanned("/start#initial"))
        assertEquals(true, A11yRegistry.markScanned("/start#dialog"))
    }
}
