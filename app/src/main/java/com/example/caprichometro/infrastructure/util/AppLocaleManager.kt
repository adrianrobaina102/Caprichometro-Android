package com.example.caprichometro.infrastructure.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    SPANISH("es"),
    ENGLISH("en");

    companion object {
        fun fromTag(rawTag: String?): AppLanguage {
            val normalized = rawTag?.trim()?.lowercase().orEmpty()
            return when {
                normalized.startsWith("es") -> SPANISH
                normalized.startsWith("en") -> ENGLISH
                else -> SYSTEM
            }
        }
    }
}

/**
 * Gestiona la preferencia de idioma por aplicación: usa `LocaleManager` en Android 13+
 * y respaldo con `SharedPreferences` + `Configuration` en Android 8–12.
 */
object AppLocaleManager {
    private const val PREFS_NAME = "app_locale_prefs"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun getSelectedLanguage(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return AppLanguage.fromTag(locales.get(0)?.toLanguageTag())
            }
            return AppLanguage.SYSTEM
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE_TAG, ""))
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LANGUAGE_TAG, language.tag) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = if (language.tag.isEmpty()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
            return
        }

        val targetLocale = if (language.tag.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(language.tag)
        }
        Locale.setDefault(targetLocale)
        (context as? Activity)?.recreate()
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val selected = getSelectedLanguage(base)
        if (selected == AppLanguage.SYSTEM) {
            return base
        }
        val locale = Locale.forLanguageTag(selected.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
