package com.example.caprichometro.infrastructure.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.infrastructure.ui.home.formatPollStatus

/**
 * Hoja inferior de «Ver todas»: desglose por categoría y lista filtrable de dudas.
 * [initialFilter] == null equivale a «Todas».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileCategoriesSheet(
    caprices: List<Caprice>,
    initialFilter: ProfileCategory?,
    onSelectCaprice: (Caprice) -> Unit,
    onDismiss: () -> Unit,
    nowMillis: Long = System.currentTimeMillis()
) {
    var filter by remember { mutableStateOf(initialFilter) }
    val counts = remember(caprices) { ProfileCategory.countsOf(caprices) }
    val visible = remember(caprices, filter) {
        caprices
            .filter { filter == null || ProfileCategory.of(it.type) == filter }
            .sortedByDescending { it.timestamp }
    }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Tus dudas por categoría",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    label = "Todas (${caprices.size})",
                    selected = filter == null,
                    onClick = { filter = null }
                )
                ProfileCategory.entries.forEach { category ->
                    CategoryChip(
                        label = "${category.label} (${counts[category] ?: 0})",
                        selected = filter == category,
                        onClick = { filter = category }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (visible.isEmpty()) {
                Text(
                    text = "No hay dudas en esta categoría.",
                    color = SubtleGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { caprice ->
                        CategoryCapriceRow(
                            caprice = caprice,
                            nowMillis = nowMillis,
                            onClick = { onSelectCaprice(caprice) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LimeGreen else CardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CategoryCapriceRow(caprice: Caprice, nowMillis: Long, onClick: () -> Unit) {
    val category = ProfileCategory.of(caprice.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = category.emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = caprice.question,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${caprice.answer1} / ${caprice.answer2}",
                color = SubtleGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = formatPollStatus(caprice.timestamp, nowMillis),
            color = SubtleGray,
            fontSize = 11.sp
        )
    }
}
