package dev.oni.dreaminstall

import android.content.Context
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.oni.dreaminstall.core.LANG_EN
import dev.oni.dreaminstall.core.LANG_PT
import dev.oni.dreaminstall.core.languageFor

/**
 * Per-app language. The first run follows the phone: any Portuguese locale keeps Portuguese,
 * anything else falls back to English. The choice is stored and can be changed in Settings.
 */
object Language {

    const val PT = LANG_PT
    const val EN = LANG_EN
    private const val PREFS = "dreaminstall"
    private const val KEY = "lang"

    fun stored(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun detect(): String {
        val locales = LocaleList.getDefault()
        val tags = ArrayList<String>(locales.size())
        for (index in 0 until locales.size()) tags.add(locales[index].toLanguageTag())
        return languageFor(tags)
    }

    fun current(context: Context): String = stored(context) ?: detect()

    /** Runs once, before the first activity, so the first screen already has the right language. */
    fun applyOnStart(context: Context) {
        val language = current(context)
        if (stored(context) == null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, language).apply()
        }
        apply(language)
    }

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
        apply(language)
    }

    private fun apply(language: String) {
        val wanted = LocaleListCompat.forLanguageTags(language)
        if (AppCompatDelegate.getApplicationLocales() != wanted) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }
}
