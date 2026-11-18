package com.example.util

enum class ScanType {
    INDIVIDUAL_RECEIPT,
    CUSTOMER_REPORT,
    MACHINE_DISPLAY
}

enum class FieldValidationStatus {
    VALID,
    WARNING,
    UNREADABLE,
    MISMATCH,
    UNMATCHED
}

data class OcrFieldDetail(
    val key: String,
    val label: String,
    val value: String?,
    val displayValue: String = value ?: "Not Readable",
    val confidence: Int = 0,
    val sourceLocation: String = "Not Detected",
    val validationStatus: FieldValidationStatus = if (value.isNullOrBlank()) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID,
    val validationNote: String? = null
)

data class SpatialOcrLine(
    val text: String,
    val confidence: Int,
    val boundingBoxLabel: String,
    val centerY: Int = 0,
    val left: Int = 0
)

data class CustomerReportRow(
    val date: String, // e.g. "01/11" or "01/11/2026"
    val shift: String, // "MORNING" or "EVENING" (from M / E)
    val fat: Double,
    val snf: Double,
    val weight: Double,
    val rate: Double = 0.0,
    val totalAmount: Double = 0.0,
    val sourceLocation: String = "OCR Row"
)

data class CustomerReportResult(
    val reportNumber: String? = null,
    val farmerName: String? = null,
    val farmerCode: String? = null,
    val stationId: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val rows: List<CustomerReportRow> = emptyList(),
    val totalEntries: Int = 0,
    val averageFat: Double = 0.0,
    val averageSnf: Double = 0.0,
    val totalWeight: Double = 0.0,
    val totalAmount: Double = 0.0,
    val calculatedWeightSum: Double = 0.0,
    val calculatedAvgFat: Double = 0.0,
    val calculatedAvgSnf: Double = 0.0,
    val calculatedAmountSum: Double = 0.0,
    val isEntriesCountMatched: Boolean = false,
    val isAvgFatMatched: Boolean = false,
    val isAvgSnfMatched: Boolean = false,
    val isWeightSumMatched: Boolean = false,
    val isAmountSumMatched: Boolean = false,
    val isFullyReconciled: Boolean = false,
    val reconciliationErrors: List<String> = emptyList(),
    val rawText: String = ""
)

data class OcrScanResult(
    val scanType: ScanType = ScanType.INDIVIDUAL_RECEIPT,
    val rawText: String = "",
    // Core extracted fields (STRICTLY null when missing or unreadable — NEVER faked!)
    val farmerName: String? = null,
    val farmerCode: String? = null, // Machine F_ID or printed Customer Code
    val customerIdConfidence: Int = 0,
    val stationId: String? = null,
    val stationName: String? = null,
    val machineNumber: String? = null, // Machine ID / M/C / Everest / Lactoscan
    val transactionId: String? = null, // Machine Transaction / Slip / Bill ID
    val detectedDate: String? = null,
    val dateConfidence: Int = 0,
    val detectedTime: String? = null,
    val timeConfidence: Int = 0,
    val shift: String? = null, // "MORNING", "EVENING", or null (UNKNOWN)
    val shiftConfidence: Int = 0,
    val fat: Double? = null,
    val fatConfidence: Int = 0,
    val snf: Double? = null,
    val snfConfidence: Int = 0,
    val water: Double? = null,
    val waterConfidence: Int = 0,
    val milkType: String? = null, // "COW", "BUFFALO", or null (UNKNOWN)
    val quantity: Double? = null, // WT (Weight)
    val quantityConfidence: Int = 0,
    val rate: Double? = null, // RT/LT (Rate Per Liter)
    val rateConfidence: Int = 0,
    val totalAmount: Double? = null, // RS / AMT printed on machine or receipt
    val amountConfidence: Int = 0,
    val calculatedAmount: Double? = null, // WT * RT/LT
    val isCalculationVerified: Boolean = false,
    val isDuplicate: Boolean = false,
    val overallConfidence: Int = 0,
    val requiresManualCheck: Boolean = true,
    val warningMessage: String? = null,
    val parseErrors: List<String> = emptyList(),
    val imageQuality: ImageQualityReport? = null,
    val fieldDetails: List<OcrFieldDetail> = emptyList(),
    val spatialLines: List<SpatialOcrLine> = emptyList(),
    // Customer report fields
    val customerReport: CustomerReportResult? = null
)
