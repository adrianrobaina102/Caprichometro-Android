package com.example.caprichometro.infrastructure.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.validation.DifferentOptionsValidator
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.domain.validation.MinLengthValidator
import com.example.caprichometro.domain.validation.NotBlankValidator
import com.example.caprichometro.domain.validation.ValidationResult
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.util.ImageCompressor
import kotlinx.coroutines.launch
import java.io.File

// Colores del tema
private val DarkBackground = Color(0xFF0F0F0F)
private val DarkSurface = Color(0xFF1C1C1C)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapriceScreen(
    viewModel: CapriceViewModel,
    friends: List<UserProfile> = emptyList(),
    onNavigateToActivePoll: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCompraType by remember { mutableStateOf(true) }
    var questionText by remember { mutableStateOf("") }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var showAudienceSheet by remember { mutableStateOf(false) }
    var selectAllFriends by remember { mutableStateOf(true) }
    var customSelectedFriendIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(friends) {
        val validIds = friends.map { it.uid }.toSet()
        customSelectedFriendIds = customSelectedFriendIds.intersect(validIds)
    }

    val effectiveSelectedFriendIds = remember(selectAllFriends, customSelectedFriendIds, friends) {
        if (selectAllFriends) {
            friends.map { it.uid }.toSet()
        } else {
            customSelectedFriendIds
        }
    }

    // Launcher de la galería nativa (PickVisualMedia)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // Si el usuario selecciona foto, la asignamos; si cancela, se conserva la anterior
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // Launcher de captura directa con la cámara nativa (TakePicture)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        // Si el disparo fue correcto, fijamos la URI capturada; si canceló, se conserva la previa
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
        }
    }

    // Solicitud de permiso de cámara en tiempo de ejecución
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val uri = getTempCameraUri(context)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Se necesita permiso de cámara para hacer fotos", Toast.LENGTH_SHORT).show()
        }
    }

    val launchCamera = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val uri = getTempCameraUri(context)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    val questionValidatorChain = remember {
        val notBlank = NotBlankValidator("La pregunta")
        notBlank.setNext(MinLengthValidator("La pregunta", minLength = 6))
        notBlank
    }

    val optionsValidator = remember { DifferentOptionsValidator() }

    val isFormFilled = questionText.isNotBlank() && answer1.isNotBlank() && answer2.isNotBlank()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Antes de lanzarlo",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        bottomBar = {
            Button(
                onClick = {
                    val questionResult = questionValidatorChain.validate(questionText)
                    if (questionResult is ValidationResult.Error) {
                        Toast.makeText(context, questionResult.message, Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val optionsResult = optionsValidator.validate(Pair(answer1, answer2))
                    if (optionsResult is ValidationResult.Error) {
                        Toast.makeText(context, optionsResult.message, Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val typeString = if (isCompraType) "Compra" else "Plan"

                    coroutineScope.launch {
                        isSubmitting = true
                        val imageBytes = selectedImageUri?.let { uri ->
                            try {
                                ImageCompressor.compressUriToWebP(context, uri)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        val saveResult = viewModel.saveCaprice(
                            type = typeString,
                            questionText = questionText.trim(),
                            answer1 = answer1.trim(),
                            answer2 = answer2.trim(),
                            imageBytes = imageBytes,
                            visibleToUserIds = effectiveSelectedFriendIds.toList()
                        )

                        isSubmitting = false
                        if (saveResult.isSuccess) {
                            selectAllFriends = true
                            customSelectedFriendIds = emptySet()
                            PollNotificationHelper.showCapriceCreatedNotification(context, questionText.trim())
                            onNavigateToActivePoll()
                        } else {
                            Toast.makeText(context, "Error al guardar: ${saveResult.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = isFormFilled && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF2C2C2E),
                    disabledContentColor = Color.Gray
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Lanzar a mis amigos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "¿De qué se trata?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { isCompraType = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompraType) LimeGreen else DarkSurface,
                        contentColor = if (isCompraType) Color.Black else Color.White
                    )
                ) {
                    Text(text = "🛍️ Compra", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { isCompraType = false },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isCompraType) LimeGreen else DarkSurface,
                        contentColor = if (!isCompraType) Color.Black else Color.White
                    )
                ) {
                    Text(text = "🎉 Plan", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tu duda o capricho",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                placeholder = { Text(text = "Ej. ¿Me compro estas zapatillas?", color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Opción A (Respuesta 1)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = answer1,
                onValueChange = { answer1 = it },
                placeholder = { Text(text = "Ej. Sí, te quedan genial", color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Opción B (Respuesta 2)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = answer2,
                onValueChange = { answer2 = it },
                placeholder = { Text(text = "Ej. No, ahorra un poco", color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Foto opcional",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, LimeGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Foto seleccionada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { showPhotoSourceSheet = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(alpha = 0.75f),
                                contentColor = LimeGreen
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Cambiar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Quitar foto",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .clickable { showPhotoSourceSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Añadir foto",
                            tint = LimeGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Hacer foto o elegir de la galería",
                            color = TextGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = LimeGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Duración límite: 5 minutos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "Tras 5 min, la votación se cierra y se da el veredicto.", color = TextGray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val audienceTitle = when {
                friends.isEmpty() -> "Visible solo para ti (sin amigos aún)"
                selectAllFriends || effectiveSelectedFriendIds.size == friends.size ->
                    "Visible para todos tus amigos (${friends.size})"
                effectiveSelectedFriendIds.isEmpty() -> "Visible solo para ti (0 amigos)"
                else -> "Visible para ${effectiveSelectedFriendIds.size} de ${friends.size} amigos"
            }
            val audienceSubtitle = when {
                friends.isEmpty() -> "Añade amigos para enviarles tus votaciones."
                else -> "Toca para elegir quién recibe este capricho."
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .clickable { showAudienceSheet = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = LimeGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = audienceTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = audienceSubtitle, color = TextGray, fontSize = 12.sp)
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Elegir amigos",
                    tint = TextGray
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Modal selector: Cámara nativa vs Galería
        if (showPhotoSourceSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPhotoSourceSheet = false },
                containerColor = Color(0xFF1E1E22),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Foto de tu capricho",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showPhotoSourceSheet = false
                                launchCamera()
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Cámara",
                            tint = LimeGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Hacer foto con la cámara",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showPhotoSourceSheet = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Galería",
                            tint = LimeGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Elegir de la galería",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    if (selectedImageUri != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = Color(0xFF2C2C30)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showPhotoSourceSheet = false
                                    selectedImageUri = null
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Quitar foto",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Quitar foto",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        if (showAudienceSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAudienceSheet = false },
                containerColor = Color(0xFF1A1A1E),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "¿A qué amigos enviarlo?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (friends.isNotEmpty()) {
                            val allSelected = selectAllFriends || effectiveSelectedFriendIds.size == friends.size
                            TextButton(
                                onClick = {
                                    if (allSelected) {
                                        selectAllFriends = false
                                        customSelectedFriendIds = emptySet()
                                    } else {
                                        selectAllFriends = true
                                        customSelectedFriendIds = emptySet()
                                    }
                                }
                            ) {
                                Text(
                                    text = if (allSelected) "Ninguno" else "Todos",
                                    color = LimeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (friends.isEmpty()) {
                        Text(
                            text = "Todavía no tienes amigos aceptados en tu lista.",
                            color = TextGray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            val toggleFriendSelection = { friendUid: String ->
                                val updated = if (friendUid in effectiveSelectedFriendIds) {
                                    effectiveSelectedFriendIds - friendUid
                                } else {
                                    effectiveSelectedFriendIds + friendUid
                                }
                                if (updated.size == friends.size) {
                                    selectAllFriends = true
                                    customSelectedFriendIds = emptySet()
                                } else {
                                    selectAllFriends = false
                                    customSelectedFriendIds = updated
                                }
                            }
                            items(friends, key = { it.uid }) { friend ->
                                val isChecked = friend.uid in effectiveSelectedFriendIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { toggleFriendSelection(friend.uid) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { toggleFriendSelection(friend.uid) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = LimeGreen,
                                            checkmarkColor = Color.Black,
                                            uncheckedColor = TextGray
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    UserAvatar(
                                        photoUrl = friend.photoUrl,
                                        username = friend.username,
                                        size = 32.dp,
                                        backgroundColor = LimeGreen,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "@${friend.username}",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showAudienceSheet = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LimeGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "Listo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun getTempCameraUri(context: Context): Uri {
    val tempFile = File.createTempFile("caprice_capture_", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}
