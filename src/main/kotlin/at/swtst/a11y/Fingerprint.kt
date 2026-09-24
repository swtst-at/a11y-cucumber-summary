package at.swtst.a11y

import java.net.URI
import java.security.MessageDigest
import java.util.*

object Fingerprint {

    fun normalizePath(url: String): String {
        val path = runCatching { URI.create(url).path }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: url
        return applyRules(path, A11yConfig.pathRules)
    }

    fun normalizeSelector(rawTarget: String): String =
        applyRules(rawTarget.trim(), A11yConfig.selectorRules)

    fun key(rule: String, page: String, selector: String): String = "$rule|$page|$selector"

    fun hash(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest).take(A11yConfig.hashLength)
    }

    private fun applyRules(input: String, rules: List<Pair<Regex, String>>): String =
        rules.fold(input) { acc, (regex, replacement) -> regex.replace(acc) { replacement } }
}
