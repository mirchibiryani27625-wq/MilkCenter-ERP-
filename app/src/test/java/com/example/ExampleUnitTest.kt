package com.example

import com.example.util.OcrParser
import com.example.util.ScanType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testIndividualReceiptOcrParsing() {
        val testReceipt = """
            PRAMOD SINAH
            STN_ID: NI-TURKI-01
            STN_NAME: NAMASTE INDIA
            DATE: 12/11/2026
            TIME: 07:45 AM
            CODE: F001
            FAT: 3.6
            SNF: 7.9
            ADD. WATER: 19%
            M_TYPE: COW
            WT: 2.92
            RT/LT: 35.47
            DUPLICATE: NO
            RS: 103.57
        """.trimIndent()

        val result = OcrParser.parseExtractedText(testReceipt)

        assertEquals(ScanType.INDIVIDUAL_RECEIPT, result.scanType)
        assertEquals(3.6, result.fat!!, 0.01)
        assertEquals(7.9, result.snf!!, 0.01)
        assertEquals(19.0, result.water!!, 0.01)
        assertEquals("COW", result.milkType)
        assertEquals(2.92, result.quantity!!, 0.01)
        assertEquals(35.47, result.rate!!, 0.01)
        assertEquals(103.57, result.totalAmount!!, 0.01)
        assertEquals("PRAMOD SINAH", result.farmerName)
        assertEquals("F001", result.farmerCode)
        assertEquals("NI-TURKI-01", result.stationId)
        assertTrue("Calculated amount should be verified", result.isCalculationVerified)
    }

    @Test
    fun testCustomerReportOcrParsing() {
        val testReport = """
            CUSTOMER REPORT 028
            FARMER CODE: F001
            DATE | FAT | SNF | WEIGHT
            01/11 M 03.4 07.3 008.41
            01/11 E 03.0 07.6 002.56
            02/11 M 03.5 07.5 007.20
            02/11 E 03.8 07.7 006.10
            03/11 M 03.6 07.4 007.50
            03/11 E 03.7 07.8 005.80
            04/11 M 03.5 07.6 006.90
            04/11 E 03.9 07.7 006.30
            05/11 M 03.6 07.5 007.10
            05/11 E 03.8 07.6 005.90
            06/11 M 03.7 07.7 007.30
            06/11 E 03.5 07.5 006.20
            07/11 M 03.6 07.6 007.40
            07/11 E 03.8 07.7 005.76
            TOTAL ENTRIES: 14
            AVERAGE FAT: 3.60
            AVERAGE SNF: 7.58
            TOTAL WEIGHT: 90.43
            TOTAL AMOUNT: 3138.07
        """.trimIndent()

        val result = OcrParser.parseExtractedText(testReport)

        assertEquals(ScanType.CUSTOMER_REPORT, result.scanType)
        val rep = result.customerReport
        assertNotNull(rep)
        assertEquals("028", rep!!.reportNumber)
        assertEquals(14, rep.rows.size)
        assertEquals(14, rep.totalEntries)
        assertEquals(3.60, rep.averageFat, 0.01)
        assertEquals(7.58, rep.averageSnf, 0.01)
        assertEquals(90.43, rep.totalWeight, 0.01)
        assertEquals(3138.07, rep.totalAmount, 0.01)
        assertEquals(90.43, rep.calculatedWeightSum, 0.01)
        assertTrue("Sum of extracted weights should match total weight", rep.isWeightSumMatched)

        // Verify first and second rows
        assertEquals("01/11", rep.rows[0].date)
        assertEquals("MORNING", rep.rows[0].shift)
        assertEquals(3.4, rep.rows[0].fat, 0.01)
        assertEquals(7.3, rep.rows[0].snf, 0.01)
        assertEquals(8.41, rep.rows[0].weight, 0.01)

        assertEquals("01/11", rep.rows[1].date)
        assertEquals("EVENING", rep.rows[1].shift)
        assertEquals(3.0, rep.rows[1].fat, 0.01)
        assertEquals(7.6, rep.rows[1].snf, 0.01)
        assertEquals(2.56, rep.rows[1].weight, 0.01)
    }

    @Test
    fun testAntiFakeOcrNeverFakesMissingFields() {
        val incompleteSlip = """
            NAMASTE INDIA DAIRY
            FAT: 4.1
            WT: 5.00
        """.trimIndent()

        val result = OcrParser.parseExtractedText(incompleteSlip)
        assertEquals(4.1, result.fat!!, 0.01)
        assertEquals(5.0, result.quantity!!, 0.01)
        assertNull("Missing Customer ID must stay null", result.farmerCode)
        assertNull("Missing SNF must stay null", result.snf)
        assertNull("Missing Rate must stay null", result.rate)
        assertNull("Missing Amount must stay null", result.totalAmount)
        assertNull("Missing Date must stay null", result.detectedDate)
        assertNull("Missing Shift must never be guessed", result.shift)
        assertTrue(result.requiresManualCheck)
    }

    @Test
    fun testMultiRowCustomerReportOver25Rows() {
        val rowsText = (1..26).joinToString("\n") { day ->
            val d = String.format("%02d/11", day)
            val s = if (day % 2 == 1) "M" else "E"
            "$d $s 04.0 08.5 010.00"
        }
        val report = """
            CUSTOMER REPORT 099
            STATION_ID: NI-TURKI-01
            CUST_ID: F002
            START_DATE: 01/11/2026
            END_DATE: 26/11/2026
            $rowsText
            TOTAL ENTRIES: 26
            AVERAGE FAT: 4.00
            AVERAGE SNF: 8.50
            TOTAL WEIGHT: 260.00
            TOTAL AMOUNT: 10920.00
        """.trimIndent()

        val result = OcrParser.parseExtractedText(report)
        assertEquals(ScanType.CUSTOMER_REPORT, result.scanType)
        assertEquals(26, result.customerReport?.rows?.size)
        assertEquals("F002", result.customerReport?.farmerCode)
        assertEquals(260.0, result.customerReport?.calculatedWeightSum ?: 0.0, 0.01)
        assertTrue(result.customerReport?.isWeightSumMatched == true)
    }

    @Test
    fun testVerificationEngineHighMediumLowAndDuplicateProtection() {
        val farmers = listOf(
            com.example.data.model.Farmer("F001", "प्रमोद सिंह", "9876543210", "तुर्की"),
            com.example.data.model.Farmer("F002", "रामेश कुमार", "9876543211", "मुजफ्फरपुर")
        )
        val mappings = listOf(
            com.example.data.model.FarmerMachineMapping(
                machineCustomerCode = "M102",
                farmerId = "F002",
                machineId = "MC-01"
            )
        )

        // 1. HIGH confidence scan via Admin mapping M102 -> F002
        val cleanDisplay = OcrParser.parseExtractedText(
            """
            LACTOSCAN S-50
            M_ID: MC-01
            F_ID: M102
            DATE: 14/11/2026
            TIME: 07:30 AM
            FAT: 4.5
            SNF: 8.5
            WATER: 0.0%
            WT: 10.00
            RATE: 42.50
            AMT: 425.00
            TXN_ID: TXN-501
            """.trimIndent()
        )
        val highOutcome = com.example.util.ScanVerificationEngine.verifyScan(
            ocr = cleanDisplay,
            farmers = farmers,
            mappings = mappings,
            existingCollections = emptyList(),
            existingScanRecords = emptyList()
        )
        assertEquals(com.example.util.ConfidenceTier.HIGH, highOutcome.confidenceTier)
        assertTrue(highOutcome.canAutoSave)
        assertEquals("F002", highOutcome.scanRecord.farmerId)
        assertEquals("2026-11-14", highOutcome.normalizedDate)

        // 2. Amount mismatch (10 * 42.50 = 425, printed 500) -> MEDIUM, cannot auto-save
        val mismatchOcr = OcrParser.parseExtractedText(
            """
            F_ID: F001
            DATE: 14/11/2026
            TIME: 07:30 AM
            FAT: 4.5
            SNF: 8.5
            WT: 10.00
            RATE: 42.50
            AMT: 500.00
            """.trimIndent()
        )
        val mismatchOutcome = com.example.util.ScanVerificationEngine.verifyScan(
            ocr = mismatchOcr,
            farmers = farmers,
            mappings = mappings,
            existingCollections = emptyList(),
            existingScanRecords = emptyList()
        )
        assertEquals(com.example.util.ConfidenceTier.MEDIUM, mismatchOutcome.confidenceTier)
        assertFalse(mismatchOutcome.canAutoSave)
        assertTrue(mismatchOutcome.amountMismatch)

        // 3. Duplicate protection (same TXN_ID blocked as EXACT_DUPLICATE, missing TXN_ID blocked as POTENTIAL_DUPLICATE)
        val existing = listOf(
            com.example.data.model.MilkCollectionEntry(
                receiptNumber = "RCP-20261114-M-001",
                date = "2026-11-14",
                shift = "MORNING",
                farmerId = "F002",
                farmerName = "रामेश कुमार",
                milkCategory = "COW",
                quantity = 10.0,
                fat = 4.5,
                snf = 8.5,
                rate = 42.5,
                totalAmount = 425.0,
                transactionId = "TXN-501"
            )
        )
        val exactDupOutcome = com.example.util.ScanVerificationEngine.verifyScan(
            ocr = cleanDisplay,
            farmers = farmers,
            mappings = mappings,
            existingCollections = existing,
            existingScanRecords = emptyList()
        )
        assertEquals(com.example.util.DuplicateStatus.EXACT_DUPLICATE, exactDupOutcome.duplicateCheck.status)
        assertEquals(com.example.util.ConfidenceTier.LOW, exactDupOutcome.confidenceTier)
        assertFalse(exactDupOutcome.canAutoSave)
        assertFalse(exactDupOutcome.canDirectSaveWithConfirm)
    }

    @Test
    fun testEverestMachineDisplayRecognition() {
        val everestScreen = """
            EVEREST DPU SMART
            M_ID: EV-202
            F_ID: F003
            DATE: 15/11/2026
            TIME: 06:20 PM
            FAT: 5.8
            SNF: 8.9
            WATER: 0.0%
            WT: 12.50
            RATE: 52.00
            RS: 650.00
        """.trimIndent()

        val result = OcrParser.parseExtractedText(everestScreen)
        assertEquals(ScanType.MACHINE_DISPLAY, result.scanType)
        assertEquals("F003", result.farmerCode)
        assertEquals("EV-202", result.machineNumber)
        assertEquals("EVENING", result.shift)
        assertEquals(5.8, result.fat!!, 0.01)
        assertEquals(8.9, result.snf!!, 0.01)
        assertEquals(0.0, result.water!!, 0.01)
        assertEquals(12.50, result.quantity!!, 0.01)
        assertEquals(52.00, result.rate!!, 0.01)
        assertEquals(650.00, result.totalAmount!!, 0.01)
        assertTrue(result.fieldDetails.any { it.key == "fat" && it.displayValue == "5.8%" })
    }

    @Test
    fun testShiftTimingClassificationDefaultAndAdminConfig() {
        val defaultCfg = com.example.data.model.StationConfig()

        // Default: Morning 04:00 AM - 11:59 AM, Evening 12:00 PM - 11:59 PM
        assertEquals("MORNING", com.example.util.ShiftTimingClassifier.classifyShift("07:45 AM", null, defaultCfg).shift)
        assertEquals("MORNING", com.example.util.ShiftTimingClassifier.classifyShift("11:59 AM", null, defaultCfg).shift)
        assertEquals("EVENING", com.example.util.ShiftTimingClassifier.classifyShift("12:00 PM", null, defaultCfg).shift)
        assertEquals("EVENING", com.example.util.ShiftTimingClassifier.classifyShift("12:15 PM", null, defaultCfg).shift)
        assertEquals("EVENING", com.example.util.ShiftTimingClassifier.classifyShift("18:30", null, defaultCfg).shift)

        // Missing time & missing label must NEVER fall back to phone system clock
        assertNull(com.example.util.ShiftTimingClassifier.classifyShift(null, null, defaultCfg).shift)

        // Admin overrides Evening start to 11:00 AM
        val customCfg = defaultCfg.copy(morningEndHour = 10, morningEndMinute = 59, eveningStartHour = 11, eveningStartMinute = 0)
        assertEquals("EVENING", com.example.util.ShiftTimingClassifier.classifyShift("11:15 AM", null, customCfg).shift)
    }

    @Test
    fun testSeparateTransactionsSameShiftNotMergedVsExactDuplicate() {
        val farmers = listOf(com.example.data.model.Farmer("F001", "प्रमोद सिंह", "9876543210", "तुर्की"))
        val existing = listOf(
            com.example.data.model.MilkCollectionEntry(
                receiptNumber = "RCP-20261115-M-001",
                date = "2026-11-15",
                shift = "MORNING",
                actualTime = "07:30 AM",
                farmerId = "F001",
                farmerName = "प्रमोद सिंह",
                milkCategory = "COW",
                quantity = 10.0,
                fat = 4.2,
                snf = 8.5,
                rate = 40.0,
                totalAmount = 400.0
            )
        )

        // Different can brought later in same shift (08:20 AM, 6.00L, ₹240.00) -> allowed separately
        val secondDelivery = OcrParser.parseExtractedText(
            """
            F_ID: F001
            DATE: 15/11/2026
            TIME: 08:20 AM
            FAT: 4.3
            SNF: 8.6
            WT: 6.00
            RATE: 40.00
            RS: 240.00
            """.trimIndent()
        )
        val sepOutcome = com.example.util.ScanVerificationEngine.verifyScan(
            ocr = secondDelivery,
            farmers = farmers,
            mappings = emptyList(),
            existingCollections = existing,
            existingScanRecords = emptyList()
        )
        assertEquals(com.example.util.DuplicateStatus.SEPARATE_TRANSACTION, sepOutcome.duplicateCheck.status)
        assertTrue("Second distinct delivery in same shift must be saveable separately", sepOutcome.canDirectSaveWithConfirm)

        // Exact re-scan of first slip (07:30 AM, 10.00L, ₹400.00) -> blocked as EXACT_DUPLICATE
        val duplicateScan = OcrParser.parseExtractedText(
            """
            F_ID: F001
            DATE: 15/11/2026
            TIME: 07:30 AM
            FAT: 4.2
            SNF: 8.5
            WT: 10.00
            RATE: 40.00
            RS: 400.00
            """.trimIndent()
        )
        val dupOutcome = com.example.util.ScanVerificationEngine.verifyScan(
            ocr = duplicateScan,
            farmers = farmers,
            mappings = emptyList(),
            existingCollections = existing,
            existingScanRecords = emptyList()
        )
        assertEquals(com.example.util.DuplicateStatus.EXACT_DUPLICATE, dupOutcome.duplicateCheck.status)
        assertFalse(dupOutcome.canDirectSaveWithConfirm)
    }

    @Test
    fun testMonthlyReportReconciliationBlocksMismatch() {
        val farmers = listOf(com.example.data.model.Farmer("F001", "प्रमोद सिंह", "9876543210", "तुर्की"))
        val mismatchedReport = OcrParser.parseExtractedText(
            """
            CUSTOMER REPORT 055
            FARMER CODE: F001
            01/11 M 03.5 07.5 005.00
            01/11 E 03.7 07.7 005.00
            TOTAL ENTRIES: 4
            AVERAGE FAT: 4.80
            AVERAGE SNF: 8.90
            TOTAL WEIGHT: 25.00
            TOTAL AMOUNT: 1000.00
            """.trimIndent()
        )

        val outcome = com.example.util.ScanVerificationEngine.verifyCustomerReport(
            ocr = mismatchedReport,
            farmers = farmers,
            mappings = emptyList(),
            existingCollections = emptyList()
        )

        assertFalse("Mismatched report must not be reconciled", outcome.isFullyReconciled)
        assertFalse("Mismatched report must block full import", outcome.canBatchSave)
        assertEquals("PARTIAL_MISMATCH", outcome.scanRecord.verificationStatus)
        assertTrue(outcome.reconciliationErrors.isNotEmpty())
    }
}
