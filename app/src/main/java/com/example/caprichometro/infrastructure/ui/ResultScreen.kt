package com.example.caprichometro.infrastructure.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.caprichometro.infrastructure.ui.components.FullScreenImageViewer
import androidx.compose.material.icons.filled.Search

private val DarkBackground = Color(0xFF0F0F0F)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)

@Composable
fun ResultScreen(
    caprice: Caprice?,
    viewModel: ActivePollViewModel? = null,
    onGoHome: () -> Unit,
    onNavigateToAuthorProfile: ((String) -> Unit)? = null
) {
    var isImageExpanded by remember { mutableStateOf(false) }
    val imageModel = remember(caprice?.imageUrl) {
        ImageModelHelper.parseImageModel(caprice?.imageUrl.orEmpty())
    }
    val authorInitial = caprice?.userId?.firstOrNull()?.uppercaseChar()?.toString() ?: "V"

    val vmState by viewModel?.state?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)

    LaunchedEffect(caprice?.id) {
        if (caprice != null && viewModel != null && vmState?.caprice?.id != caprice.id) {
            viewModel.initialize(caprice, caprice.userId)
        }
    }

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
                val canOpenAuthorProfile = !caprice?.userId.isNullOrBlank() && onNavigateToAuthorProfile != null
                Text(
                    text = "El veredicto",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (canOpenAuthorProfile) {
                                Modifier.clickable { onNavigateToAuthorProfile?.invoke(caprice!!.userId) }
                            } else Modifier
                        ),
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(LimeGreen, CircleShape)
                        .then(
                            if (canOpenAuthorProfile) {
                                Modifier.clickable { onNavigateToAuthorProfile?.invoke(caprice!!.userId) }
                            } else Modifier
                        ),
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = verdict?.headline ?: "Tus amigos\nhan opinado.",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = verdict?.subtitle ?: "Votación cerrada. Consulta el veredicto final.",
                color = TextGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            val hasImage = !caprice?.imageUrl.isNullOrBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .then(
                        if (hasImage) Modifier.clickable { isImageExpanded = true } else Modifier
                    )
            ) {
                if (hasImage) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = caprice?.question.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Ampliar foto",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                label = "A",
                answerText = caprice?.answer1 ?: "Opción A",
                percentage = opt1Pct,
                isWinner = winner == 1
            )
            Spacer(modifier = Modifier.height(12.dp))
            ResultBar(
                label = "B",
                answerText = caprice?.answer2 ?: "Opción B",
                percentage = opt2Pct,
                isWinner = winner == 2
            )

            Spacer(modifier = Modifier.height(18.dp))
            val totalVotes = verdict?.totalVotes ?: 0
            Text(
                text = when (totalVotes) {
                    0 -> "Ningún amigo votó durante el tiempo de la encuesta"
                    1 -> "1 amigo ha opinado"
                    else -> "$totalVotes amigos han opinado"
                },
                color = TextGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (isImageExpanded && !caprice?.imageUrl.isNullOrBlank()) {
        FullScreenImageViewer(
            imageUrl = caprice!!.imageUrl,
            contentDescription = caprice?.question.orEmpty(),
            onDismiss = { isImageExpanded = false }
        )
    }
}

@Composable
fun ResultBar(
    label: String,
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
                width = if (isWinner) 2.dp else 1.dp,
                color = if (isWinner) LimeGreen else Color(0xFF2C2C30),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        // Barra de progreso interior (anclada a la izquierda y ocupando la altura completa del contenedor)
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(
                            when {
                                isWinner -> LimeGreen.copy(alpha = 0.32f)
                                percentage > 0 -> LimeGreen.copy(alpha = 0.20f)
                                else -> Color.Transparent
                            }
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isWinner) LimeGreen else Color(0xFF2C2C30)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isWinner) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = answerText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.SemiBold
                    )
                    if (isWinner) {
                        Text(
                            text = "🏆 GANADOR",
                            color = LimeGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "$percentage%",
                color = if (isWinner || percentage > 0) LimeGreen else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
