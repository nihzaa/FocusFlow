package edu.unikom.focusflow.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import edu.unikom.focusflow.ui.theme.*

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundElements: List<BackgroundElement> = emptyList()
)

data class BackgroundElement(
    val icon: ImageVector,
    val size: Int,
    val offsetX: Float,
    val offsetY: Float,
    val rotation: Float = 0f,
    val alpha: Float = 0.1f
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val firebaseAuth = FirebaseAuth.getInstance()
    val currentUser = firebaseAuth.currentUser

    // If user is already logged in, navigate to home
    if (currentUser != null) {
        LaunchedEffect(true) {
            navController.navigate("home") {
                popUpTo("onboarding") { inclusive = true }
            }
        }
        return
    }

    val pages = listOf(
        OnboardingPage(
            title = "Track Your Time",
            description = "Monitor your daily productivity with smart time tracking and insightful analytics",
            icon = Icons.Filled.Timer,
            primaryColor = Color(0xFF4A6741),
            secondaryColor = Color(0xFF8BC34A),
            backgroundElements = listOf(
                BackgroundElement(Icons.Outlined.Schedule, 40, -50f, -100f, 15f),
                BackgroundElement(Icons.Outlined.AccessTime, 30, 100f, -150f, -10f),
                BackgroundElement(Icons.Outlined.Timelapse, 50, -80f, 100f, 25f),
                BackgroundElement(Icons.Outlined.AlarmOn, 35, 120f, 80f, -20f)
            )
        ),
        OnboardingPage(
            title = "Organize Tasks",
            description = "Create, prioritize, and manage your tasks with intuitive tools and smart reminders",
            icon = Icons.Filled.Assignment,
            primaryColor = Color(0xFF2196F3),
            secondaryColor = Color(0xFF64B5F6),
            backgroundElements = listOf(
                BackgroundElement(Icons.Outlined.TaskAlt, 45, -60f, -120f, 20f),
                BackgroundElement(Icons.Outlined.Checklist, 35, 90f, -80f, -15f),
                BackgroundElement(Icons.Outlined.AssignmentTurnedIn, 40, -100f, 120f, 10f),
                BackgroundElement(Icons.Outlined.ListAlt, 30, 110f, 100f, -25f)
            )
        ),
        OnboardingPage(
            title = "Boost Focus",
            description = "Use Pomodoro technique and focus sessions to maximize your productivity",
            icon = Icons.Filled.Psychology,
            primaryColor = Color(0xFF9C27B0),
            secondaryColor = Color(0xFFBA68C8),
            backgroundElements = listOf(
                BackgroundElement(Icons.Outlined.CenterFocusStrong, 50, -70f, -90f, 15f),
                BackgroundElement(Icons.Outlined.Lightbulb, 35, 80f, -110f, -20f),
                BackgroundElement(Icons.Outlined.TipsAndUpdates, 40, -90f, 110f, 25f),
                BackgroundElement(Icons.Outlined.AutoAwesome, 30, 100f, 90f, -10f)
            )
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        navController.navigate("login") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                ) {
                    Text(
                        "Skip",
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Main Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                AnimatedOnboardingPage(
                    page = pages[page],
                    isCurrentPage = pagerState.currentPage == page
                )
            }

            // Bottom Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, _ ->
                        PageIndicator(
                            isActive = index == pagerState.currentPage,
                            color = pages[pagerState.currentPage].primaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                AnimatedContent(
                    targetState = pagerState.currentPage == pages.size - 1,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    }
                ) { isLastPage ->
                    if (isLastPage) {
                        // Get Started Button for last page
                        Button(
                            onClick = {
                                navController.navigate("login") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = pages[pagerState.currentPage].primaryColor
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 8.dp
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Get Started",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        // Next Button for other pages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back button (only show after first page)
                            if (pagerState.currentPage > 0) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    },
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        1.dp,
                                        pages[pagerState.currentPage].primaryColor.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = pages[pagerState.currentPage].primaryColor
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(56.dp))
                            }

                            // Next button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = pages[pagerState.currentPage].primaryColor
                                ),
                                shape = RoundedCornerShape(16.dp),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Next",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedOnboardingPage(
    page: OnboardingPage,
    isCurrentPage: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_transition")

    // Floating animation for main icon
    val floatAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_animation"
    )

    // Scale animation for entrance
    val scale by animateFloatAsState(
        targetValue = if (isCurrentPage) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale_animation"
    )

    // Rotation animation for background elements
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing)
        ),
        label = "rotation_animation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            page.secondaryColor.copy(alpha = 0.1f),
                            Color.White
                        ),
                        radius = 600f
                    )
                )
        )

        // Animated background elements
        page.backgroundElements.forEach { element ->
            Icon(
                element.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(element.size.dp)
                    .offset(x = element.offsetX.dp, y = element.offsetY.dp)
                    .rotate(rotation * 0.1f + element.rotation)
                    .alpha(element.alpha),
                tint = page.primaryColor.copy(alpha = 0.2f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Icon Container
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(y = floatAnimation.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                page.primaryColor.copy(alpha = 0.15f),
                                page.secondaryColor.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    page.primaryColor.copy(alpha = 0.2f),
                                    page.secondaryColor.copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = page.primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Title with animation
            AnimatedVisibility(
                visible = isCurrentPage,
                enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
                exit = fadeOut()
            ) {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description with animation
            AnimatedVisibility(
                visible = isCurrentPage,
                enter = fadeIn(animationSpec = tween(delayMillis = 100)) +
                        slideInVertically(initialOffsetY = { 50 }),
                exit = fadeOut()
            ) {
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
fun PageIndicator(
    isActive: Boolean,
    color: Color
) {
    val width by animateDpAsState(
        targetValue = if (isActive) 32.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "indicator_width"
    )

    Box(
        modifier = Modifier
            .height(8.dp)
            .width(width)
            .clip(CircleShape)
            .background(
                if (isActive) color else Color.Gray.copy(alpha = 0.3f)
            )
    )
}