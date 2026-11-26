package com.example.caprichometro.infrastructure.ui.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import com.example.caprichometro.infrastructure.factory.ViewModelFactory
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.security.AppCheckInstaller
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.caprice.CreateCapriceViewModel
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.home.HomeFeedViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.example.caprichometro.infrastructure.ui.voting.ActivePollViewModel
import com.example.caprichometro.infrastructure.util.AppLocaleManager

class MainActivity : ComponentActivity() {
    private var pendingIntentState = mutableStateOf<Intent?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa Firebase App Check (Debug en desarrollo, Play Integrity en release)
        AppCheckInstaller.install(this)

        // Crea el canal de notificaciones para los avisos de votación y amigos
        PollNotificationHelper.createNotificationChannel(this)

        if (savedInstanceState == null) {
            pendingIntentState.value = intent
        }

        val viewModelFactory = ViewModelFactory.getInstance(applicationContext)
        val homeFeedViewModel = ViewModelProvider(this, viewModelFactory)[HomeFeedViewModel::class.java]
        val createCapriceViewModel = ViewModelProvider(this, viewModelFactory)[CreateCapriceViewModel::class.java]
        val authViewModel = ViewModelProvider(this, viewModelFactory)[AuthViewModel::class.java]
        val friendshipViewModel = ViewModelProvider(this, viewModelFactory)[FriendshipViewModel::class.java]
        val profileViewModel = ViewModelProvider(this, viewModelFactory)[ProfileViewModel::class.java]
        val activePollViewModel = ViewModelProvider(this, viewModelFactory)[ActivePollViewModel::class.java]
        val sessionNavViewModel = ViewModelProvider(this)[SessionNavigationViewModel::class.java]
        if (savedInstanceState == null) {
            sessionNavViewModel.pendingIntent = intent
            pendingIntentState.value = intent
        } else if (pendingIntentState.value == null && sessionNavViewModel.pendingIntent != null) {
            pendingIntentState.value = sessionNavViewModel.pendingIntent
        }

        val observeFriendCapricesUseCase = viewModelFactory.createObserveFriendCapricesUseCase()
        val notifyFriendCapriceUseCase = viewModelFactory.createNotifyFriendCapriceUseCase()
        val getNotificationCapriceUseCase = viewModelFactory.createGetNotificationCapriceUseCase()

        setContent {
            CapriceApp(
                authViewModel = authViewModel,
                homeFeedViewModel = homeFeedViewModel,
                createCapriceViewModel = createCapriceViewModel,
                friendshipViewModel = friendshipViewModel,
                profileViewModel = profileViewModel,
                activePollViewModel = activePollViewModel,
                sessionNavViewModel = sessionNavViewModel,
                observeFriendCapricesUseCase = observeFriendCapricesUseCase,
                notifyFriendCapriceUseCase = notifyFriendCapriceUseCase,
                getNotificationCapriceUseCase = getNotificationCapriceUseCase,
                onClearSessionCaches = { viewModelFactory.invalidateSessionCaches() },
                pendingIntent = pendingIntentState.value,
                onClearPendingIntent = { pendingIntentState.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntentState.value = intent
    }
}
