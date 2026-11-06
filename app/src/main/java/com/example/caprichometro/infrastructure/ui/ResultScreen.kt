package com.example.caprichometro.infrastructure.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollState
import com.example.caprichometro.infrastructure.util.ImageModelHelper

private val DarkBackground = Color(0xFF0F0F0F)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)

@Composable
fun ResultScreen(
    caprice: Caprice?,
    viewModel: ActivePollViewModel? = null,
    onGoHome: () -> Unit
) {
    val imageModel = remember(caprice?.imageUrl) {
        ImageModelHelper.parseImageModel(caprice?.imageUrl.orEmpty())
    }
    val authorInitial = caprice?.userId?.firstOrNull()?.uppercaseChar()?.toString() ?: "V"

    val vmState by viewModel?.state?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)

    val verdict = remember(vmState?.votes, vmState?.pollState, caprice) {
        if (caprice != null) {
            val expiredVerdict = (vmState?.pollState as? PollState.Expired)?.verdictResult
            expiredVerdict ?: CalculateVerdictUseCase().execute(caprice, vmState?.votes.orEmpty())
        } else null
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
                IconButton(onClick = onGoHome) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Text(
                    text = "El veredicto",
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
                    Text(authorInitial, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = "Volver con mis amigos",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "VOTACIÓN CERRADA",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = verdict?.headline ?: "Tus amigos\nhan opinado.",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = verdict?.subtitle ?: "Votación cerrada. Consulta el veredicto final.",
                color = TextGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
            ) {
                if (!caprice?.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = caprice?.question.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = caprice?.type ?: "Duda",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val opt1Pct = verdict?.option1Percentage?.toInt() ?: 0
            val opt2Pct = verdict?.option2Percentage?.toInt() ?: 0
            val winner = verdict?.winningOption

            ResultBar(
                answerText = caprice?.answer1 ?: "Opción A",
                percentage = opt1Pct,
                isWinner = winner == 1
            )
            Spacer(modifier = Modifier.height(12.dp))
            ResultBar(
                answerText = caprice?.answer2 ?: "Opción B",
                percentage = opt2Pct,
                isWinner = winner == 2
            )
        }
    }
}

@Composable
fun ResultBar(
    answerText: String,
    percentage: Int,
    isWinner: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percentage / 100f).coerceIn(0f, 1f),
        label = "result_bar_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1D))
            .border(
                1.dp,
                if (isWinner) LimeGreen else Color(0xFF2C2C30),
                RoundedCornerShape(14.dp)
            )
    ) {
        // Barra de progreso interior
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(if (isWinner) LimeGreen.copy(alpha = 0.28f) else Color(0xFF242428))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = answerText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percentage%",
                color = if (isWinner) LimeGreen else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
