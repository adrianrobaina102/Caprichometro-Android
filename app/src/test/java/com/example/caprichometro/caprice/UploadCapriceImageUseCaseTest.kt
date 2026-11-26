package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.caprice.UploadCapriceImageUseCase
import com.example.caprichometro.domain.repository.ImageStorageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UploadCapriceImageUseCaseTest {

    private val imageStorageRepository: ImageStorageRepository = mockk()
    private val useCase = UploadCapriceImageUseCase(imageStorageRepository)

    @Test
    fun `execute delega en ImageStorageRepository y devuelve la url`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        coEvery { imageStorageRepository.uploadCapriceImage(bytes, "cap-1") } returns Result.success("https://img/cap-1.webp")

        val result = useCase.execute(bytes, "cap-1")

        assertThat(result.getOrNull()).isEqualTo("https://img/cap-1.webp")
        coVerify(exactly = 1) { imageStorageRepository.uploadCapriceImage(bytes, "cap-1") }
    }
}
