package com.example.caprichometro.infrastructure.ui.components

import android.content.res.Resources
import com.example.caprichometro.R
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalizedUiMessagesTest {

    @Test
    fun `traduce errores exactos y dinamicos al ingles sin modificar desconocidos`() {
        val resources: Resources = mockk()
        every { resources.getString(R.string.err_auth_invalid_email) } returns "Please enter a valid email address"
        every { resources.getString(R.string.err_vote_poll_closed) } returns "Voting is already closed (5-minute limit reached)"
        every { resources.getString(R.string.err_auth_min_age, 13) } returns "You must be at least 13 years old to sign up"
        every { resources.getString(R.string.err_field_question) } returns "The question"
        every { resources.getString(R.string.err_validation_min_length, "The question", 6) } returns "The question must be at least 6 characters long"
        every { resources.getString(R.string.err_auth_username_taken, "alfa") } returns "Username @alfa is already taken."

        assertThat(UiErrorTranslator.translate("Por favor, introduce un correo electrónico válido", resources))
            .isEqualTo("Please enter a valid email address")
        assertThat(UiErrorTranslator.translate("La votación ya ha cerrado (límite de 5 minutos alcanzado)", resources))
            .isEqualTo("Voting is already closed (5-minute limit reached)")
        assertThat(UiErrorTranslator.translate("Debes tener al menos 13 años para registrarte", resources))
            .isEqualTo("You must be at least 13 years old to sign up")
        assertThat(UiErrorTranslator.translate("La pregunta debe tener al menos 6 caracteres", resources))
            .isEqualTo("The question must be at least 6 characters long")
        assertThat(UiErrorTranslator.translate("El nombre de usuario @alfa ya está en uso.", resources))
            .isEqualTo("Username @alfa is already taken.")
        assertThat(UiErrorTranslator.translate("PERMISSION_DENIED: custom error", resources))
            .isEqualTo("PERMISSION_DENIED: custom error")
    }
}
