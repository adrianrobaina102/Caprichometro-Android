package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.GetNearbyCapricesUseCase
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.DeviceLocationException
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.NearbyCapriceBatch
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetNearbyCapricesUseCaseTest {

    private val viewerLocation = ApproximateLocation(latitude = 40.4168, longitude = -3.7038)
    private val madridCell = NearbyCellGrid.cellId(viewerLocation)
    private val valenciaCell = NearbyCellGrid.cellId(ApproximateLocation(39.4699, -0.3763))
    private val tokyoCell = NearbyCellGrid.cellId(ApproximateLocation(35.6762, 139.6503))

    @Test
    fun `ordena por distancia ascendente fecha descendente e ID y excluye propios y bloqueados`() = runTest {
        val locationRepo = mockk<DeviceLocationRepository>()
        val nearbyRepo = mockk<NearbyCapriceRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val friendshipRepo = mockk<FriendshipRepository>()

        val ownCaprice = Caprice(
            id = "own", userId = "me", question = "¿Mío?", answer1 = "Sí", answer2 = "No",
            timestamp = 9_999L, nearbyCell = madridCell
        )
        val blockedCaprice = Caprice(
            id = "blocked", userId = "blocked_u", question = "¿Bloqueado?", answer1 = "Sí", answer2 = "No",
            timestamp = 9_000L, nearbyCell = madridCell
        )
        val madridOlder = Caprice(
            id = "c_b", userId = "author_1", question = "¿Madrid viejo?", answer1 = "A", answer2 = "B",
            timestamp = 1_000L, nearbyCell = madridCell, visibleToUserIds = listOf("author_1")
        )
        val madridNewer = Caprice(
            id = "c_z", userId = "author_1", question = "¿Madrid nuevo?", answer1 = "A", answer2 = "B",
            timestamp = 2_000L, nearbyCell = madridCell, visibleToUserIds = listOf("author_1")
        )
        val madridTieIdA = Caprice(
            id = "c_a", userId = "author_2", question = "¿Empate A?", answer1 = "A", answer2 = "B",
            timestamp = 1_000L, nearbyCell = madridCell
        )
        val valencia = Caprice(
            id = "valencia", userId = "author_1", question = "¿Valencia?", answer1 = "A", answer2 = "B",
            timestamp = 5_000L, nearbyCell = valenciaCell
        )
        val tokyo = Caprice(
            id = "tokyo", userId = "author_3", question = "¿Tokio?", answer1 = "A", answer2 = "B",
            timestamp = 8_000L, nearbyCell = tokyoCell
        )
        val invalidCell = Caprice(
            id = "invalid", userId = "author_2", question = "¿Inválida?", answer1 = "A", answer2 = "B",
            timestamp = 9_000L, nearbyCell = "bad_cell"
        )

        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(viewerLocation)
        coEvery { nearbyRepo.getDiscoverableCaprices() } returns Result.success(
            listOf(tokyo, ownCaprice, madridOlder, valencia, blockedCaprice, invalidCell, madridNewer, madridTieIdA)
        )
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(listOf("blocked_u"))
        coEvery { userRepo.getUserById("author_1") } returns Result.success(UserProfile(uid = "author_1", username = "laura"))
        coEvery { userRepo.getUserById("author_2") } returns Result.success(UserProfile(uid = "author_2", username = "pablo"))
        coEvery { userRepo.getUserById("author_3") } returns Result.success(UserProfile(uid = "author_3", username = "kenji"))

        val useCase = GetNearbyCapricesUseCase(locationRepo, nearbyRepo, userRepo, friendshipRepo)
        val items = useCase.execute("me").getOrThrow()

        assertThat(items.map { it.caprice.id }).containsExactly("c_z", "c_a", "c_b", "valencia", "tokyo")
        assertThat(items).allMatch { !it.canOpenAuthorProfile && it.approximateDistanceMeters != null }
        coVerify(exactly = 1) { userRepo.getUserById("author_1") }
        coVerify(exactly = 0) { userRepo.getUserById("blocked_u") }
    }

    @Test
    fun `propaga fallo de permiso de ubicacion sin devolver lista vacia`() = runTest {
        val locationRepo = mockk<DeviceLocationRepository>()
        val nearbyRepo = mockk<NearbyCapriceRepository>()
        val userRepo = mockk<UserProfileRepository>()

        coEvery { locationRepo.currentApproximateLocation() } returns Result.failure(DeviceLocationException.PermissionDenied())

        val useCase = GetNearbyCapricesUseCase(locationRepo, nearbyRepo, userRepo)
        val result = useCase.execute("me")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(DeviceLocationException.PermissionDenied::class.java)
    }

    @Test
    fun `propaga fallo de Firestore en caprichos descubribles`() = runTest {
        val locationRepo = mockk<DeviceLocationRepository>()
        val nearbyRepo = mockk<NearbyCapriceRepository>()
        val userRepo = mockk<UserProfileRepository>()

        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(viewerLocation)
        coEvery { nearbyRepo.getDiscoverableCaprices() } returns Result.failure(IllegalStateException("PERMISSION_DENIED"))

        val useCase = GetNearbyCapricesUseCase(locationRepo, nearbyRepo, userRepo)
        val result = useCase.execute("me")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("PERMISSION_DENIED")
    }

    @Test
    fun `observeProgressiveFeed emite primero cache local luego tanda inicial de 5 y finalmente el resto ordenado por distancia`() = runTest {
        val locationRepo = mockk<DeviceLocationRepository>()
        val nearbyRepo = mockk<NearbyCapriceRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val friendshipRepo = mockk<FriendshipRepository>()

        val cachedTokyo = Caprice(
            id = "tokyo_cached", userId = "a_tokyo", question = "¿Tokio cache?", answer1 = "A", answer2 = "B",
            timestamp = 1_000L, nearbyCell = tokyoCell
        )
        val valenciaFar = Caprice(
            id = "valencia_far", userId = "a_val", question = "¿Valencia?", answer1 = "A", answer2 = "B",
            timestamp = 2_000L, nearbyCell = valenciaCell
        )
        val madridClose = Caprice(
            id = "madrid_close", userId = "a_mad", question = "¿Madrid?", answer1 = "A", answer2 = "B",
            timestamp = 3_000L, nearbyCell = madridCell
        )

        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(viewerLocation)
        coEvery { friendshipRepo.getCachedBlockedUserIds("me") } returns emptyList()
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(emptyList())
        coEvery { userRepo.getCachedUserById("a_tokyo") } returns UserProfile(uid = "a_tokyo", username = "kenji_cache")
        coEvery { userRepo.getUserById("a_tokyo") } returns Result.success(UserProfile(uid = "a_tokyo", username = "kenji"))
        coEvery { userRepo.getUserById("a_val") } returns Result.success(UserProfile(uid = "a_val", username = "marta"))
        coEvery { userRepo.getUserById("a_mad") } returns Result.success(UserProfile(uid = "a_mad", username = "luis"))

        every { nearbyRepo.streamDiscoverableCaprices(5, 20, true) } returns flowOf(
            NearbyCapriceBatch(listOf(cachedTokyo), isFromCache = true, isComplete = false),
            NearbyCapriceBatch(listOf(valenciaFar), isFromCache = false, isComplete = false),
            NearbyCapriceBatch(listOf(cachedTokyo, valenciaFar, madridClose), isFromCache = false, isComplete = true)
        )

        val useCase = GetNearbyCapricesUseCase(locationRepo, nearbyRepo, userRepo, friendshipRepo)
        val batches = useCase.observeProgressiveFeed("me", includeCache = true, initialBatchSize = 5, pageSize = 20).toList()

        assertThat(batches).hasSize(3)
        assertThat(batches[0].isFromCache).isTrue()
        assertThat(batches[0].isComplete).isFalse()
        assertThat(batches[0].items.map { it.caprice.id }).containsExactly("tokyo_cached")

        assertThat(batches[1].isFromCache).isFalse()
        assertThat(batches[1].isComplete).isFalse()
        assertThat(batches[1].items.map { it.caprice.id }).containsExactly("valencia_far")

        assertThat(batches[2].isFromCache).isFalse()
        assertThat(batches[2].isComplete).isTrue()
        assertThat(batches[2].items.map { it.caprice.id }).containsExactly("madrid_close", "valencia_far", "tokyo_cached")
        // a_tokyo ya estaba resuelto desde caché en stream y a_val en tanda 1: no se repiten llamadas de perfil
        coVerify(exactly = 1) { userRepo.getUserById("a_val") }
    }

    @Test
    fun `opcion A hidrata solo pagina inicial de 20 con 5 primeros de cache y observeNextPage hidrata pagina 2 de 5 en 5`() = runTest {
        val locationRepo = mockk<DeviceLocationRepository>()
        val nearbyRepo = mockk<NearbyCapriceRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val friendshipRepo = mockk<FriendshipRepository>()

        val allCaprices = (1..27).map { idx ->
            Caprice(
                id = "pub_$idx",
                userId = "author_$idx",
                question = "¿Pregunta $idx?",
                answer1 = "A",
                answer2 = "B",
                timestamp = 100_000L - idx * 1_000L,
                nearbyCell = madridCell
            )
        }

        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(viewerLocation)
        coEvery { friendshipRepo.getCachedBlockedUserIds("me") } returns emptyList()
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(emptyList())
        coEvery { userRepo.getCachedUserById(any()) } answers {
            val uid = firstArg<String>()
            UserProfile(uid = uid, username = "cached_$uid")
        }
        coEvery { userRepo.getUserById(any()) } answers {
            val uid = firstArg<String>()
            Result.success(UserProfile(uid = uid, username = "srv_$uid"))
        }

        every { nearbyRepo.streamDiscoverableCaprices(5, 20, true) } returns flowOf(
            NearbyCapriceBatch(allCaprices.take(8), isFromCache = true, isComplete = false),
            NearbyCapriceBatch(allCaprices.take(5), isFromCache = false, isComplete = false),
            NearbyCapriceBatch(allCaprices, isFromCache = false, isComplete = true)
        )

        val useCase = GetNearbyCapricesUseCase(locationRepo, nearbyRepo, userRepo, friendshipRepo)
        val page1Batches = useCase.observeProgressiveFeed("me", includeCache = true, initialBatchSize = 5, pageSize = 20).toList()

        // El primer lote de caché entrega solo los 5 más relevantes de forma inmediata
        assertThat(page1Batches.first().isFromCache).isTrue()
        assertThat(page1Batches.first().items).hasSize(5)
        assertThat(page1Batches.first().totalAvailableCount).isEqualTo(8)

        // Página 1 completa se topa en 20 ítems con totalAvailableCount = 27 y sin hidratar autores 21..27
        val finalPage1 = page1Batches.last()
        assertThat(finalPage1.isComplete).isTrue()
        assertThat(finalPage1.items).hasSize(20)
        assertThat(finalPage1.totalAvailableCount).isEqualTo(27)
        for (idx in 21..27) {
            coVerify(exactly = 0) { userRepo.getUserById("author_$idx") }
        }

        // Página 2 bajo demanda hidrata primeros 5 (20 -> 25) y luego los 2 restantes (25 -> 27)
        val page2Batches = useCase.observeNextPage("me", currentLoadedCount = 20, initialBatchSize = 5, pageSize = 20).toList()
        assertThat(page2Batches).hasSize(2)
        assertThat(page2Batches[0].items).hasSize(25)
        assertThat(page2Batches[0].isComplete).isFalse()
        assertThat(page2Batches[0].totalAvailableCount).isEqualTo(27)
        assertThat(page2Batches[1].items).hasSize(27)
        assertThat(page2Batches[1].isComplete).isTrue()
        assertThat(page2Batches[1].totalAvailableCount).isEqualTo(27)
        coVerify(exactly = 1) { userRepo.getUserById("author_27") }
    }

    @Test
    fun `General excluye publicaciones de amigos aceptados manteniendo desconocidos`() = runTest {
        val madrid = ApproximateLocation(40.4168, -3.7038)
        val madridCell = NearbyCellGrid.cellId(madrid)
        val locationRepo = mockk<DeviceLocationRepository>()
        val nearbyRepo = mockk<NearbyCapriceRepository>()
        val userRepo = mockk<UserProfileRepository>()
        val friendshipRepo = mockk<FriendshipRepository>()

        val friendCaprice = Caprice(
            id = "cap_friend",
            userId = "friend_1",
            question = "¿De mi amigo?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2_000L,
            nearbyCell = madridCell
        )
        val strangerCaprice = Caprice(
            id = "cap_stranger",
            userId = "stranger_1",
            question = "¿De un desconocido?",
            answer1 = "A",
            answer2 = "B",
            timestamp = 3_000L,
            nearbyCell = madridCell
        )

        coEvery { locationRepo.currentApproximateLocation() } returns Result.success(madrid)
        coEvery { friendshipRepo.getBlockedUserIds("me") } returns Result.success(emptyList())
        every {
            nearbyRepo.streamDiscoverableCaprices(initialBatchSize = 5, pageSize = 20, includeCache = false)
        } returns flowOf(
            NearbyCapriceBatch(
                caprices = listOf(friendCaprice, strangerCaprice),
                isFromCache = false,
                isComplete = true
            )
        )
        coEvery { userRepo.getUserById("stranger_1") } returns Result.success(
            UserProfile(uid = "stranger_1", username = "desconocido")
        )

        val useCase = GetNearbyCapricesUseCase(
            locationRepository = locationRepo,
            nearbyCapriceRepository = nearbyRepo,
            userProfileRepository = userRepo,
            friendshipRepository = friendshipRepo
        )

        val result = useCase.observeProgressiveFeed(
            currentUserId = "me",
            includeCache = false,
            excludedFriendIds = setOf("friend_1")
        ).toList().last()

        assertThat(result.items.map { it.caprice.id }).containsExactly("cap_stranger")
        coVerify(exactly = 0) { userRepo.getUserById("friend_1") }
    }
}
