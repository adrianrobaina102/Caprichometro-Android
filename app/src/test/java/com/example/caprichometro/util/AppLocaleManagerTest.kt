package com.example.caprichometro.util

import com.example.caprichometro.infrastructure.util.AppLanguage
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
}
