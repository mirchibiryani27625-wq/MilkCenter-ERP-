package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farmers")
data class Farmer(
    @PrimaryKey
    val farmerId: String, // e.g. "F001", "F002"
    val fullName: String,
    val mobileNumber: String,
    val villageAddress: String,
    val milkCategory: String = "COW", // "COW" or "BUFFALO"
    val photoUri: String? = null,
    val bankName: String = "",
    val accountHolderName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "milk_collections")
data class MilkCollectionEntry(
    @PrimaryKey
    val receiptNumber: String, // e.g. "RCP-20261114-M-001"
    val date: String, // "YYYY-MM-DD"
    val timestamp: Long = System.currentTimeMillis(),
    val shift: String, // "MORNING" or "EVENING"
    val farmerId: String,
    val farmerName: String,
    val milkCategory: String, // "COW" or "BUFFALO"
    val quantity: Double, // in Liters / Kg
    val fat: Double, // %
    val snf: Double, // %
    val waterPercentage: Double = 0.0, // %
    val rate: Double, // ₹ per Liter
    val totalAmount: Double, // ₹
    val machineAmount: Double? = null,
    val scannedImageUri: String? = null,
    val isOcrScanned: Boolean = false,
    val ocrConfidence: Int = 100,
    val machineNumber: String? = null,
    val isSynced: Boolean = false,
    val actualTime: String = "",
    val transactionId: String? = null,
    val scanRecordId: String? = null,
    val verifiedBy: String? = null,
    val stationId: String? = null,
    val month: Int = 0, // 1..12 extracted from transaction date
    val year: Int = 0, // e.g. 2026 extracted from transaction date
    val sourceType: String = "MANUAL", // "MACHINE_DISPLAY", "MILK_RECEIPT", "MONTHLY_REPORT", "MANUAL"
    val verificationStatus: String = "CONFIRMED" // "CONFIRMED", "AUTO_VERIFIED", "ADMIN_VERIFIED", "PENDING_ADMIN"
)

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey
    val recordId: String, // e.g. "SCN-20261114-001"
    val farmerId: String? = null, // Nullable if Customer ID unmatched!
    val customerCode: String = "UNKNOWN",
    val machineId: String = "UNKNOWN",
    val stationId: String = "UNKNOWN",
    val transactionId: String = "",
    val date: String = "UNKNOWN", // Normalized YYYY-MM-DD or raw / "UNKNOWN"
    val time: String = "UNKNOWN",
    val shift: String = "UNKNOWN", // "MORNING", "EVENING", or "UNKNOWN"
    val animalType: String = "UNKNOWN", // "COW", "BUFFALO", or "UNKNOWN"
    val fat: Double? = null,
    val snf: Double? = null,
    val water: Double? = null,
    val weight: Double? = null,
    val rate: Double? = null,
    val amount: Double? = null,
    val expectedAmount: Double? = null,
    val rawOcrText: String = "",
    val fieldConfidence: String = "{}", // JSON of per-field confidences
    val overallConfidence: Int = 0,
    val confidenceTier: String = "LOW", // "HIGH", "MEDIUM", "LOW"
    val verificationStatus: String = "PENDING_ADMIN", // "AUTO_VERIFIED", "PENDING_ADMIN", "ADMIN_VERIFIED", "REJECTED", "DUPLICATE_BLOCKED"
    val verificationErrors: String = "",
    val duplicateWarning: String? = null,
    val sourceImage: String? = null,
    val scanMode: String = "PHOTO_DISPLAY", // "LIVE_DISPLAY", "PHOTO_DISPLAY", "CUSTOMER_REPORT"
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedBy: String? = null,
    val verifiedAt: Long? = null,
    val linkedReceiptNumber: String? = null
)

@Entity(tableName = "farmer_machine_mappings")
data class FarmerMachineMapping(
    @PrimaryKey
    val machineCustomerCode: String, // Machine F_ID or printed Customer Code (uppercased)
    val farmerId: String, // App Farmer ID e.g. "F002"
    val machineId: String = "ALL",
    val updatedBy: String = "Admin",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payments")
data class PaymentRecord(
    @PrimaryKey
    val transactionId: String, // e.g. "PAY-20261112-001"
    val farmerId: String,
    val farmerName: String,
    val paymentAmount: Double,
    val paymentDate: String, // "YYYY-MM-DD"
    val timestamp: Long = System.currentTimeMillis(),
    val paymentMode: String, // "CASH", "UPI", "BANK TRANSFER"
    val paymentType: String, // "WEEKLY", "ADVANCE", "PARTIAL", "FULL"
    val note: String = "",
    val previousBalance: Double = 0.0,
    val finalBalance: Double = 0.0,
    val isSynced: Boolean = false
)

@Entity(tableName = "rate_charts")
data class RateChartConfig(
    @PrimaryKey
    val category: String, // "COW" or "BUFFALO"
    val baseRate: Double, // Base ₹/L (e.g. 38.0 for Cow, 58.0 for Buffalo)
    val baseFat: Double, // Base Fat % (e.g. 3.5 for Cow, 6.5 for Buffalo)
    val fatMultiplier: Double, // ₹ per 1.0 Fat diff (e.g. 5.5)
    val baseSnf: Double, // Base SNF % (e.g. 8.5 for Cow, 9.0 for Buffalo)
    val snfMultiplier: Double, // ₹ per 1.0 SNF diff (e.g. 3.0)
    val waterDeductionPerPercent: Double = 0.5, // deduction per 1% water
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "expenses")
data class ExpenseRecord(
    @PrimaryKey
    val expenseId: String, // e.g. "EXP-20261118-001"
    val title: String,
    val category: String, // "ELECTRICITY", "SALARY", "MAINTENANCE", "CATTLE_FEED", "OTHER"
    val amount: Double,
    val date: String, // "YYYY-MM-DD"
    val paymentMode: String = "CASH", // "CASH", "UPI", "BANK TRANSFER"
    val note: String = "",
    val recordedBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey
    val notificationId: String,
    val title: String,
    val message: String,
    val type: String = "INFO", // "SYNC", "SCAN", "PAYMENT", "EXPENSE", "RATE", "INFO"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isSynced: Boolean = false
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val action: String,
    val details: String,
    val operatorName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val oldValue: String = "",
    val newValue: String = "",
    val recordRef: String = ""
)

data class StationConfig(
    val dairyName: String = "दुध केंद्र",
    val centerName: String = "नमस्ते इंडिया दूध संग्रहण केंद्र, तुर्की",
    val stationId: String = "NI-TURKI-01",
    val address: String = "तुर्की, मुजफ्फरपुर (बिहार)",
    val contactNumber: String = "9876543210",
    val adminPin: String = "1234",
    val morningStartHour: Int = 4,
    val morningStartMinute: Int = 0,
    val morningEndHour: Int = 11,
    val morningEndMinute: Int = 59,
    val eveningStartHour: Int = 12,
    val eveningStartMinute: Int = 0,
    val eveningEndHour: Int = 23,
    val eveningEndMinute: Int = 59,
    val amountToleranceRs: Double = 0.50
)

enum class UserRole {
    ADMIN, OPERATOR, STAFF
}

data class UserSession(
    val isLoggedIn: Boolean = false,
    val role: UserRole = UserRole.ADMIN,
    val userName: String = "व्यवस्थापक (Admin)",
    val mobileOrEmail: String = "",
    val firebaseUid: String = "",
    val email: String = ""
)

data class AppUserProfile(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "OPERATOR",
    val stationId: String = "NI-TURKI-01",
    val active: Boolean = true,
    val photoUrl: String = "",
    val updatedAtMillis: Long = 0L
)

data class CloudSyncStatus(
    val isOnline: Boolean = false,
    val isSyncing: Boolean = false,
    val isSignedIn: Boolean = false,
    val userEmail: String = "",
    val pendingCount: Int = 0,
    val lastSyncTimeMillis: Long = 0L,
    val lastError: String? = null
)
