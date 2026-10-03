package com.example.autosim.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.Sequence
import com.example.autosim.engine.SequenceRunner
import kotlinx.coroutines.launch

@Composable
fun GlobalRunnerScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val sequences by db.sequenceDao().getAll(currentProfileId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    // Global Loop Settings (Synced with GlobalSettings)
    var globalLoopMode by remember { mutableStateOf(com.example.autosim.utils.GlobalSettings.globalLoopMode) }
    var globalLoopCount by remember { mutableStateOf(com.example.autosim.utils.GlobalSettings.globalLoopCount.toString()) }
    
    // Observe SequenceRunner state
    val isRunning by SequenceRunner.isRunning.collectAsState()
    
    // Update GlobalSettings when state changes
    androidx.compose.runtime.LaunchedEffect(globalLoopMode, globalLoopCount) {
        com.example.autosim.utils.GlobalSettings.globalLoopMode = globalLoopMode
        com.example.autosim.utils.GlobalSettings.globalLoopCount = globalLoopCount.toIntOrNull() ?: 1
    }
    
    // Sort sequences by executionOrder
    val sortedSequences = sequences.sortedBy { it.executionOrder }
    
    // Dialog state
    var editingSequence by remember { mutableStateOf<Sequence?>(null) }

    if (editingSequence != null) {
        com.example.autosim.ui.dialogs.SequenceDialog(
            db = db,
            sequence = editingSequence,
            onDismiss = { editingSequence = null },
            onSave = { editingSequence = null }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Global Sequence Runner",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Global Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Global Loop Settings", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Loop Mode Selection
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var expanded by remember { mutableStateOf(false) }
                            val options = listOf("Run Playlist Once", "Run Playlist N Times", "Run Playlist Infinitely")
                            
                            Column {
                                OutlinedButton(onClick = { expanded = true }) {
                                    Text(options[globalLoopMode])
                                    Icon(Icons.Filled.ArrowDropDown, null)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    options.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = { 
                                                globalLoopMode = index
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            if (globalLoopMode == 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = globalLoopCount,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) globalLoopCount = it },
                                    label = { Text("Count") },
                                    modifier = Modifier.width(100.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Global Delay Setting
                        var globalDelayText by remember { mutableStateOf(com.example.autosim.utils.GlobalSettings.globalSequenceDelay.toString()) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Default Delay (ms):", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = globalDelayText,
                                onValueChange = { 
                                    if (it.all { char -> char.isDigit() }) {
                                        globalDelayText = it
                                        com.example.autosim.utils.GlobalSettings.globalSequenceDelay = it.toLongOrNull() ?: 500L
                                    }
                                },
                                modifier = Modifier.width(100.dp),
                                singleLine = true
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Start Playlist and Bot Overlay buttons in a row
                        val isOverlayRunning by com.example.autosim.overlay.ExecutionOverlayService.isRunning.collectAsState()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isRunning) {
                                        SequenceRunner.stopSequence()
                                    } else {
                                        val loopCount = when (globalLoopMode) {
                                            0 -> 1
                                            1 -> globalLoopCount.toIntOrNull() ?: 1
                                            else -> -1
                                        }
                                        // Filter enabled sequences
                                        val enabledSequences = sortedSequences.filter { it.isEnabled }
                                        if (enabledSequences.isNotEmpty()) {
                                            scope.launch {
                                                SequenceRunner.runPlaylist(
                                                    context = context,
                                                    sequences = enabledSequences,
                                                    globalLoopCount = loopCount,
                                                    onComplete = { }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (isRunning) 
                                    androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                else 
                                    androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(if (isRunning) androidx.compose.material.icons.Icons.Filled.PlayArrow else Icons.Filled.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isRunning) "Stop Playlist" else "Start Playlist")
                            }
                            
                            Button(
                                onClick = {
                                    if (isOverlayRunning) {
                                        val serviceIntent = android.content.Intent(context, com.example.autosim.overlay.ExecutionOverlayService::class.java)
                                        context.stopService(serviceIntent)
                                    } else {
                                        val serviceIntent = android.content.Intent(context, com.example.autosim.overlay.ExecutionOverlayService::class.java)
                                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (isOverlayRunning) 
                                    androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                else 
                                    androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(if (isOverlayRunning) "Stop Bot Overlay" else "Start Bot Overlay")
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedSequences) { sequence ->
                SequencePlaylistItem(
                    sequence = sequence,
                    onUpdate = { updatedSeq ->
                        scope.launch { db.sequenceDao().update(updatedSeq) }
                    },
                    onEdit = { editingSequence = sequence },
                    onMoveUp = {
                        val index = sortedSequences.indexOf(sequence)
                        if (index > 0) {
                            scope.launch {
                                // Create a new list with swapped positions
                                val mutableList = sortedSequences.toMutableList()
                                mutableList[index] = mutableList[index - 1].also { mutableList[index - 1] = mutableList[index] }
                                
                                // Reassign executionOrder to all sequences
                                mutableList.forEachIndexed { newIndex, seq ->
                                    db.sequenceDao().update(seq.copy(executionOrder = newIndex))
                                }
                            }
                        }
                    },
                    onMoveDown = {
                        val index = sortedSequences.indexOf(sequence)
                        if (index < sortedSequences.size - 1) {
                            scope.launch {
                                // Create a new list with swapped positions
                                val mutableList = sortedSequences.toMutableList()
                                mutableList[index] = mutableList[index + 1].also { mutableList[index + 1] = mutableList[index] }
                                
                                // Reassign executionOrder to all sequences
                                mutableList.forEachIndexed { newIndex, seq ->
                                    db.sequenceDao().update(seq.copy(executionOrder = newIndex))
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SequencePlaylistItem(
    sequence: Sequence,
    onUpdate: (Sequence) -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sequence.isEnabled,
                        onCheckedChange = { onUpdate(sequence.copy(isEnabled = it)) }
                    )
                    Text(
                        text = sequence.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onEdit() } // Make name clickable
                    )
                }
                
                Row {
                    IconButton(onClick = onMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move Up")
                    }
                    IconButton(onClick = onMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move Down")
                    }
                }
            }
            
            // Row 2: Internal Loops and Global Repeats
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Internal Loops: ${if (sequence.repeatCount == -1) "∞" else sequence.repeatCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                
                // Playlist Repeat Count Input
                var playlistRepeatText by remember(sequence.playlistRepeatCount) { mutableStateOf(sequence.playlistRepeatCount.toString()) }
                Text(
                    text = if (sequence.playlistRepeatCount == 0) "Global Repeats: (Auto)" else "Global Repeats:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = playlistRepeatText,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() }) {
                            playlistRepeatText = it
                            val count = it.toIntOrNull() ?: 0
                            if (count != sequence.playlistRepeatCount) {
                                onUpdate(sequence.copy(playlistRepeatCount = count))
                            }
                        }
                    },
                    modifier = Modifier.width(60.dp).height(45.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true
                )
            }

            // Row 3: Delay and Stop After
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Use Global Delay Checkbox
                val useGlobalDelay = sequence.playlistDelay == -1L
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useGlobalDelay,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // Use global delay
                                onUpdate(sequence.copy(playlistDelay = -1L))
                            } else {
                                // Use default 500ms delay
                                onUpdate(sequence.copy(playlistDelay = 500L))
                            }
                        }
                    )
                    Text(
                        text = "Use Global Delay",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sequence.stopAfter,
                        onCheckedChange = { onUpdate(sequence.copy(stopAfter = it)) }
                    )
                    Text(
                        text = "Stop After",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
