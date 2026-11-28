package com.example.caprichometro.infrastructure.ui.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.caprichometro.R
import com.example.caprichometro.infrastructure.ui.components.AppIcons
import com.example.caprichometro.infrastructure.ui.components.localizedUiMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val DarkBackground = Color(0xFF121212)
val LimeGreen = Color(0xFFD4FF00)
val DarkSurface = Color(0xFF1E1E1E)
val BorderGray = Color(0xFF2A2A2A)

private val AuthDarkColorScheme = darkColorScheme(
    primary = LimeGreen,
    onPrimary = Color.Black,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceContainerHigh = DarkSurface,
    onSurfaceVariant = Color(0xFFB5B5BA),
    outline = BorderGray
)

private val DisplayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private fun formatHeadlineDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    val pattern = if (locale.language == "es") "d 'de' MMM 'de' yyyy" else "MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    var isLoginMode by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var username by rememberSaveable { mutableStateOf("") }
    var birthdayEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val birthday = birthdayEpochDay?.let { LocalDate.ofEpochDay(it) }
    var showDatePicker by remember { mutableStateOf(false) }

    val submitAuth: () -> Unit = {
        viewModel.updateEmail(email)
        viewModel.updatePassword(password)
        if (isLoginMode) {
            viewModel.signIn()
        } else {
            viewModel.updateName(username)
            birthday?.let { viewModel.updateBirthday(it) }
            viewModel.register()
        }
    }

    LaunchedEffect(state.isAuthSuccessful) {
        if (state.isAuthSuccessful) {
            password = ""
            onNavigateToHome()
        }
    }

    val translatedError = state.errorMessage?.let { localizedUiMessage(it) }
    LaunchedEffect(translatedError) {
        translatedError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    if (showDatePicker) {
        BirthdayPickerDialog(
            initialBirthday = birthday,
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedDate ->
                birthdayEpochDay = pickedDate.toEpochDay()
                viewModel.clearError()
                showDatePicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            painter = painterResource(id = R.drawable.ic_caprichometro_logo),
            contentDescription = stringResource(R.string.auth_logo_cd),
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
            text = stringResource(R.string.auth_subtitle),
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
                text = stringResource(R.string.auth_tab_login),
                isSelected = isLoginMode,
                modifier = Modifier.weight(1f)
            ) {
                isLoginMode = true
                viewModel.clearError()
            }

            AuthTab(
                text = stringResource(R.string.auth_tab_register),
                isSelected = !isLoginMode,
                modifier = Modifier.weight(1f)
            ) {
                isLoginMode = false
                viewModel.clearError()
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isLoginMode) {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    viewModel.updateName(it)
                },
                placeholder = { Text(stringResource(R.string.auth_username_placeholder), color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.auth_username_icon_cd), tint = Color.Gray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showDatePicker = true }
            ) {
                OutlinedTextField(
                    value = birthday?.format(DisplayDateFormatter) ?: "",
                    onValueChange = {},
                    placeholder = { Text(stringResource(R.string.auth_birthday_placeholder), color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.auth_birthday_icon_cd),
                            tint = if (birthday != null) LimeGreen else Color.Gray
                        )
                    },
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        disabledContainerColor = DarkBackground,
                        disabledIndicatorColor = if (birthday != null) BorderGray else DarkSurface,
                        disabledTextColor = Color.White,
                        disabledPlaceholderColor = Color.Gray,
                        disabledLeadingIconColor = if (birthday != null) LimeGreen else Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                viewModel.updateEmail(it)
            },
            placeholder = { Text(stringResource(R.string.auth_email_placeholder), color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.auth_email_icon_cd), tint = Color.Gray) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
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
            onValueChange = {
                password = it
                viewModel.updatePassword(it)
            },
            placeholder = { Text(stringResource(R.string.auth_password_placeholder), color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.auth_password_icon_cd), tint = Color.Gray) },
            trailingIcon = {
                val icon = if (isPasswordVisible) AppIcons.Visibility else AppIcons.VisibilityOff
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        icon,
                        contentDescription = stringResource(
                            if (isPasswordVisible) R.string.auth_hide_password_cd else R.string.auth_show_password_cd
                        ),
                        tint = Color.Gray
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (!state.isSubmitting) submitAuth()
                }
            ),
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

        translatedError?.let { errorMsg ->
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = Color(0xFF2B1618),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMsg,
                    color = Color(0xFFFF8A8A),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        } ?: Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = submitAuth,
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
                    text = stringResource(if (isLoginMode) R.string.auth_button_login else R.string.auth_button_register),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (isLoginMode) R.string.auth_no_account_prefix else R.string.auth_has_account_prefix),
                color = Color.Gray
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(if (isLoginMode) R.string.auth_tab_register else R.string.auth_tab_login),
                color = LimeGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    isLoginMode = !isLoginMode
                    viewModel.clearError()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray)
            Text(stringResource(R.string.auth_or_divider), color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
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
                contentDescription = stringResource(R.string.auth_google_logo_cd),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.auth_google_button), fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.auth_terms_notice),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayPickerDialog(
    initialBirthday: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val todayUtcMillis = remember(today) {
        today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val initialMonthMillis = remember(initialBirthday, today) {
        (initialBirthday ?: today.minusYears(20))
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialBirthday
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
        initialDisplayedMonthMillis = initialMonthMillis,
        yearRange = 1920..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= todayUtcMillis

            override fun isSelectableYear(year: Int): Boolean =
                year <= today.year
        }
    )

    val selectedLocalDate = datePickerState.selectedDateMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    }

    MaterialTheme(colorScheme = AuthDarkColorScheme) {
        val datePickerColors = DatePickerDefaults.colors(
            containerColor = DarkSurface,
            titleContentColor = LimeGreen,
            headlineContentColor = Color.White,
            weekdayContentColor = Color(0xFF9E9EA6),
            subheadContentColor = Color(0xFFD0D0D6),
            navigationContentColor = Color.White,
            yearContentColor = Color.White,
            disabledYearContentColor = Color(0xFF55555C),
            currentYearContentColor = LimeGreen,
            selectedYearContentColor = Color.Black,
            disabledSelectedYearContentColor = Color.Black,
            selectedYearContainerColor = LimeGreen,
            disabledSelectedYearContainerColor = Color(0xFF55555C),
            dayContentColor = Color.White,
            disabledDayContentColor = Color(0xFF55555C),
            selectedDayContentColor = Color.Black,
            disabledSelectedDayContentColor = Color.Black,
            selectedDayContainerColor = LimeGreen,
            disabledSelectedDayContainerColor = Color(0xFF55555C),
            todayContentColor = LimeGreen,
            todayDateBorderColor = LimeGreen,
            dayInSelectionRangeContentColor = Color.Black,
            dayInSelectionRangeContainerColor = LimeGreen.copy(alpha = 0.35f),
            dividerColor = BorderGray,
            dateTextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = LimeGreen,
                unfocusedBorderColor = Color(0xFF4A4A50),
                focusedLabelColor = LimeGreen,
                unfocusedLabelColor = Color(0xFF9E9EA6),
                cursorColor = LimeGreen,
                errorTextColor = Color(0xFFFF6B6B),
                errorBorderColor = Color(0xFFFF6B6B),
                errorLabelColor = Color(0xFFFF6B6B)
            )
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.padding(horizontal = 16.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            shape = RoundedCornerShape(20.dp),
            confirmButton = {
                TextButton(
                    onClick = { selectedLocalDate?.let(onConfirm) },
                    enabled = selectedLocalDate != null
                ) {
                    Text(
                        text = stringResource(R.string.action_accept),
                        color = if (selectedLocalDate != null) LimeGreen else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = Color(0xFFB5B5BA))
                }
            },
            colors = datePickerColors
        ) {
            DatePicker(
                state = datePickerState,
                colors = datePickerColors,
                title = {
                    Text(
                        text = stringResource(R.string.auth_birthday_title),
                        color = LimeGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                },
                headline = {
                    val headlineText = selectedLocalDate?.let(::formatHeadlineDate)
                        ?: if (datePickerState.displayMode == DisplayMode.Input) {
                            stringResource(R.string.auth_birthday_input_hint)
                        } else {
                            stringResource(R.string.auth_birthday_headline_empty)
                        }
                    Text(
                        text = headlineText,
                        color = if (selectedLocalDate != null) Color.White else Color(0xFFB5B5BA),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                    )
                }
            )
        }
    }
}
