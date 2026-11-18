package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Farmer
import com.example.ui.components.DairyHeader
import com.example.ui.components.DashboardGrid
import com.example.ui.components.DashboardSummaryCards
import com.example.ui.components.PayableBreakdownDialog
import com.example.ui.components.ShiftCollectionDetailDialog
import com.example.ui.components.TodayMilkDetailDialog
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel

@Composable
fun DashboardScreen(
    viewModel: DairyViewModel,
    onNavigate: (DairyScreen) -> Unit,
    onOpenAiCameraScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val station by viewModel.stationConfig.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isHindi by viewModel.isHindi.collectAsState()
    val summary by viewModel.dashboardSummary.collectAsState()
    val collections by viewModel.allCollections.collectAsState()
    val farmers by viewModel.farmers.collectAsState()
    val pendingScans by viewModel.pendingScanRecords.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val notifications by viewModel.allNotifications.collectAsState()

    val todayEntries = collections.filter { it.date == viewModel.todayDate }
    val recentToday = todayEntries.take(3)

    // Modal dialog states for the summary cards
    var showTodayMilkModal by remember { mutableStateOf(false) }
    var showPayableModal by remember { mutableStateOf(false) }
    var showMorningModal by remember { mutableStateOf(false) }
    var showEveningModal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.DASHBOARD,
                isHindi = isHindi,
                onNavigateBack = {},
                onToggleLanguage = { viewModel.toggleLanguage() },
                onLogout = { viewModel.logout() },
                syncStatus = syncStatus,
                notifications = notifications,
                onSyncNow = { viewModel.syncNow() },
                onMarkNotificationRead = { viewModel.markNotificationRead(it) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Firebase Sync & Connection Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_firebase_status_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (syncStatus.isOnline) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (syncStatus.isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (syncStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val activeProjectId = remember {
                            runCatching { com.google.firebase.FirebaseApp.getInstance().options.projectId }
                                .getOrNull()
                                ?.takeIf { it.isNotBlank() }
                                ?: "milkcenter-erp"
                        }
                        Column {
                            Text(
                                text = when {
                                    syncStatus.isSyncing -> if (isHindi) "Firebase सिंक जारी ($activeProjectId)..." else "Syncing to Firebase ($activeProjectId)..."
                                    syncStatus.isOnline && syncStatus.pendingCount == 0 ->
                                        if (isHindi) "Firebase जुड़ा है • $activeProjectId" else "Firebase Connected • $activeProjectId"
                                    syncStatus.isOnline ->
                                        if (isHindi) "ऑनलाइन • ${syncStatus.pendingCount} रिकॉर्ड सिंक लंबित" else "Online • ${syncStatus.pendingCount} records pending sync"
                                    else ->
                                        if (isHindi) "ऑफ़लाइन कैश सक्रिय • ${syncStatus.pendingCount} कतार में" else "Offline Cache Active • ${syncStatus.pendingCount} queued"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (syncStatus.isOnline) Color(0xFF1B5E20) else Color(0xFFE65100)
                            )
                            Text(
                                text = if (isHindi)
                                    "${session.role.name} • खर्च: ₹${String.format("%.2f", summary.todayExpensesAmount)} • ${syncStatus.userEmail.ifBlank { "Com.milk.Centre" }}"
                                else
                                    "${session.role.name} • Expenses: ₹${String.format("%.2f", summary.todayExpensesAmount)} • ${syncStatus.userEmail.ifBlank { "Com.milk.Centre" }}",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                            if (!syncStatus.lastError.isNullOrBlank()) {
                                Text(
                                    text = syncStatus.lastError ?: "",
                                    fontSize = 10.sp,
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.syncNow() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("dashboard_sync_now_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isHindi) "सिंक" else "Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Quick AI Camera Scanner Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenAiCameraScanner)
                    .testTag("ai_camera_quick_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "AI Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isHindi) "AI स्मार्ट कैमरा स्कैनर" else "AI Smart Camera Scan",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (isHindi)
                                    "मशीन डिस्प्ले, दूध पर्ची व मासिक रिपोर्ट स्कैन करें"
                                else
                                    "Scan Machine Display, Milk Receipt or Monthly Report",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }

            // Admin Verification & Scan History Quick Card with Live Pending Badge
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onNavigate(DairyScreen.ADMIN_VERIFICATION) }
                    .testTag("dashboard_admin_verification_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = DairyRedPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Verification",
                                    tint = DairyRedPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isHindi) "Admin सत्यापन पैनल व स्कैन इतिहास" else "Admin Verification & Scan History",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isHindi)
                                    "लंबित स्कैन जांच, मशीन ID मैपिंग व ऑडिट लॉग"
                                else
                                    "Pending Scan Review, Machine ID Mapping & Audit Logs",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pendingScans.isNotEmpty()) {
                            Surface(
                                color = Color(0xFFE67E22),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${pendingScans.size} लंबित",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Interactive Summary Metrics Cards (All 5 Clickable & Functional)
            DashboardSummaryCards(
                summary = summary,
                isHindi = isHindi,
                onClickTotalMilk = { showTodayMilkModal = true },
                onClickPayableAmount = { showPayableModal = true },
                onClickRegisteredFarmers = { onNavigate(DairyScreen.FARMER_MANAGEMENT) },
                onClickMorningCollection = { showMorningModal = true },
                onClickEveningCollection = { showEveningModal = true }
            )

            // Section Header
            Text(
                text = if (isHindi) "मुख्य सेवा केंद्र (Dairy Modules)" else "Main Operations",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 6 Main Square Cards Grid
            DashboardGrid(
                isHindi = isHindi,
                onNavigate = onNavigate
            )

            // Recent Collection Entries snippet if any
            if (recentToday.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHindi) "आज की हालिया पर्चियां" else "Today's Recent Receipts",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isHindi) "सभी देखें" else "View All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DairyRedPrimary,
                            modifier = Modifier.clickable { onNavigate(DairyScreen.MILK_COLLECTION) }
                        )
                    }

                    recentToday.forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                            .background(Color(0xFFFFEBEE), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Receipt,
                                            contentDescription = null,
                                            tint = DairyRedPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "${entry.farmerName} (${entry.farmerId})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${entry.quantity}L | Fat: ${entry.fat}% | SNF: ${entry.snf}% | ${if (entry.shift == "MORNING") "सुबह" else "शाम"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "₹${String.format("%.2f", entry.totalAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal 1: Today's Complete Milk Collection Report
    if (showTodayMilkModal) {
        TodayMilkDetailDialog(
            station = station,
            todayDate = viewModel.todayDate,
            todayEntries = todayEntries,
            isHindi = isHindi,
            onDismiss = { showTodayMilkModal = false },
            onOpenFullReports = { onNavigate(DairyScreen.REPORTS_ANALYSIS) }
        )
    }

    // Modal 2: Total Payable Amount & Farmer-wise Breakdown
    if (showPayableModal) {
        PayableBreakdownDialog(
            station = station,
            todayDate = viewModel.todayDate,
            todayPayableAmount = summary.todayTotalAmount,
            farmers = farmers,
            todayEntries = todayEntries,
            onDismiss = { showPayableModal = false },
            onPayFarmer = { farmer ->
                onNavigate(DairyScreen.PAYMENT_MANAGEMENT)
            },
            onOpenPaymentManagement = { onNavigate(DairyScreen.PAYMENT_MANAGEMENT) }
        )
    }

    // Modal 3: Morning Collection Details
    if (showMorningModal) {
        ShiftCollectionDetailDialog(
            shift = "MORNING",
            station = station,
            todayDate = viewModel.todayDate,
            todayEntries = todayEntries,
            isHindi = isHindi,
            onDismiss = { showMorningModal = false },
            onAddNewEntry = {
                onNavigate(DairyScreen.MILK_COLLECTION)
            }
        )
    }

    // Modal 4: Evening Collection Details
    if (showEveningModal) {
        ShiftCollectionDetailDialog(
            shift = "EVENING",
            station = station,
            todayDate = viewModel.todayDate,
            todayEntries = todayEntries,
            isHindi = isHindi,
            onDismiss = { showEveningModal = false },
            onAddNewEntry = {
                onNavigate(DairyScreen.MILK_COLLECTION)
            }
        )
    }
}
