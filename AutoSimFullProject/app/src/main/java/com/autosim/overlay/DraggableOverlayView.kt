package com.autosim.overlay

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout

class DraggableOverlayView(context: Context) : FrameLayout(context) {
    // ... existing code ...

    private var startRawX = 0f
    private var startRawY = 0f
    private var startParamX = 0
    private var startParamY = 0

    // Use WindowMetrics + WindowInsets to get safe bounds, portrait or landscape
    private fun getSafeDisplayBounds(): Rect {
        val wm = context.getSystemService(WindowManager::class.java)
        val metrics = wm.currentWindowMetrics
        val bounds = Rect(metrics.bounds)
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        bounds.left += insets.left
        bounds.top += insets.top
        bounds.right -= insets.right
        bounds.bottom -= insets.bottom
        return bounds
    }

    // Attach this TouchListener to your arrow/click-spot view
    private val dragTouchListener = View.OnTouchListener { _, event ->
        val lp = layoutParams as WindowManager.LayoutParams
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                startParamX = lp.x
                startParamY = lp.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startRawX).toInt()
                val dy = (event.rawY - startRawY).toInt()

                val newX = startParamX + dx
                val newY = startParamY + dy

                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val safe = getSafeDisplayBounds()
                val maxX = safe.width() - width
                val maxY = safe.height() - height

                lp.x = newX.coerceIn(0, maxX)
                lp.y = newY.coerceIn(0, maxY)
                wm.updateViewLayout(this, lp)
                true
            }
            else -> false
        }
    }

    // Call this when creating the arrow view
    fun enableDraggingOn(view: View) {
        view.setOnTouchListener(dragTouchListener)
        // Respond to rotation/layout changes by revalidating bounds
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lp = layoutParams as WindowManager.LayoutParams
            val safe = getSafeDisplayBounds()
            val maxX = safe.width() - width
            val maxY = safe.height() - height
            lp.x = lp.x.coerceIn(0, maxX)
            lp.y = lp.y.coerceIn(0, maxY)
            wm.updateViewLayout(this, lp)
        }
    }

    // ... existing code ...
}