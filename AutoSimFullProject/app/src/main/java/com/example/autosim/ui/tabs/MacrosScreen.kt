package com.example.autosim.ui.tabs

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.autosim.R
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.Macro
import com.example.autosim.overlay.PreviewOverlayService
import com.example.autosim.overlay.RecordingOverlayService
import com.example.autosim.utils.GlobalSettings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    val currentProfileId by GlobalSettings.currentProfileId.collectAsState()
    
    val macros by db.macroDao().getAll(currentProfileId).collectAsState(initial = emptyList())
    
    var showDeleteDialog by remember { mutableStateOf<Macro?>(null) }
    var expandedMacroId by remember { mutableStateOf<Int?>(null) }
    var isOverlayRunning by remember { mutableStateOf(false) }

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
                            text = "Macros",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Record and manage macros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            isOverlayRunning = !isOverlayRunning
                            if (isOverlayRunning) {
                                val intent = Intent(context, PreviewOverlayService::class.java)
                                ContextCompat.startForegroundService(context, intent)
                            } else {
                                context.stopService(Intent(context, PreviewOverlayService::class.java))
                            }
                        }
                    ) {
                        Text(if (isOverlayRunning) "Stop Overlay" else "Start Overlay")
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Launch Recording Service directly for quick access
                    val intent = Intent(context, RecordingOverlayService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(painter = painterResource(R.drawable.ic_record), contentDescription = "Record Macro")
            }
        }
    ) { padding ->
        if (macros.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_record),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No macros recorded yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the red button to start recording",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(macros) { macro ->
                    MacroItem(
                        macro = macro,
                        isExpanded = expandedMacroId == macro.id,
                        onToggleExpand = {
                            expandedMacroId = if (expandedMacroId == macro.id) null else macro.id
                        },
                        onDelete = { showDeleteDialog = macro },
                        db = db
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) } // Space for FAB
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { macro ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Macro?") },
            text = { Text("Are you sure you want to delete '${macro.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            db.macroActionDao().deleteForMacro(macro.id)
                            db.macroDao().delete(macro)
                            showDeleteDialog = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MacroItem(
    macro: Macro,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    db: AppDatabase
) {
    val actions by db.macroActionDao().getActionsForMacro(macro.id).collectAsState(initial = emptyList())
    
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val createdDate = remember(macro.createdAt) { dateFormat.format(Date(macro.createdAt)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onToggleExpand
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = macro.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Created: $createdDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${actions.size} actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (isExpanded && actions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Actions:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                actions.forEach { action ->
                    Text(
                        text = when (action.type) {
                            "CLICK" -> "• Click at (${action.x}, ${action.y})"
                            "SWIPE" -> "• Swipe from (${action.x}, ${action.y}) to (${action.endX}, ${action.endY})"
                            else -> "• ${action.type}"
                        } + if (action.delayBefore > 0) " [+${action.delayBefore}ms]" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
