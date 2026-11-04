package com.example.caprichometro.infrastructure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import kotlinx.coroutines.delay

private val DarkBackground = Color(0xFF0F0F0F)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)
private val OptionCardBg = Color(0xFF1A1A1D)
private val OptionCardBorder = Color(0xFF2C2C30)

@Composable
fun ActivePollScreen(
    caprice: Caprice?,
    authorUsername: String? = null,
    onNavigateBack: () -> Unit = {},
    onTimerFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val isFriendPoll = !authorUsername.isNullOrBlank()
    val questionText = caprice?.question ?: "Cargando duda desde Firebase..."

    val imageModel = remember(caprice?.imageUrl) {
        ImageModelHelper.parseImageModel(caprice?.imageUrl.orEmpty())
    }

    var timeDisplay by remember { mutableStateOf("--:--") }
    var isPollExpired by remember { mutableStateOf(false) }

    // Solo programa la notificación en segundo plano cuando la duda es propia
    LaunchedEffect(caprice?.timestamp, caprice?.question, isFriendPoll) {
        if (caprice != null && !isFriendPoll) {
            PollNotificationScheduler.schedulePollExpiration(context, caprice)
        }
    }

    LaunchedEffect(caprice, isFriendPoll) {
        if (caprice != null) {
            val totalTimeMillis = PollNotificationScheduler.POLL_DURATION_MILLIS

            while (true) {
                val elapsedTime = System.currentTimeMillis() - caprice.timestamp
                val remainingTime = totalTimeMillis - elapsedTime

                if (remainingTime <= 0) {
                    timeDisplay = "00:00"
                    isPollExpired = true
                    if (!isFriendPoll) {
                        PollNotificationHelper.showPollClosedNotification(context, caprice.question, caprice.id)
                        onTimerFinished()
                    }
                    break
                }

                isPollExpired = false
                val minutes = (remainingTime / 1000) / 60
                val seconds = (remainingTime / 1000) % 60

                timeDisplay = String.format("%02d:%02d", minutes, seconds)

                delay(1000L)
            }
        }
    }

    val headerTitle = if (isFriendPoll) "Duda de @$authorUsername" else "Tu duda está en el aire"
    val avatarInitial = authorUsername?.firstOrNull()?.uppercaseChar()?.toString() ?: "D"

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
                    isFriendPoll -> "tiempo restante para votar"
                    else -> "para que tus amigos opinen"
                },
                color = TextGray,
                fontSize = 14.sp
            )

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PollOptionRow(label = "A", text = caprice.answer1)
                    PollOptionRow(label = "B", text = caprice.answer2)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy((-12).dp)
            ) {
                VoterAvatar(initial = "L", backgroundColor = Color(0xFFF4B8CD))
                VoterAvatar(initial = "M", backgroundColor = Color(0xFFA4BFF5))
                VoterAvatar(initial = "S", backgroundColor = Color(0xFF8EDBB8))
                VoterAvatar(initial = "A", backgroundColor = LimeGreen)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "3 amigos han votado",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Los votos no se ven hasta que cierre.",
                color = TextGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "En la app real no se puede adelantar el cierre.",
                color = Color(0xFF555555),
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun PollOptionRow(label: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OptionCardBg)
            .border(1.dp, OptionCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(OptionCardBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = LimeGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VoterAvatar(initial: String, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(2.dp, DarkBackground, CircleShape)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
