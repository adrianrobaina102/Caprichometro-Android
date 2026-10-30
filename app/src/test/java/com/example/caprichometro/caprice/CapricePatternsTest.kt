package com.example.caprichometro.caprice

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.validation.DifferentOptionsValidator
import com.example.caprichometro.domain.validation.MinLengthValidator
import com.example.caprichometro.domain.validation.NotBlankValidator
import com.example.caprichometro.domain.validation.ValidationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CapricePatternsTest {

    @Test
    fun `Builder debe construir un Caprice correctamente con valores asignados`() {
        val caprice = Caprice.Builder()
            .setUserId("user_abc")
            .setType("Plan")
            .setQuestion("¿Salimos hoy de fiesta?")
            .setOptions("De una", "Mejor peli")
            .build()

        assertThat(caprice.userId).isEqualTo("user_abc")
        assertThat(caprice.type).isEqualTo("Plan")
        assertThat(caprice.question).isEqualTo("¿Salimos hoy de fiesta?")
        assertThat(caprice.answer1).isEqualTo("De una")
        assertThat(caprice.answer2).isEqualTo("Mejor peli")
        assertThat(caprice.id).isNotEmpty()
        assertThat(caprice.timestamp).isGreaterThan(0L)
    }

    @Test
    fun `Builder debe lanzar excepcion si falta la pregunta o las opciones`() {
        assertThrows<IllegalStateException> {
            Caprice.Builder()
                .setQuestion("")
                .setOptions("Opción 1", "Opción 2")
                .build()
        }

        assertThrows<IllegalStateException> {
            Caprice.Builder()
                .setQuestion("¿Pregunta válida?")
                .setOptions("", "Opción 2")
                .build()
        }
    }

    @Test
    fun `Chain of Responsibility debe validar pregunta no vacia y con longitud minima`() {
        val questionChain = NotBlankValidator("La pregunta")
        questionChain.setNext(MinLengthValidator("La pregunta", minLength = 10))

        // Caso 1: Vacío -> Falla en el primer eslabón
        val emptyResult = questionChain.validate("   ")
        assertThat(emptyResult).isInstanceOf(ValidationResult.Error::class.java)
        assertThat((emptyResult as ValidationResult.Error).message).isEqualTo("La pregunta no puede estar vacío")

        // Caso 2: Menos de 10 caracteres -> Pasa el primero pero falla en el segundo
        val shortResult = questionChain.validate("¿Hola?")
        assertThat(shortResult).isInstanceOf(ValidationResult.Error::class.java)
        assertThat((shortResult as ValidationResult.Error).message).isEqualTo("La pregunta debe tener al menos 10 caracteres")

        // Caso 3: Válido -> Pasa toda la cadena con éxito
        val validResult = questionChain.validate("¿Compro las zapatillas?")
        assertThat(validResult).isEqualTo(ValidationResult.Success)
    }

    @Test
    fun `Chain of Responsibility debe rechazar opciones identicas`() {
        val validator = DifferentOptionsValidator()

        val invalid = validator.validate(Pair("Sí", "sí"))
        assertThat(invalid).isInstanceOf(ValidationResult.Error::class.java)
        assertThat((invalid as ValidationResult.Error).message).isEqualTo("Las dos opciones de respuesta deben ser diferentes")

        val valid = validator.validate(Pair("Comprar", "Ahorrar"))
        assertThat(valid).isEqualTo(ValidationResult.Success)
    }
}
