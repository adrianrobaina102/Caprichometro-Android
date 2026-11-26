package com.example.caprichometro.infrastructure.ui.voting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.caprichometro.domain.model.CapricePrice
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.infrastructure.ui.home.formatLocalizedDistanceMeters
import com.example.caprichometro.domain.model.PollState
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import com.example.caprichometro.infrastructure.ui.components.FullScreenImageViewer
import com.example.caprichometro.infrastructure.ui.components.PollVotersButton
import com.example.caprichometro.infrastructure.ui.components.PollVotersSheet
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import androidx.compose.material.icons.filled.Search
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val PollDarkBackground = Color(0xFF0F0F0F)
internal val PollLimeGreen = Color(0xFFD4FF00)
internal val PollTextGray = Color(0xFFAAAAAA)
internal val PollOptionCardBg = Color(0xFF1A1A1D)
internal val PollOptionCardBorder = Color(0xFF2C2C30)


/**
 * Resuelve la acción al pulsar la cabecera/avatar del autor sin exponer perfiles ajenos no amigos.
 */
internal fun resolveAuthorTapAction(
    authorUserId: String?,
    fallbackUserId: String? = null,
    canOpenAuthorProfile: Boolean,
    onNavigateToAuthorProfile: ((String) -> Unit)?,
    onOpenPublicAuthorInfo: (() -> Unit)?
): (() -> Unit)? {
    val resolvedTargetId = authorUserId?.takeIf { it.isNotBlank() }
        ?: fallbackUserId?.takeIf { it.isNotBlank() }
    return when {
        canOpenAuthorProfile && resolvedTargetId != null && onNavigateToAuthorProfile != null -> {
            { onNavigateToAuthorProfile(resolvedTargetId) }
        }
        !canOpenAuthorProfile && onOpenPublicAuthorInfo != null -> {
            onOpenPublicAuthorInfo
        }
        else -> null
    }
}

@Composable
internal fun CapriceLocationPill(
    distanceMeters: Double?,
    placeName: String?,
    isResolvingPlaceName: Boolean,
    hasDistanceCell: Boolean
) {
    val context = LocalContext.current
    val distanceLabel = distanceMeters?.let { formatLocalizedDistanceMeters(context, it) }
    val zoneLabel = when {
        distanceLabel != null && !placeName.isNullOrBlank() -> "$distanceLabel · $placeName"
        distanceLabel != null -> distanceLabel
        isResolvingPlaceName -> stringResource(R.string.location_resolving_approx_zone)
        !placeName.isNullOrBlank() -> stringResource(R.string.location_approx_zone_with_place, placeName)
        hasDistanceCell -> stringResource(R.string.location_approx_generic)
        else -> null
    } ?: return

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .background(PollOptionCardBg, RoundedCornerShape(50))
            .border(1.dp, PollOptionCardBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = PollLimeGreen,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = zoneLabel,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun PollOptionCard(
    label: String,
    text: String,
    percentage: Float,
    showPercentage: Boolean,
    isSelectedByUser: Boolean,
    isWinner: Boolean = false,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = if (showPercentage) (percentage / 100f).coerceIn(0f, 1f) else 0f,
        label = "poll_percentage"
    )

    val borderColor = when {
        isWinner || isSelectedByUser -> PollLimeGreen
        showPercentage && percentage > 0f -> PollLimeGreen.copy(alpha = 0.45f)
        else -> PollOptionCardBorder
    }
    val borderWidth = if (isWinner || isSelectedByUser) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PollOptionCardBg)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = clickable, onClick = onClick)
    ) {
        // Barra animada de porcentaje en el fondo (anclada a la izquierda y con altura coincidente con la tarjeta)
        if (showPercentage && animatedPercentage > 0f) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedPercentage)
                        .background(
                            when {
                                isWinner || isSelectedByUser -> PollLimeGreen.copy(alpha = 0.32f)
                                percentage > 0f -> PollLimeGreen.copy(alpha = 0.22f)
                                else -> Color.Transparent
                            }
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectedByUser) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(PollLimeGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (isWinner) FontWeight.Bold else FontWeight.SemiBold
                )
                if (isWinner) {
                    Text(
                        text = stringResource(R.string.poll_winner_badge),
                        color = PollLimeGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (showPercentage) {
                Text(
                    text = "${percentage.toInt()}%",
                    color = if (isWinner || isSelectedByUser || percentage > 0f) PollLimeGreen else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
