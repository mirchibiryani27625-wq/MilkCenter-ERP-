package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AppNotification
import com.example.data.model.AuditLog
import com.example.data.model.ExpenseRecord
import com.example.data.model.Farmer
import com.example.data.model.FarmerMachineMapping
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.PaymentRecord
import com.example.data.model.RateChartConfig
import com.example.data.model.ScanRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Farmer::class,
        MilkCollectionEntry::class,
        ScanRecord::class,
        FarmerMachineMapping::class,
        PaymentRecord::class,
        RateChartConfig::class,
        ExpenseRecord::class,
        AppNotification::class,
        AuditLog::class
    ],
    version = 4,
    exportSchema = false
)
abstract class DairyDatabase : RoomDatabase() {
    abstract fun dairyDao(): DairyDao

    companion object {
        @Volatile
        private var INSTANCE: DairyDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN actualTime TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN transactionId TEXT")
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN scanRecordId TEXT")
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN verifiedBy TEXT")
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN stationId TEXT")

                db.execSQL("ALTER TABLE audit_logs ADD COLUMN oldValue TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audit_logs ADD COLUMN newValue TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audit_logs ADD COLUMN recordRef TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_records` (
                        `recordId` TEXT NOT NULL,
                        `farmerId` TEXT,
                        `customerCode` TEXT NOT NULL,
                        `machineId` TEXT NOT NULL,
                        `stationId` TEXT NOT NULL,
                        `transactionId` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `time` TEXT NOT NULL,
                        `shift` TEXT NOT NULL,
                        `animalType` TEXT NOT NULL,
                        `fat` REAL,
                        `snf` REAL,
                        `water` REAL,
                        `weight` REAL,
                        `rate` REAL,
                        `amount` REAL,
                        `expectedAmount` REAL,
                        `rawOcrText` TEXT NOT NULL,
                        `fieldConfidence` TEXT NOT NULL,
                        `overallConfidence` INTEGER NOT NULL,
                        `confidenceTier` TEXT NOT NULL,
                        `verificationStatus` TEXT NOT NULL,
                        `verificationErrors` TEXT NOT NULL,
                        `duplicateWarning` TEXT,
                        `sourceImage` TEXT,
                        `scanMode` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `verifiedBy` TEXT,
                        `verifiedAt` INTEGER,
                        `linkedReceiptNumber` TEXT,
                        PRIMARY KEY(`recordId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `farmer_machine_mappings` (
                        `machineCustomerCode` TEXT NOT NULL,
                        `farmerId` TEXT NOT NULL,
                        `machineId` TEXT NOT NULL,
                        `updatedBy` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`machineCustomerCode`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN month INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE milk_collections ADD COLUMN verificationStatus TEXT NOT NULL DEFAULT 'CONFIRMED'")
                // Backfill month, year, sourceType, and verificationStatus for existing records without data loss
                db.execSQL(
                    """
                    UPDATE milk_collections SET
                        year = CAST(SUBSTR(date, 1, 4) AS INTEGER),
                        month = CAST(SUBSTR(date, 6, 2) AS INTEGER)
                    WHERE LENGTH(date) >= 7 AND year = 0
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE milk_collections SET
                        sourceType = CASE WHEN isOcrScanned = 1 THEN 'MILK_RECEIPT' ELSE 'MANUAL' END,
                        verificationStatus = CASE WHEN verifiedBy IS NOT NULL AND verifiedBy != '' THEN 'ADMIN_VERIFIED' ELSE 'CONFIRMED' END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rate_charts ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `expenseId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `date` TEXT NOT NULL,
                        `paymentMode` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `recordedBy` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isSynced` INTEGER NOT NULL,
                        PRIMARY KEY(`expenseId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `notificationId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `isSynced` INTEGER NOT NULL,
                        PRIMARY KEY(`notificationId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): DairyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DairyDatabase::class.java,
                    "dairy_erp_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Prepopulate default rate charts & initial farmers
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val dao = getInstance(context).dairyDao()
                                    // Cow Rate Chart
                                    dao.insertRateChart(
                                        RateChartConfig(
                                            category = "COW",
                                            baseRate = 38.0,
                                            baseFat = 3.5,
                                            fatMultiplier = 5.5,
                                            baseSnf = 8.5,
                                            snfMultiplier = 3.0,
                                            waterDeductionPerPercent = 0.5
                                        )
                                    )
                                    // Buffalo Rate Chart
                                    dao.insertRateChart(
                                        RateChartConfig(
                                            category = "BUFFALO",
                                            baseRate = 58.0,
                                            baseFat = 6.5,
                                            fatMultiplier = 6.0,
                                            baseSnf = 9.0,
                                            snfMultiplier = 3.5,
                                            waterDeductionPerPercent = 0.5
                                        )
                                    )
                                    // Initial Farmers
                                    dao.insertFarmer(
                                        Farmer(
                                            farmerId = "F001",
                                            fullName = "रमेश कुमार (Ramesh Kumar)",
                                            mobileNumber = "9876543210",
                                            villageAddress = "तुर्की चौक, मुजफ्फरपुर",
                                            milkCategory = "COW",
                                            bankName = "State Bank of India",
                                            accountHolderName = "Ramesh Kumar",
                                            accountNumber = "30291827364",
                                            ifscCode = "SBIN0001234"
                                        )
                                    )
                                    dao.insertFarmer(
                                        Farmer(
                                            farmerId = "F002",
                                            fullName = "सुरेश यादव (Suresh Yadav)",
                                            mobileNumber = "9812345678",
                                            villageAddress = "यादव टोला, तुर्की",
                                            milkCategory = "BUFFALO",
                                            bankName = "Punjab National Bank",
                                            accountHolderName = "Suresh Yadav",
                                            accountNumber = "18273645192",
                                            ifscCode = "PUNB0182700"
                                        )
                                    )
                                    dao.insertFarmer(
                                        Farmer(
                                            farmerId = "F003",
                                            fullName = "रामबाबू सिंह (Rambabu Singh)",
                                            mobileNumber = "9431289012",
                                            villageAddress = "गोविंदपुर, तुर्की",
                                            milkCategory = "COW",
                                            bankName = "Bank of Baroda",
                                            accountHolderName = "Rambabu Singh",
                                            accountNumber = "45129837461",
                                            ifscCode = "BARB0TURKI1"
                                        )
                                    )
                                    // Initial Audit Log
                                    dao.insertAuditLog(
                                        AuditLog(
                                            action = "SYSTEM_INITIALIZED",
                                            details = "नमस्ते इंडिया दूध संग्रहण केंद्र (NI-TURKI-01) डेटाबेस प्रारंभ किया गया।",
                                            operatorName = "System"
                                        )
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("DairyDatabase", "Database prepopulation failed safely", e)
                                }
                            }
                        }
                    }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
