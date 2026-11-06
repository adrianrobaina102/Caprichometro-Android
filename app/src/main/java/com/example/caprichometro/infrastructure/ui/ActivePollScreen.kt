package com.example.caprichometro.infrastructure.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollState
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBackground = Color(0xFF0F0F0F)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)
private val OptionCardBg = Color(0xFF1A1A1D)
private val OptionCardBorder = Color(0xFF2C2C30)
private val VotedBarBg = Color(0xFF28282D)

@Composable
fun ActivePollScreen(
    caprice: Caprice?,
    authorUsername: String? = null,
    viewModel: ActivePollViewModel? = null,
    currentUserId: String = "",
    onNavigateBack: () -> Unit = {},
    onTimerFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val isFriendPoll = !authorUsername.isNullOrBlank()
    val questionText = caprice?.question ?: "Cargando duda desde Firebase..."

    val imageModel = remember(caprice?.imageUrl) {
        ImageModelHelper.parseImageModel(caprice?.imageUrl.orEmpty())
    }

    // Inicialización del ViewModel reactivo si está presente
    LaunchedEffect(caprice?.id, currentUserId) {
        if (caprice != null && currentUserId.isNotBlank() && viewModel != null) {
            viewModel.initialize(caprice, currentUserId, authorUsername)
        }
    }

    val vmState by viewModel?.state?.collectAsState() ?: remember { mutableStateOf(null) }

    // Fallback de tiempo local si no hay ViewModel conectado
    var localTimeDisplay by remember { mutableStateOf("--:--") }
    var localIsExpired by remember { mutableStateOf(false) }

    LaunchedEffect(caprice, isFriendPoll) {
        if (caprice != null && viewModel == null) {
            val totalTimeMillis = PollNotificationScheduler.POLL_DURATION_MILLIS
            while (true) {
                val elapsedTime = System.currentTimeMillis() - caprice.timestamp
                val remainingTime = totalTimeMillis - elapsedTime

                if (remainingTime <= 0) {
                    localTimeDisplay = "00:00"
                    localIsExpired = true
                    if (!isFriendPoll) {
                        PollNotificationHelper.showPollClosedNotification(context, caprice.question, caprice.id)
                        onTimerFinished()
                    }
                    break
                }

                localIsExpired = false
                val minutes = (remainingTime / 1000) / 60
                val seconds = (remainingTime / 1000) % 60
                localTimeDisplay = String.format("%02d:%02d", minutes, seconds)
                delay(1000L)
            }
        }
    }

    // Solo programa la notificación local en segundo plano cuando la duda es propia
    LaunchedEffect(caprice?.timestamp, caprice?.question, isFriendPoll) {
        if (caprice != null && !isFriendPoll) {
            PollNotificationScheduler.schedulePollExpiration(context, caprice)
        }
    }

    // Resolución del estado según el Patrón Estado
    val currentPollState = vmState?.pollState
    val isPollExpired = when {
        currentPollState is PollState.Expired -> true
        viewModel == null -> localIsExpired
        else -> false
    }

    val timeDisplay = when (currentPollState) {
        is PollState.Active -> {
            val mins = (currentPollState.remainingMillis / 1000) / 60
            val secs = (currentPollState.remainingMillis / 1000) % 60
            String.format("%02d:%02d", mins, secs)
        }
        is PollState.Voted -> {
            val mins = (currentPollState.remainingMillis / 1000) / 60
            val secs = (currentPollState.remainingMillis / 1000) % 60
            String.format("%02d:%02d", mins, secs)
        }
        is PollState.Author -> {
            val mins = (currentPollState.remainingMillis / 1000) / 60
            val secs = (currentPollState.remainingMillis / 1000) % 60
            String.format("%02d:%02d", mins, secs)
        }
        is PollState.Expired -> "00:00"
        null -> localTimeDisplay
    }

    val headerTitle = if (isFriendPoll) "Duda de @$authorUsername" else "Tu duda está en el aire"
    val avatarInitial = authorUsername?.firstOrNull()?.uppercaseChar()?.toString()
        ?: caprice?.userId?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "C"

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Text(
                    text = headerTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(LimeGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(avatarInitial, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isFriendPoll) "@$authorUsername · ${(caprice?.type ?: "DUDA").uppercase()}" else "SOLO TUS AMIGOS",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = timeDisplay,
                color = if (isPollExpired) TextGray else LimeGreen,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 80.sp
            )
            Text(
                text = when {
                    isPollExpired -> "Votación cerrada"
                    currentPollState is PollState.Voted -> "¡Ya has votado! Esperando a los demás"
                    currentPollState is PollState.Author -> "Tus amigos están votando en directo"
                    isFriendPoll -> "Toca tu opción favorita para votar"
                    else -> "para que tus amigos opinen"
                },
                color = if (currentPollState is PollState.Voted) LimeGreen else TextGray,
                fontSize = 14.sp,
                fontWeight = if (currentPollState is PollState.Voted) FontWeight.SemiBold else FontWeight.Normal
            )

            // Hora explícita local de cierre acordada ("Cierra a las HH:mm" / "Cerró a las HH:mm")
            val closeTimeFormatted = remember(caprice?.timestamp) {
                if (caprice != null && caprice.timestamp > 0L) {
                    val closeTimestamp = caprice.timestamp + PollNotificationScheduler.POLL_DURATION_MILLIS
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(closeTimestamp))
                } else null
            }
            if (closeTimeFormatted != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPollExpired) "Cerró a las $closeTimeFormatted" else "Cierra a las $closeTimeFormatted",
                    color = TextGray.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (!caprice?.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = caprice.question,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF241B3B), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (caprice?.type?.contains("compra", ignoreCase = true) == true) "🛍️" else "🎉",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = caprice?.type ?: "Duda", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = questionText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            if (caprice != null && (caprice.answer1.isNotBlank() || caprice.answer2.isNotBlank())) {
                Spacer(modifier = Modifier.height(20.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val canVote = currentPollState is PollState.Active && !isPollExpired && !(vmState?.isCastingVote ?: false)
                    val showPercentages = currentPollState is PollState.Voted || currentPollState is PollState.Author

                    val opt1Pct = when (currentPollState) {
                        is PollState.Voted -> currentPollState.option1Percentage
                        is PollState.Author -> currentPollState.option1Percentage
                        else -> 0f
                    }
                    val opt2Pct = when (currentPollState) {
                        is PollState.Voted -> currentPollState.option2Percentage
                        is PollState.Author -> currentPollState.option2Percentage
                        else -> 0f
                    }
                    val userOption = (currentPollState as? PollState.Voted)?.selectedOption

                    PollOptionCard(
                        label = "A",
                        text = caprice.answer1,
                        percentage = opt1Pct,
                        showPercentage = showPercentages,
                        isSelectedByUser = userOption == 1,
                        clickable = canVote,
                        onClick = { viewModel?.castVote(1) }
                    )

                    PollOptionCard(
                        label = "B",
                        text = caprice.answer2,
                        percentage = opt2Pct,
                        showPercentage = showPercentages,
                        isSelectedByUser = userOption == 2,
                        clickable = canVote,
                        onClick = { viewModel?.castVote(2) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Contador real de votos de amigos en Firestore
            val totalVotesCount = when (currentPollState) {
                is PollState.Voted -> currentPollState.totalVotes
                is PollState.Author -> currentPollState.totalVotes
                else -> vmState?.votes?.size ?: 0
            }

            Text(
                text = when {
                    totalVotesCount == 0 -> "Todavía no hay votos de amigos"
                    totalVotesCount == 1 -> "1 amigo ha opinado"
                    else -> "$totalVotesCount amigos han opinado"
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    currentPollState is PollState.Active -> "Vota para ver los porcentajes de tus amigos."
                    else -> "Los votos se actualizan en vivo vía Firestore."
                },
                color = TextGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PollOptionCard(
    label: String,
    text: String,
    percentage: Float,
    showPercentage: Boolean,
    isSelectedByUser: Boolean,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = if (showPercentage) (percentage / 100f).coerceIn(0f, 1f) else 0f,
        label = "poll_percentage"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OptionCardBg)
            .border(
                width = if (isSelectedByUser) 2.dp else 1.dp,
                color = if (isSelectedByUser) LimeGreen else OptionCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = clickable, onClick = onClick)
    ) {
        // Barra animada de porcentaje en el fondo
        if (showPercentage && animatedPercentage > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedPercentage)
                    .background(if (isSelectedByUser) LimeGreen.copy(alpha = 0.28f) else VotedBarBg)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelectedByUser) LimeGreen else OptionCardBorder),
                contentAlignment = Alignment.Center
            ) {
                if (isSelectedByUser) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = label,
                        color = LimeGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            if (showPercentage) {
                Text(
                    text = "${percentage.toInt()}%",
                    color = if (isSelectedByUser) LimeGreen else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
