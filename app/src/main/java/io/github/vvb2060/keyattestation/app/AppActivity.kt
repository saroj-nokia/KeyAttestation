package io.github.vvb2060.keyattestation.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import rikka.material.app.MaterialActivity

open class AppActivity : MaterialActivity() {

    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.size() > 0) {
                val locale = locales[0]!!
                val config = Configuration(newBase.resources.configuration)
                config.setLocale(locale)
                super.attachBaseContext(newBase.createConfigurationContext(config))
            } else {
                super.attachBaseContext(newBase)
            }
        } else {
            super.attachBaseContext(newBase)
        }
    }

    // System bar colors live in the theme (themes.xml, values-v29/themes.xml), not in code.
    override fun shouldApplyTranslucentSystemBars(): Boolean {
        return true
    }
}