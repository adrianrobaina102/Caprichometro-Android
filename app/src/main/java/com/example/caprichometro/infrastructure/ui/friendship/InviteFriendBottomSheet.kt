package com.example.caprichometro.infrastructure.ui.friendship

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import com.example.caprichometro.infrastructure.ui.components.AppIcons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.ui.components.UserAvatar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.InviteDeepLink
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.abs

private val FriendSubtleGray = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InviteFriendBottomSheet(
    username: String?,
    photoUrl: String = "",
    onDismiss: () -> Unit,
    onOpenOwnProfile: () -> Unit = {},
    onShareInvite: () -> Unit,
    onCopyDirectLink: () -> Unit
) {
    val cleanUsername = username?.removePrefix("@")?.trim().orEmpty()
    val fallbackCircle = stringResource(R.string.friends_invite_fallback_circle)
    val displayUsername = if (cleanUsername.isNotEmpty()) "@$cleanUsername" else fallbackCircle
    val directUri = if (cleanUsername.isNotEmpty()) {
        InviteDeepLink.buildWebUrl(cleanUsername)
    } else {
        "https://adrianrobaina102.github.io/Caprichometro-Android/invite"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3C)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tarjeta visual estilo Pase VIP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1E18), RoundedCornerShape(22.dp))
                    .border(1.dp, LimeGreen.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = LimeGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = stringResource(R.string.friends_invite_vip_badge),
                        color = LimeGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                UserAvatar(
                    photoUrl = photoUrl,
                    username = cleanUsername.ifEmpty { "C" },
                    size = 44.dp,
                    backgroundColor = friendAvatarColorFor(cleanUsername.ifEmpty { "capricho" }),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpenOwnProfile() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = displayUsername,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.friends_invite_vip_desc),
                    color = FriendSubtleGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Píldora del enlace profundo clicable para copiar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF11120E))
                        .clickable { onCopyDirectLink() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = directUri,
                        color = LimeGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = stringResource(R.string.friends_invite_copy_cd),
                        tint = FriendSubtleGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botón principal: Compartir invitación nativa
            Button(
                onClick = onShareInvite,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeGreen,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.friends_invite_share_cta),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botón secundario: Copiar enlace directo
            OutlinedButton(
                onClick = onCopyDirectLink,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF333336)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = AppIcons.ContentCopy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.friends_invite_copy_cta),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
