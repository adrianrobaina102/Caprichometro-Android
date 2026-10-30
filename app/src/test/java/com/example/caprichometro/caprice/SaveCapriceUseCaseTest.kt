package com.example.caprichometro.caprice

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
}
