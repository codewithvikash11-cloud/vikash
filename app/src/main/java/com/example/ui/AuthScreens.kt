package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuthHelper
import com.example.ui.theme.BackgroundBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Beautiful color definitions to keep auth styling highly cohesive
val AuthBackground = Color(0xFF070707)
val AuthCard = Color(0xFF121212)
val NeonRed = Color(0xFFFF1E1E)
val DarkRedAccent = Color(0xFFC1121F)
val NeonGold = Color(0xFFFFB800)
val NeonSuccess = Color(0xFF00E676)

enum class AuthScreenType {
    LOGIN,
    REGISTER,
    OTP_VERIFY,
    PROFILE_SETUP,
    FORGOT_PASSWORD
}

@Composable
fun AuthFlowContainer(
    viewModel: WalletViewModel,
    onAuthSuccess: (username: String) -> Unit
) {
    var currentScreen by remember { mutableStateOf(AuthScreenType.LOGIN) }
    
    // Auth context shared between screens
    var tempPhone by remember { mutableStateOf("") }
    var tempUsername by remember { mutableStateOf("") }
    var tempFullName by remember { mutableStateOf("") }
    var tempEmail by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf("") }
    var tempReferralCode by remember { mutableStateOf("") }
    
    // Holds the next target screen after completing OTP
    var otpTargetScreen by remember { mutableStateOf(AuthScreenType.PROFILE_SETUP) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBackground)
    ) {
        // Subtle ambient moving particle background
        AuthParticleBackground()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Crossfade(
                targetState = currentScreen,
                animationSpec = tween(500),
                label = "auth_screen_transition"
            ) { screen ->
                when (screen) {
                    AuthScreenType.LOGIN -> LoginScreen(
                        viewModel = viewModel,
                        onNavigateToRegister = { currentScreen = AuthScreenType.REGISTER },
                        onNavigateToForgot = { currentScreen = AuthScreenType.FORGOT_PASSWORD },
                        onLoginSuccess = { username ->
                            tempUsername = username
                            // On login success, let's verify OTP as second factor
                            otpTargetScreen = AuthScreenType.PROFILE_SETUP
                            currentScreen = AuthScreenType.OTP_VERIFY
                        }
                    )
                    AuthScreenType.REGISTER -> RegisterScreen(
                        viewModel = viewModel,
                        onNavigateToLogin = { currentScreen = AuthScreenType.LOGIN },
                        onRegisterSuccess = { fullName, username, phone, email, password, referral ->
                            tempFullName = fullName
                            tempUsername = username
                            tempPhone = phone
                            tempEmail = email
                            tempPassword = password
                            tempReferralCode = referral ?: ""
                            
                            // Trigger registration write-back, then ask to verify dynamic OTP
                            otpTargetScreen = AuthScreenType.PROFILE_SETUP
                            currentScreen = AuthScreenType.OTP_VERIFY
                        }
                    )
                    AuthScreenType.OTP_VERIFY -> OtpVerifyScreen(
                        viewModel = viewModel,
                        phone = tempPhone.ifEmpty { "9876543210" },
                        onOtpVerified = {
                            if (tempPassword.isNotEmpty()) {
                                // This is a real registration flow, let's finalize the credentials DB records
                                viewModel.register(
                                    fullName = tempFullName,
                                    username = tempUsername,
                                    phone = tempPhone,
                                    email = tempEmail,
                                    passwordRaw = tempPassword,
                                    referralCode = tempReferralCode.ifEmpty { null }
                                ) {
                                    // Successfully registered, let's login to set active session
                                    viewModel.login(tempUsername, tempPassword) {
                                        currentScreen = AuthScreenType.PROFILE_SETUP
                                    }
                                }
                            } else {
                                // Typical Login flow OTP validation completion
                                currentScreen = AuthScreenType.PROFILE_SETUP
                            }
                        }
                    )
                    AuthScreenType.PROFILE_SETUP -> ProfileSetupScreen(
                        viewModel = viewModel,
                        initialUsername = tempUsername,
                        onProfileSaved = {
                            onAuthSuccess(tempUsername)
                        }
                    )
                    AuthScreenType.FORGOT_PASSWORD -> ForgotPasswordScreen(
                        viewModel = viewModel,
                        onNavigateToLogin = { currentScreen = AuthScreenType.LOGIN },
                        onPasswordResetRequested = { phone ->
                            tempPhone = phone
                            otpTargetScreen = AuthScreenType.LOGIN
                            currentScreen = AuthScreenType.OTP_VERIFY
                        }
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN A: AUTH AMBIENT DUST PARTICLES GRAPHICS
// ----------------------------------------------------
@Composable
fun AuthParticleBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_dust")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val translationX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_x"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(5.dp)
    ) {
        // Red neon vapor glow top right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonRed.copy(0.18f), Color.Transparent),
                radius = size.width * 0.9f
            ),
            center = Offset(size.width, size.height * 0.1f)
        )

        // Golden premium imperial vapor glow bottom left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonGold.copy(0.12f), Color.Transparent),
                radius = size.width * 1.0f
            ),
            center = Offset(0f, size.height * 0.9f)
        )

        // Draw multiple glowing gaming particle sparks
        val randomSparks = listOf(
            Offset(0.2f, 0.15f), Offset(0.81f, 0.28f), Offset(0.55f, 0.45f),
            Offset(0.12f, 0.65f), Offset(0.74f, 0.72f), Offset(0.38f, 0.88f)
        )

        randomSparks.forEachIndexed { idx, normalizedOffset ->
            val scaleFactor = if (idx % 2 == 0) alphaAnim else (0.8f - alphaAnim)
            drawCircle(
                color = if (idx % 3 == 0) NeonGold.copy(alpha = scaleFactor * 0.7f) else NeonRed.copy(alpha = scaleFactor * 0.7f),
                radius = (4 + (idx % 4) * 2).toDp().toPx(),
                center = Offset(
                    x = size.width * normalizedOffset.x + translationX,
                    y = size.height * normalizedOffset.y + (translationX * 0.5f)
                )
            )
        }
    }
}

// ----------------------------------------------------
// 1. LOGIN SCREEN WITH INTERACTIVE PHONE ENTRY
// ----------------------------------------------------
@Composable
fun LoginScreen(
    viewModel: WalletViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var acceptTerms by remember { mutableStateOf(true) }
    var selectedCountryCode by remember { mutableStateOf("+91") }
    var showCountryMenu by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 40.dp)
    ) {
        // Logo & Tagline Title Card
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                // Crown and Dice dynamic emblem
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(NeonRed.copy(0.3f), Color.Transparent)
                                )
                            )
                    )
                    Text("👑", fontSize = 48.sp, modifier = Modifier.offset(y = (-20).dp))
                    Text("🎲", fontSize = 32.sp, modifier = Modifier.offset(x = 10.dp, y = 18.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "RANGILO LUDO",
                    color = NeonGold,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = "Play • Win • Rule",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }
        }

        // Login inputs card with Glassmorphism overlay styling
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131313).copy(0.7f), RoundedCornerShape(24.dp))
                    .border(
                        BorderStroke(1.dp, Brush.verticalGradient(
                            colors = listOf(Color.White.copy(0.12f), Color.White.copy(0.02f))
                        )),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "ROYAL ACCESS",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Phone Number with flag/country picker
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Phone Number",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Country Code selector
                            Box(
                                modifier = Modifier
                                    .height(54.dp)
                                    .width(85.dp)
                                    .background(Color.Black.copy(0.4f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                                    .clickable { showCountryMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val flagEmoji = when(selectedCountryCode) {
                                        "+91" -> "🇮🇳"
                                        "+1" -> "🇺🇸"
                                        "+44" -> "🇬🇧"
                                        else -> "🌐"
                                    }
                                    Text(text = "$flagEmoji $selectedCountryCode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                DropdownMenu(
                                    expanded = showCountryMenu,
                                    onDismissRequest = { showCountryMenu = false },
                                    modifier = Modifier.background(AuthCard)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("🇮🇳 +91 (IND)", color = Color.White) },
                                        onClick = { selectedCountryCode = "+91"; showCountryMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🇺🇸 +1 (USA)", color = Color.White) },
                                        onClick = { selectedCountryCode = "+1"; showCountryMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🇬🇧 +44 (UK)", color = Color.White) },
                                        onClick = { selectedCountryCode = "+44"; showCountryMenu = false }
                                    )
                                }
                            }

                            // Main Phone input
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { if (it.length <= 10) phoneInput = it.filter { ch -> ch.isDigit() } },
                                placeholder = { Text("Enter 10-digit number", color = Color.DarkGray, fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("login_phone_input"),
                                shape = RoundedCornerShape(12.dp),
                                isError = phoneInput.isNotEmpty() && !AuthHelper.validatePhone(phoneInput),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonRed,
                                    unfocusedBorderColor = Color.White.copy(0.08f),
                                    focusedContainerColor = Color.Black.copy(0.4f),
                                    unfocusedContainerColor = Color.Black.copy(0.4f),
                                    errorBorderColor = NeonRed,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    // Password Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Password",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            placeholder = { Text("Enter your secure passcode", color = Color.DarkGray, fontSize = 13.sp) },
                            modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("login_password_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color.Gray
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonRed,
                                unfocusedBorderColor = Color.White.copy(0.08f),
                                focusedContainerColor = Color.Black.copy(0.4f),
                                unfocusedContainerColor = Color.Black.copy(0.4f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // Remember Me & Forgot Password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rememberMe = !rememberMe }) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = NeonRed, uncheckedColor = Color.Gray)
                            )
                            Text("Keep me in", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "FORGOT PASSPORT?",
                            color = NeonGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigateToForgot() }
                                .padding(vertical = 4.dp)
                        )
                    }

                    // Accept Terms
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acceptTerms = !acceptTerms }
                    ) {
                        Checkbox(
                            checked = acceptTerms,
                            onCheckedChange = { acceptTerms = it },
                            colors = CheckboxDefaults.colors(checkedColor = NeonRed, uncheckedColor = Color.Gray)
                        )
                        Text(
                            text = "I accept Fair Play terms & regional restrictions",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Login Battle Button
                    Button(
                        onClick = {
                            if (!acceptTerms) {
                                scope.launch {
                                    viewModel.login("", "") {} // Trigger trigger error
                                }
                                return@Button
                            }
                            if (phoneInput.isBlank() || passwordInput.isBlank()) {
                                return@Button
                            }
                            isLoading = true
                            viewModel.login(phoneInput, passwordInput) {
                                isLoading = false
                                onLoginSuccess(phoneInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_action_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                "ENTER LOBBY",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }

        // Beautiful divider: "OR BRAL CLASH SOCIAL"
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(0.06f))
                Text("OR CHOOSE ENTRANCE", color = Color.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(0.06f))
            }
        }

        // Social Credentials (Google Instant login)
        item {
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        delay(1200)
                        // Seed mock registered account if missing
                        viewModel.register("Rana Pratap Ludo", "rana_ludo", "9999999999", "rana@ludo.app", "123456", null) {}
                        viewModel.login("rana_ludo", "123456") {
                            isLoading = false
                            onLoginSuccess("rana_ludo")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🔴", fontSize = 16.sp) // Google colors accent
                    Text(
                        "SIGN IN WITH GOOGLE PLAY",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Redirect to register
        item {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("New Challenger?", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CREATE ACCOUNT",
                    color = NeonGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
        }
    }
}

// ----------------------------------------------------
// 2. REGISTER SCREEN (FULL DETAILS WITH BONUS FIELD)
// ----------------------------------------------------
@Composable
fun RegisterScreen(
    viewModel: WalletViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: (fullName: String, userName: String, phone: String, email: String, password: String, referral: String?) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }
    var acceptTerms by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
    ) {
        // Headers
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆", fontSize = 42.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "CREATING CHALLENGER",
                    color = NeonGold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Sign up & claim free ₹200 starter bonus",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        // Form Cards
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131313).copy(0.7f), RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(0.06f)), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    
                    // Full Name
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_fullname_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonRed,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Gaming Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                        label = { Text("Challenger ID (Username)", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_username_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonRed,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Phone Number
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter { ch -> ch.isDigit() } },
                        label = { Text("WhatsApp Phone Number", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonRed,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Email (Optional)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address (Optional)", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonRed,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Passcode Fields
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Access Password (min 6 chars)", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonRed,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Retype Password", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_confirm_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonRed,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Referral Code
                    OutlinedTextField(
                        value = referralCode,
                        onValueChange = { referralCode = it },
                        label = { Text("Referral Bonus Code (Optional)", color = NeonGold, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_referral_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGold,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Accept lines
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { acceptTerms = !acceptTerms }
                    ) {
                        Checkbox(
                            checked = acceptTerms,
                            onCheckedChange = { acceptTerms = it },
                            colors = CheckboxDefaults.colors(checkedColor = NeonRed)
                        )
                        Text(
                            text = "I guarantee that I am 18+ and not a resident of state forbidden zones.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action Button
                    Button(
                        onClick = {
                            if (!acceptTerms) return@Button
                            if (fullName.isBlank() || username.isBlank() || phone.isBlank() || password.isBlank()) {
                                return@Button
                            }
                            if (password != confirmPassword) return@Button
                            
                            isLoading = true
                            onRegisterSuccess(fullName, username, phone, email, password, referralCode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("register_action_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = BackgroundBlack, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                "COMMENCE JOURNEY (FREE ₹200)",
                                color = BackgroundBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Redirection to Login
        item {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Already registered?", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ENTER HALL OF FAME",
                    color = NeonRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}

// ----------------------------------------------------
// 3. OTP VERIFICATION SCREEN (DYNAMIC CODES GRID)
// ----------------------------------------------------
@Composable
fun OtpVerifyScreen(
    viewModel: WalletViewModel,
    phone: String,
    onOtpVerified: () -> Unit
) {
    var otpDigits = remember { mutableStateListOf("", "", "", "", "", "") }
    var timerSeconds by remember { mutableStateOf(45) }
    var isVerifying by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Countdown effect
    LaunchedEffect(Unit) {
        while (timerSeconds > 0) {
            delay(1000)
            timerSeconds--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic Dice Ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Text("⚔️", fontSize = 48.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "LOBBY VERIFICATION",
            color = NeonGold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "We sent a 6-digit passcode to $phone",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(30.dp))

        // OTP Row with individual Glass blocks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 6) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(Color(0xFF131313), RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (otpDigits[i].isNotEmpty()) NeonRed else Color.White.copy(0.06f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = otpDigits[i],
                        onValueChange = { input: String ->
                            val clean = input.filter { ch: Char -> ch.isDigit() }
                            if (clean.length <= 1) {
                                otpDigits[i] = clean
                            }
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("otp_digit_$i")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer or Resend Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timerSeconds > 0) {
                Text(
                    text = "Resend passcode in ${timerSeconds}s",
                    color = Color.DarkGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "RESEND OTP CODE",
                    color = NeonGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable {
                        timerSeconds = 45
                        otpDigits.fill("")
                    }
                )
            }

            // AUTO DETECT OPT (realistic flow mock)
            Button(
                onClick = {
                    isVerifying = true
                    scope.launch {
                        // Simulate physical SMS reading
                        delay(250)
                        otpDigits[0] = "7"
                        delay(120)
                        otpDigits[1] = "7"
                        delay(120)
                        otpDigits[2] = "4"
                        delay(120)
                        otpDigits[3] = "9"
                        delay(120)
                        otpDigits[4] = "1"
                        delay(120)
                        otpDigits[5] = "0"
                        
                        delay(500)
                        isVerifying = false
                        showSuccessAnimation = true
                        delay(1000)
                        onOtpVerified()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.04f)),
                border = BorderStroke(1.dp, Color.White.copy(0.08f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("AUTO READ SMS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Verify Battle button
        Button(
            onClick = {
                val enteredOtp = otpDigits.joinToString("")
                if (enteredOtp.length == 6) {
                    isVerifying = true
                    scope.launch {
                        delay(800)
                        isVerifying = false
                        showSuccessAnimation = true
                        delay(800)
                        onOtpVerified()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("otp_verify_action_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else if (showSuccessAnimation) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🟢", fontSize = 16.sp)
                    Text("VERIFICATION SECURED", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Text(
                    "VERIFY SECURITY KEY",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ----------------------------------------------------
// 4. PROFILE SETUP SCREEN WITH HERO BADGE CAROUSEL
// ----------------------------------------------------
@Composable
fun ProfileSetupScreen(
    viewModel: WalletViewModel,
    initialUsername: String,
    onProfileSaved: () -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var selectedGender by remember { mutableStateOf("Warrior") }
    var selectedState by remember { mutableStateOf("Rajasthan") }
    var showStateDropdown by remember { mutableStateOf(false) }
    
    // Royal gaming avatars
    val avatars = listOf("👑", "⚔️", "🛡️", "🦁", "🐯", "🦅", "🐉", "🔱", "🎯")
    var selectedAvatar by remember { mutableStateOf("👑") }
    
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val states = listOf(
        "Rajasthan", "Gujarat", "Delhi", "Maharashtra", "Punjab", "Haryana", "Karnataka", "Uttar Pradesh"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 40.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🦁", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ROYAL PROFILE CREATION",
                    color = NeonGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Pick your standard brand icon and status alignment",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        // Avatar selector
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CHOOSE SELECT EMBLEM",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Large active badge icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(NeonRed, NeonGold, NeonRed)
                            )
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(AuthCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = selectedAvatar, fontSize = 42.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Avatar Horizontal Scroll lists
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    avatars.forEach { avatar ->
                        val isPicked = avatar == selectedAvatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPicked) NeonRed.copy(0.15f) else Color.White.copy(0.04f))
                                .border(
                                    1.dp,
                                    if (isPicked) NeonRed else Color.White.copy(0.06f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedAvatar = avatar },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(avatar, fontSize = 24.sp)
                        }
                    }
                }
            }
        }

        // Setup Form Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131313).copy(0.7f), RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(0.06f)), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    // Username Field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                        label = { Text("Challenger Username", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("setup_username_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonRed,
                            unfocusedBorderColor = Color.White.copy(0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Gender alignment tags selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Warrior Status (Gender)",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val genders = listOf("Warrior", "Huntress", "Mystic")
                            genders.forEach { gender ->
                                val isSelected = gender == selectedGender
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeonRed.copy(0.12f) else Color.Black.copy(0.4f))
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonRed else Color.White.copy(0.06f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedGender = gender },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = gender,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // State Location Dropdown picker
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "State Guild (Location)",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .background(Color.Black.copy(0.4f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                                .clickable { showStateDropdown = true }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedState, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick State", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = showStateDropdown,
                                onDismissRequest = { showStateDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.8f).background(AuthCard)
                            ) {
                                states.forEach { state ->
                                    DropdownMenuItem(
                                        text = { Text(state, color = Color.White) },
                                        onClick = {
                                            selectedState = state
                                            showStateDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Save Profile Button
                    Button(
                        onClick = {
                            if (username.isBlank()) return@Button
                            isLoading = true
                            viewModel.setupProfile(
                                username = username,
                                gender = selectedGender,
                                state = selectedState,
                                avatar = selectedAvatar
                            ) {
                                isLoading = false
                                onProfileSaved()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("setup_save_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                "ENTER HALL OF VICTORY",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 5. FORGOT PASSWORD (PHONE VERIFY & RESET OTP RESET)
// ----------------------------------------------------
@Composable
fun ForgotPasswordScreen(
    viewModel: WalletViewModel,
    onNavigateToLogin: () -> Unit,
    onPasswordResetRequested: (phone: String) -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚙️", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "PASSPORT RESTORATION",
            color = NeonGold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "Reset your passcode instantly",
            color = Color.Gray,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF131313).copy(0.7f), RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, Color.White.copy(0.06f)), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Phone Number field
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("WhatsApp Phone Number", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("forgot_phone_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonRed,
                        unfocusedBorderColor = Color.White.copy(0.08f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // New Passcode fields
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Create New Password (min 6 chars)", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("forgot_new_password_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonRed,
                        unfocusedBorderColor = Color.White.copy(0.08f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Retype New Password", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("forgot_confirm_password_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonRed,
                        unfocusedBorderColor = Color.White.copy(0.08f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (phoneInput.isBlank() || newPassword.isBlank()) return@Button
                        if (newPassword != confirmPassword) return@Button
                        
                        isLoading = true
                        viewModel.forgotPassword(phoneInput, newPassword) {
                            isLoading = false
                            onPasswordResetRequested(phoneInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("forgot_action_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            "REQUEST SECURE RESET",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CANCEL & RETURN TO LOGIN",
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigateToLogin() }
        )
    }
}

// ----------------------------------------------------
// PRIVATE MOCK TRANSFORMATION TO PREVENT RAW PASSWORD VISIBILITY
// ----------------------------------------------------
private class PasswordTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val transformed = "*".repeat(text.text.length)
        return TransformedText(
            androidx.compose.ui.text.AnnotatedString(transformed),
            OffsetMapping.Identity
        )
    }
}
