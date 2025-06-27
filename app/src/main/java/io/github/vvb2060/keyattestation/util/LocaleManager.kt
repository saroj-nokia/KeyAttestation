package io.github.vvb2060.keyattestation.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleManager {

    fun getAvailableLocales(): List<Locale> {
        return listOf(
            Locale("en"),
            Locale("el"),
            Locale("pt", "BR"),
            Locale("uk", "UA"),
            Locale("zh", "CN"),
            Locale("zh", "TW")
        )
    }

    fun getLocaleCodes(): List<String> {
        return getAvailableLocales().map { locale ->
            if (locale.country.isNullOrEmpty()) {
                locale.language
            } else {
                "${locale.language}-${locale.country}"
            }
        }
    }

    fun getLocaleDisplayNames(context: Context): List<String> {
        val currentLocale = AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()
        return getAvailableLocales().map { locale ->
            locale.getDisplayName(currentLocale).replaceFirstChar { it.uppercaseChar() }
        }
    }

    fun setLocale(languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
    }

    fun saveLocale(context: Context, languageCode: String) {
        context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("app_locale", languageCode)
            .apply()
    }

    fun loadLocale(context: Context) {
        val langCode = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .getString("app_locale", null)
        langCode?.let { setLocale(it) }
    }

    fun showLanguagePickerDialog(context: Context, onLanguageSelected: () -> Unit) {
        val languages = getLocaleDisplayNames(context)
        val languageCodes = getLocaleCodes()

        AlertDialog.Builder(context)
            .setItems(languages.toTypedArray()) { dialog, which ->
                setLocale(languageCodes[which])
                saveLocale(context, languageCodes[which])
                onLanguageSelected()
                dialog.dismiss()
            }
            .show()
    }
}
