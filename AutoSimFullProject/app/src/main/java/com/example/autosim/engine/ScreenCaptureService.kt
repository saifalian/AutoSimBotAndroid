package com.example.autosim.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autosim.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
        
        private var instance: ScreenCaptureService? = null
        fun getInstance(): ScreenCaptureService? = instance
        
        private var mediaProjection: MediaProjection? = null
        private val _latestBitmap = MutableStateFlow<Bitmap?>(null)
        val latestBitmap: StateFlow<Bitmap?> = _latestBitmap
        
        private var captureInterval: Long = 500 // ms
        private var isCapturing = false
        
        fun setMediaProjection(projection: MediaProjection) {
            mediaProjection = projection
            instance?.startProjection()
            startCapture() // Automatically start capturing frames
        }
        
        fun stopService() {
            instance?.stopSelf()
            mediaProjection?.stop()
            mediaProjection = null
            instance = null
        }
        
        fun startCapture(intervalMs: Long = 500) {
            captureInterval = intervalMs
            isCapturing = true
        }
        
        fun stopCapture() {
            isCapturing = false
        }

        fun getLatestBitmap(): Bitmap? = _latestBitmap.value

        fun isServiceActive(): Boolean {
            return mediaProjection != null
        }

        fun cropBitmapForRegion(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap? {
            return try {
                if (left >= 0 && top >= 0 &&
                    left + width <= bitmap.width &&
                    top + height <= bitmap.height) {
                    Bitmap.createBitmap(bitmap, left, top, width, height)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cropping bitmap", e)
                null
            }
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var displayManager: DisplayManager? = null
    private var currentWidth = 0
    private var currentHeight = 0
    
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val metrics = resources.displayMetrics
            currentWidth = metrics.widthPixels
            currentHeight = metrics.heightPixels
            Log.d(TAG, "Display changed: ${currentWidth}x${currentHeight}")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, null)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupScreenCapture()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BOT Screen Capture")
            .setContentText("Capturing screen for OCR")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun setupScreenCapture() {
        val metrics = resources.displayMetrics
        // Use max dimension to create a square buffer that works in both orientations
        val maxDimension = kotlin.math.max(metrics.widthPixels, metrics.heightPixels)
        val density = metrics.densityDpi
        
        currentWidth = metrics.widthPixels
        currentHeight = metrics.heightPixels
        
        Log.d(TAG, "Setting up screen capture: ${maxDimension}x${maxDimension} (screen: ${currentWidth}x${currentHeight}) @ $density dpi")
        
        imageReader = ImageReader.newInstance(maxDimension, maxDimension, PixelFormat.RGBA_8888, 2)
        
        if (handlerThread == null) {
            handlerThread = HandlerThread("ScreenCapture").apply { start() }
            handler = Handler(handlerThread!!.looper)
        }
        
        // Try to start if projection exists (re-starting service case)
        startProjection()
        
        val captureRunnable = object : Runnable {
            override fun run() {
                if (isCapturing) {
                    try {
                        imageReader?.acquireLatestImage()?.let { image ->
                            serviceScope.launch {
                                captureImage(image)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error acquiring image", e)
                    }
                }
                handler?.postDelayed(this, captureInterval)
            }
        }
        handler?.postDelayed(captureRunnable, captureInterval)
    }

    private fun startProjection() {
        if (mediaProjection != null && virtualDisplay == null && imageReader != null) {
             // Register callback before creating VirtualDisplay (Android 14+ requirement)
             mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                 override fun onStop() {
                     super.onStop()
                     Log.d(TAG, "MediaProjection stopped")
                     stopCapture()
                     virtualDisplay?.release()
                     virtualDisplay = null
                 }
             }, handler)
             
             val metrics = resources.displayMetrics
             val maxDimension = kotlin.math.max(metrics.widthPixels, metrics.heightPixels)
             val density = metrics.densityDpi
             
             Log.d(TAG, "Creating VirtualDisplay: ${maxDimension}x${maxDimension} @ $density dpi")
             
             virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                maxDimension, maxDimension, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null, handler
            )
        }
    }
    
    private suspend fun captureImage(image: Image) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // The virtual display is square (maxDimension x maxDimension).
            // The content is centered within this square.
            // We need to crop the actual screen content.
            
            // Re-fetch current dimensions in case they changed
            // Note: We rely on displayListener to keep currentWidth/Height updated, 
            // but we can also check image dimensions vs known maxDimension if needed.
            // For now, let's assume the content is centered.
            
            val maxDim = image.width // Should be maxDimension
            
            // Calculate offsets to center crop
            // If screen is Portrait: width < height. Content is centered horizontally?
            // Actually, VirtualDisplay behavior with AUTO_MIRROR usually scales/centers.
            // But since we set the VD size to maxDimension x maxDimension, and the physical screen is smaller in one dimension,
            // the content will be letterboxed or pillarboxed.
            
            // However, we need to be careful. 
            // If we are in Portrait (W < H), and VD is H x H.
            // The content WxH is drawn into HxH.
            // Usually it's centered.
            
            val xOffset = (maxDim - currentWidth) / 2
            val yOffset = (maxDim - currentHeight) / 2
            
            // Ensure we don't crop out of bounds
            val safeX = if (xOffset < 0) 0 else xOffset
            val safeY = if (yOffset < 0) 0 else yOffset
            val safeW = if (safeX + currentWidth > bitmap.width) bitmap.width - safeX else currentWidth
            val safeH = if (safeY + currentHeight > bitmap.height) bitmap.height - safeY else currentHeight
            
            val cropped = Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
            bitmap.recycle()
            
            _latestBitmap.value = cropped
            image.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing image", e)
            try { image.close() } catch (e2: Exception) {}
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        displayManager?.unregisterDisplayListener(displayListener)
        stopCapture()
        virtualDisplay?.release()
        imageReader?.close()
        handlerThread?.quitSafely()
        mediaProjection?.stop()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
