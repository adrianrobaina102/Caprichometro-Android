package com.example.caprichometro.infrastructure.ui.friendship

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

// Colores de la app
val DarkBackground = Color(0xFF121212)
val LimeGreen = Color(0xFFD4FF00)
val DarkSurface = Color(0xFF1E1E1E)

@Composable
fun AddFriendScreen(
    viewModel: FriendshipViewModel,
    currentUserId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Obtenemos el UID actual garantizado (si por recomposición vino vacío)
    val actualUserId = remember(currentUserId) {
        currentUserId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    }

    // Escuchador de las solicitudes y amistades en tiempo real
    LaunchedEffect(actualUserId) {
        if (actualUserId.isNotBlank()) {
            viewModel.listenToIncomingRequests(actualUserId)
        }
    }

    // Escuchador de errores para mostrar un mensajito flotante
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // Escuchador de éxito al enviar solicitud
    LaunchedEffect(state.requestSentSuccess) {
        if (state.requestSentSuccess) {
            Toast.makeText(context, "¡Solicitud enviada a @${state.userFound?.username ?: ""}!", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Añadir Amigos",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 1. CAMPO DE BÚSQUEDA
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Escribe un @usuario...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedIndicatorColor = LimeGreen,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. BOTÓN DE BÚSQUEDA
        Button(
            onClick = { viewModel.searchUser() },
            enabled = state.searchQuery.isNotBlank() && !state.isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LimeGreen,
                contentColor = Color.Black,
                disabledContainerColor = Color.DarkGray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            } else {
                Text("Buscar usuario", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 3. TARJETA DEL USUARIO ENCONTRADO
        state.userFound?.let { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "@${user.username}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = { viewModel.sendRequest(actualUserId) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Enviar solicitud",
                            tint = LimeGreen
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4. SECCIÓN VISUAL DE SOLICITUDES PENDIENTES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Solicitudes pendientes",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.incomingRequests.isNotEmpty()) {
                Surface(
                    color = LimeGreen,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${state.incomingRequests.size}",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (state.incomingRequests.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tienes solicitudes pendientes",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            state.incomingRequests.forEach { request ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "@${request.sender.username}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Quiere ser tu amigo.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.acceptRequest(request.friendshipId) },
                                colors = ButtonDefaults.buttonColors(containerColor = LimeGreen, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Aceptar", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Aceptar")
                            }

                            Button(
                                onClick = { viewModel.declineRequest(request.friendshipId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Rechazar", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rechazar")
                            }
                        }
                    }
                }
            }
        }

        // 5. SECCIÓN VISUAL DE AMISTADES ACEPTADAS
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Mis amigos",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.friends.isNotEmpty()) {
                Surface(
                    color = Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${state.friends.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (state.friends.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aún no tienes amigos agregados",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            state.friends.forEach { friend ->
                var menuExpanded by remember(friend.uid) { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = LimeGreen.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = LimeGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "@${friend.username}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Amigo",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opciones de amistad",
                                    tint = Color.Gray
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(Color(0xFF2A2A2A))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Eliminar amigo", color = Color.White) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.removeFriend(actualUserId, friend.uid)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bloquear", color = Color(0xFFFF5252)) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.blockUser(actualUserId, friend.uid)
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
