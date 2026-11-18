package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.Farmer
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.PaymentRecord
import com.example.data.model.StationConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    /**
     * Generate Milk Collection Receipt PDF with Official Logo
     */
    fun generateMilkReceiptPdf(
        context: Context,
        station: StationConfig,
        entry: MilkCollectionEntry,
        farmer: Farmer?
    ): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(400, 620, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(23, 54, 93) // Deep Navy Blue (#17365D)
            textSize = 17f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val subPaint = Paint().apply {
            color = Color.rgb(102, 112, 133) // Grey (#667085)
            textSize = 10f
            textAlign = Paint.Align.LEFT
        }
        val textPaint = Paint().apply {
            color = Color.rgb(23, 43, 77) // Dark Navy
            textSize = 11f
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(23, 43, 77)
            textSize = 11f
            isFakeBoldText = true
        }
        val greenPaint = Paint().apply {
            color = Color.rgb(35, 155, 86) // Dairy Green (#239B56)
            textSize = 16f
            isFakeBoldText = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(230, 235, 245)
            strokeWidth = 1f
        }

        // Draw Official Logo at top left
        try {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_namaste_india_logo)
            if (logo != null) {
                val scaled = Bitmap.createScaledBitmap(logo, 50, 50, true)
                canvas.drawBitmap(scaled, 25f, 20f, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Header Title next to logo
        canvas.drawText(station.dairyName, 85f, 36f, titlePaint)
        canvas.drawText(station.centerName, 85f, 50f, subPaint)
        canvas.drawText("केंद्र कोड: ${station.stationId} | संपर्क: ${station.contactNumber}", 85f, 63f, subPaint)

        var y = 82f
        canvas.drawLine(20f, y, 380f, y, linePaint)

        y += 22f
        boldPaint.textSize = 13f
        canvas.drawText("दूध संग्रहण रसीद (Milk Collection Receipt)", 25f, y, boldPaint)
        boldPaint.textSize = 11f
        y += 18f

        // Draw Farmer Photo if exists
        if (!farmer?.photoUri.isNullOrBlank()) {
            try {
                val fFile = File(farmer!!.photoUri!!)
                if (fFile.exists()) {
                    val fBitmap = BitmapFactory.decodeFile(fFile.absolutePath)
                    if (fBitmap != null) {
                        val scaledPhoto = Bitmap.createScaledBitmap(fBitmap, 48, 48, true)
                        canvas.drawBitmap(scaledPhoto, 320f, y, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val details = listOf(
            "रसीद संख्या (Receipt No)" to entry.receiptNumber,
            "दिनांक व समय (Date)" to "${entry.date} ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(entry.timestamp))}",
            "पारी (Shift)" to if (entry.shift == "MORNING") "सुबह (Morning)" else "शाम (Evening)",
            "किसान कोड (Farmer ID)" to entry.farmerId,
            "किसान का नाम (Farmer Name)" to entry.farmerName,
            "मोबाइल नंबर (Mobile)" to (farmer?.mobileNumber ?: "N/A"),
            "गांव/पता (Address)" to (farmer?.villageAddress ?: "N/A"),
            "दूध श्रेणी (Category)" to if (entry.milkCategory == "COW") "गाय (COW)" else "भैंस (BUFFALO)"
        )

        for ((label, value) in details) {
            canvas.drawText(label, 25f, y, textPaint)
            canvas.drawText(value, 185f, y, boldPaint)
            y += 18f
        }

        y += 8f
        canvas.drawLine(20f, y, 380f, y, linePaint)
        y += 20f

        // Table Header
        canvas.drawText("मात्रा (Qty)", 25f, y, boldPaint)
        canvas.drawText("Fat %", 110f, y, boldPaint)
        canvas.drawText("SNF %", 170f, y, boldPaint)
        canvas.drawText("Water %", 230f, y, boldPaint)
        canvas.drawText("दर (Rate)", 300f, y, boldPaint)

        y += 18f
        canvas.drawText("${entry.quantity} L", 25f, y, textPaint)
        canvas.drawText("${entry.fat}%", 110f, y, textPaint)
        canvas.drawText("${entry.snf}%", 170f, y, textPaint)
        canvas.drawText("${entry.waterPercentage}%", 230f, y, textPaint)
        canvas.drawText("₹${entry.rate}", 300f, y, textPaint)

        y += 18f
        canvas.drawLine(20f, y, 380f, y, linePaint)
        y += 26f

        // Total Amount Box
        boldPaint.textSize = 15f
        boldPaint.color = Color.rgb(23, 54, 93)
        canvas.drawText("कुल देय राशि (Total Amount):", 25f, y, boldPaint)
        canvas.drawText("₹${String.format("%.2f", entry.totalAmount)}", 240f, y, greenPaint)
        boldPaint.textSize = 11f
        boldPaint.color = Color.rgb(23, 43, 77)

        y += 35f
        val centerSubPaint = Paint().apply {
            color = Color.rgb(102, 112, 133)
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("धन्यवाद! नमस्ते इंडिया दूध संग्रहण केंद्र, तुर्की", 200f, y, centerSubPaint)
        y += 14f
        canvas.drawText("यह एक प्रमाणित कंप्यूटर जनरेटेड रसीद है।", 200f, y, centerSubPaint)

        document.finishPage(page)

        return savePdfAndGetUri(context, document, "MilkReceipt_${entry.receiptNumber}.pdf")
    }

    /**
     * Generate Farmer Account Statement PDF with Official Logo
     */
    fun generateFarmerStatementPdf(
        context: Context,
        station: StationConfig,
        farmer: Farmer,
        collections: List<MilkCollectionEntry>,
        payments: List<PaymentRecord>,
        totalMilk: Double,
        totalBill: Double,
        totalPaid: Double,
        finalBalance: Double
    ): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(23, 54, 93) // Deep Navy
            textSize = 18f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val subPaint = Paint().apply {
            color = Color.rgb(102, 112, 133)
            textSize = 10f
            textAlign = Paint.Align.LEFT
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(23, 43, 77)
            textSize = 11f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.rgb(23, 43, 77)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(230, 235, 245)
            strokeWidth = 1f
        }

        // Draw Namaste India Logo at top
        try {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_namaste_india_logo)
            if (logo != null) {
                val scaled = Bitmap.createScaledBitmap(logo, 52, 52, true)
                canvas.drawBitmap(scaled, 30f, 25f, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        canvas.drawText(station.dairyName, 95f, 42f, titlePaint)
        canvas.drawText("${station.centerName} (${station.stationId})", 95f, 56f, subPaint)
        canvas.drawText("पता: ${station.address} | संपर्क: ${station.contactNumber}", 95f, 69f, subPaint)

        var y = 88f
        canvas.drawLine(30f, y, 565f, y, linePaint)

        y += 24f
        boldPaint.textSize = 14f
        canvas.drawText("किसान खाता विवरण (Farmer Account Statement)", 30f, y, boldPaint)
        boldPaint.textSize = 11f

        y += 22f
        canvas.drawText("किसान: ${farmer.fullName} (${farmer.farmerId})", 35f, y, boldPaint)
        canvas.drawText("मोबाइल: ${farmer.mobileNumber}", 330f, y, textPaint)
        y += 16f
        canvas.drawText("गांव/पता: ${farmer.villageAddress}", 35f, y, textPaint)
        canvas.drawText("श्रेणी: ${if (farmer.milkCategory == "COW") "गाय (COW)" else "भैंस (BUFFALO)"}", 330f, y, textPaint)

        y += 20f
        // Summary Box in Soft Navy & Green tint
        val boxPaint = Paint().apply {
            color = Color.rgb(235, 245, 240) // Soft Green container
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(30f, y, 565f, y + 50f, 8f, 8f, boxPaint)
        y += 20f
        canvas.drawText("कुल दूध: ${String.format("%.1f", totalMilk)} L", 45f, y, boldPaint)
        canvas.drawText("कुल बिल: ₹${String.format("%.2f", totalBill)}", 175f, y, boldPaint)
        canvas.drawText("कुल भुगतान: ₹${String.format("%.2f", totalPaid)}", 305f, y, boldPaint)
        boldPaint.color = Color.rgb(23, 54, 93)
        canvas.drawText("बकाया राशि: ₹${String.format("%.2f", finalBalance)}", 435f, y, boldPaint)
        boldPaint.color = Color.rgb(23, 43, 77)

        y += 45f
        canvas.drawLine(30f, y, 565f, y, linePaint)
        y += 20f

        // Table Header
        canvas.drawText("DATE", 35f, y, boldPaint)
        canvas.drawText("SHIFT", 100f, y, boldPaint)
        canvas.drawText("QTY", 155f, y, boldPaint)
        canvas.drawText("FAT", 205f, y, boldPaint)
        canvas.drawText("SNF", 255f, y, boldPaint)
        canvas.drawText("RATE", 310f, y, boldPaint)
        canvas.drawText("AMOUNT", 380f, y, boldPaint)
        canvas.drawText("RCP NO", 470f, y, boldPaint)

        y += 8f
        canvas.drawLine(30f, y, 565f, y, linePaint)
        y += 16f

        val recentCollections = collections.take(24)
        for (item in recentCollections) {
            canvas.drawText(item.date, 35f, y, textPaint)
            canvas.drawText(if (item.shift == "MORNING") "सुबह" else "शाम", 100f, y, textPaint)
            canvas.drawText("${item.quantity}L", 155f, y, textPaint)
            canvas.drawText("${item.fat}%", 205f, y, textPaint)
            canvas.drawText("${item.snf}%", 255f, y, textPaint)
            canvas.drawText("₹${item.rate}", 310f, y, textPaint)
            canvas.drawText("₹${String.format("%.2f", item.totalAmount)}", 380f, y, boldPaint)
            canvas.drawText(item.receiptNumber.takeLast(7), 470f, y, textPaint)
            y += 16f
            if (y > 800f) break
        }

        document.finishPage(page)
        return savePdfAndGetUri(context, document, "FarmerStatement_${farmer.farmerId}.pdf")
    }

    private fun savePdfAndGetUri(context: Context, document: PdfDocument, fileName: String): Uri? {
        return try {
            val fileDir = File(context.cacheDir, "pdf_reports")
            if (!fileDir.exists()) fileDir.mkdirs()
            val file = File(fileDir, fileName)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            outputStream.close()
            document.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
