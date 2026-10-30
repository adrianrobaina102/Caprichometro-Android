package com.example.caprichometro.infrastructure.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.domain.validation.DifferentOptionsValidator
import com.example.caprichometro.domain.validation.MinLengthValidator
import com.example.caprichometro.domain.validation.NotBlankValidator
import com.example.caprichometro.domain.validation.ValidationResult

// Colores del tema
private val DarkBackground = Color(0xFF0F0F0F)
private val DarkSurface = Color(0xFF1C1C1C)
private val LimeGreen = Color(0xFFD4FF00)
private val TextGray = Color(0xFFAAAAAA)

@Composable
fun CapriceScreen(
    viewModel: CapriceViewModel,
    onNavigateToActivePoll: () -> Unit
) {
    val context = LocalContext.current

    // Estados de la UI para capturar lo que escribe el usuario
    var isCompraType by remember { mutableStateOf(true) }
    var questionText by remember { mutableStateOf("") }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }

    // Cadena de Responsabilidad (Chain of Responsibility) para validar la pregunta
    val questionValidatorChain = remember {
        val notBlank = NotBlankValidator("La pregunta")
        notBlank.setNext(MinLengthValidator("La pregunta", minLength = 6))
        notBlank
    }

    // Validador de opciones distintas
    val optionsValidator = remember { DifferentOptionsValidator() }

    val isFormFilled = questionText.isNotBlank() && answer1.isNotBlank() && answer2.isNotBlank()

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
                    tint = Color.White,
                    modifier = Modifier.clickable { /* Navegar atrás */ }
                )
                Text(
                    text = "Antes de lanzarlo",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.width(24.dp))
            }
        },
        bottomBar = {
            Button(
                onClick = {
                    // Validar usando la Cadena de Responsabilidad
                    val questionResult = questionValidatorChain.validate(questionText)
                    if (questionResult is ValidationResult.Error) {
                        Toast.makeText(context, questionResult.message, Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val optionsResult = optionsValidator.validate(Pair(answer1, answer2))
                    if (optionsResult is ValidationResult.Error) {
                        Toast.makeText(context, optionsResult.message, Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val typeString = if (isCompraType) "Compra" else "Plan"

                    viewModel.saveCaprice(
                        type = typeString,
                        questionText = questionText.trim(),
                        answer1 = answer1.trim(),
                        answer2 = answer2.trim()
                    )

                    viewModel.fetchUserCaprices(limit = 1)
                    onNavigateToActivePoll()
                },
                enabled = isFormFilled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeGreen,
                    disabledContainerColor = Color(0xFF4A5500)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isFormFilled) Color.Black else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Publicar · 5 min",
                    color = if (isFormFilled) Color.Black else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Selector de Tipo: Compra o Plan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { isCompraType = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompraType) LimeGreen else DarkSurface,
                        contentColor = if (isCompraType) Color.Black else Color.White
                    )
                ) {
                    Text(text = "🛍️ Compra", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { isCompraType = false },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isCompraType) LimeGreen else DarkSurface,
                        contentColor = if (!isCompraType) Color.Black else Color.White
                    )
                ) {
                    Text(text = "🎉 Plan", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pregunta
            Text(
                text = "Tu duda o capricho",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                placeholder = { Text(text = "Ej. ¿Me compro estas zapatillas?", color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Opción 1
            Text(
                text = "Opción A (Respuesta 1)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = answer1,
                onValueChange = { answer1 = it },
                placeholder = { Text(text = "Ej. Sí, te quedan genial", color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Opción 2
            Text(
                text = "Opción B (Respuesta 2)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = answer2,
                onValueChange = { answer2 = it },
                placeholder = { Text(text = "Ej. No, ahorra un poco", color = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeGreen,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tarjetas informativas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = LimeGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Duración límite: 5 minutos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "Tras 5 min, la votación se cierra y se da el veredicto.", color = TextGray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = LimeGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Visible solo para tus amigos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "Tus amigos recibirán la votación en tiempo real.", color = TextGray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
