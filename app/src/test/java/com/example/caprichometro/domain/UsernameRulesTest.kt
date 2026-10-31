package com.example.caprichometro.domain

import com.example.caprichometro.domain.model.UsernameRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsernameRulesTest {

    @Test
    fun `normalize limpia espacios, arrobas iniciales y convierte a minusculas`() {
        assertEquals("adrian_dev", UsernameRules.normalize("adrian_dev"))
        assertEquals("adrian_dev", UsernameRules.normalize("  adrian_dev  "))
        assertEquals("adrian_dev", UsernameRules.normalize("@adrian_dev"))
        assertEquals("adrian_dev", UsernameRules.normalize("Adrian_Dev"))
        assertEquals("adrian_dev", UsernameRules.normalize("  @Adrian_Dev  "))
    }

    @Test
    fun `isValid devuelve true para nombres de usuario validos de 3 a 20 caracteres`() {
        assertTrue(UsernameRules.isValid("adrian_dev"))
        assertTrue(UsernameRules.isValid("user123"))
        assertTrue(UsernameRules.isValid("abc"))
        assertTrue(UsernameRules.isValid("un_nombre_largo"))
        assertTrue(UsernameRules.isValid("un_nombre_de_20_char"))
    }

    @Test
    fun `isValid devuelve false para nombres demasiado cortos o largos`() {
        assertFalse(UsernameRules.isValid("ad"))
        assertFalse(UsernameRules.isValid("este_nombre_es_demasiado_largo"))
    }

    @Test
    fun `isValid devuelve false para caracteres especiales no permitidos`() {
        assertFalse(UsernameRules.isValid("adrian@dev"))
        assertFalse(UsernameRules.isValid("user.name"))
        assertFalse(UsernameRules.isValid("user name"))
    }
}
