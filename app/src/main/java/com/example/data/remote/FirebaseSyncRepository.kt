package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.db.DairyDao
import com.example.data.model.AppNotification
import com.example.data.model.AppUserProfile
import com.example.data.model.ExpenseRecord
import com.example.data.model.Farmer
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.PaymentRecord
import com.example.data.model.RateChartConfig
import com.example.data.model.StationConfig
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FirebaseSyncRepository(
    private val context: Context,
    private val dao: DairyDao
) {
    companion object {
        const val COL_USERS = "Users"
        const val COL_FARMERS = "Farmers"
        const val COL_MILK_COLLECTION = "MilkCollection"
        const val COL_MORNING_SHIFT = "MorningShift"
        const val COL_EVENING_SHIFT = "EveningShift"
        const val COL_MILK_RATES = "MilkRates"
        const val COL_PAYMENTS = "Payments"
        const val COL_EXPENSES = "Expenses"
        const val COL_DAILY_REPORTS = "DailyReports"
        const val COL_MONTHLY_REPORTS = "MonthlyReports"
        const val COL_SETTINGS = "Settings"
        const val COL_NOTIFICATIONS = "Notifications"

        val BOOTSTRAPPED_ADMIN_EMAILS = setOf("mirchibiryani27625@gmail.com")
    }

    private val auth: FirebaseAuth? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            FirebaseAuth.getInstance()
        }.getOrNull()
    }

    private val firestore: FirebaseFirestore? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            val dbId = runCatching { context.getString(R.string.firestore_database_id).trim() }
                .getOrDefault("(default)")
            val instance = if (dbId.isBlank() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
            runCatching {
                instance.firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(persistentCacheSettings {})
                }
            }
            instance
        }.getOrNull()
    }

    private val storage: FirebaseStorage? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            FirebaseStorage.getInstance()
        }.getOrNull()
    }

    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    fun isFirebaseAvailable(): Boolean = auth != null && firestore != null

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        trySend(firebaseAuth.currentUser)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    private fun docKey(uid: String, entityId: String): String {
        val clean = entityId.trim().replace("/", "_").replace(".", "_")
        return "${uid}_${clean}".take(120)
    }

    private fun isBootstrappedAdmin(user: FirebaseUser): Boolean {
        return user.isEmailVerified && BOOTSTRAPPED_ADMIN_EMAILS.contains(user.email?.lowercase())
    }

    private suspend fun upsertDocument(
        collection: String,
        docId: String,
        createFields: Map<String, Any>,
        updateFields: Map<String, Any>,
        allowUpdateIfExists: Boolean = true
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val path = "$collection/$docId"
        return try {
            val ref = db.collection(collection).document(docId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val payload = createFields.toMutableMap().apply {
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.set(payload).await()
            } else if (allowUpdateIfExists) {
                val payload = updateFields.toMutableMap().apply {
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun ensureUserProfile(
        preferredRole: String,
        stationId: String
    ): Result<AppUserProfile> {
        val user = getCurrentUser() ?: return Result.failure(IllegalStateException("Not authenticated"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val uid = user.uid
        val path = "$COL_USERS/$uid"
        return try {
            val ref = db.collection(COL_USERS).document(uid)
            val snap = ref.get().await()
            val isBootAdmin = isBootstrappedAdmin(user)
            val resolvedRole = if (!snap.exists()) {
                if (isBootAdmin) "ADMIN"
                else if (preferredRole in listOf("OPERATOR", "STAFF")) preferredRole else "OPERATOR"
            } else {
                snap.getString("role") ?: if (isBootAdmin) "ADMIN" else "OPERATOR"
            }

            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Dairy User"
            val email = user.email ?: ""
            val photoUrl = user.photoUrl?.toString() ?: ""

            if (!snap.exists()) {
                val createMap = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "role" to resolvedRole,
                    "stationId" to stationId,
                    "isActive" to true,
                    "photoUrl" to photoUrl,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(createMap).await()
            } else {
                val updateMap = mapOf(
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "stationId" to stationId,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.update(updateMap).await()
            }

            Result.success(
                AppUserProfile(
                    userId = uid,
                    email = email,
                    displayName = displayName,
                    role = resolvedRole,
                    stationId = stationId,
                    active = snap.getBoolean("isActive") ?: true,
                    photoUrl = photoUrl,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateUserRoleAsAdmin(targetUid: String, newRole: String, isActive: Boolean): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val path = "$COL_USERS/$targetUid"
        return try {
            db.collection(COL_USERS).document(targetUid).update(
                mapOf(
                    "role" to newRole,
                    "isActive" to isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeUsersFlow(isAdmin: Boolean): Flow<List<AppUserProfile>> = callbackFlow {
        val user = getCurrentUser()
        val db = firestore
        if (user == null || db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query: Query = if (isBootstrappedAdmin(user) || isAdmin) {
            db.collection(COL_USERS)
        } else {
            db.collection(COL_USERS).whereEqualTo("userId", user.uid)
        }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                val fsErr = (error as? FirebaseFirestoreException) ?: (error.cause as? FirebaseFirestoreException)
                FirestoreErrorHandler.handleFirestoreError(fsErr ?: error, OperationType.LIST, COL_USERS, auth)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                val uid = doc.getString("userId") ?: doc.id
                AppUserProfile(
                    userId = uid,
                    email = doc.getString("email") ?: "",
                    displayName = doc.getString("displayName") ?: "",
                    role = doc.getString("role") ?: "OPERATOR",
                    stationId = doc.getString("stationId") ?: "NI-TURKI-01",
                    active = doc.getBoolean("isActive") ?: true,
                    photoUrl = doc.getString("photoUrl") ?: "",
                    updatedAtMillis = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
                )
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    // --- Media Storage Helper (graceful fallback if Storage bucket is not activated yet) ---
    suspend fun uploadImageIfNeeded(localPath: String?, folder: String, fileName: String): String? {
        if (localPath.isNullOrBlank()) return null
        if (localPath.startsWith("http://") || localPath.startsWith("https://")) return localPath
        val uid = getCurrentUser()?.uid ?: return localPath
        val storageInstance = storage ?: return localPath
        val file = File(localPath)
        if (!file.exists()) return localPath

        return try {
            val ref = storageInstance.reference.child("users/$uid/$folder/$fileName")
            ref.putFile(Uri.fromFile(file)).await()
            ref.downloadUrl.await().toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FirebaseSyncRepo", "Firebase Storage upload skipped (check Storage activation): ${e.message}")
            localPath
        }
    }

    // --- 2. Farmers ---
    suspend fun pushFarmer(farmer: Farmer, stationId: String): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cloudPhoto = uploadImageIfNeeded(farmer.photoUri, "farmers", "${farmer.farmerId}.jpg") ?: ""
        val fields = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to stationId,
            "farmerId" to farmer.farmerId,
            "fullName" to farmer.fullName,
            "mobileNumber" to farmer.mobileNumber,
            "villageAddress" to farmer.villageAddress,
            "milkCategory" to farmer.milkCategory,
            "photoUrl" to cloudPhoto,
            "bankName" to farmer.bankName,
            "accountHolderName" to farmer.accountHolderName,
            "accountNumber" to farmer.accountNumber,
            "ifscCode" to farmer.ifscCode
        )
        val updateFields = mapOf<String, Any>(
            "stationId" to stationId,
            "fullName" to farmer.fullName,
            "mobileNumber" to farmer.mobileNumber,
            "villageAddress" to farmer.villageAddress,
            "milkCategory" to farmer.milkCategory,
            "photoUrl" to cloudPhoto,
            "bankName" to farmer.bankName,
            "accountHolderName" to farmer.accountHolderName,
            "accountNumber" to farmer.accountNumber,
            "ifscCode" to farmer.ifscCode
        )
        return upsertDocument(COL_FARMERS, docKey(uid, farmer.farmerId), fields, updateFields).onSuccess {
            dao.markFarmerSynced(farmer.farmerId)
        }
    }

    suspend fun deleteFarmerRemote(farmerId: String): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val docId = docKey(uid, farmerId)
        return try {
            db.collection(COL_FARMERS).document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "$COL_FARMERS/$docId", auth)
            Result.failure(e)
        }
    }

    // --- 3, 4, 5. MilkCollection + MorningShift + EveningShift ---
    suspend fun pushCollection(entry: MilkCollectionEntry, stationId: String, oldShift: String? = null): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val docId = docKey(uid, entry.receiptNumber)
        val cloudScanUri = uploadImageIfNeeded(entry.scannedImageUri, "receipts", "${entry.receiptNumber}.jpg") ?: ""

        val createFields = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to (entry.stationId ?: stationId),
            "receiptNumber" to entry.receiptNumber,
            "date" to entry.date,
            "shift" to entry.shift,
            "farmerId" to entry.farmerId,
            "farmerName" to entry.farmerName,
            "milkCategory" to entry.milkCategory,
            "quantity" to entry.quantity,
            "fat" to entry.fat,
            "snf" to entry.snf,
            "waterPercentage" to entry.waterPercentage,
            "rate" to entry.rate,
            "totalAmount" to entry.totalAmount,
            "machineAmount" to (entry.machineAmount ?: entry.totalAmount),
            "scannedImageUrl" to cloudScanUri,
            "ocrScanned" to entry.isOcrScanned,
            "ocrConfidence" to entry.ocrConfidence,
            "machineNumber" to (entry.machineNumber ?: ""),
            "actualTime" to entry.actualTime,
            "transactionId" to (entry.transactionId ?: ""),
            "scanRecordId" to (entry.scanRecordId ?: ""),
            "verifiedBy" to (entry.verifiedBy ?: ""),
            "month" to entry.month,
            "year" to entry.year,
            "sourceType" to entry.sourceType,
            "verificationStatus" to entry.verificationStatus,
            "timestampMillis" to entry.timestamp
        )

        val updateFields = mapOf<String, Any>(
            "stationId" to (entry.stationId ?: stationId),
            "date" to entry.date,
            "shift" to entry.shift,
            "farmerId" to entry.farmerId,
            "farmerName" to entry.farmerName,
            "milkCategory" to entry.milkCategory,
            "quantity" to entry.quantity,
            "fat" to entry.fat,
            "snf" to entry.snf,
            "waterPercentage" to entry.waterPercentage,
            "rate" to entry.rate,
            "totalAmount" to entry.totalAmount,
            "machineAmount" to (entry.machineAmount ?: entry.totalAmount),
            "scannedImageUrl" to cloudScanUri,
            "ocrScanned" to entry.isOcrScanned,
            "ocrConfidence" to entry.ocrConfidence,
            "machineNumber" to (entry.machineNumber ?: ""),
            "actualTime" to entry.actualTime,
            "verifiedBy" to (entry.verifiedBy ?: ""),
            "month" to entry.month,
            "year" to entry.year,
            "sourceType" to entry.sourceType,
            "verificationStatus" to entry.verificationStatus
        )

        val mainRes = upsertDocument(COL_MILK_COLLECTION, docId, createFields, updateFields)
        if (mainRes.isFailure) return mainRes

        val targetShiftCol = if (entry.shift == "MORNING") COL_MORNING_SHIFT else COL_EVENING_SHIFT
        val shiftRes = upsertDocument(targetShiftCol, docId, createFields, updateFields)
        if (oldShift != null && oldShift != entry.shift) {
            val staleShiftCol = if (oldShift == "MORNING") COL_MORNING_SHIFT else COL_EVENING_SHIFT
            runCatching { db.collection(staleShiftCol).document(docId).delete().await() }
        }

        if (shiftRes.isSuccess) {
            dao.markCollectionSynced(entry.receiptNumber)
        }
        return shiftRes
    }

    suspend fun deleteCollectionRemote(entry: MilkCollectionEntry): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val docId = docKey(uid, entry.receiptNumber)
        return try {
            db.collection(COL_MILK_COLLECTION).document(docId).delete().await()
            val shiftCol = if (entry.shift == "MORNING") COL_MORNING_SHIFT else COL_EVENING_SHIFT
            runCatching { db.collection(shiftCol).document(docId).delete().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "$COL_MILK_COLLECTION/$docId", auth)
            Result.failure(e)
        }
    }

    // --- 6. MilkRates ---
    suspend fun pushRateChart(rateChart: RateChartConfig, stationId: String, isAdmin: Boolean = true): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val createFields = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to stationId,
            "category" to rateChart.category,
            "baseRate" to rateChart.baseRate,
            "baseFat" to rateChart.baseFat,
            "fatMultiplier" to rateChart.fatMultiplier,
            "baseSnf" to rateChart.baseSnf,
            "snfMultiplier" to rateChart.snfMultiplier,
            "waterDeductionPerPercent" to rateChart.waterDeductionPerPercent
        )
        val updateFields = mapOf<String, Any>(
            "stationId" to stationId,
            "baseRate" to rateChart.baseRate,
            "baseFat" to rateChart.baseFat,
            "fatMultiplier" to rateChart.fatMultiplier,
            "baseSnf" to rateChart.baseSnf,
            "snfMultiplier" to rateChart.snfMultiplier,
            "waterDeductionPerPercent" to rateChart.waterDeductionPerPercent
        )
        return upsertDocument(
            collection = COL_MILK_RATES,
            docId = docKey(uid, rateChart.category),
            createFields = createFields,
            updateFields = updateFields,
            allowUpdateIfExists = isAdmin
        ).onSuccess {
            dao.markRateChartSynced(rateChart.category)
        }
    }

    // --- 7. Payments ---
    suspend fun pushPayment(payment: PaymentRecord, stationId: String): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val createFields = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to stationId,
            "transactionId" to payment.transactionId,
            "farmerId" to payment.farmerId,
            "farmerName" to payment.farmerName,
            "paymentAmount" to payment.paymentAmount,
            "paymentDate" to payment.paymentDate,
            "paymentMode" to payment.paymentMode,
            "paymentType" to payment.paymentType,
            "note" to payment.note,
            "previousBalance" to payment.previousBalance,
            "finalBalance" to payment.finalBalance,
            "timestampMillis" to payment.timestamp
        )
        val updateFields = mapOf<String, Any>(
            "stationId" to stationId,
            "farmerId" to payment.farmerId,
            "farmerName" to payment.farmerName,
            "paymentAmount" to payment.paymentAmount,
            "paymentDate" to payment.paymentDate,
            "paymentMode" to payment.paymentMode,
            "paymentType" to payment.paymentType,
            "note" to payment.note,
            "previousBalance" to payment.previousBalance,
            "finalBalance" to payment.finalBalance
        )
        return upsertDocument(COL_PAYMENTS, docKey(uid, payment.transactionId), createFields, updateFields).onSuccess {
            dao.markPaymentSynced(payment.transactionId)
        }
    }

    suspend fun deletePaymentRemote(transactionId: String): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val docId = docKey(uid, transactionId)
        return try {
            db.collection(COL_PAYMENTS).document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "$COL_PAYMENTS/$docId", auth)
            Result.failure(e)
        }
    }

    // --- 8. Expenses ---
    suspend fun pushExpense(expense: ExpenseRecord, stationId: String): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val createFields = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to stationId,
            "expenseId" to expense.expenseId,
            "title" to expense.title,
            "category" to expense.category,
            "amount" to expense.amount,
            "date" to expense.date,
            "paymentMode" to expense.paymentMode,
            "note" to expense.note,
            "recordedBy" to expense.recordedBy,
            "timestampMillis" to expense.timestamp
        )
        val updateFields = mapOf<String, Any>(
            "stationId" to stationId,
            "title" to expense.title,
            "category" to expense.category,
            "amount" to expense.amount,
            "date" to expense.date,
            "paymentMode" to expense.paymentMode,
            "note" to expense.note,
            "recordedBy" to expense.recordedBy
        )
        return upsertDocument(COL_EXPENSES, docKey(uid, expense.expenseId), createFields, updateFields).onSuccess {
            dao.markExpenseSynced(expense.expenseId)
        }
    }

    suspend fun deleteExpenseRemote(expenseId: String): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val docId = docKey(uid, expenseId)
        return try {
            db.collection(COL_EXPENSES).document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "$COL_EXPENSES/$docId", auth)
            Result.failure(e)
        }
    }

    // --- 9 & 10. DailyReports & MonthlyReports ---
    suspend fun pushDailyAndMonthlyReports(
        date: String,
        stationId: String,
        collections: List<MilkCollectionEntry>,
        payments: List<PaymentRecord>,
        expenses: List<ExpenseRecord>
    ): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val dayEntries = collections.filter { it.date == date }
        val dayPayments = payments.filter { it.paymentDate == date }.sumOf { it.paymentAmount }
        val dayExpenses = expenses.filter { it.date == date }.sumOf { it.amount }
        val dailyCreate = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to stationId,
            "date" to date,
            "totalWeight" to dayEntries.sumOf { it.quantity },
            "totalAmount" to dayEntries.sumOf { it.totalAmount },
            "morningWeight" to dayEntries.filter { it.shift == "MORNING" }.sumOf { it.quantity },
            "eveningWeight" to dayEntries.filter { it.shift == "EVENING" }.sumOf { it.quantity },
            "avgFat" to if (dayEntries.isNotEmpty()) dayEntries.map { it.fat }.average() else 0.0,
            "avgSnf" to if (dayEntries.isNotEmpty()) dayEntries.map { it.snf }.average() else 0.0,
            "entriesCount" to dayEntries.size,
            "totalPaid" to dayPayments,
            "totalExpenses" to dayExpenses
        )
        val dailyUpdate = dailyCreate - "userId" - "date"
        val resDaily = upsertDocument(COL_DAILY_REPORTS, docKey(uid, date), dailyCreate, dailyUpdate)

        val yearMonth = date.take(7)
        if (yearMonth.length == 7) {
            val monthEntries = collections.filter { it.date.startsWith(yearMonth) }
            val monthPayments = payments.filter { it.paymentDate.startsWith(yearMonth) }.sumOf { it.paymentAmount }
            val monthExpenses = expenses.filter { it.date.startsWith(yearMonth) }.sumOf { it.amount }
            val parts = yearMonth.split("-")
            val yearInt = parts.getOrNull(0)?.toIntOrNull() ?: 2026
            val monthInt = parts.getOrNull(1)?.toIntOrNull() ?: 1

            val monthlyCreate = mapOf<String, Any>(
                "userId" to uid,
                "stationId" to stationId,
                "yearMonth" to yearMonth,
                "year" to yearInt,
                "month" to monthInt,
                "totalWeight" to monthEntries.sumOf { it.quantity },
                "totalAmount" to monthEntries.sumOf { it.totalAmount },
                "cowWeight" to monthEntries.filter { it.milkCategory == "COW" }.sumOf { it.quantity },
                "buffaloWeight" to monthEntries.filter { it.milkCategory == "BUFFALO" }.sumOf { it.quantity },
                "entriesCount" to monthEntries.size,
                "totalPaid" to monthPayments,
                "totalExpenses" to monthExpenses
            )
            val monthlyUpdate = monthlyCreate - "userId" - "yearMonth" - "year" - "month"
            val resMonthly = upsertDocument(COL_MONTHLY_REPORTS, docKey(uid, yearMonth), monthlyCreate, monthlyUpdate)
            if (resMonthly.isFailure) return resMonthly
        }
        return resDaily
    }

    // --- 11. Settings ---
    suspend fun pushSettings(config: StationConfig, isAdmin: Boolean = true): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val createFields = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to config.stationId,
            "dairyName" to config.dairyName,
            "centerName" to config.centerName,
            "address" to config.address,
            "contactNumber" to config.contactNumber,
            "morningStartHour" to config.morningStartHour,
            "morningStartMinute" to config.morningStartMinute,
            "morningEndHour" to config.morningEndHour,
            "morningEndMinute" to config.morningEndMinute,
            "eveningStartHour" to config.eveningStartHour,
            "eveningStartMinute" to config.eveningStartMinute,
            "eveningEndHour" to config.eveningEndHour,
            "eveningEndMinute" to config.eveningEndMinute,
            "amountToleranceRs" to config.amountToleranceRs
        )
        val updateFields = createFields - "userId" - "stationId"
        return upsertDocument(
            collection = COL_SETTINGS,
            docId = docKey(uid, config.stationId),
            createFields = createFields,
            updateFields = updateFields,
            allowUpdateIfExists = isAdmin
        )
    }

    // --- 12. Notifications ---
    suspend fun pushNotification(notification: AppNotification, stationId: String): Result<Unit> {
        val uid = getCurrentUser()?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val createFields = mapOf<String, Any>(
            "userId" to uid,
            "stationId" to stationId,
            "notificationId" to notification.notificationId,
            "title" to notification.title,
            "message" to notification.message,
            "type" to notification.type,
            "read" to notification.isRead,
            "timestampMillis" to notification.timestamp
        )
        val updateFields = mapOf<String, Any>(
            "stationId" to stationId,
            "title" to notification.title,
            "message" to notification.message,
            "type" to notification.type,
            "read" to notification.isRead
        )
        return upsertDocument(COL_NOTIFICATIONS, docKey(uid, notification.notificationId), createFields, updateFields).onSuccess {
            dao.markNotificationSynced(notification.notificationId)
        }
    }

    // --- Batch Offline-to-Cloud Sync ---
    suspend fun syncPendingToFirestore(
        stationConfig: StationConfig,
        todayDate: String,
        isAdmin: Boolean = false
    ): Result<Int> {
        val current = getCurrentUser() ?: return Result.failure(IllegalStateException("Not signed in"))
        val effectiveAdmin = isAdmin || isBootstrappedAdmin(current)
        var syncedCount = 0
        var firstError: Throwable? = null

        val unsyncedFarmers = dao.getUnsyncedFarmers()
        for (farmer in unsyncedFarmers) {
            pushFarmer(farmer, stationConfig.stationId)
                .onSuccess { syncedCount++ }
                .onFailure { if (firstError == null) firstError = it }
        }

        val unsyncedCollections = dao.getUnsyncedCollections()
        for (entry in unsyncedCollections) {
            pushCollection(entry, stationConfig.stationId)
                .onSuccess { syncedCount++ }
                .onFailure { if (firstError == null) firstError = it }
        }

        val unsyncedPayments = dao.getUnsyncedPayments()
        for (payment in unsyncedPayments) {
            pushPayment(payment, stationConfig.stationId)
                .onSuccess { syncedCount++ }
                .onFailure { if (firstError == null) firstError = it }
        }

        val unsyncedExpenses = dao.getUnsyncedExpenses()
        for (expense in unsyncedExpenses) {
            pushExpense(expense, stationConfig.stationId)
                .onSuccess { syncedCount++ }
                .onFailure { if (firstError == null) firstError = it }
        }

        val unsyncedRates = dao.getUnsyncedRateCharts()
        for (rate in unsyncedRates) {
            pushRateChart(rate, stationConfig.stationId, isAdmin = effectiveAdmin)
                .onSuccess { syncedCount++ }
                .onFailure { if (firstError == null) firstError = it }
        }

        val unsyncedNotifs = dao.getUnsyncedNotifications()
        for (notif in unsyncedNotifs) {
            pushNotification(notif, stationConfig.stationId)
                .onSuccess { syncedCount++ }
                .onFailure { if (firstError == null) firstError = it }
        }

        pushSettings(stationConfig, isAdmin = effectiveAdmin).onFailure { if (firstError == null) firstError = it }
        pushDailyAndMonthlyReports(
            date = todayDate,
            stationId = stationConfig.stationId,
            collections = dao.getAllCollections(),
            payments = dao.getAllPayments(),
            expenses = dao.getAllExpenses()
        ).onFailure { if (firstError == null) firstError = it }

        return if (firstError != null && syncedCount == 0) {
            Result.failure(firstError!!)
        } else {
            Result.success(syncedCount)
        }
    }

    // --- Real-time Firestore Snapshot Sync into Room ---
    fun startRealtimeSyncFlows(onError: (Throwable) -> Unit): Flow<Unit> = callbackFlow {
        val user = getCurrentUser()
        val db = firestore
        if (user == null || db == null) {
            close()
            return@callbackFlow
        }
        val uid = user.uid

        val farmersReg = db.collection(COL_FARMERS).whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val changes = snap?.documentChanges ?: return@addSnapshotListener
                launch(Dispatchers.IO) {
                    try {
                        for (change in changes) {
                            val doc = change.document
                            val fId = doc.getString("farmerId") ?: continue
                            val local = dao.getFarmerById(fId)
                            if (change.type == DocumentChange.Type.REMOVED) {
                                if (local != null && local.isSynced) dao.deleteFarmer(local)
                            } else if (local == null || local.isSynced) {
                                dao.insertFarmer(
                                    Farmer(
                                        farmerId = fId,
                                        fullName = doc.getString("fullName") ?: "",
                                        mobileNumber = doc.getString("mobileNumber") ?: "",
                                        villageAddress = doc.getString("villageAddress") ?: "",
                                        milkCategory = doc.getString("milkCategory") ?: "COW",
                                        photoUri = doc.getString("photoUrl")?.takeIf { it.isNotBlank() } ?: local?.photoUri,
                                        bankName = doc.getString("bankName") ?: "",
                                        accountHolderName = doc.getString("accountHolderName") ?: "",
                                        accountNumber = doc.getString("accountNumber") ?: "",
                                        ifscCode = doc.getString("ifscCode") ?: "",
                                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: System.currentTimeMillis(),
                                        isSynced = true
                                    )
                                )
                            }
                        }
                        trySend(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncRepo", "Farmers Room merge failed", e)
                    }
                }
            }

        val collectionsReg = db.collection(COL_MILK_COLLECTION).whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val changes = snap?.documentChanges ?: return@addSnapshotListener
                launch(Dispatchers.IO) {
                    try {
                        for (change in changes) {
                            val doc = change.document
                            val rcp = doc.getString("receiptNumber") ?: continue
                            val local = dao.getCollectionByReceipt(rcp)
                            if (change.type == DocumentChange.Type.REMOVED) {
                                if (local != null && local.isSynced) dao.deleteCollection(local)
                            } else if (local == null || local.isSynced) {
                                dao.insertCollection(
                                    MilkCollectionEntry(
                                        receiptNumber = rcp,
                                        date = doc.getString("date") ?: "",
                                        timestamp = doc.getLong("timestampMillis") ?: System.currentTimeMillis(),
                                        shift = doc.getString("shift") ?: "MORNING",
                                        farmerId = doc.getString("farmerId") ?: "",
                                        farmerName = doc.getString("farmerName") ?: "",
                                        milkCategory = doc.getString("milkCategory") ?: "COW",
                                        quantity = doc.getDouble("quantity") ?: 0.0,
                                        fat = doc.getDouble("fat") ?: 0.0,
                                        snf = doc.getDouble("snf") ?: 0.0,
                                        waterPercentage = doc.getDouble("waterPercentage") ?: 0.0,
                                        rate = doc.getDouble("rate") ?: 0.0,
                                        totalAmount = doc.getDouble("totalAmount") ?: 0.0,
                                        machineAmount = doc.getDouble("machineAmount"),
                                        scannedImageUri = doc.getString("scannedImageUrl")?.takeIf { it.isNotBlank() } ?: local?.scannedImageUri,
                                        isOcrScanned = doc.getBoolean("ocrScanned") ?: false,
                                        ocrConfidence = (doc.getLong("ocrConfidence") ?: 100L).toInt(),
                                        machineNumber = doc.getString("machineNumber")?.takeIf { it.isNotBlank() },
                                        isSynced = true,
                                        actualTime = doc.getString("actualTime") ?: "",
                                        transactionId = doc.getString("transactionId")?.takeIf { it.isNotBlank() },
                                        scanRecordId = doc.getString("scanRecordId")?.takeIf { it.isNotBlank() },
                                        verifiedBy = doc.getString("verifiedBy")?.takeIf { it.isNotBlank() },
                                        stationId = doc.getString("stationId")?.takeIf { it.isNotBlank() },
                                        month = (doc.getLong("month") ?: 0L).toInt(),
                                        year = (doc.getLong("year") ?: 0L).toInt(),
                                        sourceType = doc.getString("sourceType") ?: "MANUAL",
                                        verificationStatus = doc.getString("verificationStatus") ?: "CONFIRMED"
                                    )
                                )
                            }
                        }
                        trySend(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncRepo", "Collections Room merge failed", e)
                    }
                }
            }

        val paymentsReg = db.collection(COL_PAYMENTS).whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val changes = snap?.documentChanges ?: return@addSnapshotListener
                launch(Dispatchers.IO) {
                    try {
                        for (change in changes) {
                            val doc = change.document
                            val txn = doc.getString("transactionId") ?: continue
                            val local = dao.getPaymentById(txn)
                            if (change.type == DocumentChange.Type.REMOVED) {
                                if (local != null && local.isSynced) dao.deletePayment(local)
                            } else if (local == null || local.isSynced) {
                                dao.insertPayment(
                                    PaymentRecord(
                                        transactionId = txn,
                                        farmerId = doc.getString("farmerId") ?: "",
                                        farmerName = doc.getString("farmerName") ?: "",
                                        paymentAmount = doc.getDouble("paymentAmount") ?: 0.0,
                                        paymentDate = doc.getString("paymentDate") ?: "",
                                        timestamp = doc.getLong("timestampMillis") ?: System.currentTimeMillis(),
                                        paymentMode = doc.getString("paymentMode") ?: "CASH",
                                        paymentType = doc.getString("paymentType") ?: "WEEKLY",
                                        note = doc.getString("note") ?: "",
                                        previousBalance = doc.getDouble("previousBalance") ?: 0.0,
                                        finalBalance = doc.getDouble("finalBalance") ?: 0.0,
                                        isSynced = true
                                    )
                                )
                            }
                        }
                        trySend(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncRepo", "Payments Room merge failed", e)
                    }
                }
            }

        val expensesReg = db.collection(COL_EXPENSES).whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val changes = snap?.documentChanges ?: return@addSnapshotListener
                launch(Dispatchers.IO) {
                    try {
                        for (change in changes) {
                            val doc = change.document
                            val expId = doc.getString("expenseId") ?: continue
                            val local = dao.getExpenseById(expId)
                            if (change.type == DocumentChange.Type.REMOVED) {
                                if (local != null && local.isSynced) dao.deleteExpense(local)
                            } else if (local == null || local.isSynced) {
                                dao.insertExpense(
                                    ExpenseRecord(
                                        expenseId = expId,
                                        title = doc.getString("title") ?: "",
                                        category = doc.getString("category") ?: "OTHER",
                                        amount = doc.getDouble("amount") ?: 0.0,
                                        date = doc.getString("date") ?: "",
                                        paymentMode = doc.getString("paymentMode") ?: "CASH",
                                        note = doc.getString("note") ?: "",
                                        recordedBy = doc.getString("recordedBy") ?: "",
                                        timestamp = doc.getLong("timestampMillis") ?: System.currentTimeMillis(),
                                        isSynced = true
                                    )
                                )
                            }
                        }
                        trySend(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncRepo", "Expenses Room merge failed", e)
                    }
                }
            }

        val ratesReg = db.collection(COL_MILK_RATES).whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents ?: return@addSnapshotListener
                launch(Dispatchers.IO) {
                    try {
                        for (doc in docs) {
                            val cat = doc.getString("category") ?: continue
                            val local = dao.getRateChart(cat)
                            if (local == null || local.isSynced) {
                                dao.insertRateChart(
                                    RateChartConfig(
                                        category = cat,
                                        baseRate = doc.getDouble("baseRate") ?: 38.0,
                                        baseFat = doc.getDouble("baseFat") ?: 3.5,
                                        fatMultiplier = doc.getDouble("fatMultiplier") ?: 5.5,
                                        baseSnf = doc.getDouble("baseSnf") ?: 8.5,
                                        snfMultiplier = doc.getDouble("snfMultiplier") ?: 3.0,
                                        waterDeductionPerPercent = doc.getDouble("waterDeductionPerPercent") ?: 0.5,
                                        updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: System.currentTimeMillis(),
                                        isSynced = true
                                    )
                                )
                            }
                        }
                        trySend(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncRepo", "Rates Room merge failed", e)
                    }
                }
            }

        val notificationsReg = db.collection(COL_NOTIFICATIONS).whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents ?: return@addSnapshotListener
                launch(Dispatchers.IO) {
                    try {
                        for (doc in docs) {
                            val nId = doc.getString("notificationId") ?: continue
                            val local = dao.getNotificationById(nId)
                            if (local == null || local.isSynced) {
                                dao.insertNotification(
                                    AppNotification(
                                        notificationId = nId,
                                        title = doc.getString("title") ?: "",
                                        message = doc.getString("message") ?: "",
                                        type = doc.getString("type") ?: "INFO",
                                        timestamp = doc.getLong("timestampMillis") ?: System.currentTimeMillis(),
                                        isRead = doc.getBoolean("read") ?: false,
                                        isSynced = true
                                    )
                                )
                            }
                        }
                        trySend(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncRepo", "Notifications Room merge failed", e)
                    }
                }
            }

        awaitClose {
            farmersReg.remove()
            collectionsReg.remove()
            paymentsReg.remove()
            expensesReg.remove()
            ratesReg.remove()
            notificationsReg.remove()
        }
    }

    suspend fun signOutAndClearCredentials() {
        withContext(NonCancellable) {
            runCatching {
                val cm = CredentialManager.create(context)
                cm.clearCredentialState(ClearCredentialStateRequest())
            }
            runCatching {
                auth?.signOut()
            }
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth unavailable"))
        return try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
