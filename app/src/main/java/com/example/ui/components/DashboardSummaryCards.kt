package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DairyRedPrimary
import com.example.ui.viewmodel.DashboardSummary

@Composable
fun DashboardSummaryCards(
    summary: DashboardSummary,
    isHindi: Boolean,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onClickTotalMilk: () -> Unit,
    onClickPayableAmount: () -> Unit,
    onClickRegisteredFarmers: () -> Unit,
    onClickMorningCollection: () -> Unit,
    onClickEveningCollection: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (errorMessage != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "त्रुटि: $errorMessage",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp
            )
        }
        return
    }

    if (isLoading) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = DairyRedPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isHindi) "डेटा लोड हो रहा है..." else "Loading real-time data...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Today's Milk (Liters) & Total Payable Amount (₹)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InteractiveMetricCard(
                    title = if (isHindi) "आज का कुल दूध" else "Today's Milk",
                    value = "${String.format("%.1f", summary.todayMilksLiters)} L",
                    subtitle = if (isHindi) {
                        if (summary.todayEntriesCount > 0) "${summary.todayEntriesCount} पर्चियां दर्ज • विवरण देखें ➔"
                        else "कोई प्रविष्टि नहीं • विवरण देखें ➔"
                    } else {
                        "${summary.todayEntriesCount} receipts • View Report ➔"
                    },
                    icon = Icons.Default.LocalDrink,
                    iconTint = Color(0xFF17365D), // Deep Navy Blue
                    iconBgColor = Color(0xFFEBF3FB),
                    testTag = "card_today_total_milk",
                    onClick = onClickTotalMilk,
                    modifier = Modifier.weight(1f)
                )

                InteractiveMetricCard(
                    title = if (isHindi) "कुल देय राशि" else "Total Payable",
                    value = "₹${String.format("%.2f", summary.todayTotalAmount)}",
                    subtitle = if (isHindi) "औसत Fat: ${String.format("%.1f", summary.todayAverageFat)}% • हिसाब देखें ➔"
                               else "Avg Fat: ${String.format("%.1f", summary.todayAverageFat)}% • View Payable ➔",
                    icon = Icons.Default.CurrencyRupee,
                    iconTint = Color(0xFF239B56), // Fresh Dairy Green
                    iconBgColor = Color(0xFFE8F8F0),
                    testTag = "card_today_payable_amount",
                    onClick = onClickPayableAmount,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Registered Farmers, Morning Collection, Evening Collection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InteractiveSmallMetricCard(
                    title = if (isHindi) "पंजीकृत किसान" else "Registered Farmers",
                    value = "${summary.totalFarmersCount}",
                    hint = if (isHindi) "सूची खोलें ➔" else "View List ➔",
                    icon = Icons.Default.People,
                    iconTint = Color(0xFF17365D), // Deep Navy Blue
                    iconBgColor = Color(0xFFEBF3FB),
                    testTag = "card_registered_farmers",
                    onClick = onClickRegisteredFarmers,
                    modifier = Modifier.weight(1f)
                )

                InteractiveSmallMetricCard(
                    title = if (isHindi) "सुबह संग्रहण" else "Morning Shift",
                    value = "${String.format("%.1f", summary.morningCollectionLiters)} L",
                    hint = if (isHindi) "सुबह विवरण ➔" else "Morning Details ➔",
                    icon = Icons.Default.WbSunny,
                    iconTint = Color(0xFFE67E22), // Warm Golden / Orange
                    iconBgColor = Color(0xFFFEF5E7),
                    testTag = "card_morning_collection",
                    onClick = onClickMorningCollection,
                    modifier = Modifier.weight(1f)
                )

                InteractiveSmallMetricCard(
                    title = if (isHindi) "शाम संग्रहण" else "Evening Shift",
                    value = "${String.format("%.1f", summary.eveningCollectionLiters)} L",
                    hint = if (isHindi) "शाम विवरण ➔" else "Evening Details ➔",
                    icon = Icons.Default.NightsStay,
                    iconTint = Color(0xFF2E4053), // Slate Navy
                    iconBgColor = Color(0xFFEAEDED),
                    testTag = "card_evening_collection",
                    onClick = onClickEveningCollection,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InteractiveMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBgColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Physics spring tap & hover animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else if (isHovered) 1.02f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "metric_card_scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else if (isHovered) 6.dp else 2.dp,
        label = "metric_card_elevation"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF667085)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF172B4D)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = iconTint
                )
            }
        }
    }
}

@Composable
private fun InteractiveSmallMetricCard(
    title: String,
    value: String,
    hint: String,
    icon: ImageVector,
    iconTint: Color,
    iconBgColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Physics spring tap & hover animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (isHovered) 1.03f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "small_card_scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else if (isHovered) 5.dp else 1.5.dp,
        label = "small_card_elevation"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF172B4D)
            )

            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF667085)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = hint,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = iconTint
            )
        }
    }
}
