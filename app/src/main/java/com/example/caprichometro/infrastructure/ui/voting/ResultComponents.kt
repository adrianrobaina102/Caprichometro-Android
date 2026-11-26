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
fun ResultBar(
    answerText: String,
    percentage: Int,
    isWinner: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isWinner) LimeGreen.copy(alpha = 0.14f) else Color(0xFF1A1A1D))
            .border(
                width = if (isWinner) 2.dp else 1.dp,
                color = if (isWinner) LimeGreen else Color(0xFF2C2C30),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWinner) {
                Text(
                    text = stringResource(R.string.poll_winner_badge),
                    color = LimeGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            Text(
                text = "$percentage%",
                color = if (isWinner || percentage > 0) LimeGreen else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = answerText,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
internal fun FinalDecisionCard(
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
internal fun DecisionOptionButtons(
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
