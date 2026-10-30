package com.example.caprichometro.infrastructure.factory

import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.SaveCapriceUseCase
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.RegisterUseCase
import com.example.caprichometro.application.usecase.auth.SignInUseCase
import com.example.caprichometro.application.usecase.auth.SignInWithGoogleUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetIncomingFriendRequestsUseCase
import com.example.caprichometro.application.usecase.friendship.RespondToFriendRequestUseCase
import com.example.caprichometro.application.usecase.friendship.SearchUserByUsernameUseCase
import com.example.caprichometro.application.usecase.friendship.SendFriendRequestUseCase
import com.example.caprichometro.infrastructure.auth.FirebaseAuthRepository
import com.example.caprichometro.infrastructure.data.FirestoreCapriceRepository
import com.example.caprichometro.infrastructure.data.FirestoreFriendshipRepository
import com.example.caprichometro.infrastructure.data.FirestoreUserProfileRepository
import com.example.caprichometro.infrastructure.ui.CapriceViewModel
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel

class ViewModelFactory {
    private val capriceRepository by lazy { FirestoreCapriceRepository() }
    private val userProfileRepository by lazy { FirestoreUserProfileRepository() }
    private val authRepository by lazy { FirebaseAuthRepository() }
    private val friendshipRepository by lazy { FirestoreFriendshipRepository() }

    fun createCapriceViewModel(): CapriceViewModel {
        val saveUseCase = SaveCapriceUseCase(capriceRepository)
        val getUseCase = GetCapricesUseCase(capriceRepository)
        return CapriceViewModel(saveUseCase, getUseCase)
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
            GetAcceptedFriendsUseCase(friendshipRepository, userProfileRepository)
        )
    }
}