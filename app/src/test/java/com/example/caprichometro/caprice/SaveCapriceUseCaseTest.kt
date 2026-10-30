package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SaveCapriceUseCaseTest {

    private val capriceRepository = mockk<CapriceRepository>()
    private val saveCapriceUseCase = SaveCapriceUseCase(capriceRepository)
    private val getCapricesUseCase = GetCapricesUseCase(capriceRepository)

    @Test
    fun `debe guardar un capricho correctamente y devolver el id`() = runTest {
        val caprice = Caprice.Builder()
            .setUserId("user_123")
            .setType("Compra")
            .setQuestion("¿Me pillo este teclado mecánico?")
            .setOptions("Sí, de una", "Mejor ahorra")
            .build()

        val expectedId = "firebase_doc_id_123"
        coEvery { capriceRepository.save(caprice) } returns Result.success(expectedId)

        val result = saveCapriceUseCase.execute(caprice)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(expectedId)
        coVerify(exactly = 1) { capriceRepository.save(caprice) }
    }

    @Test
    fun `GetCapricesUseCase devuelve la lista de caprichos del usuario con el limite solicitado`() = runTest {
        val caprice = Caprice.Builder()
            .setUserId("user_123")
            .setType("Plan")
            .setQuestion("¿Vamos al cine hoy?")
            .setOptions("Sí", "No")
            .build()

        coEvery { capriceRepository.getCaprices("user_123", 1) } returns Result.success(listOf(caprice))

        val result = getCapricesUseCase.execute("user_123", limit = 1)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).hasSize(1)
        assertThat(result.getOrNull()?.first()?.question).isEqualTo("¿Vamos al cine hoy?")
        coVerify(exactly = 1) { capriceRepository.getCaprices("user_123", 1) }
    }
}
