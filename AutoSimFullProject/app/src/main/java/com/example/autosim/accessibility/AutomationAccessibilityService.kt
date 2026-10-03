package com.example.autosim.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AutomationAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set
    }
    
    private val TAG = "AutomationA11y"
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    
    override fun onInterrupt() {}
    
    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility connected")
        instance = this
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
    
    suspend fun performTap(x: Int, y: Int, durationMs: Long = 50): Boolean =
        suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "Attempting to perform tap at ($x, $y)")
                val tapDuration = if (durationMs > 0) durationMs else 50L
                val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                val stroke = GestureDescription.StrokeDescription(path, 0, tapDuration)
                val desc = GestureDescription.Builder().addStroke(stroke).build()

                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Gesture completed successfully")
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.e(TAG, "Gesture cancelled")
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                dispatchGesture(desc, callback, null)

            } catch (e: Exception) {
                Log.e(TAG, "Error performing tap", e)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

    suspend fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300): Boolean =
        suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "Attempting to perform swipe from ($startX, $startY) to ($endX, $endY)")
                val path = Path().apply {
                    moveTo(startX.toFloat(), startY.toFloat())
                    lineTo(endX.toFloat(), endY.toFloat())
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
                val desc = GestureDescription.Builder().addStroke(stroke).build()

                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Swipe completed successfully")
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.e(TAG, "Swipe cancelled")
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                dispatchGesture(desc, callback, null)

            } catch (e: Exception) {
                Log.e(TAG, "Error performing swipe", e)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
}
