package com.example.caprichometro.infrastructure.ui.app

import com.example.caprichometro.infrastructure.ui.caprice.CapriceScreen
import com.example.caprichometro.infrastructure.ui.caprice.CreateCapriceViewModel
import com.example.caprichometro.infrastructure.ui.home.HomeFeedState
import com.example.caprichometro.infrastructure.ui.home.HomeFeedTab
import com.example.caprichometro.infrastructure.ui.home.HomeFeedViewModel
import com.example.caprichometro.infrastructure.ui.home.HomeScreen
import com.example.caprichometro.infrastructure.ui.voting.ActivePollScreen
import com.example.caprichometro.infrastructure.ui.voting.ActivePollViewModel
import com.example.caprichometro.infrastructure.ui.voting.ResultScreen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.caprichometro.R
import com.example.caprichometro.application.usecase.notification.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.notification.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.notification.ObserveFriendCapricesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.InviteDeepLink
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.factory.ViewModelFactory
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.security.AppCheckInstaller
import com.example.caprichometro.infrastructure.ui.auth.AuthScreen
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.components.OfflineStatusBanner
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.infrastructure.ui.friendship.AddFriendScreen
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileScreen
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.example.caprichometro.infrastructure.util.AppLocaleManager
import com.example.caprichometro.infrastructure.util.NetworkConnectivityMonitor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val NavBarBg = Color(0xFF0D0D0D)
private val NavDivider = Color(0xFF222224)
private val ActiveLime = Color(0xFFB8E636)
private val InactiveIcon = Color(0xFF7C7C80)

class SessionNavigationViewModel : ViewModel() {
    var bootstrappedUserId: String? = null
    var selectedFeedItem by mutableStateOf<CapriceFeedItem?>(null)
    var selectedProfileCaprice by mutableStateOf<Caprice?>(null)
    var selectedPublicAuthor by mutableStateOf<UserProfile?>(null)

    fun reset() {
        bootstrappedUserId = null
        selectedFeedItem = null
        selectedProfileCaprice = null
        selectedPublicAuthor = null
    }
    var pendingIntent: Intent? = null
}

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

@Composable
fun CapriceApp(
    authViewModel: AuthViewModel,
    homeFeedViewModel: HomeFeedViewModel,
    createCapriceViewModel: CreateCapriceViewModel,
    friendshipViewModel: FriendshipViewModel,
    profileViewModel: ProfileViewModel,
    activePollViewModel: ActivePollViewModel,
    sessionNavViewModel: SessionNavigationViewModel,
    observeFriendCapricesUseCase: ObserveFriendCapricesUseCase,
    notifyFriendCapriceUseCase: NotifyFriendCapriceUseCase,
    getNotificationCapriceUseCase: GetNotificationCapriceUseCase,
    onClearSessionCaches: () -> Unit = {},
    pendingIntent: Intent? = null,
    onClearPendingIntent: () -> Unit = {}
) {
    val authState by authViewModel.state.collectAsState()
    val capriceState by homeFeedViewModel.state.collectAsState()
    val friendshipState by friendshipViewModel.state.collectAsState()
    val profileState by profileViewModel.state.collectAsState()
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var currentUserId by remember { mutableStateOf(firebaseAuth.currentUser?.uid ?: "") }
    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUserId = auth.currentUser?.uid ?: ""
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }
    val ownUserProfile = profileState.profile?.takeIf { it.uid == currentUserId }
        ?: friendshipState.currentUserProfile?.takeIf { it.uid == currentUserId }

    LaunchedEffect(currentUserId, friendshipState.hasLoadedFriends, friendshipState.friends) {
        homeFeedViewModel.updateSessionFriends(
            if (currentUserId.isNotBlank() && friendshipState.hasLoadedFriends) {
                friendshipState.friends
            } else {
                null
            }
        )
    }

    // Solicitud del permiso de notificaciones en tiempo de ejecución (Android 13+ / API 33+)
    val context = LocalContext.current
    val connectivityMonitor = remember(context) { NetworkConnectivityMonitor(context) }
    DisposableEffect(connectivityMonitor) {
        onDispose { connectivityMonitor.unregister() }
    }
    val isOnline by connectivityMonitor.isOnline.collectAsState()
    var wasOffline by remember { mutableStateOf(!isOnline) }
    DisposableEffect(context, authState.isAuthSuccessful) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && authState.isAuthSuccessful) {
                    if (connectivityMonitor.refresh()) {
                        activePollViewModel.retryPendingVoteIfAny()
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    var isNotificationPromptPending by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        isNotificationPromptPending = false
    }

    LaunchedEffect(authState.isAuthSuccessful) {
        if (authState.isAuthSuccessful && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                isNotificationPromptPending = true
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                isNotificationPromptPending = false
            }
        } else if (!authState.isAuthSuccessful) {
            isNotificationPromptPending = false
        }
    }

    var currentScreen by rememberSaveable { mutableStateOf("home") }
    var viewingProfileUserId by rememberSaveable { mutableStateOf("") }
    var previousScreenBeforeProfile by rememberSaveable { mutableStateOf("home") }
    var previousScreenBeforeAddFriend by rememberSaveable { mutableStateOf("profile") }
    var previousScreenBeforeActive by rememberSaveable { mutableStateOf("create") }
    var selectedFeedItem by remember { mutableStateOf(sessionNavViewModel.selectedFeedItem) }
    var selectedProfileCaprice by remember { mutableStateOf(sessionNavViewModel.selectedProfileCaprice) }
    var selectedPublicAuthor by remember { mutableStateOf(sessionNavViewModel.selectedPublicAuthor) }
    LaunchedEffect(selectedFeedItem, selectedProfileCaprice, selectedPublicAuthor) {
        sessionNavViewModel.selectedFeedItem = selectedFeedItem
        sessionNavViewModel.selectedProfileCaprice = selectedProfileCaprice
        sessionNavViewModel.selectedPublicAuthor = selectedPublicAuthor
    }

    val openProfile: (String, String) -> Unit = { authorId, fromScreen ->
        if (authorId.isNotBlank()) {
            viewingProfileUserId = authorId
            previousScreenBeforeProfile = fromScreen
            currentScreen = "profile"
        }
    }

    BackHandler(enabled = authState.isAuthSuccessful && (selectedPublicAuthor != null || currentScreen != "home")) {
        if (selectedPublicAuthor != null) {
            selectedPublicAuthor = null
            return@BackHandler
        }
        when (currentScreen) {
            "friend_poll" -> {
                selectedFeedItem = null
                currentScreen = "home"
            }
            "create" -> currentScreen = "home"
            "active" -> {
                selectedProfileCaprice = null
                currentScreen = when (previousScreenBeforeActive) {
                    "home" -> "home"
                    "profile" -> "profile"
                    else -> "create"
                }
            }
            "result" -> {
                val capriceToShow = selectedProfileCaprice ?: capriceState.latestCaprices.firstOrNull()
                val wasFromProfile = previousScreenBeforeActive == "profile" &&
                    selectedProfileCaprice != null &&
                    selectedFeedItem?.caprice?.id != capriceToShow?.id
                selectedProfileCaprice = null
                selectedFeedItem = null
                currentScreen = if (wasFromProfile) "profile" else "home"
            }
            "add_friend" -> {
                if (previousScreenBeforeAddFriend == "profile") {
                    viewingProfileUserId = currentUserId
                }
                currentScreen = previousScreenBeforeAddFriend
            }
            "profile" -> {
                if (viewingProfileUserId != currentUserId && viewingProfileUserId.isNotBlank() && previousScreenBeforeProfile != "profile") {
                    viewingProfileUserId = currentUserId
                    currentScreen = previousScreenBeforeProfile
                } else if (previousScreenBeforeProfile == "add_friend") {
                    viewingProfileUserId = currentUserId
                    currentScreen = "add_friend"
                } else {
                    currentScreen = "home"
                }
            }
        }
    }
    val canOpenAuthorProfileFor: (String?, Boolean) -> Boolean = { authorId, feedCanOpen ->
        feedCanOpen ||
            (!authorId.isNullOrBlank() && (
                authorId == currentUserId ||
                    friendshipState.friends.any { it.uid == authorId }
                ))
    }
    val resolveAuthorForCaprice: (Caprice?, CapriceFeedItem?) -> UserProfile? = { targetCaprice, feedItem ->
        when {
            targetCaprice == null -> null
            targetCaprice.userId == currentUserId -> ownUserProfile
            feedItem?.caprice?.id == targetCaprice.id -> feedItem.author
            profileState.profile?.uid == targetCaprice.userId -> profileState.profile
            else -> friendshipState.friends.firstOrNull { it.uid == targetCaprice.userId }
        }
    }

    // Intercepta enlaces de invitación (Deep Links) y notificaciones pulsadas
    LaunchedEffect(pendingIntent, authState.isAuthSuccessful, currentUserId) {
        val targetIntent = pendingIntent
        if (targetIntent != null && authState.isAuthSuccessful && currentUserId.isNotBlank()) {
            val invitedUsername = InviteDeepLink.extractUsername(targetIntent.dataString)
            if (invitedUsername != null) {
                if (currentScreen != "add_friend") {
                    previousScreenBeforeAddFriend = currentScreen
                }
                currentScreen = "add_friend"
                friendshipViewModel.openInviteForUsername(invitedUsername)
                sessionNavViewModel.pendingIntent = null
                onClearPendingIntent()
                return@LaunchedEffect
            }

            val dataUri = targetIntent.data
            val isInviteUri = dataUri?.scheme == "caprichometro" && dataUri.host == "invite" ||
                dataUri?.pathSegments?.contains("invite") == true
            if (isInviteUri) {
                if (currentScreen != "add_friend") {
                    previousScreenBeforeAddFriend = currentScreen
                }
                friendshipViewModel.clearSearchState()
                currentScreen = "add_friend"
                sessionNavViewModel.pendingIntent = null
                onClearPendingIntent()
                return@LaunchedEffect
            }
            val capriceId = targetIntent.getStringExtra(PollNotificationHelper.EXTRA_CAPRICE_ID)
                ?: dataUri?.lastPathSegment
            val navigateTo = targetIntent.getStringExtra(PollNotificationHelper.EXTRA_NAVIGATE_TO)
            val extraRecipientUserId = targetIntent.getStringExtra(PollNotificationHelper.EXTRA_RECIPIENT_USER_ID)
                ?.takeIf { it.isNotBlank() }
            if (extraRecipientUserId != null && extraRecipientUserId != currentUserId) {
                onClearPendingIntent()
                return@LaunchedEffect
            }
            val recipientUserId = extraRecipientUserId ?: currentUserId

            if (!capriceId.isNullOrBlank()) {
                val result = getNotificationCapriceUseCase.execute(capriceId, recipientUserId)
                val item = result.getOrNull()
                if (item != null) {
                    if (item.caprice.userId == currentUserId) {
                        viewingProfileUserId = currentUserId
                        selectedProfileCaprice = item.caprice
                        currentScreen = if (navigateTo == "result" || PollTiming.isPollExpired(item.caprice.timestamp)) {
                            "result"
                        } else {
                            "active"
                        }
                    } else {
                        if (navigateTo == "result") {
                            selectedFeedItem = item
                            selectedProfileCaprice = null
                            currentScreen = "result"
                        } else {
                            selectedFeedItem = item
                            currentScreen = "friend_poll"
                        }
                    }
                }
            } else if (navigateTo == "result") {
                currentScreen = "result"
            } else if (navigateTo == "active") {
                currentScreen = "active"
            }
            sessionNavViewModel.pendingIntent = null
            onClearPendingIntent()
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen !in setOf("friend_poll", "active", "result")) {
            activePollViewModel.reset()
        }
    }

    // Escuchamos solicitudes pendientes, amigos, feed y notificaciones de amigos al iniciar sesión
    LaunchedEffect(authState.isAuthSuccessful, currentUserId) {
        if (authState.isAuthSuccessful && currentUserId.isBlank() && firebaseAuth.currentUser == null) {
            androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
            capriceState.latestCaprices.forEach { caprice ->
                PollNotificationScheduler.cancelPollExpiration(context, caprice)
            }
            sessionNavViewModel.reset()
            onClearSessionCaches()
            profileViewModel.reset()
            friendshipViewModel.stopListening()
            activePollViewModel.reset()
            homeFeedViewModel.reset()
            createCapriceViewModel.reset()
            authViewModel.clearSessionState()
            return@LaunchedEffect
        }
        if (authState.isAuthSuccessful && currentUserId.isNotBlank()) {
            val isNewSessionBootstrap = sessionNavViewModel.bootstrappedUserId != currentUserId
            if (isNewSessionBootstrap) {
                sessionNavViewModel.bootstrappedUserId = currentUserId
                profileViewModel.reset()
            }
            val startupStart = System.currentTimeMillis()
            val foregroundStart = startupStart

            // 1. Priorizamos al 100% la carga inicial del feed para evitar contención en el arranque en frío
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            homeFeedViewModel.onLocationPermissionChanged(hasLocationPermission)
            createCapriceViewModel.onLocationPermissionChanged(hasLocationPermission)
            if (isNewSessionBootstrap) {
                homeFeedViewModel.loadFriendsFeed()
            } else {
                homeFeedViewModel.loadFriendsFeedIfNeeded()
            }

            // 2. Diferimos escuchas secundarias, notificaciones y perfil hasta que el feed inicial haya respondido
            launch {
                withTimeoutOrNull(4_000L) {
                    homeFeedViewModel.state.first { it.hasLoadedFeed || it.feedError != null }
                }
                android.util.Log.d(
                    "FeedPerf",
                    "Startup gate liberado en ${System.currentTimeMillis() - startupStart} ms"
                )

                friendshipViewModel.listenToIncomingRequests(currentUserId)
                if (isNewSessionBootstrap) {
                    homeFeedViewModel.fetchUserCaprices(limit = 50)

                    // Recuperación al volver a la app: último capricho no notificado y decisiones finales pendientes
                    launch {
                        notifyFriendCapriceUseCase.checkAndNotifyUnnotifiedFriendCaprices(currentUserId) { author, caprice ->
                            PollNotificationHelper.showFriendCapriceNotification(
                                context = context,
                                recipientUserId = currentUserId,
                                authorUsername = author.username,
                                caprice = caprice
                            )
                        }
                    }
                    launch {
                        notifyFriendCapriceUseCase.checkAndNotifyFriendFinalDecisions(currentUserId) { author, caprice ->
                            PollNotificationHelper.showFriendFinalDecisionNotification(
                                context = context,
                                recipientUserId = currentUserId,
                                authorUsername = author.username,
                                caprice = caprice
                            )
                        }
                    }
                }

                // Única escucha en vivo por amigo (caprichos nuevos + decisiones finales en 1 listener)
                launch {
                    observeFriendCapricesUseCase.observeAllUpdates(currentUserId, foregroundStart)
                        .catch { e ->
                            android.util.Log.w("MainActivity", "Error observando novedades de amigos: ${e.message}")
                        }
                        .collect { update ->
                            val item = update.feedItem
                            if (update.isFinalDecision) {
                                val decidedAt = item.caprice.finalDecisionAt ?: item.caprice.timestamp
                                notifyFriendCapriceUseCase.recordDecisionNotification(
                                    recipientUserId = currentUserId,
                                    friendUserId = item.author.uid,
                                    capriceId = item.caprice.id,
                                    timestamp = decidedAt
                                )
                                PollNotificationHelper.showFriendFinalDecisionNotification(
                                    context = context,
                                    recipientUserId = currentUserId,
                                    authorUsername = item.author.username,
                                    caprice = item.caprice
                                )
                            } else {
                                notifyFriendCapriceUseCase.recordNotification(
                                    recipientUserId = currentUserId,
                                    friendUserId = item.author.uid,
                                    capriceId = item.caprice.id,
                                    timestamp = item.caprice.timestamp
                                )
                                PollNotificationHelper.showFriendCapriceNotification(
                                    context = context,
                                    recipientUserId = currentUserId,
                                    authorUsername = item.author.username,
                                    caprice = item.caprice
                                )
                            }
                            // Recargamos el feed social invalidando caché para reflejar el nuevo capricho o decisión final
                            homeFeedViewModel.loadFriendsFeed(isManualRefresh = true)
                        }
                }
            }
        } else {
            sessionNavViewModel.reset()
            onClearSessionCaches()
            profileViewModel.reset()
            friendshipViewModel.stopListening()
            activePollViewModel.reset()
            homeFeedViewModel.reset()
            createCapriceViewModel.reset()
            selectedFeedItem = null
            selectedProfileCaprice = null
            selectedPublicAuthor = null
            viewingProfileUserId = ""
            previousScreenBeforeProfile = "home"
            previousScreenBeforeAddFriend = "profile"
            previousScreenBeforeActive = "create"
            currentScreen = "home"
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NavBarBg
        ) {
            if (!authState.isAuthSuccessful) {
                AuthScreen(
                    viewModel = authViewModel,
                    onNavigateToHome = {}
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val triggerReconnectSync: () -> Unit = {
                        homeFeedViewModel.loadFriendsFeed(isManualRefresh = true)
                        if (capriceState.selectedHomeTab == HomeFeedTab.GENERAL || capriceState.nearbyFeedItems.isNotEmpty()) {
                            homeFeedViewModel.loadNearbyFeed(isManualRefresh = true)
                        }
                        if (currentScreen == "profile" && currentUserId.isNotBlank()) {
                            val profileTarget = viewingProfileUserId.ifBlank { currentUserId }
                            profileViewModel.loadProfile(profileTarget, currentUserId)
                        }
                        activePollViewModel.retryPendingVoteIfAny()
                    }
                    LaunchedEffect(isOnline, authState.isAuthSuccessful) {
                        if (!isOnline) {
                            wasOffline = true
                        } else if (wasOffline && authState.isAuthSuccessful) {
                            wasOffline = false
                            triggerReconnectSync()
                        } else {
                            wasOffline = false
                        }
                    }
                    OfflineStatusBanner(
                        isOffline = !isOnline,
                        onRetry = {
                            if (connectivityMonitor.refresh()) {
                                triggerReconnectSync()
                            }
                        }
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
                            "home" -> {
                                HomeScreen(
                                    viewModel = homeFeedViewModel,
                                    onSelectFeedItem = { item ->
                                        selectedFeedItem = item
                                        currentScreen = "friend_poll"
                                    },
                                    onNavigateToFriends = {
                                        previousScreenBeforeAddFriend = "home"
                                        currentScreen = "add_friend"
                                    },
                                    deferLocationAutoPrompt = isNotificationPromptPending,
                                    onNavigateToUserProfile = { authorId ->
                                        openProfile(authorId, "home")
                                    },
                                    onOpenNonFriendProfile = { author ->
                                        selectedPublicAuthor = author
                                    },
                                    ownUserProfile = ownUserProfile,
                                    pendingFriendRequestsCount = friendshipState.incomingRequests.size,
                                    onOwnAvatarClick = {
                                        val activeOwnCaprice = capriceState.latestCaprices.firstOrNull {
                                            !PollTiming.isPollExpired(it.timestamp)
                                        }
                                        if (activeOwnCaprice != null) {
                                            selectedFeedItem = null
                                            selectedProfileCaprice = activeOwnCaprice
                                            previousScreenBeforeActive = "home"
                                            currentScreen = "active"
                                        } else {
                                            openProfile(currentUserId, "home")
                                        }
                                    },
                                    onOwnAvatarLongClick = {
                                        openProfile(currentUserId, "home")
                                    }
                                )
                            }
                            "friend_poll" -> {
                                val item = selectedFeedItem
                                if (item == null) {
                                    LaunchedEffect(Unit) { currentScreen = "home" }
                                } else {
                                    val pollAuthor = resolveAuthorForCaprice(item.caprice, item) ?: item.author
                                    val canOpenProfile = canOpenAuthorProfileFor(
                                        item.caprice.userId,
                                        item.canOpenAuthorProfile
                                    )
                                    ActivePollScreen(
                                        caprice = item.caprice,
                                        authorUsername = pollAuthor?.username,
                                        authorPhotoUrl = pollAuthor?.photoUrl,
                                        authorLocationCell = pollAuthor?.locationCell,
                                        viewModel = activePollViewModel,
                                        currentUserId = currentUserId,
                                        onNavigateBack = { currentScreen = "home" },
                                        onTimerFinished = {
                                            selectedProfileCaprice = item.caprice
                                            currentScreen = "result"
                                        },
                                        onNavigateToAuthorProfile = { authorId ->
                                            openProfile(authorId, "friend_poll")
                                        },
                                        onOpenPublicAuthorInfo = pollAuthor?.let { author ->
                                            { selectedPublicAuthor = author }
                                        },
                                        canOpenAuthorProfileOverride = canOpenProfile,
                                        approximateDistanceMeters = item.approximateDistanceMeters
                                    )
                                }
                            }
                            "create" -> {
                                CapriceScreen(
                                    viewModel = createCapriceViewModel,
                                    onCapriceCreated = { saved ->
                                        homeFeedViewModel.onCapriceCreated(saved)
                                        saved.locationCell?.let { cell -> homeFeedViewModel.onLocationCellResolved(cell) }
                                    },
                                    friends = friendshipState.friends,
                                    onNavigateToActivePoll = {
                                        selectedProfileCaprice = null
                                        previousScreenBeforeActive = "create"
                                        currentScreen = "active"
                                    }
                                )
                            }
                            "active" -> {
                                val capriceToShow = selectedProfileCaprice ?: capriceState.latestCaprices.firstOrNull()
                                val activeAuthor = resolveAuthorForCaprice(capriceToShow, selectedFeedItem)
                                val canOpenProfile = canOpenAuthorProfileFor(
                                    capriceToShow?.userId,
                                    if (selectedFeedItem?.caprice?.id == capriceToShow?.id) {
                                        selectedFeedItem?.canOpenAuthorProfile != false
                                    } else {
                                        true
                                    }
                                )
                                ActivePollScreen(
                                    caprice = capriceToShow,
                                    authorUsername = activeAuthor?.username,
                                    authorPhotoUrl = activeAuthor?.photoUrl,
                                    authorLocationCell = activeAuthor?.locationCell,
                                    viewModel = activePollViewModel,
                                    currentUserId = currentUserId,
                                    onNavigateBack = {
                                        selectedProfileCaprice = null
                                        currentScreen = when (previousScreenBeforeActive) {
                                            "home" -> "home"
                                            "profile" -> "profile"
                                            else -> "create"
                                        }
                                    },
                                    onTimerFinished = { currentScreen = "result" },
                                    onNavigateToAuthorProfile = { authorId ->
                                        openProfile(authorId, "active")
                                    },
                                    onOpenPublicAuthorInfo = activeAuthor?.let { author ->
                                        { selectedPublicAuthor = author }
                                    },
                                    canOpenAuthorProfileOverride = canOpenProfile,
                                    approximateDistanceMeters = if (selectedFeedItem?.caprice?.id == capriceToShow?.id) {
                                        selectedFeedItem?.approximateDistanceMeters
                                    } else {
                                        null
                                    }
                                )
                            }
                            "result" -> {
                                val capriceToShow = selectedProfileCaprice
                                    ?: selectedFeedItem?.caprice
                                    ?: capriceState.latestCaprices.firstOrNull()
                                val resultAuthor = resolveAuthorForCaprice(capriceToShow, selectedFeedItem)
                                val canOpenProfile = canOpenAuthorProfileFor(
                                    capriceToShow?.userId,
                                    if (selectedFeedItem?.caprice?.id == capriceToShow?.id) {
                                        selectedFeedItem?.canOpenAuthorProfile != false
                                    } else {
                                        true
                                    }
                                )
                                ResultScreen(
                                    caprice = capriceToShow,
                                    viewModel = activePollViewModel,
                                    currentUserId = currentUserId,
                                    authorUsername = resultAuthor?.username,
                                    authorPhotoUrl = resultAuthor?.photoUrl,
                                    authorLocationCell = resultAuthor?.locationCell,
                                    onCapriceUpdated = { updated ->
                                        selectedProfileCaprice = updated
                                        val decision = updated.finalDecision
                                        val decidedAt = updated.finalDecisionAt
                                        if (decision != null && decidedAt != null) {
                                            profileViewModel.markFinalDecision(updated.id, decision, decidedAt)
                                            homeFeedViewModel.markFinalDecision(updated.id, decision, decidedAt)
                                        }
                                    },
                                    onGoHome = {
                                        val wasFromProfile = previousScreenBeforeActive == "profile" &&
                                            selectedProfileCaprice != null &&
                                            selectedFeedItem?.caprice?.id != capriceToShow?.id
                                        selectedProfileCaprice = null
                                        selectedFeedItem = null
                                        currentScreen = if (wasFromProfile) "profile" else "home"
                                    },
                                    onNavigateToAuthorProfile = { authorId ->
                                        openProfile(authorId, "result")
                                    },
                                    onOpenPublicAuthorInfo = resultAuthor?.let { author ->
                                        { selectedPublicAuthor = author }
                                    },
                                    canOpenAuthorProfileOverride = canOpenProfile,
                                    approximateDistanceMeters = if (selectedFeedItem?.caprice?.id == capriceToShow?.id) {
                                        selectedFeedItem?.approximateDistanceMeters
                                    } else {
                                        null
                                    }
                                )
                            }
                            "add_friend" -> {
                                AddFriendScreen(
                                    viewModel = friendshipViewModel,
                                    currentUserId = currentUserId,
                                    onBack = {
                                        if (previousScreenBeforeAddFriend == "profile") {
                                            viewingProfileUserId = currentUserId
                                        }
                                        currentScreen = previousScreenBeforeAddFriend
                                    },
                                    onOpenOwnProfile = {
                                        openProfile(currentUserId, "add_friend")
                                    },
                                    onViewProfile = { friendUserId ->
                                        openProfile(friendUserId, "add_friend")
                                    }
                                )
                            }
                            "profile" -> {
                                val targetId = if (viewingProfileUserId.isNotBlank()) viewingProfileUserId else currentUserId
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    targetUserId = targetId,
                                    currentUserId = currentUserId,
                                    onNavigateToFriends = {
                                        previousScreenBeforeAddFriend = "profile"
                                        currentScreen = "add_friend"
                                    },
                                    onSelectCaprice = { caprice ->
                                        selectedProfileCaprice = caprice
                                        previousScreenBeforeActive = "profile"
                                        val isExpired = PollTiming.isPollExpired(caprice.timestamp)
                                        currentScreen = if (isExpired) "result" else "active"
                                    },
                                    onBack = {
                                        if (viewingProfileUserId != currentUserId && viewingProfileUserId.isNotBlank() && previousScreenBeforeProfile != "profile") {
                                            viewingProfileUserId = currentUserId
                                            currentScreen = previousScreenBeforeProfile
                                        } else if (previousScreenBeforeProfile == "add_friend") {
                                            viewingProfileUserId = currentUserId
                                            currentScreen = "add_friend"
                                        } else {
                                            currentScreen = "home"
                                        }
                                    },
                                    onSignedOut = {
                                        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
                                        capriceState.latestCaprices.forEach { caprice ->
                                            PollNotificationScheduler.cancelPollExpiration(context, caprice)
                                        }
                                        sessionNavViewModel.reset()
                                        onClearSessionCaches()
                                        profileViewModel.reset()
                                        friendshipViewModel.stopListening()
                                        activePollViewModel.reset()
                                        homeFeedViewModel.reset()
            createCapriceViewModel.reset()
                                        authViewModel.clearSessionState()
                                        viewingProfileUserId = ""
                                        previousScreenBeforeProfile = "home"
                                        previousScreenBeforeActive = "create"
                                        selectedFeedItem = null
                                        selectedProfileCaprice = null
                                        selectedPublicAuthor = null
                                        currentScreen = "home"
                                    }
                                )
                            }
                        }
                    }

                    // Barra de navegación inferior (Home | Cámara | Perfil)
                    HorizontalDivider(color = NavDivider, thickness = 0.8.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavBarBg)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pestaña 1: Home / Feed
                        val isHomeSelected = currentScreen == "home" || currentScreen == "friend_poll"
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(enabled = !profileState.isDeletingAccount) {
                                    selectedProfileCaprice = null
                                    selectedFeedItem = null
                                    currentScreen = "home"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = stringResource(R.string.nav_home),
                                tint = if (isHomeSelected) ActiveLime else InactiveIcon,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Pestaña 2: Cámara / Crear Capricho
                        val isCreateSelected = currentScreen == "create" || currentScreen == "active" || currentScreen == "result"
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(enabled = !profileState.isDeletingAccount) {
                                    selectedProfileCaprice = null
                                    selectedFeedItem = null
                                    currentScreen = "create"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            CameraNavIcon(tint = if (isCreateSelected) ActiveLime else InactiveIcon)
                        }

                        // Pestaña 3: Perfil (resaltado en verde lima cuando estamos en perfil propio o amigos)
                        val isProfileSelected = (currentScreen == "profile" && (viewingProfileUserId == currentUserId || viewingProfileUserId.isBlank())) || currentScreen == "add_friend"
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(enabled = !profileState.isDeletingAccount) {
                                    selectedFeedItem = null
                                    viewingProfileUserId = currentUserId
                                    currentScreen = "profile"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isProfileSelected) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = stringResource(R.string.nav_profile),
                                tint = if (isProfileSelected) ActiveLime else InactiveIcon,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                selectedPublicAuthor?.let { author ->
                    val cleanUsername = author.username.trim().removePrefix("@").ifBlank { "usuario" }
                    val isSelfAuthor = author.uid.isNotBlank() && author.uid == currentUserId
                    val isFriendAuthor = author.uid.isNotBlank() && friendshipState.friends.any { it.uid == author.uid }
                    val isSendingDirect = author.uid.isNotBlank() && author.uid in friendshipState.directRequestSendingUids
                    val isDirectSent = author.uid.isNotBlank() && author.uid in friendshipState.directRequestSentUids
                    GeneralAuthorProfileSheet(
                        author = author,
                        cleanUsername = cleanUsername,
                        isSelfOrFriend = isSelfAuthor || isFriendAuthor,
                        isSendingRequest = isSendingDirect,
                        isRequestSent = isDirectSent,
                        requestError = friendshipState.directRequestErrors[author.uid],
                        onDismiss = {
                            friendshipViewModel.clearDirectRequestError(author.uid)
                            selectedPublicAuthor = null
                        },
                        onOpenFullProfile = {
                            selectedPublicAuthor = null
                            if (author.uid.isNotBlank()) {
                                openProfile(author.uid, currentScreen)
                            }
                        },
                        onSendFriendRequest = {
                            if (author.uid.isNotBlank() && currentUserId.isNotBlank()) {
                                friendshipViewModel.sendFriendRequestToUser(currentUserId, author.uid)
                            } else {
                                selectedPublicAuthor = null
                                previousScreenBeforeAddFriend = if (currentScreen == "profile") "profile" else "home"
                                currentScreen = "add_friend"
                                friendshipViewModel.openInviteForUsername(cleanUsername)
                            }
                        },
                        onSearchFriend = {
                            friendshipViewModel.clearDirectRequestError()
                            selectedPublicAuthor = null
                            previousScreenBeforeAddFriend = if (currentScreen == "profile") "profile" else "home"
                            currentScreen = "add_friend"
                            friendshipViewModel.openInviteForUsername(cleanUsername)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralAuthorProfileSheet(
    author: UserProfile,
    cleanUsername: String,
    isSelfOrFriend: Boolean,
    isSendingRequest: Boolean,
    isRequestSent: Boolean,
    requestError: String?,
    onDismiss: () -> Unit,
    onOpenFullProfile: () -> Unit,
    onSendFriendRequest: () -> Unit,
    onSearchFriend: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3C)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserAvatar(
                photoUrl = author.photoUrl,
                username = cleanUsername,
                size = 68.dp,
                backgroundColor = ActiveLime,
                fontSize = 26.sp,
                modifier = Modifier.border(1.5.dp, ActiveLime.copy(alpha = 0.45f), CircleShape)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "@$cleanUsername",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = author.bio.takeIf { it.isNotBlank() }
                    ?: stringResource(
                        if (isSelfOrFriend) {
                            R.string.general_author_bio_friend_fallback
                        } else {
                            R.string.general_author_bio_public_fallback
                        }
                    ),
                color = Color(0xFF9E9EA6),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (!isSelfOrFriend && !requestError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = requestError,
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = if (isSelfOrFriend) onOpenFullProfile else onSendFriendRequest,
                enabled = isSelfOrFriend || (!isSendingRequest && !isRequestSent),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActiveLime,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF232329),
                    disabledContentColor = ActiveLime
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(
                        when {
                            isSelfOrFriend -> R.string.general_author_view_full_profile
                            isSendingRequest -> R.string.general_author_sending_request
                            isRequestSent -> R.string.general_author_request_sent
                            else -> R.string.general_author_send_request
                        }
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (!isSelfOrFriend) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onSearchFriend) {
                    Text(
                        text = stringResource(R.string.general_author_open_in_friends),
                        color = Color(0xFF9E9EA6),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(25.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height

        // Cuerpo de la cámara
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = Size(w * 0.84f, h * 0.62f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Visor superior
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.12f),
            size = Size(w * 0.32f, h * 0.12f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Lente central
        drawCircle(
            color = tint,
            radius = w * 0.18f,
            center = Offset(w * 0.5f, h * 0.55f),
            style = Stroke(width = stroke)
        )
    }
}
