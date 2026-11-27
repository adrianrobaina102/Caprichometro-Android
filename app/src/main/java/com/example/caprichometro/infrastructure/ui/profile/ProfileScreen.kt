package com.example.caprichometro.infrastructure.ui.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.example.caprichometro.infrastructure.ui.components.AppIcons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.caprichometro.R
import com.example.caprichometro.infrastructure.ui.components.FullScreenImageViewer
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.infrastructure.util.ImageCompressor
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollTiming
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

internal val DarkBackground = Color(0xFF0D0D0D)
internal val SheetBackground = Color(0xFF161616)
internal val CardSurface = Color(0xFF1C1C1E)
internal val LimeGreen = Color(0xFFB8E636)
internal val BadgeGreen = Color(0xFF9EE03A)
internal val BadgeRed = Color(0xFFE5484D)
internal val SubtleGray = Color(0xFF8E8E93)
internal val DividerDark = Color(0xFF232325)

private val PastelAvatarColors = listOf(
    Color(0xFFA8B8F0),
    Color(0xFFF4A9B8),
    Color(0xFF82C8A9),
    Color(0xFFF6D086),
    Color(0xFFC5A8F0)
)

internal fun avatarColorFor(username: String): Color {
    if (username.isBlank()) return PastelAvatarColors[0]
    val index = abs(username.lowercase().hashCode()) % PastelAvatarColors.size
    return PastelAvatarColors[index]
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
    onUserBlocked: (String) -> Unit = {},
    onSignedOut: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by rememberSaveable(targetUserId) { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showEditBioDialog by remember { mutableStateOf(false) }
    var showAvatarOptionsSheet by remember { mutableStateOf(false) }
    var tempCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showFriendOptionsSheet by remember { mutableStateOf(false) }
    var showRemoveFriendDialog by remember { mutableStateOf(false) }
    var showBlockFriendDialog by remember { mutableStateOf(false) }
    var isProfilePhotoExpanded by remember { mutableStateOf(false) }

    val photoUpdatedMessage = stringResource(R.string.profile_photo_updated_toast)
    val photoErrorMessage = stringResource(R.string.profile_photo_error_toast)

    val handleSelectedAvatarUri: (Uri) -> Unit = { uri ->
        coroutineScope.launch {
            try {
                val compressedBytes = ImageCompressor.compressUriToWebP(
                    context = context,
                    imageUri = uri,
                    maxDimension = 300,
                    quality = 75
                )
                val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                val dataUri = "data:image/webp;base64,$base64"
                viewModel.updateProfilePhoto(currentUserId, dataUri) {
                    Toast.makeText(context, photoUpdatedMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, photoErrorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) handleSelectedAvatarUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = tempCameraUri
        if (success && capturedUri != null) {
            handleSelectedAvatarUri(capturedUri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val uri = getTempProfileCameraUri(context)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Se necesita permiso de cámara para hacer fotos", Toast.LENGTH_SHORT).show()
        }
    }

    val launchAvatarCamera: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            try {
                val uri = getTempProfileCameraUri(context)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }


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

    LaunchedEffect(state.photoErrorMessage) {
        state.photoErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearPhotoError()
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.profile_delete_account_title), color = Color.White) },
            text = {
                Text(
                    stringResource(R.string.profile_delete_account_body),
                    color = SubtleGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        showSettingsSheet = false
                        viewModel.deleteAccount(currentUserId, state.profile?.username.orEmpty())
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = BadgeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = Color.White)
                }
            },
            containerColor = SheetBackground
        )
    }

    if (showRemoveFriendDialog) {
        val friendRemovedToast = stringResource(R.string.profile_friend_removed_toast)
        AlertDialog(
            onDismissRequest = { showRemoveFriendDialog = false },
            title = { Text(stringResource(R.string.profile_remove_friend_title), color = Color.White) },
            text = {
                Text(
                    stringResource(R.string.profile_remove_friend_body, state.profile?.username.orEmpty()),
                    color = SubtleGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveFriendDialog = false
                        viewModel.removeFriend(currentUserId, targetUserId) {
                            Toast.makeText(context, friendRemovedToast, Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = BadgeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFriendDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = Color.White)
                }
            },
            containerColor = SheetBackground
        )
    }

    if (showBlockFriendDialog) {
        val userBlockedToast = stringResource(R.string.profile_user_blocked_toast)
        AlertDialog(
            onDismissRequest = { showBlockFriendDialog = false },
            title = { Text(stringResource(R.string.profile_block_user_title), color = Color.White) },
            text = {
                Text(
                    stringResource(R.string.profile_block_user_body),
                    color = SubtleGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockFriendDialog = false
                        viewModel.blockUser(currentUserId, targetUserId) {
                            onUserBlocked(targetUserId)
                            Toast.makeText(context, userBlockedToast, Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_block), color = BadgeRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockFriendDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = Color.White)
                }
            },
            containerColor = SheetBackground
        )
    }

    if (showAvatarOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarOptionsSheet = false },
            containerColor = SheetBackground,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF48484A)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Editar perfil",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    onClick = {
                        showAvatarOptionsSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    color = CardSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = LimeGreen)
                        Text("Elegir foto de la galería", color = Color.White, fontSize = 15.sp)
                    }
                }

                Surface(
                    onClick = {
                        showAvatarOptionsSheet = false
                        launchAvatarCamera()
                    },
                    color = CardSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = LimeGreen)
                        Text("Hacer foto con la cámara", color = Color.White, fontSize = 15.sp)
                    }
                }

                if (state.profile?.photoUrl?.isNotBlank() == true) {
                    Surface(
                        onClick = {
                            showAvatarOptionsSheet = false
                            viewModel.updateProfilePhoto(currentUserId, "") {
                                Toast.makeText(context, "Foto de perfil eliminada", Toast.LENGTH_SHORT).show()
                            }
                        },
                        color = CardSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(AppIcons.DeleteForever, contentDescription = null, tint = BadgeRed)
                            Text("Quitar foto de perfil", color = BadgeRed, fontSize = 15.sp)
                        }
                    }
                }

                Surface(
                    onClick = {
                        showAvatarOptionsSheet = false
                        viewModel.clearBioError()
                        showEditBioDialog = true
                    },
                    color = CardSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                        Text(stringResource(R.string.profile_edit_bio_action), color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    if (showEditBioDialog) {
        var tempBio by remember(showEditBioDialog, state.profile?.uid) {
            mutableStateOf(state.profile?.bio.orEmpty())
        }
        AlertDialog(
            onDismissRequest = {
                if (!state.isSavingBio) {
                    viewModel.clearBioError()
                    showEditBioDialog = false
                }
            },
            title = { Text(stringResource(R.string.profile_settings_edit_profile), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempBio,
                        onValueChange = { tempBio = it },
                        enabled = !state.isSavingBio,
                        label = { Text(stringResource(R.string.profile_bio_label), color = SubtleGray) },
                        placeholder = { Text(stringResource(R.string.profile_bio_placeholder), color = SubtleGray.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = SubtleGray,
                            focusedBorderColor = LimeGreen,
                            unfocusedBorderColor = DividerDark,
                            disabledBorderColor = DividerDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.bioErrorMessage?.let { bioError ->
                        Text(
                            text = bioError,
                            color = BadgeRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isSavingBio,
                    onClick = {
                        viewModel.updateBio(currentUserId, tempBio) {
                            showEditBioDialog = false
                        }
                    }
                ) {
                    if (state.isSavingBio) {
                        CircularProgressIndicator(
                            color = LimeGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(stringResource(R.string.action_save), color = LimeGreen, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isSavingBio,
                    onClick = {
                        viewModel.clearBioError()
                        showEditBioDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_cancel), color = if (state.isSavingBio) SubtleGray else Color.White)
                }
            },
            containerColor = SheetBackground
        )
    }
    // Bottom Sheet de Ajustes (Perfil · Ajustes)
    if (showFriendOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFriendOptionsSheet = false },
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "@${state.profile?.username.orEmpty()}",
                    color = SubtleGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showFriendOptionsSheet = false
                            showRemoveFriendDialog = true
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.profile_remove_friend_action),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showFriendOptionsSheet = false
                            showBlockFriendDialog = true
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BadgeRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.profile_block_user_action),
                        color = BadgeRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    if (showSettingsSheet) {
        ProfileSettingsModalSheet(
            friendsCount = state.friends.size,
            blockedUsers = state.blockedUsers,
            onDismiss = { showSettingsSheet = false },
            onEditProfile = {
                showSettingsSheet = false
                viewModel.clearBioError()
                showEditBioDialog = true
            },
            onNavigateToFriends = {
                showSettingsSheet = false
                onNavigateToFriends()
            },
            onUnblockUser = { blockedUserId -> viewModel.unblockUser(currentUserId, blockedUserId) },
            unblockingUserIds = state.unblockingUserIds,
            onSignOut = {
                showSettingsSheet = false
                viewModel.signOut()
            },
            onRequestDeleteAccount = { showDeleteConfirmDialog = true }
        )
    }

    if (state.isLoading && state.profile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = LimeGreen)
        }
        return
    }

    // Pantalla Principal de Perfil (Perfil · Recuerdos y Perfil · Estadísticas)
    val rawUsername = state.profile?.username?.removePrefix("@")
        ?: if (state.isOwnProfile) "Mi Perfil" else "Usuario"
    val displayName = rawUsername.replaceFirstChar { it.uppercaseChar() }

    val totalDudas = state.caprices.size
    val expiredCaprices = state.caprices.filter { PollTiming.isPollExpired(it.timestamp) }
    val resolvedCaprices = expiredCaprices.filter { state.verdicts[it.id] != null || it.id in state.decidedTieIds }
    val totalResueltas = resolvedCaprices.size
    val positiveVerdicts = resolvedCaprices.count { state.verdicts[it.id] == true }
    val hiceCasoPercent = if (totalResueltas > 0) {
        ((positiveVerdicts.toFloat() / totalResueltas) * 100).toInt().coerceIn(0, 100)
    } else {
        0
    }

    val categoryCounts = remember(state.caprices) { ProfileCategory.countsOf(state.caprices) }
    val comprasCount = categoryCounts[ProfileCategory.COMPRAS] ?: 0
    val planesCount = categoryCounts[ProfileCategory.PLANES] ?: 0
    val ropaCount = categoryCounts[ProfileCategory.ROPA] ?: 0
    val otrosCount = categoryCounts[ProfileCategory.OTROS] ?: 0

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
                        contentDescription = stringResource(R.string.action_back),
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
                    onClick = { showFriendOptionsSheet = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_options),
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
                            contentDescription = stringResource(R.string.profile_back_to_memories_cd),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_options),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.profile_settings_cd),
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
            UserAvatar(
                photoUrl = state.profile?.photoUrl.orEmpty(),
                username = displayName,
                size = 104.dp,
                backgroundColor = avatarColorFor(rawUsername),
                fontSize = 38.sp,
                modifier = Modifier.clickable(enabled = !state.isOwnProfile || !state.isSavingPhoto) {
                    if (state.isOwnProfile) {
                        showAvatarOptionsSheet = true
                    } else if (!state.profile?.photoUrl.isNullOrBlank()) {
                        isProfilePhotoExpanded = true
                    } else {
                        Toast.makeText(context, "@$rawUsername aún no tiene foto de perfil", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            if (state.isSavingPhoto) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = LimeGreen,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
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
                            showAvatarOptionsSheet = true
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

        val displayBio = state.profile?.bio?.ifBlank { null }
            ?: stringResource(if (state.isOwnProfile) R.string.profile_bio_own_default else R.string.profile_bio_other_default)
        Text(
            text = displayBio,
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
            StatColumn(value = "$totalDudas", label = stringResource(R.string.profile_metric_doubts))
            StatColumn(value = "$totalResueltas", label = stringResource(R.string.profile_metric_resolved))
            StatColumn(value = "$hiceCasoPercent%", label = stringResource(R.string.profile_metric_listened))
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = DividerDark, thickness = 1.dp)

        // Barra de pestañas: Cuadrícula (Recuerdos) vs Estadísticas (Barras)
        val memoriesTabCd = stringResource(R.string.profile_tab_memories_cd)
        val statsTabCd = stringResource(R.string.profile_tab_stats_cd)
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pestaña 0: Cuadrícula
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = memoriesTabCd
                        role = Role.Tab
                    }
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
                    .semantics {
                        contentDescription = statsTabCd
                        role = Role.Tab
                    }
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

        val resolveCapriceWithImage: (Caprice) -> Caprice = { selected ->
            if (selected.imageUrl.isBlank()) {
                state.capriceImages[selected.id]?.let { selected.copy(imageUrl = it) } ?: selected
            } else {
                selected
            }
        }
        if (selectedTab == 0) {
            ProfileMemoriesTab(
                caprices = state.caprices,
                verdicts = state.verdicts,
                decidedTieIds = state.decidedTieIds,
                voteCounts = state.voteCounts,
                isOwnProfile = state.isOwnProfile,
                capriceImages = state.capriceImages,
                onRequestCapriceImage = viewModel::loadCapriceImageIfNeeded,
                hasMoreCaprices = state.hasMoreCaprices,
                isLoadingMoreCaprices = state.isLoadingMoreCaprices,
                onLoadMore = viewModel::loadMoreCaprices,
                onSelectCaprice = { selected -> onSelectCaprice(resolveCapriceWithImage(selected)) }
            )
        } else {
            ProfileStatsTab(
                hiceCasoPercent = hiceCasoPercent,
                totalResueltas = totalResueltas,
                totalDudas = totalDudas,
                comprasCount = comprasCount,
                planesCount = planesCount,
                ropaCount = ropaCount,
                otrosCount = otrosCount,
                caprices = state.caprices,
                isOwnProfile = state.isOwnProfile,
                onSelectCaprice = { selected -> onSelectCaprice(resolveCapriceWithImage(selected)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (isProfilePhotoExpanded && !state.profile?.photoUrl.isNullOrBlank()) {
        FullScreenImageViewer(
            imageUrl = state.profile!!.photoUrl,
            contentDescription = "Foto de perfil de @$rawUsername",
            onDismiss = { isProfilePhotoExpanded = false }
        )
    }

    if (state.isDeletingAccount || state.isPerformingFriendAction) {
        BackHandler(enabled = true) {}
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LimeGreen)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.isDeletingAccount) "Eliminando cuenta..." else "Procesando...",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
