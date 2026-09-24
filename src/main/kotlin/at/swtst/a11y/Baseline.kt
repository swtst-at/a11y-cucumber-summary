package at.swtst.a11y

import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

data class BaselineEntry(
    val fingerprint: String,
    val key: String? = null,
    val rule: String? = null,
    val page: String? = null,
    val ticket: String? = null,
    val reason: String? = null,
)

object Baseline {

    private val log = LoggerFactory.getLogger(Baseline::class.java)

    @Volatile
    private var cache: Map<String, BaselineEntry>? = null

    fun entries(): Map<String, BaselineEntry> =
        cache ?: synchronized(this) {
            cache ?: load(A11yConfig.baselineLocation).also { cache = it }
        }

    fun contains(fingerprint: String): Boolean = fingerprint in entries()

    fun load(location: String): Map<String, BaselineEntry> {
        val stream = openStream(location)
        if (stream == null) {
            log.info("No A11y-Baseline found '{}', all findings handled like new", location)
            return emptyMap()
        }

        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        if (text.isBlank()) {
            log.info("A11y-Baseline '{}' is empty, all findings handled like new", location)
            return emptyMap()
        }

        val list = Json.mapper.readValue<List<BaselineEntry>>(text)
        log.info("A11y-Baseline geladen: {} Einträge aus '{}'", list.size, location)
        return list.associateBy { it.fingerprint }
    }

    /** Für Tests: Baseline direkt setzen, null lädt beim nächsten Zugriff neu. */
    internal fun override(entries: Collection<BaselineEntry>?) {
        cache = entries?.associateBy { it.fingerprint }
    }

    private fun openStream(location: String): InputStream? {
        val file = runCatching { Path.of(location) }.getOrNull()
        if (file != null && Files.isRegularFile(file)) return Files.newInputStream(file)

        val classLoader = Thread.currentThread().contextClassLoader ?: Baseline::class.java.classLoader
        return classLoader.getResourceAsStream(location.removePrefix("classpath:").removePrefix("/"))
    }
}
