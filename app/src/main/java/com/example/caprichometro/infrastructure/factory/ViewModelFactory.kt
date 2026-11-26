package com.example.caprichometro.infrastructure.factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.caprichometro.application.usecase.caprice.GetApproximatePlaceNameUseCase
import com.example.caprichometro.application.usecase.caprice.GetCapricesUseCase
import com.example.caprichometro.application.usecase.voting.GetFeedPollResultUseCase
import com.example.caprichometro.application.usecase.caprice.GetFriendsCapricesUseCase
import com.example.caprichometro.application.usecase.caprice.GetNearbyCapricesUseCase
import com.example.caprichometro.application.usecase.notification.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.notification.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.notification.ObserveFriendCapricesUseCase
import com.example.caprichometro.application.usecase.caprice.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.caprice.UploadCapriceImageUseCase
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.RegisterUseCase
import com.example.caprichometro.application.usecase.auth.SignInUseCase
import com.example.caprichometro.application.usecase.auth.SignInWithGoogleUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.application.usecase.friendship.BlockUserUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetBlockedUsersUseCase
import com.example.caprichometro.application.usecase.friendship.GetIncomingFriendRequestsUseCase
import com.example.caprichometro.application.usecase.friendship.RemoveFriendUseCase
import com.example.caprichometro.application.usecase.friendship.RespondToFriendRequestUseCase
import com.example.caprichometro.application.usecase.friendship.SearchUserByUsernameUseCase
import com.example.caprichometro.application.usecase.friendship.SendFriendRequestUseCase
import com.example.caprichometro.application.usecase.friendship.UnblockUserUseCase
import com.example.caprichometro.application.usecase.profile.UpdateBioUseCase
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.domain.repository.ApproximatePlaceNameRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.infrastructure.auth.FirebaseAuthRepository
import com.example.caprichometro.infrastructure.data.Base64ImageStorageRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceDetailsRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceMediaRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceObservationRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceRepository
import com.example.caprichometro.infrastructure.data.FirestoreFriendshipRepository
import com.example.caprichometro.infrastructure.data.FirestoreNearbyCapriceRepository
import com.example.caprichometro.infrastructure.data.FirestoreUserProfileRepository
import com.example.caprichometro.infrastructure.data.FirestoreVoteRepository
import com.example.caprichometro.infrastructure.data.SharedPreferencesCapriceNotificationHistoryRepository
import com.example.caprichometro.infrastructure.location.AndroidApproximatePlaceNameRepository
import com.example.caprichometro.infrastructure.location.AndroidLocationRepository
import com.example.caprichometro.infrastructure.ui.voting.ActivePollViewModel
import com.example.caprichometro.infrastructure.ui.caprice.CreateCapriceViewModel
import com.example.caprichometro.infrastructure.ui.home.HomeFeedViewModel
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.example.caprichometro.infrastructure.util.ImageModelHelper

class ViewModelFactory(context: Context? = null) : ViewModelProvider.Factory {
    private val appContext: Context? = context?.applicationContext
    private val capriceRepository by lazy { FirestoreCapriceRepository() }
    private val userProfileRepository by lazy { FirestoreUserProfileRepository() }
    private val authRepository by lazy { FirebaseAuthRepository() }
    private val friendshipRepository by lazy { FirestoreFriendshipRepository() }
    private val imageStorageRepository by lazy { Base64ImageStorageRepository() }
    private val capriceMediaRepository by lazy { FirestoreCapriceMediaRepository() }
    private val capriceDetailsRepository by lazy { FirestoreCapriceDetailsRepository(mediaRepository = capriceMediaRepository) }
    private val capriceObservationRepository by lazy { FirestoreCapriceObservationRepository() }
    private val nearbyCapriceRepository by lazy { FirestoreNearbyCapriceRepository() }
    private val voteRepository by lazy { FirestoreVoteRepository() }
    private val locationRepository: DeviceLocationRepository? by lazy {
        appContext?.let { AndroidLocationRepository(it) }
    }
    private val approximatePlaceNameRepository: ApproximatePlaceNameRepository? by lazy {
        appContext?.let { AndroidApproximatePlaceNameRepository(it) }
    }
    private val notificationHistoryRepository by lazy {
        SharedPreferencesCapriceNotificationHistoryRepository(appContext ?: throw IllegalStateException("Context required for notification history"))
    }

    private val acceptedFriendsUseCase by lazy {
        GetAcceptedFriendsUseCase(friendshipRepository, friendshipRepository, userProfileRepository)
    }

    fun createHomeFeedViewModel(): HomeFeedViewModel {
        val getUseCase = GetCapricesUseCase(capriceRepository)
        val getFriendsCapricesUseCase = GetFriendsCapricesUseCase(
            getAcceptedFriendsUseCase = acceptedFriendsUseCase,
            capriceRepository = capriceRepository,
            friendshipRepository = friendshipRepository,
            userProfileRepository = userProfileRepository,
            locationRepository = locationRepository
        )
        val getNearbyCapricesUseCase = locationRepository?.let { locRepo ->
            GetNearbyCapricesUseCase(
                locationRepository = locRepo,
                nearbyCapriceRepository = nearbyCapriceRepository,
                userProfileRepository = userProfileRepository,
                friendshipRepository = friendshipRepository
            )
        }
        return HomeFeedViewModel(
            getCapricesUseCase = getUseCase,
            getFriendsCapricesUseCase = getFriendsCapricesUseCase,
            profileCacheControl = userProfileRepository,
            capriceCacheControl = capriceRepository,
            getNearbyCapricesUseCase = getNearbyCapricesUseCase,
            locationRepository = locationRepository,
            userProfileRepository = userProfileRepository,
            getFeedPollResultUseCase = GetFeedPollResultUseCase(voteRepository, userProfileRepository),
            capriceMediaRepository = capriceMediaRepository
        )
    }

    fun createCreateCapriceViewModel(): CreateCapriceViewModel {
        return CreateCapriceViewModel(
            saveCapriceUseCase = SaveCapriceUseCase(capriceRepository),
            uploadCapriceImageUseCase = UploadCapriceImageUseCase(imageStorageRepository),
            locationRepository = locationRepository,
            userProfileRepository = userProfileRepository
        )
    }

    fun createAuthViewModel(): AuthViewModel {
        return AuthViewModel(
            SignInUseCase(authRepository),
            RegisterUseCase(authRepository, userProfileRepository),
            SignInWithGoogleUseCase(authRepository, userProfileRepository),
            SignOutUseCase(authRepository),
            DeleteAccountUseCase(authRepository, userProfileRepository, friendshipRepository, capriceRepository),
            GetCurrentUserUseCase(authRepository)
        )
    }

    fun createFriendshipViewModel(): FriendshipViewModel {
        return FriendshipViewModel(
            SearchUserByUsernameUseCase(friendshipRepository),
            SendFriendRequestUseCase(friendshipRepository),
            GetIncomingFriendRequestsUseCase(friendshipRepository, friendshipRepository, userProfileRepository),
            RespondToFriendRequestUseCase(friendshipRepository),
            acceptedFriendsUseCase,
            RemoveFriendUseCase(friendshipRepository),
            BlockUserUseCase(friendshipRepository),
            userProfileRepository
        )
    }

    fun createProfileViewModel(): ProfileViewModel {
        return ProfileViewModel(
            getCurrentUserUseCase = GetCurrentUserUseCase(authRepository),
            userProfileRepository = userProfileRepository,
            getBlockedUsersUseCase = GetBlockedUsersUseCase(friendshipRepository, userProfileRepository),
            unblockUserUseCase = UnblockUserUseCase(friendshipRepository),
            signOutUseCase = SignOutUseCase(authRepository),
            deleteAccountUseCase = DeleteAccountUseCase(authRepository, userProfileRepository, friendshipRepository, capriceRepository),
            getCapricesUseCase = GetCapricesUseCase(capriceRepository),
            getAcceptedFriendsUseCase = acceptedFriendsUseCase,
            voteRepository = voteRepository,
            updateBioUseCase = UpdateBioUseCase(userProfileRepository),
            removeFriendUseCase = RemoveFriendUseCase(friendshipRepository),
            blockUserUseCase = BlockUserUseCase(friendshipRepository),
            capriceCacheControl = capriceRepository,
            capriceMediaRepository = capriceMediaRepository
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeFeedViewModel::class.java) -> createHomeFeedViewModel() as T
            modelClass.isAssignableFrom(CreateCapriceViewModel::class.java) -> createCreateCapriceViewModel() as T
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> createAuthViewModel() as T
            modelClass.isAssignableFrom(FriendshipViewModel::class.java) -> createFriendshipViewModel() as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> createProfileViewModel() as T
            modelClass.isAssignableFrom(ActivePollViewModel::class.java) -> createActivePollViewModel() as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    fun invalidateSessionCaches() {
        userProfileRepository.invalidateAll()
        capriceRepository.invalidateAll()
        capriceMediaRepository.clearCache()
        ImageModelHelper.clearCache()
    }

    companion object {
        @Volatile
        private var sharedInstance: ViewModelFactory? = null

        fun getInstance(context: Context): ViewModelFactory {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: ViewModelFactory(context.applicationContext).also { sharedInstance = it }
            }
        }
    }

    fun createActivePollViewModel(): ActivePollViewModel {
        val placeNameUseCase = approximatePlaceNameRepository?.let {
            GetApproximatePlaceNameUseCase(it)
        }
        return ActivePollViewModel(
            castVoteUseCase = CastVoteUseCase(voteRepository, friendshipRepository),
            observeVotesUseCase = ObserveVotesUseCase(voteRepository),
            calculateVerdictUseCase = CalculateVerdictUseCase(),
            voteRepository = voteRepository,
            updateFinalDecisionUseCase = com.example.caprichometro.application.usecase.caprice.UpdateFinalDecisionUseCase(capriceRepository),
            getApproximatePlaceNameUseCase = placeNameUseCase,
            userProfileRepository = userProfileRepository,
            capriceDetailsRepository = capriceDetailsRepository
        )
    }

    fun createObserveFriendCapricesUseCase(): ObserveFriendCapricesUseCase {
        return ObserveFriendCapricesUseCase(acceptedFriendsUseCase, capriceObservationRepository)
    }

    fun createNotifyFriendCapriceUseCase(): NotifyFriendCapriceUseCase {
        return NotifyFriendCapriceUseCase(acceptedFriendsUseCase, capriceRepository, notificationHistoryRepository)
    }

    fun createGetNotificationCapriceUseCase(): GetNotificationCapriceUseCase {
        return GetNotificationCapriceUseCase(capriceDetailsRepository, userProfileRepository, acceptedFriendsUseCase)
    }
}
