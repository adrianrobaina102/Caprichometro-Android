package com.example.caprichometro.infrastructure.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.example.caprichometro.infrastructure.ui.components.AppIcons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.domain.model.CapricePrice
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.validation.DifferentOptionsValidator
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.domain.validation.MinLengthValidator
import com.example.caprichometro.domain.validation.NotBlankValidator
import com.example.caprichometro.domain.validation.ValidationResult
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.ui.caprice.CapriceAudienceSheet
import com.example.caprichometro.infrastructure.ui.caprice.CapricePhotoSourceSheet
import com.example.caprichometro.infrastructure.ui.caprice.getTempCameraUri
import com.example.caprichometro.infrastructure.util.ImageCompressor
import kotlinx.coroutines.CancellationException
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
    val capriceState by viewModel.state.collectAsState()

    var isCompraType by remember { mutableStateOf(true) }
    var questionText by remember { mutableStateOf("") }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var showAudienceSheet by remember { mutableStateOf(false) }
    var selectAllFriends by remember { mutableStateOf(true) }
    var customSelectedFriendIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onLocationPermissionChanged(hasPerm)
    }

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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onLocationPermissionChanged(isGranted)
        if (isGranted) {
            viewModel.setNearbyDiscoveryEnabled(true)
        } else {
            viewModel.onNearbyCreationPermissionDenied()
        }
    }

    val toggleNearbyDiscovery: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setNearbyDiscoveryEnabled(false)
        } else {
            val hasCoarsePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCoarsePermission) {
                viewModel.setNearbyDiscoveryEnabled(true)
            } else {
                locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    val questionValidatorChain = remember {
        val notBlank = NotBlankValidator("La pregunta")
        notBlank.setNext(MinLengthValidator("La pregunta", minLength = 6))
        notBlank
    }

    val optionsValidator = remember { DifferentOptionsValidator() }

    val parsedPriceResult = remember(priceText) { CapricePrice.parseOptional(priceText) }
    val isPriceValid = parsedPriceResult.isSuccess
    val priceErrorText = stringResource(R.string.create_price_invalid_error)
    val saveErrorTitleText = stringResource(R.string.create_save_error_title)
    val isFormFilled = questionText.isNotBlank() && answer1.isNotBlank() && answer2.isNotBlank() && isPriceValid
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    val submitCaprice: () -> Unit = {
        val questionResult = questionValidatorChain.validate(questionText)
        if (questionResult is ValidationResult.Error) {
            Toast.makeText(context, questionResult.message, Toast.LENGTH_SHORT).show()
        } else {
            val optionsResult = optionsValidator.validate(Pair(answer1, answer2))
            if (optionsResult is ValidationResult.Error) {
                Toast.makeText(context, optionsResult.message, Toast.LENGTH_SHORT).show()
            } else {
                val parsedPrice = parsedPriceResult.getOrNull()
                if (parsedPriceResult.isFailure) {
                    Toast.makeText(context, priceErrorText, Toast.LENGTH_SHORT).show()
                } else {
                    val typeString = if (isCompraType) "Compra" else "Plan"
                    saveErrorMessage = null
                    coroutineScope.launch {
                        isSubmitting = true
                        val trimmedQuestion = questionText.trim()
                        val imageBytes = selectedImageUri?.let { uri ->
                            try {
                                ImageCompressor.compressUriToWebP(context, uri)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (selectedImageUri != null && imageBytes == null) {
                            isSubmitting = false
                            Toast.makeText(
                                context,
                                "No se pudo procesar la foto seleccionada. Inténtalo de nuevo o quítala.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        val saveResult = viewModel.saveCaprice(
                            type = typeString,
                            questionText = trimmedQuestion,
                            answer1 = answer1.trim(),
                            answer2 = answer2.trim(),
                            imageBytes = imageBytes,
                            visibleToUserIds = effectiveSelectedFriendIds.toList(),
                            nearbyCell = if (capriceState.isNearbyDiscoveryEnabled) capriceState.preparedNearbyCell else null,
                            locationCell = capriceState.preparedLocationCell ?: capriceState.preparedNearbyCell,
                            price = parsedPrice
                        )

                        isSubmitting = false
                        if (saveResult.isSuccess) {
                            val createdCaprice = saveResult.getOrNull()
                            selectAllFriends = true
                            customSelectedFriendIds = emptySet()
                            questionText = ""
                            answer1 = ""
                            answer2 = ""
                            priceText = ""
                            selectedImageUri = null
                            saveErrorMessage = null
                            PollNotificationHelper.showCapriceCreatedNotification(
                                context = context,
                                capriceQuestion = createdCaprice?.question ?: trimmedQuestion,
                                capriceId = createdCaprice?.id.orEmpty(),
                                recipientUserId = createdCaprice?.userId.orEmpty()
                            )
                            onNavigateToActivePoll()
                        } else {
                            saveErrorMessage = saveResult.exceptionOrNull()?.message
                                ?: saveErrorTitleText
                        }
                    }
                }
            }
        }
    }

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
                    text = stringResource(R.string.create_screen_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                saveErrorMessage?.let { errorText ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B181A)),
                        border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.create_save_error_title),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = errorText,
                                    color = Color(0xFFFFB4B4),
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(
                                onClick = submitCaprice,
                                enabled = !isSubmitting,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_retry),
                                    color = LimeGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Button(
                    onClick = submitCaprice,
                    enabled = isFormFilled &&
                        !isSubmitting &&
                        !capriceState.isPreparingNearbyLocation &&
                        (!capriceState.isNearbyDiscoveryEnabled || !capriceState.preparedNearbyCell.isNullOrBlank()),
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
                            text = stringResource(
                                if (capriceState.isNearbyDiscoveryEnabled && !capriceState.preparedNearbyCell.isNullOrBlank()) {
                                    R.string.create_submit_general
                                } else {
                                    R.string.create_submit_friends
                                }
                            ),
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
                text = stringResource(R.string.create_type_label),
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
                    Text(text = stringResource(R.string.create_type_compra_chip), fontWeight = FontWeight.Bold)
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
                    Text(text = stringResource(R.string.create_type_plan_chip), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.create_question_label),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                placeholder = { Text(text = stringResource(R.string.create_question_placeholder), color = TextGray) },
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
                text = stringResource(R.string.create_option_a_label),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = answer1,
                onValueChange = { answer1 = it },
                placeholder = { Text(text = stringResource(R.string.create_option_a_placeholder), color = TextGray) },
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
                text = stringResource(R.string.create_option_b_label),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = answer2,
                onValueChange = { answer2 = it },
                placeholder = { Text(text = stringResource(R.string.create_option_b_placeholder), color = TextGray) },
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
                text = stringResource(R.string.create_price_label),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                placeholder = { Text(text = stringResource(R.string.create_price_placeholder), color = TextGray) },
                trailingIcon = {
                    Text(
                        text = "€",
                        color = LimeGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                },
                singleLine = true,
                isError = priceText.isNotBlank() && !isPriceValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    errorBorderColor = Color(0xFFFF5252),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    errorTextColor = Color.White
                )
            )
            if (priceText.isNotBlank() && !isPriceValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.create_price_invalid_error),
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.create_photo_optional_label),
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
                            imageVector = AppIcons.PhotoCamera,
                            contentDescription = "Añadir foto",
                            tint = LimeGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.create_add_photo_prompt),
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
                Icon(AppIcons.Schedule, contentDescription = null, tint = LimeGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = stringResource(R.string.create_duration_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = stringResource(R.string.create_duration_subtitle), color = TextGray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val audienceTitle = when {
                friends.isEmpty() -> stringResource(R.string.create_audience_no_friends)
                selectAllFriends || effectiveSelectedFriendIds.size == friends.size ->
                    stringResource(R.string.create_audience_all_friends, friends.size)
                effectiveSelectedFriendIds.isEmpty() -> stringResource(R.string.create_audience_zero_friends)
                else -> stringResource(R.string.create_audience_some_friends, effectiveSelectedFriendIds.size, friends.size)
            }
            val audienceSubtitle = stringResource(
                if (friends.isEmpty()) R.string.create_audience_sub_empty else R.string.create_audience_sub_pick
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .clickable { showAudienceSheet = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.Group, contentDescription = null, tint = LimeGreen)
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

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = LimeGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.create_general_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(
                                when {
                                    capriceState.isPreparingNearbyLocation -> R.string.create_general_preparing
                                    capriceState.isNearbyDiscoveryEnabled -> R.string.create_general_enabled
                                    else -> R.string.create_general_default_off
                                }
                            ),
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = capriceState.isNearbyDiscoveryEnabled,
                        onCheckedChange = toggleNearbyDiscovery,
                        enabled = !isSubmitting && !capriceState.isPreparingNearbyLocation,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = LimeGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF2C2C2E)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_general_description),
                    color = TextGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (capriceState.isPreparingNearbyLocation) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = LimeGreen,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.create_general_getting_zone),
                            color = LimeGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (capriceState.isNearbyDiscoveryEnabled && !capriceState.preparedNearbyCell.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.create_general_zone_ready),
                        color = LimeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                capriceState.nearbyCreationError?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMsg,
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { toggleNearbyDiscovery(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Reintentar", color = LimeGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                val settingsIntent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(settingsIntent)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Abrir Ajustes", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Modal selector: Cámara nativa vs Galería
        if (showPhotoSourceSheet) {
            CapricePhotoSourceSheet(
                hasSelectedImage = selectedImageUri != null,
                onDismiss = { showPhotoSourceSheet = false },
                onTakePhoto = {
                    showPhotoSourceSheet = false
                    launchCamera()
                },
                onPickGallery = {
                    showPhotoSourceSheet = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemovePhoto = {
                    showPhotoSourceSheet = false
                    selectedImageUri = null
                }
            )
        }

        if (showAudienceSheet) {
            CapriceAudienceSheet(
                friends = friends,
                selectAllFriends = selectAllFriends,
                effectiveSelectedFriendIds = effectiveSelectedFriendIds,
                onDismiss = { showAudienceSheet = false },
                onSelectionChange = { newSelectAll, newCustomIds ->
                    selectAllFriends = newSelectAll
                    customSelectedFriendIds = newCustomIds
                }
            )
        }
    }
}
