package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.DairyDatabase
import com.example.data.model.AppNotification
import com.example.data.model.AppUserProfile
import com.example.data.model.AuditLog
import com.example.data.model.CloudSyncStatus
import com.example.data.model.ExpenseRecord
import com.example.data.model.Farmer
import com.example.data.model.FarmerMachineMapping
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.PaymentRecord
import com.example.data.model.RateChartConfig
import com.example.data.model.ScanRecord
import com.example.data.model.StationConfig
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.remote.FirebaseSyncRepository
import com.example.data.repository.DairyRepository
import com.example.util.FirestoreErrorHandler
import com.example.util.ImageStorageHelper
import com.example.util.NetworkMonitor
import com.example.util.OcrParser
import com.example.util.OcrScanResult
import com.example.util.RateCalculator
import com.example.util.ReportVerificationOutcome
import com.example.util.ScanVerificationEngine
import com.example.util.ScanVerificationOutcome
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DairyScreen {
    DASHBOARD,
    FARMER_MANAGEMENT,
    MILK_COLLECTION,
    REPORTS_ANALYSIS,
    FARMER_ACCOUNT,
    PAYMENT_MANAGEMENT,
    SYSTEM_SETTINGS,
    ADMIN_VERIFICATION,
    LOGIN
}

data class DashboardSummary(
    val todayMilksLiters: Double = 0.0,
    val todayTotalAmount: Double = 0.0,
    val totalFarmersCount: Int = 0,
    val morningCollectionLiters: Double = 0.0,
    val eveningCollectionLiters: Double = 0.0,
    val todayEntriesCount: Int = 0,
    val todayAverageFat: Double = 0.0,
    val todayAverageSnf: Double = 0.0,
    val todayExpensesAmount: Double = 0.0
)

class DairyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DairyDatabase.getInstance(application)
    private val dao = db.dairyDao()
    private val firebaseSync = FirebaseSyncRepository(application.applicationContext, dao)
    private val networkMonitor = NetworkMonitor(application.applicationContext)
    private val repository = DairyRepository(dao, firebaseSync)

    private val prefs = application.getSharedPreferences(
        "namaste_india_dairy_prefs",
        android.content.Context.MODE_PRIVATE
    )

    private fun loadStationConfig(): StationConfig {
        val defaultConfig = StationConfig()
        return StationConfig(
            dairyName = prefs.getString("station_dairy_name", defaultConfig.dairyName) ?: defaultConfig.dairyName,
            centerName = prefs.getString("station_center_name", defaultConfig.centerName) ?: defaultConfig.centerName,
            stationId = prefs.getString("station_id", defaultConfig.stationId) ?: defaultConfig.stationId,
            address = prefs.getString("station_address", defaultConfig.address) ?: defaultConfig.address,
            contactNumber = prefs.getString("station_contact", defaultConfig.contactNumber) ?: defaultConfig.contactNumber,
            adminPin = prefs.getString("station_admin_pin", defaultConfig.adminPin) ?: defaultConfig.adminPin,
            morningStartHour = prefs.getInt("station_shift_m_sh", defaultConfig.morningStartHour),
            morningStartMinute = prefs.getInt("station_shift_m_sm", defaultConfig.morningStartMinute),
            morningEndHour = prefs.getInt("station_shift_m_eh", defaultConfig.morningEndHour),
            morningEndMinute = prefs.getInt("station_shift_m_em", defaultConfig.morningEndMinute),
            eveningStartHour = prefs.getInt("station_shift_e_sh", defaultConfig.eveningStartHour),
            eveningStartMinute = prefs.getInt("station_shift_e_sm", defaultConfig.eveningStartMinute),
            eveningEndHour = prefs.getInt("station_shift_e_eh", defaultConfig.eveningEndHour),
            eveningEndMinute = prefs.getInt("station_shift_e_em", defaultConfig.eveningEndMinute),
            amountToleranceRs = prefs.getFloat("station_amount_tolerance", defaultConfig.amountToleranceRs.toFloat()).toDouble()
        )
    }

    private val initialStation = loadStationConfig()
    private val initialUser = firebaseSync.getCurrentUser()

    private fun loadUserSession(user: FirebaseUser?): UserSession {
        val defaultSession = UserSession()
        val roleName = prefs.getString("session_role", defaultSession.role.name) ?: defaultSession.role.name
        val role = runCatching { UserRole.valueOf(roleName) }.getOrDefault(UserRole.OPERATOR)
        val userName = user?.displayName?.takeIf { it.isNotBlank() }
            ?: prefs.getString("session_user_name", defaultSession.userName)
            ?: defaultSession.userName
        val email = user?.email ?: ""
        return UserSession(
            isLoggedIn = user != null,
            role = role,
            userName = userName,
            mobileOrEmail = email.ifBlank { prefs.getString("session_mobile", "") ?: "" },
            firebaseUid = user?.uid ?: "",
            email = email
        )
    }

    // Navigation & Session (Gated behind real FirebaseAuth user)
    private val _firebaseUser = MutableStateFlow(initialUser)
    val firebaseUser: StateFlow<FirebaseUser?> = _firebaseUser.asStateFlow()

    private val _currentScreen = MutableStateFlow(
        if (initialUser != null) DairyScreen.DASHBOARD else DairyScreen.LOGIN
    )
    val currentScreen: StateFlow<DairyScreen> = _currentScreen.asStateFlow()

    private val _userSession = MutableStateFlow(loadUserSession(initialUser))
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    private val _stationConfig = MutableStateFlow(initialStation)
    val stationConfig: StateFlow<StationConfig> = _stationConfig.asStateFlow()

    private val _isHindi = MutableStateFlow(prefs.getBoolean("pref_is_hindi", true))
    val isHindi: StateFlow<Boolean> = _isHindi.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    private val _syncStatus = MutableStateFlow(
        CloudSyncStatus(
            isOnline = networkMonitor.isOnline.value,
            isSignedIn = initialUser != null,
            userEmail = initialUser?.email ?: ""
        )
    )
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val _cloudUsers = MutableStateFlow<List<AppUserProfile>>(emptyList())
    val cloudUsers: StateFlow<List<AppUserProfile>> = _cloudUsers.asStateFlow()

    private var realtimeSyncJob: Job? = null
    private var usersSyncJob: Job? = null

    init {
        repository.setActiveStationId(initialStation.stationId)
        networkMonitor.start()

        viewModelScope.launch {
            firebaseSync.authStateFlow().collect { user ->
                _firebaseUser.value = user
                if (user != null) {
                    val preferredRole = prefs.getString("preferred_login_role", UserRole.OPERATOR.name) ?: UserRole.OPERATOR.name
                    onAuthenticatedUserActive(user, preferredRole)
                } else {
                    detachCloudListeners()
                    _cloudUsers.value = emptyList()
                    _userSession.value = UserSession(isLoggedIn = false, role = UserRole.STAFF, userName = "", mobileOrEmail = "")
                    _currentScreen.value = DairyScreen.LOGIN
                    _syncStatus.value = _syncStatus.value.copy(isSignedIn = false, userEmail = "", isSyncing = false)
                }
            }
        }

        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                refreshPendingCount()
                _syncStatus.value = _syncStatus.value.copy(isOnline = online)
                if (online && _firebaseUser.value != null) {
                    syncNow()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        detachCloudListeners()
        networkMonitor.stop()
    }

    private fun detachCloudListeners() {
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null
        usersSyncJob?.cancel()
        usersSyncJob = null
    }

    private fun onAuthenticatedUserActive(user: FirebaseUser, preferredRole: String) {
        viewModelScope.launch {
            val profileResult = firebaseSync.ensureUserProfile(preferredRole, _stationConfig.value.stationId)
            val profile = profileResult.getOrNull()
            val resolvedRole = runCatching {
                UserRole.valueOf((profile?.role ?: preferredRole).uppercase())
            }.getOrDefault(UserRole.OPERATOR)

            val resolvedName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Dairy User"

            _userSession.value = UserSession(
                isLoggedIn = true,
                role = resolvedRole,
                userName = resolvedName,
                mobileOrEmail = user.email ?: "",
                firebaseUid = user.uid,
                email = user.email ?: ""
            )
            prefs.edit()
                .putBoolean("session_logged_in", true)
                .putString("session_role", resolvedRole.name)
                .putString("session_user_name", resolvedName)
                .putString("session_mobile", user.email ?: "")
                .apply()

            if (_currentScreen.value == DairyScreen.LOGIN) {
                _currentScreen.value = DairyScreen.DASHBOARD
            }

            _syncStatus.value = _syncStatus.value.copy(
                isSignedIn = true,
                userEmail = user.email ?: "",
                lastError = profileResult.exceptionOrNull()?.let { FirestoreErrorHandler.toUserFriendlyMessage(it) }
            )

            attachCloudListeners(isAdmin = resolvedRole == UserRole.ADMIN)
            if (networkMonitor.isOnline.value) {
                syncNow()
            }
        }
    }

    private fun attachCloudListeners(isAdmin: Boolean) {
        detachCloudListeners()
        realtimeSyncJob = viewModelScope.launch {
            firebaseSync.startRealtimeSyncFlows { err ->
                val msg = FirestoreErrorHandler.toUserFriendlyMessage(err)
                _syncStatus.value = _syncStatus.value.copy(lastError = msg)
            }.collect {
                refreshPendingCount()
            }
        }
        usersSyncJob = viewModelScope.launch {
            firebaseSync.observeUsersFlow(isAdmin = isAdmin).collect { users ->
                _cloudUsers.value = users
            }
        }
    }

    fun clearUiError() {
        _uiError.value = null
    }

    private fun safeLaunch(
        errorPrefix: String = "डेटा ऑपरेशन त्रुटि",
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                block()
                refreshPendingCount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DairyViewModel", "$errorPrefix: ${e.message}", e)
                _uiError.value = "$errorPrefix: ${e.localizedMessage ?: "कृपया पुनः प्रयास करें"}"
            }
        }
    }

    private suspend fun refreshPendingCount() {
        val count = runCatching { repository.getPendingSyncCount() }.getOrDefault(0)
        _syncStatus.value = _syncStatus.value.copy(pendingCount = count)
    }

    fun syncNow() {
        if (_firebaseUser.value == null) return
        viewModelScope.launch {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true, lastError = null)
            val result = firebaseSync.syncPendingToFirestore(
                stationConfig = _stationConfig.value,
                todayDate = todayDate,
                isAdmin = _userSession.value.role == UserRole.ADMIN
            )
            refreshPendingCount()
            result.onSuccess {
                _syncStatus.value = _syncStatus.value.copy(
                    isSyncing = false,
                    lastSyncTimeMillis = System.currentTimeMillis(),
                    lastError = null
                )
            }.onFailure { err ->
                _syncStatus.value = _syncStatus.value.copy(
                    isSyncing = false,
                    lastError = FirestoreErrorHandler.toUserFriendlyMessage(err)
                )
            }
        }
    }

    fun toggleLanguage() {
        val next = !_isHindi.value
        _isHindi.value = next
        prefs.edit().putBoolean("pref_is_hindi", next).apply()
    }

    fun navigateTo(screen: DairyScreen) {
        _currentScreen.value = screen
    }

    fun onGoogleSignInSuccess(preferredRole: UserRole, user: FirebaseUser) {
        prefs.edit().putString("preferred_login_role", preferredRole.name).apply()
        onAuthenticatedUserActive(user, preferredRole.name)
        safeLaunch("लॉगिन ऑडिट त्रुटि") {
            val label = user.displayName ?: user.email ?: "Google User"
            repository.addAuditLog("USER_GOOGLE_LOGIN", "$label (${preferredRole.name}) ने Google से लॉग इन किया।", label)
        }
    }

    fun switchRoleLocally(role: UserRole, name: String, mobile: String) {
        _userSession.value = _userSession.value.copy(isLoggedIn = true, role = role, userName = name, mobileOrEmail = mobile)
        prefs.edit()
            .putBoolean("session_logged_in", true)
            .putString("session_role", role.name)
            .putString("session_user_name", name)
            .putString("session_mobile", mobile)
            .apply()
        if (_firebaseUser.value != null) {
            _currentScreen.value = DairyScreen.DASHBOARD
        }
    }

    fun login(role: UserRole, name: String, mobile: String) {
        switchRoleLocally(role, name, mobile)
        _currentScreen.value = DairyScreen.DASHBOARD
        safeLaunch("लॉगिन ऑडिट त्रुटि") {
            repository.addAuditLog("USER_LOGIN", "$name ($role) ने लॉग इन किया।", name)
        }
    }

    fun logout() {
        viewModelScope.launch {
            detachCloudListeners()
            firebaseSync.signOutAndClearCredentials()
            _userSession.value = UserSession(isLoggedIn = false, role = UserRole.STAFF, userName = "", mobileOrEmail = "")
            prefs.edit()
                .putBoolean("session_logged_in", false)
                .putString("session_role", UserRole.STAFF.name)
                .putString("session_user_name", "")
                .putString("session_mobile", "")
                .apply()
            _currentScreen.value = DairyScreen.LOGIN
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        firebaseSync.sendPasswordResetEmail(email)

    fun updateCloudUserRole(targetUid: String, newRole: String, isActive: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = firebaseSync.updateUserRoleAsAdmin(targetUid, newRole, isActive)
            res.onSuccess {
                onResult(true, "भूमिका $newRole सफलतापूर्वक अपडेट हुई")
            }.onFailure { err ->
                onResult(false, FirestoreErrorHandler.toUserFriendlyMessage(err))
            }
        }
    }

    // Today Date
    val todayDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // Data streams
    val farmers: StateFlow<List<Farmer>> = repository.allFarmers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCollections: StateFlow<List<MilkCollectionEntry>> = repository.allCollections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<PaymentRecord>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<ExpenseRecord>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotifications: StateFlow<List<AppNotification>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rateCharts: StateFlow<List<RateChartConfig>> = repository.allRateCharts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allScanRecords: StateFlow<List<ScanRecord>> = repository.allScanRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingScanRecords: StateFlow<List<ScanRecord>> = repository.pendingScanRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val machineMappings: StateFlow<List<FarmerMachineMapping>> = repository.allMachineMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard computed summary
    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        allCollections,
        farmers,
        allExpenses
    ) { collections, farmersList, expensesList ->
        val todayItems = collections.filter { it.date == todayDate }
        val totalLiters = todayItems.sumOf { it.quantity }
        val totalAmt = todayItems.sumOf { it.totalAmount }
        val morningLiters = todayItems.filter { it.shift == "MORNING" }.sumOf { it.quantity }
        val eveningLiters = todayItems.filter { it.shift == "EVENING" }.sumOf { it.quantity }
        val avgFat = if (todayItems.isNotEmpty()) todayItems.map { it.fat }.average() else 0.0
        val avgSnf = if (todayItems.isNotEmpty()) todayItems.map { it.snf }.average() else 0.0
        val todayExpenses = expensesList.filter { it.date == todayDate }.sumOf { it.amount }

        DashboardSummary(
            todayMilksLiters = totalLiters,
            todayTotalAmount = totalAmt,
            totalFarmersCount = farmersList.size,
            morningCollectionLiters = morningLiters,
            eveningCollectionLiters = eveningLiters,
            todayEntriesCount = todayItems.size,
            todayAverageFat = avgFat,
            todayAverageSnf = avgSnf,
            todayExpensesAmount = todayExpenses
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    // --- Farmer Operations ---
    fun saveFarmer(farmer: Farmer, onComplete: () -> Unit = {}) {
        safeLaunch("किसान सहेजने में त्रुटि") {
            repository.saveFarmer(farmer, _userSession.value.userName.ifEmpty { "Operator" })
            onComplete()
        }
    }

    fun deleteFarmer(farmer: Farmer) {
        safeLaunch("किसान हटाने में त्रुटि") {
            repository.deleteFarmer(farmer, _userSession.value.userName.ifEmpty { "Operator" })
        }
    }

    suspend fun getNextFarmerId(): String = repository.generateNextFarmerId()

    // --- Milk Collection Operations ---
    private val _pendingScannedCollection = MutableStateFlow<MilkCollectionEntry?>(null)
    val pendingScannedCollection: StateFlow<MilkCollectionEntry?> = _pendingScannedCollection

    fun setPendingScannedCollection(entry: MilkCollectionEntry?) {
        _pendingScannedCollection.value = entry
    }

    fun saveMilkEntry(entry: MilkCollectionEntry, onComplete: (MilkCollectionEntry) -> Unit = {}) {
        safeLaunch("दूध संग्रहण सहेजने में त्रुटि") {
            repository.saveCollection(entry, _userSession.value.userName.ifEmpty { "Operator" })
            onComplete(entry)
        }
    }

    fun saveMilkEntries(entries: List<MilkCollectionEntry>, onComplete: () -> Unit = {}) {
        safeLaunch("रिपोर्ट प्रविष्टियाँ सहेजने में त्रुटि") {
            repository.saveCollections(entries, _userSession.value.userName.ifEmpty { "Operator" })
            onComplete()
        }
    }

    fun deleteMilkEntry(entry: MilkCollectionEntry) {
        safeLaunch("संग्रहण हटाने में त्रुटि") {
            repository.deleteCollection(entry, _userSession.value.userName.ifEmpty { "Operator" })
        }
    }

    fun editVerifiedCollectionWithPin(
        oldEntry: MilkCollectionEntry,
        newEntry: MilkCollectionEntry,
        enteredPin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (enteredPin != _stationConfig.value.adminPin) {
            onResult(false, "गलत Admin PIN — सत्यापित रिकॉर्ड में बदलाव अस्वीकृत")
            return
        }
        viewModelScope.launch {
            try {
                val adminName = _userSession.value.userName.ifEmpty { "Admin" }
                repository.updateVerifiedCollectionWithAudit(oldEntry, newEntry, adminName)
                refreshPendingCount()
                onResult(true, "रिकॉर्ड सफलतापूर्वक अपडेट और ऑडिट लॉग में दर्ज हुआ")
            } catch (e: Exception) {
                onResult(false, "डेटाबेस अपडेट विफल: ${e.localizedMessage ?: "त्रुटि"}")
            }
        }
    }

    fun deleteCollectionWithPin(
        entry: MilkCollectionEntry,
        enteredPin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val isVerifiedProtected = !entry.verifiedBy.isNullOrBlank() || entry.isOcrScanned
        if (isVerifiedProtected && enteredPin != _stationConfig.value.adminPin) {
            onResult(false, "गलत Admin PIN — सत्यापित रसीद हटाने की अनुमति नहीं है")
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteCollection(entry, _userSession.value.userName.ifEmpty { "Admin" })
                refreshPendingCount()
                onResult(true, "रसीद ${entry.receiptNumber} हटाई गई")
            } catch (e: Exception) {
                onResult(false, "रसीद हटाने में त्रुटि: ${e.localizedMessage ?: "त्रुटि"}")
            }
        }
    }

    suspend fun generateReceiptNumber(shift: String, dateOverride: String = todayDate): String =
        repository.generateReceiptNumber(shift, dateOverride)

    suspend fun getRateChartForCategory(category: String): RateChartConfig =
        repository.getRateChart(category)

    // --- Advanced AI Scan & Verification Operations ---
    suspend fun persistSourceImageUri(uri: Uri?, prefix: String = "scan"): String? {
        uri ?: return null
        return ImageStorageHelper.saveScanUri(getApplication(), uri, prefix) ?: uri.toString()
    }

    suspend fun persistSourceBitmap(bitmap: Bitmap?, prefix: String = "live_scan"): String? {
        bitmap ?: return null
        return ImageStorageHelper.saveScanBitmap(getApplication(), bitmap, prefix)
    }

    suspend fun verifyOcrResult(
        ocr: OcrScanResult,
        scanMode: String,
        sourceImagePath: String?
    ): ScanVerificationOutcome {
        val farmersSnap = repository.getAllFarmersSnapshot().ifEmpty { farmers.value }
        val mappingsSnap = repository.getAllMachineMappingsSnapshot().ifEmpty { machineMappings.value }
        val collectionsSnap = repository.getAllCollectionsSnapshot().ifEmpty { allCollections.value }
        val scansSnap = repository.getAllScanRecordsSnapshot().ifEmpty { allScanRecords.value }

        return ScanVerificationEngine.verifyScan(
            ocr = ocr,
            farmers = farmersSnap,
            mappings = mappingsSnap,
            existingCollections = collectionsSnap,
            existingScanRecords = scansSnap,
            scanMode = scanMode,
            sourceImagePath = sourceImagePath,
            stationConfig = _stationConfig.value
        )
    }

    suspend fun verifyReportOcrResult(
        ocr: OcrScanResult,
        sourceImagePath: String?
    ): ReportVerificationOutcome {
        val farmersSnap = repository.getAllFarmersSnapshot().ifEmpty { farmers.value }
        val mappingsSnap = repository.getAllMachineMappingsSnapshot().ifEmpty { machineMappings.value }
        val collectionsSnap = repository.getAllCollectionsSnapshot().ifEmpty { allCollections.value }
        val refYear = todayDate.substring(0, 4).toIntOrNull() ?: 2026

        return ScanVerificationEngine.verifyCustomerReport(
            ocr = ocr,
            farmers = farmersSnap,
            mappings = mappingsSnap,
            existingCollections = collectionsSnap,
            sourceImagePath = sourceImagePath,
            fallbackYear = refYear
        )
    }

    fun recordScanOnly(record: ScanRecord, onComplete: () -> Unit = {}) {
        safeLaunch("स्कैन रिकॉर्ड सहेजने में त्रुटि") {
            repository.saveScanRecord(record, _userSession.value.userName.ifEmpty { "Operator" })
            onComplete()
        }
    }

    fun saveVerifiedScanAndCollection(
        record: ScanRecord,
        entry: MilkCollectionEntry,
        onComplete: (MilkCollectionEntry) -> Unit = {}
    ) {
        safeLaunch("सत्यापित स्कैन सहेजने में त्रुटि") {
            val op = _userSession.value.userName.ifEmpty { "Operator" }
            val finalizedRecord = record.copy(linkedReceiptNumber = entry.receiptNumber)
            repository.saveScanRecord(finalizedRecord, op)
            repository.saveCollection(entry, op)
            onComplete(entry)
        }
    }

    fun approveScanRecordAsAdmin(
        originalRecord: ScanRecord,
        editedRecord: ScanRecord,
        entry: MilkCollectionEntry,
        onComplete: () -> Unit = {}
    ) {
        safeLaunch("Admin अनुमोदन त्रुटि") {
            val adminName = _userSession.value.userName.ifEmpty { "Admin" }
            repository.approveAndSaveScanRecord(originalRecord, editedRecord, entry, adminName)
            onComplete()
        }
    }

    fun rejectScanRecordAsAdmin(
        record: ScanRecord,
        reason: String,
        onComplete: () -> Unit = {}
    ) {
        safeLaunch("स्कैन अस्वीकार करने में त्रुटि") {
            val adminName = _userSession.value.userName.ifEmpty { "Admin" }
            repository.rejectScanRecord(record, reason, adminName)
            onComplete()
        }
    }

    fun saveFarmerMachineMapping(
        machineCustomerCode: String,
        farmerId: String,
        machineId: String = "ALL",
        onComplete: () -> Unit = {}
    ) {
        if (machineCustomerCode.isBlank() || farmerId.isBlank()) return
        safeLaunch("मशीन मैपिंग सहेजने में त्रुटि") {
            val adminName = _userSession.value.userName.ifEmpty { "Admin" }
            repository.saveMachineMapping(
                FarmerMachineMapping(
                    machineCustomerCode = machineCustomerCode.trim().uppercase(),
                    farmerId = farmerId.trim().uppercase(),
                    machineId = machineId.trim().ifBlank { "ALL" },
                    updatedBy = adminName
                ),
                adminName
            )
            onComplete()
        }
    }

    fun deleteMachineMapping(mapping: FarmerMachineMapping) {
        safeLaunch("मैपिंग हटाने में त्रुटि") {
            val adminName = _userSession.value.userName.ifEmpty { "Admin" }
            repository.deleteMachineMapping(mapping, adminName)
        }
    }

    // --- Payments & Expenses Operations ---
    fun savePayment(payment: PaymentRecord, onComplete: () -> Unit = {}) {
        safeLaunch("भुगतान सहेजने में त्रुटि") {
            repository.savePayment(payment, _userSession.value.userName.ifEmpty { "Operator" })
            onComplete()
        }
    }

    fun deletePayment(payment: PaymentRecord) {
        safeLaunch("भुगतान हटाने में त्रुटि") {
            repository.deletePayment(payment, _userSession.value.userName.ifEmpty { "Operator" })
        }
    }

    fun generatePaymentId(): String = repository.generatePaymentTransactionId()

    fun saveExpense(expense: ExpenseRecord, onComplete: () -> Unit = {}) {
        safeLaunch("खर्च सहेजने में त्रुटि") {
            repository.saveExpense(expense, _userSession.value.userName.ifEmpty { "Operator" })
            onComplete()
        }
    }

    fun deleteExpense(expense: ExpenseRecord) {
        safeLaunch("खर्च हटाने में त्रुटि") {
            repository.deleteExpense(expense, _userSession.value.userName.ifEmpty { "Operator" })
        }
    }

    fun generateExpenseId(): String = repository.generateExpenseId()

    fun markNotificationRead(notificationId: String) {
        safeLaunch("सूचना अपडेट त्रुटि") {
            repository.markNotificationRead(notificationId)
        }
    }

    // --- Settings Operations ---
    fun updateStationConfig(config: StationConfig) {
        _stationConfig.value = config
        repository.setActiveStationId(config.stationId)
        prefs.edit()
            .putString("station_dairy_name", config.dairyName)
            .putString("station_center_name", config.centerName)
            .putString("station_id", config.stationId)
            .putString("station_address", config.address)
            .putString("station_contact", config.contactNumber)
            .putString("station_admin_pin", config.adminPin)
            .putInt("station_shift_m_sh", config.morningStartHour)
            .putInt("station_shift_m_sm", config.morningStartMinute)
            .putInt("station_shift_m_eh", config.morningEndHour)
            .putInt("station_shift_m_em", config.morningEndMinute)
            .putInt("station_shift_e_sh", config.eveningStartHour)
            .putInt("station_shift_e_sm", config.eveningStartMinute)
            .putInt("station_shift_e_eh", config.eveningEndHour)
            .putInt("station_shift_e_em", config.eveningEndMinute)
            .putFloat("station_amount_tolerance", config.amountToleranceRs.toFloat())
            .apply()
        safeLaunch("सेटिंग्स सहेजने में त्रुटि") {
            repository.pushStationSettings(config)
            repository.addAuditLog("SETTINGS_UPDATED", "स्टेशन सेटिंग्स अपडेट की गईं: ${config.centerName}", _userSession.value.userName)
        }
    }

    fun updateRateChart(config: RateChartConfig) {
        safeLaunch("रेट चार्ट अपडेट त्रुटि") {
            repository.updateRateChart(config, _userSession.value.userName)
        }
    }

    // --- OCR Scanning State ---
    private val _ocrResultState = MutableStateFlow<OcrScanResult?>(null)
    val ocrResultState: StateFlow<OcrScanResult?> = _ocrResultState.asStateFlow()

    private val _isOcrProcessing = MutableStateFlow(false)
    val isOcrProcessing: StateFlow<Boolean> = _isOcrProcessing.asStateFlow()

    fun processOcrImage(uri: Uri) {
        _isOcrProcessing.value = true
        viewModelScope.launch {
            try {
                val result = OcrParser.processImage(getApplication(), uri)
                _ocrResultState.value = result
            } catch (e: Exception) {
                _uiError.value = "इमेज OCR स्कैनिंग विफल: ${e.localizedMessage ?: "कृपया साफ फोटो चुनें"}"
            } finally {
                _isOcrProcessing.value = false
            }
        }
    }

    fun setOcrResult(result: OcrScanResult?) {
        _ocrResultState.value = result
    }

    // Calculate Rate utility
    suspend fun calculateMilkRate(
        category: String,
        quantity: Double,
        fat: Double,
        snf: Double,
        water: Double,
        machineAmount: Double? = null
    ): RateCalculator.CalculationResult {
        val chart = repository.getRateChart(category)
        return RateCalculator.calculate(quantity, fat, snf, water, chart, machineAmount)
    }

    // Farmer Ledger Calculations
    fun getFarmerAccountSummary(farmerId: String): FarmerAccountSummary {
        val fCollections = allCollections.value.filter { it.farmerId == farmerId }
        val fPayments = allPayments.value.filter { it.farmerId == farmerId }

        val morningMilk = fCollections.filter { it.shift == "MORNING" }.sumOf { it.quantity }
        val eveningMilk = fCollections.filter { it.shift == "EVENING" }.sumOf { it.quantity }
        val totalMilk = fCollections.sumOf { it.quantity }
        val totalBill = fCollections.sumOf { it.totalAmount }
        val totalPaid = fPayments.sumOf { it.paymentAmount }

        val avgFat = if (fCollections.isNotEmpty()) fCollections.map { it.fat }.average() else 0.0
        val avgSnf = if (fCollections.isNotEmpty()) fCollections.map { it.snf }.average() else 0.0

        val finalBalance = totalBill - totalPaid

        return FarmerAccountSummary(
            farmerId = farmerId,
            morningMilk = morningMilk,
            eveningMilk = eveningMilk,
            totalMilk = totalMilk,
            averageFat = avgFat,
            averageSnf = avgSnf,
            totalBill = totalBill,
            totalPaid = totalPaid,
            finalBalance = finalBalance,
            collections = fCollections,
            payments = fPayments
        )
    }
}

data class FarmerAccountSummary(
    val farmerId: String,
    val morningMilk: Double,
    val eveningMilk: Double,
    val totalMilk: Double,
    val averageFat: Double,
    val averageSnf: Double,
    val totalBill: Double,
    val totalPaid: Double,
    val finalBalance: Double,
    val collections: List<MilkCollectionEntry> = emptyList(),
    val payments: List<PaymentRecord> = emptyList()
)

