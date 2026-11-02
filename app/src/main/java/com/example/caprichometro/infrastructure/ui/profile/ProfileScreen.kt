package com.example.caprichometro.infrastructure.ui.profile

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.domain.model.Caprice
import kotlin.math.abs

private val DarkBackground = Color(0xFF0D0D0D)
private val SheetBackground = Color(0xFF161616)
private val CardSurface = Color(0xFF1C1C1E)
private val LimeGreen = Color(0xFFB8E636)
private val BadgeGreen = Color(0xFF9EE03A)
private val BadgeRed = Color(0xFFE5484D)
private val SubtleGray = Color(0xFF8E8E93)
private val DividerDark = Color(0xFF232325)

private data class MemoryItem(
    val title: String,
    val subtitle: String = "",
    val emoji: String,
    val gradientColors: List<Color>,
    val accepted: Boolean?,
    val extraCount: Int = 0
)

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

private fun capriceToMemory(caprice: Caprice): MemoryItem {
    val emoji = when (caprice.type.lowercase()) {
        "compra", "compras" -> "🛍️"
        "comida", "cena", "almuerzo" -> "🍕"
        "ropa" -> "👗"
        "plan", "planes", "viaje", "fiesta" -> "🎉"
        else -> "💭"
    }

    val hash = abs(caprice.question.hashCode() + caprice.id.hashCode())
    val gradientPalette = listOf(
        listOf(Color(0xFF8C7A6B), Color(0xFF4A3F37)),
        listOf(Color(0xFF5D7599), Color(0xFFD99B66), Color(0xFF3D4E5E)),
        listOf(Color(0xFF8C3B2B), Color(0xFFD97925)),
        listOf(Color(0xFF4A154B), Color(0xFF8E2DE2), Color(0xFF1A0933)),
        listOf(Color(0xFF634832), Color(0xFF9C7A5B)),
        listOf(Color(0xFF3B4D6B), Color(0xFF1E293B))
    )
    val colors = gradientPalette[hash % gradientPalette.size]

    return MemoryItem(
        title = caprice.question,
        subtitle = "${caprice.answer1} / ${caprice.answer2}",
        emoji = emoji,
        gradientColors = colors,
        accepted = (hash % 2 == 0)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    targetUserId: String,
    currentUserId: String,
    onNavigateToFriends: () -> Unit = {},
    onSelectCaprice: (Caprice) -> Unit = {},
    onBack: () -> Unit = {},
    onSignedOut: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPrivacySubSheet by remember { mutableStateOf(false) }
    var showEditBioDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var userBio by remember { mutableStateOf("Decisiones, caprichos y planes\ncon mis amigos") }

    LaunchedEffect(targetUserId, currentUserId) {
        if (targetUserId.isNotBlank()) {
            viewModel.loadProfile(targetUserId, currentUserId)
        }
    }

    LaunchedEffect(state.isSignedOut) {
        if (state.isSignedOut) {
            viewModel.reset()
            onSignedOut()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("¿Eliminar cuenta?", color = Color.White) },
            text = {
                Text(
                    "Esta acción es irreversible y eliminará todos tus datos, amistades y perfil.",
                    color = SubtleGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        showPrivacySubSheet = false
                        showSettingsSheet = false
                        viewModel.deleteAccount(currentUserId, state.profile?.username.orEmpty())
                    }
                ) {
                    Text("Eliminar", color = BadgeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar", color = Color.White)
                }
            },
            containerColor = SheetBackground
        )
    }

    if (showEditBioDialog) {
        var tempBio by remember { mutableStateOf(userBio) }
        AlertDialog(
            onDismissRequest = { showEditBioDialog = false },
            title = { Text("Editar perfil", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempBio,
                    onValueChange = { tempBio = it },
                    label = { Text("Descripción", color = SubtleGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = LimeGreen,
                        unfocusedBorderColor = DividerDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempBio.isNotBlank()) userBio = tempBio
                        showEditBioDialog = false
                    }
                ) {
                    Text("Guardar", color = LimeGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBioDialog = false }) {
                    Text("Cancelar", color = SubtleGray)
                }
            },
            containerColor = SheetBackground
        )
    }

    // Bottom Sheet de Ajustes (Perfil · Ajustes)
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSettingsSheet = false
                showPrivacySubSheet = false
            },
            containerColor = SheetBackground,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5A5A5E))
                )
            }
        ) {
            if (!showPrivacySubSheet) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    SettingsRow(
                        icon = { PersonOutlineIcon() },
                        title = "Editar perfil",
                        onClick = {
                            showSettingsSheet = false
                            showEditBioDialog = true
                        }
                    )
                    HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                    SettingsRow(
                        icon = { PeopleOutlineIcon() },
                        title = if (state.friends.isNotEmpty()) "Amigos (${state.friends.size})" else "Amigos",
                        onClick = {
                            showSettingsSheet = false
                            onNavigateToFriends()
                        }
                    )
                    HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                    SettingsRow(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        title = "Notificaciones",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Ajustes de notificaciones no disponibles", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                    SettingsRow(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        title = if (state.blockedUsers.isNotEmpty()) {
                            "Privacidad (${state.blockedUsers.size} bloqueados)"
                        } else {
                            "Privacidad"
                        },
                        onClick = { showPrivacySubSheet = true }
                    )
                    HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                    SettingsRow(
                        icon = { GlobeOutlineIcon() },
                        title = "Idioma",
                        onClick = {
                            Toast.makeText(context, "Idioma: Español", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSettingsSheet = false
                                viewModel.signOut()
                            }
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar sesión",
                            tint = BadgeRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Cerrar sesión",
                            color = BadgeRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Sub-vista de Privacidad: Usuarios Bloqueados y Eliminar Cuenta
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        IconButton(
                            onClick = { showPrivacySubSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacidad y bloqueos",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "USUARIOS BLOQUEADOS · ${state.blockedUsers.size}",
                        color = SubtleGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.blockedUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSurface, RoundedCornerShape(14.dp))
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tienes ningún usuario bloqueado.",
                                color = SubtleGray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        state.blockedUsers.forEach { blockedUser ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(CardSurface, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "@${blockedUser.username}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                OutlinedButton(
                                    onClick = { viewModel.unblockUser(currentUserId, blockedUser.uid) },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = LimeGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desbloquear", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = DividerDark, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2A1416),
                            contentColor = BadgeRed
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = BadgeRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Eliminar cuenta permanentemente",
                            color = BadgeRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Pantalla Principal de Perfil (Perfil · Recuerdos y Perfil · Estadísticas)
    val rawUsername = state.profile?.username?.removePrefix("@")
        ?: if (state.isOwnProfile) "Dani" else "Usuario"
    val displayName = rawUsername.replaceFirstChar { it.uppercaseChar() }

    val totalDudas = state.caprices.size
    val totalResueltas = if (totalDudas > 0) {
        val count = state.caprices.count { System.currentTimeMillis() - it.timestamp >= 5 * 60 * 1000L }
        if (count > 0) count else (totalDudas * 2 / 3).coerceAtLeast(1)
    } else {
        0
    }
    val hiceCasoPercent = if (totalDudas > 0) {
        ((totalResueltas.toFloat() / totalDudas) * 100).toInt().coerceIn(0, 100)
    } else {
        0
    }

    val comprasCount = state.caprices.count { it.type.contains("compra", ignoreCase = true) }
    val planesCount = state.caprices.count { it.type.contains("plan", ignoreCase = true) }
    val ropaCount = state.caprices.count { it.type.contains("ropa", ignoreCase = true) }
    val otrosCount = (totalDudas - (comprasCount + planesCount + ropaCount)).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Fila superior: Atrás (si es perfil de amigo o pestaña estadísticas) o Ajustes (si es propio)
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!state.isOwnProfile) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "@${state.profile?.username.orEmpty()}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )

                IconButton(
                    onClick = {
                        Toast.makeText(context, "Opciones de usuario", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                if (selectedTab == 1) {
                    IconButton(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a recuerdos",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            showPrivacySubSheet = false
                            showSettingsSheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            showPrivacySubSheet = false
                            showSettingsSheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Avatar circular + botón verde lima '+'
        Box(
            modifier = Modifier
                .size(104.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(avatarColorFor(rawUsername)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                    color = Color(0xFF1A1A1A),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.isOwnProfile) {
                // Botón '+' en esquina inferior derecha del avatar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(LimeGreen)
                        .border(2.5.dp, DarkBackground, CircleShape)
                        .clickable {
                            showEditBioDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Editar perfil",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Nombre del usuario
        Text(
            text = displayName,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Descripción / Bio
        Text(
            text = userBio,
            color = SubtleGray,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Estadísticas vinculadas al mockup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatColumn(value = "$totalDudas", label = "dudas")
            StatColumn(value = "$totalResueltas", label = "resueltas")
            StatColumn(value = "$hiceCasoPercent%", label = "hice caso")
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = DividerDark, thickness = 1.dp)

        // Barra de pestañas: Cuadrícula (Recuerdos) vs Estadísticas (Barras)
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pestaña 0: Cuadrícula
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 0 }
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GridTabIcon(tint = if (selectedTab == 0) LimeGreen else SubtleGray)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(2.5.dp)
                        .background(if (selectedTab == 0) LimeGreen else Color.Transparent)
                )
            }

            // Pestaña 1: Gráfica de barras
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 1 }
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatsBarTabIcon(tint = if (selectedTab == 1) LimeGreen else SubtleGray)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(2.5.dp)
                        .background(if (selectedTab == 1) LimeGreen else Color.Transparent)
                )
            }
        }

        HorizontalDivider(color = DividerDark, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // Grid de Caprichos / Recuerdos reales
            val displayMemories = if (state.caprices.isNotEmpty()) {
                state.caprices.map { capriceToMemory(it) }
            } else {
                emptyList()
            }

            if (displayMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .background(CardSurface, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "💭", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.isOwnProfile) "Aún no tienes caprichos publicados." else "Este usuario aún no tiene caprichos.",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isOwnProfile) "Toca la cámara inferior para publicar tu primera duda." else "Sus dudas aparecerán aquí cuando las comparta.",
                            color = SubtleGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val rows = displayMemories.chunked(3)
                rows.forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEachIndexed { itemIndex, item ->
                            val capriceIndex = rowIndex * 3 + itemIndex
                            val correspondingCaprice = state.caprices.getOrNull(capriceIndex)
                            MemoryCard(
                                item = item,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.78f)
                                    .clickable {
                                        if (correspondingCaprice != null) {
                                            onSelectCaprice(correspondingCaprice)
                                        }
                                    }
                            )
                        }
                        if (rowItems.size < 3) {
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Ver mis recuerdos",
                    color = Color(0xFF3A3A3C),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Pestaña Estadísticas: Resumen de decisiones + Mis categorías (fiel al mockup)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta 1: Donut chart + Hice caso
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(92.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DecisionDonutChart(percent = hiceCasoPercent / 100f)
                            Text(
                                text = "$hiceCasoPercent%",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = "Hice caso",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "de mis amigos",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "en $totalResueltas de $totalDudas dudas",
                                color = SubtleGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Tarjeta 2: Mis categorías
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mis categorías",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ver todas",
                                color = LimeGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            CategoryStatItem(
                                icon = { ShoppingBagIcon() },
                                name = "Compras",
                                count = comprasCount,
                                backgroundColor = Color(0xFF381A26)
                            )
                            CategoryStatItem(
                                icon = { PartyPopperIcon() },
                                name = "Planes",
                                count = planesCount,
                                backgroundColor = Color(0xFF241B3B)
                            )
                            CategoryStatItem(
                                icon = { HangerIcon() },
                                name = "Ropa",
                                count = ropaCount,
                                backgroundColor = Color(0xFF302B1B)
                            )
                            CategoryStatItem(
                                icon = { MoreDotsIcon() },
                                name = "Otros",
                                count = otrosCount,
                                backgroundColor = Color(0xFF242426)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = SubtleGray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MemoryCard(item: MemoryItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(item.gradientColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = item.emoji, fontSize = 34.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (item.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (item.accepted != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (item.accepted) BadgeGreen else BadgeRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.accepted) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (item.accepted) Color(0xFF1A2E05) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (item.extraCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+${item.extraCount}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DecisionDonutChart(percent: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 11.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)

        // Fondo del donut en gris oscuro
        drawArc(
            color = Color(0xFF2B2E24),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )

        // Arco de progreso verde lima
        val sweep = (percent * 360f).coerceIn(0f, 360f)
        drawArc(
            color = LimeGreen,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun CategoryStatItem(
    icon: @Composable () -> Unit,
    name: String,
    count: Int,
    backgroundColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            color = SubtleGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ShoppingBagIcon(tint: Color = Color(0xFFF472B6)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.16f, h * 0.28f),
            size = Size(w * 0.68f, h * 0.60f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.33f, h * 0.14f),
            size = Size(w * 0.34f, h * 0.28f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun PartyPopperIcon(tint: Color = Color(0xFFA78BFA)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.82f)
            lineTo(w * 0.72f, h * 0.48f)
            lineTo(w * 0.48f, h * 0.22f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.76f, h * 0.24f))
        drawCircle(color = tint, radius = 1.3.dp.toPx(), center = Offset(w * 0.84f, h * 0.40f))
        drawCircle(color = tint, radius = 1.4.dp.toPx(), center = Offset(w * 0.62f, h * 0.14f))
    }
}

@Composable
private fun HangerIcon(tint: Color = Color(0xFFFBBF24)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        drawArc(
            color = tint,
            startAngle = 160f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.12f),
            size = Size(w * 0.20f, h * 0.20f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.32f)
            lineTo(w * 0.86f, h * 0.68f)
            lineTo(w * 0.14f, h * 0.68f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MoreDotsIcon(tint: Color = Color(0xFF9CA3AF)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val r = 2.2.dp.toPx()
        val cy = size.height / 2f
        val w = size.width
        drawCircle(color = tint, radius = r, center = Offset(w * 0.25f, cy))
        drawCircle(color = tint, radius = r, center = Offset(w * 0.50f, cy))
        drawCircle(color = tint, radius = r, center = Offset(w * 0.75f, cy))
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SubtleGray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun GridTabIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val boxSize = (size.width - 4.dp.toPx()) / 2f
        val gap = 4.dp.toPx()
        val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(boxSize, boxSize),
            cornerRadius = corner,
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(boxSize + gap, 0f),
            size = Size(boxSize, boxSize),
            cornerRadius = corner,
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, boxSize + gap),
            size = Size(boxSize, boxSize),
            cornerRadius = corner,
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(boxSize + gap, boxSize + gap),
            size = Size(boxSize, boxSize),
            cornerRadius = corner,
            style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun StatsBarTabIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 2.2.dp.toPx()
        val w = size.width
        val h = size.height

        drawLine(
            color = tint,
            start = Offset(w * 0.2f, h * 0.9f),
            end = Offset(w * 0.2f, h * 0.55f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.9f),
            end = Offset(w * 0.5f, h * 0.15f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.8f, h * 0.9f),
            end = Offset(w * 0.8f, h * 0.4f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PersonOutlineIcon() {
    Icon(
        imageVector = Icons.Default.Person,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun PeopleOutlineIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawCircle(
            color = Color.White,
            radius = w * 0.18f,
            center = Offset(w * 0.38f, h * 0.32f),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.56f),
            size = Size(w * 0.52f, h * 0.42f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.14f,
            center = Offset(w * 0.72f, h * 0.36f),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color.White,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.52f, h * 0.60f),
            size = Size(w * 0.40f, h * 0.36f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun GlobeOutlineIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val r = size.minDimension * 0.42f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color.White, radius = r, center = c, style = Stroke(width = stroke))
        drawOval(
            color = Color.White,
            topLeft = Offset(c.x - r * 0.48f, c.y - r),
            size = Size(r * 0.96f, r * 2f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = Color.White,
            start = Offset(c.x - r, c.y),
            end = Offset(c.x + r, c.y),
            strokeWidth = stroke
        )
    }
}
