package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.Farmer
import com.example.data.model.MilkCollectionEntry
import com.example.data.model.PaymentRecord
import com.example.data.model.StationConfig
import java.net.URLEncoder

object WhatsAppHelper {

    /**
     * Formats professional Hindi WhatsApp text for milk collection
     */
    fun formatMilkBillMessage(
        station: StationConfig,
        entry: MilkCollectionEntry,
        farmer: Farmer?
    ): String {
        val shiftHindi = if (entry.shift == "MORNING") "सुबह (Morning)" else "शाम (Evening)"
        val categoryHindi = if (entry.milkCategory == "COW") "गाय (Cow)" else "भैंस (Buffalo)"

        return """
🥛 *${station.dairyName}*
📍 *${station.centerName}* (आईडी: ${station.stationId})
-----------------------------------------
प्रिय किसान भाई,
आपके दूध संग्रहण की पर्ची विवरण:

👤 *किसान का नाम:* ${entry.farmerName}
🏷️ *किसान कोड:* ${entry.farmerId}
📅 *दिनांक:* ${entry.date} (${shiftHindi})
🔢 *रसीद संख्या:* ${entry.receiptNumber}
🐄 *दूध श्रेणी:* $categoryHindi

📊 *मात्रा (दूध):* ${entry.quantity} लीटर / किग्रा
🧪 *Fat:* ${entry.fat}%
🔬 *SNF:* ${entry.snf}%
💧 *Water:* ${entry.waterPercentage}%
💰 *दर (Rate):* ₹${entry.rate} / लीटर

💵 *कुल राशि (Total):* ₹${String.format("%.2f", entry.totalAmount)}
-----------------------------------------
धन्यवाद! संपर्क: ${station.contactNumber}
        """.trimIndent()
    }

    /**
     * Formats professional Hindi WhatsApp text for payment receipt
     */
    fun formatPaymentMessage(
        station: StationConfig,
        payment: PaymentRecord
    ): String {
        return """
💵 *भुगतान रसीद - ${station.dairyName}*
📍 *${station.centerName}*
-----------------------------------------
👤 *किसान का नाम:* ${payment.farmerName} (${payment.farmerId})
🔢 *लेनदेन आईडी:* ${payment.transactionId}
📅 *भुगतान तिथि:* ${payment.paymentDate}
💰 *भुगतान राशि:* ₹${String.format("%.2f", payment.paymentAmount)}
💳 *भुगतान माध्यम:* ${payment.paymentMode} (${payment.paymentType})
📝 *विवरण:* ${if (payment.note.isNotBlank()) payment.note else "दूध भुगतान"}

📉 *शेष बकाया राशि:* ₹${String.format("%.2f", payment.finalBalance)}
-----------------------------------------
नमस्ते इंडिया दूध संग्रहण केंद्र
        """.trimIndent()
    }

    /**
     * Formats professional Hindi WhatsApp statement
     */
    fun formatStatementMessage(
        station: StationConfig,
        farmer: Farmer,
        totalMilk: Double,
        totalBill: Double,
        totalPaid: Double,
        finalBalance: Double
    ): String {
        return """
📋 *खाता विवरण (Account Statement)*
🥛 *${station.dairyName}* - ${station.centerName}
-----------------------------------------
👤 *किसान:* ${farmer.fullName} (${farmer.farmerId})
📞 *मोबाइल:* ${farmer.mobileNumber}
🏡 *गांव:* ${farmer.villageAddress}

🥛 *कुल दूध:* ${String.format("%.1f", totalMilk)} Ltr
💵 *कुल दूध बिल:* ₹${String.format("%.2f", totalBill)}
💳 *प्राप्त भुगतान:* ₹${String.format("%.2f", totalPaid)}
⚠️ *अंतिम बकाया राशि:* ₹${String.format("%.2f", finalBalance)}
-----------------------------------------
सटीक व पारदर्शी हिसाब। संपर्क: ${station.contactNumber}
        """.trimIndent()
    }

    /**
     * Open WhatsApp with recipient phone & prefilled message
     */
    fun sendWhatsAppText(context: Context, mobile: String, message: String) {
        try {
            val cleanNumber = mobile.replace("+", "").replace("-", "").replace(" ", "").trim()
            val formattedNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=$encodedMessage"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // General share fallback
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "व्हाट्सएप / संदेश साझा करें"))
        }
    }

    /**
     * Share PDF file via WhatsApp / Android ShareSheet
     */
    fun sharePdf(context: Context, pdfUri: Uri, title: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "$title साझा करें"))
        } catch (e: Exception) {
            Toast.makeText(context, "साझा करने में त्रुटि: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Direct Phone Call Launcher
     */
    fun makePhoneCall(context: Context, mobile: String) {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$mobile")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "कॉल शुरू करने में असमर्थ: $mobile", Toast.LENGTH_SHORT).show()
        }
    }
}
