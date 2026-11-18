package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.AuditLog
import com.example.data.model.Farmer
import com.example.data.model.FarmerMachineMapping
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.ScanRecord
import com.example.ui.components.DairyHeader
import com.example.ui.components.FarmerAvatar
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardPureWhite
import com.example.ui.theme.CowGreen
import com.example.ui.theme.DairyGreenSecondary
import com.example.ui.theme.DarkNavyTextPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GreyTextSecondary
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SoftWhiteBackground
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.FieldConfidenceMap
import com.example.util.ScanVerificationEngine
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminVerificationScreen(
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
    val pendingScans by viewModel.pendingScanRecords.collectAsState()
    val allScans by viewModel.allScanRecords.collectAsState()
    val farmers by viewModel.farmers.collectAsState()
    val mappings by viewModel.machineMappings.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var imagePreviewPath by remember { mutableStateOf<String?>(null) }
    var recordToApprove by remember { mutableStateOf<ScanRecord?>(null) }
    var recordToReject by remember { mutableStateOf<ScanRecord?>(null) }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.ADMIN_VERIFICATION,
                isHindi = isHindi,
                onNavigateBack = onBack,
                onToggleLanguage = { viewModel.toggleLanguage() },
                onLogout = { viewModel.logout() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SoftWhiteBackground)
        ) {
            // Top Title Card
            Surface(color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = NavyPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Admin सत्यापन व स्कैन इतिहास",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavyTextPrimary
                            )
                            Text(
                                text = "लंबित: ${pendingScans.size} • कुल स्कैन: ${allScans.size} • मैपिंग: ${mappings.size}",
                                fontSize = 11.sp,
                                color = GreyTextSecondary
                            )
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("admin_tab_queue"),
                    text = { Text("कतार (${pendingScans.size})", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("admin_tab_history"),
                    text = { Text("इतिहास (${allScans.size})", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.testTag("admin_tab_mappings"),
                    text = { Text("ID मैपिंग", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.testTag("admin_tab_audit"),
                    text = { Text("ऑडिट लॉग", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            when (selectedTab) {
                0 -> ScanRecordsListView(
                    records = pendingScans,
                    farmers = farmers,
                    emptyMessage = "कोई भी स्कैन सत्यापन के लिए लंबित नहीं है।",
                    showAdminActions = true,
                    onPreviewImage = { imagePreviewPath = it },
                    onApprove = { recordToApprove = it },
                    onReject = { recordToReject = it }
                )

                1 -> ScanRecordsListView(
                    records = allScans,
                    farmers = farmers,
                    emptyMessage = "अभी तक कोई स्कैन रिकॉर्ड सहेजा नहीं गया है।",
                    showAdminActions = false,
                    onPreviewImage = { imagePreviewPath = it },
                    onApprove = { recordToApprove = it },
                    onReject = { recordToReject = it }
                )

                2 -> MachineMappingTabView(
                    farmers = farmers,
                    mappings = mappings,
                    onSaveMapping = { code, fId, mId ->
                        viewModel.saveFarmerMachineMapping(code, fId, mId) {
                            Toast.makeText(context, "मशीन कोड '$code' → '$fId' मैप हो गया!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteMapping = { viewModel.deleteMachineMapping(it) }
                )

                3 -> AuditLogsTabView(logs = auditLogs)
            }
        }
    }

    // Full Source Image Viewer
    if (imagePreviewPath != null) {
        Dialog(onDismissRequest = { imagePreviewPath = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.Black, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("मूल स्कैन छवि (Original Image)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { imagePreviewPath = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    AsyncImage(
                        model = resolveImageModel(imagePreviewPath!!),
                        contentDescription = "Original Scan",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }

    // Admin Approve & Edit Dialog
    recordToApprove?.let { rec ->
        AdminApproveScanDialog(
            record = rec,
            farmers = farmers,
            onDismiss = { recordToApprove = null },
            onApproveAndSave = { editedRec, entry, savePermanentMapping ->
                scope.launch {
                    if (savePermanentMapping && rec.customerCode.isNotBlank() && rec.customerCode != "UNKNOWN") {
                        viewModel.saveFarmerMachineMapping(rec.customerCode, entry.farmerId, rec.machineId.ifBlank { "ALL" })
                    }
                    viewModel.approveScanRecordAsAdmin(rec, editedRec, entry) {
                        Toast.makeText(context, "Admin द्वारा रसीद ${entry.receiptNumber} सत्यापित कर सहेजी गई!", Toast.LENGTH_LONG).show()
                        recordToApprove = null
                    }
                }
            },
            generateReceiptNo = { shift, date -> viewModel.generateReceiptNumber(shift, date) }
        )
    }

    // Reject Reason Dialog
    recordToReject?.let { rec ->
        var reason by remember { mutableStateOf("अस्पष्ट या डुप्लीकेट रीडिंग") }
        AlertDialog(
            onDismissRequest = { recordToReject = null },
            title = { Text("स्कैन अस्वीकार करें", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("स्कैन ID: ${rec.recordId} (कोड: ${rec.customerCode})", fontSize = 12.sp)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("अस्वीकृति का कारण") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectScanRecordAsAdmin(rec, reason.ifBlank { "Rejected by Admin" }) {
                            Toast.makeText(context, "स्कैन अस्वीकृत किया गया", Toast.LENGTH_SHORT).show()
                            recordToReject = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("अस्वीकार करें") }
            },
            dismissButton = {
                TextButton(onClick = { recordToReject = null }) { Text("रद्द करें") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanRecordsListView(
    records: List<ScanRecord>,
    farmers: List<Farmer>,
    emptyMessage: String,
    showAdminActions: Boolean,
    onPreviewImage: (String) -> Unit,
    onApprove: (ScanRecord) -> Unit,
    onReject: (ScanRecord) -> Unit
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = GreyTextSecondary, fontSize = 13.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(records, key = { it.recordId }) { rec ->
            var showRaw by remember { mutableStateOf(false) }
            val matchedFarmer = farmers.find { it.farmerId == rec.farmerId }
            val confMap = remember(rec.fieldConfidence) { FieldConfidenceMap.fromJson(rec.fieldConfidence) }

            val statusColor = when (rec.verificationStatus) {
                "AUTO_VERIFIED", "ADMIN_VERIFIED" -> CowGreen
                "DUPLICATE_BLOCKED", "REJECTED" -> ErrorRed
                else -> AmberWarning
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardPureWhite),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Header: Record ID + Status Badge + Confidence
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = matchedFarmer?.let { "${it.fullName} (${it.farmerId})" } ?: "अज्ञात कोड: ${rec.customerCode}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkNavyTextPrimary
                            )
                            Text(
                                text = "${rec.scanMode} • मशीन: ${rec.machineId} • ${rec.date} (${rec.shift})",
                                fontSize = 11.sp,
                                color = GreyTextSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = rec.verificationStatus,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${rec.confidenceTier} (${rec.overallConfidence}%)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GreyTextSecondary
                            )
                        }
                    }

                    // Extracted Values Summary Row
                    Surface(color = SoftWhiteBackground, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("WT: ${rec.weight ?: "?"}L", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("FAT: ${rec.fat ?: "?"}%", fontSize = 12.sp)
                            Text("SNF: ${rec.snf ?: "?"}%", fontSize = 12.sp)
                            Text("₹${rec.amount ?: "?"}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DairyGreenSecondary)
                        }
                    }

                    // Per-field Confidence Strip
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SmallConfTag("ID", confMap.customerId)
                        SmallConfTag("FAT", confMap.fat)
                        SmallConfTag("SNF", confMap.snf)
                        SmallConfTag("WT", confMap.weight)
                        SmallConfTag("RATE", confMap.rate)
                        SmallConfTag("AMT", confMap.amount)
                        SmallConfTag("DATE", confMap.date)
                    }

                    if (!rec.duplicateWarning.isNullOrBlank()) {
                        Surface(color = ErrorRed.copy(alpha = 0.10f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(rec.duplicateWarning, fontSize = 10.sp, color = ErrorRed)
                            }
                        }
                    }

                    if (rec.verificationErrors.isNotBlank()) {
                        Text(
                            text = "नोट: ${rec.verificationErrors}",
                            fontSize = 10.sp,
                            color = Color(0xFF7D4E00)
                        )
                    }

                    if (!rec.linkedReceiptNumber.isNullOrBlank()) {
                        Text(
                            text = "✓ बही रसीद: ${rec.linkedReceiptNumber} (${rec.verifiedBy ?: "Auto"})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CowGreen
                        )
                    }

                    // Image & Raw OCR Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!rec.sourceImage.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { onPreviewImage(rec.sourceImage) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("छवि देखें", fontSize = 10.sp)
                                }
                            }
                            if (rec.rawOcrText.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { showRaw = !showRaw },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (showRaw) "OCR बंद" else "Raw OCR", fontSize = 10.sp)
                                }
                            }
                        }

                        if (showAdminActions || rec.verificationStatus in listOf("PENDING_ADMIN", "DUPLICATE_BLOCKED")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onReject(rec) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                    modifier = Modifier.height(32.dp).testTag("admin_reject_${rec.recordId}")
                                ) {
                                    Text("अस्वीकार", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { onApprove(rec) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    modifier = Modifier.height(32.dp).testTag("admin_approve_${rec.recordId}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("सत्यापित करें", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (showRaw) {
                        Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = rec.rawOcrText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MachineMappingTabView(
    farmers: List<Farmer>,
    mappings: List<FarmerMachineMapping>,
    onSaveMapping: (String, String, String) -> Unit,
    onDeleteMapping: (FarmerMachineMapping) -> Unit
) {
    var codeText by remember { mutableStateOf("") }
    var machineText by remember { mutableStateOf("ALL") }
    var selectedFarmer by remember { mutableStateOf<Farmer?>(farmers.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardPureWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddLink, contentDescription = null, tint = NavyPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("मशीन Customer ID ↔ किसान खाता मैपिंग", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it.uppercase() },
                        label = { Text("मशीन कोड (e.g. 102 / M05) *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("mapping_code_input")
                    )
                    OutlinedTextField(
                        value = machineText,
                        onValueChange = { machineText = it.uppercase() },
                        label = { Text("मशीन ID") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedFarmer?.let { "${it.fullName} (${it.farmerId})" } ?: "किसान चुनें...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("ऐप किसान (Registered Farmer) *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        farmers.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("${f.fullName} (${f.farmerId}) - ${f.villageAddress}") },
                                onClick = {
                                    selectedFarmer = f
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    enabled = codeText.isNotBlank() && selectedFarmer != null,
                    onClick = {
                        onSaveMapping(codeText, selectedFarmer!!.farmerId, machineText)
                        codeText = ""
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("save_machine_mapping_btn")
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("मैपिंग सुरक्षित करें", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("सक्रिय मशीन मैपिंग (${mappings.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        mappings.forEach { m ->
            val f = farmers.find { it.farmerId == m.farmerId }
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardPureWhite)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        FarmerAvatar(photoUri = f?.photoUri, name = f?.fullName ?: m.farmerId, size = 34.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("मशीन कोड '${m.machineCustomerCode}' → ${m.farmerId} (${f?.fullName ?: "किसान"})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("मशीन: ${m.machineId} • द्वारा: ${m.updatedBy}", fontSize = 11.sp, color = GreyTextSecondary)
                        }
                    }
                    IconButton(onClick = { onDeleteMapping(m) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Mapping", tint = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogsTabView(logs: List<AuditLog>) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("अभी कोई ऑडिट लॉग दर्ज नहीं है।", color = GreyTextSecondary)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs) { log ->
            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = CardPureWhite)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                        Text(sdf.format(Date(log.timestamp)), fontSize = 10.sp, color = GreyTextSecondary)
                    }
                    Text(log.details, fontSize = 12.sp, color = DarkNavyTextPrimary)
                    if (log.oldValue.isNotBlank() || log.newValue.isNotBlank()) {
                        Surface(color = SoftWhiteBackground, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                if (log.oldValue.isNotBlank()) {
                                    Text("पुराना मान: ${log.oldValue}", fontSize = 10.sp, color = ErrorRed)
                                }
                                if (log.newValue.isNotBlank()) {
                                    Text("नया मान: ${log.newValue}", fontSize = 10.sp, color = CowGreen)
                                }
                            }
                        }
                    }
                    Text("ऑपरेटर/Admin: ${log.operatorName}${if (log.recordRef.isNotBlank()) " • संदर्भ: ${log.recordRef}" else ""}", fontSize = 10.sp, color = GreyTextSecondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminApproveScanDialog(
    record: ScanRecord,
    farmers: List<Farmer>,
    onDismiss: () -> Unit,
    onApproveAndSave: (ScanRecord, MilkCollectionEntry, Boolean) -> Unit,
    generateReceiptNo: suspend (String, String) -> String
) {
    val scope = rememberCoroutineScope()
    var selectedFarmer by remember { mutableStateOf(farmers.find { it.farmerId == record.farmerId }) }
    var farmerMenuOpen by remember { mutableStateOf(false) }
    var saveMappingCheck by remember { mutableStateOf(record.farmerId == null && record.customerCode != "UNKNOWN") }

    var dateText by remember { mutableStateOf(if (record.date == "UNKNOWN") "" else record.date) }
    var timeText by remember { mutableStateOf(if (record.time == "UNKNOWN") "" else record.time) }
    var shiftText by remember { mutableStateOf(if (record.shift == "UNKNOWN") "MORNING" else record.shift) }
    var categoryText by remember { mutableStateOf(if (record.animalType == "BUFFALO") "BUFFALO" else "COW") }
    var fatText by remember { mutableStateOf(record.fat?.toString() ?: "") }
    var snfText by remember { mutableStateOf(record.snf?.toString() ?: "") }
    var waterText by remember { mutableStateOf(record.water?.toString() ?: "0.0") }
    var weightText by remember { mutableStateOf(record.weight?.toString() ?: "") }
    var rateText by remember { mutableStateOf(record.rate?.toString() ?: "") }
    var amountText by remember { mutableStateOf(record.amount?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin सत्यापन व सुधार (${record.customerCode})", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(expanded = farmerMenuOpen, onExpandedChange = { farmerMenuOpen = it }) {
                    OutlinedTextField(
                        value = selectedFarmer?.let { "${it.fullName} (${it.farmerId})" } ?: "किसान चुनें *",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("पंजीकृत किसान *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmerMenuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    ExposedDropdownMenu(expanded = farmerMenuOpen, onDismissRequest = { farmerMenuOpen = false }) {
                        farmers.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("${f.fullName} (${f.farmerId})") },
                                onClick = {
                                    selectedFarmer = f
                                    categoryText = f.milkCategory
                                    farmerMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (record.customerCode.isNotBlank() && record.customerCode != "UNKNOWN" && selectedFarmer != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveMappingCheck, onCheckedChange = { saveMappingCheck = it })
                        Text("मशीन कोड '${record.customerCode}' को ${selectedFarmer!!.farmerId} से स्थायी मैप करें", fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = dateText, onValueChange = { dateText = it }, label = { Text("YYYY-MM-DD *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = timeText, onValueChange = { timeText = it }, label = { Text("Time") }, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = shiftText == "MORNING", onClick = { shiftText = "MORNING" }, label = { Text("सुबह", fontSize = 11.sp) })
                    FilterChip(selected = shiftText == "EVENING", onClick = { shiftText = "EVENING" }, label = { Text("शाम", fontSize = 11.sp) })
                    FilterChip(selected = categoryText == "COW", onClick = { categoryText = "COW" }, label = { Text("गाय", fontSize = 11.sp) })
                    FilterChip(selected = categoryText == "BUFFALO", onClick = { categoryText = "BUFFALO" }, label = { Text("भैंस", fontSize = 11.sp) })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = fatText, onValueChange = { fatText = it }, label = { Text("FAT *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = snfText, onValueChange = { snfText = it }, label = { Text("SNF *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = weightText, onValueChange = { weightText = it }, label = { Text("Weight *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = waterText, onValueChange = { waterText = it }, label = { Text("Water %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = rateText, onValueChange = { rateText = it }, label = { Text("Rate *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            val isoDate = ScanVerificationEngine.normalizeDateToIso(dateText)
            val fV = fatText.toDoubleOrNull()
            val sV = snfText.toDoubleOrNull()
            val wV = weightText.toDoubleOrNull()
            val rV = rateText.toDoubleOrNull()
            val aV = amountText.toDoubleOrNull()
            val isReady = selectedFarmer != null && isoDate != null && fV != null && sV != null && wV != null && wV > 0 && rV != null && rV > 0 && aV != null && aV > 0

            Button(
                enabled = isReady,
                onClick = {
                    val farmer = selectedFarmer!!
                    scope.launch {
                        val rcpNo = generateReceiptNo(shiftText, isoDate!!)
                        val entry = MilkCollectionEntry(
                            receiptNumber = rcpNo,
                            date = isoDate,
                            timestamp = System.currentTimeMillis(),
                            shift = shiftText,
                            farmerId = farmer.farmerId,
                            farmerName = farmer.fullName,
                            milkCategory = categoryText,
                            quantity = wV!!,
                            fat = fV!!,
                            snf = sV!!,
                            waterPercentage = waterText.toDoubleOrNull() ?: 0.0,
                            rate = rV!!,
                            totalAmount = aV!!,
                            machineAmount = record.amount,
                            scannedImageUri = record.sourceImage,
                            isOcrScanned = true,
                            ocrConfidence = record.overallConfidence,
                            machineNumber = record.machineId,
                            actualTime = timeText,
                            transactionId = record.transactionId.ifBlank { null },
                            scanRecordId = record.recordId,
                            verifiedBy = "Admin Verified",
                            stationId = record.stationId.takeIf { it != "UNKNOWN" }
                        )
                        val editedRec = record.copy(
                            farmerId = farmer.farmerId,
                            date = isoDate,
                            time = timeText.ifBlank { "UNKNOWN" },
                            shift = shiftText,
                            animalType = categoryText,
                            fat = fV,
                            snf = sV,
                            water = waterText.toDoubleOrNull(),
                            weight = wV,
                            rate = rV,
                            amount = aV
                        )
                        onApproveAndSave(editedRec, entry, saveMappingCheck)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DairyGreenSecondary)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("सत्यापित व सहेजें", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("रद्द करें") }
        }
    )
}

@Composable
private fun SmallConfTag(label: String, score: Int) {
    val color = when {
        score >= 85 -> CowGreen
        score >= 60 -> AmberWarning
        else -> ErrorRed
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
        Text(
            text = "$label:${if (score > 0) "$score%" else "?"}",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

private fun resolveImageModel(path: String): Any {
    return if (path.startsWith("/")) {
        File(path)
    } else {
        Uri.parse(path)
    }
}
