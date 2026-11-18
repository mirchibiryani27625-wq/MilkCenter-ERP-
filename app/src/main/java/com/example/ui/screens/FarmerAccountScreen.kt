package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Farmer
import com.example.ui.components.DairyHeader
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.PdfGenerator
import com.example.util.WhatsAppHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerAccountScreen(
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
    val allCollections by viewModel.allCollections.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()

    var selectedFarmer by remember { mutableStateOf<Farmer?>(farmers.firstOrNull()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("ALL") } // "ALL", "MORNING", "EVENING", "MONTHLY"

    val farmerSummary = remember(selectedFarmer, allCollections, allPayments) {
        if (selectedFarmer != null) {
            viewModel.getFarmerAccountSummary(selectedFarmer!!.farmerId)
        } else null
    }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.FARMER_ACCOUNT,
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
            Text(
                text = if (isHindi) "किसान खाता बही (Farmer Ledger)" else "Farmer Passbook & Ledger",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Farmer Selector
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
            ) {
                OutlinedTextField(
                    value = if (selectedFarmer != null) "${selectedFarmer!!.fullName} (${selectedFarmer!!.farmerId}) - ${selectedFarmer!!.villageAddress}" else "किसान चुनें...",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isHindi) "किसान चुनें (Select Farmer)" else "Select Farmer") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .testTag("account_farmer_selector")
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    farmers.forEach { f ->
                        DropdownMenuItem(
                            text = { Text("${f.fullName} (${f.farmerId}) - ${f.mobileNumber}") },
                            onClick = {
                                selectedFarmer = f
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedFarmer != null && farmerSummary != null) {
                val f = selectedFarmer!!
                val s = farmerSummary

                // Farmer Profile Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                com.example.ui.components.FarmerAvatar(
                                    photoUri = f.photoUri,
                                    name = f.fullName,
                                    size = 54.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = f.fullName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.DarkNavyTextPrimary)
                                    Text(text = "कोड: ${f.farmerId} • 📱 ${f.mobileNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "गांव: ${f.villageAddress} • ${if (f.milkCategory == "COW") "गाय (COW)" else "भैंस (BUFFALO)"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Financial Metric Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AccountMetricItem(
                                title = "कुल दूध",
                                value = "${String.format("%.1f", s.totalMilk)} L",
                                modifier = Modifier.weight(1f)
                            )
                            AccountMetricItem(
                                title = "कुल दूध बिल",
                                value = "₹${String.format("%.2f", s.totalBill)}",
                                modifier = Modifier.weight(1f)
                            )
                            AccountMetricItem(
                                title = "प्राप्त भुगतान",
                                value = "₹${String.format("%.2f", s.totalPaid)}",
                                modifier = Modifier.weight(1f)
                            )
                            AccountMetricItem(
                                title = "अंतिम बकाया",
                                value = "₹${String.format("%.2f", s.finalBalance)}",
                                valueColor = DairyRedPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Shift breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "सुबह: ${String.format("%.1f", s.morningMilk)}L | शाम: ${String.format("%.1f", s.eveningMilk)}L",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "औसत FAT: ${String.format("%.1f", s.averageFat)}% | SNF: ${String.format("%.1f", s.averageSnf)}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: WhatsApp & PDF Statement
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val msg = WhatsAppHelper.formatStatementMessage(
                                        station, f, s.totalMilk, s.totalBill, s.totalPaid, s.finalBalance
                                    )
                                    WhatsAppHelper.sendWhatsAppText(context, f.mobileNumber, msg)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_whatsapp_statement"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                            ) {
                                Text("WhatsApp विवरण", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val uri = PdfGenerator.generateFarmerStatementPdf(
                                        context, station, f, s.collections, s.payments,
                                        s.totalMilk, s.totalBill, s.totalPaid, s.finalBalance
                                    )
                                    if (uri != null) {
                                        WhatsAppHelper.sharePdf(context, uri, "खाता विवरण ${f.fullName}")
                                    } else {
                                        Toast.makeText(context, "PDF तैयार करने में त्रुटि", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_pdf_statement"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DairyRedPrimary)
                            ) {
                                Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PDF स्टेटमेंट", fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Detailed Transaction Table + Shift / Monthly Filter Chips
                Text(
                    text = if (isHindi) "दूध संग्रहण बही व इतिहास (Farmer Milk Ledger)" else "Collection Transactions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "सभी (${s.collections.size})",
                        "MORNING" to "सुबह (${s.collections.count { it.shift == "MORNING" }})",
                        "EVENING" to "शाम (${s.collections.count { it.shift == "EVENING" }})",
                        "MONTHLY" to "मासिक रिपोर्ट"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = activeFilter == key,
                            onClick = { activeFilter = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DairyRedPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("ledger_filter_$key")
                        )
                    }
                }

                if (s.collections.isEmpty()) {
                    Text(
                        text = if (isHindi) "इस किसान का कोई दूध रिकॉर्ड उपलब्ध नहीं है।" else "No milk records for this farmer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else if (activeFilter == "MONTHLY") {
                    val monthlyGroups = s.collections.groupBy { entry ->
                        if (entry.date.length >= 7) entry.date.substring(0, 7) else "अज्ञात माह"
                    }.toSortedMap(compareByDescending { it })

                    monthlyGroups.forEach { (monthKey, itemsInMonth) ->
                        val mLiters = itemsInMonth.filter { it.shift == "MORNING" }.sumOf { it.quantity }
                        val eLiters = itemsInMonth.filter { it.shift == "EVENING" }.sumOf { it.quantity }
                        val totLiters = itemsInMonth.sumOf { it.quantity }
                        val totAmt = itemsInMonth.sumOf { it.totalAmount }
                        val avgFat = itemsInMonth.map { it.fat }.average()
                        val avgSnf = itemsInMonth.map { it.snf }.average()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("माह: $monthKey (${itemsInMonth.size} पर्चियां)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DairyRedPrimary)
                                    Text("₹${String.format("%.2f", totAmt)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2E7D32))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("कुल दूध: ${String.format("%.2f", totLiters)} L", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("सुबह: ${String.format("%.1f", mLiters)}L • शाम: ${String.format("%.1f", eLiters)}L", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "औसत FAT: ${String.format("%.2f", avgFat)}% | औसत SNF: ${String.format("%.2f", avgSnf)}%",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    val filteredCollections = when (activeFilter) {
                        "MORNING" -> s.collections.filter { it.shift == "MORNING" }
                        "EVENING" -> s.collections.filter { it.shift == "EVENING" }
                        else -> s.collections
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("दिनांक", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(82.dp))
                                Text("समय", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(66.dp))
                                Text("पारी", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(44.dp))
                                Text("पशु", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                                Text("मात्रा", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                                Text("FAT", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(46.dp))
                                Text("SNF", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(46.dp))
                                Text("पानी", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(46.dp))
                                Text("दर (₹)", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                                Text("कुल राशि", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(74.dp))
                                Text("मशीन", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                                Text("सत्यापन", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.LightGray))
                            Spacer(modifier = Modifier.height(4.dp))

                            filteredCollections.forEach { item ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(item.date, fontSize = 11.sp, modifier = Modifier.width(82.dp))
                                    Text(item.actualTime.ifBlank { "--" }, fontSize = 11.sp, modifier = Modifier.width(66.dp))
                                    Text(if (item.shift == "MORNING") "सुबह" else "शाम", fontSize = 11.sp, modifier = Modifier.width(44.dp))
                                    Text(if (item.milkCategory == "BUFFALO") "भैंस" else "गाय", fontSize = 11.sp, modifier = Modifier.width(56.dp))
                                    Text("${item.quantity}L", fontSize = 11.sp, modifier = Modifier.width(56.dp))
                                    Text("${item.fat}%", fontSize = 11.sp, modifier = Modifier.width(46.dp))
                                    Text("${item.snf}%", fontSize = 11.sp, modifier = Modifier.width(46.dp))
                                    Text("${item.waterPercentage}%", fontSize = 11.sp, modifier = Modifier.width(46.dp))
                                    Text("₹${item.rate}", fontSize = 11.sp, modifier = Modifier.width(56.dp))
                                    Text("₹${String.format("%.2f", item.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(74.dp), color = Color(0xFF2E7D32))
                                    Text(item.machineNumber ?: "--", fontSize = 11.sp, modifier = Modifier.width(70.dp))
                                    Text(
                                        text = item.verifiedBy ?: if (item.isOcrScanned) "OCR (${item.ocrConfidence}%)" else "Manual",
                                        fontSize = 10.sp,
                                        color = if (!item.verifiedBy.isNullOrBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(90.dp)
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
private fun AccountMetricItem(
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
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}
