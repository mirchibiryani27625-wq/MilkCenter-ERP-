package com.example.util

import com.example.data.model.Farmer
import com.example.data.model.FarmerMachineMapping
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.ScanRecord
import com.example.data.model.StationConfig
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import kotlin.math.abs

enum class ConfidenceTier {
    HIGH,
    MEDIUM,
    LOW
}

enum class CustomerIdMatchStatus {
    EXACT_ID,
    MAPPED_CODE,
    AMBIGUOUS_MATCH,
    UNMATCHED
}

enum class DuplicateStatus {
    NONE,
    EXACT_DUPLICATE,
    POTENTIAL_DUPLICATE,
    SEPARATE_TRANSACTION
}

data class FieldConfidenceMap(
    val customerId: Int = 0,
    val fat: Int = 0,
    val snf: Int = 0,
    val water: Int = 0,
    val weight: Int = 0,
    val rate: Int = 0,
    val amount: Int = 0,
    val date: Int = 0,
    val time: Int = 0,
    val shift: Int = 0
) {
    fun toJson(): String {
        return """{"customerId":$customerId,"fat":$fat,"snf":$snf,"water":$water,"weight":$weight,"rate":$rate,"amount":$amount,"date":$date,"time":$time,"shift":$shift}"""
    }

    companion object {
        fun fromJson(json: String): FieldConfidenceMap {
            fun optInt(key: String): Int {
                val m = Pattern.compile(""""$key"\s*:\s*(\d+)""").matcher(json)
                return if (m.find()) m.group(1)?.toIntOrNull() ?: 0 else 0
            }
            return try {
                FieldConfidenceMap(
                    customerId = optInt("customerId"),
                    fat = optInt("fat"),
                    snf = optInt("snf"),
                    water = optInt("water"),
                    weight = optInt("weight"),
                    rate = optInt("rate"),
                    amount = optInt("amount"),
                    date = optInt("date"),
                    time = optInt("time"),
                    shift = optInt("shift")
                )
            } catch (_: Exception) {
                FieldConfidenceMap()
            }
        }
    }
}

data class CustomerResolution(
    val matchedFarmer: Farmer?,
    val status: CustomerIdMatchStatus,
    val confidence: Int,
    val candidates: List<Farmer> = emptyList(),
    val note: String? = null
)

data class DuplicateCheckResult(
    val status: DuplicateStatus,
    val conflictingEntry: MilkCollectionEntry? = null,
    val warningMessage: String? = null
)

data class ScanVerificationOutcome(
    val scanRecord: ScanRecord,
    val confidenceTier: ConfidenceTier,
    val fieldConfidences: FieldConfidenceMap,
    val customerResolution: CustomerResolution,
    val duplicateCheck: DuplicateCheckResult,
    val normalizedDate: String?,
    val expectedAmount: Double?,
    val amountMismatch: Boolean,
    val precisionRoundingApplied: Boolean = false,
    val errors: List<String>,
    val canAutoSave: Boolean,
    val canDirectSaveWithConfirm: Boolean,
    val fieldDetails: List<OcrFieldDetail> = emptyList()
)

data class VerifiedReportRow(
    val row: CustomerReportRow,
    val normalizedDate: String?,
    val duplicateStatus: DuplicateStatus,
    val existingReceiptNumber: String? = null
)

data class ReportVerificationOutcome(
    val scanRecord: ScanRecord,
    val confidenceTier: ConfidenceTier,
    val customerResolution: CustomerResolution,
    val verifiedRows: List<VerifiedReportRow>,
    val duplicateRowCount: Int,
    val calculatedWeightSum: Double = 0.0,
    val calculatedAvgFat: Double = 0.0,
    val calculatedAvgSnf: Double = 0.0,
    val calculatedAmountSum: Double = 0.0,
    val isFullyReconciled: Boolean = false,
    val reconciliationErrors: List<String> = emptyList(),
    val errors: List<String>,
    val canBatchSave: Boolean
)

object ScanVerificationEngine {

    /**
     * Normalizes DD/MM/YYYY, DD-MM-YY, YYYY-MM-DD (or DD/MM when referenceYear is supplied from report headers)
     * into ISO YYYY-MM-DD. Returns null if invalid or incomplete. NEVER invents today's date.
     */
    fun normalizeDateToIso(rawDate: String?, referenceYear: Int? = null): String? {
        if (rawDate.isNullOrBlank()) return null
        val clean = rawDate.trim().replace('.', '/').replace('-', '/')
        val parts = clean.split('/').map { it.trim() }
        return try {
            when (parts.size) {
                3 -> {
                    if (parts[0].length == 4) {
                        val y = parts[0].toInt()
                        val m = parts[1].toInt()
                        val d = parts[2].toInt()
                        if (y in 2000..2100 && m in 1..12 && d in 1..31) {
                            String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                        } else null
                    } else {
                        val d = parts[0].toInt()
                        val m = parts[1].toInt()
                        var y = parts[2].toInt()
                        if (y in 0..99) y += 2000
                        if (y in 2000..2100 && m in 1..12 && d in 1..31) {
                            String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                        } else null
                    }
                }
                2 -> {
                    val y = referenceYear ?: return null
                    val d = parts[0].toInt()
                    val m = parts[1].toInt()
                    if (y in 2000..2100 && m in 1..12 && d in 1..31) {
                        String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
                    } else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractYearFromDate(rawDate: String?): Int? {
        val iso = normalizeDateToIso(rawDate) ?: return null
        return iso.substring(0, 4).toIntOrNull()
    }

    /**
     * Resolves scanned Customer ID (`F_ID` / `CODE`) strictly using Admin Machine Mappings first,
     * then exact Farmer ID match. Ambiguous partial matches require manual selection; wrong/unknown IDs are flagged.
     */
    fun resolveCustomer(
        rawCode: String?,
        rawName: String?,
        farmers: List<Farmer>,
        mappings: List<FarmerMachineMapping>,
        machineId: String? = null
    ): CustomerResolution {
        val codeTrimmed = rawCode?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() && it != "UNKNOWN" && it != "NOT READABLE" }

        if (codeTrimmed != null) {
            val mapping = mappings.firstOrNull {
                it.machineCustomerCode.equals(codeTrimmed, ignoreCase = true) &&
                    (!machineId.isNullOrBlank() && it.machineId.equals(machineId, ignoreCase = true))
            } ?: mappings.firstOrNull {
                it.machineCustomerCode.equals(codeTrimmed, ignoreCase = true) && it.machineId.equals("ALL", ignoreCase = true)
            } ?: mappings.firstOrNull {
                it.machineCustomerCode.equals(codeTrimmed, ignoreCase = true)
            }

            if (mapping != null) {
                val mappedFarmer = farmers.firstOrNull { it.farmerId.equals(mapping.farmerId, ignoreCase = true) }
                if (mappedFarmer != null) {
                    return CustomerResolution(
                        matchedFarmer = mappedFarmer,
                        status = CustomerIdMatchStatus.MAPPED_CODE,
                        confidence = 95,
                        candidates = listOf(mappedFarmer),
                        note = "मशीन कोड '$codeTrimmed' → किसान ${mappedFarmer.farmerId} (${mappedFarmer.fullName}) मैपिंग से मिला"
                    )
                }
            }

            val exact = farmers.firstOrNull { it.farmerId.equals(codeTrimmed, ignoreCase = true) }
            if (exact != null) {
                return CustomerResolution(
                    matchedFarmer = exact,
                    status = CustomerIdMatchStatus.EXACT_ID,
                    confidence = 98,
                    candidates = listOf(exact)
                )
            }

            val digitsOnly = codeTrimmed.replace(Regex("[^0-9]"), "").trimStart('0')
            if (digitsOnly.isNotEmpty()) {
                val candidates = farmers.filter { f ->
                    val fDigits = f.farmerId.replace(Regex("[^0-9]"), "").trimStart('0')
                    fDigits == digitsOnly
                }
                if (candidates.size == 1) {
                    return CustomerResolution(
                        matchedFarmer = candidates.first(),
                        status = CustomerIdMatchStatus.AMBIGUOUS_MATCH,
                        confidence = 70,
                        candidates = candidates,
                        note = "आंशिक मिलान: मशीन कोड '$codeTrimmed' संभवतः ${candidates.first().farmerId} (${candidates.first().fullName}) है — पुष्टि आवश्यक"
                    )
                } else if (candidates.size > 1) {
                    return CustomerResolution(
                        matchedFarmer = null,
                        status = CustomerIdMatchStatus.AMBIGUOUS_MATCH,
                        confidence = 40,
                        candidates = candidates,
                        note = "एक से अधिक किसान मिले (${candidates.joinToString { it.farmerId }}) — मैन्युअल चयन आवश्यक"
                    )
                }
            }

            return CustomerResolution(
                matchedFarmer = null,
                status = CustomerIdMatchStatus.UNMATCHED,
                confidence = 0,
                note = "अज्ञात Customer ID '$codeTrimmed' — मैन्युअल किसान चयन आवश्यक"
            )
        }

        val cleanName = rawName?.trim()?.takeIf { it.length >= 3 }
        if (cleanName != null) {
            val nameCandidates = farmers.filter { it.fullName.equals(cleanName, ignoreCase = true) }
            if (nameCandidates.size == 1) {
                return CustomerResolution(
                    matchedFarmer = nameCandidates.first(),
                    status = CustomerIdMatchStatus.AMBIGUOUS_MATCH,
                    confidence = 65,
                    candidates = nameCandidates,
                    note = "Farmer ID अनुपस्थित; नाम '${cleanName}' से संभावित मिलान — मैन्युअल पुष्टि आवश्यक"
                )
            }
        }

        return CustomerResolution(
            matchedFarmer = null,
            status = CustomerIdMatchStatus.UNMATCHED,
            confidence = 0,
            note = "पर्ची पर Farmer ID / F_ID नहीं मिली — मैन्युअल किसान चयन आवश्यक"
        )
    }

    /**
     * Checks existing collections & scan queue for duplicates based on receipt/transaction ID,
     * farmerId, date, time, shift, and weight/fat/amount details — NEVER merging two distinct milk transactions.
     */
    fun checkDuplicate(
        farmerId: String?,
        normalizedDate: String?,
        shift: String?,
        transactionId: String?,
        receiptPrintedDuplicate: Boolean,
        existingCollections: List<MilkCollectionEntry>,
        existingScanRecords: List<ScanRecord>,
        actualTime: String? = null,
        weight: Double? = null,
        fat: Double? = null,
        amount: Double? = null
    ): DuplicateCheckResult {
        if (receiptPrintedDuplicate) {
            return DuplicateCheckResult(
                status = DuplicateStatus.EXACT_DUPLICATE,
                warningMessage = "पर्ची पर 'DUPLICATE' प्रिंट है — डुप्लीकेट प्रविष्टि अवरुद्ध"
            )
        }

        val cleanTxn = transactionId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanTxn != null) {
            val exactTxnEntry = existingCollections.firstOrNull { existing ->
                existing.receiptNumber.equals(cleanTxn, ignoreCase = true) ||
                    (!existing.transactionId.isNullOrBlank() &&
                        existing.transactionId.equals(cleanTxn, ignoreCase = true) &&
                        (farmerId == null || existing.farmerId.equals(farmerId, ignoreCase = true)))
            }
            if (exactTxnEntry != null) {
                return DuplicateCheckResult(
                    status = DuplicateStatus.EXACT_DUPLICATE,
                    conflictingEntry = exactTxnEntry,
                    warningMessage = "सटीक डुप्लीकेट: रसीद/TXN ID '$cleanTxn' पहले से ${exactTxnEntry.receiptNumber} में दर्ज है!"
                )
            }
        }

        val cleanTimeMin = ShiftTimingClassifier.parseTimeToMinutes(actualTime)

        if (!farmerId.isNullOrBlank() && !normalizedDate.isNullOrBlank() && !shift.isNullOrBlank()) {
            val sameSlotEntries = existingCollections.filter { existing ->
                existing.farmerId.equals(farmerId, ignoreCase = true) &&
                    existing.date == normalizedDate &&
                    existing.shift.equals(shift, ignoreCase = true)
            }

            if (sameSlotEntries.isNotEmpty()) {
                val exactDataMatch = sameSlotEntries.firstOrNull { old ->
                    val oldTimeMin = ShiftTimingClassifier.parseTimeToMinutes(old.actualTime)
                    val sameTime = cleanTimeMin != null && oldTimeMin != null && abs(cleanTimeMin - oldTimeMin) <= 2
                    val sameMetrics = weight != null && fat != null && amount != null &&
                        abs(old.quantity - weight) <= 0.02 &&
                        abs(old.fat - fat) <= 0.05 &&
                        abs(old.totalAmount - amount) <= 0.10
                    sameTime || sameMetrics
                }
                if (exactDataMatch != null) {
                    return DuplicateCheckResult(
                        status = DuplicateStatus.EXACT_DUPLICATE,
                        conflictingEntry = exactDataMatch,
                        warningMessage = "सटीक डुप्लीकेट: किसान $farmerId की $normalizedDate ($shift) की समान मात्रा (${exactDataMatch.quantity}L, ₹${exactDataMatch.totalAmount}) पहले से ${exactDataMatch.receiptNumber} में दर्ज है!"
                    )
                }

                // Distinct legitimate delivery in same shift -> allow separately without merging!
                val firstOld = sameSlotEntries.first()
                val hasDistinctProof = (cleanTxn != null && firstOld.transactionId != null && !cleanTxn.equals(firstOld.transactionId, ignoreCase = true)) ||
                    (cleanTimeMin != null && ShiftTimingClassifier.parseTimeToMinutes(firstOld.actualTime)?.let { abs(cleanTimeMin - it) > 2 } == true) ||
                    (weight != null && abs(firstOld.quantity - weight) > 0.02)

                return if (hasDistinctProof) {
                    DuplicateCheckResult(
                        status = DuplicateStatus.SEPARATE_TRANSACTION,
                        conflictingEntry = firstOld,
                        warningMessage = "नोट: किसान $farmerId की $normalizedDate ($shift) में एक अलग प्रविष्टि (${firstOld.receiptNumber}: ${firstOld.quantity}L) पहले से है — इसे अलग लेनदेन के रूप में सहेजा जाएगा (Merge नहीं होगा)।"
                    )
                } else {
                    DuplicateCheckResult(
                        status = DuplicateStatus.POTENTIAL_DUPLICATE,
                        conflictingEntry = firstOld,
                        warningMessage = "संभावित डुप्लीकेट: किसान $farmerId के लिए $normalizedDate ($shift) की प्रविष्टि पहले से मौजूद है (${firstOld.receiptNumber})।"
                    )
                }
            }

            val pendingScan = existingScanRecords.firstOrNull { scan ->
                scan.verificationStatus in listOf("PENDING_ADMIN", "AUTO_VERIFIED", "ADMIN_VERIFIED") &&
                    scan.farmerId.equals(farmerId, ignoreCase = true) &&
                    scan.date == normalizedDate &&
                    scan.shift.equals(shift, ignoreCase = true) &&
                    ((cleanTxn != null && scan.transactionId.equals(cleanTxn, ignoreCase = true)) ||
                        (weight != null && scan.weight != null && abs(scan.weight - weight) <= 0.02))
            }
            if (pendingScan != null) {
                return DuplicateCheckResult(
                    status = DuplicateStatus.POTENTIAL_DUPLICATE,
                    warningMessage = "संभावित डुप्लीकेट: इसी किसान ($farmerId), तारीख ($normalizedDate) और शिफ्ट ($shift) का स्कैन (${pendingScan.recordId}) पहले से कतार में है।"
                )
            }
        }

        return DuplicateCheckResult(status = DuplicateStatus.NONE)
    }

    fun verifyScan(
        ocr: OcrScanResult,
        farmers: List<Farmer>,
        mappings: List<FarmerMachineMapping>,
        existingCollections: List<MilkCollectionEntry>,
        existingScanRecords: List<ScanRecord>,
        scanMode: String = if (ocr.scanType == ScanType.MACHINE_DISPLAY) "LIVE_DISPLAY" else "PHOTO_DISPLAY",
        sourceImagePath: String? = null,
        referenceYear: Int? = null,
        stationConfig: StationConfig = StationConfig()
    ): ScanVerificationOutcome {
        val errors = mutableListOf<String>()
        errors.addAll(ocr.parseErrors)

        val custRes = resolveCustomer(
            rawCode = ocr.farmerCode,
            rawName = ocr.farmerName,
            farmers = farmers,
            mappings = mappings,
            machineId = ocr.machineNumber
        )
        if (custRes.note != null && custRes.status != CustomerIdMatchStatus.EXACT_ID && custRes.status != CustomerIdMatchStatus.MAPPED_CODE) {
            if (!errors.contains(custRes.note)) errors.add(custRes.note)
        }
        val effectiveCustConf = if (ocr.farmerCode != null) {
            minOf(ocr.customerIdConfidence.coerceAtLeast(1), custRes.confidence)
        } else 0

        val normalizedDate = normalizeDateToIso(ocr.detectedDate, referenceYear)
        val effectiveDateConf = when {
            normalizedDate != null -> ocr.dateConfidence
            ocr.detectedDate != null -> {
                errors.add("तारीख प्रारूप अमान्य/अपूर्ण: '${ocr.detectedDate}'")
                30
            }
            else -> 0
        }

        val expectedAmt = if (ocr.quantity != null && ocr.rate != null) {
            Math.round(ocr.quantity * ocr.rate * 100.0) / 100.0
        } else null

        val diff = if (expectedAmt != null && ocr.totalAmount != null) abs(expectedAmt - ocr.totalAmount) else null
        val tolerance = stationConfig.amountToleranceRs.coerceAtLeast(0.05)

        // Machine rounded-weight precision check (e.g. 2.92 vs 2.924 kg * rate)
        val precisionExplainsDiff = if (diff != null && diff > 0.05 && ocr.rate != null) {
            diff <= (ocr.rate * 0.005) + 0.05 && diff <= tolerance
        } else false

        val amountMismatch = diff != null && diff > tolerance && !precisionExplainsDiff
        if (amountMismatch) {
            val msg = "राशि सत्यापन विफल: वजन (${ocr.quantity}) × दर (${ocr.rate}) = ₹$expectedAmt ≠ मुद्रित ₹${ocr.totalAmount}"
            if (!errors.any { it.contains("राशि") }) errors.add(msg)
        }

        val effectiveAmountConf = when {
            ocr.totalAmount == null -> 0
            amountMismatch -> minOf(ocr.amountConfidence, 68)
            expectedAmt != null -> ocr.amountConfidence
            else -> minOf(ocr.amountConfidence, 80)
        }

        val isBlurry = ocr.imageQuality?.isBlurry == true
        val isTooDark = ocr.imageQuality?.isTooDark == true
        if (isTooDark) {
            errors.add("छवि में रोशनी कम है — कृपया रीडिंग मिलाएं")
        }

        val dupCheck = checkDuplicate(
            farmerId = custRes.matchedFarmer?.farmerId,
            normalizedDate = normalizedDate,
            shift = ocr.shift,
            transactionId = ocr.transactionId,
            receiptPrintedDuplicate = ocr.isDuplicate,
            existingCollections = existingCollections,
            existingScanRecords = existingScanRecords,
            actualTime = ocr.detectedTime,
            weight = ocr.quantity,
            fat = ocr.fat,
            amount = ocr.totalAmount
        )
        if (dupCheck.warningMessage != null && !errors.contains(dupCheck.warningMessage)) {
            errors.add(dupCheck.warningMessage)
        }

        val fieldConf = FieldConfidenceMap(
            customerId = effectiveCustConf,
            fat = ocr.fatConfidence,
            snf = ocr.snfConfidence,
            water = ocr.waterConfidence,
            weight = ocr.quantityConfidence,
            rate = ocr.rateConfidence,
            amount = effectiveAmountConf,
            date = effectiveDateConf,
            time = ocr.timeConfidence,
            shift = ocr.shiftConfidence
        )

        val mandatoryScores = listOf(
            fieldConf.customerId, fieldConf.fat, fieldConf.snf,
            fieldConf.weight, fieldConf.rate, fieldConf.amount,
            fieldConf.date, fieldConf.shift
        )
        var overallConf = mandatoryScores.average().toInt()
        if (isBlurry) overallConf = minOf(overallConf, 52)
        if (amountMismatch) overallConf = minOf(overallConf, 74)
        if (dupCheck.status == DuplicateStatus.EXACT_DUPLICATE) overallConf = minOf(overallConf, 45)
        else if (dupCheck.status == DuplicateStatus.POTENTIAL_DUPLICATE) overallConf = minOf(overallConf, 72)

        val hasMissingCritical = ocr.fat == null || ocr.snf == null || ocr.quantity == null ||
            normalizedDate == null || ocr.shift == null
        val hasCriticalLowScore = mandatoryScores.any { it in 1..49 }

        val tier = when {
            isBlurry ||
                custRes.matchedFarmer == null ||
                dupCheck.status == DuplicateStatus.EXACT_DUPLICATE ||
                hasMissingCritical ||
                hasCriticalLowScore ||
                overallConf < 60 -> ConfidenceTier.LOW

            (custRes.status == CustomerIdMatchStatus.EXACT_ID || custRes.status == CustomerIdMatchStatus.MAPPED_CODE) &&
                !amountMismatch &&
                ocr.rate != null &&
                ocr.totalAmount != null &&
                (dupCheck.status == DuplicateStatus.NONE || dupCheck.status == DuplicateStatus.SEPARATE_TRANSACTION) &&
                !isTooDark &&
                mandatoryScores.all { it >= 85 } &&
                overallConf >= 85 -> ConfidenceTier.HIGH

            else -> ConfidenceTier.MEDIUM
        }

        val canAutoSave = (tier == ConfidenceTier.HIGH) && dupCheck.status == DuplicateStatus.NONE
        val canDirectSaveWithConfirm = (
            custRes.matchedFarmer != null &&
                dupCheck.status != DuplicateStatus.EXACT_DUPLICATE &&
                ocr.fat != null &&
                ocr.snf != null &&
                ocr.quantity != null &&
                ocr.rate != null &&
                ocr.totalAmount != null &&
                normalizedDate != null &&
                ocr.shift != null
            )

        val updatedFieldDetails = ocr.fieldDetails.map { detail ->
            when (detail.key) {
                "farmerId" -> detail.copy(
                    validationStatus = when (custRes.status) {
                        CustomerIdMatchStatus.EXACT_ID, CustomerIdMatchStatus.MAPPED_CODE -> FieldValidationStatus.VALID
                        CustomerIdMatchStatus.AMBIGUOUS_MATCH -> FieldValidationStatus.WARNING
                        CustomerIdMatchStatus.UNMATCHED -> FieldValidationStatus.UNMATCHED
                    },
                    validationNote = custRes.note
                )
                "farmerName" -> {
                    val resolvedName = custRes.matchedFarmer?.fullName ?: detail.value
                    detail.copy(
                        value = resolvedName,
                        displayValue = resolvedName ?: "Not Readable",
                        validationStatus = if (resolvedName != null) FieldValidationStatus.VALID else FieldValidationStatus.UNREADABLE
                    )
                }
                else -> detail
            }
        }

        val verificationStatus = when {
            dupCheck.status == DuplicateStatus.EXACT_DUPLICATE -> "DUPLICATE_BLOCKED"
            canAutoSave -> "AUTO_VERIFIED"
            else -> "PENDING_ADMIN"
        }

        val record = ScanRecord(
            recordId = "SCN-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}",
            farmerId = custRes.matchedFarmer?.farmerId,
            customerCode = ocr.farmerCode ?: "UNKNOWN",
            machineId = ocr.machineNumber ?: "UNKNOWN",
            stationId = ocr.stationId ?: "UNKNOWN",
            transactionId = ocr.transactionId ?: "",
            date = normalizedDate ?: ocr.detectedDate ?: "UNKNOWN",
            time = ocr.detectedTime ?: "UNKNOWN",
            shift = ocr.shift ?: "UNKNOWN",
            animalType = ocr.milkType ?: custRes.matchedFarmer?.milkCategory ?: "UNKNOWN",
            fat = ocr.fat,
            snf = ocr.snf,
            water = ocr.water,
            weight = ocr.quantity,
            rate = ocr.rate,
            amount = ocr.totalAmount,
            expectedAmount = expectedAmt,
            rawOcrText = ocr.rawText,
            fieldConfidence = fieldConf.toJson(),
            overallConfidence = overallConf,
            confidenceTier = tier.name,
            verificationStatus = verificationStatus,
            verificationErrors = errors.distinct().joinToString(" | "),
            duplicateWarning = dupCheck.warningMessage,
            sourceImage = sourceImagePath,
            scanMode = scanMode,
            createdAt = System.currentTimeMillis()
        )

        return ScanVerificationOutcome(
            scanRecord = record,
            confidenceTier = tier,
            fieldConfidences = fieldConf,
            customerResolution = custRes,
            duplicateCheck = dupCheck,
            normalizedDate = normalizedDate,
            expectedAmount = expectedAmt,
            amountMismatch = amountMismatch,
            precisionRoundingApplied = precisionExplainsDiff,
            errors = errors.distinct(),
            canAutoSave = canAutoSave,
            canDirectSaveWithConfirm = canDirectSaveWithConfirm,
            fieldDetails = updatedFieldDetails
        )
    }

    /**
     * Verifies a multi-row Monthly Customer Report with row-level duplicate detection
     * and strict total reconciliation (entries, avg fat, avg snf, total weight, total amount).
     */
    fun verifyCustomerReport(
        ocr: OcrScanResult,
        farmers: List<Farmer>,
        mappings: List<FarmerMachineMapping>,
        existingCollections: List<MilkCollectionEntry>,
        sourceImagePath: String? = null,
        fallbackYear: Int = 2026
    ): ReportVerificationOutcome {
        val rep = ocr.customerReport ?: CustomerReportResult(rawText = ocr.rawText)
        val errors = mutableListOf<String>()
        errors.addAll(ocr.parseErrors)

        val custRes = resolveCustomer(
            rawCode = rep.farmerCode ?: ocr.farmerCode,
            rawName = rep.farmerName ?: ocr.farmerName,
            farmers = farmers,
            mappings = mappings,
            machineId = ocr.machineNumber
        )
        if (custRes.note != null && custRes.status != CustomerIdMatchStatus.EXACT_ID && custRes.status != CustomerIdMatchStatus.MAPPED_CODE) {
            if (!errors.contains(custRes.note)) errors.add(custRes.note)
        }

        val reportYear = extractYearFromDate(rep.endDate)
            ?: extractYearFromDate(rep.startDate)
            ?: fallbackYear

        val verifiedRows = rep.rows.map { row ->
            val isoDate = normalizeDateToIso(row.date, reportYear)
            val matchingSlot = if (custRes.matchedFarmer != null && isoDate != null) {
                existingCollections.firstOrNull {
                    it.farmerId.equals(custRes.matchedFarmer.farmerId, ignoreCase = true) &&
                        it.date == isoDate &&
                        it.shift.equals(row.shift, ignoreCase = true)
                }
            } else null

            val dupStatus = when {
                matchingSlot == null -> DuplicateStatus.NONE
                abs(matchingSlot.quantity - row.weight) <= 0.02 && abs(matchingSlot.fat - row.fat) <= 0.05 ->
                    DuplicateStatus.EXACT_DUPLICATE
                else -> DuplicateStatus.POTENTIAL_DUPLICATE
            }
            VerifiedReportRow(
                row = row,
                normalizedDate = isoDate,
                duplicateStatus = dupStatus,
                existingReceiptNumber = matchingSlot?.receiptNumber
            )
        }

        val dupCount = verifiedRows.count { it.duplicateStatus != DuplicateStatus.NONE }
        if (dupCount > 0) {
            errors.add("$dupCount पंक्तियाँ पहले से रिकॉर्ड में मौजूद हैं (डुप्लीकेट जांच आवश्यक)")
        }

        val isBlurry = ocr.imageQuality?.isBlurry == true
        val reconciled = rep.isFullyReconciled

        val tier = when {
            isBlurry || custRes.matchedFarmer == null || rep.rows.isEmpty() -> ConfidenceTier.LOW
            (custRes.status == CustomerIdMatchStatus.EXACT_ID || custRes.status == CustomerIdMatchStatus.MAPPED_CODE) &&
                reconciled && dupCount == 0 && ocr.overallConfidence >= 85 -> ConfidenceTier.HIGH
            else -> ConfidenceTier.MEDIUM
        }

        val fieldConf = FieldConfidenceMap(
            customerId = minOf(ocr.customerIdConfidence.coerceAtLeast(if (rep.farmerCode != null) 85 else 0), custRes.confidence),
            fat = ocr.fatConfidence,
            snf = ocr.snfConfidence,
            weight = ocr.quantityConfidence,
            amount = ocr.amountConfidence,
            date = ocr.dateConfidence
        )

        val overall = when (tier) {
            ConfidenceTier.HIGH -> ocr.overallConfidence.coerceAtLeast(90)
            ConfidenceTier.MEDIUM -> minOf(ocr.overallConfidence.coerceAtLeast(65), 82)
            ConfidenceTier.LOW -> minOf(ocr.overallConfidence, 55)
        }

        val verificationStatus = when {
            !reconciled -> "PARTIAL_MISMATCH"
            tier == ConfidenceTier.HIGH -> "FULLY_IMPORTED"
            else -> "PENDING_ADMIN"
        }

        val record = ScanRecord(
            recordId = "RPT-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}",
            farmerId = custRes.matchedFarmer?.farmerId,
            customerCode = rep.farmerCode ?: ocr.farmerCode ?: "UNKNOWN",
            machineId = "REPORT-${rep.reportNumber ?: "NA"}",
            stationId = rep.stationId ?: ocr.stationId ?: "UNKNOWN",
            transactionId = rep.reportNumber ?: "",
            date = normalizeDateToIso(rep.startDate, reportYear) ?: rep.startDate ?: "UNKNOWN",
            time = normalizeDateToIso(rep.endDate, reportYear) ?: rep.endDate ?: "UNKNOWN",
            shift = "REPORT_${rep.rows.size}_ROWS",
            animalType = custRes.matchedFarmer?.milkCategory ?: "UNKNOWN",
            fat = if (rep.averageFat > 0) rep.averageFat else rep.calculatedAvgFat.takeIf { it > 0 },
            snf = if (rep.averageSnf > 0) rep.averageSnf else rep.calculatedAvgSnf.takeIf { it > 0 },
            water = null,
            weight = if (rep.totalWeight > 0) rep.totalWeight else rep.calculatedWeightSum,
            rate = null,
            amount = if (rep.totalAmount > 0) rep.totalAmount else rep.calculatedAmountSum.takeIf { it > 0 },
            expectedAmount = null,
            rawOcrText = ocr.rawText,
            fieldConfidence = fieldConf.toJson(),
            overallConfidence = overall,
            confidenceTier = tier.name,
            verificationStatus = verificationStatus,
            verificationErrors = errors.distinct().joinToString(" | "),
            duplicateWarning = if (dupCount > 0) "$dupCount डुप्लीकेट/असमान पंक्तियाँ" else null,
            sourceImage = sourceImagePath,
            scanMode = "CUSTOMER_REPORT",
            createdAt = System.currentTimeMillis()
        )

        val canBatchSave = custRes.matchedFarmer != null && verifiedRows.isNotEmpty() && !isBlurry && reconciled
        return ReportVerificationOutcome(
            scanRecord = record,
            confidenceTier = tier,
            customerResolution = custRes,
            verifiedRows = verifiedRows,
            duplicateRowCount = dupCount,
            calculatedWeightSum = rep.calculatedWeightSum,
            calculatedAvgFat = rep.calculatedAvgFat,
            calculatedAvgSnf = rep.calculatedAvgSnf,
            calculatedAmountSum = rep.calculatedAmountSum,
            isFullyReconciled = reconciled,
            reconciliationErrors = rep.reconciliationErrors,
            errors = errors.distinct(),
            canBatchSave = canBatchSave
        )
    }
}
