package io.github.vvb2060.keyattestation.util

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.github.vvb2060.keyattestation.R
import java.util.Locale

object LocaleManager {

    private val supportedLocales = listOf(
        "el",    // Greek
        "en",    // English
        "fr",    // French
        "it",    // Italian
        "iw",    // Hebrew (legacy code, matches the values-iw resource folder)
        "pt-BR", // Portuguese (Brazil)
        "uk-UA", // Ukrainian
        "zh-CN", // Chinese (Simplified)
        "zh-TW"  // Chinese (Traditional)
    )

    fun getLocaleCodes() = listOf("") + supportedLocales

    fun getLocaleDisplayNames(context: Context): List<String> {
        val sys = Resources.getSystem().configuration.locales[0]
        val app = AppCompatDelegate.getApplicationLocales()[0] ?: sys
        return listOf(sys.getDisplayName(sys)) +
            supportedLocales.map { Locale.forLanguageTag(it).getDisplayName(app) }
                .map { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }

    fun updateLocale(context: Context, code: String): String {
        context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_locale", code).commit()
        AppCompatDelegate.setApplicationLocales(
            if (code.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.create(Locale.forLanguageTag(code))
        )
        return code
    }

    fun loadLocale(context: Context): String {
        val code = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
            .getString("app_locale", "") ?: ""
        return updateLocale(context, code)
    }

    fun showLanguagePickerDialog(context: Context, onLanguageSelected: () -> Unit = {}) {
        val languageCodes = getLocaleCodes()
        val languages = getLocaleDisplayNames(context)
        val currentIndex = languageCodes.indexOf(loadLocale(context)).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.menu_language)
            .setSingleChoiceItems(languages.toTypedArray(), currentIndex) { dialog, which ->
                updateLocale(context, languageCodes[which])
                dialog.dismiss()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Handler(Looper.getMainLooper()).post {
                        val pm = context.packageManager
                        val intent = pm.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                } else {
                    if (context is Activity) context.recreate()
                }
            }
            .show()
    }
}
