package com.example.caprichometro.infrastructure.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.Vote

private val VotersLime = Color(0xFFD8FF52)
private val VotersCardBg = Color(0xFF1A1A1C)
private val VotersSheetBg = Color(0xFF161618)
private val VotersBorder = Color(0xFF2C2C2E)
private val VotersMuted = Color(0xFF9E9EA4)

@Composable
internal fun PollVotersButton(
    votes: List<Vote>,
    voterProfiles: Map<String, UserProfile>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (votes.isEmpty()) return
    val recentVoters = remember(votes) {
        votes.sortedByDescending { it.timestamp }
            .distinctBy { it.voterId }
            .take(4)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(VotersCardBg)
            .border(1.dp, VotersBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            recentVoters.forEachIndexed { index, vote ->
                val profile = voterProfiles[vote.voterId]
                val username = profile?.username?.takeIf { it.isNotBlank() } ?: "?"
                Box(
                    modifier = Modifier
                        .offset(x = (-6 * index).dp)
                        .border(1.5.dp, VotersCardBg, CircleShape)
                ) {
                    UserAvatar(
                        photoUrl = profile?.photoUrl.orEmpty(),
                        username = username,
                        size = 24.dp,
                        backgroundColor = VotersLime,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.poll_voters_button, votes.size),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = VotersLime,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollVotersSheet(
    caprice: Caprice,
    votes: List<Vote>,
    voterProfiles: Map<String, UserProfile>,
    onDismiss: () -> Unit
) {
    val sortedVotes = remember(votes) { votes.sortedByDescending { it.timestamp } }
    val groupA = remember(sortedVotes) { sortedVotes.filter { it.option == 1 } }
    val groupB = remember(sortedVotes) { sortedVotes.filter { it.option == 2 } }
    val fallbackUsername = stringResource(R.string.fallback_username)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VotersSheetBg,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.poll_voters_sheet_title, sortedVotes.size),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "header_a") {
                    Text(
                        text = stringResource(R.string.poll_voters_group_a, caprice.answer1, groupA.size),
                        color = VotersLime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (groupA.isEmpty()) {
                    item(key = "empty_a") {
                        Text(
                            text = stringResource(R.string.poll_voters_empty_group),
                            color = VotersMuted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    items(items = groupA, key = { "a_${it.voterId}_${it.timestamp}" }) { vote ->
                        VoterRow(
                            optionLabel = "A",
                            profile = voterProfiles[vote.voterId],
                            fallbackUsername = fallbackUsername
                        )
                    }
                }

                item(key = "divider_ab") {
                    HorizontalDivider(
                        color = VotersBorder,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                item(key = "header_b") {
                    Text(
                        text = stringResource(R.string.poll_voters_group_b, caprice.answer2, groupB.size),
                        color = VotersLime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (groupB.isEmpty()) {
                    item(key = "empty_b") {
                        Text(
                            text = stringResource(R.string.poll_voters_empty_group),
                            color = VotersMuted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    items(items = groupB, key = { "b_${it.voterId}_${it.timestamp}" }) { vote ->
                        VoterRow(
                            optionLabel = "B",
                            profile = voterProfiles[vote.voterId],
                            fallbackUsername = fallbackUsername
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoterRow(
    optionLabel: String,
    profile: UserProfile?,
    fallbackUsername: String
) {
    val username = profile?.username?.takeIf { it.isNotBlank() } ?: fallbackUsername
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VotersCardBg)
            .border(1.dp, VotersBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            photoUrl = profile?.photoUrl.orEmpty(),
            username = username,
            size = 34.dp,
            backgroundColor = VotersLime,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "@$username",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(VotersLime.copy(alpha = 0.16f))
                .border(1.dp, VotersLime.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = optionLabel,
                color = VotersLime,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
