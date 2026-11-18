package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.DairyHeader
import com.example.ui.theme.BuffaloBlue
import com.example.ui.theme.CowGreen
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.WhatsAppHelper
import java.io.File
import java.io.FileOutputStream

private enum class ReportSubMode {
    DAILY, MORNING_SHIFT, EVENING_SHIFT, FARMER_WISE, PAYMENTS, EXPENSES
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    viewModel: DairyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val station by viewModel.stationConfig.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isHindi by viewModel.isHindi.collectAsState()
    val collections by viewModel.allCollections.collectAsState()
    val farmers by viewModel.farmers.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val notifications by viewModel.allNotifications.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Daily & Operational, 1: Monthly
    var selectedDate by remember { mutableStateOf(viewModel.todayDate) }
    var filterShift by remember { mutableStateOf("ALL") } // "ALL", "MORNING", "EVENING"
    var subMode by remember { mutableStateOf(ReportSubMode.DAILY) }

    // Daily & Shift calculations
    val dailyAll = remember(collections, selectedDate) {
        collections.filter { it.date == selectedDate }
    }
    val effectiveShift = when (subMode) {
        ReportSubMode.MORNING_SHIFT -> "MORNING"
        ReportSubMode.EVENING_SHIFT -> "EVENING"
        else -> filterShift
    }
    val dailyList = remember(dailyAll, effectiveShift) {
        dailyAll.filter { if (effectiveShift == "ALL") true else it.shift == effectiveShift }
    }
    val dailyTotalLiters = dailyList.sumOf { it.quantity }
    val dailyTotalAmt = dailyList.sumOf { it.totalAmount }
    val dailyMorningLiters = dailyAll.filter { it.shift == "MORNING" }.sumOf { it.quantity }
    val dailyEveningLiters = dailyAll.filter { it.shift == "EVENING" }.sumOf { it.quantity }
    val dailyMorningAmt = dailyAll.filter { it.shift == "MORNING" }.sumOf { it.totalAmount }
    val dailyEveningAmt = dailyAll.filter { it.shift == "EVENING" }.sumOf { it.totalAmount }
    val dailyAvgFat = if (dailyList.isNotEmpty()) dailyList.map { it.fat }.average() else 0.0
    val dailyAvgSnf = if (dailyList.isNotEmpty()) dailyList.map { it.snf }.average() else 0.0
    val dailyFarmersCount = dailyList.map { it.farmerId }.distinct().size
    val dailyPaidAmt = payments.filter { it.paymentDate == selectedDate }.sumOf { it.paymentAmount }
    val dailyExpenseAmt = expenses.filter { it.date == selectedDate }.sumOf { it.amount }

    // Monthly calculations
    val currentMonthPrefix = remember(selectedDate) { selectedDate.take(7) }
    val monthlyList = remember(collections, currentMonthPrefix) {
        collections.filter { it.date.startsWith(currentMonthPrefix) }
    }
    val monthlyTotalLiters = monthlyList.sumOf { it.quantity }
    val monthlyTotalAmt = monthlyList.sumOf { it.totalAmount }
    val cowLiters = monthlyList.filter { it.milkCategory == "COW" }.sumOf { it.quantity }
    val buffaloLiters = monthlyList.filter { it.milkCategory == "BUFFALO" }.sumOf { it.quantity }
    val monthlyAvgFat = if (monthlyList.isNotEmpty()) monthlyList.map { it.fat }.average() else 0.0
    val monthlyAvgSnf = if (monthlyList.isNotEmpty()) monthlyList.map { it.snf }.average() else 0.0
    val monthlyPaidAmt = payments.filter { it.paymentDate.startsWith(currentMonthPrefix) }.sumOf { it.paymentAmount }
    val monthlyExpenseAmt = expenses.filter { it.date.startsWith(currentMonthPrefix) }.sumOf { it.amount }

    // Export CSV Helper
    fun exportCsv(isDaily: Boolean) {
        try {
            val fileDir = File(context.cacheDir, "exports")
            if (!fileDir.exists()) fileDir.mkdirs()
            val filename = if (isDaily) "DailyReport_$selectedDate.csv" else "MonthlyReport_$currentMonthPrefix.csv"
            val file = File(fileDir, filename)
            val outputStream = FileOutputStream(file)

            val header = "ReceiptNo,Date,Shift,FarmerID,FarmerName,Category,Quantity,Fat,Snf,Rate,TotalAmount\n"
            outputStream.write(header.toByteArray())

            val listToExport = if (isDaily) dailyList else monthlyList
            for (entry in listToExport) {
                val row = "${entry.receiptNumber},${entry.date},${entry.shift},${entry.farmerId},\"${entry.farmerName}\",${entry.milkCategory},${entry.quantity},${entry.fat},${entry.snf},${entry.rate},${entry.totalAmount}\n"
                outputStream.write(row.toByteArray())
            }
            outputStream.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(sendIntent, "Excel / CSV रिपोर्ट साझा करें"))
        } catch (e: Exception) {
            Toast.makeText(context, "CSV एक्सपोर्ट त्रुटि: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.REPORTS_ANALYSIS,
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
                text = if (isHindi) "रिपोर्ट्स व विश्लेषण (Reports & Analysis)" else "Reports & Analytics",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Daily vs Monthly Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(if (isHindi) "दैनिक / शिफ्ट / खाता" else "Daily & Shift", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(if (isHindi) "मासिक सारांश (Monthly)" else "Monthly Summary", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTabIndex == 0) {
                // Multi-Report Switcher covering Daily, Morning, Evening, Farmer-Wise, Payments, Expenses
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        ReportSubMode.DAILY to (if (isHindi) "दैनिक" else "Daily"),
                        ReportSubMode.MORNING_SHIFT to (if (isHindi) "सुबह शिफ्ट" else "Morning Shift"),
                        ReportSubMode.EVENING_SHIFT to (if (isHindi) "शाम शिफ्ट" else "Evening Shift"),
                        ReportSubMode.FARMER_WISE to (if (isHindi) "किसान-वार" else "Farmer-Wise"),
                        ReportSubMode.PAYMENTS to (if (isHindi) "भुगतान रिपोर्ट" else "Payment Report"),
                        ReportSubMode.EXPENSES to (if (isHindi) "खर्च रिपोर्ट" else "Expenses Report")
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = subMode == mode,
                            onClick = {
                                subMode = mode
                                filterShift = when (mode) {
                                    ReportSubMode.MORNING_SHIFT -> "MORNING"
                                    ReportSubMode.EVENING_SHIFT -> "EVENING"
                                    else -> "ALL"
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DairyRedPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

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
                            text = when (subMode) {
                                ReportSubMode.DAILY -> if (isHindi) "दैनिक दूध संग्रहण विवरण" else "Daily Milk Collection"
                                ReportSubMode.MORNING_SHIFT -> if (isHindi) "सुबह शिफ्ट दूध रिपोर्ट" else "Morning Shift Milk Report"
                                ReportSubMode.EVENING_SHIFT -> if (isHindi) "शाम शिफ्ट दूध रिपोर्ट" else "Evening Shift Milk Report"
                                ReportSubMode.FARMER_WISE -> if (isHindi) "किसान-वार दूध व बकाया रिपोर्ट" else "Farmer-Wise Report"
                                ReportSubMode.PAYMENTS -> if (isHindi) "किसान भुगतान रिपोर्ट" else "Farmer Payment Report"
                                ReportSubMode.EXPENSES -> if (isHindi) "डेयरी खर्च रिपोर्ट" else "Dairy Expense Report"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DairyRedPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = selectedDate,
                                onValueChange = { selectedDate = it },
                                label = { Text("दिनांक (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("report_date_input")
                            )
                            TextButton(onClick = { selectedDate = viewModel.todayDate }) {
                                Text("आज (Today)")
                            }
                        }

                        if (subMode == ReportSubMode.DAILY) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("ALL" to "सभी पारी", "MORNING" to "सुबह", "EVENING" to "शाम").forEach { (code, label) ->
                                    FilterChip(
                                        selected = filterShift == code,
                                        onClick = { filterShift = code },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DairyRedPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        when (subMode) {
                            ReportSubMode.DAILY, ReportSubMode.MORNING_SHIFT, ReportSubMode.EVENING_SHIFT -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReportMetricBox(
                                        title = if (isHindi) "कुल वजन (Weight)" else "Total Weight",
                                        value = "${String.format("%.2f", dailyTotalLiters)} L",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportMetricBox(
                                        title = if (isHindi) "कुल राशि (Amount)" else "Total Amount",
                                        value = "₹${String.format("%.2f", dailyTotalAmt)}",
                                        valueColor = Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportMetricBox(
                                        title = if (isHindi) "किसान संख्या" else "Farmers",
                                        value = "$dailyFarmersCount",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReportMetricBox(
                                        title = if (isHindi) "सुबह (Wt / ₹)" else "Morning",
                                        value = "${String.format("%.1f", dailyMorningLiters)}L / ₹${String.format("%.0f", dailyMorningAmt)}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportMetricBox(
                                        title = if (isHindi) "शाम (Wt / ₹)" else "Evening",
                                        value = "${String.format("%.1f", dailyEveningLiters)}L / ₹${String.format("%.0f", dailyEveningAmt)}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportMetricBox(
                                        title = "औसत FAT / SNF",
                                        value = "${String.format("%.1f", dailyAvgFat)} / ${String.format("%.1f", dailyAvgSnf)}",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReportMetricBox(
                                        title = if (isHindi) "आज भुगतान" else "Day Paid",
                                        value = "₹${String.format("%.2f", dailyPaidAmt)}",
                                        valueColor = Color(0xFF1565C0),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportMetricBox(
                                        title = if (isHindi) "आज के खर्च" else "Day Expenses",
                                        value = "₹${String.format("%.2f", dailyExpenseAmt)}",
                                        valueColor = DairyRedPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { exportCsv(isDaily = true) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_export_daily_csv"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Excel / CSV एक्सपोर्ट", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val summaryMsg = """
📊 *दैनिक दूध रिपोर्ट (${station.centerName})*
📅 *दिनांक:* $selectedDate | *आईडी:* ${station.stationId}
-----------------------------------------
🥛 *कुल वजन:* ${String.format("%.2f", dailyTotalLiters)} लीटर
💰 *कुल राशि:* ₹${String.format("%.2f", dailyTotalAmt)}
👥 *किसान संख्या:* $dailyFarmersCount
☀️ *सुबह:* ${String.format("%.1f", dailyMorningLiters)} L | 🌙 *शाम:* ${String.format("%.1f", dailyEveningLiters)} L
🧪 *औसत FAT:* ${String.format("%.1f", dailyAvgFat)}% | 🔬 *SNF:* ${String.format("%.1f", dailyAvgSnf)}%
💵 *भुगतान:* ₹${String.format("%.2f", dailyPaidAmt)} | 🧾 *खर्च:* ₹${String.format("%.2f", dailyExpenseAmt)}
-----------------------------------------
नमस्ते इंडिया दूध केंद्र
                                            """.trimIndent()
                                            WhatsAppHelper.sendWhatsAppText(context, station.contactNumber, summaryMsg)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_share_daily_whatsapp"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                    ) {
                                        Text("WhatsApp सारांश", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            ReportSubMode.FARMER_WISE -> {
                                val grouped = remember(collections, farmers) {
                                    farmers.map { f ->
                                        val entries = collections.filter { it.farmerId == f.farmerId }
                                        val paid = payments.filter { it.farmerId == f.farmerId }.sumOf { it.paymentAmount }
                                        val wt = entries.sumOf { it.quantity }
                                        val bill = entries.sumOf { it.totalAmount }
                                        Triple(f, wt, bill - paid)
                                    }.sortedByDescending { it.second }
                                }
                                if (grouped.isEmpty()) {
                                    Text("कोई किसान डेटा उपलब्ध नहीं है।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    grouped.take(25).forEach { (farmer, wt, due) ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${farmer.fullName} (${farmer.farmerId})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("कुल दूध: ${String.format("%.2f", wt)} L", fontSize = 11.sp, color = Color.DarkGray)
                                                }
                                                Text(
                                                    text = "बकाया: ₹${String.format("%.2f", due)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (due >= 0) Color(0xFF2E7D32) else DairyRedPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            ReportSubMode.PAYMENTS -> {
                                val totalAllPaid = payments.sumOf { it.paymentAmount }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReportMetricBox(
                                        title = if (isHindi) "चयनित दिन भुगतान" else "Selected Date Paid",
                                        value = "₹${String.format("%.2f", dailyPaidAmt)}",
                                        valueColor = Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportMetricBox(
                                        title = if (isHindi) "कुल भुगतान" else "Total All-Time Paid",
                                        value = "₹${String.format("%.2f", totalAllPaid)}",
                                        valueColor = DairyRedPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                payments.take(15).forEach { pay ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("${pay.farmerName} (${pay.farmerId})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("${pay.paymentDate} • ${pay.paymentMode} • ${pay.transactionId}", fontSize = 10.sp)
                                            }
                                            Text("₹${String.format("%.2f", pay.paymentAmount)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        }
                                    }
                                }
                            }

                            ReportSubMode.EXPENSES -> {
                                val totalAllExp = expenses.sumOf { it.amount }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReportMetricBox(
                                        title = if (isHindi) "चयनित दिन खर्च" else "Selected Date Exp",
                                        value = "₹${String.format("%.2f", dailyExpenseAmt)}",
                                        valueColor = DairyRedPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportMetricBox(
                                        title = if (isHindi) "कुल खर्च" else "Total Expenses",
                                        value = "₹${String.format("%.2f", totalAllExp)}",
                                        valueColor = Color(0xFFE65100),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                expenses.take(15).forEach { exp ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(exp.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("${exp.date} • ${exp.category} • ${exp.paymentMode}", fontSize = 10.sp)
                                            }
                                            Text("₹${String.format("%.2f", exp.amount)}", fontWeight = FontWeight.Bold, color = DairyRedPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // --- MONTHLY REPORT ---
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
                            text = "मासिक दूध संग्रहण विश्लेषण ($currentMonthPrefix)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DairyRedPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportMetricBox(
                                title = "माह कुल वजन",
                                value = "${String.format("%.2f", monthlyTotalLiters)} L",
                                modifier = Modifier.weight(1f)
                            )
                            ReportMetricBox(
                                title = "माह कुल दूध राशि",
                                value = "₹${String.format("%.2f", monthlyTotalAmt)}",
                                valueColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportMetricBox(
                                title = "माह किसान भुगतान",
                                value = "₹${String.format("%.2f", monthlyPaidAmt)}",
                                valueColor = Color(0xFF1565C0),
                                modifier = Modifier.weight(1f)
                            )
                            ReportMetricBox(
                                title = "माह डेयरी खर्च",
                                value = "₹${String.format("%.2f", monthlyExpenseAmt)}",
                                valueColor = DairyRedPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("गाय बनाम भैंस संग्रहण (Cow vs Buffalo):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🐄 गाय दूध: ${String.format("%.1f", cowLiters)} L", color = CowGreen, fontWeight = FontWeight.Bold)
                                    Text("🐃 भैंस दूध: ${String.format("%.1f", buffaloLiters)} L", color = BuffaloBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportMetricBox(
                                title = "माह औसत FAT",
                                value = "${String.format("%.1f", monthlyAvgFat)}%",
                                modifier = Modifier.weight(1f)
                            )
                            ReportMetricBox(
                                title = "माह औसत SNF",
                                value = "${String.format("%.1f", monthlyAvgSnf)}%",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { exportCsv(isDaily = false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_export_monthly_csv"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("मासिक Excel / CSV रिपोर्ट डाउनलोड करें")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricBox(
    title: String,
    value: String,
    valueColor: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}
