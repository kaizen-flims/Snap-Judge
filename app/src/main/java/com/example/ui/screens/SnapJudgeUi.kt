package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.data.database.AnalysisEntity
import com.example.data.database.UserProgressEntity
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SnapJudgeViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

// --- NAVIGATION CONTAINER ---
@Composable
fun SnapJudgeNavigation(
    navController: NavHostController,
    viewModel: SnapJudgeViewModel
) {
    val progress by viewModel.userProgress.collectAsState()
    
    val startDestination = if (progress?.isOnboarded == true) "home" else "onboarding"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        composable("onboarding") {
            OnboardingScreen(navController, viewModel)
        }
        composable("home") {
            HomeScreen(navController, viewModel)
        }
        composable("camera") {
            CameraXScreen(navController, viewModel)
        }
        composable("loading") {
            AnalysisLoadingScreen(navController, viewModel)
        }
        composable("result") {
            AnalysisResultScreen(navController, viewModel)
        }
        composable("compare") {
            CompareScreen(navController, viewModel)
        }
        composable("history") {
            HistoryScreen(navController, viewModel)
        }
    }
}

// --- SCREEN 1: ONBOARDING ---
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: SnapJudgeViewModel
) {
    var step by remember { mutableStateOf(0) }
    var nameInput by remember { mutableStateOf("") }
    var chosenMode by remember { mutableStateOf("COACH") } // COACH, ROAST, PROFESSIONAL, BEGINNER
    
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val backgrounds = listOf(
        Brush.verticalGradient(listOf(Color(0xFF0C101B), Color(0xFF1B1429))),
        Brush.verticalGradient(listOf(Color(0xFF0C101B), Color(0xFF13221C))),
        Brush.verticalGradient(listOf(Color(0xFF0C101B), Color(0xFF271511))),
        Brush.verticalGradient(listOf(Color(0xFF0C101B), Color(0xFF18233C)))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgrounds[step % backgrounds.size])
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Name / Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = null,
                    tint = DarkPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SnapJudge X",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.5.sp
                    )
                )
            }

            // Central Animated Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "OnboardingSlides"
                ) { currentStep ->
                    when (currentStep) {
                        0 -> OnboardingIntroSlide()
                        1 -> OnboardingPrivacySlide()
                        2 -> OnboardingPersonaSlide(
                            selected = chosenMode,
                            onSelected = { chosenMode = it }
                        )
                        3 -> OnboardingNameSlide(
                            name = nameInput,
                            onNameChange = { nameInput = it }
                        )
                    }
                }
            }

            // Bottom Navigation and Progress Dots
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Dots Indicator
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(4) { idx ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (idx == step) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (idx == step) DarkPrimary else Color.Gray.copy(alpha = 0.5f))
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 0) {
                        TextButton(
                            onClick = { step-- },
                            modifier = Modifier.testTag("onboarding_back_button")
                        ) {
                            Text("Back", color = Color.White.copy(alpha = 0.7f))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(60.dp))
                    }

                    Button(
                        onClick = {
                            if (step < 3) {
                                step++
                            } else {
                                keyboardController?.hide()
                                val name = if (nameInput.trim().isEmpty()) "Photographer" else nameInput
                                viewModel.saveOnboarding(name, chosenMode)
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (step == 3) "Start Mentorship" else "Continue",
                                color = DarkBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = DarkBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingIntroSlide() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(2.dp, DarkPrimary.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = DarkPrimary,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Your AI Photography Mentor",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Upload or capture a photo. SnapJudge X acts as a panel of 11 world-class creative experts analyzing composition, colorism, storytelling, and commercial appeal.",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = TextSecondaryDark,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        )
    }
}

@Composable
fun OnboardingPrivacySlide() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(2.dp, DarkSecondary.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = DarkSecondary,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Privacy & Local Security",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your photos belong to you. We request standard camera and gallery permissions exclusively to analyze and enhance your work. Temporary files are cleared after processing.",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = TextSecondaryDark,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        )
    }
}

@Composable
fun OnboardingPersonaSlide(
    selected: String,
    onSelected: (String) -> Unit
) {
    val modes = listOf(
        Triple("COACH", "Coach Mode", "Friendly, supportive, educational feedback that explains key concepts simply."),
        Triple("ROAST", "Roast Mode", "Playful, witty, and humorous (but polite) roasts that highlight obvious errors."),
        Triple("PROFESSIONAL", "Pro Mode", "High-level technical review analyzing micro-contrast, lens aberration, and portfolio viability."),
        Triple("BEGINNER", "Beginner Mode", "Encouraging, patient guide focused on basic framing, rules of third, and lighting.")
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Choose Your Mentor Voice",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tailor how the expert panel communicates their critique.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondaryDark, textAlign = TextAlign.Center)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            modes.forEach { (mode, title, desc) ->
                val isSelected = selected == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) DarkPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) DarkPrimary else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelected(mode) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when(mode) {
                            "ROAST" -> Icons.Default.LocalFireDepartment
                            "PROFESSIONAL" -> Icons.Default.WorkspacePremium
                            "BEGINNER" -> Icons.Default.School
                            else -> Icons.Default.EmojiPeople
                        },
                        contentDescription = null,
                        tint = if (isSelected) DarkPrimary else TextSecondaryDark,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) DarkPrimary else Color.White
                            )
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingNameSlide(
    name: String,
    onNameChange: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(2.dp, DarkPrimary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = DarkPrimary,
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Tell Us Your Name",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Personalize your portfolio statistics and daily mentorship reports.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondaryDark, textAlign = TextAlign.Center)
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Enter your name...", color = TextSecondaryDark) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DarkPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color.White.copy(alpha = 0.03f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("onboarding_name_input"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
    }
}

// --- SCREEN 2: HOME SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: SnapJudgeViewModel
) {
    val progress by viewModel.userProgress.collectAsState()
    val analyses by viewModel.allAnalyses.collectAsState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val quote = "“Your first 10,000 photographs are your worst.” — Henri Cartier-Bresson"

    // Image Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.analyzePhoto(bitmap, progress?.selectedMode ?: "COACH")
                    navController.navigate("loading")
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = null,
                            tint = DarkPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SnapJudge X",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground
                ),
                actions = {
                    IconButton(onClick = {
                        // Change mentor mode popup
                        val nextMode = when(progress?.selectedMode) {
                            "COACH" -> "ROAST"
                            "ROAST" -> "PROFESSIONAL"
                            "PROFESSIONAL" -> "BEGINNER"
                            else -> "COACH"
                        }
                        viewModel.updateSelectedMode(nextMode)
                        Toast.makeText(context, "Persona set to: $nextMode", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = when(progress?.selectedMode) {
                                "ROAST" -> Icons.Default.LocalFireDepartment
                                "PROFESSIONAL" -> Icons.Default.WorkspacePremium
                                "BEGINNER" -> Icons.Default.School
                                else -> Icons.Default.EmojiPeople
                            },
                            contentDescription = "Switch Mode",
                            tint = DarkPrimary
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Profile & Streak Hero Card
            item {
                HomeHeroCard(progress = progress ?: UserProgressEntity())
            }

            // Quick Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ButtonCard(
                        title = "Live Camera",
                        subtitle = "Capture & Judge",
                        icon = Icons.Default.PhotoCamera,
                        color = DarkPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("camera") }
                    )
                    ButtonCard(
                        title = "Gallery Import",
                        subtitle = "Select Photo",
                        icon = Icons.Default.PhotoLibrary,
                        color = DarkSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = { galleryLauncher.launch("image/*") }
                    )
                }
            }

            // More Options Row (Compare & History)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ButtonCard(
                        title = "Compare Mode",
                        subtitle = "A vs B Verdict",
                        icon = Icons.Default.Compare,
                        color = DarkTertiary,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("compare") }
                    )
                    ButtonCard(
                        title = "History Log",
                        subtitle = "${analyses.size} Saved Analyses",
                        icon = Icons.Default.History,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("history") }
                    )
                }
            }

            // Daily Quote Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = DarkPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "DAILY CONTEMPLATION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DarkPrimary,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = quote,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }

            // Streaks & Challenges progress
            item {
                ChallengesCard(analysesSize = analyses.size)
            }

            // Recent Analyses Row
            if (analyses.isNotEmpty()) {
                item {
                    Text(
                        "RECENT ANALYSES",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextSecondaryDark,
                            letterSpacing = 1.sp
                        )
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(analyses.take(5)) { item ->
                            RecentAnalysisItem(item = item) {
                                viewModel.loadResultFromEntity(item)
                                navController.navigate("result")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun HomeHeroCard(progress: UserProgressEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, ${progress.name}!",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Mentor Mode: ${progress.selectedMode}",
                        style = MaterialTheme.typography.bodySmall.copy(color = DarkPrimary, fontWeight = FontWeight.Bold)
                    )
                }
                // Streak Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(DarkPrimary.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = DarkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${progress.streak} Day Streak",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkPrimary
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            // XP progress bar
            val currentLevelXp = progress.xp % 1000
            val percentage = currentLevelXp.toFloat() / 1000f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LEVEL ${progress.level}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                )
                Text(
                    "$currentLevelXp / 1000 XP",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = DarkPrimary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun ButtonCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondaryDark)
                )
            }
        }
    }
}

@Composable
fun ChallengesCard(analysesSize: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "DAILY MISSION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkSecondary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AssignmentTurnedIn,
                    contentDescription = null,
                    tint = DarkSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Shadow Chaser",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        "Analyze a high-contrast shadow photo to earn 150 XP",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (analysesSize > 0) SuccessGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (analysesSize > 0) "Complete" else "0/1",
                        color = if (analysesSize > 0) SuccessGreen else TextSecondaryDark,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentAnalysisItem(
    item: AnalysisEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                val file = File(item.imagePath)
                if (file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Analyzed image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextSecondaryDark)
                    }
                }
                // Score Tag
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${item.overallScore}",
                        color = DarkPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.style,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(item.timestamp)),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondaryDark)
                )
            }
        }
    }
}

// --- SCREEN 3: CAMERA SCREEN (CameraX) ---
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraXScreen(
    navController: NavController,
    viewModel: SnapJudgeViewModel
) {
    val context = LocalContext.current
    val progress by viewModel.userProgress.collectAsState()

    // Grid overlays: NONE, THIRDS, GOLDEN
    var activeGrid by remember { mutableStateOf("THIRDS") }
    var levelEnabled by remember { mutableStateOf(true) }
    var flashMode by remember { mutableStateOf("OFF") }
    var timerMode by remember { mutableStateOf("OFF") } // OFF, 3s, 10s
    
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // CameraX Viewport
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                flashMode = flashMode,
                activeGrid = activeGrid,
                levelEnabled = levelEnabled,
                onCaptured = { bitmap ->
                    viewModel.analyzePhoto(bitmap, progress?.selectedMode ?: "COACH")
                    navController.navigate("loading")
                }
            )

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Flash Toggle
                    IconButton(onClick = {
                        flashMode = when(flashMode) {
                            "OFF" -> "ON"
                            "ON" -> "AUTO"
                            else -> "OFF"
                        }
                    }) {
                        Icon(
                            imageVector = when(flashMode) {
                                "ON" -> Icons.Default.FlashOn
                                "AUTO" -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            },
                            contentDescription = "Flash",
                            tint = if (flashMode == "OFF") Color.White else DarkPrimary
                        )
                    }

                    // Grid Toggle
                    IconButton(onClick = {
                        activeGrid = when(activeGrid) {
                            "NONE" -> "THIRDS"
                            "THIRDS" -> "GOLDEN"
                            else -> "NONE"
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Grid",
                            tint = if (activeGrid == "NONE") Color.White else DarkPrimary
                        )
                    }

                    // Level Indicator Toggle
                    IconButton(onClick = { levelEnabled = !levelEnabled }) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Level",
                            tint = if (levelEnabled) DarkPrimary else Color.White
                        )
                    }
                }
            }

            // Bottom controls overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Simulation mode pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkSecondary.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.LightMode,
                            contentDescription = null,
                            tint = DarkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "HDR + NIGHT AUTO DETECT ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkSecondary)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Large capture button is embedded inside CameraPreview but can be custom styled here:
                    Text(
                        "Tap the center viewfinder anytime or use capture trigger below",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    } else {
        // Permission Request Fallback
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.NoPhotography,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Camera Access Denied",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "SnapJudge X requires Camera permission to capture and judge photographs directly in real-time.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondaryDark, textAlign = TextAlign.Center)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
                ) {
                    Text("Grant Permission", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    flashMode: String,
    activeGrid: String,
    levelEnabled: Boolean,
    onCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "CameraX Binding failed", e)
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Custom Overlay Drawing for grids & level
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Grid Overlays
            if (activeGrid == "THIRDS") {
                // Horizontal lines
                drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, h/3), Offset(w, h/3), 1.5f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, 2*h/3), Offset(w, 2*h/3), 1.5f)
                // Vertical lines
                drawLine(Color.White.copy(alpha = 0.4f), Offset(w/3, 0f), Offset(w/3, h), 1.5f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(2*w/3, 0f), Offset(2*w/3, h), 1.5f)
            } else if (activeGrid == "GOLDEN") {
                val ratio = 0.618f
                drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, h*(1f-ratio)), Offset(w, h*(1f-ratio)), 1.5f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, h*ratio), Offset(w, h*ratio), 1.5f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(w*(1f-ratio), 0f), Offset(w*(1f-ratio), h), 1.5f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(w*ratio, 0f), Offset(w*ratio, h), 1.5f)
            }

            // 2. Level Indicator (Horizon)
            if (levelEnabled) {
                val midX = w / 2f
                val midY = h / 2f
                
                // Draw circular target
                drawCircle(
                    color = DarkPrimary.copy(alpha = 0.5f),
                    radius = 40f,
                    center = Offset(midX, midY),
                    style = Stroke(width = 2f)
                )
                // Center point
                drawCircle(
                    color = DarkPrimary,
                    radius = 4f,
                    center = Offset(midX, midY)
                )
                // Horizon bar (slightly offset randomly or level to look like real indicator)
                drawLine(
                    color = SuccessGreen,
                    start = Offset(midX - 100f, midY),
                    end = Offset(midX + 100f, midY),
                    strokeWidth = 3f
                )
            }
        }

        // Floating Simulation Trigger Buttons to support virtual environments beautifully
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SimulationSubjectButton("Golden Sunset", onCaptured, context)
            SimulationSubjectButton("City Portrait", onCaptured, context)
            SimulationSubjectButton("Geometry", onCaptured, context)
        }

        // Main Physical Capture Shutter Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .border(4.dp, Color.White, CircleShape)
                .clickable {
                    // Capture high quality mockup of selected camera environment
                    val bitmap = generateRealisticCameraBitmap(context, "Golden Sunset")
                    onCaptured(bitmap)
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun SimulationSubjectButton(
    label: String,
    onCaptured: (Bitmap) -> Unit,
    context: Context
) {
    Button(
        onClick = {
            val bitmap = generateRealisticCameraBitmap(context, label)
            onCaptured(bitmap)
        },
        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, DarkPrimary.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(130.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Simulate", fontSize = 8.sp, color = DarkPrimary)
        }
    }
}

// Generate high quality photography subject mockups
fun generateRealisticCameraBitmap(context: Context, subject: String): Bitmap {
    val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()

    when(subject) {
        "City Portrait" -> {
            // Draw skin tones and city bokeh background
            paint.color = android.graphics.Color.parseColor("#121824")
            canvas.drawRect(0f, 0f, 1200f, 900f, paint)
            // Blur circles bokeh
            paint.color = android.graphics.Color.parseColor("#342D45")
            canvas.drawCircle(200f, 300f, 120f, paint)
            paint.color = android.graphics.Color.parseColor("#1B3C45")
            canvas.drawCircle(900f, 200f, 180f, paint)
            // Subject head
            paint.color = android.graphics.Color.parseColor("#E5C1A3")
            canvas.drawCircle(600f, 400f, 200f, paint)
        }
        "Geometry" -> {
            // Cool architectural triangles and shadows
            paint.color = android.graphics.Color.parseColor("#E0E0E0")
            canvas.drawRect(0f, 0f, 1200f, 900f, paint)
            paint.color = android.graphics.Color.parseColor("#424242")
            val path = android.graphics.Path()
            path.moveTo(1200f, 900f)
            path.lineTo(400f, 900f)
            path.lineTo(1200f, 200f)
            path.close()
            canvas.drawPath(path, paint)
        }
        else -> {
            // Golden Sunset - Gradient of orange sky, ocean dark ground
            paint.color = android.graphics.Color.parseColor("#FF5722")
            canvas.drawRect(0f, 0f, 1200f, 600f, paint)
            // Sun
            paint.color = android.graphics.Color.parseColor("#FFEB3B")
            canvas.drawCircle(600f, 500f, 140f, paint)
            // Sea
            paint.color = android.graphics.Color.parseColor("#263238")
            canvas.drawRect(0f, 600f, 1200f, 900f, paint)
        }
    }
    return bitmap
}

// --- SCREEN 4: LOADING / STATUS SCREEN ---
@Composable
fun AnalysisLoadingScreen(
    navController: NavController,
    viewModel: SnapJudgeViewModel
) {
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val status by viewModel.analysisStatus.collectAsState()

    // Smooth transition into result screen once analyzing is completed
    LaunchedEffect(isAnalyzing) {
        if (!isAnalyzing && status != "Idle" && !status.startsWith("Error")) {
            navController.navigate("result") {
                popUpTo("loading") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // Glowing spinning lines background
        val infiniteTransition = rememberInfiniteTransition(label = "loadingGlow")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spinnerRotation"
        )

        Box(
            modifier = Modifier
                .size(240.dp)
                .drawWithContent {
                    rotate(degrees = rotation) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(DarkPrimary, DarkSecondary, DarkTertiary, DarkPrimary)
                            ),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                    drawContent()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = null,
                    tint = DarkPrimary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "SnapJudge X",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AI ENGINE V3",
                    fontSize = 10.sp,
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Rotating active messages status text at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = DarkPrimary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

// --- SCREEN 5: DETAILED ANALYSIS RESULT ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    navController: NavController,
    viewModel: SnapJudgeViewModel
) {
    val result by viewModel.currentResult.collectAsState()
    val imagePath by viewModel.currentResultImagePath.collectAsState()
    val isFavorite by viewModel.currentResultIsFavorite.collectAsState()

    var activeTab by remember { mutableStateOf(0) }
    val tabs = listOf("VERDICT", "EXPERTS", "METRICS", "HEATMAP", "LIGHTROOM", "STORY", "MORE")

    val context = LocalContext.current

    if (result == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = DarkPrimary)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Expert Review", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleCurrentResultFavorite() }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) ErrorRed else Color.White
                        )
                    }
                    IconButton(onClick = {
                        viewModel.deleteCurrentResult()
                        Toast.makeText(context, "Deleted analysis record", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scrolling Tabs Row
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = DarkSurface,
                contentColor = DarkPrimary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = DarkPrimary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) },
                        selectedContentColor = DarkPrimary,
                        unselectedContentColor = TextSecondaryDark
                    )
                }
            }

            // Master Scrolling Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dominant Image Card Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        val file = if (imagePath != null) File(imagePath!!) else null
                        if (file != null && file.exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(file)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Analyzed Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(48.dp))
                            }
                        }

                        // Bottom Ambient Gradient Tag with Score
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = result!!.styles.firstOrNull()?.style ?: "Custom Frame",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Analyzed with SnapJudge X Engine",
                                    color = TextSecondaryDark,
                                    fontSize = 11.sp
                                )
                            }

                            // Score Circle
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(DarkPrimary)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${result!!.overallScore}",
                                        color = DarkBackground,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        "XP +250",
                                        color = DarkBackground.copy(alpha = 0.8f),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected Tab Rendering
                item {
                    AnimatedContent(
                        targetState = activeTab,
                        label = "TabContent"
                    ) { currentTab ->
                        when (currentTab) {
                            0 -> VerdictTabContent(result!!)
                            1 -> ExpertPanelTabContent(result!!)
                            2 -> MetricsTabContent(result!!)
                            3 -> HeatmapTabContent(result!!, imagePath)
                            4 -> LightroomTabContent(result!!, imagePath)
                            5 -> StoryTabContent(result!!)
                            else -> MoreTabContent(result!!)
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun VerdictTabContent(result: AnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DarkPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "CINEMATIC VERDICT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkPrimary,
                        letterSpacing = 1.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = result.verdict,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    lineHeight = 24.sp
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("FILM SIMULATION", color = TextSecondaryDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(result.filmEmulation, color = DarkSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("CINEMA LUT", color = TextSecondaryDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(result.lutSuggestion, color = DarkTertiary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ExpertPanelTabContent(result: AnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        result.experts.forEach { opinion ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            opinion.role.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkSecondary,
                                letterSpacing = 1.sp
                            )
                        )
                        Icon(
                            imageVector = when(opinion.role) {
                                "Professional Photographer" -> Icons.Default.CameraAlt
                                "Colorist" -> Icons.Default.Palette
                                "Cinematographer" -> Icons.Default.Movie
                                "Visual Designer" -> Icons.Default.DesignServices
                                "Commercial Judge" -> Icons.Default.WorkspacePremium
                                "Psychologist" -> Icons.Default.Psychology
                                else -> Icons.Default.HistoryEdu
                            },
                            contentDescription = null,
                            tint = DarkSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        opinion.review,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, lineHeight = 20.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricsTabContent(result: AnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "DETAILED SCORES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkTertiary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable column of metrics
            result.metrics.forEach { metric ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            metric.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "${metric.score}/100",
                            color = if (metric.score >= 85) SuccessGreen else if (metric.score >= 70) WarningOrange else ErrorRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { metric.score.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (metric.score >= 85) SuccessGreen else if (metric.score >= 70) WarningOrange else ErrorRed,
                        trackColor = Color.White.copy(alpha = 0.05f)
                    )
                    Text(
                        metric.explanation,
                        color = TextSecondaryDark,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    Text(
                        "Advice: ${metric.advice}",
                        color = DarkPrimary.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
fun HeatmapTabContent(result: AnalysisResult, imagePath: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "ATTENTION HEATMAP",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkSecondary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "See where viewers naturally focus their gaze first.",
                color = TextSecondaryDark,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Canvas with Image background drawing overlay hotspots
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            ) {
                val file = if (imagePath != null) File(imagePath) else null
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Heatmap reference",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Overlay Canvas drawing red/yellow glowing heatmap circles
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    result.heatmap.forEach { pt ->
                        val targetX = pt.x * w
                        val targetY = pt.y * h
                        val rad = pt.radius * 2f

                        // Outer glow
                        drawCircle(
                            color = Color(0xFFFF5722).copy(alpha = 0.35f * pt.intensity),
                            radius = rad,
                            center = Offset(targetX, targetY)
                        )
                        // Inner hot core
                        drawCircle(
                            color = Color(0xFFFFEB3B).copy(alpha = 0.7f * pt.intensity),
                            radius = rad * 0.4f,
                            center = Offset(targetX, targetY)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Explain each point
            result.heatmap.forEach { pt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (pt.intensity > 0.8f) Color.Red else Color.Yellow)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(pt.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(pt.explanation, color = TextSecondaryDark, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LightroomTabContent(result: AnalysisResult, imagePath: String?) {
    var isBefore by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "LIGHTROOM RECOMMENDATIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                    Text("Interactive edit preview concept.", color = TextSecondaryDark, fontSize = 11.sp)
                }

                // Before / After Concept Slider Toggle
                Button(
                    onClick = { isBefore = !isBefore },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isBefore) ErrorRed else DarkPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isBefore) "BEFORE" else "AFTER PREVIEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBackground
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // The Preview Box with optional desaturated / before & after tint applied
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val file = if (imagePath != null) File(imagePath) else null
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Edit preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        // Apply a colored overlay when "After preview" is active to simulate warmth LUT edit!
                        colorFilter = if (!isBefore) {
                            ColorFilter.colorMatrix(
                                ColorMatrix().apply {
                                    // Warm tint + small contrast boost
                                    setToScale(1.05f, 1.0f, 0.9f, 1f)
                                }
                            )
                        } else null
                    )
                }

                // Slider explanation overlay label
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isBefore) "Original Image" else "Edited Preview: Warm Cinematic Emulation Applied",
                        color = if (isBefore) Color.White else DarkPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display sliders recommendations list
            result.edits.forEach { setting ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(setting.key, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(setting.formattedValue, color = DarkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(setting.explanation, color = TextSecondaryDark, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun StoryTabContent(result: AnalysisResult) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "STORY & COPY GENERATOR",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkTertiary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text("EXHIBITION TITLE", color = TextSecondaryDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "“${result.stories.creativeTitle}”",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                IconButton(onClick = {
                    copyToClipboard(context, result.stories.creativeTitle)
                    Toast.makeText(context, "Copied Title!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = DarkTertiary, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))

            // Instagram Caption
            Text("SOCIAL MEDIA CAPTION (INSTAGRAM)", color = TextSecondaryDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(result.stories.instagramCaption, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            Button(
                onClick = {
                    copyToClipboard(context, result.stories.instagramCaption)
                    Toast.makeText(context, "Copied Instagram Caption!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkTertiary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text("Copy Instagram Copy", color = DarkTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))

            // Narrative Short Story
            Text("CREATIVE NARRATIVE & SHORT STORY", color = TextSecondaryDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                result.stories.shortStory,
                color = Color.White,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(vertical = 4.dp),
                lineHeight = 18.sp
            )
            Button(
                onClick = {
                    copyToClipboard(context, result.stories.shortStory)
                    Toast.makeText(context, "Copied Short Story!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkTertiary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text("Copy Short Story", color = DarkTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MoreTabContent(result: AnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Emotional DNA Profile
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "EMOTIONAL DNA PROFILE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkSecondary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                result.emotions.forEach { emotion ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emotion.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${emotion.value}%", color = DarkSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { emotion.value.toFloat() / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = DarkSecondary,
                            trackColor = Color.White.copy(alpha = 0.05f)
                        )
                        Text(emotion.reason, color = TextSecondaryDark, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        // Movie Matches
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CINEMATIC MOVIE MATCH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkPrimary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Films sharing similar photographic lighting and framing DNA.", color = TextSecondaryDark, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(16.dp))

                result.movies.forEach { movie ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = movie.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Confidence match: ${movie.confidence}%",
                                color = DarkPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = movie.reason,
                                color = TextSecondaryDark,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Copied Text", text)
    clipboard.setPrimaryClip(clip)
}

// --- SCREEN 6: PHOTO COMPARE (A VS B) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    navController: NavController,
    viewModel: SnapJudgeViewModel
) {
    val img1 by viewModel.compareImage1.collectAsState()
    val img2 by viewModel.compareImage2.collectAsState()
    val isComparing by viewModel.isComparing.collectAsState()
    val compResult by viewModel.comparisonResult.collectAsState()

    val context = LocalContext.current

    // Launch picker for Image A
    val picker1Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) viewModel.setCompareImage1(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launch picker for Image B
    val picker2Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) viewModel.setCompareImage2(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Side-By-Side Comparison", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clearComparison() }) {
                        Text("Reset", color = DarkPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Horizontal Side-By-Side selector cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Image A Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (compResult?.winnerIndex == 1) 3.dp else 1.dp,
                                color = if (compResult?.winnerIndex == 1) SuccessGreen else CardBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable { picker1Launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (img1 != null) {
                            Image(
                                bitmap = img1!!.asImageBitmap(),
                                contentDescription = "Image A",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (compResult?.winnerIndex == 1) {
                                WinnerRibbon()
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = DarkPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("SELECT PHOTO A", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Image B Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (compResult?.winnerIndex == 2) 3.dp else 1.dp,
                                color = if (compResult?.winnerIndex == 2) SuccessGreen else CardBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable { picker2Launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (img2 != null) {
                            Image(
                                bitmap = img2!!.asImageBitmap(),
                                contentDescription = "Image B",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (compResult?.winnerIndex == 2) {
                                WinnerRibbon()
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = DarkSecondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("SELECT PHOTO B", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Compare Button trigger
            item {
                Button(
                    onClick = { viewModel.comparePhotos() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                    enabled = img1 != null && img2 != null && !isComparing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isComparing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DarkBackground)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Compare, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RUN COMPARATIVE JUDGEMENT", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Display results
            if (compResult != null) {
                // Winner Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = DarkPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "COMPARISON VERDICT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = DarkPrimary,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(compResult!!.overallVerdict, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }

                // Grid stats breakdown
                item {
                    Text(
                        "GRANULAR CRITERIA MATCHES",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextSecondaryDark,
                            letterSpacing = 1.sp
                        )
                    )
                }

                // Composition
                item {
                    ComparisonCard(
                        title = "Composition",
                        val1 = compResult!!.compositionScore1,
                        val2 = compResult!!.compositionScore2,
                        comparison = compResult!!.compositionComparison
                    )
                }

                // Lighting
                item {
                    ComparisonCard(
                        title = "Lighting",
                        val1 = compResult!!.lightingScore1,
                        val2 = compResult!!.lightingScore2,
                        comparison = compResult!!.lightingComparison
                    )
                }

                // Emotion
                item {
                    ComparisonCard(
                        title = "Emotion",
                        val1 = compResult!!.emotionScore1,
                        val2 = compResult!!.emotionScore2,
                        comparison = compResult!!.emotionComparison
                    )
                }

                // Storytelling
                item {
                    ComparisonCard(
                        title = "Storytelling",
                        val1 = compResult!!.storytellingScore1,
                        val2 = compResult!!.storytellingScore2,
                        comparison = compResult!!.storytellingComparison
                    )
                }

                // Editing
                item {
                    ComparisonCard(
                        title = "Editing & Colorism",
                        val1 = compResult!!.editingScore1,
                        val2 = compResult!!.editingScore2,
                        comparison = compResult!!.editingComparison
                    )
                }

                // Commercial Value
                item {
                    ComparisonCard(
                        title = "Commercial value",
                        val1 = compResult!!.commercialScore1,
                        val2 = compResult!!.commercialScore2,
                        comparison = compResult!!.commercialComparison
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun WinnerRibbon() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SuccessGreen.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SuccessGreen)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("WINNER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ComparisonCard(
    title: String,
    val1: Int,
    val2: Int,
    comparison: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title.uppercase(), color = DarkSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo A Score
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Photo A", fontSize = 10.sp, color = TextSecondaryDark)
                    Text("$val1", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (val1 >= val2) SuccessGreen else Color.White)
                }
                Text("vs", color = TextSecondaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                // Photo B Score
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Photo B", fontSize = 10.sp, color = TextSecondaryDark)
                    Text("$val2", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (val2 >= val1) SuccessGreen else Color.White)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(comparison, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

// --- SCREEN 7: HISTORY & ACHIEVEMENTS LOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: SnapJudgeViewModel
) {
    val analyses by viewModel.allAnalyses.collectAsState()
    val favorites by viewModel.favoriteAnalyses.collectAsState()

    var showFavoritesOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val activeList = if (showFavoritesOnly) favorites else analyses
    val filteredList = activeList.filter {
        it.style.contains(searchQuery, ignoreCase = true) ||
                it.verdict.contains(searchQuery, ignoreCase = true) ||
                it.mode.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mentorship Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search logs by style or analysis...", color = TextSecondaryDark) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondaryDark) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Favorites Filter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showFavoritesOnly,
                    onClick = { showFavoritesOnly = false },
                    label = { Text("ALL ANALYSES (${analyses.size})", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DarkPrimary,
                        selectedLabelColor = DarkBackground
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                FilterChip(
                    selected = showFavoritesOnly,
                    onClick = { showFavoritesOnly = true },
                    label = { Text("FAVORITES (${favorites.size})", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DarkPrimary,
                        selectedLabelColor = DarkBackground
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // List of Logs
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No logs found match the filter.", color = TextSecondaryDark)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList) { log ->
                        HistoryLogCard(log = log) {
                            viewModel.loadResultFromEntity(log)
                            navController.navigate("result")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryLogCard(
    log: AnalysisEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                val file = File(log.imagePath)
                if (file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumb",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            // Description
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.style,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    // Score pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${log.overallScore}",
                            color = DarkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.verdict,
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Mode: ${log.mode}",
                        color = DarkSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(log.timestamp)),
                        color = TextSecondaryDark,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
