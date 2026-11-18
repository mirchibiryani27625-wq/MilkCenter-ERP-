package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.RateChartConfig
import com.example.data.model.StationConfig
import com.example.data.model.UserRole
import com.example.ui.components.DashboardGrid
import com.example.ui.screens.LoginScreen
import com.example.ui.viewmodel.DairyScreen
import com.example.util.OcrParser
import com.example.util.RateCalculator
import kotlinx.coroutines.flow.firstOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test login screen google sign in and forgot password dialog`() {
        composeTestRule.setContent {
            LoginScreen(
                station = StationConfig(),
                isHindi = true,
                enableAutoSignInCheck = false
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("role_chip_admin").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_email_input").assertIsDisplayed()
    }

    @Test
    fun `test dashboard grid all six module buttons click`() {
        var navigatedScreen: DairyScreen? = null

        composeTestRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DashboardGrid(
                    isHindi = true,
                    onNavigate = { screen -> navigatedScreen = screen }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("menu_farmer_management").performScrollTo().performClick()
        assertEquals(DairyScreen.FARMER_MANAGEMENT, navigatedScreen)
        composeTestRule.onNodeWithTag("menu_milk_collection").performScrollTo().performClick()
        assertEquals(DairyScreen.MILK_COLLECTION, navigatedScreen)
        composeTestRule.onNodeWithTag("menu_reports_analysis").performScrollTo().performClick()
        assertEquals(DairyScreen.REPORTS_ANALYSIS, navigatedScreen)
        composeTestRule.onNodeWithTag("menu_farmer_account").performScrollTo().performClick()
        assertEquals(DairyScreen.FARMER_ACCOUNT, navigatedScreen)
        composeTestRule.onNodeWithTag("menu_payment_management").performScrollTo().performClick()
        assertEquals(DairyScreen.PAYMENT_MANAGEMENT, navigatedScreen)
        composeTestRule.onNodeWithTag("menu_system_settings").performScrollTo().performClick()
        assertEquals(DairyScreen.SYSTEM_SETTINGS, navigatedScreen)
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Namaste India Dairy Manager", appName)
    }

    @Test
    fun `test rate calculation precision`() {
        val cowChart = RateChartConfig(
            category = "COW",
            baseRate = 38.0,
            baseFat = 3.5,
            fatMultiplier = 5.5,
            baseSnf = 8.5,
            snfMultiplier = 3.0,
            waterDeductionPerPercent = 0.5
        )
        // 10L at 4.0 Fat (diff +0.5 * 5.5 = +2.75) and 8.5 SNF (diff 0) = Rate 40.75 -> Total = 407.50
        val result = RateCalculator.calculate(10.0, 4.0, 8.5, 0.0, cowChart)
        assertEquals(40.75, result.ratePerLiter, 0.01)
        assertEquals(407.50, result.totalAmount, 0.01)
    }

    @Test
    fun `test ocr parser machine screen text extraction`() {
        val sampleDisplay = """
            LACTOSCAN S-50
            FAT: 4.2%
            SNF: 8.65%
            CLR: 28.5
            WATER: 0.0%
            WT: 12.50 KG
            RATE: 42.00
            AMT: 525.00
            MC: 01
        """.trimIndent()

        val parsed = OcrParser.parseExtractedText(sampleDisplay)
        assertEquals(4.2, parsed.fat ?: 0.0, 0.01)
        assertEquals(8.65, parsed.snf ?: 0.0, 0.01)
        assertEquals(12.50, parsed.quantity ?: 0.0, 0.01)
        assertEquals(42.00, parsed.rate ?: 0.0, 0.01)
        assertEquals(525.00, parsed.totalAmount ?: 0.0, 0.01)
    }

    @Test
    fun `test image quality blur and dark brightness detection`() {
        // Uniform solid dark bitmap -> Blurry AND TooDark
        val flatDarkBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        flatDarkBitmap.eraseColor(android.graphics.Color.rgb(20, 20, 20))
        val darkReport = com.example.util.ImageQualityAnalyzer.analyzeBitmapQuality(flatDarkBitmap)
        org.junit.Assert.assertTrue("Flat image should fail sharpness", darkReport.isBlurry)
        org.junit.Assert.assertTrue("Dark image should be flagged", darkReport.isTooDark)

        // Sharp checkerboard high-contrast bitmap -> Not Blurry & Good brightness
        val sharpBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val c = if ((x + y) % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                sharpBitmap.setPixel(x, y, c)
            }
        }
        val sharpReport = com.example.util.ImageQualityAnalyzer.analyzeBitmapQuality(sharpBitmap)
        org.junit.Assert.assertFalse("High-contrast checkerboard should not be blurry", sharpReport.isBlurry)
        org.junit.Assert.assertFalse("Balanced black/white should not be too dark", sharpReport.isTooDark)
    }

    @Test
    fun `test scan record and machine mapping room persistence`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.DairyDatabase::class.java
        ).allowMainThreadQueries().build()

        val dao = db.dairyDao()
        dao.insertFarmer(com.example.data.model.Farmer("F010", "किसान परीक्षण", "9999999999", "तुर्की"))
        dao.insertMachineMapping(
            com.example.data.model.FarmerMachineMapping(
                machineCustomerCode = "M99",
                farmerId = "F010",
                machineId = "MC-01"
            )
        )
        dao.insertScanRecord(
            com.example.data.model.ScanRecord(
                recordId = "SCN-TEST-01",
                farmerId = "F010",
                customerCode = "M99",
                date = "2026-11-14",
                shift = "MORNING",
                fat = 4.2,
                snf = 8.6,
                weight = 5.0,
                rate = 42.0,
                amount = 210.0,
                overallConfidence = 92,
                confidenceTier = "HIGH",
                verificationStatus = "PENDING_ADMIN"
            )
        )

        val mappings = dao.getAllMachineMappings()
        assertEquals(1, mappings.size)
        assertEquals("F010", mappings[0].farmerId)

        val scans = dao.getAllScanRecords()
        assertEquals(1, scans.size)
        assertEquals("SCN-TEST-01", scans[0].recordId)
        db.close()
    }

    @Test
    fun `test repository full workflow farmer collection payment and admin pin audit`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.DairyDatabase::class.java
        ).allowMainThreadQueries().build()
        val repo = com.example.data.repository.DairyRepository(db.dairyDao())

        val fId = repo.generateNextFarmerId()
        assertEquals("F001", fId)
        val farmer = com.example.data.model.Farmer(fId, "सुरेश यादव", "9876543210", "तुर्की", "COW")
        repo.saveFarmer(farmer, "Admin")

        val receiptNo = repo.generateReceiptNumber("MORNING", "2026-11-14")
        val entry = com.example.data.model.MilkCollectionEntry(
            receiptNumber = receiptNo,
            date = "2026-11-14",
            shift = "MORNING",
            farmerId = fId,
            farmerName = farmer.fullName,
            milkCategory = "COW",
            quantity = 10.0,
            fat = 4.0,
            snf = 8.5,
            rate = 40.75,
            totalAmount = 407.50,
            verifiedBy = "Admin Verified"
        )
        repo.saveCollection(entry, "Admin")

        val pay = com.example.data.model.PaymentRecord(
            transactionId = repo.generatePaymentTransactionId(),
            farmerId = fId,
            farmerName = farmer.fullName,
            paymentAmount = 200.0,
            paymentDate = "2026-11-14",
            paymentMode = "CASH",
            paymentType = "PARTIAL"
        )
        repo.savePayment(pay, "Admin")

        // Verify edited record logs oldValue -> newValue
        val updatedEntry = entry.copy(quantity = 11.0, totalAmount = 448.25, verifiedBy = "Admin Edited")
        repo.updateVerifiedCollectionWithAudit(entry, updatedEntry, "Admin")

        val collections = repo.getAllCollectionsSnapshot()
        assertEquals(1, collections.size)
        assertEquals(11.0, collections[0].quantity, 0.01)

        val logs = repo.auditLogs.firstOrNull()
        assertNotNull(logs)
        org.junit.Assert.assertTrue(logs!!.any { it.action == "VERIFIED_RECORD_EDITED" && it.oldValue.contains("Wt:10.0") && it.newValue.contains("Wt:11.0") })
        db.close()
    }

    @Test
    fun `test scan confirmation table displays Not Readable and four action buttons`() {
        var confirmed = false
        var scannedAgain = false
        var cancelled = false
        val fields = listOf(
            com.example.util.OcrFieldDetail("farmerId", "Farmer ID", "F001", "F001", 96, "x:10, y:20", com.example.util.FieldValidationStatus.VALID),
            com.example.util.OcrFieldDetail("fat", "FAT %", null, "Not Readable", 0, "Not Detected", com.example.util.FieldValidationStatus.UNREADABLE)
        )

        composeTestRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                com.example.ui.components.ScanConfirmationFieldsTable(
                    fields = fields,
                    isEditMode = false,
                    onToggleEditMode = {}
                )
                com.example.ui.components.ScanActionFooterButtons(
                    canConfirmSave = true,
                    isEditing = false,
                    onConfirmSave = { confirmed = true },
                    onToggleEdit = {},
                    onScanAgain = { scannedAgain = true },
                    onCancel = { cancelled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scan_confirmation_table").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ocr_confirm_save_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("ocr_scan_again_btn").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("ocr_cancel_btn").performScrollTo().performClick()

        org.junit.Assert.assertTrue(confirmed)
        org.junit.Assert.assertTrue(scannedAgain)
        org.junit.Assert.assertTrue(cancelled)
    }

    @Test
    fun `test cropBitmap and database v2 to v3 migration`() {
        val src = android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val cropped = com.example.util.ImageQualityAnalyzer.cropBitmap(src, 0.1f, 0.2f, 0.9f, 0.8f)
        assertEquals(160, cropped.width)
        assertEquals(60, cropped.height)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_2_3.db")
        if (dbFile.exists()) dbFile.delete()

        val rawDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `farmers` (`farmerId` TEXT NOT NULL, `fullName` TEXT NOT NULL, `mobileNumber` TEXT NOT NULL, `villageAddress` TEXT NOT NULL, `milkCategory` TEXT NOT NULL, `photoUri` TEXT, `bankName` TEXT NOT NULL, `accountHolderName` TEXT NOT NULL, `accountNumber` TEXT NOT NULL, `ifscCode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`farmerId`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `scan_records` (`recordId` TEXT NOT NULL, `farmerId` TEXT, `customerCode` TEXT NOT NULL, `machineId` TEXT NOT NULL, `stationId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `shift` TEXT NOT NULL, `animalType` TEXT NOT NULL, `fat` REAL, `snf` REAL, `water` REAL, `weight` REAL, `rate` REAL, `amount` REAL, `expectedAmount` REAL, `rawOcrText` TEXT NOT NULL, `fieldConfidence` TEXT NOT NULL, `overallConfidence` INTEGER NOT NULL, `confidenceTier` TEXT NOT NULL, `verificationStatus` TEXT NOT NULL, `verificationErrors` TEXT NOT NULL, `duplicateWarning` TEXT, `sourceImage` TEXT, `scanMode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `verifiedBy` TEXT, `verifiedAt` INTEGER, `linkedReceiptNumber` TEXT, PRIMARY KEY(`recordId`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `farmer_machine_mappings` (`machineCustomerCode` TEXT NOT NULL, `farmerId` TEXT NOT NULL, `machineId` TEXT NOT NULL, `updatedBy` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`machineCustomerCode`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `payments` (`transactionId` TEXT NOT NULL, `farmerId` TEXT NOT NULL, `farmerName` TEXT NOT NULL, `paymentAmount` REAL NOT NULL, `paymentDate` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `paymentMode` TEXT NOT NULL, `paymentType` TEXT NOT NULL, `note` TEXT NOT NULL, `previousBalance` REAL NOT NULL, `finalBalance` REAL NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `rate_charts` (`category` TEXT NOT NULL, `baseRate` REAL NOT NULL, `baseFat` REAL NOT NULL, `fatMultiplier` REAL NOT NULL, `baseSnf` REAL NOT NULL, `snfMultiplier` REAL NOT NULL, `waterDeductionPerPercent` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`category`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `operatorName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `oldValue` TEXT NOT NULL, `newValue` TEXT NOT NULL, `recordRef` TEXT NOT NULL)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS milk_collections (
                receiptNumber TEXT NOT NULL PRIMARY KEY,
                date TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                shift TEXT NOT NULL,
                farmerId TEXT NOT NULL,
                farmerName TEXT NOT NULL,
                milkCategory TEXT NOT NULL,
                quantity REAL NOT NULL,
                fat REAL NOT NULL,
                snf REAL NOT NULL,
                waterPercentage REAL NOT NULL,
                rate REAL NOT NULL,
                totalAmount REAL NOT NULL,
                machineAmount REAL,
                scannedImageUri TEXT,
                isOcrScanned INTEGER NOT NULL,
                ocrConfidence INTEGER NOT NULL,
                machineNumber TEXT,
                isSynced INTEGER NOT NULL,
                actualTime TEXT NOT NULL,
                transactionId TEXT,
                scanRecordId TEXT,
                verifiedBy TEXT,
                stationId TEXT
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            INSERT INTO milk_collections VALUES (
                'RCP-MIG-1', '2026-11-15', 1000, 'MORNING', 'F001', 'Ramesh', 'COW',
                5.0, 4.0, 8.5, 0.0, 40.0, 200.0, 200.0, NULL, 1, 95, 'EV-01', 0, '07:45 AM',
                'TXN-1', 'SCN-1', 'Admin', 'NI-TURKI-01'
            )
            """.trimIndent()
        )
        rawDb.version = 2
        rawDb.close()

        val roomDb = androidx.room.Room.databaseBuilder(
            context,
            com.example.data.db.DairyDatabase::class.java,
            "test_migration_2_3.db"
        ).addMigrations(
            com.example.data.db.DairyDatabase.MIGRATION_2_3,
            com.example.data.db.DairyDatabase.MIGRATION_3_4
        )
            .allowMainThreadQueries()
            .build()

        val migrated = kotlinx.coroutines.runBlocking { roomDb.dairyDao().getAllCollections() }
        assertEquals(1, migrated.size)
        assertEquals(11, migrated[0].month)
        assertEquals(2026, migrated[0].year)
        assertEquals("MILK_RECEIPT", migrated[0].sourceType)
        assertEquals("ADMIN_VERIFIED", migrated[0].verificationStatus)
        roomDb.close()
        dbFile.delete()
    }

    @Test
    fun `test database v3 to v4 migration and expense notification repository persistence`() {
        kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_3_4.db")
        if (dbFile.exists()) dbFile.delete()

        val rawDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `farmers` (`farmerId` TEXT NOT NULL, `fullName` TEXT NOT NULL, `mobileNumber` TEXT NOT NULL, `villageAddress` TEXT NOT NULL, `milkCategory` TEXT NOT NULL, `photoUri` TEXT, `bankName` TEXT NOT NULL, `accountHolderName` TEXT NOT NULL, `accountNumber` TEXT NOT NULL, `ifscCode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`farmerId`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `milk_collections` (`receiptNumber` TEXT NOT NULL, `date` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `shift` TEXT NOT NULL, `farmerId` TEXT NOT NULL, `farmerName` TEXT NOT NULL, `milkCategory` TEXT NOT NULL, `quantity` REAL NOT NULL, `fat` REAL NOT NULL, `snf` REAL NOT NULL, `waterPercentage` REAL NOT NULL, `rate` REAL NOT NULL, `totalAmount` REAL NOT NULL, `machineAmount` REAL, `scannedImageUri` TEXT, `isOcrScanned` INTEGER NOT NULL, `ocrConfidence` INTEGER NOT NULL, `machineNumber` TEXT, `isSynced` INTEGER NOT NULL, `actualTime` TEXT NOT NULL, `transactionId` TEXT, `scanRecordId` TEXT, `verifiedBy` TEXT, `stationId` TEXT, `month` INTEGER NOT NULL, `year` INTEGER NOT NULL, `sourceType` TEXT NOT NULL, `verificationStatus` TEXT NOT NULL, PRIMARY KEY(`receiptNumber`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `scan_records` (`recordId` TEXT NOT NULL, `farmerId` TEXT, `customerCode` TEXT NOT NULL, `machineId` TEXT NOT NULL, `stationId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `shift` TEXT NOT NULL, `animalType` TEXT NOT NULL, `fat` REAL, `snf` REAL, `water` REAL, `weight` REAL, `rate` REAL, `amount` REAL, `expectedAmount` REAL, `rawOcrText` TEXT NOT NULL, `fieldConfidence` TEXT NOT NULL, `overallConfidence` INTEGER NOT NULL, `confidenceTier` TEXT NOT NULL, `verificationStatus` TEXT NOT NULL, `verificationErrors` TEXT NOT NULL, `duplicateWarning` TEXT, `sourceImage` TEXT, `scanMode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `verifiedBy` TEXT, `verifiedAt` INTEGER, `linkedReceiptNumber` TEXT, PRIMARY KEY(`recordId`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `farmer_machine_mappings` (`machineCustomerCode` TEXT NOT NULL, `farmerId` TEXT NOT NULL, `machineId` TEXT NOT NULL, `updatedBy` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`machineCustomerCode`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `payments` (`transactionId` TEXT NOT NULL, `farmerId` TEXT NOT NULL, `farmerName` TEXT NOT NULL, `paymentAmount` REAL NOT NULL, `paymentDate` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `paymentMode` TEXT NOT NULL, `paymentType` TEXT NOT NULL, `note` TEXT NOT NULL, `previousBalance` REAL NOT NULL, `finalBalance` REAL NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `rate_charts` (`category` TEXT NOT NULL, `baseRate` REAL NOT NULL, `baseFat` REAL NOT NULL, `fatMultiplier` REAL NOT NULL, `baseSnf` REAL NOT NULL, `snfMultiplier` REAL NOT NULL, `waterDeductionPerPercent` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`category`))")
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `operatorName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `oldValue` TEXT NOT NULL, `newValue` TEXT NOT NULL, `recordRef` TEXT NOT NULL)")
        rawDb.version = 3
        rawDb.close()

        val roomDb = androidx.room.Room.databaseBuilder(
            context,
            com.example.data.db.DairyDatabase::class.java,
            "test_migration_3_4.db"
        ).addMigrations(com.example.data.db.DairyDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val repo = com.example.data.repository.DairyRepository(roomDb.dairyDao())
        repo.saveExpense(
            com.example.data.model.ExpenseRecord(
                expenseId = "EXP-20261118-001",
                title = "Chiller Electricity Bill",
                category = "ELECTRICITY",
                amount = 1450.0,
                date = "2026-11-18",
                paymentMode = "UPI",
                recordedBy = "Operator"
            ),
            operator = "Operator"
        )

        val expenses = roomDb.dairyDao().getAllExpenses()
        val notifs = roomDb.dairyDao().getUnsyncedNotifications()
        assertEquals(1, expenses.size)
        assertEquals(1450.0, expenses[0].amount, 0.01)
        org.junit.Assert.assertTrue(notifs.isNotEmpty())
        assertEquals(1, repo.getPendingSyncCount() - notifs.size) // 1 unsynced expense
            roomDb.close()
            dbFile.delete()
        }
    }
}
