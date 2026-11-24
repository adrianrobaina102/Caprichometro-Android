package com.example.caprichometro.util

import android.content.Context
import android.content.SharedPreferences
import com.example.caprichometro.infrastructure.util.AppLanguage
import com.example.caprichometro.infrastructure.util.AppLocaleManager
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppLocaleManagerTest {

    @Test
    fun `fromTag resolves Spanish, English and falls back to System`() {
        assertThat(AppLanguage.fromTag("es")).isEqualTo(AppLanguage.SPANISH)
        assertThat(AppLanguage.fromTag("es-ES")).isEqualTo(AppLanguage.SPANISH)
        assertThat(AppLanguage.fromTag("EN-us")).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLanguage.fromTag("en")).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLanguage.fromTag("")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTag(null)).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTag("fr")).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun `getSelectedLanguage reads from SharedPreferences on pre-Tiramisu`() {
        val prefs = mockk<SharedPreferences>()
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_locale_prefs", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getString("language_tag", "") } returns "en"

        assertThat(AppLocaleManager.getSelectedLanguage(context)).isEqualTo(AppLanguage.ENGLISH)
    }
}
