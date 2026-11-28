package com.example.caprichometro.infrastructure.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import com.example.caprichometro.infrastructure.ui.components.localizedUiMessage

private val SheetLimeAccent = Color(0xFFB8E636)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeneralAuthorProfileSheet(
    author: UserProfile,
    cleanUsername: String,
    isSelfOrFriend: Boolean,
    isSendingRequest: Boolean,
    isRequestSent: Boolean,
    requestError: String?,
    onDismiss: () -> Unit,
    onOpenFullProfile: () -> Unit,
    onSendFriendRequest: () -> Unit,
    onSearchFriend: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3C)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserAvatar(
                photoUrl = author.photoUrl,
                username = cleanUsername,
                size = 68.dp,
                backgroundColor = SheetLimeAccent,
                fontSize = 26.sp,
                modifier = Modifier.border(1.5.dp, SheetLimeAccent.copy(alpha = 0.45f), CircleShape)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "@$cleanUsername",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = author.bio.takeIf { it.isNotBlank() }
                    ?: stringResource(
                        if (isSelfOrFriend) {
                            R.string.general_author_bio_friend_fallback
                        } else {
                            R.string.general_author_bio_public_fallback
                        }
                    ),
                color = Color(0xFF9E9EA6),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (!isSelfOrFriend && !requestError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = localizedUiMessage(requestError),
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = if (isSelfOrFriend) onOpenFullProfile else onSendFriendRequest,
                enabled = isSelfOrFriend || (!isSendingRequest && !isRequestSent),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SheetLimeAccent,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF232329),
                    disabledContentColor = SheetLimeAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(
                        when {
                            isSelfOrFriend -> R.string.general_author_view_full_profile
                            isSendingRequest -> R.string.general_author_sending_request
                            isRequestSent -> R.string.general_author_request_sent
                            else -> R.string.general_author_send_request
                        }
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (!isSelfOrFriend) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onSearchFriend) {
                    Text(
                        text = stringResource(R.string.general_author_open_in_friends),
                        color = Color(0xFF9E9EA6),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
