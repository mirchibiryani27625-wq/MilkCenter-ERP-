package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AppNotification
import com.example.data.model.AuditLog
import com.example.data.model.ExpenseRecord
import com.example.data.model.Farmer
import com.example.data.model.FarmerMachineMapping
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.PaymentRecord
import com.example.data.model.RateChartConfig
import com.example.data.model.ScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DairyDao {

    // Farmers
    @Query("SELECT * FROM farmers ORDER BY farmerId ASC")
    fun getAllFarmersFlow(): Flow<List<Farmer>>

    @Query("SELECT * FROM farmers ORDER BY farmerId ASC")
    suspend fun getAllFarmers(): List<Farmer>

    @Query("SELECT * FROM farmers WHERE isSynced = 0")
    suspend fun getUnsyncedFarmers(): List<Farmer>

    @Query("SELECT * FROM farmers WHERE farmerId = :farmerId LIMIT 1")
    suspend fun getFarmerById(farmerId: String): Farmer?

    @Query("SELECT COUNT(*) FROM farmers")
    suspend fun getFarmersCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarmer(farmer: Farmer)

    @Update
    suspend fun updateFarmer(farmer: Farmer)

    @Delete
    suspend fun deleteFarmer(farmer: Farmer)

    @Query("UPDATE farmers SET isSynced = 1 WHERE farmerId = :farmerId")
    suspend fun markFarmerSynced(farmerId: String)

    // Farmer <-> Machine ID Mapping
    @Query("SELECT * FROM farmer_machine_mappings ORDER BY machineCustomerCode ASC")
    fun getAllMachineMappingsFlow(): Flow<List<FarmerMachineMapping>>

    @Query("SELECT * FROM farmer_machine_mappings")
    suspend fun getAllMachineMappings(): List<FarmerMachineMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachineMapping(mapping: FarmerMachineMapping)

    @Delete
    suspend fun deleteMachineMapping(mapping: FarmerMachineMapping)

    // Milk Collection
    @Query("SELECT * FROM milk_collections ORDER BY timestamp DESC")
    fun getAllCollectionsFlow(): Flow<List<MilkCollectionEntry>>

    @Query("SELECT * FROM milk_collections ORDER BY timestamp DESC")
    suspend fun getAllCollections(): List<MilkCollectionEntry>

    @Query("SELECT * FROM milk_collections WHERE isSynced = 0")
    suspend fun getUnsyncedCollections(): List<MilkCollectionEntry>

    @Query("SELECT * FROM milk_collections WHERE receiptNumber = :receiptNumber LIMIT 1")
    suspend fun getCollectionByReceipt(receiptNumber: String): MilkCollectionEntry?

    @Query("SELECT * FROM milk_collections WHERE date = :date ORDER BY timestamp DESC")
    fun getCollectionsByDateFlow(date: String): Flow<List<MilkCollectionEntry>>

    @Query("SELECT * FROM milk_collections WHERE date = :date AND shift = :shift ORDER BY timestamp DESC")
    fun getCollectionsByDateAndShiftFlow(date: String, shift: String): Flow<List<MilkCollectionEntry>>

    @Query("SELECT * FROM milk_collections WHERE month = :month AND year = :year ORDER BY date DESC, timestamp DESC")
    fun getCollectionsByMonthYearFlow(month: Int, year: Int): Flow<List<MilkCollectionEntry>>

    @Query("SELECT * FROM milk_collections WHERE farmerId = :farmerId ORDER BY timestamp DESC")
    fun getCollectionsByFarmerFlow(farmerId: String): Flow<List<MilkCollectionEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entry: MilkCollectionEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(entries: List<MilkCollectionEntry>)

    @Update
    suspend fun updateCollection(entry: MilkCollectionEntry)

    @Delete
    suspend fun deleteCollection(entry: MilkCollectionEntry)

    @Query("UPDATE milk_collections SET isSynced = 1 WHERE receiptNumber = :receiptNumber")
    suspend fun markCollectionSynced(receiptNumber: String)

    @Query("SELECT COUNT(*) FROM milk_collections WHERE date = :date")
    suspend fun getCountByDate(date: String): Int

    // Scan Records & Admin Verification Queue
    @Query("SELECT * FROM scan_records ORDER BY createdAt DESC")
    fun getAllScanRecordsFlow(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records ORDER BY createdAt DESC")
    suspend fun getAllScanRecords(): List<ScanRecord>

    @Query("SELECT * FROM scan_records WHERE verificationStatus IN ('PENDING_ADMIN', 'DUPLICATE_BLOCKED') OR confidenceTier = 'LOW' ORDER BY createdAt DESC")
    fun getPendingScanRecordsFlow(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE recordId = :recordId LIMIT 1")
    suspend fun getScanRecordById(recordId: String): ScanRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRecord(record: ScanRecord)

    @Update
    suspend fun updateScanRecord(record: ScanRecord)

    @Delete
    suspend fun deleteScanRecord(record: ScanRecord)

    // Payments
    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    fun getAllPaymentsFlow(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    suspend fun getAllPayments(): List<PaymentRecord>

    @Query("SELECT * FROM payments WHERE isSynced = 0")
    suspend fun getUnsyncedPayments(): List<PaymentRecord>

    @Query("SELECT * FROM payments WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getPaymentById(transactionId: String): PaymentRecord?

    @Query("SELECT * FROM payments WHERE farmerId = :farmerId ORDER BY timestamp DESC")
    fun getPaymentsByFarmerFlow(farmerId: String): Flow<List<PaymentRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentRecord)

    @Delete
    suspend fun deletePayment(payment: PaymentRecord)

    @Query("UPDATE payments SET isSynced = 1 WHERE transactionId = :transactionId")
    suspend fun markPaymentSynced(transactionId: String)

    // Rate Charts
    @Query("SELECT * FROM rate_charts WHERE category = :category LIMIT 1")
    suspend fun getRateChart(category: String): RateChartConfig?

    @Query("SELECT * FROM rate_charts")
    fun getAllRateChartsFlow(): Flow<List<RateChartConfig>>

    @Query("SELECT * FROM rate_charts")
    suspend fun getAllRateCharts(): List<RateChartConfig>

    @Query("SELECT * FROM rate_charts WHERE isSynced = 0")
    suspend fun getUnsyncedRateCharts(): List<RateChartConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRateChart(rateChart: RateChartConfig)

    @Query("UPDATE rate_charts SET isSynced = 1 WHERE category = :category")
    suspend fun markRateChartSynced(category: String)

    // Expenses
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpensesFlow(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpenses(): List<ExpenseRecord>

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseRecord>

    @Query("SELECT * FROM expenses WHERE expenseId = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: String): ExpenseRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseRecord)

    @Delete
    suspend fun deleteExpense(expense: ExpenseRecord)

    @Query("UPDATE expenses SET isSynced = 1 WHERE expenseId = :expenseId")
    suspend fun markExpenseSynced(expenseId: String)

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT 100")
    fun getAllNotificationsFlow(): Flow<List<AppNotification>>

    @Query("SELECT * FROM notifications WHERE isSynced = 0")
    suspend fun getUnsyncedNotifications(): List<AppNotification>

    @Query("SELECT * FROM notifications WHERE notificationId = :notificationId LIMIT 1")
    suspend fun getNotificationById(notificationId: String): AppNotification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification)

    @Query("UPDATE notifications SET isRead = 1, isSynced = 0 WHERE notificationId = :notificationId")
    suspend fun markNotificationRead(notificationId: String)

    @Query("UPDATE notifications SET isSynced = 1 WHERE notificationId = :notificationId")
    suspend fun markNotificationSynced(notificationId: String)

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAuditLogsFlow(): Flow<List<AuditLog>>

    @Insert
    suspend fun insertAuditLog(log: AuditLog)
}
