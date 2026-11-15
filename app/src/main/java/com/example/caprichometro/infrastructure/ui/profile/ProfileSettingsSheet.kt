package com.example.caprichometro.infrastructure.ui.profile

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.infrastructure.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileSettingsModalSheet(
    friendsCount: Int,
    blockedUsers: List<UserProfile>,
    onDismiss: () -> Unit,
    onEditProfile: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onUnblockUser: (blockedUserId: String) -> Unit,
    onSignOut: () -> Unit,
    onRequestDeleteAccount: () -> Unit
) {
    val context = LocalContext.current
    var showPrivacySubSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5A5A5E))
            )
        }
    ) {
        if (!showPrivacySubSheet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                SettingsRow(
                    icon = { PersonOutlineIcon() },
                    title = "Editar perfil",
                    onClick = onEditProfile
                )
                HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                SettingsRow(
                    icon = { PeopleOutlineIcon() },
                    title = if (friendsCount > 0) "Amigos ($friendsCount)" else "Amigos",
                    onClick = onNavigateToFriends
                )
                HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Notificaciones",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Ajustes de notificaciones no disponibles", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = if (blockedUsers.isNotEmpty()) {
                        "Privacidad (${blockedUsers.size} bloqueados)"
                    } else {
                        "Privacidad"
                    },
                    onClick = { showPrivacySubSheet = true }
                )
                HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                SettingsRow(
                    icon = { GlobeOutlineIcon() },
                    title = "Idioma",
                    onClick = {
                        Toast.makeText(context, "Idioma: Español", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSignOut)
                        .padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Cerrar sesión",
                        tint = BadgeRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Cerrar sesión",
                        color = BadgeRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Sub-vista de Privacidad: Usuarios Bloqueados y Eliminar Cuenta
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    IconButton(
                        onClick = { showPrivacySubSheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacidad y bloqueos",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "USUARIOS BLOQUEADOS · ${blockedUsers.size}",
                    color = SubtleGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (blockedUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardSurface, RoundedCornerShape(14.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tienes ningún usuario bloqueado.",
                            color = SubtleGray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    blockedUsers.forEach { blockedUser ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(CardSurface, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        Toast.makeText(
                                            context,
                                            "@${blockedUser.username} está bloqueado. Pulsa «Desbloquear» si quieres volver a interactuar.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(end = 8.dp)
                            ) {
                                UserAvatar(
                                    photoUrl = blockedUser.photoUrl,
                                    username = blockedUser.username,
                                    size = 34.dp,
                                    backgroundColor = avatarColorFor(blockedUser.username),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "@${blockedUser.username}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { onUnblockUser(blockedUser.uid) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = LimeGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Desbloquear", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = DividerDark, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRequestDeleteAccount,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A1416),
                        contentColor = BadgeRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = BadgeRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Eliminar cuenta permanentemente",
                        color = BadgeRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SubtleGray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PersonOutlineIcon() {
    Icon(
        imageVector = Icons.Default.Person,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun PeopleOutlineIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawCircle(
            color = Color.White,
            radius = w * 0.18f,
            center = Offset(w * 0.38f, h * 0.32f),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.56f),
            size = Size(w * 0.52f, h * 0.42f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.14f,
            center = Offset(w * 0.72f, h * 0.36f),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color.White,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.52f, h * 0.60f),
            size = Size(w * 0.40f, h * 0.36f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun GlobeOutlineIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val r = size.minDimension * 0.42f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color.White, radius = r, center = c, style = Stroke(width = stroke))
        drawOval(
            color = Color.White,
            topLeft = Offset(c.x - r * 0.48f, c.y - r),
            size = Size(r * 0.96f, r * 2f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = Color.White,
            start = Offset(c.x - r, c.y),
            end = Offset(c.x + r, c.y),
            strokeWidth = stroke
        )
    }
}
