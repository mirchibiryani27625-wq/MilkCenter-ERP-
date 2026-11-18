package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Farmer
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.RateChartConfig
import com.example.ui.theme.CardPureWhite
import com.example.ui.theme.DairyGreenSecondary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GreyTextSecondary
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SoftWhiteBackground
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.DuplicateStatus
import com.example.util.RateCalculator
import com.example.util.ReportVerificationOutcome
import com.example.util.VerifiedReportRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScannerSection(
    viewModel: DairyViewModel,
    scope: CoroutineScope,
    farmers: List<Farmer>,
    selectedFarmer: Farmer?,
    onSelectFarmer: (Farmer) -> Unit,
    reportNumber: String,
    reportStartDate: String,
    reportEndDate: String,
    editStationId: String,
    editFarmerCode: String,
    reportSummaryEntries: Int,
    reportAvgFat: Double,
    reportAvgSnf: Double,
    reportTotalWeight: Double,
    reportTotalAmount: Double,
    reportVerifiedRows: MutableList<VerifiedReportRow>,
    reportOutcome: ReportVerificationOutcome?,
    isBlurry: Boolean,
    persistedImagePath: String?,
    activeRateChart: RateChartConfig,
    onSaveSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var farmerDropdownOpen by remember { mutableStateOf(false) }

    val calcRowCount = reportVerifiedRows.size
    val calcWeightSum = Math.round(reportVerifiedRows.sumOf { it.row.weight } * 100.0) / 100.0
    val calcAvgFat = if (calcRowCount > 0) Math.round(reportVerifiedRows.map { it.row.fat }.average() * 100.0) / 100.0 else 0.0
    val calcAvgSnf = if (calcRowCount > 0) Math.round(reportVerifiedRows.map { it.row.snf }.average() * 100.0) / 100.0 else 0.0
    val calcAmountSum = Math.round(reportVerifiedRows.sumOf { it.row.totalAmount } * 100.0) / 100.0

    val countOk = calcRowCount > 0 && (reportSummaryEntries == 0 || calcRowCount == reportSummaryEntries)
    val weightOk = reportTotalWeight <= 0.0 || abs(calcWeightSum - reportTotalWeight) <= 0.05
    val fatOk = reportAvgFat <= 0.0 || abs(calcAvgFat - reportAvgFat) <= 0.15
    val snfOk = reportAvgSnf <= 0.0 || abs(calcAvgSnf - reportAvgSnf) <= 0.15
    val amtOk = reportTotalAmount <= 0.0 || calcAmountSum <= 0.0 || abs(calcAmountSum - reportTotalAmount) <= 1.0
    val isLiveReconciled = countOk && weightOk && fatOk && snfOk && amtOk

    val liveErrors = remember(calcRowCount, reportSummaryEntries, calcWeightSum, reportTotalWeight, calcAvgFat, reportAvgFat, calcAvgSnf, reportAvgSnf, calcAmountSum, reportTotalAmount) {
        buildList {
            if (reportSummaryEntries > 0 && calcRowCount != reportSummaryEntries) {
                add("पंक्ति संख्या असमान: रिपोर्ट $reportSummaryEntries ≠ पढ़ी गई $calcRowCount")
            }
            if (reportTotalWeight > 0.0 && abs(calcWeightSum - reportTotalWeight) > 0.05) {
                add("वजन योग असमान: ${calcWeightSum}L ≠ ${reportTotalWeight}L")
            }
            if (reportAvgFat > 0.0 && abs(calcAvgFat - reportAvgFat) > 0.15) {
                add("FAT औसत असमान: $calcAvgFat% ≠ $reportAvgFat%")
            }
            if (reportAvgSnf > 0.0 && abs(calcAvgSnf - reportAvgSnf) > 0.15) {
                add("SNF औसत असमान: $calcAvgSnf% ≠ $reportAvgSnf%")
            }
            if (reportTotalAmount > 0.0 && calcAmountSum > 0.0 && abs(calcAmountSum - reportTotalAmount) > 1.0) {
                add("राशि योग असमान: ₹$calcAmountSum ≠ ₹$reportTotalAmount")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardPureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "मासिक ग्राहक रिपोर्ट #${reportNumber.ifBlank { "--" }} ($calcRowCount पंक्तियां)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary
            )

            if (reportStartDate.isNotBlank() || reportEndDate.isNotBlank()) {
                Text(
                    text = "अवधि: $reportStartDate से $reportEndDate • स्टेशन: ${editStationId.ifBlank { "NI-TURKI-01" }}",
                    fontSize = 11.sp,
                    color = GreyTextSecondary
                )
            }

            ExposedDropdownMenuBox(
                expanded = farmerDropdownOpen,
                onExpandedChange = { farmerDropdownOpen = it }
            ) {
                OutlinedTextField(
                    value = if (selectedFarmer != null) "${selectedFarmer.fullName} (${selectedFarmer.farmerId})" else "अज्ञात कोड ($editFarmerCode) — किसान चुनें *",
                    onValueChange = {},
                    readOnly = true,
                    isError = selectedFarmer == null && reportVerifiedRows.isNotEmpty(),
                    label = { Text("किसान खाता चुनें (Farmer Account) *") },
                    leadingIcon = {
                        selectedFarmer?.let { FarmerAvatar(photoUri = it.photoUri, name = it.fullName, size = 26.dp) }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmerDropdownOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = farmerDropdownOpen,
                    onDismissRequest = { farmerDropdownOpen = false }
                ) {
                    farmers.forEach { f ->
                        DropdownMenuItem(
                            text = { Text("${f.fullName} (${f.farmerId})") },
                            onClick = {
                                onSelectFarmer(f)
                                farmerDropdownOpen = false
                            }
                        )
                    }
                }
            }

            if (calcRowCount > 0 || reportSummaryEntries > 0) {
                MonthlyReportReconciliationCard(
                    parsedRowCount = calcRowCount,
                    printedTotalEntries = reportSummaryEntries,
                    calcAvgFat = calcAvgFat,
                    printedAvgFat = reportAvgFat,
                    calcAvgSnf = calcAvgSnf,
                    printedAvgSnf = reportAvgSnf,
                    calcWeight = calcWeightSum,
                    printedWeight = reportTotalWeight,
                    calcAmount = calcAmountSum,
                    printedAmount = reportTotalAmount,
                    isFullyReconciled = isLiveReconciled,
                    reconciliationErrors = liveErrors
                )
            }

            reportVerifiedRows.forEachIndexed { idx, vRow ->
                val row = vRow.row
                val isDup = vRow.duplicateStatus != DuplicateStatus.NONE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDup) ErrorRed.copy(alpha = 0.08f) else SoftWhiteBackground
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = if (row.shift == "MORNING") Color(0xFFFFF3E0) else Color(0xFFEDE7F6),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (row.shift == "MORNING") "सुबह" else "शाम",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.shift == "MORNING") Color(0xFFE65100) else Color(0xFF5E35B1),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("${vRow.normalizedDate ?: row.date} • ${row.weight}L", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (isDup) {
                                    Text("⚠️ पहले से दर्ज: ${vRow.existingReceiptNumber}", fontSize = 9.sp, color = ErrorRed)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("F:${row.fat} S:${row.snf}", fontSize = 11.sp, color = GreyTextSecondary)
                            IconButton(
                                onClick = { reportVerifiedRows.removeAt(idx) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Row", tint = ErrorRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            val canImportFullReport = selectedFarmer != null && calcRowCount > 0 && !isBlurry && isLiveReconciled

            if (!isLiveReconciled && calcRowCount > 0) {
                OutlinedButton(
                    onClick = {
                        reportOutcome?.scanRecord?.let { rec ->
                            viewModel.recordScanOnly(rec.copy(verificationStatus = "PARTIAL_MISMATCH")) {
                                Toast.makeText(context, "रिपोर्ट असमानता कारण PARTIAL_MISMATCH के रूप में कतार में भेजी गई", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Admin जांच के लिए भेजें (PARTIAL_MISMATCH)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                enabled = canImportFullReport,
                onClick = {
                    val farmer = selectedFarmer!!
                    scope.launch {
                        val entriesToSave = reportVerifiedRows.mapIndexed { idx, vRow ->
                            val row = vRow.row
                            val rowDate = vRow.normalizedDate ?: viewModel.todayDate
                            val parts = rowDate.split("-")
                            val calc = if (row.rate > 0.0) null else RateCalculator.calculate(row.weight, row.fat, row.snf, 0.0, activeRateChart, null)
                            val rRate = if (row.rate > 0.0) row.rate else calc!!.ratePerLiter
                            val rAmt = if (row.totalAmount > 0.0) row.totalAmount else calc!!.totalAmount
                            MilkCollectionEntry(
                                receiptNumber = "RCP-REP${reportNumber.ifBlank { "0" }}-${rowDate.replace("-", "")}-${row.shift.take(1)}-${idx + 1}",
                                date = rowDate,
                                timestamp = System.currentTimeMillis() + idx * 500,
                                shift = row.shift,
                                farmerId = farmer.farmerId,
                                farmerName = farmer.fullName,
                                milkCategory = farmer.milkCategory,
                                quantity = row.weight,
                                fat = row.fat,
                                snf = row.snf,
                                waterPercentage = 0.0,
                                rate = rRate,
                                totalAmount = rAmt,
                                scannedImageUri = persistedImagePath,
                                isOcrScanned = true,
                                ocrConfidence = reportOutcome?.scanRecord?.overallConfidence ?: 92,
                                machineNumber = "REP-$reportNumber",
                                verifiedBy = viewModel.userSession.value.userName.ifBlank { "Admin" },
                                stationId = editStationId.ifBlank { null },
                                month = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                                year = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                                sourceType = "MONTHLY_REPORT",
                                verificationStatus = "FULLY_IMPORTED"
                            )
                        }
                        reportOutcome?.scanRecord?.let { rec ->
                            viewModel.recordScanOnly(rec.copy(verificationStatus = "FULLY_IMPORTED", verifiedBy = "Monthly Import"))
                        }
                        viewModel.saveMilkEntries(entriesToSave) {
                            Toast.makeText(context, "रिपोर्ट की सभी ${entriesToSave.size} प्रविष्टियां सत्यापित आयात हुईं!", Toast.LENGTH_LONG).show()
                            onSaveSuccess()
                            onDismiss()
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DairyGreenSecondary),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ocr_save_all_rows_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("पुष्टि व पूर्ण आयात करें ($calcRowCount प्रविष्टियां)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
