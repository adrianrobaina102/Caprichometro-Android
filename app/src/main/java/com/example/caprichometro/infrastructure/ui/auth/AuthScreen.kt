package com.example.caprichometro.infrastructure.ui.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caprichometro.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val DarkBackground = Color(0xFF121212)
val LimeGreen = Color(0xFFD4FF00)
val DarkSurface = Color(0xFF1E1E1E)
val BorderGray = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.isAuthSuccessful) {
        if (state.isAuthSuccessful) {
            password = ""
            onNavigateToHome()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        birthday = localDate
                    }
                }) { Text("Aceptar", color = LimeGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar", color = Color.Gray) }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = LimeGreen,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color.Gray,
                    dayContentColor = Color.White,
                    selectedDayContainerColor = LimeGreen,
                    selectedDayContentColor = Color.Black
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            painter = painterResource(id = R.drawable.ic_caprichometro_logo),
            contentDescription = "Logo Caprichómetro",
            tint = Color.Unspecified,
            modifier = Modifier.size(90.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Caprichómetro",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tus caprichos, tus dudas y tus planes\nen 5 minutos",
            color = Color.Gray,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(12.dp))
        ) {
            AuthTab(
                text = "Iniciar sesión",
                isSelected = isLoginMode,
                modifier = Modifier.weight(1f)
            ) { isLoginMode = true }

            AuthTab(
                text = "Registrarse",
                isSelected = !isLoginMode,
                modifier = Modifier.weight(1f)
            ) { isLoginMode = false }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isLoginMode) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text("Nombre de usuario (@usuario)", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Usuario", tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkBackground,
                    unfocusedContainerColor = DarkBackground,
                    focusedIndicatorColor = LimeGreen,
                    unfocusedIndicatorColor = DarkSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                OutlinedTextField(
                    value = birthday?.toString() ?: "",
                    onValueChange = {},
                    placeholder = { Text("Fecha de nacimiento (AAAA-MM-DD)", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Fecha", tint = Color.Gray) },
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        disabledContainerColor = DarkBackground,
                        disabledIndicatorColor = DarkSurface,
                        disabledTextColor = Color.White,
                        disabledPlaceholderColor = Color.Gray,
                        disabledLeadingIconColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("Correo electrónico", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Correo", tint = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkBackground,
                unfocusedContainerColor = DarkBackground,
                focusedIndicatorColor = LimeGreen,
                unfocusedIndicatorColor = DarkSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Contraseña", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Contraseña", tint = Color.Gray) },
            trailingIcon = {
                val icon = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(icon, contentDescription = "Ver contraseña", tint = Color.Gray)
                }
            },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkBackground,
                unfocusedContainerColor = DarkBackground,
                focusedIndicatorColor = LimeGreen,
                unfocusedIndicatorColor = DarkSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.updateEmail(email)
                viewModel.updatePassword(password)

                if (isLoginMode) {
                    viewModel.signIn()
                } else {
                    viewModel.updateName(username)
                    birthday?.let { viewModel.updateBirthday(it) }
                    viewModel.register()
                }
            },
            enabled = !state.isSubmitting,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LimeGreen,
                contentColor = Color.Black,
                disabledContainerColor = Color.DarkGray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isLoginMode) "Entrar" else "Crear cuenta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isLoginMode) "¿No tienes cuenta? " else "¿Ya tienes cuenta? ",
                color = Color.Gray
            )
            Text(
                text = if (isLoginMode) "Regístrate" else "Inicia sesión",
                color = LimeGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { isLoginMode = !isLoginMode }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray)
            Text(" o ", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { viewModel.signInWithGoogle(context) },
            enabled = !state.isSubmitting,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = "Logo Google",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Continuar con Google", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Al continuar aceptas los términos",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
fun AuthTab(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    color = if (isSelected) LimeGreen else Color.Transparent,
                    shape = RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp)
                )
        )
    }
}
