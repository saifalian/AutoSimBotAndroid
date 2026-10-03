package com.example.autosim.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object ImageMatcher {
    private const val TAG = "ImageMatcher"

    /**
     * Finds the [template] bitmap within the [screen] bitmap.
     * @param screen The source image to search in.
     * @param template The template image to find.
     * @param threshold The matching threshold (0.0 to 1.0). 1.0 means exact match.
     * @return The top-left coordinate of the match, or null if not found.
     */
    suspend fun findTemplate(screen: Bitmap, template: Bitmap, threshold: Double = 0.9): Point? = withContext(Dispatchers.Default) {
        val sW = screen.width
        val sH = screen.height
        val tW = template.width
        val tH = template.height

        if (tW > sW || tH > sH) {
            Log.w(TAG, "Template is larger than screen: Template($tW x $tH) vs Screen($sW x $sH)")
            return@withContext null
        }

        val screenPixels = IntArray(sW * sH)
        screen.getPixels(screenPixels, 0, sW, 0, 0, sW, sH)

        val templatePixels = IntArray(tW * tH)
        template.getPixels(templatePixels, 0, tW, 0, 0, tW, tH)

        // Optimization: Adaptive stride based on threshold
        // If threshold is high (> 0.8), we need to be very precise, so stride = 1.
        // If threshold is lower, we can skip pixels to speed up.
        val stride = if (threshold > 0.8) 1 else maxOf(1, minOf(tW, tH) / 64)
        
        // Max allowed total error based on threshold
        // Max difference per pixel is 255 * 3 = 765
        val maxTotalDiff = (1.0 - threshold) * 765 * tW * tH

        for (y in 0..sH - tH step stride) {
            for (x in 0..sW - tW step stride) {
                if (matchAt(screenPixels, sW, x, y, templatePixels, tW, tH, maxTotalDiff)) {
                    return@withContext Point(x, y)
                }
            }
        }

        Log.d(TAG, "No match found. Threshold: $threshold")
        return@withContext null
    }

    private fun matchAt(
        screenPixels: IntArray, sW: Int, sx: Int, sy: Int,
        templatePixels: IntArray, tW: Int, tH: Int,
        maxTotalDiff: Double
    ): Boolean {
        var totalDiff = 0.0
        
        // Check Stride:
        // For very high accuracy, check every pixel.
        // For speed, we can skip some pixels in the inner loop too, but we must scale the error.
        // Let's use a small stride of 2 for general cases to keep it fast but accurate enough.
        // But if we are already doing a stride=1 search in outer loop (high threshold), 
        // we should probably check every pixel here too or be very careful.
        // Let's stick to stride 1 for inner loop to be safe, or 2 if performance is an issue.
        // Given the user's complaint, let's prioritize accuracy.
        val checkStride = 1 
        
        // If we were to use a stride, we'd need to scale maxTotalDiff.
        // Since checkStride is 1, scaledMaxDiff is just maxTotalDiff.
        val scaledMaxDiff = maxTotalDiff 

        for (ty in 0 until tH step checkStride) {
            for (tx in 0 until tW step checkStride) {
                val sp = screenPixels[(sy + ty) * sW + (sx + tx)]
                val tp = templatePixels[ty * tW + tx]
                
                totalDiff += pixelDiff(sp, tp)
                
                // Early exit
                if (totalDiff > scaledMaxDiff) return false
            }
        }
        return true
    }

    private fun pixelDiff(p1: Int, p2: Int): Int {
        val r1 = (p1 shr 16) and 0xFF
        val g1 = (p1 shr 8) and 0xFF
        val b1 = p1 and 0xFF
        
        val r2 = (p2 shr 16) and 0xFF
        val g2 = (p2 shr 8) and 0xFF
        val b2 = p2 and 0xFF
        
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
    }
}
