package com.example.caprichometro.infrastructure.ui.friendship

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.abs

val DarkBackground = Color(0xFF0D0D0D)
val LimeGreen = Color(0xFFD8FF52)
val DarkSurface = Color(0xFF1A1A1A)
private val SubtleGray = Color(0xFF8E8E93)
private val DividerDark = Color(0xFF222222)

private val PastelAvatarColors = listOf(
    Color(0xFFA8B8F0),
    Color(0xFFF4A9B8),
    Color(0xFF82C8A9),
    Color(0xFFF6D086),
    Color(0xFFC5A8F0)
)

private fun avatarColorFor(username: String): Color {
    if (username.isBlank()) return PastelAvatarColors[0]
    val index = abs(username.lowercase().hashCode()) % PastelAvatarColors.size
    return PastelAvatarColors[index]
}

private fun formatDisplayName(username: String): String {
    val clean = username.removePrefix("@").trim()
    if (clean.isEmpty()) return "Usuario"
    return clean.replaceFirstChar { it.uppercase() }
}

@Composable
fun AddFriendScreen(
    viewModel: FriendshipViewModel,
    currentUserId: String,
    onBack: () -> Unit,
    onViewProfile: (userId: String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val actualUserId = remember(currentUserId) {
        currentUserId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    }

    LaunchedEffect(actualUserId) {
        if (actualUserId.isNotBlank()) {
            viewModel.listenToIncomingRequests(actualUserId)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.requestSentSuccess) {
        if (state.requestSentSuccess) {
            Toast.makeText(context, "¡Solicitud de amistad enviada!", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Barra superior: "<   Tu gente"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Volver",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "Tu gente",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Encabezado principal
        Text(
            text = "Mejor con los tuyos.",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Solo los amigos que aceptas ven tus dudas.",
            color = SubtleGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Buscador redondeado estilo mockup
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = {
                Text(
                    text = "Buscar por nombre",
                    color = SubtleGray,
                    fontSize = 15.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = SubtleGray
                )
            },
            trailingIcon = {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = LimeGreen,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else if (state.searchQuery.isNotBlank()) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.searchUser()
                        }
                    ) {
                        Text(
                            text = "Buscar",
                            color = LimeGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (state.searchQuery.isNotBlank() && !state.isLoading) {
                        focusManager.clearFocus()
                        viewModel.searchUser()
                    }
                }
            ),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = LimeGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Resultado de búsqueda (si hay un usuario encontrado)
        state.userFound?.let { user ->
            val isSelf = user.uid == actualUserId
            val isAlreadyFriend = state.friends.any { it.uid == user.uid }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PastelInitialAvatar(
                        username = user.username,
                        backgroundColor = avatarColorFor(user.username)
                    )

                    Column {
                        Text(
                            text = formatDisplayName(user.username),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "@${user.username}",
                            color = SubtleGray,
                            fontSize = 13.sp
                        )
                    }
                }

                when {
                    isSelf -> {
                        Text(
                            text = "Tú",
                            color = SubtleGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    state.isFriendRequestBlocked -> {
                        Text(
                            text = "No disponible",
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    isAlreadyFriend -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Ya sois amigos",
                            tint = SubtleGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {
                        Button(
                            onClick = { viewModel.sendRequest(actualUserId) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeGreen,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Añadir",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECCIÓN: SOLICITUDES
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SOLICITUDES",
                color = SubtleGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Surface(
                color = Color(0xFF1F2215),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "${state.incomingRequests.size}",
                    color = LimeGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (state.incomingRequests.isEmpty()) {
            Text(
                text = "No tienes solicitudes pendientes",
                color = SubtleGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            state.incomingRequests.forEach { request ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        PastelInitialAvatar(
                            username = request.sender.username,
                            backgroundColor = Color(0xFFA8B8F0)
                        )

                        Column {
                            Text(
                                text = formatDisplayName(request.sender.username),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${request.sender.username} · Quiere ser tu amigo",
                                color = SubtleGray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { viewModel.acceptRequest(request.friendshipId) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeGreen,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = "Aceptar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = { viewModel.declineRequest(request.friendshipId) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Rechazar",
                                tint = SubtleGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = DividerDark, thickness = 1.dp)
        Spacer(modifier = Modifier.height(20.dp))

        // SECCIÓN: MIS AMIGOS · N
        Text(
            text = "MIS AMIGOS · ${state.friends.size}",
            color = SubtleGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (state.friends.isEmpty()) {
            Text(
                text = "Aún no tienes amigos agregados",
                color = SubtleGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            state.friends.forEachIndexed { index, friend ->
                var menuExpanded by remember(friend.uid) { mutableStateOf(false) }
                val avatarColor = PastelAvatarColors[(index + 1) % PastelAvatarColors.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewProfile(friend.uid) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PastelInitialAvatar(
                            username = friend.username,
                            backgroundColor = avatarColor
                        )

                        Column {
                            Text(
                                text = formatDisplayName(friend.username),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${friend.username}",
                                color = SubtleGray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Opciones de amistad",
                                tint = SubtleGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(Color(0xFF242424))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ver perfil", color = Color.White) },
                                onClick = {
                                    menuExpanded = false
                                    onViewProfile(friend.uid)
                                }
                            )
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

        Spacer(modifier = Modifier.height(28.dp))

        // Botón inferior: "Invitar a un amigo"
        Button(
            onClick = {
                val usernameToShare = state.currentUsername?.takeIf { it.isNotBlank() }
                    ?: FirebaseAuth.getInstance().currentUser?.displayName?.replace(" ", "")?.takeIf { it.isNotBlank() }

                val shareText = if (!usernameToShare.isNullOrBlank()) {
                    "¡Únete a Caprichómetro y ayúdame a decidir mis caprichos en 5 minutos! Búscame en la app como @$usernameToShare"
                } else {
                    "¡Únete a Caprichómetro y ayúdame a decidir mis caprichos en 5 minutos!"
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Invitar a un amigo"))
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF2F2F2),
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Invitar a un amigo",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Pie de privacidad: "Sin seguidores públicos. Sin desconocidos."
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = SubtleGray,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Sin seguidores públicos. Sin desconocidos.",
                color = SubtleGray,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PastelInitialAvatar(
    username: String,
    backgroundColor: Color
) {
    val initial = username.removePrefix("@").trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color(0xFF1A1A1A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
