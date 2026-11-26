package com.example.caprichometro.infrastructure.ui.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.caprichometro.R
import com.example.caprichometro.application.usecase.notification.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.notification.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.notification.ObserveFriendCapricesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.ui.auth.AuthScreen
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.caprice.CapriceScreen
import com.example.caprichometro.infrastructure.ui.caprice.CreateCapriceViewModel
import com.example.caprichometro.infrastructure.ui.components.OfflineStatusBanner
import com.example.caprichometro.infrastructure.ui.friendship.AddFriendScreen
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.home.GeneralAuthorProfileSheet
import com.example.caprichometro.infrastructure.ui.home.HomeFeedTab
import com.example.caprichometro.infrastructure.ui.home.HomeFeedViewModel
import com.example.caprichometro.infrastructure.ui.home.HomeScreen
import com.example.caprichometro.infrastructure.ui.profile.ProfileScreen
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.example.caprichometro.infrastructure.ui.voting.ActivePollScreen
import com.example.caprichometro.infrastructure.ui.voting.ActivePollViewModel
import com.example.caprichometro.infrastructure.ui.voting.ResultScreen
import com.example.caprichometro.infrastructure.util.NetworkConnectivityMonitor
import com.google.firebase.auth.FirebaseAuth

private val NavBarBg = Color(0xFF0D0D0D)
private val NavDivider = Color(0xFF222224)
private val ActiveLime = Color(0xFFB8E636)
private val InactiveIcon = Color(0xFF7C7C80)

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

    val isNotificationPromptPending = rememberNotificationPermissionPending(authState.isAuthSuccessful)

    var currentRoute by rememberSaveable(stateSaver = AppRoute.Saver) { mutableStateOf<AppRoute>(AppRoute.Home) }
    var viewingProfileUserId by rememberSaveable { mutableStateOf("") }
    var previousRouteBeforeProfile by rememberSaveable(stateSaver = AppRoute.Saver) { mutableStateOf<AppRoute>(AppRoute.Home) }
    var previousRouteBeforeAddFriend by rememberSaveable(stateSaver = AppRoute.Saver) { mutableStateOf<AppRoute>(AppRoute.Profile) }
    var previousRouteBeforeActive by rememberSaveable(stateSaver = AppRoute.Saver) { mutableStateOf<AppRoute>(AppRoute.Create) }
    var selectedFeedItem by remember { mutableStateOf(sessionNavViewModel.selectedFeedItem) }
    var selectedProfileCaprice by remember { mutableStateOf(sessionNavViewModel.selectedProfileCaprice) }
    var selectedPublicAuthor by remember { mutableStateOf(sessionNavViewModel.selectedPublicAuthor) }
    LaunchedEffect(selectedFeedItem, selectedProfileCaprice, selectedPublicAuthor) {
        sessionNavViewModel.selectedFeedItem = selectedFeedItem
        sessionNavViewModel.selectedProfileCaprice = selectedProfileCaprice
        sessionNavViewModel.selectedPublicAuthor = selectedPublicAuthor
    }

    val openProfile: (String, AppRoute) -> Unit = { authorId, fromRoute ->
        if (authorId.isNotBlank()) {
            viewingProfileUserId = authorId
            previousRouteBeforeProfile = fromRoute
            currentRoute = AppRoute.Profile
        }
    }

    BackHandler(enabled = authState.isAuthSuccessful && (selectedPublicAuthor != null || currentRoute != AppRoute.Home)) {
        if (selectedPublicAuthor != null) {
            selectedPublicAuthor = null
            return@BackHandler
        }
        when (currentRoute) {
            AppRoute.FriendPoll -> {
                selectedFeedItem = null
                currentRoute = AppRoute.Home
            }
            AppRoute.Create -> currentRoute = AppRoute.Home
            AppRoute.ActivePoll -> {
                selectedProfileCaprice = null
                currentRoute = when (previousRouteBeforeActive) {
                    AppRoute.Home -> AppRoute.Home
                    AppRoute.Profile -> AppRoute.Profile
                    else -> AppRoute.Create
                }
            }
            AppRoute.Result -> {
                val capriceToShow = selectedProfileCaprice ?: capriceState.latestCaprices.firstOrNull()
                val wasFromProfile = previousRouteBeforeActive == AppRoute.Profile &&
                    selectedProfileCaprice != null &&
                    selectedFeedItem?.caprice?.id != capriceToShow?.id
                selectedProfileCaprice = null
                selectedFeedItem = null
                currentRoute = if (wasFromProfile) AppRoute.Profile else AppRoute.Home
            }
            AppRoute.AddFriend -> {
                if (previousRouteBeforeAddFriend == AppRoute.Profile) {
                    viewingProfileUserId = currentUserId
                }
                currentRoute = previousRouteBeforeAddFriend
            }
            AppRoute.Profile -> {
                if (viewingProfileUserId != currentUserId && viewingProfileUserId.isNotBlank() && previousRouteBeforeProfile != AppRoute.Profile) {
                    viewingProfileUserId = currentUserId
                    currentRoute = previousRouteBeforeProfile
                } else if (previousRouteBeforeProfile == AppRoute.AddFriend) {
                    viewingProfileUserId = currentUserId
                    currentRoute = AppRoute.AddFriend
                } else {
                    currentRoute = AppRoute.Home
                }
            }
            AppRoute.Home -> Unit
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

    AppPendingIntentEffect(
        pendingIntent = pendingIntent,
        isAuthSuccessful = authState.isAuthSuccessful,
        currentUserId = currentUserId,
        currentRoute = currentRoute,
        friendshipViewModel = friendshipViewModel,
        sessionNavViewModel = sessionNavViewModel,
        getNotificationCapriceUseCase = getNotificationCapriceUseCase,
        onClearPendingIntent = onClearPendingIntent,
        onOpenInviteRoute = {
            if (currentRoute != AppRoute.AddFriend) {
                previousRouteBeforeAddFriend = currentRoute
            }
            currentRoute = AppRoute.AddFriend
        },
        onOpenOwnNotificationCaprice = { caprice, dest ->
            viewingProfileUserId = currentUserId
            selectedProfileCaprice = caprice
            currentRoute = dest
        },
        onOpenFriendNotificationCaprice = { item, dest ->
            selectedFeedItem = item
            if (dest == AppRoute.Result) {
                selectedProfileCaprice = null
            }
            currentRoute = dest
        },
        onFallbackRoute = { dest -> currentRoute = dest }
    )

    LaunchedEffect(currentRoute) {
        if (currentRoute !in setOf(AppRoute.FriendPoll, AppRoute.ActivePoll, AppRoute.Result)) {
            activePollViewModel.reset()
        }
    }

    AppSessionBootstrapEffect(
        isAuthSuccessful = authState.isAuthSuccessful,
        currentUserId = currentUserId,
        firebaseAuth = firebaseAuth,
        latestCaprices = capriceState.latestCaprices,
        authViewModel = authViewModel,
        homeFeedViewModel = homeFeedViewModel,
        createCapriceViewModel = createCapriceViewModel,
        friendshipViewModel = friendshipViewModel,
        profileViewModel = profileViewModel,
        activePollViewModel = activePollViewModel,
        sessionNavViewModel = sessionNavViewModel,
        observeFriendCapricesUseCase = observeFriendCapricesUseCase,
        notifyFriendCapriceUseCase = notifyFriendCapriceUseCase,
        onClearSessionCaches = onClearSessionCaches,
        onSessionCleared = {
            selectedFeedItem = null
            selectedProfileCaprice = null
            selectedPublicAuthor = null
            viewingProfileUserId = ""
            previousRouteBeforeProfile = AppRoute.Home
            previousRouteBeforeAddFriend = AppRoute.Profile
            previousRouteBeforeActive = AppRoute.Create
            currentRoute = AppRoute.Home
        }
    )

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
                        if (currentRoute == AppRoute.Profile && currentUserId.isNotBlank()) {
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
                        when (currentRoute) {
                            AppRoute.Home -> {
                                HomeScreen(
                                    viewModel = homeFeedViewModel,
                                    onSelectFeedItem = { item ->
                                        selectedFeedItem = item
                                        currentRoute = AppRoute.FriendPoll
                                    },
                                    onNavigateToFriends = {
                                        previousRouteBeforeAddFriend = AppRoute.Home
                                        currentRoute = AppRoute.AddFriend
                                    },
                                    deferLocationAutoPrompt = isNotificationPromptPending,
                                    onNavigateToUserProfile = { authorId ->
                                        openProfile(authorId, AppRoute.Home)
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
                                            previousRouteBeforeActive = AppRoute.Home
                                            currentRoute = AppRoute.ActivePoll
                                        } else {
                                            openProfile(currentUserId, AppRoute.Home)
                                        }
                                    },
                                    onOwnAvatarLongClick = {
                                        openProfile(currentUserId, AppRoute.Home)
                                    }
                                )
                            }
                            AppRoute.FriendPoll -> {
                                val item = selectedFeedItem
                                if (item == null) {
                                    LaunchedEffect(Unit) { currentRoute = AppRoute.Home }
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
                                        onNavigateBack = { currentRoute = AppRoute.Home },
                                        onTimerFinished = {
                                            selectedProfileCaprice = item.caprice
                                            currentRoute = AppRoute.Result
                                        },
                                        onNavigateToAuthorProfile = { authorId ->
                                            openProfile(authorId, AppRoute.FriendPoll)
                                        },
                                        onOpenPublicAuthorInfo = pollAuthor?.let { author ->
                                            { selectedPublicAuthor = author }
                                        },
                                        canOpenAuthorProfileOverride = canOpenProfile,
                                        approximateDistanceMeters = item.approximateDistanceMeters
                                    )
                                }
                            }
                            AppRoute.Create -> {
                                CapriceScreen(
                                    viewModel = createCapriceViewModel,
                                    friends = friendshipState.friends,
                                    onCapriceCreated = { saved ->
                                        homeFeedViewModel.onCapriceCreated(saved)
                                        saved.locationCell?.let { cell ->
                                            homeFeedViewModel.onLocationCellResolved(cell)
                                        }
                                    },
                                    onNavigateToActivePoll = {
                                        selectedProfileCaprice = null
                                        previousRouteBeforeActive = AppRoute.Create
                                        currentRoute = AppRoute.ActivePoll
                                    }
                                )
                            }
                            AppRoute.ActivePoll -> {
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
                                        currentRoute = when (previousRouteBeforeActive) {
                                            AppRoute.Home -> AppRoute.Home
                                            AppRoute.Profile -> AppRoute.Profile
                                            else -> AppRoute.Create
                                        }
                                    },
                                    onTimerFinished = { currentRoute = AppRoute.Result },
                                    onNavigateToAuthorProfile = { authorId ->
                                        openProfile(authorId, AppRoute.ActivePoll)
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
                            AppRoute.Result -> {
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
                                        val wasFromProfile = previousRouteBeforeActive == AppRoute.Profile &&
                                            selectedProfileCaprice != null &&
                                            selectedFeedItem?.caprice?.id != capriceToShow?.id
                                        selectedProfileCaprice = null
                                        selectedFeedItem = null
                                        currentRoute = if (wasFromProfile) AppRoute.Profile else AppRoute.Home
                                    },
                                    onNavigateToAuthorProfile = { authorId ->
                                        openProfile(authorId, AppRoute.Result)
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
                            AppRoute.AddFriend -> {
                                AddFriendScreen(
                                    viewModel = friendshipViewModel,
                                    currentUserId = currentUserId,
                                    onBack = {
                                        if (previousRouteBeforeAddFriend == AppRoute.Profile) {
                                            viewingProfileUserId = currentUserId
                                        }
                                        currentRoute = previousRouteBeforeAddFriend
                                    },
                                    onOpenOwnProfile = {
                                        openProfile(currentUserId, AppRoute.AddFriend)
                                    },
                                    onViewProfile = { friendUserId ->
                                        openProfile(friendUserId, AppRoute.AddFriend)
                                    }
                                )
                            }
                            AppRoute.Profile -> {
                                val targetId = if (viewingProfileUserId.isNotBlank()) viewingProfileUserId else currentUserId
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    targetUserId = targetId,
                                    currentUserId = currentUserId,
                                    onNavigateToFriends = {
                                        previousRouteBeforeAddFriend = AppRoute.Profile
                                        currentRoute = AppRoute.AddFriend
                                    },
                                    onSelectCaprice = { caprice ->
                                        selectedProfileCaprice = caprice
                                        previousRouteBeforeActive = AppRoute.Profile
                                        val isExpired = PollTiming.isPollExpired(caprice.timestamp)
                                        currentRoute = if (isExpired) AppRoute.Result else AppRoute.ActivePoll
                                    },
                                    onBack = {
                                        if (viewingProfileUserId != currentUserId && viewingProfileUserId.isNotBlank() && previousRouteBeforeProfile != AppRoute.Profile) {
                                            viewingProfileUserId = currentUserId
                                            currentRoute = previousRouteBeforeProfile
                                        } else if (previousRouteBeforeProfile == AppRoute.AddFriend) {
                                            viewingProfileUserId = currentUserId
                                            currentRoute = AppRoute.AddFriend
                                        } else {
                                            currentRoute = AppRoute.Home
                                        }
                                    },
                                    onSignedOut = {
                                        NotificationManagerCompat.from(context).cancelAll()
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
                                        previousRouteBeforeProfile = AppRoute.Home
                                        previousRouteBeforeActive = AppRoute.Create
                                        selectedFeedItem = null
                                        selectedProfileCaprice = null
                                        selectedPublicAuthor = null
                                        currentRoute = AppRoute.Home
                                    }
                                )
                            }
                        }
                    }

                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        currentUserId = currentUserId,
                        viewingProfileUserId = viewingProfileUserId,
                        isDeletingAccount = profileState.isDeletingAccount,
                        onSelectHome = {
                            selectedProfileCaprice = null
                            selectedFeedItem = null
                            currentRoute = AppRoute.Home
                        },
                        onSelectCreate = {
                            selectedProfileCaprice = null
                            selectedFeedItem = null
                            currentRoute = AppRoute.Create
                        },
                        onSelectProfile = {
                            selectedFeedItem = null
                            viewingProfileUserId = currentUserId
                            currentRoute = AppRoute.Profile
                        }
                    )
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
                                openProfile(author.uid, currentRoute)
                            }
                        },
                        onSendFriendRequest = {
                            if (author.uid.isNotBlank() && currentUserId.isNotBlank()) {
                                friendshipViewModel.sendFriendRequestToUser(currentUserId, author.uid)
                            } else {
                                selectedPublicAuthor = null
                                previousRouteBeforeAddFriend = if (currentRoute == AppRoute.Profile) AppRoute.Profile else AppRoute.Home
                                currentRoute = AppRoute.AddFriend
                                friendshipViewModel.openInviteForUsername(cleanUsername)
                            }
                        },
                        onSearchFriend = {
                            friendshipViewModel.clearDirectRequestError()
                            selectedPublicAuthor = null
                            previousRouteBeforeAddFriend = if (currentRoute == AppRoute.Profile) AppRoute.Profile else AppRoute.Home
                            currentRoute = AppRoute.AddFriend
                            friendshipViewModel.openInviteForUsername(cleanUsername)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    currentRoute: AppRoute,
    currentUserId: String,
    viewingProfileUserId: String,
    isDeletingAccount: Boolean,
    onSelectHome: () -> Unit,
    onSelectCreate: () -> Unit,
    onSelectProfile: () -> Unit
) {
    HorizontalDivider(color = NavDivider, thickness = 0.8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavBarBg)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isHomeSelected = currentRoute == AppRoute.Home || currentRoute == AppRoute.FriendPoll
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = !isDeletingAccount, onClick = onSelectHome),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                contentDescription = stringResource(R.string.nav_home),
                tint = if (isHomeSelected) ActiveLime else InactiveIcon,
                modifier = Modifier.size(26.dp)
            )
        }

        val isCreateSelected = currentRoute == AppRoute.Create ||
            currentRoute == AppRoute.ActivePoll ||
            currentRoute == AppRoute.Result
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = !isDeletingAccount, onClick = onSelectCreate),
            contentAlignment = Alignment.Center
        ) {
            CameraNavIcon(tint = if (isCreateSelected) ActiveLime else InactiveIcon)
        }

        val isProfileSelected = (currentRoute == AppRoute.Profile &&
            (viewingProfileUserId == currentUserId || viewingProfileUserId.isBlank())) ||
            currentRoute == AppRoute.AddFriend
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = !isDeletingAccount, onClick = onSelectProfile),
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

@Composable
private fun CameraNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(25.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = Size(w * 0.84f, h * 0.62f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.12f),
            size = Size(w * 0.32f, h * 0.12f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = w * 0.18f,
            center = Offset(w * 0.5f, h * 0.55f),
            style = Stroke(width = stroke)
        )
    }
}
