package com.example.caprichometro.infrastructure.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.caprichometro.infrastructure.ui.components.AppIcons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import kotlin.math.abs

internal data class MemoryItem(
    val title: String,
    val subtitle: String = "",
    val emoji: String,
    val imageUrl: String = "",
    val gradientColors: List<Color>,
    val accepted: Boolean?,
    val isDecidedTie: Boolean = false,
    val isActive: Boolean = false,
    val isDecisionPending: Boolean = false,
    val extraCount: Int = 0
)

private fun capriceToMemory(
    caprice: Caprice,
    imageUrlOverride: String? = null,
    accepted: Boolean?,
    isDecidedTie: Boolean = false,
    isActive: Boolean,
    isDecisionPending: Boolean = false
): MemoryItem {
    val emoji = when (caprice.type.lowercase()) {
        "compra", "compras" -> "\uD83D\uDECD\uFE0F"
        "comida", "cena", "almuerzo" -> "\uD83C\uDF55"
        "ropa" -> "\uD83D\uDC55"
        "plan", "planes", "viaje", "fiesta" -> "\uD83C\uDF89"
        else -> "\u2728"
    }

    val hash = abs(caprice.question.hashCode() + caprice.id.hashCode())
    val gradientPalette = listOf(
        listOf(Color(0xFF8C7A6B), Color(0xFF4A3F37)),
        listOf(Color(0xFF5D7599), Color(0xFFD99B66), Color(0xFF3D4E5E)),
        listOf(Color(0xFF8C3B2B), Color(0xFFD97925)),
        listOf(Color(0xFF4A154B), Color(0xFF8E2DE2), Color(0xFF1A0933)),
        listOf(Color(0xFF634832), Color(0xFF9C7A5B)),
        listOf(Color(0xFF3B4D6B), Color(0xFF1E293B))
    )
    val colors = gradientPalette[hash % gradientPalette.size]

    return MemoryItem(
        title = caprice.question,
        subtitle = "${caprice.answer1} / ${caprice.answer2}",
        emoji = emoji,
        imageUrl = caprice.imageUrl.ifBlank { imageUrlOverride.orEmpty() },
        gradientColors = colors,
        accepted = accepted,
        isDecidedTie = isDecidedTie,
        isActive = isActive,
        isDecisionPending = isDecisionPending
    )
}

@Composable
internal fun ProfileMemoriesTab(
    caprices: List<Caprice>,
    verdicts: Map<String, Boolean?>,
    decidedTieIds: Set<String>,
    voteCounts: Map<String, Int>,
    isOwnProfile: Boolean,
    capriceImages: Map<String, String> = emptyMap(),
    onRequestCapriceImage: (Caprice) -> Unit = {},
    hasMoreCaprices: Boolean = false,
    isLoadingMoreCaprices: Boolean = false,
    onLoadMore: () -> Unit = {},
    onSelectCaprice: (Caprice) -> Unit
) {
    val displayMemories = if (caprices.isNotEmpty()) {
        caprices.map { caprice ->
            val isExpired = PollTiming.isPollExpired(caprice.timestamp)
            capriceToMemory(
                caprice = caprice,
                imageUrlOverride = capriceImages[caprice.id],
                accepted = verdicts[caprice.id],
                isDecidedTie = caprice.id in decidedTieIds,
                isActive = !isExpired,
                isDecisionPending = isOwnProfile &&
                    isExpired &&
                    caprice.finalDecision == null &&
                    (voteCounts[caprice.id] ?: 0) > 0
            )
        }
    } else {
        emptyList()
    }

    if (displayMemories.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
                .background(CardSurface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "💭", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(if (isOwnProfile) R.string.profile_memories_empty_own else R.string.profile_memories_empty_other),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(if (isOwnProfile) R.string.profile_memories_empty_own_sub else R.string.profile_memories_empty_other_sub),
                    color = SubtleGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val rows = displayMemories.chunked(3)
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEachIndexed { itemIndex, item ->
                    val capriceIndex = rowIndex * 3 + itemIndex
                    val correspondingCaprice = caprices.getOrNull(capriceIndex)
                    if (correspondingCaprice != null) {
                        LaunchedEffect(correspondingCaprice.id, correspondingCaprice.hasImage, item.imageUrl) {
                            if (correspondingCaprice.hasImage && item.imageUrl.isBlank()) {
                                onRequestCapriceImage(correspondingCaprice)
                            }
                        }
                    }
                    MemoryCard(
                        item = item,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.78f)
                            .clickable {
                                if (correspondingCaprice != null) {
                                    onSelectCaprice(correspondingCaprice)
                                }
                            }
                    )
                }
                if (rowItems.size < 3) {
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (hasMoreCaprices) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = onLoadMore,
                    enabled = !isLoadingMoreCaprices,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoadingMoreCaprices) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.profile_memories_load_more),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.profile_memories_footer),
            color = Color(0xFF3A3A3C),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun MemoryCard(item: MemoryItem, modifier: Modifier = Modifier) {
    val imageModel = remember(item.imageUrl) {
        ImageModelHelper.parseImageModel(item.imageUrl)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(item.gradientColors))
    ) {
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageModel,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            startY = 60f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = if (item.imageUrl.isNotBlank()) Arrangement.Bottom else Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (item.imageUrl.isBlank()) {
                Text(text = item.emoji, fontSize = 34.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = item.title,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (item.subtitle.isNotBlank() && item.imageUrl.isBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (item.isDecisionPending) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xFF2B2410), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.profile_badge_pending),
                    color = Color(0xFFFFD54F),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (item.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E3D18)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\u23F1\uFE0F", fontSize = 12.sp)
            }
        } else if (item.accepted != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (item.accepted) BadgeGreen else BadgeRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.accepted) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (item.accepted) Color(0xFF1A2E05) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else if (item.isDecidedTie) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(BadgeGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = stringResource(R.string.profile_badge_tie_cd),
                    tint = Color(0xFF1A2E05),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (item.extraCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+${item.extraCount}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
