package edu.unikom.focusflow.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import edu.unikom.focusflow.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val coroutineScope = rememberCoroutineScope()

    // Google Sign-In setup
    val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()


    val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)
    val signInIntent = googleSignInClient.signInIntent
    val credentialManager = CredentialManager.create(context)


    // States
    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Handle Google Sign-In result
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                isLoading = true
                val task: Task<GoogleSignInAccount> =
                    GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(
                    task = task,
                    auth = auth,
                    navController = navController,
                    googleSignInClient = googleSignInClient, // Pass client untuk sign out
                    onError = { error ->
                        isLoading = false
                        errorMessage = error
                        showError = true
                    },
                    onSuccess = {
                        isLoading = false
                    }
                )
            } else {
                Log.e("LoginScreen", "Google Sign-In cancelled or failed")
            }
        }
    )


    // Animations
    val infiniteTransition = rememberInfiniteTransition()

    val float1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val float2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Background decorations
        BackgroundDecoration(rotation = rotation, float1 = float1, float2 = float2)

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Section with animation
            AnimatedLogo(float1 = float1)

            Spacer(modifier = Modifier.height(24.dp))

            // App Name and Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FocusFlow",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A6741)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Boost Your Productivity",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Welcome Text
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFFFFB74D)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sign in to sync your tasks and track progress across all devices",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Sign In Options
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Google Sign In Button
                GoogleSignInButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true

                            // IMPORTANT: Sign out dulu untuk force account chooser
                            googleSignInClient.signOut().addOnCompleteListener {
                                // Launch sign in dengan account chooser
                                val signInIntent = googleSignInClient.signInIntent
                                googleSignInLauncher.launch(signInIntent)
                            }
                        }
                    },
                    isLoading = isLoading
                )
                // Or Divider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.Gray.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "   or   ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.Gray.copy(alpha = 0.3f)
                    )
                }

                // Continue as Guest Button (Disabled - show info)
                OutlinedButton(
                    onClick = {
                        // Show info that guest mode is not available
                        errorMessage = "Guest mode is not available. Please sign in with Google to access all features."
                        showError = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Gray.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Guest Mode (Unavailable)",
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Terms and Privacy
            Text(
                text = "By continuing, you agree to our Terms of Service\nand Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }

        // Error Snackbar
        AnimatedVisibility(
            visible = showError,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE57373)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showError = false }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Loading Overlay
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4A6741)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Signing in...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundDecoration(rotation: Float, float1: Float, float2: Float) {
    // Top left decoration
    Box(
        modifier = Modifier
            .offset(x = (-50).dp, y = (-50).dp + float1.dp)
            .size(150.dp)
            .rotate(rotation * 0.1f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4A6741).copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
    )

    // Top right decoration
    Box(
        modifier = Modifier
            .offset(x = 300.dp, y = 100.dp + float2.dp)
            .size(100.dp)
            .rotate(-rotation * 0.15f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8BC34A).copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )
    )

    // Bottom decoration
    Box(
        modifier = Modifier
            .offset(x = 50.dp, y = 600.dp + float1.dp)
            .size(200.dp)
            .rotate(rotation * 0.05f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2196F3).copy(alpha = 0.06f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun AnimatedLogo(float1: Float) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .offset(y = float1.dp * 0.3f)
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                spotColor = Color(0xFF4A6741).copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4A6741),
                        Color(0xFF6B8E5A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_focusflow_trans),
            contentDescription = "FocusFlow Logo",
            modifier = Modifier.size(100.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = if (isLoading) 0.dp else 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF4285F4).copy(alpha = 0.3f)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        enabled = !isLoading,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = "Google",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Continue with Google",
                color = Color.Black.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Enhanced handleSignInResult with better error handling
fun handleSignInResult(
    task: Task<GoogleSignInAccount>,
    auth: FirebaseAuth,
    navController: NavController,
    googleSignInClient: GoogleSignInClient, // Tambah parameter ini
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    try {
        val account = task.getResult(ApiException::class.java)
        account?.let {
            val idToken = it.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful) {
                        saveUserToFirestore(auth.currentUser!!)
                        onSuccess()
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    } else {
                        // Sign out jika gagal
                        googleSignInClient.signOut()
                        onError("Sign in failed. Please try again.")
                        Log.e("LoginScreen", "Login failed: ${signInTask.exception?.message}")
                    }
                }
            } else {
                googleSignInClient.signOut()
                onError("Authentication failed. Please try again.")
                Log.e("LoginScreen", "idToken is null")
            }
        } ?: run {
            onError("Sign in cancelled")
            Log.e("LoginScreen", "Google Sign-In Account is null")
        }
    } catch (e: ApiException) {
        googleSignInClient.signOut() // Sign out on error
        when (e.statusCode) {
            12501 -> onError("Sign in cancelled")
            12500 -> onError("Sign in failed. Please check your internet connection.")
            else -> onError("An error occurred. Please try again.")
        }
        Log.e("LoginScreen", "Google sign-in failed: ${e.message}")
    }
}

// Enhanced saveUserToFirestore with better data structure
fun saveUserToFirestore(user: FirebaseUser) {
    val db = FirebaseFirestore.getInstance()

    val userData = hashMapOf(
        "name" to (user.displayName ?: "User"),
        "email" to user.email,
        "photoUrl" to user.photoUrl.toString(),
        "uid" to user.uid,
        "createdAt" to com.google.firebase.Timestamp.now(),
        "lastLogin" to com.google.firebase.Timestamp.now(),
        "provider" to "google"
    )

    db.collection("users")
        .document(user.uid)
        .set(userData, com.google.firebase.firestore.SetOptions.merge())
        .addOnSuccessListener {
            Log.d("Firestore", "User successfully saved/updated!")
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error saving user", e)
        }
}