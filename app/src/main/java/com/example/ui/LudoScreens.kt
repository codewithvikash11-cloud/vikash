package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Navigation tab enum
enum class LudoScreen(val title: String) {
    HOME("Home"),
    TABLES("Tables"),
    TOURNAMENTS("Clash"),
    WALLET("Wallet"),
    PROFILE("Profile"),
    ADMIN("Admin")
}

@Composable
fun MainLudoApp(viewModel: WalletViewModel) {
    val walletState by viewModel.userWallet.collectAsState()
    val tables by viewModel.liveTables.collectAsState()
    val tournaments by viewModel.tournaments.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val disputes by viewModel.disputes.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showSplash by remember { mutableStateOf(true) }
    var currentScreen by remember { mutableStateOf(LudoScreen.HOME) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle incoming notification snackbars
    LaunchedEffect(errorMessage, successMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.clearMessages()
        }
        successMessage?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.clearMessages()
        }
    }

    val activeSession by viewModel.activeSession.collectAsState()

    // Splash Timer
    LaunchedEffect(Unit) {
        delay(2200)
        showSplash = false
    }

    if (showSplash) {
        PremiumSplashScreen(onSkip = { showSplash = false })
    } else if (activeSession == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = BackgroundBlack,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AuthFlowContainer(
                    viewModel = viewModel,
                    onAuthSuccess = { username ->
                        currentScreen = LudoScreen.HOME
                    }
                )
            }
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                LudoBottomNavigation(
                    currentScreen = currentScreen,
                    onSelectScreen = { currentScreen = it }
                )
            },
            containerColor = BackgroundBlack,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .drawBehind {
                        // Soft desert sun background gradient
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SecondaryRed.copy(alpha = 0.25f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.2f),
                                radius = size.width * 0.7f
                            )
                        )
                    }
            ) {
                if (walletState?.isBanned == true) {
                    BannedView(viewModel = viewModel)
                } else {
                    Crossfade(
                        targetState = currentScreen,
                        animationSpec = tween(300),
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            LudoScreen.HOME -> HomeScreen(
                                viewModel = viewModel,
                                wallet = walletState,
                                tables = tables,
                                disputes = disputes,
                                onNavigateToTables = { currentScreen = LudoScreen.TABLES },
                                onNavigateToWallet = { currentScreen = LudoScreen.WALLET }
                            )
                            LudoScreen.TABLES -> TablesScreen(
                                viewModel = viewModel,
                                tables = tables
                            )
                            LudoScreen.TOURNAMENTS -> TournamentsScreen(
                                tournaments = tournaments,
                                onRegister = { t ->
                                    scope.launch {
                                        viewModel.createTable(
                                            entryAmount = t.entryFee,
                                            playerCount = 2,
                                            isPrivate = false,
                                            roomCode = String.format("%06d", Random.nextInt(100000, 999999))
                                        )
                                        snackbarHostState.showSnackbar("Registered for ${t.title}! Tournament match table created.")
                                    }
                                }
                            )
                            LudoScreen.WALLET -> WalletScreen(
                                viewModel = viewModel,
                                wallet = walletState,
                                transactions = transactions
                            )
                            LudoScreen.PROFILE -> ProfileScreen(
                                viewModel = viewModel,
                                wallet = walletState,
                                transactions = transactions
                            )
                            LudoScreen.ADMIN -> AdminPanelScreen(
                                viewModel = viewModel,
                                wallet = walletState,
                                disputes = disputes,
                                tables = tables
                            )
                        }
                    }
                }

                // Small Royal floating action button to access the Admin Console quickly to test disputes!
                FloatingActionButton(
                    onClick = {
                        currentScreen = if (currentScreen == LudoScreen.ADMIN) LudoScreen.HOME else LudoScreen.ADMIN
                    },
                    containerColor = RoyalGold,
                    contentColor = BackgroundBlack,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag("admin_toggle_fab")
                ) {
                    Icon(
                        imageVector = if (currentScreen == LudoScreen.ADMIN) Icons.Default.SportsEsports else Icons.Default.Gavel,
                        contentDescription = "Toggle Admin Panel"
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 1: ADAPTIVE ANIMATED ROYAL SPLASH SCREEN
// ----------------------------------------------------
@Composable
fun PremiumSplashScreen(onSkip: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    
    // Rotating Crown angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse Logo
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Animated particles
    val particleOffsets = remember {
        List(15) {
            Offset(Random.nextFloat() * 1000f, Random.nextFloat() * 1600f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundBlack, Color(0xFF1C0303), BackgroundBlack)
                )
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Draw float gold dust stars
        Canvas(modifier = Modifier.fillMaxSize()) {
            particleOffsets.forEach { base ->
                drawCircle(
                    color = RoyalGold.copy(alpha = 0.35f),
                    radius = Random.nextInt(3, 8).dp.toPx(),
                    center = Offset(
                        x = (base.x + rotation * 0.5f) % size.width,
                        y = (base.y - rotation * 0.2f) % size.height
                    )
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Logo emblem
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .drawBehind {
                        val width = size.width
                        val height = size.height

                        // Golden radial glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(RoyalGold.copy(alpha = 0.3f), Color.Transparent),
                                center = Offset(width * 0.5f, height * 0.5f),
                                radius = width * 0.55f
                            )
                        )

                        // Red crimson neon portal arc
                        drawArc(
                            color = PrimaryRed,
                            startAngle = -45f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Interactive dynamic Shield Crown + Dice shape
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .testTag("animated_crown_canvas")
                ) {
                    val w = size.width
                    val h = size.height

                    rotate(rotation, pivot = Offset(w * 0.5f, h * 0.5f)) {
                        // Draw small surrounding gold halo rings
                        drawCircle(
                            color = RoyalGold.copy(alpha = 0.4f),
                            radius = w * 0.42f,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f))
                        )
                    }

                    // Draw 3D dice symbol inside
                    val diceSize = w * 0.4f
                    val diceX = w * 0.3f
                    val diceY = h * 0.3f

                    drawRoundRect(
                        brush = Brush.linearGradient(colors = listOf(PrimaryRed, DarkRed)),
                        topLeft = Offset(diceX, diceY),
                        size = Size(diceSize, diceSize),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )

                    // Draw dice pips (gold dots)
                    drawCircle(color = RoyalGold, radius = 3.dp.toPx(), center = Offset(diceX + diceSize * 0.25f, diceY + diceSize * 0.25f))
                    drawCircle(color = RoyalGold, radius = 3.dp.toPx(), center = Offset(diceX + diceSize * 0.75f, diceY + diceSize * 0.25f))
                    drawCircle(color = RoyalGold, radius = 3.dp.toPx(), center = Offset(diceX + diceSize * 0.5f, diceY + diceSize * 0.5f))
                    drawCircle(color = RoyalGold, radius = 3.dp.toPx(), center = Offset(diceX + diceSize * 0.25f, diceY + diceSize * 0.75f))
                    drawCircle(color = RoyalGold, radius = 3.dp.toPx(), center = Offset(diceX + diceSize * 0.75f, diceY + diceSize * 0.75f))

                    // Draw overlay Crown (Top)
                    val crownPath = Path().apply {
                        moveTo(w * 0.25f, h * 0.28f)
                        lineTo(w * 0.15f, h * 0.12f)
                        lineTo(w * 0.35f, h * 0.2f)
                        lineTo(w * 0.5f, h * 0.05f) // Peak
                        lineTo(w * 0.65f, h * 0.2f)
                        lineTo(w * 0.85f, h * 0.12f)
                        lineTo(w * 0.75f, h * 0.28f)
                        quadraticBezierTo(w * 0.5f, h * 0.32f, w * 0.25f, h * 0.28f)
                        close()
                    }
                    drawPath(path = crownPath, brush = Brush.linearGradient(colors = listOf(RoyalGold, Color(0xFFCC9900))))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Name
            Text(
                text = "RANGILO LUDO",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                style = MaterialTheme.typography.displayLarge
            )

            // Dynamic gold horizontal line
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(180.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, RoyalGold, Color.Transparent)
                        )
                    )
            )

            // Tagline
            Text(
                text = "Play • Win • Rule",
                color = RoyalGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Progress loader
            CircularProgressIndicator(
                color = PrimaryRed,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Skip trigger target
            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                modifier = Modifier.testTag("skip_splash_button")
            ) {
                Text("Enter Kingdom", color = RoyalGold.copy(alpha = 0.8f))
            }
        }
    }
}

// ----------------------------------------------------
// UI MODULE: BOTTOM NAVIGATION BAR
// ----------------------------------------------------
@Composable
fun LudoBottomNavigation(currentScreen: LudoScreen, onSelectScreen: (LudoScreen) -> Unit) {
    Surface(
        color = BackgroundBlack,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val screens = listOf(
                Triple(LudoScreen.HOME, "Home", "🏠"),
                Triple(LudoScreen.TABLES, "Tables", "⚔️"),
                Triple(LudoScreen.TOURNAMENTS, "Play", "🏆"),
                Triple(LudoScreen.WALLET, "Wallet", "👛"),
                Triple(LudoScreen.PROFILE, "Profile", "👤")
            )

            screens.forEach { (screen, title, icon) ->
                val isSelected = currentScreen == screen
                if (screen == LudoScreen.TOURNAMENTS) {
                    // Elevated Center element
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(y = (-12).dp)
                            .clickable { onSelectScreen(screen) }
                            .testTag("nav_item_${screen.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(RoyalGold, Color(0xFFE6A600))
                                    )
                                )
                                .border(4.dp, BackgroundBlack, CircleShape)
                                .drawBehind {
                                    // Golden shadow glow
                                    drawCircle(
                                        color = RoyalGold.copy(alpha = 0.4f),
                                        radius = size.width * 0.6f
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = title.uppercase(),
                            color = RoyalGold,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Regular elements
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectScreen(screen) }
                            .padding(vertical = 4.dp)
                            .testTag("nav_item_${screen.name.lowercase()}"),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = icon,
                            fontSize = 20.sp,
                            modifier = Modifier.alpha(if (isSelected) 1f else 0.4f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = title.uppercase(),
                            color = if (isSelected) PrimaryRed else Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.alpha(if (isSelected) 1f else 0.4f)
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// BANNED VIEW OVERLAY
// ----------------------------------------------------
@Composable
fun BannedView(viewModel: WalletViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Banned Icon",
                tint = PrimaryRed,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Access Suspended",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your account has been restricted by Rangeelo Ludo admin due to screenshot verification discrepancies or policy breach.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { viewModel.adminBanUser(false) },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)
            ) {
                Text("Appeal & Reinstate Profile", color = BackgroundBlack)
            }
        }
    }
}

// ----------------------------------------------------
// BEAUTIFUL DESIGN HELPERS FOR THE IMMERSIVE UI
// ----------------------------------------------------
@Composable
fun RoyalHeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 7f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .drawBehind {
                // Gradient overlay 1: Linear from DarkRed (#C1121F) via PrimaryRed (#FF1E1E) to transparent
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(DarkRed, PrimaryRed, Color.Transparent),
                        startX = 0f,
                        endX = size.width
                    )
                )
                // Gradient overlay 2: Radial gradient from top right with RoyalGold (#FFB800) at 30% width
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(RoyalGold.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.7f
                    )
                )
            }
            .padding(16.dp)
    ) {
        // Content Area
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "MEGA EVENT",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "ROYAL CLASH\nCUP 2024",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 24.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Win Upto ₹1,00,000 Today",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Decorative Dice in bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 10.dp)
                .alpha(0.35f)
        ) {
            Text(
                text = "🎲",
                fontSize = 52.sp
            )
        }
    }
}

@Composable
fun QuickStatsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBlack),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ONLINE",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "12.4K",
                    color = SuccessGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Card 2
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBlack),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ACTIVE TABLES",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "842",
                    color = PrimaryRed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RoyalReferralBanner(inviteCode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalGold)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "INVITE & EARN",
                    color = Color.Black.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Get ₹50 per friend",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = inviteCode.uppercase(),
                    color = RoyalGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 2: ACTIVE HOME SCREEN
// ----------------------------------------------------
@Composable
fun HomeScreen(
    viewModel: WalletViewModel,
    wallet: UserWallet?,
    tables: List<LudoTable>,
    disputes: List<Dispute>,
    onNavigateToTables: () -> Unit,
    onNavigateToWallet: () -> Unit
) {
    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Immersive Title bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile/Avatar nested circular design with gradient + crown from HTML
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.sweepGradient(
                                    colors = listOf(PrimaryRed, RoyalGold, PrimaryRed)
                                )
                            )
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(BackgroundBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👑", fontSize = 18.sp)
                        }
                    }

                    Column {
                        Text(
                            text = "RANGILO LUDO",
                            color = RoyalGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Play • Win • Rule",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Balance badge capsule from HTML
                Box(
                    modifier = Modifier
                        .background(CardBlack, RoundedCornerShape(20.dp))
                        .border(1.dp, RoyalGold.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .clickable { onNavigateToWallet() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "₹ ${String.format("%.2f", wallet?.totalBalance ?: 0.0)}",
                            color = RoyalGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(RoyalGold)
                                .clickable { showDepositDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                color = BackgroundBlack,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Hero Mega Event Banner
        item {
            RoyalHeroBanner()
        }

        // Live Quick Stats Row online status
        item {
            QuickStatsRow()
        }

        // Shimmering Luxury Wallet Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_wallet_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(RoyalGold.copy(0.4f), PrimaryRed.copy(0.2f))))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Gold shimmering background arc
                            drawCircle(
                                color = RoyalGold.copy(alpha = 0.08f),
                                radius = size.width * 0.45f,
                                center = Offset(size.width * 0.9f, size.height * 0.5f)
                            )
                        }
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    "ACTIVE GAMING BALANCES",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "₹${wallet?.totalBalance ?: 0.0}",
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            
                            IconButton(onClick = onNavigateToWallet) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Wallet History",
                                    tint = RoyalGold
                                )
                            }
                        }
                        
                        Divider(
                            color = Color.White.copy(alpha = 0.1f), 
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Deposit", color = Color.Gray, fontSize = 11.sp)
                                Text("₹${wallet?.depositBalance ?: 0.0}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Winnings", color = Color.Gray, fontSize = 11.sp)
                                Text("₹${wallet?.winningBalance ?: 0.0}", color = RoyalGold, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Column {
                                Text("Bonus Reward", color = Color.Gray, fontSize = 11.sp)
                                Text("₹${wallet?.bonusBalance ?: 0.0}", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Wallet Quick interactive deposit/withdraw actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showDepositDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("action_deposit_quick"),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Cash", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { showWithdrawDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("action_withdraw_quick"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, RoyalGold),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Withdraw", color = RoyalGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Entry joining buttons
        item {
            Column {
                Text(
                    "QUICK JOIN LUDO POOLS",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val entryFeeOptions = listOf(19.0, 49.0, 99.0, 199.0)
                    entryFeeOptions.forEach { fee ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardBlack)
                                .border(1.dp, DarkRed.copy(0.4f), RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.createTable(
                                        entryAmount = fee,
                                        playerCount = 2,
                                        isPrivate = false,
                                        roomCode = String.format("%06d", Random.nextInt(100000, 999999))
                                    )
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LUDO KING", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("₹${fee.toInt()}", color = RoyalGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Win Win", color = SuccessGreen, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Ludo Matchmaking Tables
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LIVE MATCHMAKING TABLES",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onNavigateToTables) {
                    Text("Create Custom Table", color = RoyalGold, fontSize = 12.sp)
                }
            }

            val waitingTables = tables.filter { it.status == "WAITING" || it.status == "PLAYING" || it.status == "RESULT_SUBMITTED" }
            if (waitingTables.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(CardBlack, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("No active matches. Create one above!", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    waitingTables.take(5).forEach { table ->
                        TableProgressLogItem(table = table, viewModel = viewModel, clipboardManager = clipboardManager)
                    }
                }
            }
        }

        // Previous Winners Slider
        item {
            Column {
                Text(
                    "RECENT ROYAL WINNERS payouts",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(DarkRed.copy(0.4f), CardBlack)))
                        .border(1.dp, RoyalGold.copy(0.2f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(RoyalGold.copy(0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Ludo_King_Udaipur", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Earned crown on Clash Cup", color = Color.LightGray, fontSize = 11.sp)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹4,500", color = SuccessGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Settled via UPI", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Daily Pavitra Rewards Tracker
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(0.5.dp, Color.White.copy(0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎁", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Daily Royal Darbar Bonus", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Claim free ₹10 bonus cash daily", color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { viewModel.depositFunds(10.0, "DAILY_DARBAR") },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Claim", color = BackgroundBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Immersive Invite / Referral Banner from HTML design (fully functional!)
        item {
            RoyalReferralBanner(inviteCode = wallet?.inviteCode ?: "RL2024")
        }

        // Mini Leaderboard Hub
        item {
            Column {
                Text(
                    "LEADERBOARD LEGENDS",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBlack),
                    border = BorderStroke(0.5.dp, RoyalGold.copy(0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val champions = listOf(
                            Triple("Rajput_Fighter", "₹42,500", "Wins: 184"),
                            Triple("Marwar_Warrior", "₹31,000", "Wins: 110"),
                            Triple("Royal_Thakur", "₹22,900", "Wins: 91")
                        )

                        champions.forEachIndexed { index, champ ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${index + 1}",
                                        color = when(index) {
                                            0 -> RoyalGold
                                            1 -> Color.LightGray
                                            else -> DarkRed
                                        },
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.width(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(champ.first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(champ.third, color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                                Text(champ.second, color = RoyalGold, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            if (index < champions.size - 1) {
                                Divider(color = Color.White.copy(0.05f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog for Deposit Integration
    if (showDepositDialog) {
        DepositSimulationDialog(
            onDismiss = { showDepositDialog = false },
            onConfirm = { amt, upi ->
                viewModel.depositFunds(amt, upi)
                showDepositDialog = false
            }
        )
    }

    // Modal Dialog for Withdraw System
    if (showWithdrawDialog) {
        WithdrawSimulationDialog(
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amt, upi ->
                viewModel.withdrawFunds(amt, upi)
                showWithdrawDialog = false
            }
        )
    }
}

// Support Item: Live table list logger
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableProgressLogItem(
    table: LudoTable,
    viewModel: WalletViewModel,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    var showResultSheet by remember { mutableStateOf(false) }
    var showDisputeSheet by remember { mutableStateOf(false) }

    val isHighStake = table.entryAmount >= 100
    val cardBorderColor = when (table.status) {
        "WAITING" -> if (isHighStake) RoyalGold.copy(0.25f) else Color.White.copy(0.08f)
        "PLAYING" -> PrimaryRed.copy(0.4f)
        "RESULT_SUBMITTED" -> Color.Gray.copy(0.3f)
        else -> Color.White.copy(0.05f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("table_item_${table.id}")
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    table.roomCode?.let {
                        clipboardManager.setText(AnnotatedString(it))
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlack),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // First Row: Header with Status indicator and ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = when (table.status) {
                                    "WAITING" -> RoyalGold
                                    "PLAYING" -> PrimaryRed
                                    "RESULT_SUBMITTED" -> Color.LightGray
                                    else -> SuccessGreen
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (table.status) {
                            "WAITING" -> "Open Challenge"
                            "PLAYING" -> "Match In Progress"
                            "RESULT_SUBMITTED" -> "Claim Sent"
                            else -> "Finalized"
                        },
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "Table #${table.id}",
                    color = Color.DarkGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle Row: Avatar on left, Title & Entry in middle, Join/Action on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Luxury Avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1A1A1A), Color(0xFF0A0A0A))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHighStake) "👑" else "🎲",
                            fontSize = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        val tableTypeName = when {
                            table.entryAmount >= 500 -> "Diamond Royale"
                            table.entryAmount >= 100 -> "Crown Imperial"
                            else -> "Quick Battle"
                        }
                        Text(
                            text = tableTypeName,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ENTRY: ₹${table.entryAmount.toInt()}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Action button depending on status
                Box {
                    if (table.status == "WAITING") {
                        if (table.creatorName != "Rana Pratap Ludo" && table.creatorName != "Maharana Ludo" && table.creatorName != "Rana Pratap Ludo") {
                            if (isHighStake) {
                                // Red Gradient joining button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(PrimaryRed, DarkRed)
                                            )
                                        )
                                        .clickable { viewModel.joinTable(table.id) }
                                        .drawBehind {
                                            // Soft red glow
                                            drawRoundRect(
                                                color = PrimaryRed.copy(alpha = 0.2f),
                                                size = size,
                                                cornerRadius = CornerRadius(12.dp.toPx())
                                            )
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "JOIN",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            } else {
                                // Outlined button
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, PrimaryRed, RoundedCornerShape(12.dp))
                                        .clickable { viewModel.joinTable(table.id) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "JOIN",
                                        color = PrimaryRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "MATCHING...",
                                color = RoyalGold.copy(0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Display match target winnings badge
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Win Prize", color = Color.Gray, fontSize = 9.sp)
                            Text(
                                text = "₹${(table.entryAmount * 1.85).toInt()}",
                                color = RoyalGold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Expiry/Room Code detail parameters
            table.roomCode?.let { rCode ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ludo King Code: $rCode",
                        color = RoyalGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = Color.LightGray,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(rCode))
                            }
                    )
                }
            }

            // Waiting, Active Match Declarations, or Admin Reviews rows
            if (table.status == "PLAYING") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showResultSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(36.dp)
                    ) {
                        Text("Declare Winner", color = BackgroundBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showDisputeSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, PrimaryRed),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(36.dp)
                    ) {
                        Text("Dispute", color = PrimaryRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (table.status == "RESULT_SUBMITTED") {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(0.04f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Payment audit in progress • Results declared instantly",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }

    // Settle claim modals
    if (showResultSheet) {
        WinnerClaimDialog(
            tableId = table.id,
            onDismiss = { showResultSheet = false },
            onConfirm = { proofUri ->
                viewModel.submitVictory(table.id, proofUri)
                showResultSheet = false
            }
        )
    }

    if (showDisputeSheet) {
        DisputeRaiseDialog(
            tableId = table.id,
            onDismiss = { showDisputeSheet = false },
            onConfirm = { desc, proofUri ->
                viewModel.disputeMatch(table.id, desc, proofUri)
                showDisputeSheet = false
            }
        )
    }
}

// ----------------------------------------------------
// SCREEN 3: CUSTOM MATCHMAKING TABLES LIST
// ----------------------------------------------------
@Composable
fun TablesScreen(viewModel: WalletViewModel, tables: List<LudoTable>) {
    var entryInput by remember { mutableStateOf("19") }
    var selectPlayers by remember { mutableStateOf(2) }
    var selectPrivate by remember { mutableStateOf(false) }
    var roomCodeInput by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("tables_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                "CHALLENGE CENTER",
                color = RoyalGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Establish custom arenas to connect with players in Ludo King.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        // Table Builder Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Forge Ludo Challenge Board",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Entry fee amount
                    Text("Entry Amount (₹)", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = entryInput,
                        onValueChange = { entryInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("E.g. 19, 49, 100", color = Color.Gray) },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalGold,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("table_entry_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom presets rows
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("19", "49", "599", "1000", "2500").forEach { p ->
                            Box(
                                modifier = Modifier
                                    .border(0.5.dp, if (entryInput == p) RoyalGold else Color.DarkGray, RoundedCornerShape(4.dp))
                                    .background(if (entryInput == p) PrimaryRed.copy(0.15f) else Color.Transparent)
                                    .clickable { entryInput = p }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("₹$p", color = if (entryInput == p) RoyalGold else Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Players count option
                    Text("Ludo King Matches Count", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectPlayers == 2,
                                onClick = { selectPlayers = 2 },
                                colors = RadioButtonDefaults.colors(selectedColor = RoyalGold)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("2 Players (1v1)", color = Color.White, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectPlayers == 4,
                                onClick = { selectPlayers = 4 },
                                colors = RadioButtonDefaults.colors(selectedColor = RoyalGold)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("4 Players (Ludo Jam)", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Public / Private Check
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Private Table Invite", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Restricts table visibility to code linkers", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = selectPrivate,
                            onCheckedChange = { selectPrivate = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = RoyalGold)
                        )
                    }

                    if (selectPrivate) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Custom Room Code", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = roomCodeInput,
                            onValueChange = { roomCodeInput = it },
                            placeholder = { Text("E.g. Enter manual room code", color = Color.Gray) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val fee = entryInput.toDoubleOrNull() ?: 19.0
                            val code = if (roomCodeInput.isNotEmpty()) roomCodeInput else null
                            viewModel.createTable(fee, selectPlayers, selectPrivate, code)
                            roomCodeInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_table_submit")
                    ) {
                        Text("Establish Board Arena & Share", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // List Header
        item {
            Text(
                "OPEN CHALLENGES",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        val allActive = tables.filter { it.status == "WAITING" || it.status == "PLAYING" || it.status == "RESULT_SUBMITTED" }
        if (allActive.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(CardBlack, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No online tables found. Create yours above to play!", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(allActive) { table ->
                TableProgressLogItem(table = table, viewModel = viewModel, clipboardManager = clipboardManager)
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 4: ROYAL TOURNAMENTS (10 PRESETS)
// ----------------------------------------------------
@Composable
fun TournamentsScreen(tournaments: List<Tournament>, onRegister: (Tournament) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("tournaments_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                "ROYAL CLASH TOURNAMENTS",
                color = RoyalGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Battle in premium multi-player tournaments. 6% client share, massive prizes.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        if (tournaments.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoyalGold)
                }
            }
        } else {
            items(tournaments) { cup ->
                TournamentCupCard(cup = cup, onRegister = onRegister)
            }
        }
    }
}

@Composable
fun TournamentCupCard(cup: Tournament, onRegister: (Tournament) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tournament_card_${cup.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlack),
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(RoyalGold.copy(0.3f), Color.Transparent)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(cup.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clashes in ${cup.countdownMinutes} minutes", color = Color.LightGray, fontSize = 11.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SuccessGreen.copy(0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Auto-Start", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Prize Pool", color = Color.Gray, fontSize = 10.sp)
                    Text("₹${cup.prizePool.toInt()}", color = RoyalGold, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Entry Fee", color = Color.Gray, fontSize = 10.sp)
                    Text("₹${cup.entryFee.toInt()}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onRegister(cup) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Join Arena", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Player progress bar
            val progress = remember { (cup.joinedCount.toFloat() / cup.maxPlayers.toFloat()).coerceIn(0.1f, 1.0f) }
            Column {
                LinearProgressIndicator(
                    progress = progress,
                    color = RoyalGold,
                    trackColor = Color.DarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${cup.joinedCount} Joined", color = Color.Gray, fontSize = 10.sp)
                    Text("${cup.maxPlayers} Spots Left", color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 5: WALLET DEPOSIT & WITHDRAW DEEP WORKFLOWS
// ----------------------------------------------------
@Composable
fun WalletScreen(
    viewModel: WalletViewModel,
    wallet: UserWallet?,
    transactions: List<WalletTransaction>
) {
    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("wallet_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                "SECURED WALLET VAULT",
                color = RoyalGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Claim victories, settle immediately, and withdraw to instant UPI.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        // Royal Vault details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(1.dp, RoyalGold.copy(0.3f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("CONSOLIDATED ACCOUNT BALANCE", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("₹${wallet?.totalBalance ?: 0.0}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Deposit Balance", color = Color.Gray, fontSize = 10.sp)
                                Text("₹${wallet?.depositBalance ?: 0.0}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Winning Balance", color = Color.Gray, fontSize = 10.sp)
                                Text("₹${wallet?.winningBalance ?: 0.0}", color = RoyalGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showDepositDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Deposit Cash", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showWithdrawDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, RoyalGold),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Withdraw UPI", color = RoyalGold, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimum barrier reminder tag
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryRed.copy(0.08f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, PrimaryRed.copy(0.2f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⚠️ Minimum withdrawal limit: ₹300 from Winning balance.",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Transactions History Log list
        item {
            Text(
                "TRANSACTION HISTORY LOGS",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(CardBlack, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions completed matching criteria query.", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(transactions) { txn ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBlack),
                    border = BorderStroke(0.5.dp, Color.White.copy(0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        when (txn.type) {
                                            "DEPOSIT" -> SuccessGreen.copy(0.12f)
                                            "WITHDRAW" -> PrimaryRed.copy(0.12f)
                                            "MATCH_ENTRY" -> Color.LightGray.copy(0.12f)
                                            "MATCH_WIN" -> RoyalGold.copy(0.12f)
                                            else -> SuccessGreen.copy(0.12f)
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when(txn.type) {
                                        "DEPOSIT" -> Icons.Default.Add
                                        "WITHDRAW" -> Icons.Default.Remove
                                        "MATCH_ENTRY" -> Icons.Default.Casino
                                        "MATCH_WIN" -> Icons.Default.EmojiEvents
                                        else -> Icons.Default.AccountBalanceWallet
                                    },
                                    contentDescription = null,
                                    tint = when (txn.type) {
                                        "DEPOSIT" -> SuccessGreen
                                        "WITHDRAW" -> PrimaryRed
                                        "MATCH_ENTRY" -> Color.White
                                        "MATCH_WIN" -> RoyalGold
                                        else -> SuccessGreen
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(txn.details, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = when(txn.type) {
                                        "DEPOSIT" -> "UPI Deposit Cash"
                                        "WITHDRAW" -> "Instant UPI Withdrawal"
                                        "MATCH_ENTRY" -> "Wallet Match Challenge Fee"
                                        "MATCH_WIN" -> "Settled Gaming Victory"
                                        else -> "Wallet Settlement"
                                    },
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = (if (txn.type == "WITHDRAW" || txn.type == "MATCH_ENTRY") "-" else "+") + "₹${txn.amount.toInt()}",
                                color = when (txn.type) {
                                    "WITHDRAW" -> PrimaryRed
                                    "MATCH_ENTRY" -> Color.LightGray
                                    else -> SuccessGreen
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("SUCCESS", color = SuccessGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }
    }

    if (showDepositDialog) {
        DepositSimulationDialog(
            onDismiss = { showDepositDialog = false },
            onConfirm = { amt, upi ->
                viewModel.depositFunds(amt, upi)
                showDepositDialog = false
            }
        )
    }

    if (showWithdrawDialog) {
        WithdrawSimulationDialog(
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amt, upi ->
                viewModel.withdrawFunds(amt, upi)
                showWithdrawDialog = false
            }
        )
    }
}

// ----------------------------------------------------
// SCREEN 6: USER PROFILE & REFERRAL SYSTEMS
// ----------------------------------------------------
@Composable
fun ProfileScreen(viewModel: WalletViewModel, wallet: UserWallet?, transactions: List<WalletTransaction>) {
    var promoInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                "ROYAL GLORY PROFILE",
                color = RoyalGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Monitor your arena records, stats, badges, and rewards history.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        // Stats card display
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(1.dp, PrimaryRed.copy(0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(RoyalGold.copy(0.12f))
                            .border(2.dp, RoyalGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👑", fontSize = 36.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(wallet?.userName ?: "Maharana Ludo", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Invite Code: ${wallet?.inviteCode ?: "ROYAL777"}", color = RoyalGold, fontSize = 12.sp, modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(wallet?.inviteCode ?: "ROYAL777"))
                    })

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Clashes Played", color = Color.Gray, fontSize = 10.sp)
                            Text("${wallet?.matchesPlayed ?: 0}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Royal Victories", color = Color.Gray, fontSize = 10.sp)
                            Text("${wallet?.matchesWon ?: 0}", color = SuccessGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Golden Earnings", color = Color.Gray, fontSize = 10.sp)
                            Text("₹${wallet?.totalEarnings?.toInt() ?: 0}", color = RoyalGold, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // Referral Card System
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1414)),
                border = BorderStroke(1.dp, RoyalGold.copy(0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💸 Invite Friends, Claim ₹50 Free Bonus!", color = RoyalGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Share your exclusive royal invitation code. Earn ₹50 golden tokens instantly credited to your wallet bonus once they claim.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = promoInput,
                            onValueChange = { promoInput = it },
                            placeholder = { Text("Enter invite code", color = Color.Gray) },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = RoyalGold,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("referral_input_field")
                        )

                        Button(
                            onClick = {
                                if (promoInput.isNotBlank()) {
                                    viewModel.applyReferral(promoInput)
                                    promoInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Apply", color = BackgroundBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Badges Section achievements
        item {
            Column {
                Text(
                    "OBTAINED WARRIOR ACHIEVEMENT BADGES",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badges = listOf(
                        Pair("🎯 First Victory", "Defeat your first opponent in Ludo King"),
                        Pair("🔱 Rajput Guard", "Played more than 20 Clash matches"),
                        Pair("💰 Treasure Hoarder", "Earned more than ₹1000 winnings")
                    )

                    badges.forEach { b ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBlack)
                                .border(0.5.dp, RoyalGold.copy(0.15f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(b.first, color = RoyalGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(b.second, color = Color.Gray, fontSize = 9.sp, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Secure Logout Button Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.logout()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile_logout_button"),
                border = BorderStroke(1.dp, RoyalGold.copy(0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Logout, contentDescription = "Logout icon", tint = Color.White)
                    Text("SECURE LOBBY LOGOUT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------
// INTERACTIVE SIMULATION DIALOGS
// ----------------------------------------------------
@Composable
fun DepositSimulationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var depAmt by remember { mutableStateOf("500") }
    var upiId by remember { mutableStateOf("khadoliyavikash@upi") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Secured Indian UPI Deposit Gate", color = RoyalGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Simulates instant cash deposit gateway using fast Indian UPI integrations.", color = Color.LightGray, fontSize = 12.sp)
                
                Text("Deposit Amount (₹)", color = Color.White, fontSize = 12.sp)
                OutlinedTextField(
                    value = depAmt,
                    onValueChange = { depAmt = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Sender UPI ID Virtual Address", color = Color.White, fontSize = 12.sp)
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = depAmt.toDoubleOrNull() ?: 500.0
                    onConfirm(amt, upiId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Text("Authorize UPI Payment", color = BackgroundBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abondon Portal", color = Color.Gray)
            }
        },
        containerColor = CardBlack,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun WithdrawSimulationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var withAmt by remember { mutableStateOf("300") }
    var upiId by remember { mutableStateOf("khadoliyavikash@upi") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdraw Winning Balance to UPI", color = RoyalGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Request direct UPI settlement payout. Min Limit: ₹300.", color = Color.LightGray, fontSize = 12.sp)

                Text("Withdraw Amount (₹)", color = Color.White, fontSize = 12.sp)
                OutlinedTextField(
                    value = withAmt,
                    onValueChange = { withAmt = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Destination UPI address / VPA", color = Color.White, fontSize = 12.sp)
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = withAmt.toDoubleOrNull() ?: 300.0
                    onConfirm(amt, upiId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                Text("Confirm Settlement", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = CardBlack,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun WinnerClaimDialog(
    tableId: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Claim Victory Settle Request", color = RoyalGold, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Did you win the match inside Ludo King?", color = Color.White, fontSize = 14.sp)
                Text("Submitting victory will prompt opponent review. Any fake claims will attract a suspension to user profile.", color = Color.LightGray, fontSize = 12.sp)
                Text("Simulating match outcome screenshot upload below:", color = Color.Gray, fontSize = 11.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color.White.copy(0.05f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📸 result_screenshot.png (Attached)", color = SuccessGreen, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm("content://media/external/images/media/ludo_victory_screenshot") },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Text("Send Victory Claim", color = BackgroundBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = CardBlack,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DisputeRaiseDialog(
    tableId: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var msg by remember { mutableStateOf("Opponent fake disconnected during last move!") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conflict Dispute Reporter", color = PrimaryRed, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Report rule breaches, exit cheats, or fake claims directly to the Rangeelo Ludo referee panel.", color = Color.LightGray, fontSize = 12.sp)

                Text("Provide match remarks / issue", color = Color.White, fontSize = 12.sp)
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color.White.copy(0.05f), RoundedCornerShape(6.dp))
                        .border(1.dp, PrimaryRed.copy(0.3f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📸 dispute_proof.png (Auto-uploaded)", color = RoyalGold, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(msg, "content://media/external/images/media/dispute_proof") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                Text("File Global Dispute", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = CardBlack,
        shape = RoundedCornerShape(16.dp)
    )
}

// ----------------------------------------------------
// SCREEN 7: REAL-TIME SECURED ADMIN PANEL
// ----------------------------------------------------
@Composable
fun AdminPanelScreen(
    viewModel: WalletViewModel,
    wallet: UserWallet?,
    disputes: List<Dispute>,
    tables: List<LudoTable>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("admin_panel_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "REFREE CONSOLE",
                        color = RoyalGold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Resolve matches, handle disputes, and monitor game revenue.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = { viewModel.resetDatabase() },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Reset DB", color = Color.White, fontSize = 10.sp)
                }
            }
        }

        // Live stats counter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardBlack)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Active Tables", color = Color.Gray, fontSize = 11.sp)
                        Text("${tables.size}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardBlack)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Active Disputes", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = "${disputes.filter { it.status == "PENDING" }.size}",
                            color = PrimaryRed,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Action block: Manage User Banning Simulation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(1.dp, RoyalGold.copy(0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("User Control Panel", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Simulate ban restriction for the current wallet model", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (wallet?.isBanned == true) "Current Status: Banned" else "Current Status: Active Player",
                            color = if (wallet?.isBanned == true) PrimaryRed else SuccessGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { viewModel.adminBanUser(wallet?.isBanned != true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (wallet?.isBanned == true) SuccessGreen else PrimaryRed
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(if (wallet?.isBanned == true) "Reinstate User" else "Ban Account", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Disputes List
        item {
            Text(
                "UNRESOLVED DISPUTE TICKETS",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        val pendingDisputes = disputes.filter { it.status == "PENDING" }
        if (pendingDisputes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(CardBlack, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active dispute tickets found. All matches settled!", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(pendingDisputes) { disp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBlack),
                    border = BorderStroke(1.dp, PrimaryRed.copy(0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dispute Ticket #${disp.id}", color = RoyalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Table #${disp.tableId}", color = Color.Gray, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Claimer: ${disp.claimerName}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Opponent Name: ${disp.opponentName}", color = Color.LightGray, fontSize = 13.sp)
                        Text("Reason: ${disp.info}", color = Color.LightGray, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(Color.White.copy(0.05f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🖼️ proof_validation_screenshot_img.png", color = SuccessGreen, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Award Winner Allocation Side:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.adminResolveDispute(disp.id, "CREATOR") },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Creator Wins", color = BackgroundBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.adminResolveDispute(disp.id, "OPPONENT") },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Opponent Wins", color = BackgroundBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.adminResolveDispute(disp.id, "VOID") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Refund Both", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
