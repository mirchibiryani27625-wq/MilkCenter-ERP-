package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppNotification
import com.example.data.model.CloudSyncStatus
import com.example.data.model.StationConfig
import com.example.data.model.UserSession
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DairyScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DairyHeader(
    station: StationConfig,
    session: UserSession,
    currentScreen: DairyScreen,
    isHindi: Boolean,
    onNavigateBack: () -> Unit,
    onToggleLanguage: () -> Unit,
    onLogout: () -> Unit,
    syncStatus: CloudSyncStatus = CloudSyncStatus(),
    notifications: List<AppNotification> = emptyList(),
    onSyncNow: () -> Unit = {},
    onMarkNotificationRead: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showProfileMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    val formattedDate = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DairyRedPrimary,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Row: Back button (if in subscreen) or Icon, App Name & Station Name, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (currentScreen != DairyScreen.DASHBOARD && currentScreen != DairyScreen.LOGIN) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("header_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "वापस जाएं (Back)",
                                tint = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(38.dp)
                            .padding(end = 4.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_namaste_india_logo),
                            contentDescription = "नमस्ते इंडिया लोगो",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = station.dairyName,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = com.example.ui.theme.GoldenYellowHighlight,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = station.stationId,
                                    color = com.example.ui.theme.DarkNavyTextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = station.centerName,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Actions: Language Toggle, Notification, Profile
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier.testTag("lang_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "भाषा बदलें",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isHindi) "EN" else "हिन्दी",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { showNotificationsDialog = true },
                        modifier = Modifier.testTag("notification_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(containerColor = Color(0xFFFFD54F), contentColor = Color.Black) {
                                        Text("$unreadCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "सूचनाएं (Notifications)",
                                tint = Color.White
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showProfileMenu = true },
                            modifier = Modifier.testTag("profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "प्रोफ़ाइल (Profile)",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = session.userName.ifEmpty { "Dairy User" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (session.email.isNotBlank()) {
                                            Text(
                                                text = session.email,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "भूमिका: ${session.role.name}",
                                            fontSize = 12.sp,
                                            color = DairyRedPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                },
                                onClick = { showProfileMenu = false }
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = DairyRedPrimary
                                    )
                                },
                                text = {
                                    Text(
                                        text = if (isHindi) "क्लाउड सिंक अभी करें" else "Sync Now",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    showProfileMenu = false
                                    onSyncNow()
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                text = {
                                    Text(
                                        text = if (isHindi) "लॉग आउट करें" else "Log Out",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                onClick = {
                                    showProfileMenu = false
                                    showLogoutConfirm = true
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Sub-bar: Date and Live Cloud Sync Status Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 $formattedDate",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                val statusDotColor = when {
                    syncStatus.isSyncing -> Color(0xFFFFD54F)
                    !syncStatus.isOnline -> Color(0xFFFFAB40)
                    syncStatus.pendingCount > 0 -> Color(0xFFFFD54F)
                    else -> Color(0xFF69F0AE)
                }
                val statusLabel = when {
                    syncStatus.isSyncing -> if (isHindi) "क्लाउड सिंक जारी..." else "Syncing Cloud..."
                    !syncStatus.isOnline && syncStatus.pendingCount > 0 ->
                        if (isHindi) "ऑफ़लाइन • ${syncStatus.pendingCount} लंबित" else "Offline • ${syncStatus.pendingCount} Pending"
                    !syncStatus.isOnline -> if (isHindi) "ऑफ़लाइन मोड" else "Offline Mode"
                    syncStatus.pendingCount > 0 ->
                        if (isHindi) "सिंक लंबित (${syncStatus.pendingCount})" else "Tap to Sync (${syncStatus.pendingCount})"
                    else -> if (isHindi) "Firebase सिंक सक्रिय" else "Firebase Synced"
                }

                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable { onSyncNow() }
                        .testTag("header_sync_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        if (syncStatus.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = Color.White,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusDotColor, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = {
                Text(
                    text = if (isHindi) "लॉग आउट की पुष्टि" else "Confirm Logout",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isHindi)
                        "क्या आप वास्तव में लॉग आउट करना चाहते हैं? आपका ऑफलाइन और क्लाउड डेटा सुरक्षित रहेगा।"
                    else
                        "Are you sure you want to log out? Your local and cloud synced data remain safe."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isHindi) "हाँ, लॉग आउट करें" else "Yes, Log Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    // Notifications Dialog backed by live Room + Firestore Notifications collection
    if (showNotificationsDialog) {
        val timeFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = DairyRedPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "सूचनाएं (${notifications.size})" else "Notifications (${notifications.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (notifications.isEmpty()) {
                        Text(
                            text = if (isHindi)
                                "• नमस्ते इंडिया दूध संग्रहण केंद्र (${station.stationId}) सक्रिय है।\n• AI स्मार्ट कैमरा और Firebase क्लाउड सिंक तैयार हैं।"
                            else
                                "• Collection Center (${station.stationId}) is active.\n• AI Smart Camera & Firebase Cloud Sync are ready.",
                            fontSize = 13.sp
                        )
                    } else {
                        notifications.take(15).forEach { notif ->
                            Surface(
                                color = if (notif.isRead) MaterialTheme.colorScheme.surfaceVariant
                                else DairyRedPrimary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!notif.isRead) onMarkNotificationRead(notif.notificationId)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = notif.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (notif.isRead) MaterialTheme.colorScheme.onSurface else DairyRedPrimary
                                        )
                                        Text(
                                            text = timeFmt.format(Date(notif.timestamp)),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = notif.message, fontSize = 12.sp)
                                    if (!notif.isRead) {
                                        Text(
                                            text = if (isHindi) "पठित के रूप में चिह्नित करने के लिए टैप करें" else "Tap to mark read",
                                            fontSize = 10.sp,
                                            color = DairyRedPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text(if (isHindi) "बंद करें" else "Close")
                }
            }
        )
    }
}
