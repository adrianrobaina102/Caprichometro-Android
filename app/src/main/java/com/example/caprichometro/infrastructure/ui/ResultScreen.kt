package com.example.caprichometro.infrastructure.ui

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
import androidx.compose.ui.res.stringResource
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
    val vmState by viewModel?.state?.collectAsState() ?: remember { mutableStateOf(null) }
    val displayedCaprice = vmState?.caprice?.takeIf { it.id == caprice?.id } ?: caprice
    val isPublicNearby = !displayedCaprice?.nearbyCell.isNullOrBlank()

    val imageModel = remember(displayedCaprice?.imageUrl) {
        ImageModelHelper.parseImageModel(displayedCaprice?.imageUrl.orEmpty())
    }
    val resolvedUsername = authorUsername?.trim()?.removePrefix("@")
        ?.takeIf { it.isNotBlank() }
        ?: vmState?.authorUsername?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
    val authorInitial = resolvedUsername?.firstOrNull()?.uppercaseChar()?.toString()
        ?: displayedCaprice?.userId?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "A"

    LaunchedEffect(caprice?.id, currentUserId, approximateDistanceMeters, authorLocationCell) {
        val effectiveUserId = currentUserId.ifBlank { caprice?.userId.orEmpty() }
        if (caprice != null && viewModel != null && (vmState?.caprice?.id != caprice.id || vmState?.currentUserId != effectiveUserId)) {
            viewModel.initialize(caprice, effectiveUserId, resolvedUsername, approximateDistanceMeters, authorLocationCell)
        }
    }

    val verdict = remember(vmState?.votes, vmState?.pollState, caprice) {
        if (caprice != null) {
            val expiredVerdict = (vmState?.pollState as? PollState.Expired)?.verdictResult
            expiredVerdict ?: CalculateVerdictUseCase().execute(caprice, vmState?.votes.orEmpty())
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
                    text = if (isPublicNearby) "Volver al feed" else "Volver con mis amigos",
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
                .verticalScroll(rememberScrollState())
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

            displayedCaprice?.price?.let { capricePrice ->
                Spacer(modifier = Modifier.height(10.dp))
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val localizedHeadline = when {
                verdict == null -> stringResource(R.string.result_verdict_winner_headline)
                verdict.totalVotes == 0 -> stringResource(R.string.result_verdict_empty_headline)
                verdict.winningOption == null -> stringResource(R.string.result_verdict_tie_headline)
                else -> stringResource(R.string.result_verdict_winner_headline)
            }
            val localizedSubtitle = when {
                verdict == null -> stringResource(R.string.result_verdict_empty_subtitle)
                verdict.totalVotes == 0 -> stringResource(R.string.result_verdict_empty_subtitle)
                verdict.winningOption == 1 -> stringResource(
                    R.string.result_verdict_winner_subtitle,
                    caprice?.answer1.orEmpty(),
                    verdict.option1Percentage.toInt()
                )
                verdict.winningOption == 2 -> stringResource(
                    R.string.result_verdict_winner_subtitle,
                    caprice?.answer2.orEmpty(),
                    verdict.option2Percentage.toInt()
                )
                else -> stringResource(R.string.result_verdict_tie_subtitle)
            }

            Text(
                text = localizedHeadline,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = localizedSubtitle,
                color = TextGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            val hasImage = !caprice?.imageUrl.isNullOrBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
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
                    0 -> stringResource(if (isPublicNearby) R.string.result_votes_zero_public else R.string.result_votes_zero_friends)
                    1 -> stringResource(if (isPublicNearby) R.string.result_votes_one_public else R.string.result_votes_one_friend)
                    else -> stringResource(
                        if (isPublicNearby) R.string.result_votes_many_public else R.string.result_votes_many_friends,
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
                            text = stringResource(R.string.poll_winner_badge),
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

@Composable
private fun FinalDecisionCard(
    caprice: Caprice,
    isAuthor: Boolean,
    totalVotes: Int,
    finalDecision: Int?,
    isSaving: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onSelectDecision: (Int) -> Unit
) {
    var isRectifying by remember(caprice.id, finalDecision) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1D))
            .border(
                width = 1.dp,
                color = if (finalDecision != null) LimeGreen.copy(alpha = 0.6f) else Color(0xFF2C2C30),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        when {
            finalDecision != null && !(isAuthor && isRectifying) -> {
                val label = if (finalDecision == 1) "A" else "B"
                val answerText = if (finalDecision == 1) caprice.answer1 else caprice.answer2
                Text(
                    text = stringResource(if (isAuthor) R.string.result_decision_your_final else R.string.result_decision_author_final),
                    color = LimeGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$label · $answerText",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isAuthor) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            onClearError()
                            isRectifying = true
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LimeGreen.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LimeGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.result_decision_rectify_button),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            finalDecision != null && isAuthor && isRectifying -> {
                Text(
                    text = stringResource(R.string.result_decision_rectify_title),
                    color = Color(0xFFFFD54F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_decision_rectify_subtitle),
                    color = TextGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                DecisionOptionButtons(
                    caprice = caprice,
                    currentDecision = finalDecision,
                    isSaving = isSaving,
                    errorMessage = errorMessage,
                    onSelectDecision = onSelectDecision
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        onClearError()
                        isRectifying = false
                    },
                    enabled = !isSaving,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.action_cancel), color = TextGray, fontSize = 13.sp)
                }
            }
            totalVotes <= 0 -> {
                Text(
                    text = stringResource(R.string.result_decision_no_votes_title),
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_decision_no_votes_body),
                    color = TextGray,
                    fontSize = 13.sp
                )
            }
            isAuthor -> {
                Text(
                    text = stringResource(R.string.result_decision_pending_author_title),
                    color = Color(0xFFFFD54F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_decision_pending_author_question),
                    color = TextGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                DecisionOptionButtons(
                    caprice = caprice,
                    currentDecision = null,
                    isSaving = isSaving,
                    errorMessage = errorMessage,
                    onSelectDecision = onSelectDecision
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.result_decision_pending_friend_title),
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_decision_pending_friend_body),
                    color = TextGray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DecisionOptionButtons(
    caprice: Caprice,
    currentDecision: Int?,
    isSaving: Boolean,
    errorMessage: String?,
    onSelectDecision: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(1 to caprice.answer1, 2 to caprice.answer2).forEach { (option, answerText) ->
            val label = if (option == 1) "A" else "B"
            val isCurrent = currentDecision == option
            OutlinedButton(
                onClick = { onSelectDecision(option) },
                enabled = !isSaving && !isCurrent,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isCurrent) LimeGreen.copy(alpha = 0.7f) else Color(0xFF4A4A50)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = if (isCurrent) LimeGreen else Color.Gray
                )
            ) {
                Text(
                    text = if (isCurrent) "$label · $answerText ✓" else "$label · $answerText",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    if (isSaving) {
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = LimeGreen
        )
    }
    if (!errorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            color = Color(0xFFFF6B6B),
            fontSize = 12.sp
        )
    }
}
