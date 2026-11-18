package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.Farmer
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.RateChartConfig
import com.example.data.model.ScanRecord
import com.example.ui.components.CameraXLiveScanner
import com.example.ui.components.FarmerAvatar
import com.example.ui.components.MonthlyReportScannerSection
import com.example.ui.components.ScanActionFooterButtons
import com.example.ui.components.ScanConfirmationFieldsTable
import com.example.ui.components.ScannerImagePreviewAndToolsCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardPureWhite
import com.example.ui.theme.CowGreen
import com.example.ui.theme.DairyGreenSecondary
import com.example.ui.theme.DarkNavyTextPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GreyTextSecondary
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SoftWhiteBackground
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.ConfidenceTier
import com.example.util.DuplicateStatus
import com.example.util.FieldValidationStatus
import com.example.util.NormalizedCropRect
import com.example.util.OcrFieldDetail
import com.example.util.OcrParser
import com.example.util.OcrScanResult
import com.example.util.ReportVerificationOutcome
import com.example.util.ScanType
import com.example.util.ScanVerificationEngine
import com.example.util.ScanVerificationOutcome
import com.example.util.VerifiedReportRow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiCameraScannerDialog(
    viewModel: DairyViewModel,
    farmers: List<Farmer>,
    initialShift: String = "MORNING",
    initialCategory: String = "COW",
    onDismiss: () -> Unit,
    onSaveSuccess: () -> Unit = {},
    onDataPopulated: (MilkCollectionEntry) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stationConfig by viewModel.stationConfig.collectAsState()

    var selectedScanType by remember { mutableStateOf(ScanType.MACHINE_DISPLAY) }
    var showLiveViewfinder by remember { mutableStateOf(false) }

    var currentImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var persistedImagePath by remember { mutableStateOf<String?>(null) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var previewZoom by remember { mutableFloatStateOf(1f) }
    var cropRect by remember { mutableStateOf(NormalizedCropRect()) }
    var isContrastEnhanced by remember { mutableStateOf(true) }
    var brightnessOffset by remember { mutableFloatStateOf(0f) }
    var contrastScale by remember { mutableFloatStateOf(1.38f) }
    var showRawOcrText by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var ocrResult by remember { mutableStateOf<OcrScanResult?>(null) }
    var verificationOutcome by remember { mutableStateOf<ScanVerificationOutcome?>(null) }
    var reportOutcome by remember { mutableStateOf<ReportVerificationOutcome?>(null) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    var rateChartCow by remember { mutableStateOf(RateChartConfig("COW", 38.0, 3.5, 5.5, 8.5, 3.0)) }
    var rateChartBuf by remember { mutableStateOf(RateChartConfig("BUFFALO", 55.0, 6.0, 7.0, 9.0, 4.0)) }
    LaunchedEffect(Unit) {
        rateChartCow = viewModel.getRateChartForCategory("COW")
        rateChartBuf = viewModel.getRateChartForCategory("BUFFALO")
    }

    var selectedFarmer by remember { mutableStateOf<Farmer?>(null) }
    var farmerDropdownOpen by remember { mutableStateOf(false) }
    var editFarmerName by remember { mutableStateOf("") }
    var editFarmerCode by remember { mutableStateOf("") }
    var editMachineId by remember { mutableStateOf("") }
    var editStationId by remember { mutableStateOf("") }
    var editTransactionId by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editTime by remember { mutableStateOf("") }
    var editShift by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf(initialCategory) }
    var editFat by remember { mutableStateOf("") }
    var editSnf by remember { mutableStateOf("") }
    var editWater by remember { mutableStateOf("") }
    var editWeight by remember { mutableStateOf("") }
    var editRate by remember { mutableStateOf("") }
    var editTotalAmount by remember { mutableStateOf("") }
    var machinePrintedAmount by remember { mutableStateOf<Double?>(null) }

    val reportVerifiedRows = remember { mutableStateListOf<VerifiedReportRow>() }
    var reportNumber by remember { mutableStateOf("") }
    var reportStartDate by remember { mutableStateOf("") }
    var reportEndDate by remember { mutableStateOf("") }
    var reportSummaryEntries by remember { mutableIntStateOf(0) }
    var reportAvgFat by remember { mutableDoubleStateOf(0.0) }
    var reportAvgSnf by remember { mutableDoubleStateOf(0.0) }
    var reportTotalWeight by remember { mutableDoubleStateOf(0.0) }
    var reportTotalAmount by remember { mutableDoubleStateOf(0.0) }

    fun resetScanState() {
        ocrResult = null
        verificationOutcome = null
        reportOutcome = null
        selectedFarmer = null
        editFarmerName = ""
        editFarmerCode = ""
        editMachineId = ""
        editStationId = ""
        editTransactionId = ""
        editDate = ""
        editTime = ""
        editShift = ""
        editFat = ""
        editSnf = ""
        editWater = ""
        editWeight = ""
        editRate = ""
        editTotalAmount = ""
        machinePrintedAmount = null
        reportVerifiedRows.clear()
        isEditMode = false
    }

    suspend fun handleVerifiedScan(res: OcrScanResult, scanMode: String, sourcePath: String?) {
        ocrResult = res
        selectedScanType = res.scanType
        if (res.scanType == ScanType.CUSTOMER_REPORT && res.customerReport != null) {
            val rOutcome = viewModel.verifyReportOcrResult(res, sourcePath)
            reportOutcome = rOutcome
            verificationOutcome = null
            val rep = res.customerReport
            reportNumber = rep.reportNumber ?: "NA"
            reportStartDate = rep.startDate ?: ""
            reportEndDate = rep.endDate ?: ""
            reportVerifiedRows.clear()
            reportVerifiedRows.addAll(rOutcome.verifiedRows)
            reportSummaryEntries = rep.totalEntries
            reportAvgFat = rep.averageFat
            reportAvgSnf = rep.averageSnf
            reportTotalWeight = rep.totalWeight
            reportTotalAmount = rep.totalAmount
            selectedFarmer = rOutcome.customerResolution.matchedFarmer
            editFarmerCode = rep.farmerCode ?: "Not Readable"
            editFarmerName = selectedFarmer?.fullName ?: (rep.farmerName ?: "Not Readable")
            editStationId = rep.stationId ?: res.stationId ?: ""
        } else {
            val outcome = viewModel.verifyOcrResult(res, scanMode, sourcePath)
            verificationOutcome = outcome
            reportOutcome = null
            selectedFarmer = outcome.customerResolution.matchedFarmer
            editFarmerCode = res.farmerCode ?: ""
            editFarmerName = selectedFarmer?.fullName ?: (res.farmerName ?: "")
            editMachineId = res.machineNumber ?: ""
            editStationId = res.stationId ?: ""
            editTransactionId = res.transactionId ?: ""
            editCategory = res.milkType ?: selectedFarmer?.milkCategory ?: initialCategory
            editDate = outcome.normalizedDate ?: res.detectedDate ?: ""
            editTime = res.detectedTime ?: ""
            editShift = res.shift ?: ""
            editFat = res.fat?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            editSnf = res.snf?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            editWater = res.water?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            editWeight = res.quantity?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            editRate = res.rate?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            editTotalAmount = res.totalAmount?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            machinePrintedAmount = res.totalAmount
            // Automatically enter edit mode if critical values were missing/unreadable
            isEditMode = outcome.scanRecord.verificationStatus != "AUTO_VERIFIED" || selectedFarmer == null
        }
    }

    fun scanModeTag(type: ScanType): String = when (type) {
        ScanType.MACHINE_DISPLAY -> "LIVE_DISPLAY"
        ScanType.INDIVIDUAL_RECEIPT -> "PHOTO_DISPLAY"
        ScanType.CUSTOMER_REPORT -> "CUSTOMER_REPORT"
    }

    fun reprocessCurrentImage() {
        val bmp = capturedBitmap
        val uri = currentImageUri
        if (bmp == null && uri == null) return
        isProcessing = true
        scope.launch {
            val res = if (bmp != null) {
                OcrParser.processBitmap(bmp, rotationAngle, isContrastEnhanced, brightnessOffset, selectedScanType, contrastScale, cropRect, stationConfig)
            } else {
                OcrParser.processImage(context, uri!!, rotationAngle, isContrastEnhanced, brightnessOffset, selectedScanType, contrastScale, cropRect, stationConfig)
            }
            handleVerifiedScan(res, scanModeTag(selectedScanType), persistedImagePath)
            isProcessing = false
        }
    }

    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraImageUri != null) {
            currentImageUri = tempCameraImageUri
            capturedBitmap = null
            rotationAngle = 0f
            previewZoom = 1f
            cropRect = NormalizedCropRect()
            isProcessing = true
            scope.launch {
                val savedPath = viewModel.persistSourceImageUri(tempCameraImageUri, "camera_scan")
                persistedImagePath = savedPath
                val res = OcrParser.processImage(context, tempCameraImageUri!!, rotationAngle, isContrastEnhanced, brightnessOffset, selectedScanType, contrastScale, cropRect, stationConfig)
                handleVerifiedScan(res, scanModeTag(selectedScanType), savedPath)
                isProcessing = false
            }
        }
    }

    fun launchCameraDirectly() {
        try {
            val storageDir = File(context.cacheDir, "camera_scans").apply { if (!exists()) mkdirs() }
            val photoFile = File(storageDir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            tempCameraImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "कैमरा शुरू करने में विफल: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    var pendingCameraAction by remember { mutableStateOf<String?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDeniedMessage = null
            if (pendingCameraAction == "LIVE") showLiveViewfinder = true else launchCameraDirectly()
        } else {
            showLiveViewfinder = false
            permissionDeniedMessage = "कैमरा अनुमति आवश्यक है। कृपया अनुमति दें या गैलरी से फोटो चुनें।"
        }
        pendingCameraAction = null
    }

    fun requestCamera(mode: String) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDeniedMessage = null
            if (mode == "LIVE") showLiveViewfinder = true else launchCameraDirectly()
        } else {
            pendingCameraAction = mode
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            currentImageUri = uri
            capturedBitmap = null
            rotationAngle = 0f
            previewZoom = 1f
            cropRect = NormalizedCropRect()
            isProcessing = true
            scope.launch {
                val savedPath = viewModel.persistSourceImageUri(uri, "gallery_scan")
                persistedImagePath = savedPath
                val res = OcrParser.processImage(context, uri, rotationAngle, isContrastEnhanced, brightnessOffset, selectedScanType, contrastScale, cropRect, stationConfig)
                handleVerifiedScan(res, scanModeTag(selectedScanType), savedPath)
                isProcessing = false
            }
        }
    }

    fun runPreset(text: String, type: ScanType) {
        selectedScanType = type
        currentImageUri = null
        capturedBitmap = null
        persistedImagePath = null
        isProcessing = true
        scope.launch {
            val res = OcrParser.parseExtractedText(text, type, stationConfig = stationConfig)
            handleVerifiedScan(res, scanModeTag(type), null)
            isProcessing = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = SoftWhiteBackground,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(color = NavyPrimary, shape = CircleShape, modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("AI स्मार्ट कैमरा स्कैनर", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavyTextPrimary)
                            Text("Everest डिस्प्ले • दूध पर्ची • मासिक रिपोर्ट", fontSize = 11.sp, color = GreyTextSecondary)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_ai_scanner_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkNavyTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3 Large Mobile-Friendly Scan Type Options (Hindi + English)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple(ScanType.MACHINE_DISPLAY, "1. मशीन डिस्प्ले", Icons.Default.Monitor),
                        Triple(ScanType.INDIVIDUAL_RECEIPT, "2. दूध पर्ची", Icons.AutoMirrored.Filled.ReceiptLong),
                        Triple(ScanType.CUSTOMER_REPORT, "3. मासिक रिपोर्ट", Icons.Default.Description)
                    ).forEach { (type, title, icon) ->
                        val selected = selectedScanType == type
                        Button(
                            onClick = {
                                selectedScanType = type
                                if (type != ScanType.MACHINE_DISPLAY) showLiveViewfinder = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) NavyPrimary else CardPureWhite,
                                contentColor = if (selected) Color.White else DarkNavyTextPrimary
                            ),
                            border = if (selected) null else BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("scan_option_${type.name.lowercase()}")
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showLiveViewfinder) {
                        CameraXLiveScanner(
                            onBitmapCaptured = { bitmap ->
                                showLiveViewfinder = false
                                capturedBitmap = bitmap
                                currentImageUri = null
                                rotationAngle = 0f
                                previewZoom = 1f
                                cropRect = NormalizedCropRect()
                                isProcessing = true
                                scope.launch {
                                    val savedPath = viewModel.persistSourceBitmap(bitmap, "live_scan")
                                    persistedImagePath = savedPath
                                    val res = OcrParser.processBitmap(bitmap, rotationAngle, isContrastEnhanced, brightnessOffset, selectedScanType, contrastScale, cropRect, stationConfig)
                                    handleVerifiedScan(res, scanModeTag(selectedScanType), savedPath)
                                    isProcessing = false
                                }
                            },
                            onClose = { showLiveViewfinder = false },
                            onError = { msg -> permissionDeniedMessage = msg }
                        )
                    }

                    // Capture & Sample Bar
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardPureWhite)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            permissionDeniedMessage?.let {
                                Text(it, fontSize = 11.sp, color = ErrorRed, fontWeight = FontWeight.Medium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { requestCamera("LIVE") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("ai_live_camera_btn")
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live कैमरा", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { requestCamera("PHOTO") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28558A)),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("ai_camera_capture_btn")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("फोटो लें", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DairyGreenSecondary),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("ai_gallery_pick_btn")
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("गैलरी", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Text("परीक्षण टेम्पलेट (Verify Parser):", fontSize = 10.sp, color = GreyTextSecondary, fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        runPreset(
                                            "EVEREST DPU\nM_ID: MC-01\nF_ID: F001\nDATE: 14/11/2026\nTIME: 06:20 PM\nFAT: 6.4\nSNF: 9.1\nWATER: 0.0%\nWT: 8.50\nRATE: 60.00\nAMT: 510.00\nTXN_ID: TXN-8841",
                                            ScanType.MACHINE_DISPLAY
                                        )
                                    },
                                    label = { Text("Everest डिस्प्ले", fontSize = 10.sp) },
                                    modifier = Modifier.testTag("test_display_lactoscan")
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        runPreset(
                                            "PRAMOD SINAH\nSTN_ID: NI-TURKI-01\nSTN_NAME: NAMASTE INDIA\nDATE: 12/11/2026\nTIME: 07:45 AM\nCODE: F001\nFAT: 3.6\nSNF: 7.9\nADD. WATER: 19%\nM_TYPE: COW\nWT: 2.92\nRT/LT: 35.47\nDUPLICATE: NO\nRS: 103.57",
                                            ScanType.INDIVIDUAL_RECEIPT
                                        )
                                    },
                                    label = { Text("दूध पर्ची (103.57₹)", fontSize = 10.sp) },
                                    modifier = Modifier.testTag("test_slip_pramod")
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        runPreset(
                                            "CUSTOMER REPORT 028\nFARMER CODE: F001\nDATE | FAT | SNF | WEIGHT\n01/11 M 03.4 07.3 008.41\n01/11 E 03.0 07.6 002.56\n02/11 M 03.5 07.5 007.20\n02/11 E 03.8 07.7 006.10\n03/11 M 03.6 07.4 007.50\n03/11 E 03.7 07.8 005.80\n04/11 M 03.5 07.6 006.90\n04/11 E 03.9 07.7 006.30\n05/11 M 03.6 07.5 007.10\n05/11 E 03.8 07.6 005.90\n06/11 M 03.7 07.7 007.30\n06/11 E 03.5 07.5 006.20\n07/11 M 03.6 07.6 007.40\n07/11 E 03.8 07.7 005.76\nTOTAL ENTRIES: 14\nAVERAGE FAT: 3.60\nAVERAGE SNF: 7.58\nTOTAL WEIGHT: 90.43\nTOTAL AMOUNT: 3138.07",
                                            ScanType.CUSTOMER_REPORT
                                        )
                                    },
                                    label = { Text("मासिक रिपोर्ट (14 Rows)", fontSize = 10.sp) },
                                    modifier = Modifier.testTag("test_report_customer")
                                )
                            }
                        }
                    }

                    if (isProcessing) {
                        Surface(color = NavyPrimary.copy(alpha = 0.09f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("OCR पहचान, शिफ्ट व डेटा सत्यापन जारी...", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                            }
                        }
                    }

                    if (capturedBitmap != null || currentImageUri != null) {
                        ScannerImagePreviewAndToolsCard(
                            capturedBitmap = capturedBitmap,
                            currentImageUri = currentImageUri,
                            imageQuality = ocrResult?.imageQuality,
                            rotationAngle = rotationAngle,
                            previewZoom = previewZoom,
                            cropRect = cropRect,
                            isContrastEnhanced = isContrastEnhanced,
                            brightnessOffset = brightnessOffset,
                            contrastScale = contrastScale,
                            onRotate = { rotationAngle = (rotationAngle + 90f) % 360f; reprocessCurrentImage() },
                            onZoomChange = { previewZoom = it },
                            onCropChange = { cropRect = it; reprocessCurrentImage() },
                            onToggleContrast = { isContrastEnhanced = !isContrastEnhanced; reprocessCurrentImage() },
                            onBrightnessChange = { brightnessOffset = it },
                            onBrightnessFinished = { reprocessCurrentImage() },
                            onContrastScaleChange = { contrastScale = it },
                            onContrastFinished = { reprocessCurrentImage() },
                            onRescan = { reprocessCurrentImage() }
                        )
                    }

                    if (selectedScanType == ScanType.CUSTOMER_REPORT && (reportVerifiedRows.isNotEmpty() || reportOutcome != null)) {
                        MonthlyReportScannerSection(
                            viewModel = viewModel, scope = scope, farmers = farmers,
                            selectedFarmer = selectedFarmer,
                            onSelectFarmer = { f -> selectedFarmer = f; editFarmerCode = f.farmerId; editFarmerName = f.fullName },
                            reportNumber = reportNumber, reportStartDate = reportStartDate, reportEndDate = reportEndDate,
                            editStationId = editStationId, editFarmerCode = editFarmerCode,
                            reportSummaryEntries = reportSummaryEntries, reportAvgFat = reportAvgFat,
                            reportAvgSnf = reportAvgSnf, reportTotalWeight = reportTotalWeight,
                            reportTotalAmount = reportTotalAmount, reportVerifiedRows = reportVerifiedRows,
                            reportOutcome = reportOutcome, isBlurry = ocrResult?.imageQuality?.isBlurry == true,
                            persistedImagePath = persistedImagePath,
                            activeRateChart = if (editCategory == "BUFFALO") rateChartBuf else rateChartCow,
                            onSaveSuccess = onSaveSuccess, onDismiss = onDismiss
                        )
                    } else if (verificationOutcome != null || ocrResult != null) {
                        val outcome = verificationOutcome
                        val normDate = ScanVerificationEngine.normalizeDateToIso(editDate)
                        val wVal = editWeight.toDoubleOrNull()
                        val rVal = editRate.toDoubleOrNull()
                        val amtVal = editTotalAmount.toDoubleOrNull()
                        val fatVal = editFat.toDoubleOrNull()
                        val snfVal = editSnf.toDoubleOrNull()
                        val waterVal = editWater.toDoubleOrNull() ?: 0.0
                        val expAmt = if (wVal != null && rVal != null) Math.round(wVal * rVal * 100.0) / 100.0 else null
                        val amtDiff = if (expAmt != null && amtVal != null) abs(expAmt - amtVal) else null
                        val liveAmountMismatch = amtDiff != null && amtDiff > stationConfig.amountToleranceRs

                        val liveFields = listOf(
                            OcrFieldDetail("farmerId", "Farmer ID", editFarmerCode.ifBlank { null }, selectedFarmer?.farmerId ?: editFarmerCode.ifBlank { "Not Readable" }, outcome?.fieldConfidences?.customerId ?: 0, outcome?.fieldDetails?.find { it.key == "farmerId" }?.sourceLocation ?: "OCR", if (selectedFarmer == null) FieldValidationStatus.UNMATCHED else FieldValidationStatus.VALID),
                            OcrFieldDetail("farmerName", "Farmer Name", selectedFarmer?.fullName ?: editFarmerName.ifBlank { null }, selectedFarmer?.fullName ?: editFarmerName.ifBlank { "Not Readable" }, if (selectedFarmer != null) 98 else 0, "Farmer Master", if (selectedFarmer == null && editFarmerName.isBlank()) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID),
                            OcrFieldDetail("date", "Date", normDate ?: editDate.ifBlank { null }, normDate ?: editDate.ifBlank { "Not Readable" }, outcome?.fieldConfidences?.date ?: 0, outcome?.fieldDetails?.find { it.key == "date" }?.sourceLocation ?: "OCR", if (normDate == null) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID),
                            OcrFieldDetail("time", "Time", editTime.ifBlank { null }, editTime.ifBlank { "Not Readable" }, outcome?.fieldConfidences?.time ?: 0, outcome?.fieldDetails?.find { it.key == "time" }?.sourceLocation ?: "OCR", if (editTime.isBlank()) FieldValidationStatus.WARNING else FieldValidationStatus.VALID),
                            OcrFieldDetail("shift", "Shift", editShift.ifBlank { null }, editShift.ifBlank { "Not Readable" }, outcome?.fieldConfidences?.shift ?: 0, outcome?.fieldDetails?.find { it.key == "shift" }?.sourceLocation ?: "Time Rule", if (editShift != "MORNING" && editShift != "EVENING") FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID),
                            OcrFieldDetail("fat", "FAT %", fatVal?.toString(), fatVal?.toString() ?: "Not Readable", outcome?.fieldConfidences?.fat ?: 0, outcome?.fieldDetails?.find { it.key == "fat" }?.sourceLocation ?: "OCR", if (fatVal == null) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID),
                            OcrFieldDetail("snf", "SNF %", snfVal?.toString(), snfVal?.toString() ?: "Not Readable", outcome?.fieldConfidences?.snf ?: 0, outcome?.fieldDetails?.find { it.key == "snf" }?.sourceLocation ?: "OCR", if (snfVal == null) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID),
                            OcrFieldDetail("water", "Water %", editWater.toDoubleOrNull()?.toString(), editWater.ifBlank { "Not Readable" }, outcome?.fieldConfidences?.water ?: 0, outcome?.fieldDetails?.find { it.key == "water" }?.sourceLocation ?: "OCR", if (editWater.isBlank()) FieldValidationStatus.WARNING else FieldValidationStatus.VALID),
                            OcrFieldDetail("rate", "Rate (₹/L)", rVal?.toString(), rVal?.toString() ?: "Not Readable", outcome?.fieldConfidences?.rate ?: 0, outcome?.fieldDetails?.find { it.key == "rate" }?.sourceLocation ?: "OCR", if (rVal == null) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID),
                            OcrFieldDetail("weight", "Weight (L)", wVal?.toString(), wVal?.toString() ?: "Not Readable", outcome?.fieldConfidences?.weight ?: 0, outcome?.fieldDetails?.find { it.key == "weight" }?.sourceLocation ?: "OCR", if (wVal == null) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID),
                            OcrFieldDetail("amount", "Total Amount", amtVal?.toString(), amtVal?.let { "₹$it" } ?: "Not Readable", outcome?.fieldConfidences?.amount ?: 0, outcome?.fieldDetails?.find { it.key == "amount" }?.sourceLocation ?: "OCR", when { amtVal == null -> FieldValidationStatus.UNREADABLE; liveAmountMismatch -> FieldValidationStatus.MISMATCH; else -> FieldValidationStatus.VALID }, if (liveAmountMismatch) "Expected ₹$expAmt ≠ ₹$amtVal" else null)
                        )

                        outcome?.let { out ->
                            if (out.duplicateCheck.status != DuplicateStatus.NONE) {
                                Surface(
                                    color = (if (out.duplicateCheck.status == DuplicateStatus.SEPARATE_TRANSACTION) AmberWarning else ErrorRed).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (out.duplicateCheck.status == DuplicateStatus.SEPARATE_TRANSACTION) AmberWarning else ErrorRed),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = if (out.duplicateCheck.status == DuplicateStatus.SEPARATE_TRANSACTION) AmberWarning else ErrorRed, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(out.duplicateCheck.warningMessage ?: "", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        ScanConfirmationFieldsTable(
                            fields = liveFields,
                            isEditMode = isEditMode,
                            onToggleEditMode = { isEditMode = !isEditMode }
                        )

                        if (isEditMode) {
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardPureWhite)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExposedDropdownMenuBox(expanded = farmerDropdownOpen, onExpandedChange = { farmerDropdownOpen = it }) {
                                        OutlinedTextField(
                                            value = selectedFarmer?.let { "${it.fullName} (${it.farmerId})" } ?: "किसान चुनें (Unmatched) *",
                                            onValueChange = {},
                                            readOnly = true,
                                            isError = selectedFarmer == null,
                                            label = { Text("किसान चुनें (Farmer ID Match) *") },
                                            leadingIcon = { selectedFarmer?.let { FarmerAvatar(it.photoUri, it.fullName, 24.dp) } },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(farmerDropdownOpen) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                        )
                                        ExposedDropdownMenu(expanded = farmerDropdownOpen, onDismissRequest = { farmerDropdownOpen = false }) {
                                            farmers.forEach { f ->
                                                DropdownMenuItem(text = { Text("${f.fullName} (${f.farmerId})") }, onClick = {
                                                    selectedFarmer = f; editFarmerCode = f.farmerId; editFarmerName = f.fullName; farmerDropdownOpen = false
                                                })
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = editDate, onValueChange = { editDate = it }, label = { Text("दिनांक YYYY-MM-DD *") }, isError = normDate == null, modifier = Modifier.weight(1f).testTag("ocr_edit_date"))
                                        OutlinedTextField(value = editTime, onValueChange = { editTime = it }, label = { Text("समय (Time)") }, modifier = Modifier.weight(1f).testTag("ocr_edit_time"))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("MORNING" to "सुबह", "EVENING" to "शाम").forEach { (sh, lbl) ->
                                            FilterChip(selected = editShift == sh, onClick = { editShift = sh }, label = { Text(lbl, fontSize = 11.sp) })
                                        }
                                        listOf("COW" to "गाय", "BUFFALO" to "भैंस").forEach { (c, lbl) ->
                                            FilterChip(selected = editCategory == c, onClick = { editCategory = c }, label = { Text(lbl, fontSize = 11.sp) })
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = editFat, onValueChange = { editFat = it }, label = { Text("FAT % *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = fatVal == null, modifier = Modifier.weight(1f).testTag("ocr_edit_fat"))
                                        OutlinedTextField(value = editSnf, onValueChange = { editSnf = it }, label = { Text("SNF % *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = snfVal == null, modifier = Modifier.weight(1f).testTag("ocr_edit_snf"))
                                        OutlinedTextField(value = editWater, onValueChange = { editWater = it }, label = { Text("Water %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("ocr_edit_water"))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = editWeight, onValueChange = { editWeight = it }, label = { Text("Weight L *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = (wVal ?: 0.0) <= 0.0, modifier = Modifier.weight(1f).testTag("ocr_edit_weight"))
                                        OutlinedTextField(value = editRate, onValueChange = { editRate = it }, label = { Text("Rate ₹ *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = (rVal ?: 0.0) <= 0.0, modifier = Modifier.weight(1f).testTag("ocr_edit_rate"))
                                        OutlinedTextField(value = editTotalAmount, onValueChange = { editTotalAmount = it }, label = { Text("Total ₹ *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = (amtVal ?: 0.0) <= 0.0, modifier = Modifier.weight(1f).testTag("ocr_edit_amount"))
                                    }
                                }
                            }
                        }

                        if (!outcome?.scanRecord?.rawOcrText.isNullOrBlank()) {
                            TextButton(onClick = { showRawOcrText = !showRawOcrText }) {
                                Text(if (showRawOcrText) "Raw OCR छिपाएं" else "Raw OCR टेक्स्ट देखें", fontSize = 11.sp)
                            }
                            if (showRawOcrText) {
                                Surface(color = CardPureWhite, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(outcome!!.scanRecord.rawOcrText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                                }
                            }
                        }

                        val isExactDup = outcome?.duplicateCheck?.status == DuplicateStatus.EXACT_DUPLICATE
                        val isBlurry = ocrResult?.imageQuality?.isBlurry == true
                        val canConfirmSave = selectedFarmer != null && !isExactDup && !isBlurry &&
                            normDate != null && (editShift == "MORNING" || editShift == "EVENING") &&
                            fatVal != null && snfVal != null && wVal != null && wVal > 0.0 &&
                            rVal != null && rVal > 0.0 && amtVal != null && amtVal > 0.0

                        OutlinedButton(
                            onClick = {
                                outcome?.scanRecord?.let { rec ->
                                    viewModel.recordScanOnly(rec.copy(farmerId = selectedFarmer?.farmerId, customerCode = editFarmerCode.ifBlank { "UNKNOWN" }, verificationStatus = "PENDING_ADMIN")) {
                                        Toast.makeText(context, "स्कैन Admin सत्यापन कतार में भेजा गया", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = outcome != null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_send_to_admin_queue")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Admin सत्यापन कतार में भेजें (Send to Review Queue)", fontSize = 11.sp)
                        }

                        ScanActionFooterButtons(
                            canConfirmSave = canConfirmSave,
                            isEditing = isEditMode,
                            onConfirmSave = {
                                val farmer = selectedFarmer!!
                                val dateParts = normDate!!.split("-")
                                scope.launch {
                                    val receiptNo = viewModel.generateReceiptNumber(editShift, normDate)
                                    val sourceTypeName = when (selectedScanType) {
                                        ScanType.MACHINE_DISPLAY -> "MACHINE_DISPLAY"
                                        ScanType.INDIVIDUAL_RECEIPT -> "MILK_RECEIPT"
                                        ScanType.CUSTOMER_REPORT -> "MONTHLY_REPORT"
                                    }
                                    val entry = MilkCollectionEntry(
                                        receiptNumber = receiptNo, date = normDate, timestamp = System.currentTimeMillis(),
                                        shift = editShift, farmerId = farmer.farmerId, farmerName = farmer.fullName,
                                        milkCategory = editCategory, quantity = wVal!!, fat = fatVal!!, snf = snfVal!!,
                                        waterPercentage = waterVal, rate = rVal!!, totalAmount = amtVal!!,
                                        machineAmount = machinePrintedAmount, scannedImageUri = persistedImagePath,
                                        isOcrScanned = true, ocrConfidence = outcome?.scanRecord?.overallConfidence ?: 95,
                                        machineNumber = editMachineId.ifBlank { editStationId }, actualTime = editTime,
                                        transactionId = editTransactionId.ifBlank { null },
                                        scanRecordId = outcome?.scanRecord?.recordId,
                                        verifiedBy = viewModel.userSession.value.userName.ifBlank { "Operator" },
                                        stationId = editStationId.ifBlank { null },
                                        month = dateParts.getOrNull(1)?.toIntOrNull() ?: 0,
                                        year = dateParts.getOrNull(0)?.toIntOrNull() ?: 0,
                                        sourceType = sourceTypeName,
                                        verificationStatus = if (outcome?.confidenceTier == ConfidenceTier.HIGH) "AUTO_VERIFIED" else "ADMIN_VERIFIED"
                                    )
                                    val updatedRec = (outcome?.scanRecord ?: ScanRecord("SCN-${System.currentTimeMillis()}")).copy(
                                        farmerId = farmer.farmerId, date = normDate, shift = editShift,
                                        fat = fatVal, snf = snfVal, weight = wVal, rate = rVal, amount = amtVal,
                                        verificationStatus = entry.verificationStatus, verifiedBy = entry.verifiedBy, verifiedAt = System.currentTimeMillis()
                                    )
                                    viewModel.saveVerifiedScanAndCollection(updatedRec, entry) {
                                        Toast.makeText(context, "✓ रसीद ${entry.receiptNumber} सत्यापित व सहेजी गई!", Toast.LENGTH_SHORT).show()
                                        onDataPopulated(entry)
                                        onSaveSuccess()
                                        onDismiss()
                                    }
                                }
                            },
                            onToggleEdit = { isEditMode = !isEditMode },
                            onScanAgain = {
                                resetScanState()
                                currentImageUri = null
                                capturedBitmap = null
                                requestCamera(if (selectedScanType == ScanType.MACHINE_DISPLAY) "LIVE" else "PHOTO")
                            },
                            onCancel = onDismiss
                        )
                    }
                }
            }
        }
    }
}
