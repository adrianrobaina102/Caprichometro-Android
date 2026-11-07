package com.example.caprichometro.infrastructure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import kotlinx.coroutines.delay

private val FeedBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF18181A)
private val CardBorder = Color(0xFF28282C)
private val OptionBg = Color(0xFF222225)
private val LimeAccent = Color(0xFFD4FF00)
private val TextSecondary = Color(0xFF9E9EA4)
private val ClosedBadgeBg = Color(0xFF2A2A2E)

@Composable
fun HomeScreen(
    viewModel: CapriceViewModel,
    onSelectFeedItem: (CapriceFeedItem) -> Unit,
    onNavigateToFriends: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Carga inicial al entrar en la pestaña de Inicio solo si no hay datos en memoria
    LaunchedEffect(Unit) {
        nowMillis = System.currentTimeMillis()
        if (state.feedItems.isEmpty()) {
            viewModel.loadFriendsFeed()
        }
    }

    // Reloj de refresco cada 60s para recalcular "Ahora / Xm / Xh / Xd" y el estado temporal sin consultar Firestore
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    Scaffold(
        containerColor = FeedBg,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Inicio",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Las dudas recientes de tus amigos",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            nowMillis = System.currentTimeMillis()
                            viewModel.loadFriendsFeed()
                        },
                        enabled = !state.isFeedLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CardBg)
                            .border(1.dp, CardBorder, CircleShape)
                    ) {
                        if (state.isFeedLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LimeAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar feed",
                                tint = LimeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (state.isFeedLoading && state.feedItems.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = LimeAccent,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isFeedLoading && state.feedItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = LimeAccent)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Cargando dudas de tus amigos...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                state.feedError != null && state.feedItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No se pudo cargar el feed",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.feedError ?: "Error desconocido",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                nowMillis = System.currentTimeMillis()
                                viewModel.loadFriendsFeed()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reintentar", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                state.feedItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(CardBg)
                                .border(1.dp, CardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "👀", fontSize = 30.sp)
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Nada por aquí todavía",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cuando tus amigos publiquen una compra o un plan, aparecerán aquí las 20 dudas más recientes.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    nowMillis = System.currentTimeMillis()
                                    viewModel.loadFriendsFeed()
                                },
                                enabled = !state.isFeedLoading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Actualizar", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = onNavigateToFriends,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LimeAccent,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Buscar amigos", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = state.feedItems,
                            key = { "${it.author.uid}_${it.caprice.id}_${it.caprice.timestamp}" }
                        ) { item ->
                            CapriceFeedCard(
                                item = item,
                                nowMillis = nowMillis,
                                onClick = { onSelectFeedItem(item) },
                                onNavigateToUserProfile = onNavigateToUserProfile
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapriceFeedCard(
    item: CapriceFeedItem,
    nowMillis: Long,
    onClick: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {}
) {
    val caprice = item.caprice
    val author = item.author
    val isActive = (nowMillis - caprice.timestamp) < PollNotificationScheduler.POLL_DURATION_MILLIS
    val relativeTime = remember(caprice.timestamp, nowMillis) {
        formatRelativeTime(caprice.timestamp, nowMillis)
    }
    val statusText = remember(caprice.timestamp, nowMillis) {
        formatPollStatus(caprice.timestamp, nowMillis)
    }
    val imageModel = remember(caprice.imageUrl) {
        ImageModelHelper.parseImageModel(caprice.imageUrl)
    }
    val typeEmoji = if (caprice.type.contains("compra", ignoreCase = true)) "🛍️" else "🎉"
    val initial = author.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // Cabecera: Avatar + @username clicables para ver perfil en modo visitante
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigateToUserProfile(author.uid) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(LimeAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "@${author.username}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$typeEmoji ${caprice.type} • $relativeTime",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive) LimeAccent.copy(alpha = 0.16f) else ClosedBadgeBg)
                    .border(
                        width = 1.dp,
                        color = if (isActive) LimeAccent else CardBorder,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    color = if (isActive) LimeAccent else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pregunta principal
        Text(
            text = caprice.question,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp
        )

        // Foto opcional debajo de la pregunta y antes de las opciones
        if (caprice.imageUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121214))
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = caprice.question,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dos opciones informativas
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedOptionPill(label = "A", text = caprice.answer1)
            FeedOptionPill(label = "B", text = caprice.answer2)
        }
    }
}

@Composable
private fun FeedOptionPill(label: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OptionBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(CardBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = LimeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatRelativeTime(timestampMillis: Long, nowMillis: Long): String {
    val diffMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L

    return when {
        minutes < 1L -> "Ahora"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        else -> "${days}d"
    }
}

internal fun formatPollStatus(timestampMillis: Long, nowMillis: Long): String {
    val totalDuration = PollNotificationScheduler.POLL_DURATION_MILLIS
    val closeTimestamp = timestampMillis + totalDuration
    val remainingMillis = closeTimestamp - nowMillis

    return if (remainingMillis > 0) {
        val remainingMin = kotlin.math.ceil(remainingMillis / 60_000.0).toLong().coerceAtLeast(1L)
        if (remainingMin == 1L) "Queda 1 min" else "Quedan $remainingMin min"
    } else {
        val closedSinceMillis = (nowMillis - closeTimestamp).coerceAtLeast(0L)
        val closedMinutes = closedSinceMillis / 60_000L
        val closedHours = closedMinutes / 60L
        val closedDays = closedHours / 24L

        when {
            closedMinutes < 1L -> "Cerró ahora"
            closedMinutes < 60L -> "Cerró hace ${closedMinutes} min"
            closedHours < 24L -> "Cerró hace ${closedHours}h"
            else -> "Cerró hace ${closedDays}d"
        }
    }
}
