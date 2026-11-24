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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.caprichometro.domain.model.CapricePrice
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.R
import com.example.caprichometro.application.usecase.FeedPollResult
import kotlin.math.roundToInt
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.splitByDistanceRadius
import com.example.caprichometro.infrastructure.ui.CapriceViewModel
import com.example.caprichometro.infrastructure.ui.HomeFeedTab
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.infrastructure.util.ImageModelHelper

internal val FeedBg = Color(0xFF0D0D0D)
internal val CardBg = Color(0xFF18181A)
internal val CardBorder = Color(0xFF28282C)
internal val OptionBg = Color(0xFF222225)
internal val LimeAccent = Color(0xFFD4FF00)
internal val TextSecondary = Color(0xFF9E9EA4)
internal val ClosedBadgeBg = Color(0xFF2A2A2E)

@Composable
internal fun FriendsLocationPermissionBanner(
    isPermanentlyDenied: Boolean,
    onAllowClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = LimeAccent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_location_banner_text),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onAllowClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(if (isPermanentlyDenied) R.string.action_open_settings else R.string.home_location_banner_activate),
                color = LimeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.home_location_banner_dismiss_cd),
                tint = TextSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
internal fun HomeFeedTabBar(
    selectedTab: HomeFeedTab,
    onSelectTab: (HomeFeedTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HomeFeedTabPill(
            label = stringResource(R.string.home_tab_friends),
            selected = selectedTab == HomeFeedTab.FRIENDS,
            onClick = { onSelectTab(HomeFeedTab.FRIENDS) },
            modifier = Modifier.weight(1f)
        )
        HomeFeedTabPill(
            label = stringResource(R.string.home_tab_general),
            selected = selectedTab == HomeFeedTab.GENERAL,
            onClick = { onSelectTab(HomeFeedTab.GENERAL) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeFeedTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) LimeAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
internal fun FeedLoadingState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = LimeAccent)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
internal fun FeedErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = LimeAccent,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.action_retry), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun FriendsEmptyState(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigateToFriends: () -> Unit
) {
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
            text = stringResource(R.string.home_empty_friends_title),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_friends_body),
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(stringResource(R.string.action_refresh), fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onNavigateToFriends,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.home_find_friends), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun FeedActionState(
    emoji: String,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
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
            Text(text = emoji, fontSize = 30.sp)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (secondaryLabel != null && onSecondaryClick != null) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(secondaryLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(primaryLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun FeedItemsList(
    items: List<CapriceFeedItem>,
    totalCount: Int,
    hasMore: Boolean,
    isBackgroundSyncing: Boolean,
    onLoadMore: () -> Unit,
    nowMillis: Long,
    closedPollResults: Map<String, FeedPollResult> = emptyMap(),
    onRequestClosedPollResult: (String) -> Unit = {},
    capriceImages: Map<String, String> = emptyMap(),
    onRequestCapriceImage: (com.example.caprichometro.domain.model.Caprice) -> Unit = {},
    onSelectFeedItem: (CapriceFeedItem) -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onOpenNonFriendProfile: (UserProfile) -> Unit,
    onImageClick: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    val sections = remember(items) { splitByDistanceRadius(items) }
    val showPaginationFooter = !isBackgroundSyncing &&
        (hasMore || totalCount > CapriceViewModel.FEED_PAGE_SIZE)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sección < 50 km sin cabecera ni texto adicional
        items(
            items = sections.localItems,
            key = { "${it.author.uid}_${it.caprice.id}_${it.caprice.timestamp}" }
        ) { item ->
            CapriceFeedCard(
                item = item,
                nowMillis = nowMillis,
                closedPollResult = closedPollResults[item.caprice.id],
                onRequestClosedPollResult = onRequestClosedPollResult,
                resolvedImageUrl = capriceImages[item.caprice.id],
                onRequestCapriceImage = onRequestCapriceImage,
                onClick = { onSelectFeedItem(item) },
                onNavigateToUserProfile = onNavigateToUserProfile,
                onOpenNonFriendProfile = onOpenNonFriendProfile,
                onImageClick = onImageClick
            )
        }

        if (sections.farItems.isNotEmpty()) {
            item(key = "far_caprices_section_header") {
                FarCapricesSectionHeader()
            }
            items(
                items = sections.farItems,
                key = { "far_${it.author.uid}_${it.caprice.id}_${it.caprice.timestamp}" }
            ) { item ->
                CapriceFeedCard(
                    item = item,
                    nowMillis = nowMillis,
                    closedPollResult = closedPollResults[item.caprice.id],
                    onRequestClosedPollResult = onRequestClosedPollResult,
                    resolvedImageUrl = capriceImages[item.caprice.id],
                    onRequestCapriceImage = onRequestCapriceImage,
                    onClick = { onSelectFeedItem(item) },
                    onNavigateToUserProfile = onNavigateToUserProfile,
                    onOpenNonFriendProfile = onOpenNonFriendProfile,
                    onImageClick = onImageClick
                )
            }
        }

        if (showPaginationFooter) {
            item(key = "feed_pagination_footer") {
                FeedPaginationFooter(
                    visibleCount = items.size,
                    totalCount = totalCount,
                    hasMore = hasMore,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun FarCapricesSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = CardBorder
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(CardBg)
                .border(1.dp, CardBorder, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.home_far_section_title),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.home_far_section_badge),
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = CardBorder
        )
    }
}

@Composable
private fun FeedPaginationFooter(
    visibleCount: Int,
    totalCount: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    val remaining = (totalCount - visibleCount).coerceAtLeast(0)
    val nextBatch = remaining.coerceAtMost(CapriceViewModel.FEED_PAGE_SIZE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasMore) {
                stringResource(R.string.home_pagination_showing, visibleCount, totalCount)
            } else {
                stringResource(R.string.home_pagination_all_shown, visibleCount)
            },
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        if (hasMore) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent.copy(alpha = 0.14f),
                    contentColor = LimeAccent
                ),
                border = BorderStroke(1.dp, LimeAccent)
            ) {
                Text(
                    text = stringResource(R.string.home_pagination_load_more, nextBatch),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

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
    onImageClick: (String) -> Unit = {}
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

        if (caprice.price != null) {
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
        if (effectiveImageUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.feed_enlarge_photo_cd),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
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
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isWinner) LimeAccent else CardBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isWinner) Color.Black else LimeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
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

internal fun localizedCapriceType(context: Context, rawType: String): String = when {
    rawType.contains("compra", ignoreCase = true) -> context.getString(R.string.type_compra)
    rawType.contains("plan", ignoreCase = true) -> context.getString(R.string.type_plan)
    rawType.contains("ropa", ignoreCase = true) -> context.getString(R.string.category_ropa)
    rawType.isBlank() -> context.getString(R.string.category_otros)
    else -> rawType
}

internal fun formatLocalizedDistanceMeters(context: Context, distanceMeters: Double): String {
    if (!distanceMeters.isFinite() || distanceMeters < 0.0 || distanceMeters < 1_000.0) {
        return context.getString(R.string.distance_your_zone)
    }
    val km = (distanceMeters / 1_000.0).roundToInt().coerceAtLeast(1)
    return context.getString(R.string.distance_approx_km, km)
}

internal fun formatRelativeTime(context: Context, timestampMillis: Long, nowMillis: Long): String {
    val diffMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L

    return when {
        minutes < 1L -> context.getString(R.string.feed_time_now)
        minutes < 60L -> context.getString(R.string.feed_time_minutes, minutes)
        hours < 24L -> context.getString(R.string.feed_time_hours, hours)
        else -> context.getString(R.string.feed_time_days, days)
    }
}

internal fun formatPollStatus(context: Context, timestampMillis: Long, nowMillis: Long): String {
    val closeTimestamp = PollTiming.calculateTriggerTime(timestampMillis)
    val remainingMillis = closeTimestamp - nowMillis

    return if (remainingMillis > 0) {
        val remainingMin = kotlin.math.ceil(remainingMillis / 60_000.0).toLong().coerceAtLeast(1L)
        if (remainingMin == 1L) {
            context.getString(R.string.poll_status_remaining_one)
        } else {
            context.getString(R.string.poll_status_remaining_many, remainingMin)
        }
    } else {
        val closedSinceMillis = (nowMillis - closeTimestamp).coerceAtLeast(0L)
        val closedMinutes = closedSinceMillis / 60_000L
        val closedHours = closedMinutes / 60L
        val closedDays = closedHours / 24L

        when {
            closedMinutes < 1L -> context.getString(R.string.poll_status_closed_now)
            closedMinutes < 60L -> context.getString(R.string.poll_status_closed_minutes, closedMinutes)
            closedHours < 24L -> context.getString(R.string.poll_status_closed_hours, closedHours)
            else -> context.getString(R.string.poll_status_closed_days, closedDays)
        }
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
    val closeTimestamp = PollTiming.calculateTriggerTime(timestampMillis)
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
