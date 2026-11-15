package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.GetNearbyCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.DeviceLocationException
import com.example.caprichometro.domain.model.ProfileCacheControl
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.infrastructure.ui.CapriceViewModel
import com.example.caprichometro.infrastructure.ui.HomeFeedTab
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapriceViewModelNearbyTest {

    private val testDispatcher = StandardTestDispatcher()
    private val saveCapriceUseCase: SaveCapriceUseCase = mockk()
    private val getCapricesUseCase: GetCapricesUseCase = mockk()
    private val profileCacheControl: ProfileCacheControl = mockk()
    private val getNearbyCapricesUseCase: GetNearbyCapricesUseCase = mockk()
    private val locationRepository: DeviceLocationRepository = mockk()

    private var currentClock = 1_000_000L
    private var currentUid = "user_1"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        currentClock = 1_000_000L
        currentUid = "user_1"
        every { profileCacheControl.invalidateAll() } just runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CapriceViewModel {
        return CapriceViewModel(
            saveCapriceUseCase = saveCapriceUseCase,
            getCapricesUseCase = getCapricesUseCase,
            profileCacheControl = profileCacheControl,
            getNearbyCapricesUseCase = getNearbyCapricesUseCase,
            locationRepository = locationRepository,
            clockMillis = { currentClock },
            currentUserIdProvider = { currentUid },
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `loadNearbyFeedIfNeeded reutiliza resultados durante 60s y recarga al expirar o refrescar manualmente`() = runTest(testDispatcher) {
        val sampleItem = CapriceFeedItem(
            caprice = Caprice(id = "c1", userId = "other", question = "¿Compro?", answer1 = "Sí", answer2 = "No", nearbyCell = "v1_2608_3525"),
            author = UserProfile(uid = "other", username = "marta"),
            canOpenAuthorProfile = false,
            approximateDistanceMeters = 1_200.0
        )
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { getNearbyCapricesUseCase.execute("user_1") } returns Result.success(listOf(sampleItem))

        val vm = createViewModel()
        vm.selectHomeTab(HomeFeedTab.FOR_YOU)
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()

        assertThat(vm.state.value.nearbyFeedItems).hasSize(1)
        assertThat(vm.state.value.hasLoadedNearbyFeed).isTrue()
        coVerify(exactly = 1) { getNearbyCapricesUseCase.execute("user_1") }

        // A los 30s conserva caché sin consultar de nuevo
        currentClock += 30_000L
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()
        coVerify(exactly = 1) { getNearbyCapricesUseCase.execute("user_1") }

        // Al pasar 60s desde la última carga vuelve a consultar
        currentClock += 31_000L
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()
        coVerify(exactly = 2) { getNearbyCapricesUseCase.execute("user_1") }

        // Refresco manual invalida perfiles y recarga incluso dentro de la ventana
        vm.loadNearbyFeed(isManualRefresh = true)
        advanceUntilIdle()
        verify(exactly = 1) { profileCacheControl.invalidateAll() }
        coVerify(exactly = 3) { getNearbyCapricesUseCase.execute("user_1") }
    }

    @Test
    fun `loadNearbyFeedIfNeeded marca permiso denegado y cambia limpiamente entre cuentas`() = runTest(testDispatcher) {
        every { locationRepository.hasLocationPermission() } returns false
        val vm = createViewModel()

        vm.loadNearbyFeedIfNeeded()
        assertThat(vm.state.value.nearbyNeedsLocationPermission).isTrue()
        coVerify(exactly = 0) { getNearbyCapricesUseCase.execute(any()) }

        // Al cambiar a user_2 con permiso concedido, no filtra caché anterior
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { getNearbyCapricesUseCase.execute("user_2") } returns Result.success(emptyList())
        currentUid = "user_2"
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()

        assertThat(vm.state.value.nearbyNeedsLocationPermission).isFalse()
        assertThat(vm.state.value.hasLoadedNearbyFeed).isTrue()
        coVerify(exactly = 1) { getNearbyCapricesUseCase.execute("user_2") }
    }

    @Test
    fun `setNearbyDiscoveryEnabled prepara nearbyCell y saveCaprice la persiste y reinicia estado`() = runTest(testDispatcher) {
        coEvery { locationRepository.currentApproximateLocation() } returns Result.success(
            ApproximateLocation(latitude = 40.4168, longitude = -3.7038)
        )
        coEvery { saveCapriceUseCase.execute(any()) } returns Result.success("doc_public_1")

        val vm = createViewModel()
        vm.setNearbyDiscoveryEnabled(true)
        advanceUntilIdle()

        assertThat(vm.state.value.isNearbyDiscoveryEnabled).isTrue()
        assertThat(vm.state.value.preparedNearbyCell).isEqualTo("v1_2608_3525")

        val saved = vm.saveCaprice(
            type = "Compra",
            questionText = "¿Lo pillo hoy?",
            answer1 = "Sí",
            answer2 = "No",
            nearbyCell = vm.state.value.preparedNearbyCell
        ).getOrThrow()

        assertThat(saved.nearbyCell).isEqualTo("v1_2608_3525")
        assertThat(vm.state.value.isNearbyDiscoveryEnabled).isFalse()
        assertThat(vm.state.value.preparedNearbyCell).isNull()
    }

    @Test
    fun `setNearbyDiscoveryEnabled revierte interruptor si falla ubicacion`() = runTest(testDispatcher) {
        coEvery { locationRepository.currentApproximateLocation() } returns Result.failure(
            DeviceLocationException.LocationServicesDisabled()
        )

        val vm = createViewModel()
        vm.setNearbyDiscoveryEnabled(true)
        advanceUntilIdle()

        assertThat(vm.state.value.isNearbyDiscoveryEnabled).isFalse()
        assertThat(vm.state.value.preparedNearbyCell).isNull()
        assertThat(vm.state.value.nearbyCreationError).contains("ubicación")
    }
}
