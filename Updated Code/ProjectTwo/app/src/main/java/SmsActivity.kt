package com.zybooks.cs360project2updated

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SmsActivity : AppCompatActivity() {

    // UI elements
    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnSendSms: Button

    // SmsHandler instance for sending SMS
    private lateinit var smsHandler: SmsHandler

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms)

        // Initialize SmsHandler and UI elements
        smsHandler = SmsHandler(this)
        tvPermissionStatus = findViewById(R.id.tv_permission_status)
        btnSendSms = findViewById(R.id.btn_send_sms)

        // Set click listener to send SMS
        btnSendSms.setOnClickListener {
            sendSmsNotification("Congratulations, You've Reached Your Goal!!!")
        }
    }

    // Method to send SMS notification using SmsHandler
    private fun sendSmsNotification(message: String) {
        val phoneNumber = "4124124122"
        smsHandler.sendNotification(phoneNumber, message)
    }
}
