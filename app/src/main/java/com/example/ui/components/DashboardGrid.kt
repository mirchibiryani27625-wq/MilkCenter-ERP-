package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DairyScreen

data class DashboardMenuItem(
    val screen: DairyScreen,
    val titleHindi: String,
    val titleEnglish: String,
    val subtitleHindi: String,
    val subtitleEnglish: String,
    val icon: ImageVector,
    val badge: String? = null,
    val accentColor: Color,
    val testTag: String
)

@Composable
fun DashboardGrid(
    isHindi: Boolean,
    onNavigate: (DairyScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val menuItems = listOf(
        DashboardMenuItem(
            screen = DairyScreen.FARMER_MANAGEMENT,
            titleHindi = "किसान प्रबंधन",
            titleEnglish = "Farmer Management",
            subtitleHindi = "पंजीकरण, विवरण व सूची",
            subtitleEnglish = "Register, profiles & list",
            icon = Icons.Default.People,
            accentColor = Color(0xFF17365D), // Deep Navy Blue
            testTag = "menu_farmer_management"
        ),
        DashboardMenuItem(
            screen = DairyScreen.MILK_COLLECTION,
            titleHindi = "दूध संग्रहण",
            titleEnglish = "Milk Collection",
            subtitleHindi = "सुबह/शाम पर्ची + AI कैमरा",
            subtitleEnglish = "Morning/Evening + AI Scan",
            icon = Icons.Default.LocalDrink,
            badge = "AI Scan",
            accentColor = Color(0xFF239B56), // Fresh Dairy Green
            testTag = "menu_milk_collection"
        ),
        DashboardMenuItem(
            screen = DairyScreen.REPORTS_ANALYSIS,
            titleHindi = "रिपोर्ट्स व विश्लेषण",
            titleEnglish = "Reports & Analysis",
            subtitleHindi = "दैनिक/मासिक PDF व Excel",
            subtitleEnglish = "Daily/Monthly PDF & CSV",
            icon = Icons.Default.Analytics,
            accentColor = Color(0xFF17365D), // Deep Navy Blue
            testTag = "menu_reports_analysis"
        ),
        DashboardMenuItem(
            screen = DairyScreen.FARMER_ACCOUNT,
            titleHindi = "किसान खाता / लेजर",
            titleEnglish = "Farmer Account",
            subtitleHindi = "खाता बही व WhatsApp विवरण",
            subtitleEnglish = "Passbook & WhatsApp bill",
            icon = Icons.Default.AccountBalance,
            accentColor = Color(0xFF239B56), // Fresh Dairy Green
            testTag = "menu_farmer_account"
        ),
        DashboardMenuItem(
            screen = DairyScreen.PAYMENT_MANAGEMENT,
            titleHindi = "भुगतान प्रबंधन",
            titleEnglish = "Payment Management",
            subtitleHindi = "नकद / UPI भुगतान व रसीद",
            subtitleEnglish = "Cash/UPI payments & receipt",
            icon = Icons.Default.Payments,
            accentColor = Color(0xFFE67E22), // Golden Orange
            testTag = "menu_payment_management"
        ),
        DashboardMenuItem(
            screen = DairyScreen.SYSTEM_SETTINGS,
            titleHindi = "सिस्टम सेटिंग्स",
            titleEnglish = "System Settings",
            subtitleHindi = "रेट चार्ट, बैकअप व सुरक्षा",
            subtitleEnglish = "Rate charts, backup & PIN",
            icon = Icons.Default.Settings,
            accentColor = Color(0xFF667085), // Slate Grey
            testTag = "menu_system_settings"
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 600.dp) 3 else 2

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val chunked = menuItems.chunked(columns)
            for (rowItems in chunked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (item in rowItems) {
                        SquareMenuCard(
                            item = item,
                            isHindi = isHindi,
                            onClick = { onNavigate(item.screen) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining empty columns if last row has fewer items
                    if (rowItems.size < columns) {
                        for (i in 0 until (columns - rowItems.size)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquareMenuCard(
    item: DashboardMenuItem,
    isHindi: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f) // Strict square card as requested!
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(item.testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Optional Badge on top right (e.g. AI Scan)
            if (item.badge != null) {
                Surface(
                    color = item.accentColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.size(3.dp))
                        Text(
                            text = item.badge,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Center Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (item.badge != null) 8.dp else 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(item.accentColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.titleEnglish,
                        tint = item.accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isHindi) item.titleHindi else item.titleEnglish,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isHindi) item.subtitleHindi else item.subtitleEnglish,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
