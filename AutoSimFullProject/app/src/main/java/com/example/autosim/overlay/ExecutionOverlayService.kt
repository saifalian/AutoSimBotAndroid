package com.example.autosim.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.example.autosim.R
import com.example.autosim.accessibility.AutomationAccessibilityService
import com.example.autosim.db.AppDatabase
import com.example.autosim.engine.SequenceRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class ExecutionOverlayService : Service() {
    companion object {
        const val CHANNEL_ID = "autosim_overlay"
        private var instance: ExecutionOverlayService? = null
        fun getInstance(): ExecutionOverlayService? = instance

        val isRunning = MutableStateFlow(false)
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning.value = true
        createNotificationChannel()
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoSim Overlay")
            .setContentText("Floating control bubble")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1337, notif)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupBubble()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubble() {
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.overlay_bubble, null)

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val btnToggleVisibility = bubbleView!!.findViewById<ImageButton>(R.id.btn_toggle_visibility)
        val panelControls = bubbleView!!.findViewById<LinearLayout>(R.id.panel_controls)

        btnToggleVisibility.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams!!.x
                    initialY = bubbleParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    bubbleParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                        // It's a click
                        panelControls.visibility = if (panelControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }

        val btnStart = bubbleView!!.findViewById<Button>(R.id.btn_start)
        val btnPause = bubbleView!!.findViewById<Button>(R.id.btn_pause)
        val btnStop = bubbleView!!.findViewById<Button>(R.id.btn_stop)

        btnStart.setOnClickListener {
            val db = AppDatabase.getDatabase(this)
            val accessibilityService = AutomationAccessibilityService.instance
            if (accessibilityService != null) {
                SequenceRunner.setAccessibilityService(accessibilityService)
                SequenceRunner.setDatabase(db)
                serviceScope.launch {
                    val currentProfileId = com.example.autosim.utils.GlobalSettings.currentProfileId.value
                    val sequences = db.sequenceDao().getAll(currentProfileId).first()
                    val enabledSequences = sequences.filter { it.isEnabled }.sortedBy { it.executionOrder }
                    
                    if (enabledSequences.isNotEmpty()) {
                        // Check for visual/OCR steps in any enabled sequence
                        var needsCapture = false
                        for (seq in enabledSequences) {
                            if (SequenceRunner.requiresScreenCapture(seq.id)) {
                                needsCapture = true
                                break
                            }
                        }
                        
                        val isCaptureActive = com.example.autosim.engine.ScreenCaptureService.isServiceActive()
                        
                        if (needsCapture && !isCaptureActive) {
                            // Need permission! Launch MainActivity to request it
                            val intent = Intent(this@ExecutionOverlayService, com.example.autosim.MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra("REQUEST_SCREEN_CAPTURE", true)
                            }
                            startActivity(intent)
                        } else {
                            // Get global settings
                            val globalLoopMode = com.example.autosim.utils.GlobalSettings.globalLoopMode
                            val globalLoopCountVal = com.example.autosim.utils.GlobalSettings.globalLoopCount
                            val loopCount = when (globalLoopMode) {
                                0 -> 1
                                1 -> globalLoopCountVal
                                else -> -1
                            }
                            
                            SequenceRunner.runPlaylist(
                                context = this@ExecutionOverlayService,
                                sequences = enabledSequences,
                                globalLoopCount = loopCount,
                                onComplete = { }
                            )
                        }
                    } else {
                        Log.e("ExecutionOverlayService", "No enabled sequences found.")
                    }
                }
            } else {
                Log.e("ExecutionOverlayService", "Accessibility service not available.")
            }
        }

        btnPause.setOnClickListener {
            if (SequenceRunner.isPaused.value) {
                SequenceRunner.resume()
            } else {
                SequenceRunner.pause()
            }
        }

        btnStop.setOnClickListener {
            SequenceRunner.stop()
        }

        windowManager?.addView(bubbleView, bubbleParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager?.removeView(bubbleView)
        instance = null
        isRunning.value = false
        SequenceRunner.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Execution Overlay",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
