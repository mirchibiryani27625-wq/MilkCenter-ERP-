package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Farmer
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.StationConfig
import com.example.ui.theme.DairyRedPrimary
import com.example.util.WhatsAppHelper

/**
 * Dialog 1: Today's Complete Milk Collection Report
 */
@Composable
fun TodayMilkDetailDialog(
    station: StationConfig,
    todayDate: String,
    todayEntries: List<MilkCollectionEntry>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onOpenFullReports: () -> Unit
) {
    var selectedShiftFilter by remember { mutableStateOf("ALL") }

    val filteredList = remember(todayEntries, selectedShiftFilter) {
        if (selectedShiftFilter == "ALL") todayEntries
        else todayEntries.filter { it.shift == selectedShiftFilter }
    }

    val totalLiters = filteredList.sumOf { it.quantity }
    val totalAmount = filteredList.sumOf { it.totalAmount }
    val avgFat = if (filteredList.isNotEmpty()) filteredList.map { it.fat }.average() else 0.0
    val avgSnf = if (filteredList.isNotEmpty()) filteredList.map { it.snf }.average() else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF17365D), // Deep Navy Blue
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LocalDrink, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isHindi) "आज का कुल दूध संग्रहण विवरण" else "Today's Milk Collection Report",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF172B4D)
                            )
                            Text(
                                text = "दिनांक: $todayDate • ${filteredList.size} पर्चियां",
                                fontSize = 11.sp,
                                color = Color(0xFF667085)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_today_milk_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary Stats Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F8FC))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("कुल दूध", fontSize = 11.sp, color = Color(0xFF667085))
                            Text("${String.format("%.1f", totalLiters)} L", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17365D))
                        }
                        Column {
                            Text("कुल राशि", fontSize = 11.sp, color = Color(0xFF667085))
                            Text("₹${String.format("%.2f", totalAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF239B56))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("औसत Fat / SNF", fontSize = 11.sp, color = Color(0xFF667085))
                            Text("${String.format("%.1f", avgFat)}% / ${String.format("%.1f", avgSnf)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172B4D))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Shift Filter Chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "सभी पारी", "MORNING" to "सुबह (Morning)", "EVENING" to "शाम (Evening)").forEach { (shift, label) ->
                        FilterChip(
                            selected = selectedShiftFilter == shift,
                            onClick = { selectedShiftFilter = shift },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF17365D),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Farmer-wise Records List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHindi) "आज कोई दूध संग्रहण दर्ज नहीं हुआ है।" else "No milk collections recorded today.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.receiptNumber }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (entry.shift == "MORNING") Color(0xFFFFF3E0) else Color(0xFFEDE7F6),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (entry.shift == "MORNING") "सुबह" else "शाम",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (entry.shift == "MORNING") Color(0xFFE65100) else Color(0xFF5E35B1),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${entry.farmerName} (${entry.farmerId})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "रसीद: ${entry.receiptNumber} • ${entry.milkCategory} • Fat: ${entry.fat}% | SNF: ${entry.snf}% | ₹${entry.rate}/L",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${entry.quantity} L",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "₹${String.format("%.2f", entry.totalAmount)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Full Reports Button
                Button(
                    onClick = {
                        onDismiss()
                        onOpenFullReports()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17365D))
                ) {
                    Text(if (isHindi) "विस्तृत दैनिक/मासिक रिपोर्ट खोलें (Full Report)" else "Open Full Reports", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dialog 2: Total Payable Amount & Farmer-wise Payable Breakdown
 */
@Composable
fun PayableBreakdownDialog(
    station: StationConfig,
    todayDate: String,
    todayPayableAmount: Double,
    farmers: List<Farmer>,
    todayEntries: List<MilkCollectionEntry>,
    onDismiss: () -> Unit,
    onPayFarmer: (Farmer) -> Unit,
    onOpenPaymentManagement: () -> Unit
) {
    // Farmer-wise today aggregation
    val farmerBreakdown = remember(farmers, todayEntries) {
        farmers.map { f ->
            val fEntries = todayEntries.filter { it.farmerId == f.farmerId }
            val fLiters = fEntries.sumOf { it.quantity }
            val fBill = fEntries.sumOf { it.totalAmount }
            Triple(f, fLiters, fBill)
        }.filter { it.second > 0.0 || it.third > 0.0 }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF2E7D32),
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "कुल देय राशि एवं किसान-वार हिसाब",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "आज की कुल देय राशि: ₹${String.format("%.2f", todayPayableAmount)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_payable_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Explanation Banner
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "नीचे उन सभी किसानों की सूची है जिन्होंने आज दूध जमा किया है। 'भुगतान करें' दबाकर तुरंत रसीद बनाएं:",
                            fontSize = 11.sp,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Farmers with today's bill
                if (farmerBreakdown.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "आज अभी तक किसी किसान का दूध बिल दर्ज नहीं हुआ है।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(farmerBreakdown, key = { it.first.farmerId }) { (farmer, liters, bill) ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = farmer.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "कोड: ${farmer.farmerId} • 📱 ${farmer.mobileNumber} • 📍 ${farmer.villageAddress}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "आज का दूध: ${String.format("%.1f", liters)} L",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF17365D)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₹${String.format("%.2f", bill)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF239B56)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onPayFarmer(farmer)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF239B56)),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("भुगतान करें", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onOpenPaymentManagement()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17365D))
                ) {
                    Text("भुगतान प्रबंधन (Payment Management) खोलें", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dialog 3: Shift-wise Milk Collection (Morning or Evening)
 */
@Composable
fun ShiftCollectionDetailDialog(
    shift: String, // "MORNING" or "EVENING"
    station: StationConfig,
    todayDate: String,
    todayEntries: List<MilkCollectionEntry>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onAddNewEntry: (shift: String) -> Unit
) {
    val isMorning = shift == "MORNING"
    val shiftTitle = if (isMorning) "सुबह का दूध संग्रहण (Morning Milk)" else "शाम का दूध संग्रहण (Evening Milk)"
    val shiftThemeColor = if (isMorning) Color(0xFFF57C00) else Color(0xFF5E35B1)

    val shiftEntries = remember(todayEntries, shift) {
        todayEntries.filter { it.shift == shift }
    }

    val totalLiters = shiftEntries.sumOf { it.quantity }
    val totalAmount = shiftEntries.sumOf { it.totalAmount }
    val avgFat = if (shiftEntries.isNotEmpty()) shiftEntries.map { it.fat }.average() else 0.0
    val avgSnf = if (shiftEntries.isNotEmpty()) shiftEntries.map { it.snf }.average() else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = shiftThemeColor,
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isMorning) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = shiftTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "दिनांक: $todayDate • ${shiftEntries.size} प्रविष्टियां दर्ज",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_shift_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = shiftThemeColor.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(if (isMorning) "सुबह कुल दूध" else "शाम कुल दूध", fontSize = 11.sp, color = Color.DarkGray)
                            Text("${String.format("%.1f", totalLiters)} L", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = shiftThemeColor)
                        }
                        Column {
                            Text("कुल राशि", fontSize = 11.sp, color = Color.DarkGray)
                            Text("₹${String.format("%.2f", totalAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("औसत FAT / SNF", fontSize = 11.sp, color = Color.DarkGray)
                            Text("${String.format("%.1f", avgFat)}% / ${String.format("%.1f", avgSnf)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Entries List
                if (shiftEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isMorning) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = null,
                                tint = shiftThemeColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isMorning) "आज सुबह का कोई दूध संग्रह दर्ज नहीं हुआ है।" else "आज शाम का कोई दूध संग्रह दर्ज नहीं हुआ है।",
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
                        items(shiftEntries, key = { it.receiptNumber }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "${entry.farmerName} (${entry.farmerId})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "रसीद: ${entry.receiptNumber} • ${entry.milkCategory}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Fat: ${entry.fat}% | SNF: ${entry.snf}% | ₹${entry.rate}/L",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${entry.quantity} L", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "₹${String.format("%.2f", entry.totalAmount)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Button: Add Milk Entry for this shift
                Button(
                    onClick = {
                        onDismiss()
                        onAddNewEntry(shift)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = shiftThemeColor)
                ) {
                    Text(if (isMorning) "+ सुबह दूध प्रविष्टि दर्ज करें" else "+ शाम दूध प्रविष्टि दर्ज करें", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
