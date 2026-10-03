package com.example.autosim.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.Swipe
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.autosim.overlay.PreviewOverlayService
import kotlinx.coroutines.launch

@Composable
fun SwipesScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val swipes by db.swipeDao().getAll(currentProfileId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showDrawingScreen by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var tempSwipeCoords by remember { mutableStateOf<List<Int>?>(null) }


    if (showDrawingScreen) {
        SwipeDrawingScreen(
            onSave = { x1, y1, x2, y2 ->
                tempSwipeCoords = listOf(x1, y1, x2, y2)
                showDrawingScreen = false
                showNameDialog = true
            },
            onCancel = { showDrawingScreen = false },
            useScreenCapture = true
        )
    } else {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recorded Swipes",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manage swipe gestures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                PreviewOverlayService.showSwipes = !PreviewOverlayService.showSwipes
                                if (PreviewOverlayService.showSwipes) {
                                    ContextCompat.startForegroundService(context, Intent(context, PreviewOverlayService::class.java))
                                } else {
                                    context.stopService(Intent(context, PreviewOverlayService::class.java))
                                }
                            }
                        ) {
                            Text(if (PreviewOverlayService.showSwipes) "Stop Overlay" else "Start Overlay")
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (com.example.autosim.utils.GlobalSettings.detectActionsWithScreenCapture) {
                            showDrawingScreen = true
                        } else {
                            android.widget.Toast.makeText(context, "To record swipes without screen capture, please use the Overlay.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Swipe")
                }
            }
        ) { paddingValues ->
            if (swipes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwipeRight,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No swipes recorded yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use the overlay to record swipes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(swipes) { swipe ->
                        SwipeItem(
                            swipe = swipe,
                            onDelete = {
                                scope.launch {
                                    db.swipeDao().delete(swipe)
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                }
            }
        }
    }
    
    if (showNameDialog && tempSwipeCoords != null) {
        var name by remember { mutableStateOf("New Swipe") }
        var duration by remember { mutableStateOf("300") }
        
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Save Swipe") },
            text = {
                Column {
                    TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration (ms)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val coords = tempSwipeCoords!!
                        db.swipeDao().insert(
                            Swipe(
                                profileId = currentProfileId,
                                name = name,
                                startX = coords[0],
                                startY = coords[1],
                                endX = coords[2],
                                endY = coords[3],
                                duration = duration.toLongOrNull() ?: 300L
                            )
                        )
                        showNameDialog = false
                        tempSwipeCoords = null
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SwipeItem(
    swipe: Swipe,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = swipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration: ${swipe.duration}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "From (${swipe.startX}, ${swipe.startY}) to (${swipe.endX}, ${swipe.endY})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete Swipe")
            }
        }
    }
}
