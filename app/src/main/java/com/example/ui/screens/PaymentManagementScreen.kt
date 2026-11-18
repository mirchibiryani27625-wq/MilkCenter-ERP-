package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.example.data.model.ExpenseRecord
import com.example.data.model.Farmer
import com.example.data.model.PaymentRecord
import com.example.ui.components.DairyHeader
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.WhatsAppHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaymentManagementScreen(
    viewModel: DairyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val station by viewModel.stationConfig.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isHindi by viewModel.isHindi.collectAsState()
    val farmers by viewModel.farmers.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allCollections by viewModel.allCollections.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val notifications by viewModel.allNotifications.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Payments, 1: Dairy Expenses
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<PaymentRecord?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseRecord?>(null) }

    // Payment Summary calculations
    val totalMilkBill = allCollections.sumOf { it.totalAmount }
    val totalPaymentReceived = allPayments.sumOf { it.paymentAmount }
    val totalPendingAmount = totalMilkBill - totalPaymentReceived

    // Expense Summary calculations
    val totalExpensesAmount = allExpenses.sumOf { it.amount }
    val todayExpensesAmount = allExpenses.filter { it.date == viewModel.todayDate }.sumOf { it.amount }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.PAYMENT_MANAGEMENT,
                isHindi = isHindi,
                onNavigateBack = onBack,
                onToggleLanguage = { viewModel.toggleLanguage() },
                onLogout = { viewModel.logout() },
                syncStatus = syncStatus,
                notifications = notifications,
                onSyncNow = { viewModel.syncNow() },
                onMarkNotificationRead = { viewModel.markNotificationRead(it) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (activeTab == 0) showAddPaymentDialog = true else showAddExpenseDialog = true
                },
                containerColor = DairyRedPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag(if (activeTab == 0) "add_payment_fab" else "add_expense_fab")
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (activeTab == 0) "भुगतान दर्ज करें (Add Payment)" else "खर्च दर्ज करें (Add Expense)"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isHindi) "भुगतान व डेयरी खर्च प्रबंधन" else "Payments & Expenses",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 2-Tab Selector: Farmer Payments | Dairy Expenses
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.testTag("tab_farmer_payments"),
                    text = {
                        Text(
                            if (isHindi) "किसान भुगतान (${allPayments.size})" else "Payments (${allPayments.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.testTag("tab_dairy_expenses"),
                    text = {
                        Text(
                            if (isHindi) "डेयरी खर्च (${allExpenses.size})" else "Expenses (${allExpenses.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                )
            }

            if (activeTab == 0) {
                // Summary 3 Cards: Total Milk Bill, Total Paid, Total Pending
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentSummaryBox(
                        title = if (isHindi) "कुल दूध बिल" else "Total Bill",
                        amount = totalMilkBill,
                        containerColor = Color(0xFFE8EAF6),
                        amountColor = Color(0xFF283593),
                        modifier = Modifier.weight(1f)
                    )

                    PaymentSummaryBox(
                        title = if (isHindi) "कुल भुगतान" else "Total Paid",
                        amount = totalPaymentReceived,
                        containerColor = Color(0xFFE8F5E9),
                        amountColor = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )

                    PaymentSummaryBox(
                        title = if (isHindi) "कुल बकाया" else "Pending Due",
                        amount = totalPendingAmount,
                        containerColor = Color(0xFFFFEBEE),
                        amountColor = DairyRedPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Recent Payments Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHindi) "हालिया भुगतान रसीदें (${allPayments.size})" else "Recent Payments (${allPayments.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = { showAddPaymentDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(if (isHindi) "+ नया भुगतान" else "+ New Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (allPayments.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isHindi) "अभी तक कोई भुगतान दर्ज नहीं हुआ है।" else "No payment records found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allPayments, key = { it.transactionId }) { pay ->
                            val f = farmers.find { it.farmerId == pay.farmerId }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            com.example.ui.components.FarmerAvatar(
                                                photoUri = f?.photoUri,
                                                name = pay.farmerName,
                                                size = 42.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(text = "${pay.farmerName} (${pay.farmerId})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(
                                                    text = "आईडी: ${pay.transactionId} • ${pay.paymentDate} • ${pay.paymentMode}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Text(
                                            text = "₹${String.format("%.2f", pay.paymentAmount)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }

                                    if (pay.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "नोट: ${pay.note}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "शेष बकाया: ₹${String.format("%.2f", pay.finalBalance)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DairyRedPrimary
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(
                                                onClick = {
                                                    val msg = WhatsAppHelper.formatPaymentMessage(station, pay)
                                                    val mobile = f?.mobileNumber ?: "9876543210"
                                                    WhatsAppHelper.sendWhatsAppText(context, mobile, msg)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("WhatsApp रसीद", fontSize = 11.sp, color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            IconButton(
                                                onClick = { paymentToDelete = pay },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // --- DAIRY EXPENSES TAB ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentSummaryBox(
                        title = if (isHindi) "आज का खर्च" else "Today's Expenses",
                        amount = todayExpensesAmount,
                        containerColor = Color(0xFFFFF3E0),
                        amountColor = Color(0xFFE65100),
                        modifier = Modifier.weight(1f)
                    )
                    PaymentSummaryBox(
                        title = if (isHindi) "कुल डेयरी खर्च" else "Total Expenses",
                        amount = totalExpensesAmount,
                        containerColor = Color(0xFFFFEBEE),
                        amountColor = DairyRedPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHindi) "डेयरी खर्च सूची (${allExpenses.size})" else "Expense Entries (${allExpenses.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = { showAddExpenseDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("btn_new_expense")
                    ) {
                        Text(if (isHindi) "+ नया खर्च" else "+ Add Expense", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (allExpenses.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isHindi) "अभी तक कोई डेयरी खर्च दर्ज नहीं हुआ है।" else "No expenses recorded yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allExpenses, key = { it.expenseId }) { exp ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = exp.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                text = "${exp.category} • ${exp.date} • ${exp.paymentMode}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "₹${String.format("%.2f", exp.amount)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = DairyRedPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { expenseToDelete = exp },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Expense", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    if (exp.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "नोट: ${exp.note}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Payment Dialog
    if (showAddPaymentDialog) {
        var selectedF by remember { mutableStateOf<Farmer?>(farmers.firstOrNull()) }
        var dropdownExp by remember { mutableStateOf(false) }
        var amountStr by remember { mutableStateOf("") }
        var paymentMode by remember { mutableStateOf("CASH") }
        var paymentType by remember { mutableStateOf("WEEKLY") }
        var note by remember { mutableStateOf("") }

        val fSummary = remember(selectedF) {
            if (selectedF != null) viewModel.getFarmerAccountSummary(selectedF!!.farmerId) else null
        }
        val curBalance = fSummary?.finalBalance ?: 0.0
        val pAmt = amountStr.toDoubleOrNull() ?: 0.0
        val newBalance = curBalance - pAmt

        AlertDialog(
            onDismissRequest = { showAddPaymentDialog = false },
            title = { Text(if (isHindi) "नया भुगतान दर्ज करें" else "Record Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExp,
                        onExpandedChange = { dropdownExp = !dropdownExp }
                    ) {
                        OutlinedTextField(
                            value = if (selectedF != null) "${selectedF!!.fullName} (${selectedF!!.farmerId})" else "किसान चुनें...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("किसान") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExp,
                            onDismissRequest = { dropdownExp = false }
                        ) {
                            farmers.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text("${f.fullName} (${f.farmerId})") },
                                    onClick = {
                                        selectedF = f
                                        dropdownExp = false
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("वर्तमान बकाया: ₹${String.format("%.2f", curBalance)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("भुगतान बाद बकाया: ₹${String.format("%.2f", newBalance)}", fontSize = 12.sp, color = DairyRedPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("भुगतान राशि (₹ Amount) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_payment_amount")
                    )

                    Text("भुगतान माध्यम (Mode):", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH" to "नकद (Cash)", "UPI" to "UPI", "BANK TRANSFER" to "बैंक खाता").forEach { (m, label) ->
                            FilterChip(
                                selected = paymentMode == m,
                                onClick = { paymentMode = m },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DairyRedPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Text("भुगतान प्रकार (Type):", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("WEEKLY" to "साप्ताहिक", "ADVANCE" to "अग्रिम", "PARTIAL" to "आंशिक", "FULL" to "पूर्ण हिसाब").forEach { (t, label) ->
                            FilterChip(
                                selected = paymentType == t,
                                onClick = { paymentType = t },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("विवरण / नोट (वैकल्पिक)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedF == null) {
                            Toast.makeText(context, "किसान का चयन करें", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pAmt <= 0.0) {
                            Toast.makeText(context, "कृपया सही भुगतान राशि दर्ज करें", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val payId = viewModel.generatePaymentId()
                        val record = PaymentRecord(
                            transactionId = payId,
                            farmerId = selectedF!!.farmerId,
                            farmerName = selectedF!!.fullName,
                            paymentAmount = pAmt,
                            paymentDate = viewModel.todayDate,
                            paymentMode = paymentMode,
                            paymentType = paymentType,
                            note = note.trim().take(250),
                            previousBalance = curBalance,
                            finalBalance = newBalance
                        )

                        viewModel.savePayment(record) {
                            showAddPaymentDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary),
                    modifier = Modifier.testTag("submit_payment_button")
                ) {
                    Text(if (isHindi) "भुगतान सहेजें" else "Save Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPaymentDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        var title by remember { mutableStateOf("") }
        var expCategory by remember { mutableStateOf("ELECTRICITY") }
        var expAmountStr by remember { mutableStateOf("") }
        var expMode by remember { mutableStateOf("CASH") }
        var expNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text(if (isHindi) "नया डेयरी खर्च जोड़ें" else "Add Dairy Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (isHindi) "खर्च का विवरण (जैसे बिजली बिल) *" else "Expense Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_expense_title")
                    )

                    Text(if (isHindi) "श्रेणी (Category):" else "Category:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "ELECTRICITY" to "बिजली",
                            "SALARY" to "वेतन",
                            "MAINTENANCE" to "मरम्मत",
                            "CATTLE_FEED" to "पशु आहार",
                            "OTHER" to "अन्य"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = expCategory == code,
                                onClick = { expCategory = code },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DairyRedPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = expAmountStr,
                        onValueChange = { expAmountStr = it },
                        label = { Text(if (isHindi) "राशि (₹ Amount) *" else "Amount (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_expense_amount")
                    )

                    Text(if (isHindi) "माध्यम (Mode):" else "Payment Mode:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH" to "नकद", "UPI" to "UPI", "BANK TRANSFER" to "बैंक").forEach { (m, label) ->
                            FilterChip(
                                selected = expMode == m,
                                onClick = { expMode = m },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = expNote,
                        onValueChange = { expNote = it },
                        label = { Text(if (isHindi) "नोट (वैकल्पिक)" else "Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = expAmountStr.toDoubleOrNull() ?: 0.0
                        if (title.trim().length < 2) {
                            Toast.makeText(context, "कृपया खर्च का नाम दर्ज करें", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (amt <= 0.0) {
                            Toast.makeText(context, "कृपया सही खर्च राशि दर्ज करें", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val record = ExpenseRecord(
                            expenseId = viewModel.generateExpenseId(),
                            title = title.trim().take(120),
                            category = expCategory,
                            amount = amt,
                            date = viewModel.todayDate,
                            paymentMode = expMode,
                            note = expNote.trim().take(250),
                            recordedBy = session.userName.ifBlank { "Operator" }
                        )
                        viewModel.saveExpense(record) {
                            showAddExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary),
                    modifier = Modifier.testTag("submit_expense_button")
                ) {
                    Text(if (isHindi) "खर्च सहेजें" else "Save Expense")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    // Delete Payment confirmation
    if (paymentToDelete != null) {
        val p = paymentToDelete!!
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text(if (isHindi) "भुगतान हटाने की पुष्टि" else "Confirm Delete") },
            text = { Text("क्या आप लेनदेन ${p.transactionId} हटाना चाहते हैं?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(p)
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("हटाएं")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text("रद्द करें")
                }
            }
        )
    }

    // Delete Expense confirmation
    if (expenseToDelete != null) {
        val e = expenseToDelete!!
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(if (isHindi) "खर्च हटाने की पुष्टि" else "Delete Expense") },
            text = { Text("क्या आप खर्च ${e.title} (₹${String.format("%.2f", e.amount)}) हटाना चाहते हैं?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(e)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("हटाएं")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("रद्द करें")
                }
            }
        )
    }
}

@Composable
private fun PaymentSummaryBox(
    title: String,
    amount: Double,
    containerColor: Color,
    amountColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "₹${String.format("%.2f", amount)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}
