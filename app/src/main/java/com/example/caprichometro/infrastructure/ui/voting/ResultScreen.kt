package com.example.caprichometro.infrastructure.ui.voting

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.caprichometro.infrastructure.ui.home.localizedCapriceType
import androidx.compose.ui.text.font.FontWeight
import com.example.caprichometro.domain.model.CapricePrice
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.caprichometro.R
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollState
import com.example.caprichometro.infrastructure.util.ImageModelHelper
import com.example.caprichometro.infrastructure.ui.components.FullScreenImageViewer
import com.example.caprichometro.infrastructure.ui.components.PollVotersButton
import com.example.caprichometro.infrastructure.ui.components.PollVotersSheet
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import androidx.compose.material.icons.filled.Search

private val DarkBackground = Color(0xFF0F0F0F)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)

@Composable
fun ResultScreen(
    caprice: Caprice?,
    viewModel: ActivePollViewModel? = null,
    currentUserId: String = "",
    authorUsername: String? = null,
    authorPhotoUrl: String? = null,
    authorLocationCell: String? = null,
    onCapriceUpdated: ((Caprice) -> Unit)? = null,
    onGoHome: () -> Unit,
    onNavigateToAuthorProfile: ((String) -> Unit)? = null,
    onOpenPublicAuthorInfo: (() -> Unit)? = null,
    canOpenAuthorProfileOverride: Boolean = true,
    approximateDistanceMeters: Double? = null
) {
    var isImageExpanded by remember { mutableStateOf(false) }
    val rawVmState by viewModel?.state?.collectAsState() ?: remember { mutableStateOf(null) }
    val vmState = rawVmState?.takeIf { it.caprice?.id == caprice?.id }
    val displayedCaprice = vmState?.caprice ?: caprice
    val isPublicNearby = !displayedCaprice?.nearbyCell.isNullOrBlank()

    val imageModel = com.example.caprichometro.infrastructure.util.rememberImageModel(
        displayedCaprice?.imageUrl.orEmpty()
    )
    val resolvedUsername = authorUsername?.trim()?.removePrefix("@")
        ?.takeIf { it.isNotBlank() }
        ?: vmState?.authorUsername?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
    val authorInitial = resolvedUsername?.firstOrNull()?.uppercaseChar()?.toString()
        ?: displayedCaprice?.userId?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "A"

    LaunchedEffect(caprice?.id, caprice?.finalDecision, currentUserId, approximateDistanceMeters, authorLocationCell) {
        val effectiveUserId = currentUserId.ifBlank { caprice?.userId.orEmpty() }
        if (caprice != null && viewModel != null) {
            viewModel.clearError()
            viewModel.initialize(caprice, effectiveUserId, resolvedUsername, approximateDistanceMeters, authorLocationCell)
            viewModel.refreshFinalDecision()
        }
    }

    val verdict = remember(vmState?.votes, vmState?.pollState, displayedCaprice) {
        if (displayedCaprice != null) {
            val expiredVerdict = (vmState?.pollState as? PollState.Expired)?.verdictResult
            expiredVerdict ?: CalculateVerdictUseCase().execute(displayedCaprice, vmState?.votes.orEmpty())
        } else null
    }
    var showVotersSheet by remember { mutableStateOf(false) }
    val pollVotes = vmState?.votes.orEmpty()
    LaunchedEffect(pollVotes, displayedCaprice?.id) {
        if (pollVotes.isNotEmpty() && viewModel != null) {
            viewModel.resolveVoterProfilesIfNeeded(pollVotes)
        }
    }
    val avatarUsername = resolvedUsername
        ?: displayedCaprice?.userId?.takeIf { it.isNotBlank() }
        ?: authorInitial
    val authorTapAction = resolveAuthorTapAction(
        authorUserId = displayedCaprice?.userId ?: caprice?.userId,
        fallbackUserId = currentUserId,
        canOpenAuthorProfile = canOpenAuthorProfileOverride,
        onNavigateToAuthorProfile = onNavigateToAuthorProfile,
        onOpenPublicAuthorInfo = onOpenPublicAuthorInfo
    )

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
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.result_screen_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (authorTapAction != null) {
                                Modifier.clickable { authorTapAction() }
                            } else Modifier
                        ),
                    textAlign = TextAlign.Center
                )
                UserAvatar(
                    photoUrl = authorPhotoUrl.orEmpty(),
                    username = avatarUsername,
                    size = 32.dp,
                    backgroundColor = LimeGreen,
                    fontSize = 14.sp,
                    modifier = if (authorTapAction != null) {
                        Modifier.clickable { authorTapAction() }
                    } else {
                        Modifier
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
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
                    text = stringResource(R.string.result_badge_closed),
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (resolvedUsername != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1A1A1A))
                        .then(
                            if (authorTapAction != null) {
                                Modifier.clickable { authorTapAction() }
                            } else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "@$resolvedUsername",
                        color = if (authorTapAction != null) LimeGreen else TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            CapriceLocationPill(
                distanceMeters = vmState?.approximateDistanceMeters ?: approximateDistanceMeters,
                placeName = vmState?.approximatePlaceName,
                isResolvingPlaceName = vmState?.isResolvingPlaceName == true,
                hasDistanceCell = !displayedCaprice?.effectiveDistanceCell(authorLocationCell).isNullOrBlank()
            )

            val isAuthor = displayedCaprice?.userId == currentUserId && currentUserId.isNotBlank()
            val finalDecisionSummary = displayedCaprice?.finalDecision?.let { decision ->
                val chosenAnswer = if (decision == 1) displayedCaprice.answer1 else displayedCaprice.answer2
                val prefix = stringResource(if (isAuthor) R.string.result_decision_your_final else R.string.result_decision_author_final)
                "$prefix: $chosenAnswer"
            }

            if (displayedCaprice?.question?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = displayedCaprice.question,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 29.sp
                )
            }

            val context = LocalContext.current
            val hasImage = !displayedCaprice?.imageUrl.isNullOrBlank()
            if (!hasImage && finalDecisionSummary != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LimeGreen.copy(alpha = 0.14f))
                        .border(1.dp, LimeGreen.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = finalDecisionSummary,
                        color = LimeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1C1C1E), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = localizedCapriceType(context, displayedCaprice?.type ?: stringResource(R.string.poll_fallback_type)),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                displayedCaprice?.price?.let { capricePrice ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LimeGreen.copy(alpha = 0.14f))
                            .border(1.dp, LimeGreen.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = CapricePrice.formatEuros(capricePrice),
                            color = LimeGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (hasImage) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                        .clickable { isImageExpanded = true }
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = caprice?.question.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (finalDecisionSummary != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = finalDecisionSummary,
                                color = LimeGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.poll_enlarge_photo_cd),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val opt1Pct = verdict?.option1Percentage?.toInt() ?: 0
            val opt2Pct = verdict?.option2Percentage?.toInt() ?: 0
            val winner = verdict?.winningOption

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ResultBar(
                    answerText = displayedCaprice?.answer1 ?: stringResource(R.string.result_option_a_fallback),
                    percentage = opt1Pct,
                    isWinner = winner == 1,
                    modifier = Modifier.weight(1f)
                )
                ResultBar(
                    answerText = displayedCaprice?.answer2 ?: stringResource(R.string.result_option_b_fallback),
                    percentage = opt2Pct,
                    isWinner = winner == 2,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            val totalVotes = verdict?.totalVotes ?: 0
            Text(
                text = if (totalVotes == 0) {
                    stringResource(if (isPublicNearby) R.string.result_votes_zero_public else R.string.result_votes_zero_friends)
                } else {
                    pluralStringResource(
                        if (isPublicNearby) R.plurals.result_votes_public else R.plurals.result_votes_friends,
                        totalVotes,
                        totalVotes
                    )
                },
                color = TextGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            if (pollVotes.isNotEmpty() && displayedCaprice != null) {
                Spacer(modifier = Modifier.height(14.dp))
                PollVotersButton(
                    votes = pollVotes,
                    voterProfiles = vmState?.voterProfiles.orEmpty(),
                    onClick = { showVotersSheet = true }
                )
            }

            if (showVotersSheet && displayedCaprice != null) {
                PollVotersSheet(
                    caprice = displayedCaprice,
                    votes = pollVotes,
                    voterProfiles = vmState?.voterProfiles.orEmpty(),
                    onDismiss = { showVotersSheet = false }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (displayedCaprice != null) {
                val isAuthor = currentUserId.isNotBlank() && displayedCaprice.userId == currentUserId
                val finalDecision = displayedCaprice.finalDecision
                val isSavingDecision = vmState?.isSavingFinalDecision == true

                FinalDecisionCard(
                    caprice = displayedCaprice,
                    isAuthor = isAuthor,
                    totalVotes = totalVotes,
                    finalDecision = finalDecision,
                    isSaving = isSavingDecision,
                    errorMessage = vmState?.errorMessage,
                    onClearError = { viewModel?.clearError() },
                    onSelectDecision = { option ->
                        viewModel?.confirmFinalDecision(option) { updated ->
                            onCapriceUpdated?.invoke(updated)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (isImageExpanded && !displayedCaprice?.imageUrl.isNullOrBlank()) {
        FullScreenImageViewer(
            imageUrl = displayedCaprice.imageUrl,
            contentDescription = displayedCaprice.question,
            onDismiss = { isImageExpanded = false }
        )
    }
}
