package com.example.caprichometro.infrastructure.ui.home

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapricePrice
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.R
import com.example.caprichometro.application.usecase.voting.FeedPollResult
import kotlin.math.roundToInt
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.splitByDistanceRadius
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.infrastructure.util.ImageModelHelper


@Composable
internal fun CapriceFeedCard(
    item: CapriceFeedItem,
    nowMillis: Long,
    closedPollResult: FeedPollResult? = null,
    onRequestClosedPollResult: (String) -> Unit = {},
    resolvedImageUrl: String? = null,
    onRequestCapriceImage: (com.example.caprichometro.domain.model.Caprice) -> Unit = {},
    onClick: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    onOpenNonFriendProfile: (UserProfile) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onCommentsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val caprice = item.caprice
    val author = item.author
    val effectiveImageUrl = caprice.imageUrl.ifBlank { resolvedImageUrl.orEmpty() }
    val isActive = !PollTiming.isPollExpired(caprice.timestamp, nowMillis)

    LaunchedEffect(caprice.id, isActive) {
        if (!isActive) {
            onRequestClosedPollResult(caprice.id)
        }
    }
    LaunchedEffect(caprice.id, caprice.hasImage, effectiveImageUrl) {
        if (caprice.hasImage && effectiveImageUrl.isBlank()) {
            onRequestCapriceImage(caprice)
        }
    }
    val relativeTime = remember(caprice.timestamp, nowMillis, context) {
        formatRelativeTime(context, caprice.timestamp, nowMillis)
    }
    val statusText = remember(caprice.timestamp, nowMillis, context) {
        formatPollStatus(context, caprice.timestamp, nowMillis)
    }
    val imageModel = com.example.caprichometro.infrastructure.util.rememberImageModel(effectiveImageUrl)
    val typeEmoji = if (caprice.type.contains("compra", ignoreCase = true)) "🛍️" else "🎉"
    val typeDisplay = localizedCapriceType(context, caprice.type)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // Cabecera: Avatar + @username siempre reactivos (perfil si amigo/propio, ficha añadir si desconocido)
        val canOpenAuthor = item.canOpenAuthorProfile && author.uid.isNotBlank()
        val hasAuthorTapAction = author.uid.isNotBlank()
        val distanceLabel = remember(item.approximateDistanceMeters, context) {
            item.approximateDistanceMeters?.let { formatLocalizedDistanceMeters(context, it) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (hasAuthorTapAction) {
                            Modifier.clickable {
                                if (canOpenAuthor) {
                                    onNavigateToUserProfile(author.uid)
                                } else {
                                    onOpenNonFriendProfile(author)
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 2.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    photoUrl = author.photoUrl,
                    username = author.username,
                    size = 38.dp,
                    backgroundColor = LimeAccent,
                    fontSize = 16.sp
                )

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
                        text = "$typeEmoji $typeDisplay • $relativeTime",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (distanceLabel != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = LimeAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = distanceLabel,
                                color = LimeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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

        val hasFeedImage = effectiveImageUrl.isNotBlank()
        if (!hasFeedImage && caprice.price != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LimeAccent.copy(alpha = 0.14f))
                    .border(1.dp, LimeAccent.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = CapricePrice.formatEuros(caprice.price),
                    color = LimeAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Foto opcional debajo de la pregunta y antes de las opciones
        if (hasFeedImage) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121214))
                    .clickable { onImageClick(effectiveImageUrl) }
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = caprice.question,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (caprice.price != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(1.dp, LimeAccent.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = CapricePrice.formatEuros(caprice.price),
                            color = LimeAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dos opciones informativas (o resultados con porcentajes y avatares cuando está cerrada)
        val closedResult = if (!isActive) closedPollResult else null
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedOptionPill(
                label = "A",
                text = caprice.answer1,
                percentage = closedResult?.takeIf { it.totalVotes > 0 }?.option1Percentage,
                isWinner = closedResult?.winningOption == 1,
                voterAvatars = closedResult?.option1VoterAvatars.orEmpty(),
                totalVotersForOption = closedResult?.option1TotalVoters ?: 0
            )
            FeedOptionPill(
                label = "B",
                text = caprice.answer2,
                percentage = closedResult?.takeIf { it.totalVotes > 0 }?.option2Percentage,
                isWinner = closedResult?.winningOption == 2,
                voterAvatars = closedResult?.option2VoterAvatars.orEmpty(),
                totalVotersForOption = closedResult?.option2TotalVoters ?: 0
            )
            if (closedResult != null && closedResult.totalVotes == 0) {
                Text(
                    text = stringResource(R.string.feed_poll_no_votes),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }

        val sampleComments = remember(caprice.id, caprice.type, context) {
            sampleFeedComments(context, caprice)
        }
        Spacer(modifier = Modifier.height(10.dp))
        FeedCommentPreviewButton(
            previewComment = sampleComments.first(),
            sampleCount = sampleComments.size,
            onClick = onCommentsClick
        )
    }
}

@Composable
private fun FeedOptionPill(
    label: String,
    text: String,
    percentage: Int? = null,
    isWinner: Boolean = false,
    voterAvatars: List<UserProfile> = emptyList(),
    totalVotersForOption: Int = 0
) {
    val borderColor = if (isWinner) LimeAccent else CardBorder
    val bgColor = if (isWinner) LimeAccent.copy(alpha = 0.12f) else OptionBg
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isWinner) {
            Text(
                text = "🏆",
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (voterAvatars.isNotEmpty() || percentage != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (voterAvatars.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        voterAvatars.take(3).forEachIndexed { index, voter ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (-4 * index).dp)
                                    .border(1.dp, CardBg, CircleShape)
                            ) {
                                UserAvatar(
                                    photoUrl = voter.photoUrl,
                                    username = voter.username,
                                    size = 18.dp,
                                    backgroundColor = LimeAccent,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        val extraCount = totalVotersForOption - voterAvatars.take(3).size
                        if (extraCount > 0) {
                            Text(
                                text = "+$extraCount",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
                if (percentage != null) {
                    Text(
                        text = "$percentage%",
                        color = if (isWinner) LimeAccent else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
