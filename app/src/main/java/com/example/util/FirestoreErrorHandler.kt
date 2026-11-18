package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

data class FirestoreErrorInfo(
    val error: String,
    val operationType: String,
    val path: String?,
    val authUserId: String?,
    val email: String?
)

object FirestoreErrorHandler {

    fun toUserFriendlyMessage(exception: Throwable): String {
        val code = (exception as? FirebaseFirestoreException)?.code
            ?: (exception.cause as? FirebaseFirestoreException)?.code
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "अनुमति अस्वीकृत: कृपया अपने Google खाते से पुनः साइन इन करें।"
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "इंटरनेट या सर्वर उपलब्ध नहीं है। डेटा ऑफ़लाइन सहेजा गया है।"
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "प्रमाणीकरण आवश्यक है। कृपया Google खाते से लॉग इन करें।"
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "डेटाबेस रिकॉर्ड या कॉन्फ़िगरेशन नहीं मिला।"
            else -> exception.localizedMessage ?: "क्लाउड सिंक त्रुटि"
        }
    }

    fun handleFirestoreError(
        error: Exception,
        operationType: OperationType,
        path: String?,
        auth: FirebaseAuth? = null
    ) {
        val user = runCatching { auth?.currentUser ?: FirebaseAuth.getInstance().currentUser }.getOrNull()
        val json = JSONObject().apply {
            put("error", error.message ?: "Unknown Firestore error")
            put("operationType", operationType.name.lowercase())
            put("path", path ?: "")
            put("authInfo", JSONObject().apply {
                put("userId", user?.uid ?: "null")
                put("email", user?.email ?: "null")
                put("emailVerified", user?.isEmailVerified ?: false)
            })
        }
        Log.e("FirestoreErrorHandler", "Firestore Error: $json", error)
    }
}
