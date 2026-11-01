package com.example.caprichometro.infrastructure.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
import com.example.caprichometro.infrastructure.factory.ViewModelFactory
import com.example.caprichometro.infrastructure.ui.auth.AuthScreen
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import com.example.caprichometro.infrastructure.ui.friendship.AddFriendScreen
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import com.example.caprichometro.infrastructure.ui.profile.ProfileScreen
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModelFactory = ViewModelFactory()
        val capriceViewModel = viewModelFactory.createCapriceViewModel()
        val authViewModel = viewModelFactory.createAuthViewModel()
        val friendshipViewModel = viewModelFactory.createFriendshipViewModel()
        val profileViewModel = viewModelFactory.createProfileViewModel()

        setContent {
            CapriceApp(authViewModel, capriceViewModel, friendshipViewModel, profileViewModel)
        }
    }
}

@Composable
fun CapriceApp(
    authViewModel: AuthViewModel,
    capriceViewModel: CapriceViewModel,
    friendshipViewModel: FriendshipViewModel,
    profileViewModel: ProfileViewModel
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
                                    containerColor = if (currentScreen != "add_friend" && currentScreen != "profile") Color(0xFFD4FF00) else Color.DarkGray,
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

                        // Acceso a la pantalla de perfil
                        IconButton(onClick = { currentScreen = "profile" }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Mi perfil",
                                tint = if (currentScreen == "profile") Color(0xFFD4FF00) else Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
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
                            "profile" -> {
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    currentUserId = currentUserId,
                                    onBack = { currentScreen = "create" },
                                    onSignedOut = {
                                        friendshipViewModel.stopListening()
                                        authViewModel.signOut()
                                        currentScreen = "create"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
