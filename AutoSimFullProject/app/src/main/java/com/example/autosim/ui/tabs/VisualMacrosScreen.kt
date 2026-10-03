package com.example.autosim.ui.tabs

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import com.example.autosim.db.Sequence
import com.example.autosim.overlay.VisualRecordingOverlayService
import com.example.autosim.utils.GlobalSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualMacrosScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()
    val currentProfileId by GlobalSettings.currentProfileId.collectAsState()
    
    // Filter for visual macros
    val sequences by db.sequenceDao().getAll(currentProfileId).collectAsState(initial = emptyList())
    val visualMacros = sequences.filter { it.isVisualMacro }
    
    var showDeleteDialog by remember { mutableStateOf<Sequence?>(null) }
    var expandedSequenceId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Visual Macros",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Record actions with image detection context",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Launch Visual Recording Service
                    val intent = Intent(context, VisualRecordingOverlayService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(painter = painterResource(R.drawable.ic_record), contentDescription = "Record Visual Macro")
            }
        }
    ) { padding ->
        if (visualMacros.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_image),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No visual macros yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the button to start recording with image context",
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
                items(visualMacros) { sequence ->
                    VisualMacroItem(
                        sequence = sequence,
                        isExpanded = expandedSequenceId == sequence.id,
                        onToggleExpand = {
                            expandedSequenceId = if (expandedSequenceId == sequence.id) null else sequence.id
                        },
                        onDelete = { showDeleteDialog = sequence },
                        db = db
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) } // Space for FAB
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { sequence ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Visual Macro?") },
            text = { Text("Are you sure you want to delete '${sequence.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // Delete sequence and its steps
                            val steps = db.sequenceStepDao().getStepsForSequence(sequence.id).first()
                            steps.forEach { step -> db.sequenceStepDao().delete(step) }
                            db.sequenceDao().delete(sequence)
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
fun VisualMacroItem(
    sequence: Sequence,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    db: AppDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps by db.sequenceStepDao().getStepsForSequence(sequence.id).collectAsState(initial = emptyList())
    val isRunning by com.example.autosim.engine.SequenceRunner.isRunning.collectAsState()
    
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
                        text = sequence.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${steps.size} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Row {
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                com.example.autosim.engine.SequenceRunner.stop()
                            } else {
                                scope.launch {
                                    com.example.autosim.engine.SequenceRunner.startSequence(sequence.id)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "Stop" else "Play",
                            tint = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
            }
            
            if (isExpanded && steps.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Steps:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                steps.sortedBy { it.stepNumber }.forEach { step ->
                    Text(
                        text = "• Step ${step.stepNumber}: ${step.type}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
