package com.autosim.click

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.view.MotionEvent
import android.view.WindowManager

/**
 * A data class that robustly stores a captured tap.
 * It holds the raw (x, y) coordinates of the tap AND the screen dimensions at the moment of capture.
 * This allows for accurate scaling of the tap location when the screen orientation changes.
 */
data class CapturedTap(val tapPoint: Point, val captureScreenSize: Point)

/**
 * Gets the real screen dimensions in pixels, accounting for the current orientation.
 * This is the definitive method for measuring the screen for our purpose.
 *
 * @param context The current context.
 * @return A [Point] where x is the current screen width and y is the current screen height.
 */
@Suppress("DEPRECATION") // Required to use `getRealSize` on APIs < 30 for backward compatibility.
internal fun getRealScreenSize(context: Context): Point {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.currentWindowMetrics.bounds
        Point(bounds.width(), bounds.height())
    } else {
        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        point
    }
}

/**
 * ### Step 1: Record a Tap Correctly
 *
 * This function captures the touch point and the screen dimensions at the same time.
 * It must be called from a fullscreen overlay's touch listener.
 *
 * @param context The current context.
 * @param event The MotionEvent from `onTouchEvent`. We use `event.x` and `event.y` which are
 *              correctly mapped to the rotated coordinate system of a fullscreen view.
 * @return A [CapturedTap] object containing all necessary info for a perfect replay.
 */
@Suppress("unused") // This function is intended for use in other files (e.g., an overlay view).
fun recordTap(context: Context, event: MotionEvent): CapturedTap {
    // The coordinates from the MotionEvent in a fullscreen view.
    val tapPoint = Point(event.x.toInt(), event.y.toInt())
    // The screen dimensions at the exact moment of the tap.
    val captureScreenSize = getRealScreenSize(context)

    return CapturedTap(tapPoint, captureScreenSize)
}

/**
 * ### Step 2: Perform the Tap with Scaling
 *
 * This is the core of the solution. It takes a previously recorded tap and accurately
 * replays it on the screen, regardless of the current orientation.
 *
 * @param service The running [AccessibilityService] instance.
 * @param capturedTap The [CapturedTap] object you created in Step 1.
 */
@Suppress("unused") // This function is intended for use in other files (e.g., the AccessibilityService).
fun performTap(service: AccessibilityService, capturedTap: CapturedTap) {
    // Get the screen size right now, at the moment of replay.
    val currentScreenSize = getRealScreenSize(service)

    // If the screen size hasn't changed, we can click the original point directly.
    if (currentScreenSize == capturedTap.captureScreenSize) {
        val gesturePath = Path().apply {
            moveTo(capturedTap.tapPoint.x.toFloat(), capturedTap.tapPoint.y.toFloat())
        }
        dispatchGesture(service, gesturePath)
        return
    }

    // The screen size HAS changed (e.g., orientation swap). We must scale the point.
    val scaleX = currentScreenSize.x.toFloat() / capturedTap.captureScreenSize.x.toFloat()
    val scaleY = currentScreenSize.y.toFloat() / capturedTap.captureScreenSize.y.toFloat()

    // Apply the scale factors to the original tap point to find the new location.
    val scaledX = capturedTap.tapPoint.x * scaleX
    val scaledY = capturedTap.tapPoint.y * scaleY

    val scaledPath = Path().apply {
        moveTo(scaledX, scaledY)
    }
    dispatchGesture(service, scaledPath)
}

/**
 * Helper function to build and dispatch the gesture.
 */
private fun dispatchGesture(service: AccessibilityService, path: Path) {
    val gestureBuilder = GestureDescription.Builder()
    gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50)) // 50ms duration
    service.dispatchGesture(gestureBuilder.build(), null, null)
}


/**
 * =====================================================================================
 *                                  EXAMPLE USAGE
 * =====================================================================================
 *
 * This demonstrates how you would integrate the new scaling logic into your app.
 *
 * --- A. Touch listener for recording coordinates ---
 *
 * '''kotlin
 * // In your RecordingOverlayView.kt file
 * // Imports needed: import android.annotation.SuppressLint, android.view.MotionEvent, android.view.View
 * @SuppressLint("ClickableViewAccessibility")
 * class RecordingOverlayView(context: Context) : View(context) {
 *     var onTapRecorded: ((CapturedTap) -> Unit)? = null
 *
 *     override fun onTouchEvent(event: MotionEvent): Boolean {
 *         if (event.action == MotionEvent.ACTION_DOWN) {
 *             // Record the tap using the new, correct function.
 *             val capturedTap = recordTap(context, event)
 *             onTapRecorded?.invoke(capturedTap)
 *             return true // Event consumed
 *         }
 *         return super.onTouchEvent(event)
 *     }
 * }
 * '''
 *
 * --- B. AccessibilityService for performing the tap ---
 *
 * '''kotlin
 * // In your MyAutoClickerService.kt file
 * class MyAutoClickerService : AccessibilityService() {
 *
 *     private var latestTap: CapturedTap? = null
 *
 *     // Example of how to store the tap from your UI
 *     fun setTapToReplay(tap: CapturedTap) {
 *         this.latestTap = tap
 *     }
 *
 *     // When you need to perform the click (e.g., user presses a "play" button)
 *     fun executeClick() {
 *         latestTap?.let {
 *             performTap(this, it) // Call the new performTap function
 *         }
 *     }
 *
 *     override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
 *     override fun onInterrupt() {}
 * }
 * '''
 * =====================================================================================
 */
