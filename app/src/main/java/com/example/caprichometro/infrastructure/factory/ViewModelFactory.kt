package com.example.caprichometro.infrastructure.factory

import android.content.Context
import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.GetFriendsCapricesUseCase
import com.example.caprichometro.application.usecase.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.ObserveFriendCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.UploadCapriceImageUseCase
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
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.infrastructure.auth.FirebaseAuthRepository
import com.example.caprichometro.infrastructure.data.Base64ImageStorageRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceDetailsRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceObservationRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceRepository
import com.example.caprichometro.infrastructure.data.FirestoreFriendshipRepository
import com.example.caprichometro.infrastructure.data.FirestoreUserProfileRepository
import com.example.caprichometro.infrastructure.data.FirestoreVoteRepository
import com.example.caprichometro.infrastructure.data.SharedPreferencesCapriceNotificationHistoryRepository
import com.example.caprichometro.infrastructure.ui.ActivePollViewModel
import com.example.caprichometro.infrastructure.ui.CapriceViewModel
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel

class ViewModelFactory(private val context: Context? = null) {
    private val capriceRepository by lazy { FirestoreCapriceRepository() }
    private val userProfileRepository by lazy { FirestoreUserProfileRepository() }
    private val authRepository by lazy { FirebaseAuthRepository() }
    private val friendshipRepository by lazy { FirestoreFriendshipRepository() }
    private val imageStorageRepository by lazy { Base64ImageStorageRepository() }
    private val capriceDetailsRepository by lazy { FirestoreCapriceDetailsRepository() }
    private val capriceObservationRepository by lazy { FirestoreCapriceObservationRepository() }
    private val voteRepository by lazy { FirestoreVoteRepository() }
    private val notificationHistoryRepository by lazy {
        SharedPreferencesCapriceNotificationHistoryRepository(context ?: throw IllegalStateException("Context required for notification history"))
    }

    private val acceptedFriendsUseCase by lazy {
        GetAcceptedFriendsUseCase(friendshipRepository, userProfileRepository)
    }

    fun createCapriceViewModel(): CapriceViewModel {
        val saveUseCase = SaveCapriceUseCase(capriceRepository)
        val getUseCase = GetCapricesUseCase(capriceRepository)
        val uploadImageUseCase = UploadCapriceImageUseCase(imageStorageRepository)
        val getFriendsCapricesUseCase = GetFriendsCapricesUseCase(acceptedFriendsUseCase, capriceRepository)
        return CapriceViewModel(saveUseCase, getUseCase, uploadImageUseCase, getFriendsCapricesUseCase)
    }

    fun createAuthViewModel(): AuthViewModel {
        return AuthViewModel(
            SignInUseCase(authRepository),
            RegisterUseCase(authRepository, userProfileRepository),
            SignInWithGoogleUseCase(authRepository, userProfileRepository),
            SignOutUseCase(authRepository),
            DeleteAccountUseCase(authRepository, userProfileRepository, friendshipRepository),
            GetCurrentUserUseCase(authRepository)
        )
    }

    fun createFriendshipViewModel(): FriendshipViewModel {
        return FriendshipViewModel(
            SearchUserByUsernameUseCase(friendshipRepository),
            SendFriendRequestUseCase(friendshipRepository),
            GetIncomingFriendRequestsUseCase(friendshipRepository, userProfileRepository),
            RespondToFriendRequestUseCase(friendshipRepository),
            acceptedFriendsUseCase,
            RemoveFriendUseCase(friendshipRepository),
            BlockUserUseCase(friendshipRepository),
            userProfileRepository
        )
    }

    fun createProfileViewModel(): ProfileViewModel {
        return ProfileViewModel(
            GetCurrentUserUseCase(authRepository),
            userProfileRepository,
            GetBlockedUsersUseCase(friendshipRepository, userProfileRepository),
            UnblockUserUseCase(friendshipRepository),
            SignOutUseCase(authRepository),
            DeleteAccountUseCase(authRepository, userProfileRepository, friendshipRepository),
            GetCapricesUseCase(capriceRepository),
            acceptedFriendsUseCase
        )
    }

    fun createActivePollViewModel(): ActivePollViewModel {
        return ActivePollViewModel(
            CastVoteUseCase(voteRepository),
            ObserveVotesUseCase(voteRepository),
            CalculateVerdictUseCase(),
            voteRepository
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
