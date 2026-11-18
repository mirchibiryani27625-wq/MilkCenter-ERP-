package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Farmer
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.RateChartConfig
import com.example.ui.components.DairyHeader
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BuffaloBlue
import com.example.ui.theme.CowGreen
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.PdfGenerator
import com.example.util.RateCalculator
import com.example.util.WhatsAppHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilkCollectionScreen(
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
    val farmers by viewModel.farmers.collectAsState()
    val collections by viewModel.allCollections.collectAsState()

    var shift by remember { mutableStateOf("MORNING") } // "MORNING" or "EVENING"
    var selectedFarmer by remember { mutableStateOf<Farmer?>(farmers.firstOrNull()) }
    var farmerDropdownExpanded by remember { mutableStateOf(false) }

    var category by remember { mutableStateOf(selectedFarmer?.milkCategory ?: "COW") }
    var quantityText by remember { mutableStateOf("10.0") }
    var fatText by remember { mutableStateOf("4.2") }
    var snfText by remember { mutableStateOf("8.5") }
    var waterText by remember { mutableStateOf("0.0") }
    var calculatedRate by remember { mutableStateOf(42.0) }
    var totalAmount by remember { mutableStateOf(420.0) }
    var machineAmt by remember { mutableStateOf<Double?>(null) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var isOcrScanned by remember { mutableStateOf(false) }

    val pendingScanned by viewModel.pendingScannedCollection.collectAsStateWithLifecycle()
    LaunchedEffect(pendingScanned) {
        pendingScanned?.let { entry ->
            selectedFarmer = farmers.find { it.farmerId.equals(entry.farmerId, ignoreCase = true) }
                ?: Farmer(
                    farmerId = entry.farmerId,
                    fullName = entry.farmerName,
                    mobileNumber = "9876543210",
                    villageAddress = "तुर्की",
                    milkCategory = entry.milkCategory
                )
            category = entry.milkCategory
            shift = entry.shift
            fatText = "${entry.fat}"
            snfText = "${entry.snf}"
            waterText = "${entry.waterPercentage}"
            quantityText = "${entry.quantity}"
            calculatedRate = entry.rate
            totalAmount = entry.totalAmount
            machineAmt = entry.machineAmount
            isOcrScanned = true
            viewModel.setPendingScannedCollection(null)
        }
    }
    var activeRateChart by remember {
        mutableStateOf(
            RateChartConfig(
                category = "COW",
                baseRate = 38.0,
                baseFat = 3.5,
                fatMultiplier = 5.5,
                baseSnf = 8.5,
                snfMultiplier = 3.0
            )
        )
    }

    // Refresh rate chart on category change
    remember(category) {
        scope.launch {
            activeRateChart = viewModel.getRateChartForCategory(category)
            val q = quantityText.toDoubleOrNull() ?: 0.0
            val f = fatText.toDoubleOrNull() ?: 0.0
            val s = snfText.toDoubleOrNull() ?: 0.0
            val w = waterText.toDoubleOrNull() ?: 0.0
            val res = RateCalculator.calculate(q, f, s, w, activeRateChart, machineAmt)
            calculatedRate = res.ratePerLiter
            totalAmount = res.totalAmount
        }
    }

    fun recalculate() {
        val q = quantityText.toDoubleOrNull() ?: 0.0
        val f = fatText.toDoubleOrNull() ?: 0.0
        val s = snfText.toDoubleOrNull() ?: 0.0
        val w = waterText.toDoubleOrNull() ?: 0.0
        val res = RateCalculator.calculate(q, f, s, w, activeRateChart, machineAmt)
        calculatedRate = res.ratePerLiter
        totalAmount = res.totalAmount
    }

    var entryToDelete by remember { mutableStateOf<MilkCollectionEntry?>(null) }
    var entryToEdit by remember { mutableStateOf<MilkCollectionEntry?>(null) }
    var lastSavedEntry by remember { mutableStateOf<MilkCollectionEntry?>(null) }
    var showSuccessReceiptDialog by remember { mutableStateOf(false) }

    var historyFilterMode by remember { mutableStateOf("DAILY") } // DAILY, MORNING, EVENING, MONTHLY, FARMER
    val currentMonthPrefix = remember(viewModel.todayDate) { viewModel.todayDate.take(7) }
    val filteredHistoryEntries = remember(collections, historyFilterMode, viewModel.todayDate, selectedFarmer, currentMonthPrefix) {
        when (historyFilterMode) {
            "MORNING" -> collections.filter { it.date == viewModel.todayDate && it.shift == "MORNING" }
            "EVENING" -> collections.filter { it.date == viewModel.todayDate && it.shift == "EVENING" }
            "MONTHLY" -> collections.filter { it.date.startsWith(currentMonthPrefix) }
            "FARMER" -> selectedFarmer?.let { f -> collections.filter { it.farmerId == f.farmerId } } ?: collections
            else -> collections.filter { it.date == viewModel.todayDate }
        }
    }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.MILK_COLLECTION,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Shift Selector & AI Scan Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shift Toggle (Morning / Evening)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shift == "MORNING",
                        onClick = { shift = "MORNING" },
                        label = { Text(if (isHindi) "सुबह (Morning)" else "Morning") },
                        leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF57C00),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("shift_morning_chip")
                    )

                    FilterChip(
                        selected = shift == "EVENING",
                        onClick = { shift = "EVENING" },
                        label = { Text(if (isHindi) "शाम (Evening)" else "Evening") },
                        leadingIcon = { Icon(Icons.Default.NightsStay, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF5E35B1),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("shift_evening_chip")
                    )
                }

                // AI Smart Camera Button
                Button(
                    onClick = { showCameraScanner = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary),
                    modifier = Modifier.testTag("btn_open_ai_camera")
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isHindi) "AI कैमरा स्कैन" else "AI Camera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Collection Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                        text = if (isHindi) "दूध संग्रहण प्रविष्टि (Milk Entry)" else "Milk Collection Entry",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Farmer Selection Dropdown
                    ExposedDropdownMenuBox(
                        expanded = farmerDropdownExpanded,
                        onExpandedChange = { farmerDropdownExpanded = !farmerDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (selectedFarmer != null) "${selectedFarmer!!.fullName} (${selectedFarmer!!.farmerId})" else "किसान चुनें...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isHindi) "किसान चुनें (Select Farmer) *" else "Select Farmer") },
                            leadingIcon = {
                                if (selectedFarmer != null) {
                                    com.example.ui.components.FarmerAvatar(
                                        photoUri = selectedFarmer!!.photoUri,
                                        name = selectedFarmer!!.fullName,
                                        size = 28.dp
                                    )
                                }
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmerDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .testTag("collection_farmer_selector")
                        )

                        ExposedDropdownMenu(
                            expanded = farmerDropdownExpanded,
                            onDismissRequest = { farmerDropdownExpanded = false }
                        ) {
                            farmers.forEach { f ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        com.example.ui.components.FarmerAvatar(
                                            photoUri = f.photoUri,
                                            name = f.fullName,
                                            size = 28.dp
                                        )
                                    },
                                    text = { Text("${f.fullName} (${f.farmerId}) - ${f.villageAddress}") },
                                    onClick = {
                                        selectedFarmer = f
                                        category = f.milkCategory
                                        farmerDropdownExpanded = false
                                        recalculate()
                                    }
                                )
                            }
                        }
                    }

                    // Animal Category: COW / BUFFALO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHindi) "दूध श्रेणी:" else "Category:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = category == "COW",
                                onClick = {
                                    category = "COW"
                                    recalculate()
                                },
                                label = { Text("गाय (COW)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CowGreen,
                                    selectedLabelColor = Color.White
                                )
                            )

                            FilterChip(
                                selected = category == "BUFFALO",
                                onClick = {
                                    category = "BUFFALO"
                                    recalculate()
                                },
                                label = { Text("भैंस (BUFFALO)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BuffaloBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Quantity (Weight) & FAT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = {
                                quantityText = it
                                recalculate()
                            },
                            label = { Text("मात्रा / वजन (L/Kg) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_milk_qty")
                        )

                        OutlinedTextField(
                            value = fatText,
                            onValueChange = {
                                fatText = it
                                recalculate()
                            },
                            label = { Text("FAT (%) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_milk_fat")
                        )
                    }

                    // SNF & Water %
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = snfText,
                            onValueChange = {
                                snfText = it
                                recalculate()
                            },
                            label = { Text("SNF (%) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_milk_snf")
                        )

                        OutlinedTextField(
                            value = waterText,
                            onValueChange = {
                                waterText = it
                                recalculate()
                            },
                            label = { Text("Water (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_milk_water")
                        )
                    }

                    // Dynamic Rate & Total Amount Display Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isHindi) "गणना दर (Rate):" else "Rate/Liter:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "₹${String.format("%.2f", calculatedRate)} / L",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DairyRedPrimary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isHindi) "कुल देय राशि (Total Amount):" else "Total Amount:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "₹${String.format("%.2f", totalAmount)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    // Save / Print Receipt Button
                    Button(
                        onClick = {
                            if (selectedFarmer == null) {
                                Toast.makeText(context, "कृपया किसान का चयन करें", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val q = quantityText.toDoubleOrNull() ?: 0.0
                            val f = fatText.toDoubleOrNull() ?: 0.0
                            val s = snfText.toDoubleOrNull() ?: 0.0
                            val w = waterText.toDoubleOrNull() ?: 0.0

                            if (q <= 0.0) {
                                Toast.makeText(context, "कृपया सही दूध मात्रा दर्ज करें", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch {
                                val rcpNo = viewModel.generateReceiptNumber(shift)
                                val dateParts = viewModel.todayDate.split("-")
                                val entry = MilkCollectionEntry(
                                    receiptNumber = rcpNo,
                                    date = viewModel.todayDate,
                                    shift = shift,
                                    farmerId = selectedFarmer!!.farmerId,
                                    farmerName = selectedFarmer!!.fullName,
                                    milkCategory = category,
                                    quantity = q,
                                    fat = f,
                                    snf = s,
                                    waterPercentage = w,
                                    rate = calculatedRate,
                                    totalAmount = totalAmount,
                                    machineAmount = machineAmt,
                                    isOcrScanned = isOcrScanned,
                                    month = dateParts.getOrNull(1)?.toIntOrNull() ?: 0,
                                    year = dateParts.getOrNull(0)?.toIntOrNull() ?: 0,
                                    sourceType = if (isOcrScanned) "MILK_RECEIPT" else "MANUAL",
                                    verificationStatus = "CONFIRMED"
                                )
                                viewModel.saveMilkEntry(entry) { saved ->
                                    lastSavedEntry = saved
                                    showSuccessReceiptDialog = true
                                    // Reset quantity for next farmer
                                    quantityText = ""
                                    isOcrScanned = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_milk_collection_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                    ) {
                        Text(
                            text = if (isHindi) "पर्ची सहेजें व रसीद बनाएं (Save & Receipt)" else "Save & Generate Receipt",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Collection History Filter Section (Daily, Morning, Evening, Monthly, Farmer)
            Text(
                text = if (isHindi) "दूध संग्रहण इतिहास (${filteredHistoryEntries.size})" else "Collection History (${filteredHistoryEntries.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "DAILY" to if (isHindi) "आज (Daily)" else "Daily",
                    "MORNING" to if (isHindi) "सुबह (Morning)" else "Morning",
                    "EVENING" to if (isHindi) "शाम (Evening)" else "Evening",
                    "MONTHLY" to if (isHindi) "मासिक (Monthly)" else "Monthly",
                    "FARMER" to if (isHindi) "किसान-वार (Farmer)" else "Farmer"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = historyFilterMode == key,
                        onClick = { historyFilterMode = key },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DairyRedPrimary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("history_filter_$key")
                    )
                }
            }

            if (filteredHistoryEntries.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalDrink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isHindi) "चयनित फ़िल्टर में कोई दूध संग्रह नहीं मिला।" else "No milk collections found for filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                filteredHistoryEntries.forEach { entry ->
                    val farmerObj = farmers.find { it.farmerId == entry.farmerId }
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
                                        photoUri = farmerObj?.photoUri,
                                        name = entry.farmerName,
                                        size = 42.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (entry.shift == "MORNING") Color(0xFFFFF3E0) else Color(0xFFEDE7F6),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (entry.shift == "MORNING") "सुबह" else "शाम",
                                                    color = if (entry.shift == "MORNING") Color(0xFFE65100) else Color(0xFF5E35B1),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${entry.farmerName} (${entry.farmerId})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "रसीद: ${entry.receiptNumber} • ${entry.milkCategory}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "₹${String.format("%.2f", entry.totalAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "रसीद: ${entry.receiptNumber} • ${entry.quantity}L • Fat: ${entry.fat}% • SNF: ${entry.snf}% • दर: ₹${entry.rate}${if (!entry.verifiedBy.isNullOrBlank()) " • 🔒 ${entry.verifiedBy}" else ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // WhatsApp Button
                                OutlinedButton(
                                    onClick = {
                                        val msg = WhatsAppHelper.formatMilkBillMessage(station, entry, farmerObj)
                                        val mobile = farmerObj?.mobileNumber ?: "9876543210"
                                        WhatsAppHelper.sendWhatsAppText(context, mobile, msg)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("WhatsApp", fontSize = 11.sp, color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // PDF Download / Share Button
                                OutlinedButton(
                                    onClick = {
                                        val uri = PdfGenerator.generateMilkReceiptPdf(context, station, entry, farmerObj)
                                        if (uri != null) {
                                            WhatsAppHelper.sharePdf(context, uri, "दूध रसीद ${entry.receiptNumber}")
                                        } else {
                                            Toast.makeText(context, "PDF तैयार करने में त्रुटि", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp), tint = DairyRedPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF", fontSize = 11.sp, color = DairyRedPrimary)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { entryToEdit = entry },
                                    modifier = Modifier.size(34.dp).testTag("edit_entry_${entry.receiptNumber}")
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Admin Edit", tint = DairyRedPrimary, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { entryToDelete = entry },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // AI Camera Scanner Dialog
    if (showCameraScanner) {
        AiCameraScannerDialog(
            viewModel = viewModel,
            farmers = farmers,
            initialShift = shift,
            initialCategory = category,
            onDismiss = { showCameraScanner = false },
            onSaveSuccess = {
                showCameraScanner = false
            },
            onDataPopulated = { entry ->
                selectedFarmer = farmers.find { it.farmerId.equals(entry.farmerId, ignoreCase = true) }
                    ?: Farmer(
                        farmerId = entry.farmerId,
                        fullName = entry.farmerName,
                        mobileNumber = "9876543210",
                        villageAddress = "तुर्की",
                        milkCategory = entry.milkCategory
                    )
                category = entry.milkCategory
                shift = entry.shift
                fatText = "${entry.fat}"
                snfText = "${entry.snf}"
                waterText = "${entry.waterPercentage}"
                quantityText = "${entry.quantity}"
                calculatedRate = entry.rate
                totalAmount = entry.totalAmount
                machineAmt = entry.machineAmount
                isOcrScanned = true
                showCameraScanner = false
            }
        )
    }

    // Protected Admin Edit Dialog (Requires Admin PIN & logs old/new values in audit_logs)
    if (entryToEdit != null) {
        val oldEntry = entryToEdit!!
        var editWt by remember(oldEntry) { mutableStateOf(oldEntry.quantity.toString()) }
        var editFat by remember(oldEntry) { mutableStateOf(oldEntry.fat.toString()) }
        var editSnf by remember(oldEntry) { mutableStateOf(oldEntry.snf.toString()) }
        var editRate by remember(oldEntry) { mutableStateOf(oldEntry.rate.toString()) }
        var editAmt by remember(oldEntry) { mutableStateOf(oldEntry.totalAmount.toString()) }
        var adminPinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { entryToEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = DairyRedPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Admin सुरक्षित संशोधन (${oldEntry.receiptNumber})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("सत्यापित रसीद में बदलाव के लिए Admin PIN अनिवार्य है। सभी बदलाव ऑडिट लॉग में दर्ज होंगे।", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = editWt, onValueChange = { editWt = it }, label = { Text("Weight") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = editFat, onValueChange = { editFat = it }, label = { Text("FAT %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = editSnf, onValueChange = { editSnf = it }, label = { Text("SNF %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = editRate, onValueChange = { editRate = it }, label = { Text("Rate ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = editAmt, onValueChange = { editAmt = it }, label = { Text("Total Amount ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = { adminPinInput = it; pinError = null },
                        label = { Text("Admin PIN दर्ज करें (Default: 1234) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError != null,
                        modifier = Modifier.fillMaxWidth().testTag("edit_admin_pin_input")
                    )
                    if (pinError != null) {
                        Text(pinError!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = oldEntry.copy(
                            quantity = editWt.toDoubleOrNull() ?: oldEntry.quantity,
                            fat = editFat.toDoubleOrNull() ?: oldEntry.fat,
                            snf = editSnf.toDoubleOrNull() ?: oldEntry.snf,
                            rate = editRate.toDoubleOrNull() ?: oldEntry.rate,
                            totalAmount = editAmt.toDoubleOrNull() ?: oldEntry.totalAmount,
                            verifiedBy = "Admin Edited"
                        )
                        viewModel.editVerifiedCollectionWithPin(oldEntry, updated, adminPinInput) { ok, msg ->
                            if (ok) {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                entryToEdit = null
                            } else {
                                pinError = msg
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                ) { Text("अपडेट करें") }
            },
            dismissButton = {
                TextButton(onClick = { entryToEdit = null }) { Text("रद्द करें") }
            }
        )
    }

    // Delete Confirmation Dialog (Admin PIN protected for verified / OCR receipts)
    if (entryToDelete != null) {
        val entry = entryToDelete!!
        val needsPin = !entry.verifiedBy.isNullOrBlank() || entry.isOcrScanned
        var deletePin by remember { mutableStateOf("") }
        var deletePinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(if (isHindi) "रसीद हटाने की पुष्टि" else "Confirm Delete Receipt", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("क्या आप वास्तव में रसीद ${entry.receiptNumber} हटाना चाहते हैं?")
                    if (needsPin) {
                        OutlinedTextField(
                            value = deletePin,
                            onValueChange = { deletePin = it; deletePinError = null },
                            label = { Text("सत्यापित रसीद — Admin PIN दर्ज करें *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = deletePinError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (deletePinError != null) {
                            Text(deletePinError!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCollectionWithPin(entry, deletePin) { ok, msg ->
                            if (ok) {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                entryToDelete = null
                            } else {
                                deletePinError = msg
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isHindi) "हटाएं" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    // Success Receipt Dialog with WhatsApp & PDF
    if (showSuccessReceiptDialog && lastSavedEntry != null) {
        val entry = lastSavedEntry!!
        val farmerObj = farmers.find { it.farmerId == entry.farmerId }
        AlertDialog(
            onDismissRequest = { showSuccessReceiptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_namaste_india_logo),
                            contentDescription = "नमस्ते इंडिया लोगो",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "दूध पर्ची रसीद (Milk Receipt)",
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.DarkNavyTextPrimary,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.components.FarmerAvatar(
                            photoUri = farmerObj?.photoUri,
                            name = entry.farmerName,
                            size = 46.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(entry.farmerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("आईडी: ${entry.farmerId} • ${entry.milkCategory}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Surface(
                        color = com.example.ui.theme.DairyGreenContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("रसीद संख्या: ${entry.receiptNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("मात्रा: ${entry.quantity}L | Fat: ${entry.fat}% | SNF: ${entry.snf}%", fontSize = 12.sp)
                            Text("दर: ₹${entry.rate} / L", fontSize = 12.sp)
                            Text("कुल राशि: ₹${String.format("%.2f", entry.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = com.example.ui.theme.DairyGreenSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = WhatsAppHelper.formatMilkBillMessage(station, entry, farmerObj)
                        val mobile = farmerObj?.mobileNumber ?: "9876543210"
                        WhatsAppHelper.sendWhatsAppText(context, mobile, msg)
                        showSuccessReceiptDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text("WhatsApp भेजें")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val uri = PdfGenerator.generateMilkReceiptPdf(context, station, entry, farmerObj)
                        if (uri != null) {
                            WhatsAppHelper.sharePdf(context, uri, "दूध रसीद ${entry.receiptNumber}")
                        }
                        showSuccessReceiptDialog = false
                    }
                ) {
                    Text("PDF रसीद")
                }
            }
        )
    }
}
