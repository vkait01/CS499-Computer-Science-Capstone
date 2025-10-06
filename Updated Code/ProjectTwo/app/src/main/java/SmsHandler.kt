package com.zybooks.cs360project2updated

import android.content.Context
import android.telephony.SmsManager

// Responsible for sending SMS notifications.
class SmsHandler(private val context: Context) {

    // Sends SMS to provided phone number.
    fun sendNotification(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            // Send SMS.
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
        }
    }

    fun checkSmsPermission() {

    }
}
