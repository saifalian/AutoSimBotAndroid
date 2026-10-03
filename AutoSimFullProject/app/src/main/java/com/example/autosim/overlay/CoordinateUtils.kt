package com.example.autosim.overlay

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

// The canonical space is the natural portrait orientation.
fun getCanonicalSize(context: Context): Point {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val realScreenSize = Point()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowMetrics = windowManager.currentWindowMetrics
        realScreenSize.x = windowMetrics.bounds.width()
        realScreenSize.y = windowMetrics.bounds.height()
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(realScreenSize)
    }

    val currentW = realScreenSize.x
    val currentH = realScreenSize.y
    return Point(min(currentW, currentH), max(currentW, currentH))
}

/**
 * Converts a point from the current screen's coordinate system to the canonical (portrait) system.
 */
fun toCanonical(context: Context, screenX: Float, screenY: Float): Pair<Float, Float> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val canonicalSize = getCanonicalSize(context)
    @Suppress("DEPRECATION")
    val rotation = windowManager.defaultDisplay.rotation
    return when (rotation) {
        Surface.ROTATION_90 -> Pair(screenY, canonicalSize.y - screenX) // Landscape to Portrait
        Surface.ROTATION_180 -> Pair(canonicalSize.x - screenX, canonicalSize.y - screenY) // Reverse Portrait to Portrait
        Surface.ROTATION_270 -> Pair(canonicalSize.x - screenY, screenX) // Reverse Landscape to Portrait
        else -> Pair(screenX, screenY) // Portrait to Portrait (no-op for ROTATION_0)
    }
}

/**
 * Converts a point from the canonical (portrait) system to the current screen's coordinate system for drawing.
 */
fun fromCanonical(context: Context, canonicalX: Int, canonicalY: Int): Pair<Float, Float> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val canonicalSize = getCanonicalSize(context)
    @Suppress("DEPRECATION")
    val rotation = windowManager.defaultDisplay.rotation
    return when (rotation) {
        Surface.ROTATION_90 -> Pair(canonicalSize.y - canonicalY.toFloat(), canonicalX.toFloat()) // Portrait to Landscape
        Surface.ROTATION_180 -> Pair(canonicalSize.x - canonicalX.toFloat(), canonicalSize.y - canonicalY.toFloat()) // Portrait to Reverse Portrait
        Surface.ROTATION_270 -> Pair(canonicalY.toFloat(), canonicalSize.x - canonicalX.toFloat()) // Portrait to Reverse Landscape
        else -> Pair(canonicalX.toFloat(), canonicalY.toFloat()) // Portrait to Portrait (no-op for ROTATION_0)
    }
}
