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
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
}
