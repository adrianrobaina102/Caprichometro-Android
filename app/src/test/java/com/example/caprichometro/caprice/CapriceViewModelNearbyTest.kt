package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.GetApproximatePlaceNameUseCase
import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.GetFriendsCapricesUseCase
import com.example.caprichometro.application.usecase.GetNearbyCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.UploadCapriceImageUseCase
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceCacheControl
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.DeviceLocationException
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.ProfileCacheControl
import com.example.caprichometro.domain.model.ProgressiveFeedBatch
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.VoteRepository
import com.example.caprichometro.infrastructure.ui.ActivePollViewModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val getFriendsCapricesUseCase: GetFriendsCapricesUseCase = mockk()
    private val profileCacheControl: ProfileCacheControl = mockk()
    private val capriceCacheControl: CapriceCacheControl = mockk()
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
        every { capriceCacheControl.invalidateAll() } just runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        userProfileRepository: UserProfileRepository? = null
    ): CapriceViewModel {
        return CapriceViewModel(
            saveCapriceUseCase = saveCapriceUseCase,
            getCapricesUseCase = getCapricesUseCase,
            getFriendsCapricesUseCase = getFriendsCapricesUseCase,
            profileCacheControl = profileCacheControl,
            capriceCacheControl = capriceCacheControl,
            getNearbyCapricesUseCase = getNearbyCapricesUseCase,
            locationRepository = locationRepository,
            userProfileRepository = userProfileRepository,
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
        every {
            getNearbyCapricesUseCase.observeProgressiveFeed(
                currentUserId = "user_1",
                includeCache = any(),
                initialBatchSize = 5,
                pageSize = 20
            )
        } answers {
            flowOf(ProgressiveFeedBatch(listOf(sampleItem), isFromCache = false, isComplete = true))
        }

        val vm = createViewModel()
        vm.selectHomeTab(HomeFeedTab.GENERAL)
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()

        assertThat(vm.state.value.nearbyFeedItems).hasSize(1)
        assertThat(vm.state.value.hasLoadedNearbyFeed).isTrue()
        verify(exactly = 1) {
            getNearbyCapricesUseCase.observeProgressiveFeed("user_1", includeCache = true, initialBatchSize = 5, pageSize = 20)
        }

        // A los 30s conserva caché sin consultar de nuevo
        currentClock += 30_000L
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()
        verify(exactly = 1) {
            getNearbyCapricesUseCase.observeProgressiveFeed("user_1", includeCache = any(), initialBatchSize = 5, pageSize = 20)
        }

        // Al pasar 60s desde la última carga vuelve a consultar
        currentClock += 31_000L
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()
        verify(exactly = 2) {
            getNearbyCapricesUseCase.observeProgressiveFeed("user_1", includeCache = any(), initialBatchSize = 5, pageSize = 20)
        }

        // Refresco manual invalida perfiles y recarga sin caché incluso dentro de la ventana
        vm.loadNearbyFeed(isManualRefresh = true)
        advanceUntilIdle()
        verify(exactly = 1) { profileCacheControl.invalidateAll() }
        verify(exactly = 1) {
            getNearbyCapricesUseCase.observeProgressiveFeed("user_1", includeCache = false, initialBatchSize = 5, pageSize = 20)
        }
    }

    @Test
    fun `loadNearbyFeedIfNeeded marca permiso denegado y cambia limpiamente entre cuentas`() = runTest(testDispatcher) {
        every { locationRepository.hasLocationPermission() } returns false
        val vm = createViewModel()

        vm.loadNearbyFeedIfNeeded()
        assertThat(vm.state.value.nearbyNeedsLocationPermission).isTrue()
        verify(exactly = 0) { getNearbyCapricesUseCase.observeProgressiveFeed(any(), any(), any(), any()) }

        // Al cambiar a user_2 con permiso concedido, no filtra caché anterior
        every { locationRepository.hasLocationPermission() } returns true
        every {
            getNearbyCapricesUseCase.observeProgressiveFeed("user_2", includeCache = true, initialBatchSize = 5, pageSize = 20)
        } returns flowOf(ProgressiveFeedBatch(emptyList(), isFromCache = false, isComplete = true))
        currentUid = "user_2"
        vm.loadNearbyFeedIfNeeded()
        advanceUntilIdle()

        assertThat(vm.state.value.nearbyNeedsLocationPermission).isFalse()
        assertThat(vm.state.value.hasLoadedNearbyFeed).isTrue()
        verify(exactly = 1) {
            getNearbyCapricesUseCase.observeProgressiveFeed("user_2", includeCache = true, initialBatchSize = 5, pageSize = 20)
        }
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

    @Test
    fun `loadFriendsFeed evita recarga duplicada en arranque y solo invalida caches en refresco manual`() = runTest(testDispatcher) {
        every {
            getFriendsCapricesUseCase.observeProgressiveFeed(
                currentUserId = "user_1",
                initialBatchSize = 5,
                perFriendLimit = 100,
                totalLimit = 200,
                includeCache = any()
            )
        } answers {
            flowOf(ProgressiveFeedBatch(emptyList(), isFromCache = false, isComplete = true))
        }

        val vm = createViewModel()
        vm.loadFriendsFeed()
        // Síncronamente antes de ejecutar la corrutina ya debe constar isFeedLoading = true
        assertThat(vm.state.value.isFeedLoading).isTrue()

        vm.loadFriendsFeedIfNeeded()
        advanceUntilIdle()

        verify(exactly = 1) {
            getFriendsCapricesUseCase.observeProgressiveFeed("user_1", 5, 100, 200, includeCache = true)
        }
        verify(exactly = 0) { profileCacheControl.invalidateAll() }
        verify(exactly = 0) { capriceCacheControl.invalidateAll() }

        vm.loadFriendsFeed(isManualRefresh = true)
        advanceUntilIdle()

        verify(exactly = 1) {
            getFriendsCapricesUseCase.observeProgressiveFeed("user_1", 5, 100, 200, includeCache = false)
        }
        verify(exactly = 1) { profileCacheControl.invalidateAll() }
        verify(exactly = 1) { capriceCacheControl.invalidateAll() }
    }

    @Test
    fun `paginacion de Amigos y General muestra 20 caprichos iniciales y avanza de 20 en 20`() = runTest(testDispatcher) {
        val friendItems = (1..25).map { idx ->
            CapriceFeedItem(
                caprice = Caprice(
                    id = "f_$idx",
                    userId = "friend_1",
                    question = "¿Duda amigo $idx?",
                    answer1 = "Sí",
                    answer2 = "No",
                    timestamp = 1_000_000L - idx * 1_000L
                ),
                author = UserProfile(uid = "friend_1", username = "ana")
            )
        }
        val nearbyItems = (1..23).map { idx ->
            CapriceFeedItem(
                caprice = Caprice(
                    id = "g_$idx",
                    userId = "stranger_$idx",
                    question = "¿Duda general $idx?",
                    answer1 = "Sí",
                    answer2 = "No",
                    nearbyCell = "v1_2608_3525"
                ),
                author = UserProfile(uid = "stranger_$idx", username = "user$idx"),
                canOpenAuthorProfile = false,
                approximateDistanceMeters = idx * 100.0
            )
        }

        every {
            getFriendsCapricesUseCase.observeProgressiveFeed("user_1", 5, 100, 200, any())
        } returns flowOf(ProgressiveFeedBatch(friendItems, isFromCache = false, isComplete = true))
        every { locationRepository.hasLocationPermission() } returns true
        every {
            getNearbyCapricesUseCase.observeProgressiveFeed("user_1", any(), 5, 20)
        } returns flowOf(ProgressiveFeedBatch(nearbyItems, isFromCache = false, isComplete = true))

        val vm = createViewModel()
        vm.loadFriendsFeed()
        vm.loadNearbyFeed()
        advanceUntilIdle()

        assertThat(vm.state.value.feedItems).hasSize(20)
        assertThat(vm.state.value.totalFriendsFeedCount).isEqualTo(25)
        assertThat(vm.state.value.hasMoreFriendsFeed).isTrue()

        vm.loadMoreFriendsFeed()
        assertThat(vm.state.value.feedItems).hasSize(25)
        assertThat(vm.state.value.hasMoreFriendsFeed).isFalse()

        assertThat(vm.state.value.nearbyFeedItems).hasSize(20)
        assertThat(vm.state.value.totalNearbyFeedCount).isEqualTo(23)
        assertThat(vm.state.value.hasMoreNearbyFeed).isTrue()

        vm.loadMoreNearbyFeed()
        assertThat(vm.state.value.nearbyFeedItems).hasSize(23)
        assertThat(vm.state.value.hasMoreNearbyFeed).isFalse()

        // Un refresco manual vuelve a mostrar la primera página de 20
        vm.loadFriendsFeed(isManualRefresh = true)
        advanceUntilIdle()
        assertThat(vm.state.value.feedItems).hasSize(20)
        assertThat(vm.state.value.hasMoreFriendsFeed).isTrue()
    }

    @Test
    fun `carga progresiva desbloquea isFeedLoading en el primer lote de 5 y sincroniza el resto en segundo plano`() = runTest(testDispatcher) {
        val allItems = (1..8).map { idx ->
            CapriceFeedItem(
                caprice = Caprice(
                    id = "f_$idx",
                    userId = "friend_1",
                    question = "¿Pregunta $idx?",
                    answer1 = "Sí",
                    answer2 = "No",
                    timestamp = 1_000_000L - idx * 1_000L
                ),
                author = UserProfile(uid = "friend_1", username = "ana")
            )
        }

        every {
            getFriendsCapricesUseCase.observeProgressiveFeed("user_1", 5, 100, 200, true)
        } returns flow {
            emit(ProgressiveFeedBatch(allItems.take(5), isFromCache = false, isComplete = false))
            delay(500L)
            emit(ProgressiveFeedBatch(allItems, isFromCache = false, isComplete = true))
        }

        val vm = createViewModel()
        vm.loadFriendsFeed()
        testScheduler.advanceTimeBy(100L)
        testScheduler.runCurrent()

        assertThat(vm.state.value.hasLoadedFeed).isTrue()
        assertThat(vm.state.value.isFeedLoading).isFalse()
        assertThat(vm.state.value.isFriendsBackgroundSyncing).isTrue()
        assertThat(vm.state.value.feedItems).hasSize(5)

        advanceUntilIdle()

        assertThat(vm.state.value.isFriendsBackgroundSyncing).isFalse()
        assertThat(vm.state.value.feedItems).hasSize(8)
        assertThat(vm.state.value.totalFriendsFeedCount).isEqualTo(8)
    }

    @Test
    fun `preparePrivateLocationCellIfPermitted captura locationCell sin activar General y saveCaprice la guarda`() = runTest(testDispatcher) {
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.currentApproximateLocation() } returns Result.success(
            ApproximateLocation(latitude = 40.4168, longitude = -3.7038)
        )
        coEvery { saveCapriceUseCase.execute(any()) } returns Result.success("doc_priv_loc_1")

        val vm = createViewModel()
        vm.preparePrivateLocationCellIfPermitted()
        advanceUntilIdle()

        assertThat(vm.state.value.isNearbyDiscoveryEnabled).isFalse()
        assertThat(vm.state.value.preparedNearbyCell).isNull()
        assertThat(vm.state.value.preparedLocationCell).isEqualTo("v1_2608_3525")

        val saved = vm.saveCaprice(
            type = "Plan",
            questionText = "¿Café?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = listOf("friend_1"),
            nearbyCell = null,
            locationCell = vm.state.value.preparedLocationCell
        ).getOrThrow()

        assertThat(saved.nearbyCell).isNull()
        assertThat(saved.locationCell).isEqualTo("v1_2608_3525")
        assertThat(saved.distanceCell()).isEqualTo("v1_2608_3525")
        assertThat(saved.isVisibleTo("friend_1")).isTrue()
        assertThat(saved.isVisibleTo("stranger_99")).isFalse()
    }

    @Test
    fun `opcion A revela pagina 1 de 5 en 5 hasta 20 e hidrata pagina 2 bajo demanda de 5 en 5`() = runTest(testDispatcher) {
        val allItems = (1..35).map { idx ->
            CapriceFeedItem(
                caprice = Caprice(
                    id = "opt_$idx",
                    userId = "friend_1",
                    question = "¿Pregunta $idx?",
                    answer1 = "Sí",
                    answer2 = "No",
                    timestamp = 1_000_000L - idx * 1_000L
                ),
                author = UserProfile(uid = "friend_1", username = "ana")
            )
        }

        every {
            getFriendsCapricesUseCase.observeProgressiveFeed("user_1", 5, 100, 200, true)
        } returns flowOf(
            ProgressiveFeedBatch(
                items = allItems.take(20),
                isFromCache = false,
                isComplete = true,
                totalAvailableCount = 35
            )
        )
        every {
            getFriendsCapricesUseCase.observeNextPage("user_1", 20, 5, 20)
        } returns flowOf(
            ProgressiveFeedBatch(
                items = allItems.take(25),
                isFromCache = false,
                isComplete = false,
                totalAvailableCount = 35
            ),
            ProgressiveFeedBatch(
                items = allItems,
                isFromCache = false,
                isComplete = true,
                totalAvailableCount = 35
            )
        )

        val vm = createViewModel()
        vm.loadFriendsFeed()
        testScheduler.runCurrent()

        // Primeros 5 visibles de inmediato
        assertThat(vm.state.value.feedItems).hasSize(5)
        assertThat(vm.state.value.isFeedLoading).isFalse()
        assertThat(vm.state.value.isFriendsBackgroundSyncing).isTrue()

        // Resto de página 1 avanza de 5 en 5 (10 -> 15 -> 20)
        testScheduler.advanceTimeBy(60L)
        testScheduler.runCurrent()
        assertThat(vm.state.value.feedItems).hasSize(10)

        testScheduler.advanceTimeBy(60L)
        testScheduler.runCurrent()
        assertThat(vm.state.value.feedItems).hasSize(15)

        testScheduler.advanceTimeBy(60L)
        testScheduler.runCurrent()
        assertThat(vm.state.value.feedItems).hasSize(20)
        assertThat(vm.state.value.totalFriendsFeedCount).isEqualTo(35)
        assertThat(vm.state.value.hasMoreFriendsFeed).isTrue()
        assertThat(vm.state.value.isFriendsBackgroundSyncing).isFalse()

        // Página 2 bajo demanda llama observeNextPage, revela primeros 5 (25) de inmediato y luego 30 -> 35
        vm.loadMoreFriendsFeed()
        testScheduler.runCurrent()
        assertThat(vm.state.value.feedItems).hasSize(25)
        verify(exactly = 1) { getFriendsCapricesUseCase.observeNextPage("user_1", 20, 5, 20) }

        testScheduler.advanceTimeBy(60L)
        testScheduler.runCurrent()
        assertThat(vm.state.value.feedItems).hasSize(30)

        testScheduler.advanceTimeBy(60L)
        testScheduler.runCurrent()
        assertThat(vm.state.value.feedItems).hasSize(35)
        assertThat(vm.state.value.hasMoreFriendsFeed).isFalse()
        assertThat(vm.state.value.isFriendsBackgroundSyncing).isFalse()
    }

    @Test
    fun `onLocationPermissionChanged true recalcula distancias en Amigos tras haber cargado sin permiso`() = runTest(testDispatcher) {
        val rawItem = CapriceFeedItem(
            caprice = Caprice(
                id = "c_loc",
                userId = "friend_1",
                question = "¿Con ubicación?",
                answer1 = "Sí",
                answer2 = "No",
                locationCell = "v1_2608_3525"
            ),
            author = UserProfile(uid = "friend_1", username = "ana"),
            approximateDistanceMeters = null
        )
        val itemWithDistance = rawItem.copy(approximateDistanceMeters = 1_500.0)

        every {
            getFriendsCapricesUseCase.observeProgressiveFeed("user_1", 5, 100, 200, true)
        } returns flowOf(ProgressiveFeedBatch(listOf(rawItem), isFromCache = false, isComplete = true))
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.currentApproximateLocation() } returns Result.success(
            ApproximateLocation(latitude = 40.4168, longitude = -3.7038)
        )
        coEvery {
            getFriendsCapricesUseCase.refreshCachedDistances("user_1", 20)
        } returns ProgressiveFeedBatch(listOf(itemWithDistance), isFromCache = false, isComplete = true)

        val vm = createViewModel()
        vm.loadFriendsFeed()
        advanceUntilIdle()
        assertThat(vm.state.value.feedItems.single().approximateDistanceMeters).isNull()

        vm.onLocationPermissionChanged(true)
        advanceUntilIdle()

        assertThat(vm.state.value.isLocationPermissionGranted).isTrue()
        assertThat(vm.state.value.feedItems.single().approximateDistanceMeters).isEqualTo(1_500.0)
        coVerify(exactly = 1) { getFriendsCapricesUseCase.refreshCachedDistances("user_1", 20) }
    }

    @Test
    fun `saveCaprice adjunta locationCell automaticamente si ya hay permiso aunque locationCell sea null`() = runTest(testDispatcher) {
        every { locationRepository.hasLocationPermission() } returns true
        coEvery { locationRepository.currentApproximateLocation() } returns Result.success(
            ApproximateLocation(latitude = 40.4168, longitude = -3.7038)
        )
        coEvery { saveCapriceUseCase.execute(any()) } returns Result.success("doc_auto_loc")

        val vm = createViewModel()
        vm.onLocationPermissionChanged(true)

        val saved = vm.saveCaprice(
            type = "Plan",
            questionText = "¿Vamos al cine?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = listOf("friend_1"),
            nearbyCell = null,
            locationCell = null
        ).getOrThrow()

        assertThat(saved.locationCell).isEqualTo("v1_2608_3525")
        assertThat(saved.nearbyCell).isNull()
        assertThat(saved.isVisibleTo("stranger_99")).isFalse()
    }

    @Test
    fun `ActivePollViewModel resuelve zona aproximada usando distanceCell y conserva distancia en Amigos`() = runTest(testDispatcher) {
        val castVote = mockk<CastVoteUseCase>()
        val observeVotes = mockk<ObserveVotesUseCase>()
        val calculateVerdict = CalculateVerdictUseCase()
        val voteRepo = mockk<VoteRepository>()
        val placeNameUseCase = mockk<GetApproximatePlaceNameUseCase>()

        coEvery { voteRepo.getUserVote("priv_1", "user_1") } returns Result.success(null)
        every { observeVotes.execute("priv_1") } returns flowOf(emptyList())
        coEvery { placeNameUseCase.execute("v1_2608_3525") } returns "Madrid"

        val activePollVm = ActivePollViewModel(
            castVoteUseCase = castVote,
            observeVotesUseCase = observeVotes,
            calculateVerdictUseCase = calculateVerdict,
            voteRepository = voteRepo,
            getApproximatePlaceNameUseCase = placeNameUseCase
        )
        val friendCaprice = Caprice(
            id = "priv_1",
            userId = "friend_1",
            question = "¿En el centro?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = System.currentTimeMillis(),
            nearbyCell = null,
            locationCell = "v1_2608_3525"
        )

        activePollVm.initialize(
            caprice = friendCaprice,
            currentUserId = "user_1",
            authorUsername = "ana",
            approximateDistanceMeters = 90_000.0
        )
        testScheduler.runCurrent()

        assertThat(activePollVm.state.value.approximateDistanceMeters).isEqualTo(90_000.0)
        assertThat(activePollVm.state.value.approximatePlaceName).isEqualTo("Madrid")
        assertThat(activePollVm.state.value.isResolvingPlaceName).isFalse()
        activePollVm.reset()
        testScheduler.runCurrent()
    }

    @Test
    fun `loadFriendsFeed recalcula distancias si se concede permiso en mitad de la carga y sincroniza locationCell del perfil`() = runTest(testDispatcher) {
        val madrid = ApproximateLocation(40.4168, -3.7038)
        val userProfileRepo = mockk<UserProfileRepository>()
        val authorWithCell = UserProfile(uid = "friend_1", username = "ana", locationCell = "v1_2608_3525")
        val legacyCaprice = Caprice(
            id = "f_race",
            userId = "friend_1",
            question = "¿Con distancia tras permiso?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 1_000L,
            locationCell = null,
            nearbyCell = null
        )
        val rawItem = CapriceFeedItem(
            caprice = legacyCaprice,
            author = authorWithCell,
            canOpenAuthorProfile = true,
            approximateDistanceMeters = null
        )
        val itemWithDistance = rawItem.copy(approximateDistanceMeters = 1_500.0)

        var permissionGranted = false
        var snapshotReady = false
        every { locationRepository.hasLocationPermission() } answers { permissionGranted }
        coEvery { locationRepository.currentApproximateLocation() } returns Result.success(madrid)
        coEvery { userProfileRepo.getCachedUserById("user_1") } returns null
        coEvery { userProfileRepo.updateLocationCell("user_1", "v1_2608_3525") } returns Result.success(Unit)
        every {
            getFriendsCapricesUseCase.observeProgressiveFeed("user_1", 5, 100, 200, any())
        } returns flow {
            emit(ProgressiveFeedBatch(listOf(rawItem), isFromCache = false, isComplete = false, hasViewerLocation = false))
            delay(50)
            snapshotReady = true
            emit(ProgressiveFeedBatch(listOf(rawItem), isFromCache = false, isComplete = true, hasViewerLocation = false))
        }
        coEvery {
            getFriendsCapricesUseCase.refreshCachedDistances("user_1", 20)
        } answers {
            if (snapshotReady) {
                ProgressiveFeedBatch(listOf(itemWithDistance), isFromCache = false, isComplete = true, hasViewerLocation = true)
            } else {
                null
            }
        }

        val vm = createViewModel(userProfileRepository = userProfileRepo)
        vm.loadFriendsFeed()
        testScheduler.advanceTimeBy(10)
        testScheduler.runCurrent()

        // El usuario concede permiso en mitad de la carga, antes de que cachedSnapshots esté listo
        permissionGranted = true
        vm.onLocationPermissionChanged(true)
        advanceUntilIdle()

        assertThat(vm.state.value.feedItems.single().approximateDistanceMeters).isEqualTo(1_500.0)
        assertThat(vm.state.value.isLocationPermissionGranted).isTrue()
        coVerify(atLeast = 1) { getFriendsCapricesUseCase.refreshCachedDistances("user_1", 20) }
        coVerify(exactly = 1) { userProfileRepo.updateLocationCell("user_1", "v1_2608_3525") }

        // Una recarga posterior sin cambio de celda no vuelve a escribir en users/{uid}
        vm.loadFriendsFeed(isManualRefresh = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { userProfileRepo.updateLocationCell("user_1", "v1_2608_3525") }
    }

    @Test
    fun `ActivePollViewModel usa authorLocationCell como respaldo cuando el capricho no tiene locationCell`() = runTest(testDispatcher) {
        val castVote = mockk<CastVoteUseCase>()
        val observeVotes = mockk<ObserveVotesUseCase>()
        val calculateVerdict = CalculateVerdictUseCase()
        val voteRepo = mockk<VoteRepository>()
        val placeNameUseCase = mockk<GetApproximatePlaceNameUseCase>()

        coEvery { voteRepo.getUserVote("priv_legacy", "user_1") } returns Result.success(null)
        every { observeVotes.execute("priv_legacy") } returns flowOf(emptyList())
        coEvery { placeNameUseCase.execute("v1_2608_3525") } returns "Madrid"

        val activePollVm = ActivePollViewModel(
            castVoteUseCase = castVote,
            observeVotesUseCase = observeVotes,
            calculateVerdictUseCase = calculateVerdict,
            voteRepository = voteRepo,
            getApproximatePlaceNameUseCase = placeNameUseCase
        )
        val legacyFriendCaprice = Caprice(
            id = "priv_legacy",
            userId = "friend_1",
            question = "¿Antigua duda?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = System.currentTimeMillis(),
            nearbyCell = null,
            locationCell = null
        )

        activePollVm.initialize(
            caprice = legacyFriendCaprice,
            currentUserId = "user_1",
            authorUsername = "ana",
            approximateDistanceMeters = 1_800.0,
            authorLocationCell = "v1_2608_3525"
        )
        testScheduler.runCurrent()

        assertThat(activePollVm.state.value.approximateDistanceMeters).isEqualTo(1_800.0)
        assertThat(activePollVm.state.value.approximatePlaceName).isEqualTo("Madrid")
        assertThat(activePollVm.state.value.isResolvingPlaceName).isFalse()
        activePollVm.reset()
        testScheduler.runCurrent()
    }

    @Test
    fun `saveCaprice falla y no publica si falla la subida de imagen`() = runTest(testDispatcher) {
        val uploadUseCase = mockk<UploadCapriceImageUseCase>()
        val vm = CapriceViewModel(
            saveCapriceUseCase = saveCapriceUseCase,
            getCapricesUseCase = getCapricesUseCase,
            uploadCapriceImageUseCase = uploadUseCase,
            getFriendsCapricesUseCase = getFriendsCapricesUseCase,
            profileCacheControl = profileCacheControl,
            capriceCacheControl = capriceCacheControl,
            getNearbyCapricesUseCase = getNearbyCapricesUseCase,
            locationRepository = locationRepository,
            clockMillis = { currentClock },
            currentUserIdProvider = { currentUid },
            ioDispatcher = testDispatcher
        )
        every { locationRepository.hasLocationPermission() } returns false
        coEvery { uploadUseCase.execute(any(), any()) } returns Result.failure(IllegalStateException("Image too large"))

        val result = vm.saveCaprice(
            type = "Compra",
            questionText = "¿Compro chaqueta?",
            answer1 = "Sí",
            answer2 = "No",
            imageBytes = byteArrayOf(1, 2, 3)
        )

        assertThat(result.isFailure).isTrue()
        assertThat(vm.state.value.error).contains("Image too large")
        assertThat(vm.state.value.isLoading).isFalse()
        coVerify(exactly = 0) { saveCapriceUseCase.execute(any()) }
    }

    @Test
    fun `ActivePollViewModel es idempotente al reinicializar una encuesta expirada y propaga error de escucha`() = runTest(testDispatcher) {
        val castVote = mockk<CastVoteUseCase>()
        val observeVotes = mockk<ObserveVotesUseCase>()
        val calculateVerdict = CalculateVerdictUseCase()
        val voteRepo = mockk<VoteRepository>()
        val existingVote = Vote(capriceId = "exp_1", voterId = "user_1", option = 1)

        coEvery { voteRepo.getUserVote("exp_1", "user_1") } returns Result.success(existingVote)
        every { observeVotes.execute("exp_1") } returns flowOf(listOf(existingVote))

        val activePollVm = ActivePollViewModel(
            castVoteUseCase = castVote,
            observeVotesUseCase = observeVotes,
            calculateVerdictUseCase = calculateVerdict,
            voteRepository = voteRepo
        )
        val expiredCaprice = Caprice(
            id = "exp_1",
            userId = "friend_1",
            question = "¿Votación cerrada?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = System.currentTimeMillis() - PollTiming.POLL_DURATION_MILLIS - 60_000L
        )

        activePollVm.initialize(expiredCaprice, "user_1")
        testScheduler.runCurrent()
        activePollVm.initialize(expiredCaprice.copy(finalDecision = 1, finalDecisionAt = expiredCaprice.timestamp + 400_000L), "user_1")
        testScheduler.runCurrent()

        coVerify(exactly = 1) { voteRepo.getUserVote("exp_1", "user_1") }
        assertThat(activePollVm.state.value.userVote).isEqualTo(existingVote)
        assertThat(activePollVm.state.value.caprice?.finalDecision).isEqualTo(1)

        // Verifica manejo seguro de error en la escucha de votos
        coEvery { voteRepo.getUserVote("err_1", "user_1") } returns Result.success(null)
        every { observeVotes.execute("err_1") } returns flow {
            throw IllegalStateException("PERMISSION_DENIED")
        }
        activePollVm.initialize(expiredCaprice.copy(id = "err_1"), "user_1")
        testScheduler.runCurrent()

        assertThat(activePollVm.state.value.errorMessage).contains("PERMISSION_DENIED")
        activePollVm.reset()
        testScheduler.runCurrent()
    }
}
