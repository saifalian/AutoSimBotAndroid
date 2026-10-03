package com.example.autosim.ui.tabs

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.autosim.engine.ScreenCaptureService

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SwipeDrawingScreen(
    onSave: (Int, Int, Int, Int) -> Unit,
    onCancel: () -> Unit,
    useScreenCapture: Boolean = false
) {
    val context = LocalContext.current
    var startLocalOffset by remember { mutableStateOf<Offset?>(null) }
    var endLocalOffset by remember { mutableStateOf<Offset?>(null) }
    
    var startScreenOffset by remember { mutableStateOf<Offset?>(null) }
    var endScreenOffset by remember { mutableStateOf<Offset?>(null) }
    
    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // If using screen capture, try to get the bitmap
    LaunchedEffect(useScreenCapture) {
        if (useScreenCapture) {
            val service = ScreenCaptureService.getInstance()
            if (service != null) {
                kotlinx.coroutines.delay(500) 
                capturedBitmap = ScreenCaptureService.getLatestBitmap()
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
            Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = "Captured Screen",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter {
                when (it.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startLocalOffset = Offset(it.x, it.y)
                        endLocalOffset = Offset(it.x, it.y) // Initially end is same as start
                        isDragging = true
                        
                        if (useScreenCapture) {
                             startScreenOffset = Offset(it.x, it.y)
                             endScreenOffset = Offset(it.x, it.y)
                        } else {
                            val correctedY = it.rawY - statusBarHeight
                            startScreenOffset = Offset(it.rawX, correctedY)
                            endScreenOffset = Offset(it.rawX, correctedY)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            endLocalOffset = Offset(it.x, it.y)
                            
                            if (useScreenCapture) {
                                 endScreenOffset = Offset(it.x, it.y)
                            } else {
                                val correctedY = it.rawY - statusBarHeight
                                endScreenOffset = Offset(it.rawX, correctedY)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }
        ) {
            if (startLocalOffset != null && endLocalOffset != null) {
                // Draw line
                drawLine(
                    color = Color.Blue,
                    start = startLocalOffset!!,
                    end = endLocalOffset!!,
                    strokeWidth = 10f
                )
                
                // Draw start circle
                drawCircle(
                    color = Color.Green,
                    center = startLocalOffset!!,
                    radius = 20f
                )
                
                // Draw end circle (arrow head approximation)
                drawCircle(
                    color = Color.Red,
                    center = endLocalOffset!!,
                    radius = 20f
                )
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
                 Text("Waiting for screen capture...", color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                 Button(onClick = {
                     (context as? com.example.autosim.MainActivity)?.startScreenCaptureIntent()
                 }) {
                     Text("Start Screen Capture Service")
                 }
            }
            
            Button(
                onClick = {
                    if (startScreenOffset != null && endScreenOffset != null) {
                        onSave(
                            startScreenOffset!!.x.toInt(),
                            startScreenOffset!!.y.toInt(),
                            endScreenOffset!!.x.toInt(),
                            endScreenOffset!!.y.toInt()
                        )
                    }
                },
                enabled = startScreenOffset != null && endScreenOffset != null && !isDragging,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save This Swipe")
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
