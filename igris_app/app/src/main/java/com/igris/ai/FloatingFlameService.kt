package com.igris.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingFlameService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var voiceEngine: IgrisVoice
    private lateinit var actionManager: IgrisActionManager
    private lateinit var brain: IgrisBrain

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var tvStatus: TextView
    private lateinit var flameContainer: FrameLayout

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceWithNotification()

        val prefs = getSharedPreferences("igris_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        actionManager = IgrisActionManager(this)
        brain = IgrisBrain(apiKey)

        voiceEngine = IgrisVoice(
            context = this,
            onSpeechResult = { query -> onUserSpoke(query) },
            onStatusChange = { status -> updateStatus(status) }
        )

        initFloatingFlameOverlay()
    }

    private fun initFloatingFlameOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_flame, null)

        tvStatus = floatingView.findViewById(R.id.tvOverlayStatus)
        flameContainer = floatingView.findViewById(R.id.flameContainer)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        setupDragAndTap(flameContainer)
        windowManager.addView(floatingView, layoutParams)
    }

    private fun setupDragAndTap(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isClick = false
                        }

                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            onFlameTapped()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun onFlameTapped() {
        updateStatus("Listening, Boss...")
        voiceEngine.startListening()
    }

    private fun onUserSpoke(command: String) {
        updateStatus("Thinking...")

        serviceScope.launch {
            val response = brain.processCommand(command)
            updateStatus("Executing...")

            // Speak deep voice response
            voiceEngine.speak(response.speechText)

            // Perform phone action
            when (response.actionType) {
                "CALL" -> actionManager.makeCall(response.param1)
                "APP" -> actionManager.launchApp(response.param1)
                "YOUTUBE" -> actionManager.playYouTube(response.param1)
                "TORCH" -> actionManager.toggleTorch(response.param1.uppercase() == "ON")
                "BATTERY" -> {
                    val pct = actionManager.getBatteryLevel()
                    voiceEngine.speak("Boss, battery level $pct percent hai.")
                }
                "EMAIL" -> actionManager.sendEmail(response.param1, response.param2, response.param3)
            }

            floatingView.postDelayed({
                updateStatus(null)
            }, 3000)
        }
    }

    private fun updateStatus(status: String?) {
        if (status == null) {
            tvStatus.visibility = View.GONE
        } else {
            tvStatus.text = status
            tvStatus.visibility = View.VISIBLE
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "igris_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Igris Shadow Knight Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("IGRIS Shadow Knight")
            .setContentText("Igris is online and standing by.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        voiceEngine.destroy()
    }
}
