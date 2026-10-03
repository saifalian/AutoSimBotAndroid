package com.example.autosim.ui.tabs

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClickSpotDrawingScreen(
    onSave: (Int, Int) -> Unit,
    onCancel: () -> Unit,
    useScreenCapture: Boolean = false
) {
    val context = LocalContext.current
    var tapLocalOffset by remember { mutableStateOf<Offset?>(null) }
    var tapScreenOffset by remember { mutableStateOf<Offset?>(null) }
    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    // If using screen capture, try to get the bitmap
    LaunchedEffect(useScreenCapture) {
        if (useScreenCapture) {
            val service = com.example.autosim.engine.ScreenCaptureService.getInstance()
            if (service != null) {
                // Wait a bit for a fresh capture if needed or just get latest
                kotlinx.coroutines.delay(500) 
                capturedBitmap = com.example.autosim.engine.ScreenCaptureService.getLatestBitmap()
            }
        }
    }

    val statusBarHeight = remember {
        val resources = context.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (useScreenCapture && capturedBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = "Captured Screen",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
            )
        }
    
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter {
                when (it.action) {
                    MotionEvent.ACTION_DOWN -> {
                        tapLocalOffset = Offset(it.x, it.y)
                        // If using screen capture, the touch coordinates on the image ARE the screen coordinates (mostly)
                        // Assuming the image fills the screen.
                        // However, we still need to account for status bar if the image includes it or not.
                        // ScreenCaptureService usually captures the whole screen including status bar.
                        // So if we are displaying it fullscreen, the touch X/Y on the view should map 1:1 to screen X/Y
                        // BUT, our activity has a status bar too.
                        // If the app is fullscreen, it matches.
                        // Let's assume 1:1 mapping for now but keep the status bar correction for the "transparent" mode.
                        
                        if (useScreenCapture) {
                             tapScreenOffset = Offset(it.x, it.y)
                        } else {
                            val correctedScreenY = it.rawY - statusBarHeight
                            tapScreenOffset = Offset(it.rawX, correctedScreenY)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        tapLocalOffset = Offset(it.x, it.y)
                        if (useScreenCapture) {
                             tapScreenOffset = Offset(it.x, it.y)
                        } else {
                            val correctedScreenY = it.rawY - statusBarHeight
                            tapScreenOffset = Offset(it.rawX, correctedScreenY)
                        }
                        true
                    }
                    else -> false
                }
            }
        ) {
            tapLocalOffset?.let { touchPointInCanvas ->
                // Draw marker
                val arrowHeadInCanvas = if (useScreenCapture) {
                     touchPointInCanvas
                } else {
                     Offset(touchPointInCanvas.x, touchPointInCanvas.y - statusBarHeight)
                }

                drawLine(
                    color = Color.Green,
                    start = touchPointInCanvas,
                    end = arrowHeadInCanvas,
                    strokeWidth = 5f
                )

                val path = Path().apply {
                    moveTo(touchPointInCanvas.x - 15f, touchPointInCanvas.y + 15f)
                    lineTo(touchPointInCanvas.x, touchPointInCanvas.y)
                    lineTo(touchPointInCanvas.x + 15f, touchPointInCanvas.y + 15f)
                }
                drawPath(path, color = Color.Green, style = Stroke(width = 5f))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (useScreenCapture && capturedBitmap == null) {
                 Text("Waiting for screen capture...", color = Color.Red, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                 Button(onClick = {
                     (context as? com.example.autosim.MainActivity)?.startScreenCaptureIntent()
                 }) {
                     Text("Start Screen Capture Service")
                 }
            }
            
            Button(
                onClick = {
                    tapScreenOffset?.let { onSave(it.x.toInt(), it.y.toInt()) }
                },
                enabled = tapScreenOffset != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save This Spot")
            }
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
