package com.example.data.repository

import com.example.data.db.DairyDao
import com.example.data.model.AppNotification
import com.example.data.model.AuditLog
import com.example.data.model.ExpenseRecord
import com.example.data.model.Farmer
import com.example.data.model.FarmerMachineMapping
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.PaymentRecord
import com.example.data.model.RateChartConfig
import com.example.data.model.ScanRecord
import com.example.data.model.StationConfig
import com.example.data.remote.FirebaseSyncRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DairyRepository(
    private val dao: DairyDao,
    private val firebaseSync: FirebaseSyncRepository? = null
) {

    private var activeStationId: String = "NI-TURKI-01"

    fun setActiveStationId(stationId: String) {
        if (stationId.isNotBlank()) activeStationId = stationId
    }

    // Farmers
    val allFarmers: Flow<List<Farmer>> = dao.getAllFarmersFlow()

    suspend fun getAllFarmersSnapshot(): List<Farmer> = dao.getAllFarmers()

    suspend fun getFarmerById(id: String): Farmer? = dao.getFarmerById(id)

    suspend fun saveFarmer(farmer: Farmer, operator: String) {
        val existing = dao.getFarmerById(farmer.farmerId)
        val localFarmer = farmer.copy(isSynced = false)
        dao.insertFarmer(localFarmer)
        val action = if (existing == null) "FARMER_REGISTERED" else "FARMER_UPDATED"
        dao.insertAuditLog(
            AuditLog(
                action = action,
                details = "किसान ${farmer.fullName} (${farmer.farmerId}) सहेजा गया।",
                operatorName = operator,
                oldValue = existing?.let { "${it.fullName} (${it.mobileNumber})" } ?: "",
                newValue = "${farmer.fullName} (${farmer.mobileNumber})",
                recordRef = farmer.farmerId
            )
        )
        firebaseSync?.pushFarmer(localFarmer, activeStationId)
    }

    suspend fun deleteFarmer(farmer: Farmer, operator: String) {
        dao.deleteFarmer(farmer)
        dao.insertAuditLog(
            AuditLog(
                action = "FARMER_DELETED",
                details = "किसान ${farmer.fullName} (${farmer.farmerId}) हटाया गया।",
                operatorName = operator,
                oldValue = "${farmer.fullName} (${farmer.farmerId})",
                recordRef = farmer.farmerId
            )
        )
        firebaseSync?.deleteFarmerRemote(farmer.farmerId)
    }

    suspend fun generateNextFarmerId(): String {
        val existingIds = dao.getAllFarmers().map { it.farmerId }.toSet()
        var index = dao.getFarmersCount() + 1
        var candidate = String.format(Locale.US, "F%03d", index)
        while (existingIds.contains(candidate)) {
            index++
            candidate = String.format(Locale.US, "F%03d", index)
        }
        return candidate
    }

    // Farmer <-> Machine ID Mappings
    val allMachineMappings: Flow<List<FarmerMachineMapping>> = dao.getAllMachineMappingsFlow()

    suspend fun getAllMachineMappingsSnapshot(): List<FarmerMachineMapping> = dao.getAllMachineMappings()

    suspend fun saveMachineMapping(mapping: FarmerMachineMapping, adminName: String) {
        dao.insertMachineMapping(mapping)
        dao.insertAuditLog(
            AuditLog(
                action = "MACHINE_ID_MAPPED",
                details = "मशीन कोड ${mapping.machineCustomerCode} (${mapping.machineId}) → किसान ${mapping.farmerId}",
                operatorName = adminName,
                newValue = "${mapping.machineCustomerCode} -> ${mapping.farmerId}",
                recordRef = mapping.machineCustomerCode
            )
        )
    }

    suspend fun deleteMachineMapping(mapping: FarmerMachineMapping, adminName: String) {
        dao.deleteMachineMapping(mapping)
        dao.insertAuditLog(
            AuditLog(
                action = "MACHINE_MAPPING_DELETED",
                details = "मशीन मैपिंग ${mapping.machineCustomerCode} हटाई गई।",
                operatorName = adminName,
                oldValue = "${mapping.machineCustomerCode} -> ${mapping.farmerId}",
                recordRef = mapping.machineCustomerCode
            )
        )
    }

    // Collections
    val allCollections: Flow<List<MilkCollectionEntry>> = dao.getAllCollectionsFlow()

    suspend fun getAllCollectionsSnapshot(): List<MilkCollectionEntry> = dao.getAllCollections()

    fun getCollectionsByDate(date: String): Flow<List<MilkCollectionEntry>> =
        dao.getCollectionsByDateFlow(date)

    fun getCollectionsByDateAndShift(date: String, shift: String): Flow<List<MilkCollectionEntry>> =
        dao.getCollectionsByDateAndShiftFlow(date, shift)

    fun getCollectionsByMonthYear(month: Int, year: Int): Flow<List<MilkCollectionEntry>> =
        dao.getCollectionsByMonthYearFlow(month, year)

    fun getCollectionsByFarmer(farmerId: String): Flow<List<MilkCollectionEntry>> =
        dao.getCollectionsByFarmerFlow(farmerId)

    private fun withDerivedMonthYear(entry: MilkCollectionEntry): MilkCollectionEntry {
        if (entry.year > 0 && entry.month in 1..12) return entry
        val parts = entry.date.split("-")
        val derivedYear = parts.getOrNull(0)?.toIntOrNull() ?: entry.year
        val derivedMonth = parts.getOrNull(1)?.toIntOrNull() ?: entry.month
        return entry.copy(
            year = if (entry.year > 0) entry.year else derivedYear,
            month = if (entry.month in 1..12) entry.month else derivedMonth
        )
    }

    suspend fun saveCollection(entry: MilkCollectionEntry, operator: String) {
        val normalizedEntry = withDerivedMonthYear(entry).copy(isSynced = false)
        val sameDayEntries = dao.getAllCollections().filter {
            it.date == normalizedEntry.date && it.shift == normalizedEntry.shift && it.farmerId == normalizedEntry.farmerId
        }
        val isDuplicateDoubleTap = sameDayEntries.any { existing ->
            existing.receiptNumber != normalizedEntry.receiptNumber &&
                kotlin.math.abs(existing.timestamp - normalizedEntry.timestamp) < 60_000L &&
                existing.quantity == normalizedEntry.quantity &&
                existing.fat == normalizedEntry.fat &&
                existing.snf == normalizedEntry.snf
        }
        if (isDuplicateDoubleTap) {
            dao.insertAuditLog(
                AuditLog(
                    action = "DUPLICATE_ENTRY_BLOCKED",
                    details = "डुप्लिकेट प्रविष्टि रोकी गई: ${normalizedEntry.farmerName} (${normalizedEntry.quantity}L)",
                    operatorName = operator,
                    recordRef = normalizedEntry.receiptNumber
                )
            )
            return
        }
        dao.insertCollection(normalizedEntry)
        dao.insertAuditLog(
            AuditLog(
                action = "MILK_COLLECTED",
                details = "पर्ची ${normalizedEntry.receiptNumber}: ${normalizedEntry.farmerName} (${normalizedEntry.quantity}L, ₹${normalizedEntry.totalAmount})",
                operatorName = operator,
                newValue = "Fat:${normalizedEntry.fat} SNF:${normalizedEntry.snf} Wt:${normalizedEntry.quantity} Amt:${normalizedEntry.totalAmount}",
                recordRef = normalizedEntry.receiptNumber
            )
        )
        firebaseSync?.pushCollection(normalizedEntry, activeStationId)
        syncReportsForDate(normalizedEntry.date)
    }

    suspend fun saveCollections(entries: List<MilkCollectionEntry>, operator: String) {
        val normalizedEntries = entries.map { withDerivedMonthYear(it).copy(isSynced = false) }
        dao.insertCollections(normalizedEntries)
        dao.insertAuditLog(
            AuditLog(
                action = "BATCH_MILK_COLLECTED",
                details = "ग्राहक रिपोर्ट: ${normalizedEntries.size} प्रविष्टियां दर्ज (कुल दूध: ${String.format(Locale.US, "%.2f", normalizedEntries.sumOf { it.quantity })}L, राशि: ₹${String.format(Locale.US, "%.2f", normalizedEntries.sumOf { it.totalAmount })})",
                operatorName = operator,
                newValue = "${normalizedEntries.size} entries saved",
                recordRef = normalizedEntries.firstOrNull()?.receiptNumber ?: ""
            )
        )
        val datesToRefresh = normalizedEntries.map { it.date }.distinct()
        for (item in normalizedEntries) {
            firebaseSync?.pushCollection(item, activeStationId)
        }
        datesToRefresh.forEach { syncReportsForDate(it) }
    }

    suspend fun updateVerifiedCollectionWithAudit(
        oldEntry: MilkCollectionEntry,
        newEntry: MilkCollectionEntry,
        adminName: String
    ) {
        val normalizedNew = withDerivedMonthYear(newEntry).copy(isSynced = false)
        dao.updateCollection(normalizedNew)
        val changes = mutableListOf<String>()
        val oldParts = mutableListOf<String>()
        val newParts = mutableListOf<String>()
        if (oldEntry.farmerId != normalizedNew.farmerId) {
            changes.add("FarmerID"); oldParts.add("ID:${oldEntry.farmerId}"); newParts.add("ID:${normalizedNew.farmerId}")
        }
        if (oldEntry.shift != normalizedNew.shift) {
            changes.add("Shift"); oldParts.add("Shift:${oldEntry.shift}"); newParts.add("Shift:${normalizedNew.shift}")
        }
        if (oldEntry.fat != normalizedNew.fat) {
            changes.add("FAT"); oldParts.add("FAT:${oldEntry.fat}"); newParts.add("FAT:${normalizedNew.fat}")
        }
        if (oldEntry.snf != normalizedNew.snf) {
            changes.add("SNF"); oldParts.add("SNF:${oldEntry.snf}"); newParts.add("SNF:${normalizedNew.snf}")
        }
        if (oldEntry.quantity != normalizedNew.quantity) {
            changes.add("Weight"); oldParts.add("Wt:${oldEntry.quantity}"); newParts.add("Wt:${normalizedNew.quantity}")
        }
        if (oldEntry.rate != normalizedNew.rate) {
            changes.add("Rate"); oldParts.add("Rate:${oldEntry.rate}"); newParts.add("Rate:${normalizedNew.rate}")
        }
        if (oldEntry.totalAmount != normalizedNew.totalAmount) {
            changes.add("Amount"); oldParts.add("Amt:${oldEntry.totalAmount}"); newParts.add("Amt:${normalizedNew.totalAmount}")
        }
        dao.insertAuditLog(
            AuditLog(
                action = "VERIFIED_RECORD_EDITED",
                details = "रसीद ${normalizedNew.receiptNumber} में Admin ($adminName) द्वारा संशोधन: ${changes.joinToString(", ").ifEmpty { "Metadata" }}",
                operatorName = adminName,
                oldValue = oldParts.joinToString("; "),
                newValue = newParts.joinToString("; "),
                recordRef = normalizedNew.receiptNumber
            )
        )
        firebaseSync?.pushCollection(normalizedNew, activeStationId, oldShift = oldEntry.shift)
        syncReportsForDate(normalizedNew.date)
    }

    suspend fun deleteCollection(entry: MilkCollectionEntry, operator: String) {
        dao.deleteCollection(entry)
        dao.insertAuditLog(
            AuditLog(
                action = "COLLECTION_DELETED",
                details = "पर्ची ${entry.receiptNumber} (${entry.farmerName}, ${entry.quantity}L, ₹${entry.totalAmount}) हटाई गई।",
                operatorName = operator,
                oldValue = "Fat:${entry.fat} SNF:${entry.snf} Wt:${entry.quantity} Amt:${entry.totalAmount}",
                recordRef = entry.receiptNumber
            )
        )
        firebaseSync?.deleteCollectionRemote(entry)
        syncReportsForDate(entry.date)
    }

    suspend fun generateReceiptNumber(shift: String, dateStr: String): String {
        val cleanDate = dateStr.replace("-", "")
        val shiftCode = if (shift.uppercase() == "MORNING") "M" else "E"
        val existingReceipts = dao.getAllCollections().map { it.receiptNumber }.toSet()
        var count = dao.getCountByDate(dateStr) + 1
        var candidate = "RCP-$cleanDate-$shiftCode-${String.format(Locale.US, "%03d", count)}"
        while (existingReceipts.contains(candidate)) {
            count++
            candidate = "RCP-$cleanDate-$shiftCode-${String.format(Locale.US, "%03d", count)}"
        }
        return candidate
    }

    // Scan Records & Admin Verification Queue
    val allScanRecords: Flow<List<ScanRecord>> = dao.getAllScanRecordsFlow()
    val pendingScanRecords: Flow<List<ScanRecord>> = dao.getPendingScanRecordsFlow()

    suspend fun getAllScanRecordsSnapshot(): List<ScanRecord> = dao.getAllScanRecords()

    suspend fun saveScanRecord(record: ScanRecord, operator: String) {
        dao.insertScanRecord(record)
        dao.insertAuditLog(
            AuditLog(
                action = "SCAN_RECORDED_${record.confidenceTier}",
                details = "स्कैन ${record.recordId} (${record.scanMode}, कोड: ${record.customerCode}) • स्थिति: ${record.verificationStatus} • Confidence: ${record.overallConfidence}%",
                operatorName = operator,
                newValue = "Wt:${record.weight} FAT:${record.fat} SNF:${record.snf} Amt:${record.amount}",
                recordRef = record.recordId
            )
        )
        if (record.verificationStatus == "PENDING_ADMIN" || record.verificationStatus == "DUPLICATE_BLOCKED") {
            createNotification(
                title = "स्कैन सत्यापन लंबित",
                message = "कोड ${record.customerCode} (${record.date}) Admin अनुमोदन की प्रतीक्षा में है।",
                type = "SCAN"
            )
        }
    }

    suspend fun approveAndSaveScanRecord(
        originalRecord: ScanRecord,
        editedRecord: ScanRecord,
        entry: MilkCollectionEntry,
        adminName: String
    ) {
        val normalizedEntry = withDerivedMonthYear(entry).copy(isSynced = false)
        dao.insertCollection(normalizedEntry)
        val finalized = editedRecord.copy(
            verificationStatus = "ADMIN_VERIFIED",
            verifiedBy = adminName,
            verifiedAt = System.currentTimeMillis(),
            linkedReceiptNumber = normalizedEntry.receiptNumber
        )
        dao.updateScanRecord(finalized)

        val diffOld = "Code:${originalRecord.customerCode} Wt:${originalRecord.weight} FAT:${originalRecord.fat} SNF:${originalRecord.snf} Rate:${originalRecord.rate} Amt:${originalRecord.amount}"
        val diffNew = "Farmer:${entry.farmerId} Wt:${entry.quantity} FAT:${entry.fat} SNF:${entry.snf} Rate:${entry.rate} Amt:${entry.totalAmount} Receipt:${entry.receiptNumber}"

        dao.insertAuditLog(
            AuditLog(
                action = "SCAN_ADMIN_APPROVED",
                details = "Admin $adminName ने स्कैन ${originalRecord.recordId} को सत्यापित कर रसीद ${entry.receiptNumber} में सहेजा।",
                operatorName = adminName,
                oldValue = diffOld,
                newValue = diffNew,
                recordRef = originalRecord.recordId
            )
        )
        firebaseSync?.pushCollection(normalizedEntry, activeStationId)
        syncReportsForDate(normalizedEntry.date)
    }

    suspend fun rejectScanRecord(record: ScanRecord, reason: String, adminName: String) {
        val rejected = record.copy(
            verificationStatus = "REJECTED",
            verifiedBy = adminName,
            verifiedAt = System.currentTimeMillis(),
            verificationErrors = if (record.verificationErrors.isBlank()) reason else "${record.verificationErrors} | अस्वीकृत: $reason"
        )
        dao.updateScanRecord(rejected)
        dao.insertAuditLog(
            AuditLog(
                action = "SCAN_ADMIN_REJECTED",
                details = "Admin $adminName ने स्कैन ${record.recordId} अस्वीकार किया: $reason",
                operatorName = adminName,
                oldValue = record.verificationStatus,
                newValue = "REJECTED ($reason)",
                recordRef = record.recordId
            )
        )
    }

    // Payments
    val allPayments: Flow<List<PaymentRecord>> = dao.getAllPaymentsFlow()

    fun getPaymentsByFarmer(farmerId: String): Flow<List<PaymentRecord>> =
        dao.getPaymentsByFarmerFlow(farmerId)

    suspend fun savePayment(payment: PaymentRecord, operator: String) {
        val localPayment = payment.copy(isSynced = false)
        dao.insertPayment(localPayment)
        dao.insertAuditLog(
            AuditLog(
                action = "PAYMENT_RECORDED",
                details = "भुगतान ${payment.transactionId}: ${payment.farmerName} को ₹${payment.paymentAmount} भुगतान किया गया।",
                operatorName = operator,
                newValue = "₹${payment.paymentAmount} (${payment.paymentMode})",
                recordRef = payment.transactionId
            )
        )
        createNotification(
            title = "नया भुगतान दर्ज",
            message = "${payment.farmerName} को ₹${String.format(Locale.US, "%.2f", payment.paymentAmount)} (${payment.paymentMode}) भुगतान किया गया।",
            type = "PAYMENT"
        )
        firebaseSync?.pushPayment(localPayment, activeStationId)
        syncReportsForDate(payment.paymentDate)
    }

    suspend fun deletePayment(payment: PaymentRecord, operator: String) {
        dao.deletePayment(payment)
        dao.insertAuditLog(
            AuditLog(
                action = "PAYMENT_DELETED",
                details = "भुगतान ${payment.transactionId} हटाया गया।",
                operatorName = operator,
                oldValue = "₹${payment.paymentAmount}",
                recordRef = payment.transactionId
            )
        )
        firebaseSync?.deletePaymentRemote(payment.transactionId)
        syncReportsForDate(payment.paymentDate)
    }

    fun generatePaymentTransactionId(): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()
        return "PAY-$datePart-$randomSuffix"
    }

    // Expenses
    val allExpenses: Flow<List<ExpenseRecord>> = dao.getAllExpensesFlow()

    suspend fun saveExpense(expense: ExpenseRecord, operator: String) {
        val localExpense = expense.copy(isSynced = false)
        dao.insertExpense(localExpense)
        dao.insertAuditLog(
            AuditLog(
                action = "EXPENSE_RECORDED",
                details = "खर्च ${expense.expenseId}: ${expense.title} (₹${expense.amount}, ${expense.category})",
                operatorName = operator,
                newValue = "₹${expense.amount} (${expense.paymentMode})",
                recordRef = expense.expenseId
            )
        )
        createNotification(
            title = "डेयरी खर्च दर्ज",
            message = "${expense.title}: ₹${String.format(Locale.US, "%.2f", expense.amount)} (${expense.category})",
            type = "EXPENSE"
        )
        firebaseSync?.pushExpense(localExpense, activeStationId)
        syncReportsForDate(expense.date)
    }

    suspend fun deleteExpense(expense: ExpenseRecord, operator: String) {
        dao.deleteExpense(expense)
        dao.insertAuditLog(
            AuditLog(
                action = "EXPENSE_DELETED",
                details = "खर्च ${expense.expenseId} (${expense.title}) हटाया गया।",
                operatorName = operator,
                oldValue = "₹${expense.amount}",
                recordRef = expense.expenseId
            )
        )
        firebaseSync?.deleteExpenseRemote(expense.expenseId)
        syncReportsForDate(expense.date)
    }

    fun generateExpenseId(): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()
        return "EXP-$datePart-$randomSuffix"
    }

    // Notifications
    val allNotifications: Flow<List<AppNotification>> = dao.getAllNotificationsFlow()

    suspend fun createNotification(title: String, message: String, type: String = "INFO") {
        val notif = AppNotification(
            notificationId = "NTF-${System.currentTimeMillis()}-${(10..99).random()}",
            title = title,
            message = message,
            type = type,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            isSynced = false
        )
        dao.insertNotification(notif)
        firebaseSync?.pushNotification(notif, activeStationId)
    }

    suspend fun markNotificationRead(notificationId: String) {
        dao.markNotificationRead(notificationId)
        dao.getNotificationById(notificationId)?.let {
            firebaseSync?.pushNotification(it, activeStationId)
        }
    }

    // Rate Charts
    suspend fun getRateChart(category: String): RateChartConfig {
        return dao.getRateChart(category) ?: if (category == "BUFFALO") {
            RateChartConfig(
                category = "BUFFALO",
                baseRate = 58.0,
                baseFat = 6.5,
                fatMultiplier = 6.0,
                baseSnf = 9.0,
                snfMultiplier = 3.5
            )
        } else {
            RateChartConfig(
                category = "COW",
                baseRate = 38.0,
                baseFat = 3.5,
                fatMultiplier = 5.5,
                baseSnf = 8.5,
                snfMultiplier = 3.0
            )
        }
    }

    val allRateCharts: Flow<List<RateChartConfig>> = dao.getAllRateChartsFlow()

    suspend fun updateRateChart(rateChart: RateChartConfig, operator: String) {
        val localChart = rateChart.copy(updatedAt = System.currentTimeMillis(), isSynced = false)
        dao.insertRateChart(localChart)
        dao.insertAuditLog(
            AuditLog(
                action = "RATE_CHART_UPDATED",
                details = "${rateChart.category} रेट चार्ट अपडेट: बेस ₹${rateChart.baseRate}",
                operatorName = operator,
                newValue = "Base:${rateChart.baseRate} FatX:${rateChart.fatMultiplier}",
                recordRef = rateChart.category
            )
        )
        createNotification(
            title = "दूध दर चार्ट अपडेट",
            message = "${rateChart.category} की नई बेस दर ₹${rateChart.baseRate}/L लागू की गई।",
            type = "RATE"
        )
        firebaseSync?.pushRateChart(localChart, activeStationId)
    }

    // Station Settings & Report Snapshots
    suspend fun pushStationSettings(config: StationConfig) {
        activeStationId = config.stationId
        firebaseSync?.pushSettings(config)
    }

    private suspend fun syncReportsForDate(date: String) {
        val sync = firebaseSync ?: return
        if (sync.getCurrentUser() == null || date.length < 7) return
        sync.pushDailyAndMonthlyReports(
            date = date,
            stationId = activeStationId,
            collections = dao.getAllCollections(),
            payments = dao.getAllPayments(),
            expenses = dao.getAllExpenses()
        )
    }

    suspend fun getPendingSyncCount(): Int {
        return dao.getUnsyncedFarmers().size +
            dao.getUnsyncedCollections().size +
            dao.getUnsyncedPayments().size +
            dao.getUnsyncedExpenses().size +
            dao.getUnsyncedRateCharts().size +
            dao.getUnsyncedNotifications().size
    }

    // Audit Logs
    val auditLogs: Flow<List<AuditLog>> = dao.getAuditLogsFlow()

    suspend fun addAuditLog(action: String, details: String, operator: String) {
        dao.insertAuditLog(
            AuditLog(
                action = action,
                details = details,
                operatorName = operator
            )
        )
    }
}


