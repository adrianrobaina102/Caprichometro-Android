package com.example.caprichometro.infrastructure.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import com.example.caprichometro.infrastructure.factory.ViewModelFactory
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.ui.auth.AuthScreen
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.friendship.AddFriendScreen
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileScreen
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth

private val NavBarBg = Color(0xFF0D0D0D)
private val NavDivider = Color(0xFF222224)
private val ActiveLime = Color(0xFFB8E636)
private val InactiveIcon = Color(0xFF7C7C80)

class MainActivity : ComponentActivity() {
    private var initialScreen = "create"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crea el canal de notificaciones para los avisos de votación
        PollNotificationHelper.createNotificationChannel(this)

        if (intent?.getStringExtra(PollNotificationHelper.EXTRA_NAVIGATE_TO) == "result") {
            initialScreen = "result"
        }

        val viewModelFactory = ViewModelFactory()
        val capriceViewModel = viewModelFactory.createCapriceViewModel()
        val authViewModel = viewModelFactory.createAuthViewModel()
        val friendshipViewModel = viewModelFactory.createFriendshipViewModel()
        val profileViewModel = viewModelFactory.createProfileViewModel()

        setContent {
            CapriceApp(
                authViewModel = authViewModel,
                capriceViewModel = capriceViewModel,
                friendshipViewModel = friendshipViewModel,
                profileViewModel = profileViewModel,
                startScreen = initialScreen
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun CapriceApp(
    authViewModel: AuthViewModel,
    capriceViewModel: CapriceViewModel,
    friendshipViewModel: FriendshipViewModel,
    profileViewModel: ProfileViewModel,
    startScreen: String = "create"
) {
    val authState by authViewModel.state.collectAsState()
    val capriceState by capriceViewModel.state.collectAsState()
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

    // Escuchamos solicitudes pendientes y amigos en tiempo real en cuanto el usuario inicia sesión
    LaunchedEffect(authState.isAuthSuccessful, currentUserId) {
        if (authState.isAuthSuccessful && currentUserId.isNotBlank()) {
            profileViewModel.reset()
            friendshipViewModel.listenToIncomingRequests(currentUserId)
            capriceViewModel.fetchUserCaprices(limit = 50)
        } else {
            profileViewModel.reset()
            friendshipViewModel.stopListening()
        }
    }

    var currentScreen by remember { mutableStateOf(startScreen) }
    var viewingProfileUserId by remember { mutableStateOf("") }

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
                                val latestCaprice = capriceState.latestCaprices.firstOrNull()
                                if (latestCaprice != null) {
                                    ActivePollScreen(
                                        caprice = latestCaprice,
                                        onNavigateBack = { currentScreen = "create" },
                                        onTimerFinished = { currentScreen = "result" }
                                    )
                                } else {
                                    CapriceScreen(
                                        viewModel = capriceViewModel,
                                        onNavigateToActivePoll = { currentScreen = "active" }
                                    )
                                }
                            }
                            "create" -> {
                                CapriceScreen(
                                    viewModel = capriceViewModel,
                                    onNavigateToActivePoll = { currentScreen = "active" }
                                )
                            }
                            "active" -> {
                                val latestCaprice = capriceState.latestCaprices.firstOrNull()
                                ActivePollScreen(
                                    caprice = latestCaprice,
                                    onNavigateBack = { currentScreen = "create" },
                                    onTimerFinished = { currentScreen = "result" }
                                )
                            }
                            "result" -> {
                                val latestCaprice = capriceState.latestCaprices.firstOrNull()
                                ResultScreen(
                                    caprice = latestCaprice,
                                    onGoHome = { currentScreen = "create" }
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
                                    onSelectCaprice = { _ ->
                                        currentScreen = "home"
                                    },
                                    onBack = {
                                        if (viewingProfileUserId != currentUserId && viewingProfileUserId.isNotBlank()) {
                                            viewingProfileUserId = currentUserId
                                            currentScreen = "add_friend"
                                        } else {
                                            currentScreen = "create"
                                        }
                                    },
                                    onSignedOut = {
                                        profileViewModel.reset()
                                        friendshipViewModel.stopListening()
                                        authViewModel.signOut()
                                        viewingProfileUserId = ""
                                        currentScreen = "create"
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
                        val isHomeSelected = currentScreen == "home" || currentScreen == "active" || currentScreen == "result"
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
                        val isCreateSelected = currentScreen == "create"
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { currentScreen = "create" },
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
