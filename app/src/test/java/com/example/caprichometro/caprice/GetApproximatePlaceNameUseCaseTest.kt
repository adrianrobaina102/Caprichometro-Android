package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.caprice.GetApproximatePlaceNameUseCase
import com.example.caprichometro.domain.repository.ApproximatePlaceNameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetApproximatePlaceNameUseCaseTest {

    @Test
    fun `celda vacia devuelve null sin consultar repositorio`() = runTest {
        val repo = mockk<ApproximatePlaceNameRepository>()
        val useCase = GetApproximatePlaceNameUseCase(repo)

        assertThat(useCase.execute("   ")).isNull()
        coVerify(exactly = 0) { repo.resolvePlaceName(any()) }
    }

    @Test
    fun `celda valida devuelve el nombre aproximado de ciudad o zona`() = runTest {
        val repo = mockk<ApproximatePlaceNameRepository>()
        coEvery { repo.resolvePlaceName("v1_2608_3525") } returns "Madrid"

        val useCase = GetApproximatePlaceNameUseCase(repo)
        assertThat(useCase.execute("v1_2608_3525")).isEqualTo("Madrid")
    }
}
