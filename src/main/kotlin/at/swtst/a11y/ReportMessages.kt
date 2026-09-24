package at.swtst.a11y

import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

class ReportMessages(val locale: Locale) {

    private val bundle: ResourceBundle = ResourceBundle.getBundle(
        BUNDLE,
        locale,
        ReportMessages::class.java.classLoader,
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
    )

    operator fun get(key: String, vararg args: Any): String {
        val pattern = try {
            bundle.getString(key)
        } catch (e: MissingResourceException) {
            return "??$key??"
        }
        return MessageFormat(pattern, locale).format(args)
    }

    companion object {
        const val BUNDLE = "at.swtst.a11y.i18n.messages"
    }
}