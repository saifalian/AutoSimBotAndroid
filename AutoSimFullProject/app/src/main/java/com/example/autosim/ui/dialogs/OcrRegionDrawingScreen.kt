package com.example.autosim.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RegionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

@Composable
fun OcrRegionDrawingScreen(
    onSave: (String, Int, Int, Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    val config = LocalConfiguration.current
    val screenWidth = with(LocalDensity.current) { config.screenWidthDp.dp.toPx() }
    val screenHeight = with(LocalDensity.current) { config.screenHeightDp.dp.toPx() }
    
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var regionName by remember { mutableStateOf("Region ${System.currentTimeMillis() % 10000}") }
    val magneticThreshold = 20f // pixels
    
    val currentRect: RegionRect? = remember(startPoint, currentPoint) {
        if (startPoint != null && currentPoint != null) {
            val start = startPoint!!
            val current = currentPoint!!
            
            // Apply magnetic alignment
            var left = min(start.x, current.x)
            var top = min(start.y, current.y)
            var right = max(start.x, current.x)
            var bottom = max(start.y, current.y)
            
            // Magnetic alignment to edges
            if (abs(left) < magneticThreshold) left = 0f
            if (abs(top) < magneticThreshold) top = 0f
            if (abs(right - screenWidth) < magneticThreshold) right = screenWidth
            if (abs(bottom - screenHeight) < magneticThreshold) bottom = screenHeight
            
            // Magnetic alignment to other edges (snap to grid)
            val gridSize = 50f
            left = (left / gridSize).toInt() * gridSize
            top = (top / gridSize).toInt() * gridSize
            right = ((right / gridSize).toInt() + 1) * gridSize
            bottom = ((bottom / gridSize).toInt() + 1) * gridSize
            
            RegionRect(left, top, right, bottom)
        } else null
    }
    
    Scaffold(
        topBar = {
            Text("Draw OCR Region", modifier = Modifier.padding(16.dp))
        },
        bottomBar = {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        currentRect?.let { rect ->
                            onSave(
                                regionName,
                                rect.left.toInt(),
                                rect.top.toInt(),
                                rect.width.toInt(),
                                rect.height.toInt()
                            )
                        }
                    },
                    enabled = currentRect != null
                ) {
                    Text("Save")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startPoint = offset
                            currentPoint = offset
                        },
                        onDrag = { change, dragAmount ->
                            currentPoint = change.position
                        },
                        onDragEnd = {
                            // Keep the rectangle
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                currentRect?.let { rect ->
                    drawRect(
                        color = Color.Red.copy(alpha = 0.3f),
                        topLeft = Offset(rect.left, rect.top),
                        size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(rect.left, rect.top),
                        size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

