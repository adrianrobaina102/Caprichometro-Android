package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.GetFriendsCapricesUseCase
import com.example.caprichometro.application.usecase.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.ObserveFriendCapricesUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.splitByDistanceRadius
import com.example.caprichometro.domain.repository.CapriceDetailsRepository
import com.example.caprichometro.domain.repository.CapriceNotificationHistoryRepository
import com.example.caprichometro.domain.repository.CapriceObservationRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CapriceAudienceVisibilityTest {

    @Test
    fun `isVisibleTo respeta lista vacia por compatibilidad y restringe cuando hay destinatarios`() {
        val legacyCaprice = Caprice(
            id = "legacy",
            userId = "author_1",
            question = "¿Legacy?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = emptyList()
        )
        assertThat(legacyCaprice.isVisibleTo("friend_a")).isTrue()
        assertThat(legacyCaprice.isVisibleTo("")).isFalse()

        val targetedCaprice = Caprice(
            id = "private",
            userId = "author_1",
            question = "¿Solo para A?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = listOf("author_1", "friend_a")
        )
        assertThat(targetedCaprice.isVisibleTo("author_1")).isTrue()
        assertThat(targetedCaprice.isVisibleTo("friend_a")).isTrue()
        assertThat(targetedCaprice.isVisibleTo("friend_b")).isFalse()
    }

    @Test
    fun `GetFriendsCapricesUseCase filtra caprichos privados que no incluyen al usuario actual`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val capriceRepository = mockk<CapriceRepository>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val visibleCaprice = Caprice(
            id = "c1",
            userId = "friend_1",
            question = "¿Para mí?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 100L,
            visibleToUserIds = listOf("friend_1", "me")
        )
        val hiddenCaprice = Caprice(
            id = "c2",
            userId = "friend_1",
            question = "¿Solo para otro?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 200L,
            visibleToUserIds = listOf("friend_1", "other")
        )
        val legacyCaprice = Caprice(
            id = "c3",
            userId = "friend_1",
            question = "¿Antiguo?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 50L,
            visibleToUserIds = emptyList()
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        coEvery { capriceRepository.getCaprices("friend_1", 20) } returns Result.success(
            listOf(hiddenCaprice, visibleCaprice, legacyCaprice)
        )

        val useCase = GetFriendsCapricesUseCase(getAcceptedFriendsUseCase, capriceRepository)
        val result = useCase.execute("me").getOrThrow()

        assertThat(result.map { it.caprice.id }).containsExactly("c1", "c3")
    }

    @Test
    fun `ObserveFriendCapricesUseCase ignora caprichos en directo no dirigidos al usuario`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val observationRepository = mockk<CapriceObservationRepository>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val hiddenLive = Caprice(
            id = "live_hidden",
            userId = "friend_1",
            question = "¿Oculto?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2000L,
            visibleToUserIds = listOf("friend_1", "other")
        )
        val visibleLive = Caprice(
            id = "live_visible",
            userId = "friend_1",
            question = "¿Visible?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2001L,
            visibleToUserIds = listOf("friend_1", "me")
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        every { observationRepository.observeFriendCaprices("friend_1", 1000L) } returns flowOf(hiddenLive, visibleLive)

        val useCase = ObserveFriendCapricesUseCase(getAcceptedFriendsUseCase, observationRepository)
        val emitted = useCase.execute("me", 1000L).toList()

        assertThat(emitted.map { it.caprice.id }).containsExactly("live_visible")
    }

    @Test
    fun `NotifyFriendCapriceUseCase omite capricho mas reciente oculto y notifica el ultimo visible`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val capriceRepository = mockk<CapriceRepository>()
        val historyRepository = mockk<CapriceNotificationHistoryRepository>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val newerHidden = Caprice(
            id = "c_hidden",
            userId = "friend_1",
            question = "¿Secreto?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 3000L,
            visibleToUserIds = listOf("friend_1", "other")
        )
        val olderVisible = Caprice(
            id = "c_visible",
            userId = "friend_1",
            question = "¿Para ti?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2000L,
            visibleToUserIds = listOf("friend_1", "me")
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        coEvery { capriceRepository.getCaprices("friend_1", 20) } returns Result.success(
            listOf(newerHidden, olderVisible)
        )
        every { historyRepository.getLastNotifiedCapriceId("me", "friend_1") } returns null
        every { historyRepository.getLastNotifiedTimestamp("me", "friend_1") } returns 0L
        every { historyRepository.setLastNotified("me", "friend_1", "c_visible", 2000L) } just runs

        val notified = mutableListOf<String>()
        val useCase = NotifyFriendCapriceUseCase(getAcceptedFriendsUseCase, capriceRepository, historyRepository)
        val items = useCase.checkAndNotifyUnnotifiedFriendCaprices("me") { _, caprice ->
            notified.add(caprice.id)
        }

        assertThat(notified).containsExactly("c_visible")
        assertThat(items.map { it.caprice.id }).containsExactly("c_visible")
        verify(exactly = 1) { historyRepository.setLastNotified("me", "friend_1", "c_visible", 2000L) }
    }

    @Test
    fun `GetNotificationCapriceUseCase deniega acceso si el receptor no esta en visibleToUserIds`() = runTest {
        val capriceDetailsRepository = mockk<CapriceDetailsRepository>()
        val userProfileRepository = mockk<UserProfileRepository>()
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val restrictedCaprice = Caprice(
            id = "cap_restricted",
            userId = "friend_1",
            question = "¿Solo para otro?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = listOf("friend_1", "other")
        )

        coEvery { capriceDetailsRepository.getCapriceById("cap_restricted") } returns Result.success(restrictedCaprice)
        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))

        val useCase = GetNotificationCapriceUseCase(
            capriceDetailsRepository,
            userProfileRepository,
            getAcceptedFriendsUseCase
        )
        val result = useCase.execute("cap_restricted", "me")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `GetFriendsCapricesUseCase calcula distancia en Amigos ordena menor de 50km antes de mayor de 50km y emite progresivo desde cache`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val madrid = ApproximateLocation(40.4168, -3.7038)
        val madridCell = NearbyCellGrid.cellId(madrid)
        val valenciaCell = NearbyCellGrid.cellId(ApproximateLocation(39.4699, -0.3763))

        val friendshipRepo = mockk<FriendshipRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val capriceRepo = mockk<CapriceRepository>()
        val acceptedUseCase = mockk<GetAcceptedFriendsUseCase>()
        val locationRepo = mockk<DeviceLocationRepository>()

        val friendClose = UserProfile(uid = "f_close", username = "cerca")
        val friendFar = UserProfile(uid = "f_far", username = "lejos")

        val cachedCaprice = Caprice(
            id = "c_cache", userId = "f_close", question = "¿En Madrid?", answer1 = "Sí", answer2 = "No",
            timestamp = 1_000L, locationCell = madridCell
        )
        val farNewerCaprice = Caprice(
            id = "c_far", userId = "f_far", question = "¿Desde Valencia?", answer1 = "Sí", answer2 = "No",
            timestamp = 5_000L, nearbyCell = valenciaCell
        )
        val legacyNoDistance = Caprice(
            id = "c_legacy", userId = "f_close", question = "¿Sin ubicación?", answer1 = "Sí", answer2 = "No",
            timestamp = 2_000L, nearbyCell = null, locationCell = null
        )

        val acceptedFriendships = listOf(
            Friendship("f_close_me", listOf("me", "f_close"), "me", FriendshipStatus.ACCEPTED),
            Friendship("f_far_me", listOf("me", "f_far"), "me", FriendshipStatus.ACCEPTED)
        )

        every { locationRepo.hasLocationPermission() } returns true
        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(madrid)

        coEvery { friendshipRepo.getCachedFriendships("me") } returns acceptedFriendships
        coEvery { friendshipRepo.getCachedBlockedUserIds("me") } returns emptyList()
        coEvery { friendshipRepo.isCachedBlocked("me", any()) } returns false
        coEvery { userRepo.getCachedUserById("f_close") } returns friendClose
        coEvery { userRepo.getCachedUserById("f_far") } returns friendFar
        coEvery { capriceRepo.getCachedCaprices("f_close", 100) } returns listOf(cachedCaprice)
        coEvery { capriceRepo.getCachedCaprices("f_far", 100) } returns emptyList()

        every { friendshipRepo.getFriendships("me") } returns flowOf(acceptedFriendships)
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(emptyList())
        coEvery { friendshipRepo.isBlocked("me", any()) } returns Result.success(false)
        coEvery { userRepo.getUserById("f_close") } returns Result.success(friendClose)
        coEvery { userRepo.getUserById("f_far") } returns Result.success(friendFar)
        coEvery { capriceRepo.getCaprices("f_close", 100) } returns Result.success(listOf(legacyNoDistance, cachedCaprice))
        coEvery { capriceRepo.getCaprices("f_far", 100) } returns Result.success(listOf(farNewerCaprice))

        val useCase = GetFriendsCapricesUseCase(
            getAcceptedFriendsUseCase = acceptedUseCase,
            capriceRepository = capriceRepo,
            defaultDispatcher = testDispatcher,
            friendshipRepository = friendshipRepo,
            userProfileRepository = userRepo,
            locationRepository = locationRepo
        )

        val batches = useCase.observeProgressiveFeed(
            currentUserId = "me",
            initialBatchSize = 2,
            perFriendLimit = 100,
            totalLimit = 200,
            includeCache = true
        ).toList()

        assertThat(batches.first().isFromCache).isTrue()
        assertThat(batches.first().items.map { it.caprice.id }).containsExactly("c_cache")
        assertThat(batches.first().items.first().approximateDistanceMeters).isLessThan(5_000.0)

        val finalBatch = batches.last()
        assertThat(finalBatch.isComplete).isTrue()
        assertThat(finalBatch.isFromCache).isFalse()
        // Los <= 50 km o sin ubicación van arriba por fecha (c_legacy, c_cache); > 50 km va debajo (c_far)
        assertThat(finalBatch.items.map { it.caprice.id }).containsExactly("c_legacy", "c_cache", "c_far")

        val sections = splitByDistanceRadius(finalBatch.items)
        assertThat(sections.localItems.map { it.caprice.id }).containsExactly("c_legacy", "c_cache")
        assertThat(sections.farItems.map { it.caprice.id }).containsExactly("c_far")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `GetFriendsCapricesUseCase opcion A emite 5 primeros de cache pagina en 20 y pospone perfiles de pagina 2 a observeNextPage`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val madrid = ApproximateLocation(40.4168, -3.7038)
        val madridCell = NearbyCellGrid.cellId(madrid)

        val friendshipRepo = mockk<FriendshipRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val capriceRepo = mockk<CapriceRepository>()
        val acceptedUseCase = mockk<GetAcceptedFriendsUseCase>()
        val locationRepo = mockk<DeviceLocationRepository>()

        val requestedServerProfiles = mutableListOf<String>()
        val friendA = UserProfile(uid = "f_a", username = "ana")
        val friendB = UserProfile(uid = "f_b", username = "bruno")

        val friendACaprices = (1..20).map { idx ->
            Caprice(
                id = "a_$idx", userId = "f_a", question = "¿A $idx?", answer1 = "Sí", answer2 = "No",
                timestamp = 100_000L - idx * 1_000L, locationCell = madridCell
            )
        }
        val friendBCaprices = (21..27).map { idx ->
            Caprice(
                id = "b_$idx", userId = "f_b", question = "¿B $idx?", answer1 = "Sí", answer2 = "No",
                timestamp = 100_000L - idx * 1_000L, locationCell = madridCell
            )
        }

        val acceptedFriendships = listOf(
            Friendship("f_a_me", listOf("me", "f_a"), "me", FriendshipStatus.ACCEPTED),
            Friendship("f_b_me", listOf("me", "f_b"), "me", FriendshipStatus.ACCEPTED)
        )

        every { locationRepo.hasLocationPermission() } returns true
        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(madrid)

        coEvery { friendshipRepo.getCachedFriendships("me") } returns acceptedFriendships
        coEvery { friendshipRepo.getCachedBlockedUserIds("me") } returns emptyList()
        coEvery { friendshipRepo.isCachedBlocked("me", any()) } returns false
        coEvery { userRepo.getCachedUserById("f_a") } returns friendA
        coEvery { userRepo.getCachedUserById("f_b") } returns friendB
        coEvery { capriceRepo.getCachedCaprices("f_a", 100) } returns friendACaprices.take(8)
        coEvery { capriceRepo.getCachedCaprices("f_b", 100) } returns emptyList()

        every { friendshipRepo.getFriendships("me") } returns flowOf(acceptedFriendships)
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(emptyList())
        coEvery { friendshipRepo.isBlocked("me", any()) } returns Result.success(false)
        coEvery { userRepo.getUserById(any()) } answers {
            val uid = firstArg<String>()
            requestedServerProfiles += uid
            Result.success(if (uid == "f_a") friendA else friendB)
        }
        coEvery { capriceRepo.getCaprices("f_a", 100) } returns Result.success(friendACaprices)
        coEvery { capriceRepo.getCaprices("f_b", 100) } returns Result.success(friendBCaprices)

        val useCase = GetFriendsCapricesUseCase(
            getAcceptedFriendsUseCase = acceptedUseCase,
            capriceRepository = capriceRepo,
            defaultDispatcher = testDispatcher,
            friendshipRepository = friendshipRepo,
            userProfileRepository = userRepo,
            locationRepository = locationRepo
        )

        val page1Batches = useCase.observeProgressiveFeed(
            currentUserId = "me",
            initialBatchSize = 5,
            perFriendLimit = 100,
            totalLimit = 200,
            includeCache = true
        ).toList()

        // Cache entrega 5 rápidos primero y luego completa los 8 en caché
        assertThat(page1Batches[0].isFromCache).isTrue()
        assertThat(page1Batches[0].items).hasSize(5)
        assertThat(page1Batches[0].totalAvailableCount).isEqualTo(8)

        val finalPage1 = page1Batches.last()
        assertThat(finalPage1.isComplete).isTrue()
        assertThat(finalPage1.items).hasSize(20)
        assertThat(finalPage1.totalAvailableCount).isEqualTo(27)
        assertThat(requestedServerProfiles).containsExactly("f_a")

        // Página 2 bajo demanda hidrata 5 primero (25) y luego 2 restantes (27) consultando f_b solo entonces
        val page2Batches = useCase.observeNextPage(
            currentUserId = "me",
            currentLoadedCount = 20,
            initialBatchSize = 5,
            pageSize = 20
        ).toList()

        assertThat(page2Batches).hasSize(2)
        assertThat(page2Batches[0].items).hasSize(25)
        assertThat(page2Batches[0].isComplete).isFalse()
        assertThat(page2Batches[0].totalAvailableCount).isEqualTo(27)
        assertThat(page2Batches[1].items).hasSize(27)
        assertThat(page2Batches[1].isComplete).isTrue()
        assertThat(page2Batches[1].totalAvailableCount).isEqualTo(27)
        assertThat(requestedServerProfiles).containsExactly("f_a", "f_b")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `GetFriendsCapricesUseCase refreshCachedDistances recalcula distancias y reordena sin reconsultar Firestore`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val madrid = ApproximateLocation(40.4168, -3.7038)
        val madridCell = NearbyCellGrid.cellId(madrid)
        val valenciaCell = NearbyCellGrid.cellId(ApproximateLocation(39.4699, -0.3763))

        val friendshipRepo = mockk<FriendshipRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val capriceRepo = mockk<CapriceRepository>()
        val acceptedUseCase = mockk<GetAcceptedFriendsUseCase>()
        val locationRepo = mockk<DeviceLocationRepository>()

        val friendA = UserProfile(uid = "f_a", username = "ana")
        val friendB = UserProfile(uid = "f_b", username = "bruno")

        val localOlder = Caprice(
            id = "local_old", userId = "f_a", question = "¿Cerca?", answer1 = "Sí", answer2 = "No",
            timestamp = 1_000L, locationCell = madridCell
        )
        val farNewer = Caprice(
            id = "far_new", userId = "f_b", question = "¿Lejos?", answer1 = "Sí", answer2 = "No",
            timestamp = 5_000L, locationCell = valenciaCell
        )
        val friendships = listOf(
            Friendship("fa", listOf("me", "f_a"), "me", FriendshipStatus.ACCEPTED),
            Friendship("fb", listOf("me", "f_b"), "me", FriendshipStatus.ACCEPTED)
        )

        var permissionGranted = false
        every { locationRepo.hasLocationPermission() } answers { permissionGranted }
        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(madrid)

        every { friendshipRepo.getFriendships("me") } returns flowOf(friendships)
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(emptyList())
        coEvery { friendshipRepo.isBlocked("me", any()) } returns Result.success(false)
        coEvery { userRepo.getUserById("f_a") } returns Result.success(friendA)
        coEvery { userRepo.getUserById("f_b") } returns Result.success(friendB)
        coEvery { capriceRepo.getCaprices("f_a", 100) } returns Result.success(listOf(localOlder))
        coEvery { capriceRepo.getCaprices("f_b", 100) } returns Result.success(listOf(farNewer))

        val useCase = GetFriendsCapricesUseCase(
            getAcceptedFriendsUseCase = acceptedUseCase,
            capriceRepository = capriceRepo,
            defaultDispatcher = testDispatcher,
            friendshipRepository = friendshipRepo,
            userProfileRepository = userRepo,
            locationRepository = locationRepo
        )

        // Arranque inicial sin permiso: ordena solo por timestamp (far_new arriba) sin distancias
        val initialBatch = useCase.observeProgressiveFeed(
            currentUserId = "me",
            initialBatchSize = 5,
            perFriendLimit = 100,
            totalLimit = 200,
            includeCache = false
        ).toList().last()
        assertThat(initialBatch.items.map { it.caprice.id }).containsExactly("far_new", "local_old")
        assertThat(initialBatch.items).allMatch { it.approximateDistanceMeters == null }

        // Al concederse permiso, recalcula distancias en memoria sin volver a consultar Firestore
        permissionGranted = true
        val refreshed = useCase.refreshCachedDistances(currentUserId = "me", visiblePageLimit = 20)
        assertThat(refreshed).isNotNull
        assertThat(refreshed!!.items.map { it.caprice.id }).containsExactly("local_old", "far_new")
        assertThat(refreshed.items[0].approximateDistanceMeters).isLessThan(5_000.0)
        assertThat(refreshed.items[1].approximateDistanceMeters).isGreaterThan(50_000.0)
        io.mockk.coVerify(exactly = 1) { capriceRepo.getCaprices("f_a", 100) }
        io.mockk.coVerify(exactly = 1) { capriceRepo.getCaprices("f_b", 100) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `GetFriendsCapricesUseCase usa author locationCell como respaldo sin pisar la celda propia del capricho`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val madrid = ApproximateLocation(40.4168, -3.7038)
        val madridCell = NearbyCellGrid.cellId(madrid)
        val valenciaCell = NearbyCellGrid.cellId(ApproximateLocation(39.4699, -0.3763))

        val friendshipRepo = mockk<FriendshipRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val capriceRepo = mockk<CapriceRepository>()
        val acceptedUseCase = mockk<GetAcceptedFriendsUseCase>()
        val locationRepo = mockk<DeviceLocationRepository>()

        val friendA = UserProfile(uid = "f_a", username = "ana", locationCell = madridCell)
        val friendB = UserProfile(uid = "f_b", username = "bruno", locationCell = madridCell)

        val legacyWithoutCapriceCell = Caprice(
            id = "legacy_friend",
            userId = "f_a",
            question = "¿Capricho histórico sin celda?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2_000L,
            locationCell = null,
            nearbyCell = null
        )
        val explicitCapriceCell = Caprice(
            id = "explicit_cell",
            userId = "f_b",
            question = "¿Capricho publicado desde otra ciudad?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 3_000L,
            locationCell = valenciaCell,
            nearbyCell = null
        )

        val friendships = listOf(
            Friendship("fa", listOf("me", "f_a"), "me", FriendshipStatus.ACCEPTED),
            Friendship("fb", listOf("me", "f_b"), "me", FriendshipStatus.ACCEPTED)
        )

        every { locationRepo.hasLocationPermission() } returns true
        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(madrid)
        every { friendshipRepo.getFriendships("me") } returns flowOf(friendships)
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(emptyList())
        coEvery { friendshipRepo.isBlocked("me", any()) } returns Result.success(false)
        coEvery { userRepo.getUserById("f_a") } returns Result.success(friendA)
        coEvery { userRepo.getUserById("f_b") } returns Result.success(friendB)
        coEvery { capriceRepo.getCaprices("f_a", 100) } returns Result.success(listOf(legacyWithoutCapriceCell))
        coEvery { capriceRepo.getCaprices("f_b", 100) } returns Result.success(listOf(explicitCapriceCell))

        val useCase = GetFriendsCapricesUseCase(
            getAcceptedFriendsUseCase = acceptedUseCase,
            capriceRepository = capriceRepo,
            defaultDispatcher = testDispatcher,
            friendshipRepository = friendshipRepo,
            userProfileRepository = userRepo,
            locationRepository = locationRepo
        )

        val finalBatch = useCase.observeProgressiveFeed(
            currentUserId = "me",
            initialBatchSize = 5,
            perFriendLimit = 100,
            totalLimit = 200,
            includeCache = false
        ).toList().last()

        assertThat(finalBatch.hasViewerLocation).isTrue()
        assertThat(finalBatch.items.map { it.caprice.id }).containsExactly("legacy_friend", "explicit_cell")
        assertThat(finalBatch.items[0].approximateDistanceMeters).isLessThan(5_000.0)
        assertThat(finalBatch.items[1].approximateDistanceMeters).isGreaterThan(50_000.0)
    }
}
