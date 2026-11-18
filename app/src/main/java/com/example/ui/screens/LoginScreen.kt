package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.StationConfig
import com.example.data.model.UserRole
import com.example.ui.theme.DairyRedPrimary
import com.example.util.FirestoreErrorHandler
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun LoginScreen(
    station: StationConfig,
    isHindi: Boolean,
    onGoogleSignInSuccess: (UserRole, FirebaseUser) -> Unit = { _, _ -> },
    onSendPasswordReset: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    enableAutoSignInCheck: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedRole by remember { mutableStateOf(UserRole.OPERATOR) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetStatusMessage by remember { mutableStateOf<String?>(null) }

    // Silent Credential Manager check on launch (uses GetGoogleIdOption with authorized accounts only)
    LaunchedEffect(enableAutoSignInCheck) {
        if (!enableAutoSignInCheck) return@LaunchedEffect
        val activity = context.findActivity() ?: return@LaunchedEffect
        val firebaseInitialized = runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        }.getOrDefault(false)
        if (!firebaseInitialized) return@LaunchedEffect

        val existingUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (existingUser != null) {
            onGoogleSignInSuccess(selectedRole, existingUser)
            return@LaunchedEffect
        }

        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        if (clientId.isNullOrBlank()) return@LaunchedEffect

        try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authCred = GoogleAuthProvider.getCredential(idToken, null)
                val authRes = FirebaseAuth.getInstance().signInWithCredential(authCred).await()
                authRes.user?.let { user ->
                    onGoogleSignInSuccess(selectedRole, user)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            // Expected on clean devices / first launch before interactive Google Sign-In
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Silent Google Sign-In cancelled or dismissed: ${e.message}", e)
        } catch (e: Exception) {
            Log.d("Auth", "Silent Google Sign-In skipped: ${e.message}")
        }
    }

    fun launchInteractiveGoogleSignIn() {
        val activity = context.findActivity()
        if (activity == null) {
            errorMessage = if (isHindi) "प्रमाणीकरण प्रारंभ नहीं हो सका (Activity अनुपलब्ध)" else "Unable to start Google Sign-In cleanly."
            return
        }
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        if (clientId.isNullOrBlank()) {
            errorMessage = if (isHindi)
                "Google Sign-In कॉन्फ़िगरेशन उपलब्ध नहीं है (default_web_client_id अनुपस्थित)।"
            else
                "Google Sign-In configuration missing (default_web_client_id not found)."
            return
        }

        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val credentialManager = CredentialManager.create(context)
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val result = credentialManager.getCredential(activity, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val firebaseCred = GoogleAuthProvider.getCredential(token, null)
                    val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                    val firebaseUser = authResult.user
                    if (firebaseUser != null) {
                        isLoading = false
                        onGoogleSignInSuccess(selectedRole, firebaseUser)
                    } else {
                        isLoading = false
                        errorMessage = if (isHindi) "उपयोगकर्ता सत्र प्राप्त नहीं हो सका।" else "Failed to obtain user session."
                    }
                } else {
                    isLoading = false
                    errorMessage = if (isHindi) "अमान्य क्रेडेंशियल प्रकार।" else "Unexpected credential type."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
                isLoading = false
            } catch (e: NoCredentialException) {
                Log.w("Auth", "No Google credentials found: ${e.message}", e)
                isLoading = false
                errorMessage = if (isHindi)
                    "डिवाइस पर कोई Google खाता नहीं मिला। कृपया अपने फोन की Settings में Google खाता जोड़ें और पुनः प्रयास करें।"
                else
                    "No Google account found on this device. Please add a Google account in device Settings and try again."
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                isLoading = false
                errorMessage = FirestoreErrorHandler.toUserFriendlyMessage(e)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Dairy Branding Logo
            Surface(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape),
                color = DairyRedPrimary,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_namaste_india_logo),
                        contentDescription = "नमस्ते इंडिया लोगो",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "MilkCenter ERP • ${station.dairyName}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DairyRedPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = station.centerName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "स्टेशन आईडी: ${station.stationId}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = DairyRedPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHindi) "Firebase सुरक्षित Google लॉगिन" else "Secure Firebase Google Sign-In",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isHindi)
                            "सभी किसान, दूध संग्रहण, पेमेंट, खर्च और रिपोर्ट डेटा Firebase क्लाउड में सुरक्षित सिंक होगा।"
                        else
                            "Sign in with Google to securely sync Farmers, Milk Collections, Payments, Expenses & Reports to Firebase Cloud.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Role Selector
                    Text(
                        text = if (isHindi) "केंद्र भूमिका चुनें (User Role):" else "Select Center Role:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = {
                                    Text(
                                        text = when (role) {
                                            UserRole.ADMIN -> if (isHindi) "प्रशासक" else "Admin"
                                            UserRole.OPERATOR -> if (isHindi) "ऑपरेटर" else "Operator"
                                            UserRole.STAFF -> if (isHindi) "स्टाफ" else "Staff"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedRole == role) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DairyRedPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("role_chip_${role.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Interactive Google Sign-In Button
                    Button(
                        onClick = { launchInteractiveGoogleSignIn() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isHindi) "प्रमाणीकरण हो रहा है..." else "Signing in...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isHindi) "Google से लॉगिन करें (Sign in with Google)" else "Sign in with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Error box
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password Reset Email Helper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showForgotPasswordDialog = true },
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockReset,
                                contentDescription = null,
                                tint = DairyRedPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "पासवर्ड रीसेट ईमेल?" else "Password Reset Email?",
                                fontSize = 12.sp,
                                color = DairyRedPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Firebase Firestore • Firebase Auth • ऑफलाइन-फर्स्ट सिंक",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    // Password Reset Email Dialog
    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf("") }
        var isSendingReset by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSendingReset) showForgotPasswordDialog = false },
            title = {
                Text(
                    text = if (isHindi) "पासवर्ड रीसेट ईमेल भेजें" else "Send Password Reset Email",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isHindi)
                            "अपना पंजीकृत ईमेल पता दर्ज करें। Firebase द्वारा पासवर्ड रीसेट लिंक ईमेल किया जाएगा।"
                        else
                            "Enter your registered email address to receive a Firebase password reset link."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_email_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = resetEmail.contains("@") && !isSendingReset,
                    onClick = {
                        isSendingReset = true
                        scope.launch {
                            val res = onSendPasswordReset(resetEmail.trim())
                            isSendingReset = false
                            showForgotPasswordDialog = false
                            resetStatusMessage = res.fold(
                                onSuccess = {
                                    if (isHindi) "पासवर्ड रीसेट लिंक ${resetEmail.trim()} पर भेजा गया है।"
                                    else "Password reset email sent to ${resetEmail.trim()}."
                                },
                                onFailure = { err -> FirestoreErrorHandler.toUserFriendlyMessage(err) }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                ) {
                    Text(if (isHindi) "ईमेल भेजें" else "Send Email")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }, enabled = !isSendingReset) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    if (resetStatusMessage != null) {
        AlertDialog(
            onDismissRequest = { resetStatusMessage = null },
            title = { Text(if (isHindi) "सूचना" else "Notice") },
            text = { Text(resetStatusMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { resetStatusMessage = null }) {
                    Text(if (isHindi) "ठीक है" else "OK")
                }
            }
        )
    }
}
