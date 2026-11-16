package com.example.caprichometro.infrastructure.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.caprichometro.domain.model.Caprice
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.R

@Composable
internal fun ProfileStatsTab(
    hiceCasoPercent: Int,
    totalResueltas: Int,
    totalDudas: Int,
    comprasCount: Int,
    planesCount: Int,
    ropaCount: Int,
    otrosCount: Int,
    caprices: List<Caprice> = emptyList(),
    onSelectCaprice: (Caprice) -> Unit = {}
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetFilter by remember { mutableStateOf<ProfileCategory?>(null) }
    val openSheet: (ProfileCategory?) -> Unit = { category ->
        sheetFilter = category
        sheetOpen = true
    }

    if (sheetOpen) {
        ProfileCategoriesSheet(
            caprices = caprices,
            initialFilter = sheetFilter,
            onSelectCaprice = { caprice ->
                sheetOpen = false
                onSelectCaprice(caprice)
            },
            onDismiss = { sheetOpen = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta 1: Donut chart + Hice caso
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DecisionDonutChart(percent = hiceCasoPercent / 100f)
                    Text(
                        text = "$hiceCasoPercent%",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.profile_stats_listened_title),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.profile_stats_listened_subtitle),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.profile_stats_listened_detail, totalResueltas, totalDudas),
                        color = SubtleGray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Tarjeta 2: Mis categorías
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.profile_stats_categories_title),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.profile_stats_see_all),
                        color = LimeGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { openSheet(null) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    CategoryStatItem(
                        icon = { ShoppingBagIcon() },
                        onClick = { openSheet(ProfileCategory.COMPRAS) },
                        name = stringResource(R.string.category_compras),
                        count = comprasCount,
                        backgroundColor = Color(0xFF381A26)
                    )
                    CategoryStatItem(
                        icon = { PartyPopperIcon() },
                        onClick = { openSheet(ProfileCategory.PLANES) },
                        name = stringResource(R.string.category_planes),
                        count = planesCount,
                        backgroundColor = Color(0xFF241B3B)
                    )
                    CategoryStatItem(
                        icon = { HangerIcon() },
                        onClick = { openSheet(ProfileCategory.ROPA) },
                        name = stringResource(R.string.category_ropa),
                        count = ropaCount,
                        backgroundColor = Color(0xFF302B1B)
                    )
                    CategoryStatItem(
                        icon = { MoreDotsIcon() },
                        onClick = { openSheet(ProfileCategory.OTROS) },
                        name = stringResource(R.string.category_otros),
                        count = otrosCount,
                        backgroundColor = Color(0xFF242426)
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionDonutChart(percent: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 11.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = Color(0xFF2B2E24),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )

        val sweep = (percent * 360f).coerceIn(0f, 360f)
        drawArc(
            color = LimeGreen,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun CategoryStatItem(
    icon: @Composable () -> Unit,
    name: String,
    count: Int,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            color = SubtleGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ShoppingBagIcon(tint: Color = Color(0xFFF472B6)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.16f, h * 0.28f),
            size = Size(w * 0.68f, h * 0.60f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.33f, h * 0.14f),
            size = Size(w * 0.34f, h * 0.28f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun PartyPopperIcon(tint: Color = Color(0xFFA78BFA)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.82f)
            lineTo(w * 0.72f, h * 0.48f)
            lineTo(w * 0.48f, h * 0.22f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(w * 0.76f, h * 0.24f))
        drawCircle(color = tint, radius = 1.3.dp.toPx(), center = Offset(w * 0.84f, h * 0.40f))
        drawCircle(color = tint, radius = 1.4.dp.toPx(), center = Offset(w * 0.62f, h * 0.14f))
    }
}

@Composable
private fun HangerIcon(tint: Color = Color(0xFFFBBF24)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        drawArc(
            color = tint,
            startAngle = 160f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = Offset(w * 0.42f, h * 0.12f),
            size = Size(w * 0.20f, h * 0.20f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.32f)
            lineTo(w * 0.86f, h * 0.68f)
            lineTo(w * 0.14f, h * 0.68f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MoreDotsIcon(tint: Color = Color(0xFF9CA3AF)) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val r = 2.2.dp.toPx()
        val cy = size.height / 2f
        val w = size.width
        drawCircle(color = tint, radius = r, center = Offset(w * 0.25f, cy))
        drawCircle(color = tint, radius = r, center = Offset(w * 0.50f, cy))
        drawCircle(color = tint, radius = r, center = Offset(w * 0.75f, cy))
    }
}
