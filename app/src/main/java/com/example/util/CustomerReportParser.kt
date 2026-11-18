package com.example.util

import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

object CustomerReportParser {

    fun parse(
        raw: String,
        lines: List<String>,
        spatialLines: List<SpatialOcrLine> = emptyList()
    ): OcrScanResult {
        var reportNumber: String? = null
        var farmerName: String? = null
        var farmerCode: String? = null
        var custIdConf = 0
        var stnId: String? = null
        var startDate: String? = null
        var endDate: String? = null
        val rows = mutableListOf<CustomerReportRow>()
        var totalEntries = 0
        var avgFat = 0.0
        var avgSnf = 0.0
        var totalWeight = 0.0
        var totalAmount = 0.0

        Pattern.compile("""(?:CUSTOMER|MONTHLY)\s*REPORT[\s#:]*([A-Z0-9\-_]+)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) reportNumber = it.group(1)?.trim() }

        Pattern.compile("""(?:STN[_\s]*ID|STATION[_\s]*ID)[\s:=]*([A-Z0-9\-_]+)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) stnId = it.group(1)?.trim() }

        Pattern.compile(
            """(?:FARMER[_\s]*(?:CODE|ID)|CUST(?:OMER)?[_\s]*(?:CODE|ID)|F[_\s]*ID|MEM(?:BER)?[_\s]*ID|CODE)[\s:=#]*([A-Z0-9\-_]+)""",
            Pattern.CASE_INSENSITIVE
        ).matcher(raw).let {
            if (it.find()) {
                val cand = it.group(1)?.trim()?.uppercase(Locale.US)
                if (!cand.isNullOrBlank() && !cand.equals(stnId, ignoreCase = true)) {
                    farmerCode = cand
                    custIdConf = 94
                }
            }
        }

        Pattern.compile("""(?:^|\n)\s*(?:FARMER[_\s]*NAME|CUST(?:OMER)?[_\s]*NAME|NAME)\s*[:=]+\s*([^\n\r]+)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) farmerName = it.group(1)?.trim() }

        Pattern.compile("""(?:START[_\s]*DATE|FROM[_\s]*DATE|FROM)[\s:=]*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) startDate = it.group(1)?.trim() }

        Pattern.compile("""(?:END[_\s]*DATE|TO[_\s]*DATE|TO)[\s:=]*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) endDate = it.group(1)?.trim() }

        Pattern.compile("""TOTAL\s*(?:ENTRIES|ROWS|COUNT)[\s:=]*([0-9]+)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) totalEntries = it.group(1)?.toIntOrNull() ?: 0 }

        Pattern.compile("""(?:AVERAGE|AVG|TOTAL)\s*FAT[\s:=]*([0-9]{1,3}(?:\.[0-9]{1,2})?)%?""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) avgFat = it.group(1)?.toDoubleOrNull() ?: 0.0 }

        Pattern.compile("""(?:AVERAGE|AVG|TOTAL)\s*SNF[\s:=]*([0-9]{1,3}(?:\.[0-9]{1,2})?)%?""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) avgSnf = it.group(1)?.toDoubleOrNull() ?: 0.0 }

        Pattern.compile("""TOTAL\s*(?:WEIGHT|WT|MILK|QTY)[\s:=]*([0-9]{1,6}(?:\.[0-9]{1,3})?)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) totalWeight = it.group(1)?.toDoubleOrNull() ?: 0.0 }

        Pattern.compile("""TOTAL\s*(?:AMOUNT|AMT|RS\.?)[\s:=₹]*(?:RS\.?|₹)?\s*([0-9]{1,8}(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
            .matcher(raw).let { if (it.find()) totalAmount = it.group(1)?.toDoubleOrNull() ?: 0.0 }

        // Row pattern: DATE | SHIFT (M or E or MORNING/EVENING) | FAT | SNF | WEIGHT | [RATE] | [AMOUNT]
        val rowRegex = Pattern.compile(
            """(\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?)[\s|]+([MmEe]|MORNING|EVENING|MOR|EVE)[\s|]+(\d{1,2}(?:\.\d{1,2})?)[\s|]+(\d{1,2}(?:\.\d{1,2})?)[\s|]+(\d{1,4}(?:\.\d{1,3})?)(?:[\s|]+(\d{1,3}(?:\.\d{1,2})?))?(?:[\s|]+(\d{1,6}(?:\.\d{1,2})?))?""",
            Pattern.CASE_INSENSITIVE
        )

        lines.forEachIndexed { idx, line ->
            val matcher = rowRegex.matcher(line)
            val spatial = spatialLines.getOrNull(idx)
            while (matcher.find()) {
                val d = matcher.group(1)?.trim() ?: ""
                val shiftToken = matcher.group(2)?.uppercase(Locale.US)?.trim() ?: ""
                val f = matcher.group(3)?.toDoubleOrNull()
                val s = matcher.group(4)?.toDoubleOrNull()
                val w = matcher.group(5)?.toDoubleOrNull()
                val col6 = matcher.group(6)?.toDoubleOrNull()
                val col7 = matcher.group(7)?.toDoubleOrNull()

                // If 6 numeric tokens exist, interpret col6 as RATE and col7 as AMOUNT; if only one extra exists, treat > 150 as AMOUNT else RATE
                val (r, a) = when {
                    col6 != null && col7 != null -> col6 to col7
                    col6 != null && col6 > 150.0 -> 0.0 to col6
                    col6 != null -> col6 to 0.0
                    else -> 0.0 to 0.0
                }

                if (f != null && s != null && w != null && w > 0.0 && (shiftToken.startsWith("M") || shiftToken.startsWith("E"))) {
                    val shiftStr = if (shiftToken.startsWith("M")) "MORNING" else "EVENING"
                    rows.add(
                        CustomerReportRow(
                            date = d,
                            shift = shiftStr,
                            fat = f,
                            snf = s,
                            weight = w,
                            rate = r,
                            totalAmount = a,
                            sourceLocation = spatial?.boundingBoxLabel ?: "Line ${idx + 1}"
                        )
                    )
                }
            }
        }

        if (startDate == null && rows.isNotEmpty()) startDate = rows.first().date
        if (endDate == null && rows.isNotEmpty()) endDate = rows.last().date

        val calculatedSum = Math.round(rows.sumOf { it.weight } * 100.0) / 100.0
        val calculatedAvgFat = if (rows.isNotEmpty()) Math.round(rows.map { it.fat }.average() * 100.0) / 100.0 else 0.0
        val calculatedAvgSnf = if (rows.isNotEmpty()) Math.round(rows.map { it.snf }.average() * 100.0) / 100.0 else 0.0
        val calculatedAmtSum = Math.round(rows.sumOf { it.totalAmount } * 100.0) / 100.0

        val isCountMatched = rows.isNotEmpty() && (totalEntries == 0 || rows.size == totalEntries)
        val isWeightMatched = totalWeight <= 0.0 || abs(calculatedSum - totalWeight) <= 0.05
        val isFatMatched = avgFat <= 0.0 || abs(calculatedAvgFat - avgFat) <= 0.15
        val isSnfMatched = avgSnf <= 0.0 || abs(calculatedAvgSnf - avgSnf) <= 0.15
        val isAmtMatched = totalAmount <= 0.0 || calculatedAmtSum <= 0.0 || abs(calculatedAmtSum - totalAmount) <= 1.0

        val errors = mutableListOf<String>()
        if (farmerCode == null) errors.add("ग्राहक रिपोर्ट में Farmer ID / Code नहीं मिला (Not Readable)")
        if (rows.isEmpty()) errors.add("ग्राहक रिपोर्ट से कोई भी तालिका पंक्ति नहीं पढ़ी जा सकी")
        if (totalEntries > 0 && rows.size != totalEntries) {
            errors.add("प्रविष्टि संख्या अंतर: रिपोर्ट में $totalEntries पंक्तियाँ ≠ पढ़ी गई ${rows.size}")
        }
        if (totalWeight > 0.0 && rows.isNotEmpty() && !isWeightMatched) {
            errors.add("कुल वजन अंतर: पंक्तियों का योग (${calculatedSum}L) ≠ रिपोर्ट कुल वजन (${totalWeight}L)")
        }
        if (avgFat > 0.0 && rows.isNotEmpty() && !isFatMatched) {
            errors.add("FAT मिलान त्रुटि: गणना औसत $calculatedAvgFat% ≠ मुद्रित $avgFat%")
        }
        if (avgSnf > 0.0 && rows.isNotEmpty() && !isSnfMatched) {
            errors.add("SNF मिलान त्रुटि: गणना औसत $calculatedAvgSnf% ≠ मुद्रित $avgSnf%")
        }
        if (totalAmount > 0.0 && calculatedAmtSum > 0.0 && !isAmtMatched) {
            errors.add("कुल राशि अंतर: पंक्ति योग ₹$calculatedAmtSum ≠ मुद्रित ₹$totalAmount")
        }

        val effectiveTotalEntries = if (totalEntries > 0) totalEntries else rows.size
        val isFullyReconciled = rows.isNotEmpty() &&
            farmerCode != null &&
            isCountMatched &&
            isWeightMatched &&
            isFatMatched &&
            isSnfMatched &&
            isAmtMatched

        val customerReportResult = CustomerReportResult(
            reportNumber = reportNumber,
            farmerName = farmerName,
            farmerCode = farmerCode,
            stationId = stnId,
            startDate = startDate,
            endDate = endDate,
            rows = rows,
            totalEntries = effectiveTotalEntries,
            averageFat = avgFat,
            averageSnf = avgSnf,
            totalWeight = totalWeight,
            totalAmount = totalAmount,
            calculatedWeightSum = calculatedSum,
            calculatedAvgFat = calculatedAvgFat,
            calculatedAvgSnf = calculatedAvgSnf,
            calculatedAmountSum = calculatedAmtSum,
            isEntriesCountMatched = isCountMatched,
            isAvgFatMatched = isFatMatched,
            isAvgSnfMatched = isSnfMatched,
            isWeightSumMatched = isWeightMatched,
            isAmountSumMatched = isAmtMatched,
            isFullyReconciled = isFullyReconciled,
            reconciliationErrors = errors.toList(),
            rawText = raw
        )

        val fieldDetails = listOf(
            OcrFieldDetail(
                key = "farmerId",
                label = "Farmer ID",
                value = farmerCode,
                confidence = custIdConf,
                sourceLocation = findSourceLocation("FARMER|CUST|CODE|F_ID", spatialLines),
                validationStatus = if (farmerCode == null) FieldValidationStatus.UNREADABLE else FieldValidationStatus.VALID
            ),
            OcrFieldDetail(
                key = "rows",
                label = "Rows Parsed",
                value = if (rows.isNotEmpty()) "${rows.size} / $effectiveTotalEntries" else null,
                confidence = if (isCountMatched) 95 else 55,
                sourceLocation = "Report Table",
                validationStatus = when {
                    rows.isEmpty() -> FieldValidationStatus.UNREADABLE
                    !isCountMatched -> FieldValidationStatus.MISMATCH
                    else -> FieldValidationStatus.VALID
                }
            ),
            OcrFieldDetail(
                key = "totalWeight",
                label = "Total Weight",
                value = if (totalWeight > 0) "$totalWeight L" else if (calculatedSum > 0) "$calculatedSum L" else null,
                confidence = if (isWeightMatched) 95 else 60,
                sourceLocation = findSourceLocation("TOTAL\\s*(?:WEIGHT|WT)", spatialLines),
                validationStatus = when {
                    rows.isEmpty() && totalWeight <= 0 -> FieldValidationStatus.UNREADABLE
                    !isWeightMatched -> FieldValidationStatus.MISMATCH
                    else -> FieldValidationStatus.VALID
                }
            ),
            OcrFieldDetail(
                key = "totalAmount",
                label = "Total Amount",
                value = if (totalAmount > 0) "₹$totalAmount" else if (calculatedAmtSum > 0) "₹$calculatedAmtSum" else null,
                confidence = if (isAmtMatched) 94 else 62,
                sourceLocation = findSourceLocation("TOTAL\\s*(?:AMOUNT|AMT)", spatialLines),
                validationStatus = when {
                    totalAmount <= 0 && calculatedAmtSum <= 0 -> FieldValidationStatus.UNREADABLE
                    !isAmtMatched -> FieldValidationStatus.MISMATCH
                    else -> FieldValidationStatus.VALID
                }
            )
        )

        return OcrScanResult(
            scanType = ScanType.CUSTOMER_REPORT,
            rawText = raw,
            farmerName = farmerName,
            farmerCode = farmerCode,
            customerIdConfidence = custIdConf,
            stationId = stnId,
            detectedDate = startDate,
            dateConfidence = if (startDate != null) 90 else 0,
            fat = if (avgFat > 0) avgFat else if (calculatedAvgFat > 0) calculatedAvgFat else null,
            fatConfidence = if (isFatMatched && rows.isNotEmpty()) 92 else if (rows.isNotEmpty()) 65 else 0,
            snf = if (avgSnf > 0) avgSnf else if (calculatedAvgSnf > 0) calculatedAvgSnf else null,
            snfConfidence = if (isSnfMatched && rows.isNotEmpty()) 92 else if (rows.isNotEmpty()) 65 else 0,
            quantity = if (totalWeight > 0) totalWeight else if (calculatedSum > 0) calculatedSum else null,
            quantityConfidence = if (isWeightMatched && rows.isNotEmpty()) 96 else if (rows.isNotEmpty()) 68 else 0,
            totalAmount = if (totalAmount > 0) totalAmount else if (calculatedAmtSum > 0) calculatedAmtSum else null,
            amountConfidence = if (isAmtMatched && totalAmount > 0) 94 else if (totalAmount > 0) 65 else 0,
            overallConfidence = when {
                rows.isEmpty() -> 0
                isFullyReconciled -> 95
                isCountMatched && farmerCode != null -> 78
                else -> 58
            },
            requiresManualCheck = errors.isNotEmpty() || !isFullyReconciled,
            warningMessage = errors.firstOrNull(),
            parseErrors = errors,
            fieldDetails = fieldDetails,
            spatialLines = spatialLines,
            customerReport = customerReportResult
        )
    }

    internal fun findSourceLocation(regexPattern: String, spatialLines: List<SpatialOcrLine>): String {
        if (spatialLines.isEmpty()) return "OCR Text"
        val p = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE)
        return spatialLines.firstOrNull { p.matcher(it.text).find() }?.boundingBoxLabel ?: "OCR Text"
    }
}
