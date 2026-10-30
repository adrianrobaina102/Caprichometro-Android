package com.example.caprichometro.infrastructure.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteForever
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.example.caprichometro.infrastructure.ui.auth.AuthScreen
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.friendship.AddFriendScreen
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dependencias de Caprichos
        val capriceRepository = FirestoreCapriceRepository()
        val saveCapriceUseCase = SaveCapriceUseCase(capriceRepository)
        val getCapricesUseCase = GetCapricesUseCase(capriceRepository)
        val capriceViewModel = CapriceViewModel(saveCapriceUseCase, getCapricesUseCase)

        // Dependencias de Auth
        val userProfileRepository = FirestoreUserProfileRepository()
        val authRepository = FirebaseAuthRepository()
        val signInUseCase = SignInUseCase(authRepository)
        val registerUseCase = RegisterUseCase(authRepository, userProfileRepository)
        val signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository, userProfileRepository)
        val signOutUseCase = SignOutUseCase(authRepository)
        val deleteAccountUseCase = DeleteAccountUseCase(authRepository, userProfileRepository)
        val getCurrentUserUseCase = GetCurrentUserUseCase(authRepository)
        val authViewModel = AuthViewModel(
            signInUseCase,
            registerUseCase,
            signInWithGoogleUseCase,
            signOutUseCase,
            deleteAccountUseCase,
            getCurrentUserUseCase
        )

        // Dependencias de Amistad
        val friendshipRepository = FirestoreFriendshipRepository()
        val searchUserUseCase = SearchUserByUsernameUseCase(friendshipRepository)
        val sendRequestUseCase = SendFriendRequestUseCase(friendshipRepository)
        val getIncomingRequestsUseCase = GetIncomingFriendRequestsUseCase(friendshipRepository, userProfileRepository)
        val respondToRequestUseCase = RespondToFriendRequestUseCase(friendshipRepository)
        val getAcceptedFriendsUseCase = GetAcceptedFriendsUseCase(friendshipRepository, userProfileRepository)
        val friendshipViewModel = FriendshipViewModel(
            searchUserUseCase,
            sendRequestUseCase,
            getIncomingRequestsUseCase,
            respondToRequestUseCase,
            getAcceptedFriendsUseCase
        )

        setContent {
            CapriceApp(authViewModel, capriceViewModel, friendshipViewModel)
        }
    }
}

@Composable
fun CapriceApp(
    authViewModel: AuthViewModel,
    capriceViewModel: CapriceViewModel,
    friendshipViewModel: FriendshipViewModel
) {
    val authState by authViewModel.state.collectAsState()
    val capriceState by capriceViewModel.state.collectAsState()
    val friendshipState by friendshipViewModel.state.collectAsState()

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Escuchamos solicitudes pendientes y amigos en tiempo real en cuanto el usuario inicia sesión
    LaunchedEffect(authState.isAuthSuccessful, currentUserId) {
        if (authState.isAuthSuccessful && currentUserId.isNotBlank()) {
            friendshipViewModel.listenToIncomingRequests(currentUserId)
        } else {
            friendshipViewModel.stopListening()
        }
    }

    var currentScreen by remember { mutableStateOf("create") }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!authState.isAuthSuccessful) {
                AuthScreen(
                    viewModel = authViewModel,
                    onNavigateToHome = {}
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { currentScreen = "create" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentScreen != "add_friend") Color(0xFFD4FF00) else Color.DarkGray,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Caprichos")
                            }

                            Button(
                                onClick = { currentScreen = "add_friend" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentScreen == "add_friend") Color(0xFFD4FF00) else Color.DarkGray,
                                    contentColor = Color.Black
                                )
                            ) {
                                val pendingCount = friendshipState.incomingRequests.size
                                Text(if (pendingCount > 0) "Amigos ($pendingCount)" else "Amigos")
                            }
                        }

                        // Menú de opciones de perfil (Cerrar sesión / Borrar cuenta)
                        Box {
                            IconButton(onClick = { showProfileMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Opciones de perfil",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false },
                                modifier = Modifier.background(Color(0xFF2A2A2A))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Cerrar sesión", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        friendshipViewModel.stopListening()
                                        authViewModel.signOut()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Borrar cuenta", color = Color(0xFFFF5252)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteForever,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252)
                                        )
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }

                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("¿Eliminar cuenta?") },
                            text = { Text("Esta acción es irreversible y eliminará todos tus datos y perfil.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirmDialog = false
                                        friendshipViewModel.stopListening()
                                        authViewModel.deleteAccount(currentUserId, "")
                                    }
                                ) {
                                    Text("Eliminar", color = Color.Red)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                    Text("Cancelar", color = Color.White)
                                }
                            },
                            containerColor = Color(0xFF2A2A2A),
                            titleContentColor = Color.White,
                            textContentColor = Color.LightGray
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
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
                                    onBack = { currentScreen = "create" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
