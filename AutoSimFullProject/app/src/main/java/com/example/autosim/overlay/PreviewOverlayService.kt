package com.example.autosim.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import com.example.autosim.ACTION_STOP_EDITING
import com.example.autosim.R
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.ClickSpot
import com.example.autosim.db.OcrRegion
import com.example.autosim.db.Swipe
import com.example.autosim.db.ImageTemplate
import com.example.autosim.engine.ScreenCaptureService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import android.widget.Toast

class PreviewOverlayService : Service() {

    companion object {
        var showClickSpots = false
        var showOcrRegions = false
        var showSwipes = false
        var showImageTemplates = false
        var showTextDetection = false
        var isEditing = false
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var controlsContainer: LinearLayout
    private lateinit var overlayParams: WindowManager.LayoutParams
    private lateinit var controlsParams: WindowManager.LayoutParams

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clickSpots: List<ClickSpot> = emptyList()
    private var ocrRegions: List<OcrRegion> = emptyList()
    private var swipes: List<Swipe> = emptyList()
    private var imageTemplates: List<ImageTemplate> = emptyList()
    
    // Selection and Interaction State
    private var selectedSpot: ClickSpot? = null
    private var selectedRegion: OcrRegion? = null
    private var selectedSwipe: Swipe? = null
    private var selectedTemplate: ImageTemplate? = null

    private var isMoveMode = false
    private var isDrawingRegion = false
    private var isDrawingSwipe = false
    private var isCapturingImage = false
    
    // Dragging State
    private var initialDragX = 0f
    private var initialDragY = 0f
    private var currentDrawingRegion: Rect? = null
    private var drawingStartPoint: Point? = null
    private var swipeStartPoint: Point? = null
    private var swipeEndPoint: Point? = null
    
    // Resize State
    private var resizeMode: ResizeMode = ResizeMode.NONE
    
    // UI Elements that need to be accessed outside setupOverlay
    private lateinit var addRegionFab: FloatingActionButton
    private lateinit var addSwipeFab: FloatingActionButton
    private lateinit var addImageFab: FloatingActionButton
    private lateinit var recordFab: FloatingActionButton
    private lateinit var moveFab: FloatingActionButton
    private lateinit var mainFab: FloatingActionButton
    private lateinit var actionButtons: List<FloatingActionButton>
    
    private enum class ResizeMode {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    @Suppress("DEPRECATION")
    private val serviceDisplay: Display?
        get() = windowManager?.defaultDisplay

    private val realScreenSize = Point()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        updateScreenSize()

        createNotificationChannel()
        startForeground(1338, createNotification())
        setupOverlay()
        observeData()
    }

    private fun updateScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            realScreenSize.x = windowMetrics?.bounds?.width() ?: 0
            realScreenSize.y = windowMetrics?.bounds?.height() ?: 0
        } else {
            @Suppress("DEPRECATION")
            serviceDisplay?.getRealSize(realScreenSize)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "preview_overlay",
                "Preview Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "preview_overlay")
            .setContentTitle("BOT Preview")
            .setContentText("Showing click spots and regions")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_AutoSim)

        // Drawing view
        overlayView = object : View(this) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                val oldIsLandscape = oldw > oldh
                val newIsLandscape = w > h
                
                updateScreenSize()
                
                // If orientation changed and menu is open, update layout
                if (oldw > 0 && oldh > 0 && oldIsLandscape != newIsLandscape) {
                    val areActionsVisible = actionButtons.any { it.isVisible }
                    if (areActionsVisible) {
                        updateMenuLayout()
                    }
                }
                
                invalidate()
            }
            
            private val arrowPaint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            }
            private val selectedArrowPaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            }
            private val regionPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }
            private val selectedRegionPaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            }
            private val drawingRegionPaint = Paint().apply {
                color = Color.CYAN
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                isAntiAlias = true
            }
            private val swipePaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 8f
                isAntiAlias = true
            }
            private val selectedSwipePaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 8f
                isAntiAlias = true
            }
            private val imageTemplatePaint = Paint().apply {
                color = Color.MAGENTA
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }
            private val selectedImageTemplatePaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 5f
                isAntiAlias = true
            }
            private val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 30f
                isAntiAlias = true
                setShadowLayer(2f, 0f, 0f, Color.BLACK)
            }
            private val handlePaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                
                // Calculate offset
                val location = IntArray(2)
                getLocationOnScreen(location)
                val offsetX = location[0].toFloat()
                val offsetY = location[1].toFloat()

                if (showClickSpots || isEditing) {
                    clickSpots.forEach { spot ->
                        val (canonicalX, canonicalY) = fromCanonical(context, spot.x, spot.y)
                        // Adjust for overlay offset
                        val drawX = canonicalX - offsetX
                        val drawY = canonicalY - offsetY
                        
                        val paint = if (spot == selectedSpot) selectedArrowPaint else arrowPaint
                        drawArrow(canvas, paint, drawX, drawY)
                        canvas.drawText(spot.name, drawX + 30f, drawY + 50f, textPaint)
                    }
                }
                
                if (showOcrRegions || isEditing) {
                    ocrRegions.forEach { region ->
                        val paint = if (region == selectedRegion) selectedRegionPaint else regionPaint
                        
                        val rLeft = region.left - offsetX
                        val rTop = region.top - offsetY
                        val rRight = rLeft + region.width
                        val rBottom = rTop + region.height
                        
                        canvas.drawRect(rLeft, rTop, rRight, rBottom, paint)
                        canvas.drawText(region.name, rLeft, rTop - 10f, textPaint)
                        
                        if (region == selectedRegion && isMoveMode) {
                            // Draw resize handles
                            val handleSize = 15f
                            canvas.drawCircle(rLeft, rTop, handleSize, handlePaint) // TL
                            canvas.drawCircle(rRight, rTop, handleSize, handlePaint) // TR
                            canvas.drawCircle(rLeft, rBottom, handleSize, handlePaint) // BL
                            canvas.drawCircle(rRight, rBottom, handleSize, handlePaint) // BR
                        }
                    }
                }

                // Draw Swipes
                if (showSwipes || isEditing) {
                    swipes.forEach { swipe ->
                        val (startX, startY) = fromCanonical(context, swipe.startX, swipe.startY)
                        val (endX, endY) = fromCanonical(context, swipe.endX, swipe.endY)
                        
                        val drawStartX = startX - offsetX
                        val drawStartY = startY - offsetY
                        val drawEndX = endX - offsetX
                        val drawEndY = endY - offsetY
                        
                        val paint = if (swipe == selectedSwipe) selectedSwipePaint else swipePaint
                        drawArrowLine(canvas, paint, drawStartX, drawStartY, drawEndX, drawEndY)
                        canvas.drawText(swipe.name, drawStartX, drawStartY - 10f, textPaint)
                    }
                }

                // Draw Image Templates
                if (showImageTemplates || isEditing) {
                    imageTemplates.forEach { template ->
                        // If it has a region, we could draw it? 
                        // But ImageTemplate itself doesn't store coordinates unless we link it to a region or it's full screen.
                        // For now, we might not visualize them on overlay unless they are tied to a region?
                        // The user request implies "detection in specific region".
                        // If regionId is set, we can draw a box around that region with a different color?
                        if (template.regionId != null) {
                            val region = ocrRegions.find { it.id == template.regionId }
                            if (region != null) {
                                val rLeft = region.left - offsetX
                                val rTop = region.top - offsetY
                                val rRight = rLeft + region.width
                                val rBottom = rTop + region.height
                                val paint = if (template == selectedTemplate) selectedImageTemplatePaint else imageTemplatePaint
                                canvas.drawRect(rLeft, rTop, rRight, rBottom, paint)
                                canvas.drawText("IMG: ${template.name}", rLeft, rTop + 20f, textPaint)
                            }
                        }
                    }
                }
                
                // Text Detection (Just ensures overlay is drawn if active, maybe draw a global indicator?)
                if (showTextDetection) {
                    // For now, maybe just a small indicator or nothing specific if it's global
                }
                
                currentDrawingRegion?.let { rect ->
                    canvas.drawRect(rect, drawingRegionPaint)
                }
                
                if (isDrawingSwipe && swipeStartPoint != null && swipeEndPoint != null) {
                    drawArrowLine(canvas, swipePaint, swipeStartPoint!!.x.toFloat(), swipeStartPoint!!.y.toFloat(), swipeEndPoint!!.x.toFloat(), swipeEndPoint!!.y.toFloat())
                }
            }

            private fun drawArrowLine(canvas: Canvas, paint: Paint, startX: Float, startY: Float, endX: Float, endY: Float) {
                canvas.drawLine(startX, startY, endX, endY, paint)
                // Draw arrow head at end
                val angle = kotlin.math.atan2(endY - startY, endX - startX)
                val arrowHeadLength = 40f
                val arrowHeadAngle = Math.toRadians(30.0)
                
                val x1 = endX - arrowHeadLength * kotlin.math.cos(angle - arrowHeadAngle)
                val y1 = endY - arrowHeadLength * kotlin.math.sin(angle - arrowHeadAngle)
                val x2 = endX - arrowHeadLength * kotlin.math.cos(angle + arrowHeadAngle)
                val y2 = endY - arrowHeadLength * kotlin.math.sin(angle + arrowHeadAngle)
                
                canvas.drawLine(endX, endY, x1.toFloat(), y1.toFloat(), paint)
                canvas.drawLine(endX, endY, x2.toFloat(), y2.toFloat(), paint)
            }

            private fun drawArrow(canvas: Canvas, paint: Paint, x: Float, y: Float) {
                val arrowBodyLength = 40f
                val headWidth = 30f
                val headHeight = 30f

                val path = Path().apply {
                    val headBaseY = y + headHeight
                    moveTo(x - headWidth / 2, headBaseY)
                    lineTo(x, y)
                    lineTo(x + headWidth / 2, headBaseY)
                    moveTo(x, headBaseY)
                    lineTo(x, headBaseY + arrowBodyLength)
                }
                canvas.drawPath(path, paint)
            }
        }

        // Controls
        mainFab = FloatingActionButton(contextThemeWrapper).apply { 
            setImageResource(R.drawable.ic_edit)
        }
        val addFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_add) }
        addRegionFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_ocr_region) }
        addSwipeFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_swipe) } // Need icon
        addImageFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_image) } // Need icon
        recordFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_record) }
        val deleteFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_delete) }
        moveFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_drag_handle) }
        val closeFab = FloatingActionButton(contextThemeWrapper).apply { setImageResource(R.drawable.ic_close) }

        actionButtons = listOf(addFab, addRegionFab, addSwipeFab, addImageFab, recordFab, deleteFab, moveFab, closeFab)
        actionButtons.forEach { it.isVisible = false }

        mainFab.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = controlsParams.x
                        initialY = controlsParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY

                        // Only start dragging if moved more than 10 pixels
                        if (!isDragging && (abs(deltaX) > 10f || abs(deltaY) > 10f)) {
                            // Only allow dragging when in move mode OR when action buttons are hidden (collapsed)
                            val areActionsVisible = actionButtons.any { it.isVisible }
                            if (isMoveMode || !areActionsVisible) {
                                isDragging = true
                            }
                        }

                        if (isDragging) {
                            controlsParams.x = (initialX - deltaX).toInt()
                            controlsParams.y = (initialY - deltaY).toInt()
                            windowManager?.updateViewLayout(controlsContainer, controlsParams)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            isDragging = false
                            return true
                        } else {
                            // It was a click
                            val areActionsVisible = actionButtons.any { it.isVisible }
                            if (!areActionsVisible) {
                                // About to expand, update layout first
                                updateMenuLayout()
                            }
                            actionButtons.forEach { button -> button.isVisible = !areActionsVisible }
                            return true
                        }
                    }
                }
                return false
            }
        })

        addFab.setOnClickListener {
            serviceScope.launch {
                val currentProfileId = com.example.autosim.utils.GlobalSettings.currentProfileId.value
                val spots = AppDatabase.getDatabase(this@PreviewOverlayService).clickSpotDao().getAll(currentProfileId).first()
                val nextName = ((spots.mapNotNull { it.name.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()
                
                updateScreenSize()
                val centerX = realScreenSize.x / 2f
                val centerY = realScreenSize.y / 2f
                val (canonicalX, canonicalY) = toCanonical(this@PreviewOverlayService, centerX, centerY)

                val newSpot = ClickSpot(
                    profileId = currentProfileId,
                    name = nextName, 
                    x = canonicalX.toInt(), 
                    y = canonicalY.toInt()
                )
                AppDatabase.getDatabase(this@PreviewOverlayService).clickSpotDao().insert(newSpot)
            }
        }
        
        addRegionFab.setOnClickListener {
            if (isDrawingRegion && !isCapturingImage) {
                toggleMode(Mode.NONE) // Exit region mode
            } else {
                toggleMode(Mode.REGION) // Enter region mode
            }
        }
        
        addSwipeFab.setOnClickListener {
            if (isDrawingSwipe) {
                toggleMode(Mode.NONE) // Exit swipe mode
            } else {
                toggleMode(Mode.SWIPE) // Enter swipe mode
            }
        }
        
        addImageFab.setOnClickListener {
            if (isCapturingImage) {
                toggleMode(Mode.NONE) // Exit image capture mode
            } else {
                // Check if screen capture is available
                if (ScreenCaptureService.getLatestBitmap() == null) {
                    // Request screen capture permission
                    Toast.makeText(this, "Please grant screen capture permission first", Toast.LENGTH_LONG).show()
                    // Send broadcast to request screen capture
                    val intent = Intent("com.example.autosim.REQUEST_SCREEN_CAPTURE")
                    sendBroadcast(intent)
                } else {
                    toggleMode(Mode.IMAGE) // Enter image capture mode
                }
            }
        }
        
        recordFab.setOnClickListener {
            // Start the Recording Service
            val intent = Intent(this, RecordingOverlayService::class.java)
            startService(intent)
            
            // Collapse menu
            actionButtons.forEach { it.isVisible = false }
        }
        
        deleteFab.setOnClickListener {
            serviceScope.launch {
                val db = AppDatabase.getDatabase(this@PreviewOverlayService)
                when {
                    selectedSpot != null -> {
                        db.clickSpotDao().deleteRelatedSequenceSteps(selectedSpot!!.id)
                        db.clickSpotDao().delete(selectedSpot!!)
                        selectedSpot = null
                        overlayView?.invalidate()
                    }
                    selectedRegion != null -> {
                        db.ocrRegionDao().deleteRelatedSequenceSteps(selectedRegion!!.id)
                        db.ocrRegionDao().delete(selectedRegion!!)
                        selectedRegion = null
                        overlayView?.invalidate()
                    }
                    selectedSwipe != null -> {
                        db.swipeDao().deleteRelatedSequenceSteps(selectedSwipe!!.id)
                        db.swipeDao().delete(selectedSwipe!!)
                        selectedSwipe = null
                        overlayView?.invalidate()
                    }
                    selectedTemplate != null -> {
                        db.imageTemplateDao().deleteRelatedSequenceSteps(selectedTemplate!!.id)
                        db.imageTemplateDao().delete(selectedTemplate!!)
                        selectedTemplate = null
                        overlayView?.invalidate()
                    }
                }
            }
        }
        
        moveFab.setOnClickListener {
            if (isMoveMode) {
                toggleMode(Mode.NONE) // Exit move mode
            } else {
                toggleMode(Mode.MOVE) // Enter move mode
            }
        }
        
        closeFab.setOnClickListener {
            sendBroadcast(Intent(ACTION_STOP_EDITING))
            isEditing = false
            stopSelf()
        }

        controlsContainer = LinearLayout(contextThemeWrapper).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(mainFab)
            actionButtons.forEach { addView(it) }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            overlayType, 
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        windowManager?.addView(overlayView, overlayParams)

        controlsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.BOTTOM or Gravity.END
            x = 16
            y = 100 
        }

        moveFab.setOnClickListener {
            if (isMoveMode) {
                toggleMode(Mode.NONE) // Exit move mode
            } else {
                toggleMode(Mode.MOVE) // Enter move mode
            }
        }


        
        windowManager?.addView(controlsContainer, controlsParams)

        overlayView?.setOnTouchListener { v, event ->
            // Calculate offset
            val location = IntArray(2)
            v.getLocationOnScreen(location)
            val offsetX = location[0].toFloat()
            val offsetY = location[1].toFloat()
            
            // Raw coordinates on screen
            val rawX = event.x + offsetX
            val rawY = event.y + offsetY
            
            // Canonical coordinates for ClickSpots
            val (canonicalX, canonicalY) = toCanonical(this, rawX, rawY)

            if (isDrawingRegion || isCapturingImage) {
                handleDrawing(event, offsetX, offsetY)
            } else if (isDrawingSwipe) {
                handleSwipeDrawing(event, offsetX, offsetY)
            } else if (isMoveMode) {
                handleMoveAndResize(event, rawX, rawY, canonicalX, canonicalY, offsetX, offsetY)
            } else {
                false
            }
        }
    }
    
    private fun handleDrawing(event: MotionEvent, offsetX: Float, offsetY: Float): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawingStartPoint = Point(x.toInt(), y.toInt())
                currentDrawingRegion = Rect(x.toInt(), y.toInt(), x.toInt(), y.toInt())
                overlayView?.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                drawingStartPoint?.let { start ->
                    currentDrawingRegion = Rect(
                        kotlin.math.min(start.x, x.toInt()),
                        kotlin.math.min(start.y, y.toInt()),
                        kotlin.math.max(start.x, x.toInt()),
                        kotlin.math.max(start.y, y.toInt())
                    )
                    overlayView?.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentDrawingRegion?.let { rect ->
                    // Save region
                    // Note: Saving in raw screen coordinates relative to the overlay's current position + offset
                    // Ideally we want global screen coordinates.
                    // rect is in overlay local coordinates.
                    // Global rect:
                    val globalLeft = rect.left + offsetX.toInt()
                    val globalTop = rect.top + offsetY.toInt()
                    
                    serviceScope.launch {
                        val currentProfileId = com.example.autosim.utils.GlobalSettings.currentProfileId.value
                        val regions = AppDatabase.getDatabase(this@PreviewOverlayService).ocrRegionDao().getAll(currentProfileId).first()
                        val nextName = "Region " + ((regions.size) + 1).toString()
                        
                        val newRegion = OcrRegion(
                            profileId = currentProfileId,
                            name = nextName,
                            left = globalLeft,
                            top = globalTop,
                            width = rect.width(),
                            height = rect.height()
                        )
                        
                        if (isCapturingImage) {
                            captureAndSaveImageTemplate(newRegion)
                        } else {
                            AppDatabase.getDatabase(this@PreviewOverlayService).ocrRegionDao().insert(newRegion)
                        }
                    }
                }
                currentDrawingRegion = null
                drawingStartPoint = null
                
                // Reset mode after drawing? Maybe keep it for multiple additions
                // For now, let's keep it active until user toggles off
                overlayView?.invalidate()
                return true
            }
        }
        return false
    }
    
    private fun handleSwipeDrawing(event: MotionEvent, offsetX: Float, offsetY: Float): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartPoint = Point(x.toInt(), y.toInt())
                swipeEndPoint = Point(x.toInt(), y.toInt())
                overlayView?.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                swipeEndPoint = Point(x.toInt(), y.toInt())
                overlayView?.invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (swipeStartPoint != null && swipeEndPoint != null) {
                    val startX = swipeStartPoint!!.x + offsetX
                    val startY = swipeStartPoint!!.y + offsetY
                    val endX = swipeEndPoint!!.x + offsetX
                    val endY = swipeEndPoint!!.y + offsetY
                    
                    val (cStartX, cStartY) = toCanonical(this, startX, startY)
                    val (cEndX, cEndY) = toCanonical(this, endX, endY)
                    
                    serviceScope.launch {
                        val currentProfileId = com.example.autosim.utils.GlobalSettings.currentProfileId.value
                        val swipes = AppDatabase.getDatabase(this@PreviewOverlayService).swipeDao().getAll(currentProfileId).first()
                        val nextName = "Swipe " + (swipes.size + 1)
                        
                        val newSwipe = Swipe(
                            profileId = currentProfileId,
                            name = nextName,
                            startX = cStartX.toInt(),
                            startY = cStartY.toInt(),
                            endX = cEndX.toInt(),
                            endY = cEndY.toInt()
                        )
                        AppDatabase.getDatabase(this@PreviewOverlayService).swipeDao().insert(newSwipe)
                    }
                }
                swipeStartPoint = null
                swipeEndPoint = null
                overlayView?.invalidate()
                return true
            }
        }
        return false
    }
    
    private fun captureAndSaveImageTemplate(region: OcrRegion) {
        val rawBitmap = ScreenCaptureService.getLatestBitmap()
        if (rawBitmap == null) {
            Toast.makeText(this, "Screen capture not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // ScreenCaptureService now returns the bitmap cropped to the actual screen size.
        // We can directly crop the region from it.
        
        android.util.Log.d("PreviewOverlay", "=== IMAGE CAPTURE DEBUG ===")
        android.util.Log.d("PreviewOverlay", "Screen bitmap: ${rawBitmap.width}x${rawBitmap.height}")
        android.util.Log.d("PreviewOverlay", "Region: left=${region.left}, top=${region.top}, width=${region.width}, height=${region.height}")
        
        // Validate region bounds
        if (region.left < 0 || region.top < 0 || 
            region.left + region.width > rawBitmap.width || 
            region.top + region.height > rawBitmap.height) {
            android.util.Log.e("PreviewOverlay", "Region out of bounds!")
            android.util.Log.e("PreviewOverlay", "Bitmap size: ${rawBitmap.width}x${rawBitmap.height}")
            android.util.Log.e("PreviewOverlay", "Region: (${region.left}, ${region.top}, ${region.width}, ${region.height})")
            Toast.makeText(this, "Region out of bounds - check coordinates", Toast.LENGTH_LONG).show()
            return
        }
        
        val cropped = try {
            ScreenCaptureService.cropBitmapForRegion(
                rawBitmap, 
                region.left, region.top, region.width, region.height
            )
        } catch (e: Exception) {
            android.util.Log.e("PreviewOverlay", "Failed to crop region: ${e.message}", e)
            null
        }
        
        if (cropped != null) {
            android.util.Log.d("PreviewOverlay", "Cropped template: ${cropped.width}x${cropped.height}")
            serviceScope.launch {
                val currentProfileId = com.example.autosim.utils.GlobalSettings.currentProfileId.value
                val templates = AppDatabase.getDatabase(this@PreviewOverlayService).imageTemplateDao().getAll(currentProfileId).first()
                val nextName = "Img " + (templates.size + 1)
                val filename = "template_${System.currentTimeMillis()}.png"
                val file = File(filesDir, filename)
                
                try {
                    FileOutputStream(file).use { out ->
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    
                    // Save the region
                    val regionId = AppDatabase.getDatabase(this@PreviewOverlayService).ocrRegionDao().insert(region)
                    
                    val newTemplate = ImageTemplate(
                        profileId = currentProfileId,
                        name = nextName,
                        imagePath = file.absolutePath,
                        regionId = regionId.toInt()
                    )
                    AppDatabase.getDatabase(this@PreviewOverlayService).imageTemplateDao().insert(newTemplate)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PreviewOverlayService, "Image Template Saved: ${cropped.width}x${cropped.height}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PreviewOverlayService, "Failed to save template: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Clean up
                cropped.recycle()
                // rawBitmap is managed by ScreenCaptureService, do not recycle it here unless we made a copy (which we didn't)
            }
        } else {
            android.util.Log.e("PreviewOverlay", "Failed to crop region - cropBitmapForRegion returned null")
            Toast.makeText(this, "Failed to crop region - check logs", Toast.LENGTH_SHORT).show()
        }
    }

    private enum class Mode {
        NONE, MOVE, REGION, SWIPE, IMAGE
    }

    private fun toggleMode(mode: Mode) {
        // Reset all flags
        isMoveMode = false
        isDrawingRegion = false
        isDrawingSwipe = false
        isCapturingImage = false
        
        // Reset UI
        moveFab.setImageResource(R.drawable.ic_drag_handle)
        addRegionFab.clearColorFilter()
        addSwipeFab.clearColorFilter()
        addImageFab.clearColorFilter()
        
        // Set new mode
        when (mode) {
            Mode.MOVE -> {
                isMoveMode = true
                moveFab.setImageResource(R.drawable.ic_done)
            }
            Mode.REGION -> {
                isDrawingRegion = true
                addRegionFab.setColorFilter(Color.GREEN)
            }
            Mode.SWIPE -> {
                isDrawingSwipe = true
                addSwipeFab.setColorFilter(Color.GREEN)
            }
            Mode.IMAGE -> {
                isCapturingImage = true
                isDrawingRegion = true // Reuse drawing logic
                addImageFab.setColorFilter(Color.GREEN)
            }
            Mode.NONE -> {}
        }
        
        // Deselect everything when mode changes
        if (mode != Mode.NONE && mode != Mode.MOVE) {
            selectedSpot = null
            selectedRegion = null
            selectedSwipe = null
            selectedTemplate = null
        }
        
        updateOverlayFlags()
        overlayView?.invalidate()
    }

    private fun handleMoveAndResize(
        event: MotionEvent, 
        rawX: Float, rawY: Float, 
        canonicalX: Float, canonicalY: Float,
        offsetX: Float, offsetY: Float
    ): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check for resize handles first if a region is selected
                if (selectedRegion != null) {
                    val region = selectedRegion!!
                    val rLeft = region.left - offsetX
                    val rTop = region.top - offsetY
                    val rRight = rLeft + region.width
                    val rBottom = rTop + region.height
                    val touchX = event.x
                    val touchY = event.y
                    val threshold = 40f
                    
                    if (abs(touchX - rLeft) < threshold && abs(touchY - rTop) < threshold) {
                        resizeMode = ResizeMode.TOP_LEFT
                        return true
                    } else if (abs(touchX - rRight) < threshold && abs(touchY - rTop) < threshold) {
                        resizeMode = ResizeMode.TOP_RIGHT
                        return true
                    } else if (abs(touchX - rLeft) < threshold && abs(touchY - rBottom) < threshold) {
                        resizeMode = ResizeMode.BOTTOM_LEFT
                        return true
                    } else if (abs(touchX - rRight) < threshold && abs(touchY - rBottom) < threshold) {
                        resizeMode = ResizeMode.BOTTOM_RIGHT
                        return true
                    }
                }
                
                // Check for regions (Move)
                val clickedRegion = findClosestRegion(rawX, rawY)
                if (clickedRegion != null) {
                    selectedRegion = clickedRegion
                    selectedSpot = null
                    resizeMode = ResizeMode.CENTER
                    initialDragX = clickedRegion.left - rawX
                    initialDragY = clickedRegion.top - rawY
                    overlayView?.invalidate()
                    return true
                }

                // Check for click spots (Move)
                val clickedSpot = findClosestClickSpot(canonicalX, canonicalY)
                if (clickedSpot != null) {
                    selectedSpot = clickedSpot
                    selectedRegion = null
                    selectedSwipe = null
                    resizeMode = ResizeMode.CENTER
                    initialDragX = clickedSpot.x - canonicalX
                    initialDragY = clickedSpot.y - canonicalY
                    overlayView?.invalidate()
                    return true
                }
                
                // Check for swipes (Move)
                val clickedSwipe = findClosestSwipe(canonicalX, canonicalY)
                if (clickedSwipe != null) {
                    selectedSwipe = clickedSwipe
                    selectedSpot = null
                    selectedRegion = null
                    resizeMode = ResizeMode.CENTER
                    // Store offset from start point for dragging
                    initialDragX = clickedSwipe.startX - canonicalX
                    initialDragY = clickedSwipe.startY - canonicalY
                    overlayView?.invalidate()
                    return true
                }
                
                // Deselect if clicked empty space
                selectedSpot = null
                selectedRegion = null
                selectedSwipe = null
                selectedTemplate = null
                overlayView?.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedRegion != null && resizeMode != ResizeMode.NONE) {
                    val region = selectedRegion!!
                    val touchX = event.x
                    val touchY = event.y
                    
                    var newLeft = region.left
                    var newTop = region.top
                    var newWidth = region.width
                    var newHeight = region.height
                    
                    when (resizeMode) {
                        ResizeMode.TOP_LEFT -> {
                            newLeft = (rawX + initialDragX).toInt()
                            newTop = (rawY + initialDragY).toInt()
                            newWidth = region.left + region.width - newLeft
                            newHeight = region.top + region.height - newTop
                        }
                        ResizeMode.TOP_RIGHT -> {
                            newTop = (rawY + initialDragY).toInt()
                            newWidth = (touchX - (region.left - offsetX)).toInt()
                            newHeight = region.top + region.height - newTop
                        }
                        ResizeMode.BOTTOM_LEFT -> {
                            newLeft = (rawX + initialDragX).toInt()
                            newWidth = region.left + region.width - newLeft
                            newHeight = (touchY - (region.top - offsetY)).toInt()
                        }
                        ResizeMode.BOTTOM_RIGHT -> {
                            newWidth = (touchX - (region.left - offsetX)).toInt()
                            newHeight = (touchY - (region.top - offsetY)).toInt()
                        }
                        ResizeMode.CENTER -> {
                            newLeft = (rawX + initialDragX).toInt()
                            newTop = (rawY + initialDragY).toInt()
                        }
                        else -> {}
                    }
                    
                    // Min size constraint
                    if (newWidth < 50) newWidth = 50
                    if (newHeight < 50) newHeight = 50
                    
                    val updatedRegion = region.copy(left = newLeft, top = newTop, width = newWidth, height = newHeight)
                    selectedRegion = updatedRegion // Update local reference for smooth dragging
                    
                    // Debounced DB update could be better, but direct update for now
                    serviceScope.launch { AppDatabase.getDatabase(this@PreviewOverlayService).ocrRegionDao().update(updatedRegion) }
                    
                    overlayView?.invalidate()
                    return true
                } else if (selectedSpot != null) {
                    val newX = canonicalX + initialDragX
                    val newY = canonicalY + initialDragY
                    val updatedSpot = selectedSpot!!.copy(x = newX.toInt(), y = newY.toInt())
                    selectedSpot = updatedSpot
                    serviceScope.launch { AppDatabase.getDatabase(this@PreviewOverlayService).clickSpotDao().update(updatedSpot) }
                    return true
                } else if (selectedSwipe != null) {
                    val swipe = selectedSwipe!!
                    val dx = swipe.endX - swipe.startX
                    val dy = swipe.endY - swipe.startY
                    
                    val newStartX = canonicalX + initialDragX
                    val newStartY = canonicalY + initialDragY
                    val newEndX = newStartX + dx
                    val newEndY = newStartY + dy
                    
                    val updatedSwipe = swipe.copy(
                        startX = newStartX.toInt(),
                        startY = newStartY.toInt(),
                        endX = newEndX.toInt(),
                        endY = newEndY.toInt()
                    )
                    selectedSwipe = updatedSwipe
                    serviceScope.launch { AppDatabase.getDatabase(this@PreviewOverlayService).swipeDao().update(updatedSwipe) }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                resizeMode = ResizeMode.NONE
                overlayView?.invalidate()
                return true
            }
        }
        return false
    }

    private fun updateOverlayFlags() {
        if (overlayView?.parent == null || windowManager == null) return

        val overlayFlag = when {
            isMoveMode || isDrawingRegion || isDrawingSwipe -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            else -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (overlayParams.flags != overlayFlag) {
            overlayParams.flags = overlayFlag
            windowManager?.updateViewLayout(overlayView, overlayParams)
        }
    }

    private fun findClosestClickSpot(x: Float, y: Float): ClickSpot? {
        return clickSpots.minByOrNull { spot ->
            sqrt((spot.x - x).pow(2) + (spot.y - y).pow(2))
        }?.takeIf { spot ->
            sqrt((spot.x - x).pow(2) + (spot.y - y).pow(2)) < 100
        }
    }
    
    private fun findClosestRegion(x: Float, y: Float): OcrRegion? {
        // Find region that contains the point
        return ocrRegions.findLast { region -> 
            x >= region.left && x <= region.left + region.width &&
            y >= region.top && y <= region.top + region.height
        }
    }

    private fun findClosestSwipe(x: Float, y: Float): Swipe? {
        // Check distance to start point, end point, or the line itself?
        // For simplicity, let's check distance to start point (the "handle")
        return swipes.minByOrNull { swipe ->
            sqrt((swipe.startX - x).pow(2) + (swipe.startY - y).pow(2))
        }?.takeIf { swipe ->
            sqrt((swipe.startX - x).pow(2) + (swipe.startY - y).pow(2)) < 100
        }
    }

    private var dataJob: kotlinx.coroutines.Job? = null

    private fun observeData() {
        val db = AppDatabase.getDatabase(this)
        
        serviceScope.launch {
            com.example.autosim.utils.GlobalSettings.currentProfileId.collect { profileId ->
                // Cancel previous data observation
                dataJob?.cancel()
                
                // Start new observation for the new profile
                dataJob = serviceScope.launch {
                    launch {
                        db.clickSpotDao().getAll(profileId).collect { spots ->
                            clickSpots = spots
                            overlayView?.invalidate()
                        }
                    }
                    launch {
                        db.ocrRegionDao().getAll(profileId).collect { regions ->
                            ocrRegions = regions
                            overlayView?.invalidate()
                        }
                    }
                    launch {
                        db.swipeDao().getAll(profileId).collect { s ->
                            swipes = s
                            overlayView?.invalidate()
                        }
                    }
                    launch {
                        db.imageTemplateDao().getAll(profileId).collect { t ->
                            imageTemplates = t
                            overlayView?.invalidate()
                        }
                    }
                }
            }
        }
    }

    private fun updateMenuLayout() {
        if (windowManager == null) return
        
        updateScreenSize()
        
        // Determine orientation
        val isLandscape = realScreenSize.x > realScreenSize.y
        
        // Determine position (controlsParams.x is distance from Right, y is distance from Bottom)
        // because gravity is BOTTOM | END
        val distFromRight = controlsParams.x
        val distFromBottom = controlsParams.y
        
        // Center of screen
        val centerX = realScreenSize.x / 2
        val centerY = realScreenSize.y / 2
        
        // Logic:
        // x small -> Right side. x large -> Left side.
        // y small -> Bottom side. y large -> Top side.
        
        val isOnRight = distFromRight < centerX
        val isOnBottom = distFromBottom < centerY
        
        controlsContainer.removeAllViews()
        
        // Calculate margin in pixels (8dp)
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        
        if (isLandscape) {
            controlsContainer.orientation = LinearLayout.HORIZONTAL
            controlsContainer.gravity = Gravity.CENTER_VERTICAL
            if (isOnRight) {
                // On Right side, expand Left.
                // Order: Buttons (Left) -> Main (Right)
                actionButtons.forEachIndexed { index, button ->
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) leftMargin = marginPx
                    }
                    controlsContainer.addView(button, params)
                }
                val mainParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = marginPx
                }
                controlsContainer.addView(mainFab, mainParams)
            } else {
                // On Left side, expand Right.
                // Order: Main (Left) -> Buttons (Right)
                controlsContainer.addView(mainFab)
                actionButtons.forEach { button ->
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = marginPx
                    }
                    controlsContainer.addView(button, params)
                }
            }
        } else {
            controlsContainer.orientation = LinearLayout.VERTICAL
            controlsContainer.gravity = Gravity.CENTER_HORIZONTAL
            if (isOnBottom) {
                // On Bottom side, expand Up.
                // Order: Buttons (Top) -> Main (Bottom)
                actionButtons.forEachIndexed { index, button ->
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = marginPx
                    }
                    controlsContainer.addView(button, params)
                }
                val mainParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = marginPx
                }
                controlsContainer.addView(mainFab, mainParams)
            } else {
                // On Top side, expand Down.
                // Order: Main (Top) -> Buttons (Bottom)
                controlsContainer.addView(mainFab)
                actionButtons.forEach { button ->
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = marginPx
                    }
                    controlsContainer.addView(button, params)
                }
            }
        }
        
        // Ensure params are set to WRAP_CONTENT to fit all buttons
        controlsParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        controlsParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        
        // Force window manager to update layout with new dimensions
        windowManager?.updateViewLayout(controlsContainer, controlsParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView?.isAttachedToWindow == true) windowManager?.removeView(overlayView)
        if (controlsContainer.isAttachedToWindow) windowManager?.removeView(controlsContainer)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
