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
        (hasMore || totalCount > HomeFeedViewModel.FEED_PAGE_SIZE)
    var selectedCommentItem by remember { mutableStateOf<CapriceFeedItem?>(null) }

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
                onImageClick = onImageClick,
                onCommentsClick = { selectedCommentItem = item }
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
                    onImageClick = onImageClick,
                    onCommentsClick = { selectedCommentItem = item }
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

    selectedCommentItem?.let { commentItem ->
        FeedCommentsPreviewSheet(
            item = commentItem,
            onDismiss = { selectedCommentItem = null }
        )
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
    val hasKnownRemaining = remaining > 0
    val nextBatch = remaining.coerceAtMost(HomeFeedViewModel.FEED_PAGE_SIZE)

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
            text = when {
                !hasMore -> pluralStringResource(R.plurals.home_pagination_all_shown, visibleCount, visibleCount)
                hasKnownRemaining -> pluralStringResource(R.plurals.home_pagination_showing, totalCount, visibleCount, totalCount)
                else -> stringResource(R.string.home_pagination_showing_loaded, visibleCount)
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
                    text = if (hasKnownRemaining) {
                        pluralStringResource(R.plurals.home_pagination_load_more, nextBatch, nextBatch)
                    } else {
                        stringResource(R.string.home_pagination_load_more_generic)
                    },
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
