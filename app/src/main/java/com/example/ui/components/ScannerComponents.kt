package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardPureWhite
import com.example.ui.theme.CowGreen
import com.example.ui.theme.DairyGreenSecondary
import com.example.ui.theme.DarkNavyTextPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldenYellowHighlight
import com.example.ui.theme.GreyTextSecondary
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SoftWhiteBackground
import com.example.util.FieldValidationStatus
import com.example.util.ImageQualityReport
import com.example.util.NormalizedCropRect
import com.example.util.OcrFieldDetail

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerImagePreviewAndToolsCard(
    capturedBitmap: Bitmap?,
    currentImageUri: Uri?,
    imageQuality: ImageQualityReport?,
    rotationAngle: Float,
    previewZoom: Float,
    cropRect: NormalizedCropRect,
    isContrastEnhanced: Boolean,
    brightnessOffset: Float,
    contrastScale: Float,
    onRotate: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onCropChange: (NormalizedCropRect) -> Unit,
    onToggleContrast: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    onContrastScaleChange: (Float) -> Unit,
    onContrastFinished: () -> Unit,
    onRescan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardPureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("इमेज पूर्वावलोकन व सुधार (Crop / Rotate / Zoom):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavyTextPrimary)
                imageQuality?.let { q ->
                    Surface(
                        color = if (q.isBlurry) ErrorRed.copy(alpha = 0.12f) else CowGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (q.isBlurry) "⚠️ धुंधली (${q.sharpnessScore.toInt()})" else "✓ स्पष्ट (${q.sharpnessScore.toInt()})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (q.isBlurry) ErrorRed else CowGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Zoomable Preview Box with Crop Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val imgModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = previewZoom,
                        scaleY = previewZoom,
                        rotationZ = rotationAngle
                    )
                if (capturedBitmap != null) {
                    Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "Captured Scan Preview",
                        modifier = imgModifier
                    )
                } else if (currentImageUri != null) {
                    AsyncImage(
                        model = currentImageUri,
                        contentDescription = "Gallery Receipt Preview",
                        modifier = imgModifier
                    )
                }

                if (cropRect.left > 0.01f || cropRect.top > 0.01f || cropRect.right < 0.99f || cropRect.bottom < 0.99f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((cropRect.right - cropRect.left).coerceIn(0.2f, 1f))
                            .height((150 * (cropRect.bottom - cropRect.top)).dp)
                            .border(BorderStroke(2.dp, GoldenYellowHighlight), RoundedCornerShape(6.dp))
                    )
                }
            }

            // Rotate, HD Contrast, Focus Crop Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = onRotate,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_rotate_image")
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("घुमाएं ${rotationAngle.toInt()}°", fontSize = 11.sp)
                }

                FilterChip(
                    selected = cropRect.left > 0.05f,
                    onClick = {
                        val next = if (cropRect.left > 0.05f) {
                            NormalizedCropRect(0f, 0f, 1f, 1f)
                        } else {
                            NormalizedCropRect(0.10f, 0.12f, 0.90f, 0.88f)
                        }
                        onCropChange(next)
                    },
                    leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(if (cropRect.left > 0.05f) "Crop सक्रिय (Reset)" else "केंद्र क्रॉप (Crop)", fontSize = 11.sp) },
                    modifier = Modifier.testTag("btn_crop_center")
                )

                FilterChip(
                    selected = isContrastEnhanced,
                    onClick = onToggleContrast,
                    leadingIcon = { Icon(Icons.Default.BrightnessHigh, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text("HD कंट्रास्ट", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NavyPrimary,
                        selectedLabelColor = Color.White
                    )
                )

                OutlinedButton(
                    onClick = onRescan,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_reapply_ocr")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("पुनः OCR स्कैन", fontSize = 11.sp)
                }
            }

            // Zoom, Brightness & Contrast Sliders
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ज़ूम:", fontSize = 10.sp, color = GreyTextSecondary)
                Slider(
                    value = previewZoom,
                    onValueChange = onZoomChange,
                    valueRange = 1f..3f,
                    modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 6.dp)
                )
                Text("${String.format("%.1f", previewZoom)}x", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("रोशनी:", fontSize = 10.sp, color = GreyTextSecondary)
                Slider(
                    value = brightnessOffset,
                    onValueChange = onBrightnessChange,
                    onValueChangeFinished = onBrightnessFinished,
                    valueRange = -40f..60f,
                    modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 6.dp)
                )
                Text("कंट्रास्ट:", fontSize = 10.sp, color = GreyTextSecondary)
                Slider(
                    value = contrastScale,
                    onValueChange = onContrastScaleChange,
                    onValueChangeFinished = onContrastFinished,
                    valueRange = 0.9f..2.0f,
                    modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ScanConfirmationFieldsTable(
    fields: List<OcrFieldDetail>,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("scan_confirmation_table"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardPureWhite),
        border = BorderStroke(1.dp, NavyPrimary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "स्कैन परिणाम तालिका (Verification Table)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Text(
                        text = "स्रोत स्थान, OCR Confidence व सत्यापन स्थिति",
                        fontSize = 10.sp,
                        color = GreyTextSecondary
                    )
                }
                TextButton(
                    onClick = onToggleEditMode,
                    modifier = Modifier.testTag("ocr_edit_details_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isEditMode) "तालिका देखें" else "विवरण सुधारें", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyPrimary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text("फ़ील्ड", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NavyPrimary, modifier = Modifier.weight(1.1f))
                Text("रीडिंग (Value)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NavyPrimary, modifier = Modifier.weight(1.3f))
                Text("Conf / स्थान", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NavyPrimary, modifier = Modifier.weight(1.0f))
            }

            fields.forEach { f ->
                val isRedAlert = f.validationStatus == FieldValidationStatus.UNREADABLE ||
                    f.validationStatus == FieldValidationStatus.MISMATCH ||
                    f.validationStatus == FieldValidationStatus.UNMATCHED
                val isWarn = f.validationStatus == FieldValidationStatus.WARNING

                val rowBg = when {
                    isRedAlert -> ErrorRed.copy(alpha = 0.10f)
                    isWarn -> AmberWarning.copy(alpha = 0.10f)
                    else -> SoftWhiteBackground
                }
                val textColor = when {
                    isRedAlert -> ErrorRed
                    isWarn -> Color(0xFF9C5700)
                    else -> DarkNavyTextPrimary
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DarkNavyTextPrimary, modifier = Modifier.weight(1.1f))
                        Text(
                            text = f.displayValue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.weight(1.3f)
                        )
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = if (f.confidence > 0) "${f.confidence}% • ${f.validationStatus.name}" else f.validationStatus.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRedAlert) ErrorRed else if (isWarn) AmberWarning else CowGreen
                            )
                            Text(
                                text = f.sourceLocation,
                                fontSize = 8.sp,
                                color = GreyTextSecondary
                            )
                        }
                    }
                    if (!f.validationNote.isNullOrBlank()) {
                        Text(
                            text = f.validationNote,
                            fontSize = 9.sp,
                            color = textColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanActionFooterButtons(
    canConfirmSave: Boolean,
    isEditing: Boolean,
    onConfirmSave: () -> Unit,
    onToggleEdit: () -> Unit,
    onScanAgain: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = canConfirmSave,
                onClick = onConfirmSave,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .testTag("ocr_confirm_save_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("पुष्टि करें व सहेजें (Confirm & Save)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onToggleEdit,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(0.9f)
                    .height(48.dp)
                    .testTag("ocr_toggle_edit_action")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isEditing) "Done" else "Edit Details", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onScanAgain,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ocr_scan_again_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("फिर स्कैन करें (Scan Again)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(0.8f)
                    .height(48.dp)
                    .testTag("ocr_cancel_btn")
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("रद्द करें (Cancel)", fontSize = 11.sp, color = ErrorRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MonthlyReportReconciliationCard(
    parsedRowCount: Int,
    printedTotalEntries: Int,
    calcAvgFat: Double,
    printedAvgFat: Double,
    calcAvgSnf: Double,
    printedAvgSnf: Double,
    calcWeight: Double,
    printedWeight: Double,
    calcAmount: Double,
    printedAmount: Double,
    isFullyReconciled: Boolean,
    reconciliationErrors: List<String>
) {
    Surface(
        color = if (isFullyReconciled) CowGreen.copy(alpha = 0.08f) else ErrorRed.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, if (isFullyReconciled) CowGreen else ErrorRed),
        modifier = Modifier.fillMaxWidth().testTag("monthly_reconciliation_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isFullyReconciled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isFullyReconciled) CowGreen else ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFullyReconciled)
                        "✓ मासिक रिपोर्ट पूर्णतः मिलान (FULLY RECONCILED)"
                    else
                        "❌ कुल मिलान असफल — पूर्ण आयात अवरुद्ध (PARTIAL_MISMATCH)",
                    color = if (isFullyReconciled) CowGreen else ErrorRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("पंक्तियां: $parsedRowCount / $printedTotalEntries", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("वजन: ${calcWeight}L / ${printedWeight}L", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("औसत FAT: $calcAvgFat% / $printedAvgFat%", fontSize = 11.sp)
                Text("औसत SNF: $calcAvgSnf% / $printedAvgSnf%", fontSize = 11.sp)
            }
            if (printedAmount > 0 || calcAmount > 0) {
                Text("राशि योग: ₹$calcAmount / मुद्रित ₹$printedAmount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            if (reconciliationErrors.isNotEmpty()) {
                reconciliationErrors.forEach { err ->
                    Text("• $err", fontSize = 10.sp, color = ErrorRed, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
