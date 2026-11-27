package com.example.caprichometro.infrastructure.ui.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.caprichometro.application.usecase.notification.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.notification.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.notification.ObserveFriendCapricesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.InviteDeepLink
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.caprice.CreateCapriceViewModel
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.home.HomeFeedViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.example.caprichometro.infrastructure.ui.voting.ActivePollViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Solicita el permiso de notificaciones en Android 13+ al iniciar sesión y expone si sigue pendiente.
 */
@Composable
internal fun rememberNotificationPermissionPending(isAuthSuccessful: Boolean): Boolean {
    val context = LocalContext.current
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

    LaunchedEffect(isAuthSuccessful) {
        if (isAuthSuccessful && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        } else if (!isAuthSuccessful) {
            isNotificationPromptPending = false
        }
    }

    return isNotificationPromptPending
}

/**
 * Gestiona el arranque progresivo de sesión (feed prioritario + escuchas secundarias + avisos en vivo)
 * y la limpieza completa al cerrar o perder la sesión.
 */
@Composable
internal fun AppSessionBootstrapEffect(
    isAuthSuccessful: Boolean,
    currentUserId: String,
    firebaseAuth: FirebaseAuth,
    latestCaprices: List<Caprice>,
    authViewModel: AuthViewModel,
    homeFeedViewModel: HomeFeedViewModel,
    createCapriceViewModel: CreateCapriceViewModel,
    friendshipViewModel: FriendshipViewModel,
    profileViewModel: ProfileViewModel,
    activePollViewModel: ActivePollViewModel,
    sessionNavViewModel: SessionNavigationViewModel,
    observeFriendCapricesUseCase: ObserveFriendCapricesUseCase,
    notifyFriendCapriceUseCase: NotifyFriendCapriceUseCase,
    onClearSessionCaches: () -> Unit,
    onSessionCleared: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(isAuthSuccessful, currentUserId) {
        if (isAuthSuccessful && currentUserId.isBlank() && firebaseAuth.currentUser == null) {
            NotificationManagerCompat.from(context).cancelAll()
            latestCaprices.forEach { caprice ->
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
        if (isAuthSuccessful && currentUserId.isNotBlank()) {
            val previousBootstrappedUserId = sessionNavViewModel.bootstrappedUserId
            val isNewSessionBootstrap = previousBootstrappedUserId != currentUserId
            if (isNewSessionBootstrap) {
                if (!previousBootstrappedUserId.isNullOrBlank()) {
                    NotificationManagerCompat.from(context).cancelAll()
                    onClearSessionCaches()
                    friendshipViewModel.stopListening()
                    activePollViewModel.reset()
                    homeFeedViewModel.reset()
                    createCapriceViewModel.reset()
                }
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
                withTimeoutOrNull(1_500L) {
                    homeFeedViewModel.state.first { it.hasLoadedFeed || it.feedError != null }
                }
                Log.d(
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
                    var debouncedRefreshJob: Job? = null
                    observeFriendCapricesUseCase.observeAllUpdates(currentUserId, foregroundStart)
                        .catch { e ->
                            Log.w("AppSessionBootstrap", "Error observando novedades de amigos: ${e.message}")
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
                            // Coalescemos ráfagas simultáneas sin reiniciar la carga en cada evento ni perder paginación
                            debouncedRefreshJob?.cancel()
                            debouncedRefreshJob = launch {
                                delay(400L)
                                homeFeedViewModel.loadFriendsFeed(
                                    isManualRefresh = true,
                                    preserveVisiblePage = true
                                )
                            }
                        }
                }
            }
        } else {
            NotificationManagerCompat.from(context).cancelAll()
            sessionNavViewModel.reset()
            onClearSessionCaches()
            profileViewModel.reset()
            friendshipViewModel.stopListening()
            activePollViewModel.reset()
            homeFeedViewModel.reset()
            createCapriceViewModel.reset()
            onSessionCleared()
        }
    }
}

/**
 * Intercepta enlaces de invitación (Deep Links) y notificaciones pulsadas.
 */
@Composable
internal fun AppPendingIntentEffect(
    pendingIntent: Intent?,
    isAuthSuccessful: Boolean,
    currentUserId: String,
    currentRoute: AppRoute,
    friendshipViewModel: FriendshipViewModel,
    sessionNavViewModel: SessionNavigationViewModel,
    getNotificationCapriceUseCase: GetNotificationCapriceUseCase,
    onClearPendingIntent: () -> Unit,
    onOpenInviteRoute: (String?) -> Unit,
    onOpenOwnNotificationCaprice: (Caprice, AppRoute) -> Unit,
    onOpenFriendNotificationCaprice: (CapriceFeedItem, AppRoute) -> Unit,
    onFallbackRoute: (AppRoute) -> Unit
) {
    LaunchedEffect(pendingIntent, isAuthSuccessful, currentUserId) {
        val targetIntent = pendingIntent
        if (targetIntent != null && isAuthSuccessful && currentUserId.isNotBlank()) {
            val invitedUsername = InviteDeepLink.extractUsername(targetIntent.dataString)
            if (invitedUsername != null) {
                onOpenInviteRoute(invitedUsername)
                friendshipViewModel.openInviteForUsername(invitedUsername)
                sessionNavViewModel.pendingIntent = null
                onClearPendingIntent()
                return@LaunchedEffect
            }

            val dataUri = targetIntent.data
            val isInviteUri = (dataUri?.scheme == "caprichometro" && dataUri.host == "invite") ||
                dataUri?.pathSegments?.contains("invite") == true
            if (isInviteUri) {
                friendshipViewModel.clearSearchState()
                onOpenInviteRoute(null)
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
                        val dest = if (navigateTo == "result" || PollTiming.isPollExpired(item.caprice.timestamp)) {
                            AppRoute.Result
                        } else {
                            AppRoute.ActivePoll
                        }
                        onOpenOwnNotificationCaprice(item.caprice, dest)
                    } else {
                        val dest = if (navigateTo == "result") AppRoute.Result else AppRoute.FriendPoll
                        onOpenFriendNotificationCaprice(item, dest)
                    }
                }
            } else if (navigateTo == "result") {
                onFallbackRoute(AppRoute.Result)
            } else if (navigateTo == "active") {
                onFallbackRoute(AppRoute.ActivePoll)
            }
            sessionNavViewModel.pendingIntent = null
            onClearPendingIntent()
        }
    }
}
