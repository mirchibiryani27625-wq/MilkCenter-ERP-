package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Farmer
import com.example.ui.components.DairyHeader
import com.example.ui.components.FarmerAvatar
import com.example.ui.components.PhotoSourceDialog
import com.example.ui.theme.DairyGreenSecondary
import com.example.ui.theme.DarkNavyTextPrimary
import com.example.ui.theme.GoldenYellowHighlight
import com.example.ui.theme.GreyTextSecondary
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.DairyScreen
import com.example.ui.viewmodel.DairyViewModel
import com.example.util.ImageStorageHelper
import com.example.util.WhatsAppHelper
import kotlinx.coroutines.launch

@Composable
fun FarmerManagementScreen(
    viewModel: DairyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val station by viewModel.stationConfig.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isHindi by viewModel.isHindi.collectAsState()
    val farmers by viewModel.farmers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var farmerToEdit by remember { mutableStateOf<Farmer?>(null) }
    var farmerToDelete by remember { mutableStateOf<Farmer?>(null) }
    var selectedFarmerDetail by remember { mutableStateOf<Farmer?>(null) }

    // Quick Photo Update state for a specific farmer from the list
    var farmerForPhotoUpdate by remember { mutableStateOf<Farmer?>(null) }

    val cameraLauncherForList = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null && farmerForPhotoUpdate != null) {
            val f = farmerForPhotoUpdate!!
            val savedPath = ImageStorageHelper.saveCompressedBitmap(context, bitmap, "farmer_${f.farmerId}")
            if (savedPath != null) {
                val updated = f.copy(photoUri = savedPath)
                viewModel.saveFarmer(updated)
                Toast.makeText(context, "${f.fullName} की फोटो अपडेट हुई!", Toast.LENGTH_SHORT).show()
            }
            farmerForPhotoUpdate = null
        }
    }

    val galleryLauncherForList = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && farmerForPhotoUpdate != null) {
            val f = farmerForPhotoUpdate!!
            val savedPath = ImageStorageHelper.saveCompressedUri(context, uri, "farmer_${f.farmerId}")
            if (savedPath != null) {
                val updated = f.copy(photoUri = savedPath)
                viewModel.saveFarmer(updated)
                Toast.makeText(context, "${f.fullName} की फोटो अपडेट हुई!", Toast.LENGTH_SHORT).show()
            }
            farmerForPhotoUpdate = null
        }
    }

    val filteredFarmers = remember(farmers, searchQuery) {
        if (searchQuery.isBlank()) farmers
        else {
            val q = searchQuery.trim().lowercase()
            farmers.filter {
                it.fullName.lowercase().contains(q) ||
                it.farmerId.lowercase().contains(q) ||
                it.mobileNumber.contains(q) ||
                it.villageAddress.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            DairyHeader(
                station = station,
                session = session,
                currentScreen = DairyScreen.FARMER_MANAGEMENT,
                isHindi = isHindi,
                onNavigateBack = onBack,
                onToggleLanguage = { viewModel.toggleLanguage() },
                onLogout = { viewModel.logout() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DairyGreenSecondary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_farmer_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "किसान जोड़ें (Add Farmer)")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Screen Title & Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isHindi) "किसान प्रबंधन (Farmer Management)" else "Farmer Management",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavyTextPrimary
                    )
                    Text(
                        text = "पंजीकृत किसानों की सूची व प्रोफाइल फोटो",
                        fontSize = 11.sp,
                        color = GreyTextSecondary
                    )
                }

                Surface(
                    color = NavyPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${filteredFarmers.size} ${if (isHindi) "किसान" else "Farmers"}",
                        color = NavyPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isHindi) "आईडी, नाम या मोबाइल से खोजें..." else "Search by ID, Name or Mobile...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = NavyPrimary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("farmer_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Farmer List
            if (filteredFarmers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = GreyTextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isHindi) "कोई किसान नहीं मिला" else "No farmers found",
                            color = GreyTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFarmers, key = { it.farmerId }) { farmer ->
                        FarmerItemCardWithPhoto(
                            farmer = farmer,
                            isHindi = isHindi,
                            onPhotoClick = { farmerForPhotoUpdate = farmer },
                            onCardClick = { selectedFarmerDetail = farmer },
                            onCallClick = { WhatsAppHelper.makePhoneCall(context, farmer.mobileNumber) },
                            onWhatsAppClick = {
                                val msg = "नमस्ते ${farmer.fullName} जी! नमस्ते इंडिया दूध संग्रहण केंद्र (${station.stationId}) में आपका स्वागत है।"
                                WhatsAppHelper.sendWhatsAppText(context, farmer.mobileNumber, msg)
                            },
                            onEditClick = { farmerToEdit = farmer },
                            onDeleteClick = { farmerToDelete = farmer }
                        )
                    }
                }
            }
        }
    }

    // Photo Source Dialog when user taps the photo icon
    if (farmerForPhotoUpdate != null) {
        val f = farmerForPhotoUpdate!!
        PhotoSourceDialog(
            farmerName = f.fullName,
            hasExistingPhoto = !f.photoUri.isNullOrBlank(),
            onDismiss = { farmerForPhotoUpdate = null },
            onLaunchCamera = { cameraLauncherForList.launch(null) },
            onLaunchGallery = {
                galleryLauncherForList.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemovePhoto = {
                val updated = f.copy(photoUri = null)
                viewModel.saveFarmer(updated)
                Toast.makeText(context, "फोटो हटा दी गई।", Toast.LENGTH_SHORT).show()
                farmerForPhotoUpdate = null
            }
        )
    }

    // Add or Edit Farmer Dialog
    if (showAddDialog || farmerToEdit != null) {
        val editing = farmerToEdit
        FarmerFormDialogWithPhoto(
            initialFarmer = editing,
            isHindi = isHindi,
            onDismiss = {
                showAddDialog = false
                farmerToEdit = null
            },
            onGenerateId = { viewModel.getNextFarmerId() },
            onSave = { farmer ->
                viewModel.saveFarmer(farmer) {
                    showAddDialog = false
                    farmerToEdit = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (farmerToDelete != null) {
        val f = farmerToDelete!!
        AlertDialog(
            onDismissRequest = { farmerToDelete = null },
            title = {
                Text(
                    text = if (isHindi) "किसान हटाने की पुष्टि" else "Confirm Delete Farmer",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = if (isHindi)
                        "क्या आप वाकई ${f.fullName} (${f.farmerId}) को हटाना चाहते हैं? यह क्रिया पूर्ववत नहीं की जा सकती।"
                    else
                        "Are you sure you want to delete ${f.fullName} (${f.farmerId})?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFarmer(f)
                        farmerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isHindi) "हाँ, हटाएं" else "Yes, Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { farmerToDelete = null }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    // Detailed Farmer Profile Dialog
    if (selectedFarmerDetail != null) {
        val f = selectedFarmerDetail!!
        FarmerDetailDialogWithPhoto(
            farmer = f,
            station = station,
            isHindi = isHindi,
            onDismiss = { selectedFarmerDetail = null },
            onPhotoClick = { farmerForPhotoUpdate = f },
            onCall = { WhatsAppHelper.makePhoneCall(context, f.mobileNumber) },
            onWhatsApp = {
                val summary = viewModel.getFarmerAccountSummary(f.farmerId)
                val msg = WhatsAppHelper.formatStatementMessage(
                    station, f, summary.totalMilk, summary.totalBill, summary.totalPaid, summary.finalBalance
                )
                WhatsAppHelper.sendWhatsAppText(context, f.mobileNumber, msg)
            }
        )
    }
}

/**
 * Farmer Item Card with Circular Farmer Photo Icon before Full Name
 */
@Composable
private fun FarmerItemCardWithPhoto(
    farmer: Farmer,
    isHindi: Boolean,
    onPhotoClick: () -> Unit,
    onCardClick: () -> Unit,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("farmer_card_${farmer.farmerId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular Farmer Photo Icon before Full Name (Clickable to change photo!)
                FarmerAvatar(
                    photoUri = farmer.photoUri,
                    name = farmer.fullName,
                    size = 50.dp,
                    showCameraBadge = true,
                    onClick = onPhotoClick
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = farmer.fullName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DarkNavyTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Farmer ID badge
                        Surface(
                            color = NavyPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = farmer.farmerId,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "📞 ${farmer.mobileNumber} • 📍 ${farmer.villageAddress}",
                        fontSize = 11.sp,
                        color = GreyTextSecondary
                    )

                    // Animal Category Chip
                    val isCow = farmer.milkCategory == "COW"
                    Text(
                        text = if (isCow) "🐄 गाय दूध (COW)" else "🐃 भैंस दूध (BUFFALO)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCow) DairyGreenSecondary else NavyPrimary
                    )
                }
            }

            // Action Buttons: Call, Edit, Delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("call_${farmer.farmerId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "कॉल करें",
                        tint = DairyGreenSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("edit_${farmer.farmerId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "संपादित करें",
                        tint = NavyPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_${farmer.farmerId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "हटाएं",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Farmer Registration & Edit Form with Circular Photo Picker, Address & Village Name
 */
@Composable
private fun FarmerFormDialogWithPhoto(
    initialFarmer: Farmer?,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onGenerateId: suspend () -> String,
    onSave: (Farmer) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photoUriString by remember { mutableStateOf(initialFarmer?.photoUri) }
    var showPhotoOptions by remember { mutableStateOf(false) }

    var farmerId by remember { mutableStateOf(initialFarmer?.farmerId ?: "") }
    var fullName by remember { mutableStateOf(initialFarmer?.fullName ?: "") }
    var mobile by remember { mutableStateOf(initialFarmer?.mobileNumber ?: "") }

    // Split existing address or set distinct
    val rawAddress = initialFarmer?.villageAddress ?: ""
    val addressParts = rawAddress.split(",", limit = 2)
    var villageName by remember { mutableStateOf(if (addressParts.size > 1) addressParts[0].trim() else rawAddress) }
    var addressDetail by remember { mutableStateOf(if (addressParts.size > 1) addressParts[1].trim() else "") }

    var category by remember { mutableStateOf(initialFarmer?.milkCategory ?: "COW") }

    // Bank Details
    var bankName by remember { mutableStateOf(initialFarmer?.bankName ?: "") }
    var accountHolder by remember { mutableStateOf(initialFarmer?.accountHolderName ?: "") }
    var accountNumber by remember { mutableStateOf(initialFarmer?.accountNumber ?: "") }
    var ifsc by remember { mutableStateOf(initialFarmer?.ifscCode ?: "") }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val saved = ImageStorageHelper.saveCompressedBitmap(context, bitmap, "reg_${farmerId.ifEmpty { "farmer" }}")
            if (saved != null) {
                photoUriString = saved
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val saved = ImageStorageHelper.saveCompressedUri(context, uri, "reg_${farmerId.ifEmpty { "farmer" }}")
            if (saved != null) {
                photoUriString = saved
            }
        }
    }

    remember {
        if (initialFarmer == null && farmerId.isEmpty()) {
            scope.launch {
                farmerId = onGenerateId()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialFarmer == null)
                    (if (isHindi) "नया किसान पंजीकरण (Registration)" else "New Farmer Registration")
                else
                    (if (isHindi) "किसान विवरण संपादित करें" else "Edit Farmer Details"),
                fontWeight = FontWeight.Bold,
                color = NavyPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Farmer Photo Picker (Camera / Gallery)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    FarmerAvatar(
                        photoUri = photoUriString,
                        name = fullName.ifBlank { "K" },
                        size = 80.dp,
                        showCameraBadge = true,
                        onClick = { showPhotoOptions = true }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (photoUriString.isNullOrBlank())
                            "फोटो जोड़ने के लिए टैप करें (Camera / Gallery)"
                        else
                            "फोटो बदलने के लिए टैप करें",
                        fontSize = 11.sp,
                        color = NavyPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 2. Automatic Farmer ID
                OutlinedTextField(
                    value = farmerId,
                    onValueChange = { farmerId = it },
                    label = { Text("किसान आईडी (Automatic Farmer ID) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. पूरा नाम (Full Name)
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        if (accountHolder.isBlank()) accountHolder = it
                    },
                    label = { Text("3. पूरा नाम (Full Name) *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reg_farmer_name")
                )

                // 4. Mobile Number
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("4. मोबाइल नंबर (10 Digit Mobile) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reg_farmer_mobile")
                )

                // 5. Village Name (गांव का नाम)
                OutlinedTextField(
                    value = villageName,
                    onValueChange = { villageName = it },
                    label = { Text("5. गांव का नाम (Village Name) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 6. Address (पता)
                OutlinedTextField(
                    value = addressDetail,
                    onValueChange = { addressDetail = it },
                    label = { Text("6. पूरा पता / पोस्ट / थाना (Address)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Milk Category Chips
                Text(
                    text = "दूध श्रेणी (Milk Category):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = category == "COW",
                        onClick = { category = "COW" },
                        label = { Text("गाय (COW)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DairyGreenSecondary,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = category == "BUFFALO",
                        onClick = { category = "BUFFALO" },
                        label = { Text("भैंस (BUFFALO)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 7. Bank Details (बैंक खाता विवरण)
                Text(
                    text = "7. बैंक खाता विवरण (वैकल्पिक):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("बैंक का नाम (Bank Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accountHolder,
                    onValueChange = { accountHolder = it },
                    label = { Text("खाताधारक का नाम") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("खाता संख्या (A/C Number)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ifsc,
                    onValueChange = { ifsc = it },
                    label = { Text("IFSC कोड") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (farmerId.isBlank()) {
                        errorMsg = "किसान आईडी आवश्यक है।"
                        return@Button
                    }
                    if (fullName.isBlank()) {
                        errorMsg = "किसान का नाम आवश्यक है।"
                        return@Button
                    }
                    if (mobile.trim().length < 10) {
                        errorMsg = "कृपया सही 10-अंकीय मोबाइल नंबर दर्ज करें।"
                        return@Button
                    }
                    if (villageName.isBlank()) {
                        errorMsg = "गांव का नाम आवश्यक है।"
                        return@Button
                    }

                    val fullAddress = if (addressDetail.isNotBlank()) "$villageName, $addressDetail" else villageName

                    onSave(
                        Farmer(
                            farmerId = farmerId.trim().uppercase(),
                            fullName = fullName.trim(),
                            mobileNumber = mobile.trim(),
                            villageAddress = fullAddress.trim(),
                            milkCategory = category,
                            photoUri = photoUriString,
                            bankName = bankName.trim(),
                            accountHolderName = accountHolder.ifBlank { fullName }.trim(),
                            accountNumber = accountNumber.trim(),
                            ifscCode = ifsc.trim().uppercase()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DairyGreenSecondary),
                modifier = Modifier.testTag("save_farmer_submit_btn")
            ) {
                Text(if (isHindi) "सहेजें (Save Farmer)" else "Save Farmer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isHindi) "रद्द करें" else "Cancel")
            }
        }
    )

    // Photo Source Picker (Camera vs Gallery)
    if (showPhotoOptions) {
        PhotoSourceDialog(
            farmerName = fullName.ifBlank { "किसान" },
            hasExistingPhoto = !photoUriString.isNullOrBlank(),
            onDismiss = { showPhotoOptions = false },
            onLaunchCamera = { cameraLauncher.launch(null) },
            onLaunchGallery = {
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemovePhoto = { photoUriString = null }
        )
    }
}

/**
 * Farmer Detail Dialog with Prominent Photo
 */
@Composable
private fun FarmerDetailDialogWithPhoto(
    farmer: Farmer,
    station: com.example.data.model.StationConfig,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onPhotoClick: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FarmerAvatar(
                    photoUri = farmer.photoUri,
                    name = farmer.fullName,
                    size = 54.dp,
                    showCameraBadge = true,
                    onClick = onPhotoClick
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = farmer.fullName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DarkNavyTextPrimary)
                    Text(text = "कोड: ${farmer.farmerId}", fontSize = 12.sp, color = GreyTextSecondary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📱 मोबाइल: ${farmer.mobileNumber}")
                Text("📍 गांव/पता: ${farmer.villageAddress}")
                Text("🐄 श्रेणी: ${if (farmer.milkCategory == "COW") "गाय (COW)" else "भैंस (BUFFALO)"}")
                if (farmer.bankName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("🏦 बैंक: ${farmer.bankName}")
                    Text("🔢 खाता संख्या: ${farmer.accountNumber}")
                    Text("🏛️ IFSC: ${farmer.ifscCode}")
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCall,
                    colors = ButtonDefaults.buttonColors(containerColor = DairyGreenSecondary)
                ) {
                    Text("कॉल करें (Call)")
                }
                Button(
                    onClick = onWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text("WhatsApp")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isHindi) "बंद करें" else "Close")
            }
        }
    )
}
