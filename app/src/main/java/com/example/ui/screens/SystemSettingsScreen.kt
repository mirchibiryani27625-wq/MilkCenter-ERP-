package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RateChartConfig
import com.example.data.model.StationConfig
import com.example.data.model.UserRole
import com.example.ui.components.DairyHeader
import com.example.ui.theme.BuffaloBlue
import com.example.ui.theme.CowGreen
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.RateCalculator
import kotlinx.coroutines.launch

@Composable
fun SystemSettingsScreen(
    viewModel: DairyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val station by viewModel.stationConfig.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isHindi by viewModel.isHindi.collectAsState()
    val rateCharts by viewModel.rateCharts.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val cloudUsers by viewModel.cloudUsers.collectAsState()
    val notifications by viewModel.allNotifications.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Rate Charts, 1: Station Info, 2: Security & Audit

    // Editable Station Info & Shift Timings
    var dairyName by remember(station) { mutableStateOf(station.dairyName) }
    var centerName by remember(station) { mutableStateOf(station.centerName) }
    var stationId by remember(station) { mutableStateOf(station.stationId) }
    var address by remember(station) { mutableStateOf(station.address) }
    var contactNumber by remember(station) { mutableStateOf(station.contactNumber) }
    var morningStartStr by remember(station) { mutableStateOf("${station.morningStartHour}") }
    var morningEndStr by remember(station) { mutableStateOf("${station.morningEndHour}") }
    var eveningStartStr by remember(station) { mutableStateOf("${station.eveningStartHour}") }
    var eveningEndStr by remember(station) { mutableStateOf("${station.eveningEndHour}") }
    var amountToleranceStr by remember(station) { mutableStateOf("${station.amountToleranceRs}") }

    // Rate Chart Editor States
    var selectedCategory by remember { mutableStateOf("COW") }
    val currentRateChart = remember(rateCharts, selectedCategory) {
        rateCharts.find { it.category == selectedCategory } ?: if (selectedCategory == "BUFFALO") {
            RateChartConfig("BUFFALO", 58.0, 6.5, 6.0, 9.0, 3.5)
        } else {
            RateChartConfig("COW", 38.0, 3.5, 5.5, 8.5, 3.0)
        }
    }

    var baseRateStr by remember(currentRateChart) { mutableStateOf("${currentRateChart.baseRate}") }
    var baseFatStr by remember(currentRateChart) { mutableStateOf("${currentRateChart.baseFat}") }
    var fatMultiplierStr by remember(currentRateChart) { mutableStateOf("${currentRateChart.fatMultiplier}") }
    var baseSnfStr by remember(currentRateChart) { mutableStateOf("${currentRateChart.baseSnf}") }
    var snfMultiplierStr by remember(currentRateChart) { mutableStateOf("${currentRateChart.snfMultiplier}") }
    var waterDeductionStr by remember(currentRateChart) { mutableStateOf("${currentRateChart.waterDeductionPerPercent}") }

    // Test Simulator States
    var testFat by remember { mutableStateOf("4.0") }
    var testSnf by remember { mutableStateOf("8.5") }
    var testWater by remember { mutableStateOf("0.0") }
    var testQty by remember { mutableStateOf("10.0") }
    var testCalculatedRate by remember { mutableStateOf(40.75) }
    var testCalculatedTotal by remember { mutableStateOf(407.50) }

    fun runTestCalculation() {
        val bRate = baseRateStr.toDoubleOrNull() ?: currentRateChart.baseRate
        val bFat = baseFatStr.toDoubleOrNull() ?: currentRateChart.baseFat
        val fMul = fatMultiplierStr.toDoubleOrNull() ?: currentRateChart.fatMultiplier
        val bSnf = baseSnfStr.toDoubleOrNull() ?: currentRateChart.baseSnf
        val sMul = snfMultiplierStr.toDoubleOrNull() ?: currentRateChart.snfMultiplier
        val wDed = waterDeductionStr.toDoubleOrNull() ?: currentRateChart.waterDeductionPerPercent

        val tempChart = RateChartConfig(selectedCategory, bRate, bFat, fMul, bSnf, sMul, wDed)
        val q = testQty.toDoubleOrNull() ?: 10.0
        val f = testFat.toDoubleOrNull() ?: 4.0
        val s = testSnf.toDoubleOrNull() ?: 8.5
        val w = testWater.toDoubleOrNull() ?: 0.0

        val res = RateCalculator.calculate(q, f, s, w, tempChart)
        testCalculatedRate = res.ratePerLiter
        testCalculatedTotal = res.totalAmount
    }

    // Role switcher & Admin PIN dialog
    var showChangePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.SYSTEM_SETTINGS,
                isHindi = isHindi,
                onNavigateBack = onBack,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isHindi) "सिस्टम सेटिंग्स (System Settings)" else "System Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(if (isHindi) "रेट चार्ट (Rates)" else "Rate Charts", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(if (isHindi) "केंद्र विवरण (Center)" else "Center Info", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(if (isHindi) "सुरक्षा व लॉग्स" else "Security & Logs", fontWeight = FontWeight.Bold) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // --- RATE CHART EDITOR ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "दूध दर चार्ट विन्यास (Pricing Formula)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DairyRedPrimary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedCategory == "COW",
                                    onClick = { selectedCategory = "COW" },
                                    label = { Text("गाय रेट चार्ट (COW)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CowGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = selectedCategory == "BUFFALO",
                                    onClick = { selectedCategory = "BUFFALO" },
                                    label = { Text("भैंस रेट चार्ट (BUFFALO)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BuffaloBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            Text(
                                text = "सूत्र: दर = बेस रेट + (Fat - बेस Fat) × Fat गुणक + (SNF - बेस SNF) × SNF गुणक - (पानी% × कटौती)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = baseRateStr,
                                    onValueChange = { baseRateStr = it; runTestCalculation() },
                                    label = { Text("बेस दर (₹ Base Rate)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = baseFatStr,
                                    onValueChange = { baseFatStr = it; runTestCalculation() },
                                    label = { Text("बेस FAT (%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = fatMultiplierStr,
                                    onValueChange = { fatMultiplierStr = it; runTestCalculation() },
                                    label = { Text("Fat गुणक (₹/FAT)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = baseSnfStr,
                                    onValueChange = { baseSnfStr = it; runTestCalculation() },
                                    label = { Text("बेस SNF (%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = snfMultiplierStr,
                                    onValueChange = { snfMultiplierStr = it; runTestCalculation() },
                                    label = { Text("SNF गुणक (₹/SNF)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = waterDeductionStr,
                                    onValueChange = { waterDeductionStr = it; runTestCalculation() },
                                    label = { Text("पानी कटौती (₹/%)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    val bRate = baseRateStr.toDoubleOrNull() ?: currentRateChart.baseRate
                                    val bFat = baseFatStr.toDoubleOrNull() ?: currentRateChart.baseFat
                                    val fMul = fatMultiplierStr.toDoubleOrNull() ?: currentRateChart.fatMultiplier
                                    val bSnf = baseSnfStr.toDoubleOrNull() ?: currentRateChart.baseSnf
                                    val sMul = snfMultiplierStr.toDoubleOrNull() ?: currentRateChart.snfMultiplier
                                    val wDed = waterDeductionStr.toDoubleOrNull() ?: currentRateChart.waterDeductionPerPercent

                                    val updated = RateChartConfig(
                                        category = selectedCategory,
                                        baseRate = bRate,
                                        baseFat = bFat,
                                        fatMultiplier = fMul,
                                        baseSnf = bSnf,
                                        snfMultiplier = sMul,
                                        waterDeductionPerPercent = wDed
                                    )
                                    viewModel.updateRateChart(updated)
                                    Toast.makeText(context, "$selectedCategory रेट चार्ट सफलतापूर्वक अपडेट हुआ!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_save_rate_chart"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("रेट चार्ट सहेजें व लागू करें", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Calculate, contentDescription = null, tint = DairyRedPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("लाइव रेट कैलकुलेटर (Test Simulator)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = testFat,
                                            onValueChange = { testFat = it; runTestCalculation() },
                                            label = { Text("Fat %") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = testSnf,
                                            onValueChange = { testSnf = it; runTestCalculation() },
                                            label = { Text("SNF %") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = testQty,
                                            onValueChange = { testQty = it; runTestCalculation() },
                                            label = { Text("Qty (L)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("दर: ₹${String.format("%.2f", testCalculatedRate)} / L", fontWeight = FontWeight.Bold, color = DairyRedPrimary)
                                            Text("कुल: ₹${String.format("%.2f", testCalculatedTotal)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // --- STATION INFO EDITOR ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "दूध केंद्र व स्टेशन विवरण",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DairyRedPrimary
                            )

                            OutlinedTextField(
                                value = dairyName,
                                onValueChange = { dairyName = it },
                                label = { Text("डेयरी का नाम (App / Dairy Name)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = centerName,
                                onValueChange = { centerName = it },
                                label = { Text("दूध संग्रहण केंद्र का नाम (Center Name)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = stationId,
                                onValueChange = { stationId = it },
                                label = { Text("स्टेशन आईडी (Station ID)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("पता / गांव (Address)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = contactNumber,
                                onValueChange = { contactNumber = it },
                                label = { Text("संपर्क मोबाइल नंबर (Contact Number)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "शिफ्ट समय (Morning / Evening Timing Rules) व स्कैनर टॉलरेंस",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DairyRedPrimary
                            )
                            Text(
                                text = "डिफ़ॉल्ट: सुबह 04:00–11:59 (4–11) | शाम 12:00–23:59 (12–23)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = morningStartStr,
                                    onValueChange = { morningStartStr = it },
                                    label = { Text("सुबह प्रारंभ (Hr 0-23)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("settings_morning_start_hr")
                                )
                                OutlinedTextField(
                                    value = morningEndStr,
                                    onValueChange = { morningEndStr = it },
                                    label = { Text("सुबह समाप्ति (Hr)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("settings_morning_end_hr")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = eveningStartStr,
                                    onValueChange = { eveningStartStr = it },
                                    label = { Text("शाम प्रारंभ (Hr 0-23)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("settings_evening_start_hr")
                                )
                                OutlinedTextField(
                                    value = eveningEndStr,
                                    onValueChange = { eveningEndStr = it },
                                    label = { Text("शाम समाप्ति (Hr)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("settings_evening_end_hr")
                                )
                            }

                            OutlinedTextField(
                                value = amountToleranceStr,
                                onValueChange = { amountToleranceStr = it },
                                label = { Text("राशि राउंडिंग छूट (Amount Tolerance ₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    viewModel.updateStationConfig(
                                        station.copy(
                                            dairyName = dairyName.trim().ifBlank { station.dairyName },
                                            centerName = centerName.trim().ifBlank { station.centerName },
                                            stationId = stationId.trim().uppercase().ifBlank { station.stationId },
                                            address = address.trim(),
                                            contactNumber = contactNumber.trim(),
                                            morningStartHour = (morningStartStr.toIntOrNull() ?: station.morningStartHour).coerceIn(0, 23),
                                            morningEndHour = (morningEndStr.toIntOrNull() ?: station.morningEndHour).coerceIn(0, 23),
                                            eveningStartHour = (eveningStartStr.toIntOrNull() ?: station.eveningStartHour).coerceIn(0, 23),
                                            eveningEndHour = (eveningEndStr.toIntOrNull() ?: station.eveningEndHour).coerceIn(0, 23),
                                            amountToleranceRs = (amountToleranceStr.toDoubleOrNull() ?: station.amountToleranceRs).coerceIn(0.05, 5.0)
                                        )
                                    )
                                    Toast.makeText(context, "केंद्र व शिफ्ट सेटिंग्स Firebase और लोकल में सहेजी गईं!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_save_station_info"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                            ) {
                                Text("केंद्र विवरण व शिफ्ट समय सहेजें", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                2 -> {
                    // --- SECURITY, FIREBASE ACCOUNT, CLOUD ROLES & AUDIT LOGS ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Firebase खाता, भूमिका व क्लाउड सुरक्षा",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DairyRedPrimary
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "नाम: ${session.userName} • भूमिका: ${session.role.name}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val activeProjectId = remember {
                                        runCatching { com.google.firebase.FirebaseApp.getInstance().options.projectId }
                                            .getOrNull()
                                            ?.takeIf { it.isNotBlank() }
                                            ?: "milkcenter-erp"
                                    }
                                    Text(
                                        text = "Firebase प्रोजेक्ट: $activeProjectId • पैकेज: Com.milk.Centre",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (session.email.isNotBlank()) {
                                        Text(
                                            text = "ईमेल: ${session.email}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "सिंक स्थिति: ${if (syncStatus.isOnline) "Online" else "Offline"} • लंबित रिकॉर्ड: ${syncStatus.pendingCount}",
                                        fontSize = 11.sp,
                                        color = if (syncStatus.isOnline) Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                    if (!syncStatus.lastError.isNullOrBlank()) {
                                        Text(
                                            text = "अंतिम त्रुटि: ${syncStatus.lastError}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.syncNow() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Text("क्लाउड सिंक अभी करें", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val email = session.email
                                        if (email.contains("@")) {
                                            scope.launch {
                                                val res = viewModel.sendPasswordResetEmail(email)
                                                Toast.makeText(
                                                    context,
                                                    res.fold(
                                                        onSuccess = { "पासवर्ड रीसेट लिंक $email पर भेजा गया" },
                                                        onFailure = { "ईमेल भेजने में त्रुटि: ${it.localizedMessage}" }
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("पासवर्ड रीसेट ईमेल", fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = { showChangePinDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("व्यवस्थापक (Admin) PIN बदलें")
                            }

                            // Cloud Users Role Management (Users collection)
                            if (cloudUsers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "क्लाउड उपयोगकर्ता व भूमिका प्रबंधन (${cloudUsers.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = DairyRedPrimary
                                )
                                cloudUsers.forEach { profile ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(profile.displayName.ifBlank { profile.email }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("${profile.email} • ${profile.role}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Text(
                                                    text = if (profile.active) "सक्रिय" else "निष्क्रिय",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (profile.active) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            if (session.role == UserRole.ADMIN) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf("ADMIN", "OPERATOR", "STAFF").forEach { roleOpt ->
                                                        FilterChip(
                                                            selected = profile.role == roleOpt,
                                                            onClick = {
                                                                viewModel.updateCloudUserRole(profile.userId, roleOpt, profile.active) { _, msg ->
                                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            label = { Text(roleOpt, fontSize = 10.sp) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Audit Logs Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = DairyRedPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ऑडिट लॉग्स (Audit Logs)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text("${auditLogs.size} लॉग्स", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            auditLogs.take(10).forEach { log ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = log.action, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DairyRedPrimary)
                                            Text(text = log.operatorName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = log.details, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Change Admin PIN Dialog (Persisted to StationConfig)
    if (showChangePinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("नया पिन सेट करें", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = { Text("नया 4-अंकीय पिन दर्ज करें") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPin.trim()
                        if (trimmed.length >= 4) {
                            viewModel.updateStationConfig(station.copy(adminPin = trimmed))
                            showChangePinDialog = false
                            Toast.makeText(context, "नया पिन सफलतापूर्वक सहेजा गया!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "पिन कम से कम 4 अंकों का होना चाहिए", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                ) {
                    Text("पिन सहेजें")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("रद्द करें")
                }
            }
        )
    }
}
