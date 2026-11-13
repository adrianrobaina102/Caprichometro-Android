package com.example.caprichometro.infrastructure.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.caprichometro.application.usecase.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.ObserveFriendCapricesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.InviteDeepLink
import com.example.caprichometro.infrastructure.factory.ViewModelFactory
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.security.AppCheckInstaller
import com.example.caprichometro.infrastructure.ui.auth.AuthScreen
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.friendship.AddFriendScreen
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileScreen
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private val NavBarBg = Color(0xFF0D0D0D)
private val NavDivider = Color(0xFF222224)
private val ActiveLime = Color(0xFFB8E636)
private val InactiveIcon = Color(0xFF7C7C80)

class MainActivity : ComponentActivity() {
    private var pendingIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa Firebase App Check (Debug en desarrollo, Play Integrity en release)
        AppCheckInstaller.install(this)

        // Crea el canal de notificaciones para los avisos de votación y amigos
        PollNotificationHelper.createNotificationChannel(this)

        pendingIntentState.value = intent

        val viewModelFactory = ViewModelFactory(this)
        val capriceViewModel = viewModelFactory.createCapriceViewModel()
        val authViewModel = viewModelFactory.createAuthViewModel()
        val friendshipViewModel = viewModelFactory.createFriendshipViewModel()
        val profileViewModel = viewModelFactory.createProfileViewModel()
        val activePollViewModel = viewModelFactory.createActivePollViewModel()
        val observeFriendCapricesUseCase = viewModelFactory.createObserveFriendCapricesUseCase()
        val notifyFriendCapriceUseCase = viewModelFactory.createNotifyFriendCapriceUseCase()
        val getNotificationCapriceUseCase = viewModelFactory.createGetNotificationCapriceUseCase()

        setContent {
            CapriceApp(
                authViewModel = authViewModel,
                capriceViewModel = capriceViewModel,
                friendshipViewModel = friendshipViewModel,
                profileViewModel = profileViewModel,
                activePollViewModel = activePollViewModel,
                observeFriendCapricesUseCase = observeFriendCapricesUseCase,
                notifyFriendCapriceUseCase = notifyFriendCapriceUseCase,
                getNotificationCapriceUseCase = getNotificationCapriceUseCase,
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
    capriceViewModel: CapriceViewModel,
    friendshipViewModel: FriendshipViewModel,
    profileViewModel: ProfileViewModel,
    activePollViewModel: ActivePollViewModel,
    observeFriendCapricesUseCase: ObserveFriendCapricesUseCase,
    notifyFriendCapriceUseCase: NotifyFriendCapriceUseCase,
    getNotificationCapriceUseCase: GetNotificationCapriceUseCase,
    pendingIntent: Intent? = null,
    onClearPendingIntent: () -> Unit = {}
) {
    val authState by authViewModel.state.collectAsState()
    val capriceState by capriceViewModel.state.collectAsState()
    val friendshipState by friendshipViewModel.state.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Solicitud del permiso de notificaciones en tiempo de ejecución (Android 13+ / API 33+)
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* El usuario decide si autoriza o deniega las notificaciones */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var currentScreen by remember { mutableStateOf("home") }
    var viewingProfileUserId by remember { mutableStateOf("") }
    var previousScreenBeforeProfile by remember { mutableStateOf("home") }
    var selectedFeedItem by remember { mutableStateOf<CapriceFeedItem?>(null) }
    var selectedProfileCaprice by remember { mutableStateOf<Caprice?>(null) }

    // Intercepta enlaces de invitación (Deep Links) y notificaciones pulsadas
    LaunchedEffect(pendingIntent, authState.isAuthSuccessful, currentUserId) {
        val targetIntent = pendingIntent
        if (targetIntent != null && authState.isAuthSuccessful && currentUserId.isNotBlank()) {
            val invitedUsername = InviteDeepLink.extractUsername(targetIntent.dataString)
            if (invitedUsername != null) {
                currentScreen = "add_friend"
                friendshipViewModel.openInviteForUsername(invitedUsername)
                onClearPendingIntent()
                return@LaunchedEffect
            }

            val capriceId = targetIntent.getStringExtra(PollNotificationHelper.EXTRA_CAPRICE_ID)
                ?: targetIntent.data?.lastPathSegment
            val navigateTo = targetIntent.getStringExtra(PollNotificationHelper.EXTRA_NAVIGATE_TO)
            val recipientUserId = targetIntent.getStringExtra(PollNotificationHelper.EXTRA_RECIPIENT_USER_ID) ?: currentUserId

            if (!capriceId.isNullOrBlank()) {
                val result = getNotificationCapriceUseCase.execute(capriceId, recipientUserId)
                val item = result.getOrNull()
                if (item != null) {
                    if (item.caprice.userId == currentUserId) {
                        selectedProfileCaprice = item.caprice
                        currentScreen = if (navigateTo == "result" || PollNotificationScheduler.isPollExpired(item.caprice.timestamp)) {
                            "result"
                        } else {
                            "active"
                        }
                    } else {
                        if (navigateTo == "result") {
                            selectedProfileCaprice = item.caprice
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
            onClearPendingIntent()
        }
    }

    // Escuchamos solicitudes pendientes, amigos, feed y notificaciones de amigos al iniciar sesión
    LaunchedEffect(authState.isAuthSuccessful, currentUserId) {
        if (authState.isAuthSuccessful && currentUserId.isNotBlank()) {
            profileViewModel.reset()
            friendshipViewModel.listenToIncomingRequests(currentUserId)
            capriceViewModel.fetchUserCaprices(limit = 50)
            capriceViewModel.loadFriendsFeed()

            val foregroundStart = System.currentTimeMillis()

            // 1. Recuperación al volver a la app: último capricho no notificado por amigo (limit = 1)
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

            // 2. Escucha en vivo de publicaciones nuevas de amigos mientras la sesión está abierta
            launch {
                observeFriendCapricesUseCase.execute(currentUserId, foregroundStart).collect { item ->
                    PollNotificationHelper.showFriendCapriceNotification(
                        context = context,
                        recipientUserId = currentUserId,
                        authorUsername = item.author.username,
                        caprice = item.caprice
                    )
                    // Recargamos el feed social para que aparezca la nueva duda
                    capriceViewModel.loadFriendsFeed()
                }
            }

            // 3. Recuperación al volver a la app: decisiones finales de amigos aún no notificadas
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

            // 4. Escucha en vivo de decisiones finales confirmadas por amigos con la app abierta
            launch {
                observeFriendCapricesUseCase.observeFinalDecisions(currentUserId, foregroundStart).collect { item ->
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
                    capriceViewModel.loadFriendsFeed()
                }
            }
        } else {
            profileViewModel.reset()
            friendshipViewModel.stopListening()
            activePollViewModel.reset()
            capriceViewModel.reset()
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
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
                            "home" -> {
                                HomeScreen(
                                    viewModel = capriceViewModel,
                                    onSelectFeedItem = { item ->
                                        selectedFeedItem = item
                                        currentScreen = "friend_poll"
                                    },
                                    onNavigateToFriends = {
                                        currentScreen = "add_friend"
                                    },
                                    onNavigateToUserProfile = { authorId ->
                                        viewingProfileUserId = authorId
                                        previousScreenBeforeProfile = "home"
                                        currentScreen = "profile"
                                    }
                                )
                            }
                            "friend_poll" -> {
                                val item = selectedFeedItem
                                ActivePollScreen(
                                    caprice = item?.caprice,
                                    authorUsername = item?.author?.username,
                                    viewModel = activePollViewModel,
                                    currentUserId = currentUserId,
                                    onNavigateBack = { currentScreen = "home" },
                                    onTimerFinished = {
                                        selectedProfileCaprice = item?.caprice
                                        currentScreen = "result"
                                    },
                                    onNavigateToAuthorProfile = { authorId ->
                                        viewingProfileUserId = authorId
                                        previousScreenBeforeProfile = "friend_poll"
                                        currentScreen = "profile"
                                    }
                                )
                            }
                            "create" -> {
                                CapriceScreen(
                                    viewModel = capriceViewModel,
                                    friends = friendshipState.friends,
                                    onNavigateToActivePoll = {
                                        selectedProfileCaprice = null
                                        currentScreen = "active"
                                    }
                                )
                            }
                            "active" -> {
                                val capriceToShow = selectedProfileCaprice ?: capriceState.latestCaprices.firstOrNull()
                                ActivePollScreen(
                                    caprice = capriceToShow,
                                    viewModel = activePollViewModel,
                                    currentUserId = currentUserId,
                                    onNavigateBack = {
                                        if (selectedProfileCaprice != null) {
                                            selectedProfileCaprice = null
                                            currentScreen = "profile"
                                        } else {
                                            currentScreen = "create"
                                        }
                                    },
                                    onTimerFinished = { currentScreen = "result" }
                                )
                            }
                            "result" -> {
                                val capriceToShow = selectedProfileCaprice ?: capriceState.latestCaprices.firstOrNull()
                                ResultScreen(
                                    caprice = capriceToShow,
                                    viewModel = activePollViewModel,
                                    currentUserId = currentUserId,
                                    onCapriceUpdated = { updated ->
                                        selectedProfileCaprice = updated
                                        val decision = updated.finalDecision
                                        val decidedAt = updated.finalDecisionAt
                                        if (decision != null && decidedAt != null) {
                                            profileViewModel.markFinalDecision(updated.id, decision, decidedAt)
                                            capriceViewModel.markFinalDecision(updated.id, decision, decidedAt)
                                        }
                                    },
                                    onGoHome = {
                                        val wasFromProfile = selectedProfileCaprice != null
                                        selectedProfileCaprice = null
                                        currentScreen = if (wasFromProfile) "profile" else "home"
                                    },
                                    onNavigateToAuthorProfile = { authorId ->
                                        viewingProfileUserId = authorId
                                        previousScreenBeforeProfile = "result"
                                        currentScreen = "profile"
                                    }
                                )
                            }
                            "add_friend" -> {
                                AddFriendScreen(
                                    viewModel = friendshipViewModel,
                                    currentUserId = currentUserId,
                                    onBack = {
                                        viewingProfileUserId = currentUserId
                                        currentScreen = "profile"
                                    },
                                    onViewProfile = { friendUserId ->
                                        viewingProfileUserId = friendUserId
                                        previousScreenBeforeProfile = "add_friend"
                                        currentScreen = "profile"
                                    }
                                )
                            }
                            "profile" -> {
                                val targetId = if (viewingProfileUserId.isNotBlank()) viewingProfileUserId else currentUserId
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    targetUserId = targetId,
                                    currentUserId = currentUserId,
                                    onNavigateToFriends = { currentScreen = "add_friend" },
                                    onSelectCaprice = { caprice ->
                                        selectedProfileCaprice = caprice
                                        val isExpired = (System.currentTimeMillis() - caprice.timestamp) >= PollNotificationScheduler.POLL_DURATION_MILLIS
                                        currentScreen = if (isExpired) "result" else "active"
                                    },
                                    onBack = {
                                        if (viewingProfileUserId != currentUserId && viewingProfileUserId.isNotBlank()) {
                                            viewingProfileUserId = currentUserId
                                            currentScreen = previousScreenBeforeProfile
                                        } else {
                                            currentScreen = "home"
                                        }
                                    },
                                    onSignedOut = {
                                        profileViewModel.reset()
                                        friendshipViewModel.stopListening()
                                        activePollViewModel.reset()
                                        capriceViewModel.reset()
                                        authViewModel.signOut()
                                        viewingProfileUserId = ""
                                        selectedFeedItem = null
                                        selectedProfileCaprice = null
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
                                .clickable { currentScreen = "home" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Inicio",
                                tint = if (isHomeSelected) ActiveLime else InactiveIcon,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Pestaña 2: Cámara / Crear Capricho
                        val isCreateSelected = currentScreen == "create" || currentScreen == "active" || currentScreen == "result"
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    selectedProfileCaprice = null
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
                                .clickable {
                                    viewingProfileUserId = currentUserId
                                    currentScreen = "profile"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isProfileSelected) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = "Perfil",
                                tint = if (isProfileSelected) ActiveLime else InactiveIcon,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
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
