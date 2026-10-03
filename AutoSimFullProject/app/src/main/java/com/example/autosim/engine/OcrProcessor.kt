package com.example.autosim.engine

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeBitmap(bitmap: Bitmap): String {
        return try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(input).await()
            result.text
        } catch (e: Exception) {
            Log.e("OcrProcessor", "ocr error", e)
            ""
        }
    }
}
