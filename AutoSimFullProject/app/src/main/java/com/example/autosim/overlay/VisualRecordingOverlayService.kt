package com.example.autosim.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
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
import com.example.autosim.db.ImageTemplate
import com.example.autosim.db.Sequence
import com.example.autosim.db.SequenceStep
import com.example.autosim.db.StepType
import com.example.autosim.db.Swipe
import com.example.autosim.engine.ScreenCaptureService
import com.example.autosim.utils.GlobalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class VisualRecordingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var controlView: View? = null
    private var touchView: View? = null
    private var isRecording = false
    private var isPaused = false
    private var waitForSuccess = true
    
    private var currentSequenceId: Int = 0
    private var currentStepNumber: Int = 1
    
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xAA000000.toInt())
            setPadding(8, 8, 8, 8)
        }

        // Start/Pause Button
        val btnStartPause = ImageButton(this).apply {
            setImageResource(R.drawable.ic_record)
            setBackgroundColor(0)
            setPadding(16, 16, 16, 16)
        }
        
        // Stop Button
        val btnStop = ImageButton(this).apply {
            setImageResource(R.drawable.ic_stop)
            setBackgroundColor(0)
            setPadding(16, 16, 16, 16)
            isEnabled = false
            alpha = 0.5f
        }
        
        // Close Button
        val btnClose = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundColor(0)
            setPadding(16, 16, 16, 16)
        }
        
        // Wait for Success Checkbox
        val cbWait = android.widget.CheckBox(this).apply {
            text = "Wait"
            setTextColor(android.graphics.Color.WHITE)
            isChecked = true
        }
        
        // Set up click listeners after all buttons are created
        btnStartPause.setOnClickListener { 
            if (!isRecording) {
                if (ScreenCaptureService.getLatestBitmap() == null) {
                    Toast.makeText(this@VisualRecordingOverlayService, "Screen Capture not ready. Please enable it in main app.", Toast.LENGTH_LONG).show()
                    // Try to request capture
                    val intent = Intent("com.example.autosim.REQUEST_SCREEN_CAPTURE")
                    sendBroadcast(intent)
                } else {
                    startRecording()
                    btnStartPause.setImageResource(R.drawable.ic_pause)
                    btnStop.isEnabled = true
                    btnStop.alpha = 1.0f
                }
            } else {
                if (isPaused) {
                    resumeRecording()
                    btnStartPause.setImageResource(R.drawable.ic_pause)
                } else {
                    pauseRecording()
                    btnStartPause.setImageResource(R.drawable.ic_play)
                }
            }
        }
        
        btnStop.setOnClickListener { 
            stopRecording() 
            btnStartPause.setImageResource(R.drawable.ic_record)
            btnStop.isEnabled = false
            btnStop.alpha = 0.5f
        }
        
        btnClose.setOnClickListener { stopSelf() }
        
        cbWait.setOnCheckedChangeListener { _, isChecked ->
            waitForSuccess = isChecked
        }

        layout.addView(btnStartPause)
        layout.addView(btnStop)
        layout.addView(btnClose)
        layout.addView(cbWait)

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
    }

    private fun startRecording() {
        serviceScope.launch {
            val profileId = GlobalSettings.currentProfileId.value
            val seqName = "Visual Macro ${System.currentTimeMillis()}"
            val newSeq = Sequence(
                profileId = profileId,
                name = seqName,
                isVisualMacro = true
            )
            currentSequenceId = db.sequenceDao().insert(newSeq).toInt()
            currentStepNumber = 1
            
            withContext(Dispatchers.Main) {
                isRecording = true
                isPaused = false
                
                // Increase capture rate for better accuracy
                ScreenCaptureService.getInstance()?.let { ScreenCaptureService.startCapture(100) }
                
                showTouchOverlay()
                bringControlToFront()
                Toast.makeText(this@VisualRecordingOverlayService, "Visual Recording Started", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun bringControlToFront() {
        controlView?.let {
            windowManager.removeView(it)
            windowManager.addView(it, controlParams)
        }
    }

    private fun pauseRecording() {
        isPaused = true
        removeTouchOverlay()
        Toast.makeText(this, "Recording Paused", Toast.LENGTH_SHORT).show()
    }

    private fun resumeRecording() {
        isPaused = false
        showTouchOverlay()
        bringControlToFront()
        Toast.makeText(this, "Recording Resumed", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        isPaused = false
        removeTouchOverlay()
        // Reset capture rate
        ScreenCaptureService.getInstance()?.let { ScreenCaptureService.startCapture(500) }
        Toast.makeText(this, "Visual Macro Saved", Toast.LENGTH_LONG).show()
    }

    private fun showTouchOverlay() {
        if (touchView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    private inner class GestureView(context: android.content.Context) : View(context) {
        private val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.FILL
            alpha = 200
        }
        private var clickX = -1f
        private var clickY = -1f
        
        fun showClick(x: Float, y: Float) {
            clickX = x
            clickY = y
            invalidate()
            postDelayed({
                clickX = -1f
                clickY = -1f
                invalidate()
            }, 3000) // Show for 3 seconds
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (clickX >= 0 && clickY >= 0) {
                canvas.drawCircle(clickX, clickY, 30f, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            handleTouch(event)
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
                
                // Capture Image Context
                val bitmap = ScreenCaptureService.getLatestBitmap()
                if (bitmap != null) {
                    if (distanceX > SWIPE_THRESHOLD || distanceY > SWIPE_THRESHOLD) {
                        // Swipe
                        processSwipe(startX, startY, endX, endY, duration, bitmap)
                        replaySwipe(startX, startY, endX, endY, duration)
                    } else {
                        // Click
                        (touchView as? GestureView)?.showClick(startX, startY)
                        processClick(startX, startY, bitmap)
                        replayClick(startX, startY)
                    }
                } else {
                    Toast.makeText(this, "Screen capture failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun processClick(x: Float, y: Float, bitmap: Bitmap) {
        serviceScope.launch {
            // Crop region around click (e.g., 100x100)
            val size = 100
            val left = (x - size / 2).toInt().coerceAtLeast(0)
            val top = (y - size / 2).toInt().coerceAtLeast(0)
            val width = size.coerceAtMost(bitmap.width - left)
            val height = size.coerceAtMost(bitmap.height - top)
            
            val cropped = ScreenCaptureService.cropBitmapForRegion(bitmap, left, top, width, height)
            if (cropped != null) {
                val filename = "vm_click_${System.currentTimeMillis()}.png"
                val file = File(filesDir, filename)
                FileOutputStream(file).use { out ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                val profileId = GlobalSettings.currentProfileId.value
                val template = ImageTemplate(
                    profileId = profileId,
                    name = "VM Click $currentStepNumber",
                    imagePath = file.absolutePath,
                    threshold = 0.8f
                )
                val templateId = db.imageTemplateDao().insert(template).toInt()
                
                val step = SequenceStep(
                    sequenceId = currentSequenceId,
                    stepNumber = currentStepNumber++,
                    type = StepType.IMAGE_DETECTION,
                    targetId = templateId,
                    successActionType = "CLICK_MATCH", // Special type for clicking match center
                    retryOnFailure = waitForSuccess
                )
                db.sequenceStepDao().insert(step)
            }
        }
    }

    private fun processSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long, bitmap: Bitmap) {
        serviceScope.launch {
            // Crop region around start point
            val size = 100
            val left = (startX - size / 2).toInt().coerceAtLeast(0)
            val top = (startY - size / 2).toInt().coerceAtLeast(0)
            val width = size.coerceAtMost(bitmap.width - left)
            val height = size.coerceAtMost(bitmap.height - top)
            
            val cropped = ScreenCaptureService.cropBitmapForRegion(bitmap, left, top, width, height)
            if (cropped != null) {
                val filename = "vm_swipe_${System.currentTimeMillis()}.png"
                val file = File(filesDir, filename)
                FileOutputStream(file).use { out ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                val profileId = GlobalSettings.currentProfileId.value
                val template = ImageTemplate(
                    profileId = profileId,
                    name = "VM Swipe $currentStepNumber",
                    imagePath = file.absolutePath,
                    threshold = 0.8f
                )
                val templateId = db.imageTemplateDao().insert(template).toInt()
                
                // Calculate relative swipe vector
                // We want to swipe from Center of Match to (Center + Vector)
                // Vector = End - Start
                val vecX = (endX - startX).toInt()
                val vecY = (endY - startY).toInt()
                
                // Store vector in a Swipe entity?
                // Or just use Swipe entity with start=0,0 end=vecX,vecY
                val swipe = Swipe(
                    profileId = profileId,
                    name = "VM Swipe Vector $currentStepNumber",
                    startX = 0, startY = 0,
                    endX = vecX, endY = vecY,
                    duration = duration
                )
                val swipeId = db.swipeDao().insert(swipe).toInt()
                
                val step = SequenceStep(
                    sequenceId = currentSequenceId,
                    stepNumber = currentStepNumber++,
                    type = StepType.IMAGE_DETECTION,
                    targetId = templateId,
                    successActionType = "SWIPE_MATCH", // Special type for swiping relative to match
                    successActionId = swipeId,
                    retryOnFailure = waitForSuccess
                )
                db.sequenceStepDao().insert(step)
            }
        }
    }

    private fun replayClick(x: Float, y: Float) {
        val service = AutomationAccessibilityService.instance
        if (service != null) {
            serviceScope.launch {
                service.performTap(x.toInt(), y.toInt())
            }
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

    override fun onDestroy() {
        super.onDestroy()
        if (controlView != null) windowManager.removeView(controlView)
        if (touchView != null) windowManager.removeView(touchView)
    }
}
