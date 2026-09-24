package at.swtst.a11y

import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object A11yConfig {

    private const val UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    val pathRules: MutableList<Pair<Regex, String>> = CopyOnWriteArrayList(
        listOf(
            Regex("/$UUID(?=/|$)") to "/{uuid}",
            Regex("/\\d+(?=/|$)") to "/{id}",
        )
    )

    val selectorRules: MutableList<Pair<Regex, String>> = CopyOnWriteArrayList(
        listOf(
            Regex("j_idt\\d+") to "j_idt{n}",
            Regex(UUID) to "{uuid}",
            Regex("\\d{3,}") to "{n}",
        )
    )

    @Volatile
    var hashLength: Int = 12

    @Volatile
    var baselineLocation: String = System.getProperty("a11y.baseline", "a11y-baseline.json")

    @Volatile
    var reportLocale: Locale = Locale.forLanguageTag(System.getProperty("a11y.report.lang", "en"))
}
