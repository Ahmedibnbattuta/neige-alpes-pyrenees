package fr.neige.stations

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Langue de l'application, indépendante de celle du téléphone.
 * Anglais par défaut ; français, italien et allemand au choix dans le menu.
 */
object LocaleHelper {
    /** Code de langue -> nom affiché dans sa propre langue. */
    val SUPPORTED = linkedMapOf("en" to "English", "fr" to "Français", "it" to "Italiano", "de" to "Deutsch")
    const val DEFAULT = "en"
    private const val KEY = "language"

    fun current(context: Context): String =
        context.getSharedPreferences("neige", Context.MODE_PRIVATE).getString(KEY, null)
            ?.takeIf { it in SUPPORTED } ?: DEFAULT

    fun save(context: Context, code: String) {
        if (code !in SUPPORTED) return
        context.getSharedPreferences("neige", Context.MODE_PRIVATE).edit().putString(KEY, code).commit()
    }

    /** Contexte dont les textes et les dates sont dans la langue choisie. */
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(current(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
