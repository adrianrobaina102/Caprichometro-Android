package com.example.caprichometro

import android.content.Context
import com.example.caprichometro.application.usecase.caprice.GetCapricesUseCase
import com.example.caprichometro.application.usecase.caprice.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.caprice.UpdateFinalDecisionUseCase
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.RegisterUseCase
import com.example.caprichometro.application.usecase.auth.SignInUseCase
import com.example.caprichometro.application.usecase.auth.SignInWithGoogleUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.application.usecase.friendship.BlockUserUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetBlockedUsersUseCase
import com.example.caprichometro.application.usecase.friendship.RemoveFriendUseCase
import com.example.caprichometro.application.usecase.friendship.UnblockUserUseCase
import com.example.caprichometro.application.usecase.profile.UpdateBioUseCase
import com.example.caprichometro.application.usecase.profile.UpdateProfilePhotoUseCase
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.domain.model.Friendship
import com.example.caprichometro.domain.repository.FriendshipRepository
import com.example.caprichometro.domain.model.FriendshipStatus
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceRepository
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileRepository
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.AuthRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.VoteRepository
import com.example.caprichometro.infrastructure.ui.voting.ActivePollViewModel
import com.example.caprichometro.infrastructure.ui.CapriceViewModel
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Dobles en memoria puros sobre puertos de dominio para ejecutar pruebas Compose UI sin Firebase.
 */
internal class FakeUiAuthRepository(
    private var currentUser: User? = null
) : AuthRepository {
    override suspend fun signIn(email: String, password: String): Result<User> {
        val logged = User(id = "user_1", email = email, name = "adri")
        currentUser = logged
        return Result.success(logged)
    }

    override suspend fun register(email: String, password: String, name: String): Result<User> {
        val created = User(id = "user_1", email = email, name = name)
        currentUser = created
        return Result.success(created)
    }

    override suspend fun signInWithGoogle(context: Context): Result<User> =
        Result.failure(UnsupportedOperationException("No utilizado en smoke tests"))

    override suspend fun signOut(): Result<Unit> {
        currentUser = null
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        currentUser = null
        return Result.success(Unit)
    }

    override fun getCurrentUser(): User? = currentUser
}

internal class FakeUiUserProfileRepository(
    initialProfiles: List<UserProfile> = emptyList()
) : UserProfileRepository {
    private val profiles = initialProfiles.associateBy { it.uid }.toMutableMap()

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> =
        Result.success(profiles.values.none { it.username.equals(username, ignoreCase = true) })

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        profiles[profile.uid] = profile
        return Result.success(Unit)
    }

    override suspend fun getUserById(userId: String): Result<UserProfile?> =
        Result.success(profiles[userId])

    override suspend fun getCachedUserById(userId: String): UserProfile? = profiles[userId]

    override suspend fun updateBio(userId: String, bio: String): Result<Unit> {
        profiles[userId]?.let { profiles[userId] = it.copy(bio = bio) }
        return Result.success(Unit)
    }

    override suspend fun updatePhotoUrl(userId: String, photoUrl: String): Result<Unit> {
        profiles[userId]?.let { profiles[userId] = it.copy(photoUrl = photoUrl) }
        return Result.success(Unit)
    }

    override suspend fun updateLocationCell(userId: String, locationCell: String?): Result<Unit> {
        profiles[userId]?.let { profiles[userId] = it.copy(locationCell = locationCell) }
        return Result.success(Unit)
    }

    override suspend fun deleteProfile(userId: String, username: String): Result<Unit> {
        profiles.remove(userId)
        return Result.success(Unit)
    }
}

internal class FakeUiCapriceRepository(
    initialCaprices: List<Caprice> = emptyList()
) : CapriceRepository {
    val savedCaprices = initialCaprices.toMutableList()

    override suspend fun save(caprice: Caprice): Result<String> {
        val assignedId = caprice.id.ifBlank { "cap_ui_${savedCaprices.size + 1}" }
        savedCaprices.add(0, caprice.copy(id = assignedId))
        return Result.success(assignedId)
    }

    override suspend fun getCaprices(userId: String, limit: Int): Result<List<Caprice>> =
        Result.success(
            savedCaprices
                .filter { it.userId == userId }
                .sortedByDescending { it.timestamp }
                .take(limit)
        )

    override suspend fun updateFinalDecision(
        capriceId: String,
        decision: Int,
        decidedAt: Long
    ): Result<Unit> {
        val index = savedCaprices.indexOfFirst { it.id == capriceId }
        if (index >= 0) {
            savedCaprices[index] = savedCaprices[index].copy(
                finalDecision = decision,
                finalDecisionAt = decidedAt
            )
        }
        return Result.success(Unit)
    }
}

internal class FakeUiFriendshipRepository(
    initialFriendships: List<Friendship> = emptyList(),
    initialBlockedIds: List<String> = emptyList()
) : FriendshipRepository {
    private val friendshipsFlow = MutableStateFlow(initialFriendships)
    private val blockedIds = initialBlockedIds.toMutableList()

    override suspend fun searchUserByUsername(username: String): Result<UserProfile?> = Result.success(null)
    override suspend fun sendFriendRequest(friendship: Friendship): Result<Unit> = Result.success(Unit)
    override suspend fun updateFriendshipStatus(friendshipId: String, status: FriendshipStatus): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeFriendship(userId: String, friendId: String): Result<Unit> = Result.success(Unit)
    override suspend fun blockUser(blockerId: String, blockedUserId: String): Result<Unit> = Result.success(Unit)
    override suspend fun unblockUser(blockerId: String, blockedUserId: String): Result<Unit> {
        blockedIds.remove(blockedUserId)
        return Result.success(Unit)
    }

    override suspend fun getBlockedUserIds(userId: String): Result<List<String>> = Result.success(blockedIds.toList())
    override suspend fun isBlocked(userId: String, otherUserId: String): Result<Boolean> =
        Result.success(otherUserId in blockedIds)

    override suspend fun deleteFriendshipsForUser(userId: String): Result<Unit> = Result.success(Unit)
    override fun getFriendships(userId: String): Flow<List<Friendship>> =
        friendshipsFlow.map { list -> list.filter { userId in it.memberIDs } }
}

internal class FakeUiVoteRepository(
    votesByCaprice: Map<String, List<Vote>> = emptyMap()
) : VoteRepository {
    private val votesMap = votesByCaprice.mapValues { it.value.toMutableList() }.toMutableMap()

    override suspend fun castVote(vote: Vote): Result<Unit> {
        val list = votesMap.getOrPut(vote.capriceId) { mutableListOf() }
        list.add(vote)
        return Result.success(Unit)
    }

    override fun observeVotes(capriceId: String): Flow<List<Vote>> =
        flowOf(votesMap[capriceId].orEmpty().toList())

    override suspend fun getVotes(capriceId: String): Result<List<Vote>> =
        Result.success(votesMap[capriceId].orEmpty().toList())

    override suspend fun getUserVote(capriceId: String, voterId: String): Result<Vote?> =
        Result.success(votesMap[capriceId].orEmpty().firstOrNull { it.voterId == voterId })
}

internal class FakeUiLocationRepository(
    private val granted: Boolean = true,
    private val location: ApproximateLocation = ApproximateLocation(40.4168, -3.7038)
) : DeviceLocationRepository {
    override fun hasLocationPermission(): Boolean = granted
    override suspend fun currentApproximateLocation(): Result<ApproximateLocation> = Result.success(location)
}

internal object UiSmokeTestFactories {
    fun createCapriceViewModel(
        capriceRepo: CapriceRepository = FakeUiCapriceRepository(),
        locationRepo: DeviceLocationRepository = FakeUiLocationRepository(),
        profileRepo: UserProfileRepository = FakeUiUserProfileRepository(),
        currentUserId: String = "user_1",
        ioDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    ): CapriceViewModel = CapriceViewModel(
        saveCapriceUseCase = SaveCapriceUseCase(capriceRepo),
        getCapricesUseCase = GetCapricesUseCase(capriceRepo),
        locationRepository = locationRepo,
        currentUserIdProvider = { currentUserId },
        ioDispatcher = ioDispatcher,
        userProfileRepository = profileRepo
    )

    fun createActivePollViewModel(
        capriceRepo: CapriceRepository = FakeUiCapriceRepository(),
        voteRepo: VoteRepository = FakeUiVoteRepository()
    ): ActivePollViewModel = ActivePollViewModel(
        castVoteUseCase = CastVoteUseCase(voteRepo),
        observeVotesUseCase = ObserveVotesUseCase(voteRepo),
        calculateVerdictUseCase = CalculateVerdictUseCase(),
        voteRepository = voteRepo,
        updateFinalDecisionUseCase = UpdateFinalDecisionUseCase(capriceRepo)
    )

    fun createProfileViewModel(
        authRepo: AuthRepository,
        profileRepo: UserProfileRepository,
        capriceRepo: CapriceRepository,
        friendshipRepo: FriendshipRepository,
        voteRepo: VoteRepository = FakeUiVoteRepository()
    ): ProfileViewModel = ProfileViewModel(
        getCurrentUserUseCase = GetCurrentUserUseCase(authRepo),
        userProfileRepository = profileRepo,
        getBlockedUsersUseCase = GetBlockedUsersUseCase(friendshipRepo, profileRepo),
        unblockUserUseCase = UnblockUserUseCase(friendshipRepo),
        signOutUseCase = SignOutUseCase(authRepo),
        deleteAccountUseCase = DeleteAccountUseCase(authRepo, profileRepo, friendshipRepo, capriceRepo),
        getCapricesUseCase = GetCapricesUseCase(capriceRepo),
        getAcceptedFriendsUseCase = GetAcceptedFriendsUseCase(friendshipRepo, profileRepo),
        voteRepository = voteRepo,
        updateBioUseCase = UpdateBioUseCase(profileRepo),
        removeFriendUseCase = RemoveFriendUseCase(friendshipRepo),
        blockUserUseCase = BlockUserUseCase(friendshipRepo),
        updateProfilePhotoUseCase = UpdateProfilePhotoUseCase(profileRepo)
    )

    fun createAuthViewModel(
        authRepo: AuthRepository = FakeUiAuthRepository(),
        profileRepo: UserProfileRepository = FakeUiUserProfileRepository(),
        friendshipRepo: FriendshipRepository = FakeUiFriendshipRepository(),
        capriceRepo: CapriceRepository = FakeUiCapriceRepository()
    ): AuthViewModel = AuthViewModel(
        signInUseCase = SignInUseCase(authRepo),
        registerUseCase = RegisterUseCase(authRepo, profileRepo),
        signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepo, profileRepo),
        signOutUseCase = SignOutUseCase(authRepo),
        deleteAccountUseCase = DeleteAccountUseCase(authRepo, profileRepo, friendshipRepo, capriceRepo),
        getCurrentUserUseCase = GetCurrentUserUseCase(authRepo)
    )
}
