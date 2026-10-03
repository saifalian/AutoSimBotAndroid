package com.example.autosim.utils

import android.content.res.Resources
import kotlin.math.roundToInt

/**
 * Density scaling utility for Xiaomi 14
 * Resolution: 1200 × 2670
 * Density: 460 PPI
 */
object DensityUtils {
    // Base density for Xiaomi 14 (460 PPI = 2.875x mdpi)
    private const val BASE_DENSITY = 460f
    private const val BASE_DENSITY_DPI = 160f // mdpi baseline
    
    /**
     * Scale pixel value based on device density
     * @param px Original pixel value
     * @param resources Resources to get current density
     * @return Scaled pixel value
     */
    fun s(px: Int, resources: Resources): Int {
        val densityScale = resources.displayMetrics.densityDpi / BASE_DENSITY_DPI
        return (px * densityScale).roundToInt()
    }
    
    /**
     * Scale pixel value using fixed density scale
     * @param px Original pixel value
     * @param densityScale Density scale factor
     * @return Scaled pixel value
     */
    fun s(px: Int, densityScale: Float = BASE_DENSITY / BASE_DENSITY_DPI): Int {
        return (px * densityScale).roundToInt()
    }
    
    /**
     * Get density scale for current device
     */
    fun getDensityScale(resources: Resources): Float {
        return resources.displayMetrics.densityDpi / BASE_DENSITY_DPI
    }
}

