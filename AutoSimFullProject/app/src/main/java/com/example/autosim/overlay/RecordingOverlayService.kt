package com.example.autosim.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import com.example.autosim.R
import com.example.autosim.accessibility.AutomationAccessibilityService
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.Macro
import com.example.autosim.db.MacroAction
import com.example.autosim.utils.GlobalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class RecordingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var controlView: View? = null
    private var touchView: View? = null
    private var isRecording = false
    private var isPaused = false
    private var startTime = 0L
    private var lastActionTime = 0L
    
    private val recordedActions = mutableListOf<MacroAction>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var db: AppDatabase

    // Touch tracking
    private var startX = 0f
    private var startY = 0f
    private var touchDownTime = 0L
    private val SWIPE_THRESHOLD = 20

    // Window Params for dragging
    private lateinit var controlParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        db = AppDatabase.getDatabase(applicationContext)
        showControlOverlay()
    }

    private fun showControlOverlay() {
        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        // Create a compact layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xAA000000.toInt())
            setPadding(8, 8, 8, 8)
        }

        // Start/Pause Button (Merged)
        val btnStartPause = ImageButton(this).apply {
            setImageResource(R.drawable.ic_record) // Initial state: Record
            setBackgroundColor(0) // Transparent background
            setPadding(16, 16, 16, 16)
            setOnClickListener { 
                if (!isRecording) {
                    startRecording()
                    setImageResource(R.drawable.ic_pause)
                } else {
                    if (isPaused) {
                        resumeRecording()
                        setImageResource(R.drawable.ic_pause)
                    } else {
                        pauseRecording()
                        setImageResource(R.drawable.ic_play) // Show Play to resume
                    }
                }
            }
        }
        
        // Stop Button
        val btnStop = ImageButton(this).apply {
            setImageResource(R.drawable.ic_stop)
            setBackgroundColor(0)
            setPadding(16, 16, 16, 16)
            isEnabled = false
            alpha = 0.5f
            setOnClickListener { 
                stopRecording() 
                // Reset Start/Pause button
                btnStartPause.setImageResource(R.drawable.ic_record)
                isEnabled = false
                alpha = 0.5f
            }
        }
        
        // Close Button
        val btnClose = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(0)
            setPadding(16, 16, 16, 16)
            setOnClickListener { stopSelf() }
        }

        layout.addView(btnStartPause)
        layout.addView(btnStop)
        layout.addView(btnClose)

        // Drag Logic
        layout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = controlParams.x
                        initialY = controlParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        controlParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        controlParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(controlView, controlParams)
                        return true
                    }
                }
                return false
            }
        })

        controlView = layout
        windowManager.addView(controlView, controlParams)
        
        // Store references to update UI later
        btnStartPause.tag = "start_pause"
        btnStop.tag = "stop"
    }

    private fun startRecording() {
        isRecording = true
        isPaused = false
        recordedActions.clear()
        startTime = System.currentTimeMillis()
        lastActionTime = startTime
        
        // Update UI
        val layout = controlView as LinearLayout
        layout.findViewWithTag<ImageButton>("stop")?.apply {
            isEnabled = true
            alpha = 1.0f
        }
        
        showTouchOverlay()
        bringControlToFront() // Ensure control stays on top
        Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show()
    }
    
    private fun bringControlToFront() {
        // Remove and re-add control view to bring it to front
        controlView?.let {
            windowManager.removeView(it)
            windowManager.addView(it, controlParams)
        }
    }

    private fun pauseRecording() {
        isPaused = true
        removeTouchOverlay() // Remove overlay so user can interact without recording
        Toast.makeText(this, "Recording Paused", Toast.LENGTH_SHORT).show()
    }

    private fun resumeRecording() {
        isPaused = false
        // Reset last action time so we don't record the pause duration as a huge delay
        lastActionTime = System.currentTimeMillis()
        showTouchOverlay()
        bringControlToFront() // Ensure control stays on top
        Toast.makeText(this, "Recording Resumed", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        isPaused = false
        removeTouchOverlay()
        saveMacro()
    }

    private fun showTouchOverlay() {
        if (touchView != null) return // Already showing

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        
        touchView = GestureView(this)
        windowManager.addView(touchView, params)
    }

    private fun removeTouchOverlay() {
        touchView?.let {
            windowManager.removeView(it)
            touchView = null
        }
    }

    // Custom View for drawing gestures
    private inner class GestureView(context: android.content.Context) : View(context) {
        private val path = android.graphics.Path()
        private val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.CYAN
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 10f
            alpha = 150
            isAntiAlias = true
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            canvas.drawPath(path, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            handleTouch(event)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    path.reset()
                    path.moveTo(event.x, event.y)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.x, event.y)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    path.reset()
                    invalidate()
                }
            }
            return true
        }
    }

    private fun handleTouch(event: MotionEvent) {
        if (!isRecording || isPaused) return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                touchDownTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val endX = event.rawX
                val endY = event.rawY
                val duration = System.currentTimeMillis() - touchDownTime
                val distanceX = abs(endX - startX)
                val distanceY = abs(endY - startY)
                
                val currentTime = System.currentTimeMillis()
                val delayBefore = currentTime - lastActionTime
                lastActionTime = currentTime

                if (distanceX > SWIPE_THRESHOLD || distanceY > SWIPE_THRESHOLD) {
                    // Swipe
                    val action = MacroAction(
                        macroId = 0, 
                        order = recordedActions.size,
                        type = "SWIPE",
                        x = startX.toInt(),
                        y = startY.toInt(),
                        endX = endX.toInt(),
                        endY = endY.toInt(),
                        duration = duration,
                        delayBefore = delayBefore
                    )
                    recordedActions.add(action)
                    replaySwipe(startX, startY, endX, endY, duration)
                } else {
                    // Click
                    val action = MacroAction(
                        macroId = 0, 
                        order = recordedActions.size,
                        type = "CLICK",
                        x = startX.toInt(),
                        y = startY.toInt(),
                        delayBefore = delayBefore
                    )
                    recordedActions.add(action)
                    replayClick(startX, startY)
                }
            }
        }
    }

    private fun replayClick(x: Float, y: Float) {
        val service = AutomationAccessibilityService.instance
        if (service != null) {
            serviceScope.launch {
                service.performTap(x.toInt(), y.toInt())
            }
        } else {
            Toast.makeText(this, "Accessibility Service not running!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun replaySwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val service = AutomationAccessibilityService.instance
        if (service != null) {
            serviceScope.launch {
                service.performSwipe(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt(), duration)
            }
        }
    }

    private fun saveMacro() {
        if (recordedActions.isEmpty()) {
            Toast.makeText(this, "No actions recorded", Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            val profileId = GlobalSettings.currentProfileId.value
            val macroName = "Macro ${System.currentTimeMillis()}" 
            
            val macro = Macro(
                profileId = profileId,
                name = macroName
            )
            
            val macroId = db.macroDao().insert(macro).toInt()
            
            val actionsToSave = recordedActions.map { it.copy(macroId = macroId) }
            db.macroActionDao().insertAll(actionsToSave)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Macro Saved: $macroName", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (controlView != null) windowManager.removeView(controlView)
        if (touchView != null) windowManager.removeView(touchView)
    }
}
