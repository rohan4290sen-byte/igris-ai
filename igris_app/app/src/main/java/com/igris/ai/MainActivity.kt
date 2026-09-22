package com.igris.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var btnToggleIgris: Button
    private lateinit var btnPermissions: Button
    private lateinit var tvStatus: TextView

    private val PERMISSION_REQUEST_CODE = 101
    private val OVERLAY_PERMISSION_REQ_CODE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiKey = findViewById(R.id.etApiKey)
        btnToggleIgris = findViewById(R.id.btnToggleIgris)
        btnPermissions = findViewById(R.id.btnPermissions)
        tvStatus = findViewById(R.id.tvStatus)

        loadSavedApiKey()

        btnPermissions.setOnClickListener {
            checkAndRequestPermissions()
        }

        btnToggleIgris.setOnClickListener {
            saveApiKey()
            if (checkOverlayPermission()) {
                startFloatingFlameService()
            } else {
                requestOverlayPermission()
            }
        }
    }

    private fun loadSavedApiKey() {
        val prefs = getSharedPreferences("igris_prefs", Context.MODE_PRIVATE)
        val key = prefs.getString("gemini_api_key", "") ?: ""
        etApiKey.setText(key)
    }

    private fun saveApiKey() {
        val key = etApiKey.text.toString().trim()
        val prefs = getSharedPreferences("igris_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "Igris Floating Flame ke liye overlay permission allow karein", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Toast.makeText(this, "Saari permissions pehle se granted hain, Boss!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFloatingFlameService() {
        val intent = Intent(this, FloatingFlameService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        tvStatus.text = "Status: Igris Flame is ACTIVE on Screen!"
        Toast.makeText(this, "Igris Activated! Screen par Flame aa chuki hai.", Toast.LENGTH_SHORT).show()
        
        // Minimize app to show floating flame
        moveTaskToBack(true)
    }
}
