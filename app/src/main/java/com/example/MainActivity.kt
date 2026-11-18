package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.SplashScreenOverlay
import com.example.ui.screens.AdminVerificationScreen
import com.example.ui.screens.AiCameraScannerDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FarmerAccountScreen
import com.example.ui.screens.FarmerManagementScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MilkCollectionScreen
import com.example.ui.screens.PaymentManagementScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SystemSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 960.dp)
                        ) {
                            DairyAppNavigation()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DairyAppNavigation(viewModel: DairyViewModel = viewModel()) {
    val firebaseUser by viewModel.firebaseUser.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val station by viewModel.stationConfig.collectAsState()
    val isHindi by viewModel.isHindi.collectAsState()
    val uiError by viewModel.uiError.collectAsState()

    var showSplash by remember { mutableStateOf(true) }
    var showQuickScanner by remember { mutableStateOf(false) }

    LaunchedEffect(firebaseUser) {
        if (firebaseUser == null) {
            showQuickScanner = false
        }
    }

    if (firebaseUser == null) {
        LoginScreen(
            station = station,
            isHindi = isHindi,
            onGoogleSignInSuccess = { role, user ->
                viewModel.onGoogleSignInSuccess(role, user)
            },
            onSendPasswordReset = { email ->
                viewModel.sendPasswordResetEmail(email)
            }
        )
    } else {
        val activeUid = firebaseUser!!.uid
        key(activeUid) {
            when (currentScreen) {
                DairyScreen.LOGIN,
                DairyScreen.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { screen -> viewModel.navigateTo(screen) },
                        onOpenAiCameraScanner = { showQuickScanner = true }
                    )
                }

                DairyScreen.FARMER_MANAGEMENT -> {
                    FarmerManagementScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(DairyScreen.DASHBOARD) }
                    )
                }

                DairyScreen.MILK_COLLECTION -> {
                    MilkCollectionScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(DairyScreen.DASHBOARD) }
                    )
                }

                DairyScreen.REPORTS_ANALYSIS -> {
                    ReportsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(DairyScreen.DASHBOARD) }
                    )
                }

                DairyScreen.FARMER_ACCOUNT -> {
                    FarmerAccountScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(DairyScreen.DASHBOARD) }
                    )
                }

                DairyScreen.PAYMENT_MANAGEMENT -> {
                    PaymentManagementScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(DairyScreen.DASHBOARD) }
                    )
                }

                DairyScreen.SYSTEM_SETTINGS -> {
                    SystemSettingsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(DairyScreen.DASHBOARD) }
                    )
                }

                DairyScreen.ADMIN_VERIFICATION -> {
                    AdminVerificationScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(DairyScreen.DASHBOARD) }
                    )
                }
            }

            // Direct AI Camera Scanner launcher from Dashboard quick banner
            if (showQuickScanner) {
                val farmers by viewModel.farmers.collectAsState()
                AiCameraScannerDialog(
                    viewModel = viewModel,
                    farmers = farmers,
                    onDismiss = { showQuickScanner = false },
                    onSaveSuccess = {
                        showQuickScanner = false
                        viewModel.navigateTo(DairyScreen.MILK_COLLECTION)
                    },
                    onDataPopulated = { entry ->
                        viewModel.setPendingScannedCollection(entry)
                        showQuickScanner = false
                        viewModel.navigateTo(DairyScreen.MILK_COLLECTION)
                    }
                )
            }
        }
    }

    if (uiError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearUiError() },
            title = { Text(if (isHindi) "सूचना / त्रुटि" else "Operation Error") },
            text = { Text(uiError ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearUiError() }) {
                    Text(if (isHindi) "ठीक है" else "OK")
                }
            }
        )
    }

    SplashScreenOverlay(
        station = station,
        isVisible = showSplash,
        onSplashFinished = { showSplash = false }
    )
}
