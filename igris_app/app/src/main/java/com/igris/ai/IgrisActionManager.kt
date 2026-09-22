package com.igris.ai

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager

class IgrisActionManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var isTorchOn = false

    // 1. Phone Call Lagana
    fun makeCall(number: String) {
        val cleanNumber = number.filter { it.isDigit() || it == '+' }
        if (cleanNumber.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // 2. Phone Ki Kisi Bhi App Ko Dynamic Search Karke Kholna (Old + Any New Apps)
    fun launchApp(appName: String): Boolean {
        val query = appName.lowercase().trim()
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        var matchedPackage: String? = null

        // Sabhi installed apps ke labels check karega
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(query) || query.contains(label)) {
                matchedPackage = app.packageName
                break
            }
        }

        // Agar label direct nahi mila toh common package fallback
        if (matchedPackage == null) {
            matchedPackage = when {
                query.contains("youtube") -> "com.google.android.youtube"
                query.contains("whatsapp") -> "com.whatsapp"
                query.contains("instagram") -> "com.instagram.android"
                query.contains("chrome") -> "com.android.chrome"
                query.contains("camera") -> "com.android.camera"
                query.contains("settings") -> "com.android.settings"
                else -> null
            }
        }

        return if (matchedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    // 3. YouTube Direct Search & Play
    fun playYouTube(query: String) {
        val encodedQuery = Uri.encode(query)
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(webIntent)
    }

    // 4. Flashlight / Torch Toggle
    fun toggleTorch(enable: Boolean? = null) {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            isTorchOn = enable ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 5. Battery Status Check
    fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // 6. Smart Email Sending / Draft
    fun sendEmail(to: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
