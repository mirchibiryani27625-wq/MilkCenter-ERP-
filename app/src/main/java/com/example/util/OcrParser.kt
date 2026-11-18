package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.model.StationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.math.abs

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

object OcrParser {

    /**
     * Preprocesses a bitmap for thermal receipts & backlit machine displays:
     * - Optional crop region
     * - EXIF / manual rotation
     * - Adaptive brightness + grayscale contrast correction
     */
    fun preprocessBitmap(
        original: Bitmap,
        rotationDegrees: Float = 0f,
        enhanceContrast: Boolean = true,
        brightnessOffset: Float = 0f,
        contrastScale: Float = 1.38f,
        cropRect: NormalizedCropRect = NormalizedCropRect()
    ): Bitmap {
        val cropped = ImageQualityAnalyzer.cropBitmap(
            original = original,
            leftFraction = cropRect.left,
            topFraction = cropRect.top,
            rightFraction = cropRect.right,
            bottomFraction = cropRect.bottom
        )
        return ImageQualityAnalyzer.enhanceForOcr(
            original = cropped,
            totalRotationDegrees = rotationDegrees,
            enhanceContrast = enhanceContrast,
            brightnessOffset = brightnessOffset,
            contrastScale = contrastScale
        )
    }

    /**
     * Builds spatially ordered OCR lines using ML Kit bounding boxes so two-column receipts
     * and tabular monthly customer reports stay aligned row-by-row.
     */
    fun buildSpatialLines(visionText: Text): List<SpatialOcrLine> {
        val rawLines = visionText.textBlocks.flatMap { it.lines }
        if (rawLines.isEmpty()) {
            return visionText.text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { idx, txt ->
                    SpatialOcrLine(
                        text = txt,
                        confidence = 85,
                        boundingBoxLabel = "Line ${idx + 1}",
                        centerY = idx * 20,
                        left = 0
                    )
                }
        }

        val sorted = rawLines.sortedWith(
            compareBy<Text.Line> { it.boundingBox?.centerY() ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
        )

        val bands = mutableListOf<MutableList<Text.Line>>()
        for (line in sorted) {
            val box = line.boundingBox
            val cy = box?.centerY() ?: 0
            val lineHeight = (box?.height() ?: 24).coerceAtLeast(12)
            val threshold = maxOf(14, (lineHeight * 0.65).toInt())

            val matchingBand = bands.lastOrNull()?.let { band ->
                val bandY = band.mapNotNull { it.boundingBox?.centerY() }.average()
                if (abs(cy - bandY) <= threshold) band else null
            }
            if (matchingBand != null) {
                matchingBand.add(line)
            } else {
                bands.add(mutableListOf(line))
            }
        }

        return bands.mapIndexed { idx, band ->
            val orderedBand = band.sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE }
            val mergedText = orderedBand.joinToString(" ") { it.text.trim() }.trim()
            val confScores = orderedBand.map { line ->
                val rawConf = line.confidence
                if (rawConf in 0.01f..1.0f) (rawConf * 100f).toInt() else 86
            }
            val avgConf = if (confScores.isNotEmpty()) confScores.average().toInt().coerceIn(20, 99) else 85

            val minLeft = orderedBand.mapNotNull { it.boundingBox?.left }.minOrNull() ?: 0
            val minTop = orderedBand.mapNotNull { it.boundingBox?.top }.minOrNull() ?: 0
            val maxRight = orderedBand.mapNotNull { it.boundingBox?.right }.maxOrNull() ?: 0
            val maxBottom = orderedBand.mapNotNull { it.boundingBox?.bottom }.maxOrNull() ?: 0
            val boxLabel = if (maxRight > minLeft && maxBottom > minTop) {
                "x:$minLeft, y:$minTop (${maxRight - minLeft}x${maxBottom - minTop})"
            } else {
                "Line ${idx + 1}"
            }

            SpatialOcrLine(
                text = mergedText,
                confidence = avgConf,
                boundingBoxLabel = boxLabel,
                centerY = (minTop + maxBottom) / 2,
                left = minLeft
            )
        }.filter { it.text.isNotEmpty() }
    }

    /**
     * Recognize text from image Uri using EXIF orientation, crop, blur check, enhancement & ML Kit.
     */
    suspend fun processImage(
        context: Context,
        imageUri: Uri,
        rotationDegrees: Float = 0f,
        enhanceContrast: Boolean = true,
        brightnessOffset: Float = 0f,
        typeOverride: ScanType? = null,
        contrastScale: Float = 1.38f,
        cropRect: NormalizedCropRect = NormalizedCropRect(),
        stationConfig: StationConfig = StationConfig()
    ): OcrScanResult {
        return try {
            val exifRotation = ImageQualityAnalyzer.getExifRotationDegrees(context, imageUri)
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return OcrScanResult(
                rawText = "",
                warningMessage = "छवि लोड नहीं हो सकी। कृपया दूसरी स्पष्ट फोटो चुनें।",
                parseErrors = listOf("छवि डिकोड विफल"),
                requiresManualCheck = true
            )

            val totalRotation = (exifRotation + rotationDegrees) % 360f
            processBitmap(
                bitmap = bitmap,
                rotationDegrees = totalRotation,
                enhanceContrast = enhanceContrast,
                brightnessOffset = brightnessOffset,
                typeOverride = typeOverride,
                contrastScale = contrastScale,
                cropRect = cropRect,
                stationConfig = stationConfig
            )
        } catch (e: Exception) {
            OcrScanResult(
                rawText = "",
                warningMessage = "छवि प्रोसेस करने में त्रुटि: ${e.localizedMessage}",
                parseErrors = listOf(e.localizedMessage ?: "Image error"),
                requiresManualCheck = true
            )
        }
    }

    /**
     * Recognize text from a Bitmap (CameraX Live Display / Photo / Gallery) with blur & illumination validation.
     */
    suspend fun processBitmap(
        bitmap: Bitmap,
        rotationDegrees: Float = 0f,
        enhanceContrast: Boolean = true,
        brightnessOffset: Float = 0f,
        typeOverride: ScanType? = null,
        contrastScale: Float = 1.38f,
        cropRect: NormalizedCropRect = NormalizedCropRect(),
        stationConfig: StationConfig = StationConfig()
    ): OcrScanResult {
        val croppedForQuality = ImageQualityAnalyzer.cropBitmap(
            original = bitmap,
            leftFraction = cropRect.left,
            topFraction = cropRect.top,
            rightFraction = cropRect.right,
            bottomFraction = cropRect.bottom
        )
        val qualityReport = ImageQualityAnalyzer.analyzeBitmapQuality(croppedForQuality, rotationDegrees)
        val preprocessed = preprocessBitmap(
            original = bitmap,
            rotationDegrees = rotationDegrees,
            enhanceContrast = enhanceContrast,
            brightnessOffset = brightnessOffset,
            contrastScale = contrastScale,
            cropRect = cropRect
        )

        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(preprocessed, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val spatialLines = buildSpatialLines(visionText)
                        val normalizedRaw = if (spatialLines.isNotEmpty()) {
                            spatialLines.joinToString("\n") { it.text }
                        } else {
                            visionText.text
                        }

                        val parsed = parseExtractedText(
                            raw = normalizedRaw,
                            scanTypeOverride = typeOverride,
                            spatialLines = spatialLines,
                            stationConfig = stationConfig
                        )
                        val adjustedConfidence = if (qualityReport.isBlurry) {
                            minOf(parsed.overallConfidence, 55)
                        } else {
                            parsed.overallConfidence
                        }
                        val combinedWarning = listOfNotNull(qualityReport.qualityWarning, parsed.warningMessage)
                            .joinToString("\n")
                            .ifBlank { null }
                        val extraErrors = if (qualityReport.isBlurry) {
                            parsed.parseErrors + "फोटो धुंधली (Blurry) होने से रीडिंग असुरक्षित है"
                        } else {
                            parsed.parseErrors
                        }

                        continuation.resume(
                            parsed.copy(
                                imageQuality = qualityReport,
                                overallConfidence = adjustedConfidence,
                                requiresManualCheck = parsed.requiresManualCheck || qualityReport.isBlurry,
                                warningMessage = combinedWarning,
                                parseErrors = extraErrors,
                                spatialLines = spatialLines
                            )
                        )
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(
                            OcrScanResult(
                                rawText = "",
                                imageQuality = qualityReport,
                                warningMessage = "OCR पहचान त्रुटि: ${exception.localizedMessage ?: "रसीद पढ़ने में असमर्थ"}",
                                parseErrors = listOf("ML Kit OCR त्रुटि"),
                                requiresManualCheck = true
                            )
                        )
                    }
            } catch (e: Exception) {
                continuation.resume(
                    OcrScanResult(
                        rawText = "",
                        imageQuality = qualityReport,
                        warningMessage = "छवि विश्लेषण त्रुटि: ${e.localizedMessage}",
                        parseErrors = listOf(e.localizedMessage ?: "Processing error"),
                        requiresManualCheck = true
                    )
                )
            }
        }
    }

    /**
     * Master parsing function with automatic receipt / machine display / monthly report format detection.
     */
    fun parseExtractedText(
        raw: String,
        scanTypeOverride: ScanType? = null,
        spatialLines: List<SpatialOcrLine> = emptyList(),
        stationConfig: StationConfig = StationConfig()
    ): OcrScanResult {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val upper = raw.uppercase(Locale.US)

        val rowLinesCount = lines.count { line ->
            Pattern.compile("""\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?[\s|]+[MEme][\s|]+\d+""").matcher(line).find()
        }

        val isCustomerReportCandidate = upper.contains("CUSTOMER REPORT") ||
            upper.contains("CUSTOMER_REPORT") ||
            upper.contains("MONTHLY REPORT") ||
            (upper.contains("TOTAL ENTRIES") && (upper.contains("AVERAGE FAT") || upper.contains("TOTAL FAT"))) ||
            rowLinesCount >= 2

        val isMachineDisplayCandidate = upper.contains("EVEREST") ||
            upper.contains("LACTOSCAN") ||
            upper.contains("ECOMILK") ||
            upper.contains("ESSAE") ||
            upper.contains("PROMPT") ||
            upper.contains("F_ID") ||
            (upper.contains("CLR") && upper.contains("FAT"))

        val effectiveType = scanTypeOverride ?: when {
            isCustomerReportCandidate -> ScanType.CUSTOMER_REPORT
            isMachineDisplayCandidate -> ScanType.MACHINE_DISPLAY
            else -> ScanType.INDIVIDUAL_RECEIPT
        }

        return when (effectiveType) {
            ScanType.CUSTOMER_REPORT -> CustomerReportParser.parse(raw, lines, spatialLines)
            ScanType.MACHINE_DISPLAY -> parseMachineOrSlip(raw, lines, ScanType.MACHINE_DISPLAY, spatialLines, stationConfig)
            ScanType.INDIVIDUAL_RECEIPT -> parseMachineOrSlip(raw, lines, ScanType.INDIVIDUAL_RECEIPT, spatialLines, stationConfig)
        }
    }

    /**
     * Unified strict parser for Individual Milk Receipts & Machine Displays (Everest, Lactoscan, Ecomilk, Essae, Prompt).
     * ANTI-FAKE RULE: Any missing, illegible, or ambiguous field stays NULL (shown as "Not Readable"). Never invents defaults!
     */
    private fun parseMachineOrSlip(
        raw: String,
        lines: List<String>,
        targetType: ScanType,
        spatialLines: List<SpatialOcrLine>,
        stationConfig: StationConfig
    ): OcrScanResult {
        val upper = raw.uppercase(Locale.US)
        val errors = mutableListOf<String>()

        var farmerName: String? = null
        var farmerCode: String? = null
        var custIdConf = 0
        var stnId: String? = null
        var stnName: String? = null
        var machineId: String? = null
        var txnId: String? = null
        var detectedDate: String? = null
        var dateConf = 0
        var detectedTime: String? = null
        var timeConf = 0
        var explicitShiftLabel: String? = null
        var milkType: String? = null

        fun matchLocation(pattern: String): String {
            if (spatialLines.isEmpty()) return "OCR Text"
            val p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            return spatialLines.firstOrNull { p.matcher(it.text).find() }?.boundingBoxLabel ?: "OCR Text"
        }

        fun lineConfidence(pattern: String, fallback: Int): Int {
            if (spatialLines.isEmpty()) return fallback
            val p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val lineConf = spatialLines.firstOrNull { p.matcher(it.text).find() }?.confidence
            return if (lineConf != null) minOf(fallback, lineConf.coerceAtLeast(50)).coerceAtLeast(fallback - 8) else fallback
        }

        // 1. Station ID
        Pattern.compile("""(?:STN[_\s]*ID|STATION[_\s]*ID)[\s:=#]*([A-Z0-9\-_]+)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) stnId = it.group(1)?.trim() }

        // 2. Station Name
        Pattern.compile("""(?:STN[_\s]*NAME|STATION[_\s]*NAME|CENTER[_\s]*NAME|DAIRY)[\s:=]*([^\n\r]+)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let {
                if (it.find()) {
                    val candidate = it.group(1)?.trim()
                    if (!candidate.isNullOrBlank() && candidate.length >= 2) stnName = candidate
                }
            }

        // 3. Machine ID (Everest, Lactoscan, Ecomilk, Essae, Prompt, M_ID, M/C)
        Pattern.compile(
            """(?:MACHINE[_\s]*ID|M[_\s]*ID|M\s*/\s*C|MC|MAC[_\s]*NO)[\s:=#]*([A-Z0-9\-_]+)""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let {
            if (it.find()) {
                val cand = it.group(1)?.trim()
                if (!cand.isNullOrBlank()) machineId = cand
            }
        }
        if (machineId == null) {
            Pattern.compile(
                """\b(EVEREST[\s\-A-Z0-9]*|LACTOSCAN[\s\-A-Z0-9]*|ECOMILK[\s\-A-Z0-9]*|ESSAE[\s\-A-Z0-9]*|PROMPT[\s\-A-Z0-9]*)\b""",
                Pattern.CASE_INSENSITIVE
            ).matcher(raw).let {
                if (it.find()) machineId = it.group(1)?.trim()
            }
        }

        // 4. Machine Transaction ID / Slip No / Receipt No / Bill No
        Pattern.compile(
            """(?:TXN[_\s]*ID|TRANS(?:ACTION)?[_\s]*ID|SLIP[_\s]*(?:NO|ID)|RECEIPT[_\s]*(?:NO|ID)|RCP[_\s]*(?:NO|ID)|BILL[_\s]*NO|SR[_\s]*NO)[\s:=#]*([A-Z0-9\-_]+)""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let { if (it.find()) txnId = it.group(1)?.trim() }

        // 5. Farmer ID / Customer ID / F_ID / CODE
        Pattern.compile(
            """(?:^|[\s\n])(?:F[_\s]*ID|CUSTOMER[_\s]*ID|CUST[_\s]*ID|FARMER[_\s]*(?:ID|CODE)|MEM(?:BER)?[_\s]*(?:ID|CODE)|CODE)[\s:=#]*([A-Z0-9\-_]+)""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let { m ->
            while (m.find()) {
                val candidate = m.group(1)?.trim()
                if (!candidate.isNullOrBlank() &&
                    !candidate.equals(stnId, ignoreCase = true) &&
                    !candidate.equals(txnId, ignoreCase = true) &&
                    !candidate.equals(machineId, ignoreCase = true)
                ) {
                    farmerCode = candidate.uppercase(Locale.US)
                    custIdConf = lineConfidence("""(?:F[_\s]*ID|CUSTOMER[_\s]*ID|FARMER[_\s]*ID|CODE)""", 95)
                    break
                }
            }
        }

        // 6. Date (DD/MM/YYYY, DD-MM-YY, YYYY-MM-DD)
        Pattern.compile(
            """(?:DATE|DT|दिनांक)?[\s:=]*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let {
            if (it.find()) {
                detectedDate = it.group(1)?.trim()
                dateConf = lineConfidence("""(?:DATE|DT|\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""", 95)
            }
        }

        // 7. Time (HH:MM(:SS)? (AM/PM)?) — never allow bare decimals like "5.00" (weight/rate) to match as time!
        Pattern.compile(
            """(?:^|[\s\n])(?:TIME|TM|समय)[\s:=]+(\d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:\s*[AaPp][Mm])?)""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let {
            if (it.find()) {
                detectedTime = it.group(1)?.trim()
                timeConf = lineConfidence("""(?:TIME|TM|\d{1,2}[:.]\d{2})""", 94)
            }
        }
        if (detectedTime == null) {
            Pattern.compile(
                """\b(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AaPp][Mm])?|\d{1,2}\.\d{2}\s*[AaPp][Mm])\b""",
                Pattern.CASE_INSENSITIVE
            ).matcher(raw).let {
                if (it.find()) {
                    detectedTime = it.group(1)?.trim()
                    timeConf = lineConfidence("""\d{1,2}[:.]\d{2}""", 90)
                }
            }
        }

        // 8. Explicit Shift Label + Admin-configurable Time-based Shift Classification
        val shiftMatcher = Pattern.compile(
            """(?:SHIFT|MACHINE\s*SHIFT|SFT|पारी)[\s:=]*(MORNING|EVENING|MOR|EVE|M|E|सुबह|शाम)\b""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw)
        if (shiftMatcher.find()) {
            explicitShiftLabel = shiftMatcher.group(1)?.trim()
        }

        val shiftClassification = ShiftTimingClassifier.classifyShift(
            extractedTime = detectedTime,
            explicitShiftLabel = explicitShiftLabel,
            config = stationConfig
        )
        val shift = shiftClassification.shift
        val shiftConf = shiftClassification.confidence
        if (shiftClassification.note != null && shift == null) {
            errors.add(shiftClassification.note)
        } else if (shiftClassification.note != null) {
            errors.add(shiftClassification.note)
        }

        // 9. Animal / Milk Type (COW / BUFFALO)
        Pattern.compile(
            """(?:M[_\s]*TYPE|MILK[_\s]*TYPE|ANIMAL[_\s]*TYPE|TYPE)[\s:=]*(COW|BUFFALO|BUF|BM|CM|C|B|गाय|भैंस)""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let {
            if (it.find()) {
                val t = it.group(1)?.uppercase(Locale.US)?.trim()
                milkType = when {
                    t == "BUFFALO" || t == "BUF" || t == "BM" || t == "B" || t?.contains("भैंस") == true -> "BUFFALO"
                    t == "COW" || t == "CM" || t == "C" || t?.contains("गाय") == true -> "COW"
                    else -> null
                }
            }
        }

        // 10. Numeric measurements with range validation & compact display fallbacks
        val fatResult = extractLabeledDecimal(raw, """(?:FAT|वसा)""", 1.5, 15.0)
        var fat = fatResult.value
        var fatConf = fatResult.confidence
        if (fatResult.error != null) errors.add("FAT: ${fatResult.error}")

        val snfResult = extractLabeledDecimal(raw, """(?:SNF|S\.N\.F|एसएनएफ)""", 5.0, 14.0)
        var snf = snfResult.value
        var snfConf = snfResult.confidence
        if (snfResult.error != null) errors.add("SNF: ${snfResult.error}")

        val waterResult = extractLabeledDecimal(raw, """(?:ADD\.?\s*WATER|A\.?\s*WATER|WATER\s*%?|WTR|पानी)""", 0.0, 80.0)
        var water = waterResult.value
        var waterConf = waterResult.confidence
        if (waterResult.error != null) errors.add("WATER: ${waterResult.error}")

        val wtResult = extractLabeledDecimal(raw, """(?:WT|WEIGHT|QTY|QUANTITY|LTR|VOL|वजन|मात्रा)""", 0.1, 1000.0)
        val quantity = wtResult.value
        val qtyConf = wtResult.confidence
        if (wtResult.error != null) errors.add("WEIGHT: ${wtResult.error}")

        val rateResult = extractLabeledDecimal(
            raw, """(?:RT\s*/\s*LTR?|RATE\s*/\s*LTR?|RATE\s*PER\s*LITER|RATE|PRICE|RT|दर)""", 10.0, 250.0, currencyPrefix = true
        )
        val rate = rateResult.value
        val rateConf = rateResult.confidence
        if (rateResult.error != null) errors.add("RATE: ${rateResult.error}")

        val amtResult = extractLabeledDecimal(
            raw, """(?:TOTAL\s*AMOUNT|TOTAL\s*AMT|NET\s*AMT|AMOUNT|AMT|RS\.?|कुल\s*राशि)""", 1.0, 500000.0, currencyPrefix = true
        )
        val totalAmount = amtResult.value
        val amtConf = amtResult.confidence
        if (amtResult.error != null) errors.add("AMOUNT: ${amtResult.error}")

        // Compact LCD fallback (e.g. "F:4.2 S:8.5 W:0.0")
        if (fat == null) {
            Pattern.compile("""\bF[\s:=]*([0-9]+\.[0-9]{1,2})\b""").matcher(upper).let { m ->
                if (m.find()) m.group(1)?.toDoubleOrNull()?.takeIf { it in 1.5..15.0 }?.let {
                    fat = it; fatConf = 78
                }
            }
        }
        if (snf == null) {
            Pattern.compile("""\bS[\s:=]*([0-9]+\.[0-9]{1,2})\b""").matcher(upper).let { m ->
                if (m.find()) m.group(1)?.toDoubleOrNull()?.takeIf { it in 5.0..14.0 }?.let {
                    snf = it; snfConf = 78
                }
            }
        }
        if (water == null) {
            Pattern.compile("""\bW[\s:=]*([0-9]+(?:\.[0-9]{1,2})?)%?""").matcher(upper).let { m ->
                if (m.find()) m.group(1)?.toDoubleOrNull()?.takeIf { it in 0.0..80.0 }?.let {
                    water = it; waterConf = 76
                }
            }
        }

        // Duplicate marker
        val isDuplicate = upper.contains("DUPLICATE: YES") ||
            upper.contains("DUPLICATE SLIP") ||
            (upper.contains("DUPLICATE") && !upper.contains("DUPLICATE: NO") && !upper.contains("DUPLICATE NO"))

        // Explicit Farmer Name label first (line-start anchored so STN_NAME / CENTER_NAME never matches), then clean header fallback
        Pattern.compile(
            """(?:^|\n)\s*(?:FARMER[_\s]*NAME|CUST(?:OMER)?[_\s]*NAME|NAME)\s*[:=]+\s*([^\n\r]+)""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let { m ->
            if (m.find()) {
                val labeled = m.group(1)?.trim()
                if (!labeled.isNullOrBlank() && labeled.length >= 2 && !labeled.equals(stnName, ignoreCase = true)) {
                    farmerName = labeled
                }
            }
        }
        if (farmerName == null) {
            for (i in 0 until minOf(lines.size, 6)) {
                val line = lines[i]
                val lineU = line.uppercase(Locale.US)
                if (!lineU.contains("STN") && !lineU.contains("DATE") && !lineU.contains("CODE") &&
                    !lineU.contains("F_ID") && !lineU.contains("CUST") && !lineU.contains("TIME") &&
                    !lineU.contains("RECEIPT") && !lineU.contains("NAMASTE") && !lineU.contains("DAIRY") &&
                    !lineU.contains("LACTOSCAN") && !lineU.contains("ECOMILK") && !lineU.contains("EVEREST") &&
                    !lineU.contains("PROMPT") && !lineU.contains("ESSAE") && !lineU.contains("FAT") &&
                    !lineU.contains("SNF") && !lineU.contains("CENTER") && !lineU.contains("CENTRE") &&
                    !lineU.contains("TXN")
                ) {
                    val lettersOnly = line.replace(Regex("""[^a-zA-Z\u0900-\u097F\s.]"""), "").trim()
                    if (lettersOnly.length >= 3 && lettersOnly.any { it.isLetter() } && !line.any { it.isDigit() }) {
                        farmerName = line.trim()
                        break
                    }
                }
            }
        }

        // Expected amount calculation with rounding tolerance
        val calculatedAmt = if (quantity != null && rate != null) {
            Math.round(quantity * rate * 100.0) / 100.0
        } else null
        val diff = if (calculatedAmt != null && totalAmount != null) abs(calculatedAmt - totalAmount) else null
        val tolerance = stationConfig.amountToleranceRs.coerceAtLeast(0.05)
        val isCalcVerified = diff != null && diff <= tolerance

        if (farmerCode == null) errors.add("Farmer ID / F_ID नहीं मिली (Not Readable)")
        if (fat == null) errors.add("FAT रीडिंग स्पष्ट नहीं है (Not Readable)")
        if (snf == null) errors.add("SNF रीडिंग स्पष्ट नहीं है (Not Readable)")
        if (quantity == null) errors.add("वजन (Weight) स्पष्ट नहीं है (Not Readable)")
        if (rate == null) errors.add("दर (Rate) स्पष्ट नहीं है (Not Readable)")
        if (totalAmount == null) errors.add("कुल राशि (Amount) स्पष्ट नहीं है (Not Readable)")
        if (detectedDate == null) errors.add("तारीख (Date) रसीद पर नहीं मिली (Not Readable)")
        if (shift == null && errors.none { it.contains("शिफ्ट") || it.contains("समय") }) {
            errors.add("शिफ्ट (Morning/Evening) स्पष्ट नहीं है — कृपया चुनें")
        }
        if (diff != null && diff > tolerance) {
            errors.add("राशि अंतर: प्रिंट ₹$totalAmount ≠ गणना ₹$calculatedAmt (${quantity} × ${rate})")
        }
        if (isDuplicate) {
            errors.add("पर्ची पर DUPLICATE चिह्नित है")
        }

        val amountStatus = when {
            totalAmount == null -> FieldValidationStatus.UNREADABLE
            diff != null && diff > tolerance -> FieldValidationStatus.MISMATCH
            diff != null && diff > 0.05 -> FieldValidationStatus.WARNING
            else -> FieldValidationStatus.VALID
        }
        val amountNote = when {
            totalAmount == null -> "Not Readable"
            diff != null && diff > tolerance -> "Expected ₹$calculatedAmt ≠ Printed ₹$totalAmount"
            diff != null && diff > 0.05 -> "Precision tolerance (diff ₹${String.format(Locale.US, "%.2f", diff)})"
            else -> null
        }

        val fieldDetails = listOf(
            OcrFieldDetail("farmerId", "Farmer ID", farmerCode, farmerCode ?: "Not Readable", custIdConf, matchLocation("""(?:F[_\s]*ID|CODE|CUST)""")),
            OcrFieldDetail("farmerName", "Farmer Name", farmerName, farmerName ?: "Not Readable", if (farmerName != null) 90 else 0, matchLocation("""(?:NAME|${farmerName?.take(8) ?: "___"})""")),
            OcrFieldDetail("date", "Date", detectedDate, detectedDate ?: "Not Readable", dateConf, matchLocation("""(?:DATE|DT|\d{1,2}[/-]\d{1,2})""")),
            OcrFieldDetail("time", "Time", detectedTime, detectedTime ?: "Not Readable", timeConf, matchLocation("""(?:TIME|TM|\d{1,2}:\d{2})""")),
            OcrFieldDetail("shift", "Shift", shift, shift ?: "Not Readable", shiftConf, matchLocation("""(?:SHIFT|TIME|\d{1,2}:\d{2})""")),
            OcrFieldDetail("fat", "FAT %", fat?.toString(), fat?.let { "$it%" } ?: "Not Readable", fatConf, matchLocation("""\bFAT\b|\bF:""")),
            OcrFieldDetail("snf", "SNF %", snf?.toString(), snf?.let { "$it%" } ?: "Not Readable", snfConf, matchLocation("""\bSNF\b|\bS:""")),
            OcrFieldDetail("water", "Water %", water?.toString(), water?.let { "$it%" } ?: "Not Readable", waterConf, matchLocation("""WATER|WTR"""), if (water == null) FieldValidationStatus.WARNING else FieldValidationStatus.VALID),
            OcrFieldDetail("rate", "Rate (₹/L)", rate?.toString(), rate?.let { "₹$it" } ?: "Not Readable", rateConf, matchLocation("""RATE|RT/LT""")),
            OcrFieldDetail("weight", "Weight (L)", quantity?.toString(), quantity?.let { "$it L" } ?: "Not Readable", qtyConf, matchLocation("""WT|WEIGHT|QTY""")),
            OcrFieldDetail("amount", "Total Amount", totalAmount?.toString(), totalAmount?.let { "₹$it" } ?: "Not Readable", amtConf, matchLocation("""RS|AMT|AMOUNT"""), amountStatus, amountNote)
        )

        val mandatoryConfs = listOf(custIdConf, fatConf, snfConf, qtyConf, rateConf, amtConf, dateConf, shiftConf)
        val overall = if (mandatoryConfs.isNotEmpty()) mandatoryConfs.average().toInt() else 0
        val warning = errors.firstOrNull()
        val needsManualCheck = errors.isNotEmpty() || overall < 85

        return OcrScanResult(
            scanType = targetType,
            rawText = raw,
            farmerName = farmerName,
            farmerCode = farmerCode,
            customerIdConfidence = custIdConf,
            stationId = stnId,
            stationName = stnName,
            machineNumber = machineId,
            transactionId = txnId,
            detectedDate = detectedDate,
            dateConfidence = dateConf,
            detectedTime = detectedTime,
            timeConfidence = timeConf,
            shift = shift,
            shiftConfidence = shiftConf,
            fat = fat,
            fatConfidence = fatConf,
            snf = snf,
            snfConfidence = snfConf,
            water = water,
            waterConfidence = waterConf,
            milkType = milkType,
            quantity = quantity,
            quantityConfidence = qtyConf,
            rate = rate,
            rateConfidence = rateConf,
            totalAmount = totalAmount,
            amountConfidence = amtConf,
            calculatedAmount = calculatedAmt,
            isCalculationVerified = isCalcVerified,
            isDuplicate = isDuplicate,
            overallConfidence = if (isCalcVerified && errors.isEmpty()) maxOf(overall, 95) else overall,
            requiresManualCheck = needsManualCheck,
            warningMessage = warning,
            parseErrors = errors,
            fieldDetails = fieldDetails,
            spatialLines = spatialLines
        )
    }

    private data class FieldExtractResult(
        val value: Double?,
        val confidence: Int,
        val error: String? = null
    )

    private fun extractLabeledDecimal(
        raw: String,
        labelPattern: String,
        min: Double,
        max: Double,
        currencyPrefix: Boolean = false
    ): FieldExtractResult {
        val prefix = if (currencyPrefix) """(?:RS\.?|₹)?[\s:=]*""" else """"""
        val regex = Pattern.compile(
            """(?:^|[\s\n])$labelPattern[\s:=]*$prefix([0-9OIl]+(?:\.[0-9OIl]{1,3})?)%?""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = regex.matcher(raw)
        if (!matcher.find()) {
            return FieldExtractResult(null, 0)
        }

        val token = matcher.group(1)?.trim() ?: return FieldExtractResult(null, 0)
        val cleaned = token.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1')
        val hadCharNoise = cleaned != token
        val parsed = cleaned.toDoubleOrNull()
            ?: return FieldExtractResult(null, 20, "अस्पष्ट अंक '$token'")

        if (parsed !in min..max) {
            return FieldExtractResult(null, 35, "अमान्य रेंज '$token' (अपेक्षित $min–$max)")
        }

        val conf = if (hadCharNoise) 74 else 96
        return FieldExtractResult(parsed, conf)
    }
}
