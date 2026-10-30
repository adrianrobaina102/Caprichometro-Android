package com.example.caprichometro.infrastructure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.domain.model.Caprice
import kotlinx.coroutines.delay

private val DarkBackground = Color(0xFF0F0F0F)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)

@Composable
fun ActivePollScreen(
    caprice: Caprice?,
    onNavigateBack: () -> Unit = {},
    onTimerFinished: () -> Unit = {}
) {
    val questionText = caprice?.question ?: "Cargando duda desde Firebase..."

    // Estado para guardar el texto del cronómetro
    var timeDisplay by remember { mutableStateOf("--:--") }

    // Este bloque crea la cuenta atrás real
    LaunchedEffect(caprice) {
        if (caprice != null) {
            val totalTimeMillis = 5 * 60 * 1000L

            while (true) {
                // Calculamos cuánto tiempo queda restando la hora actual menos la hora de creación
                val elapsedTime = System.currentTimeMillis() - caprice.timestamp
                val remainingTime = totalTimeMillis - elapsedTime

                if (remainingTime <= 0) {
                    timeDisplay = "00:00"
                    onTimerFinished()
                    break
                }

                // Convertimos los milisegundos restantes a minutos y segundos
                val minutes = (remainingTime / 1000) / 60
                val seconds = (remainingTime / 1000) % 60

                // Le damos formato 05:00
                timeDisplay = String.format("%02d:%02d", minutes, seconds)

                delay(1000L) // Esperamos 1 segundo antes de volver a calcular
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
                Text(
                    text = "Tu duda está en el aire",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(LimeGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("D", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    text = "SOLO TUS AMIGOS",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AQUI PINTAMOS EL TIEMPO REAL DE FIREBASE
            Text(
                text = timeDisplay,
                color = LimeGreen,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 80.sp
            )
            Text(
                text = "para que tus amigos opinen",
                color = TextGray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFFB73239), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Imagen de Zapatillas", color = Color.White, modifier = Modifier.padding(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AQUI PINTAMOS LA PREGUNTA REAL DE FIREBASE
            Text(
                text = questionText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy((-12).dp)
            ) {
                VoterAvatar(initial = "L", backgroundColor = Color(0xFFF4B8CD))
                VoterAvatar(initial = "M", backgroundColor = Color(0xFFA4BFF5))
                VoterAvatar(initial = "S", backgroundColor = Color(0xFF8EDBB8))
                VoterAvatar(initial = "A", backgroundColor = LimeGreen)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "3 amigos han votado",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Los votos no se ven hasta que cierre.",
                color = TextGray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "En la app real no se puede adelantar el cierre.",
                color = Color(0xFF555555),
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
fun VoterAvatar(initial: String, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(2.dp, DarkBackground, CircleShape)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}