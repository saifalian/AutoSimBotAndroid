package com.example.autosim.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.SequenceStep
import com.example.autosim.db.StepType
import kotlinx.coroutines.launch

@Composable
fun SequenceBuilderScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val sequenceDao = db.sequenceDao()
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val sequences by sequenceDao.getAll(currentProfileId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedSequenceId by remember { mutableStateOf<Int?>(null) }
    var showAddSequenceDialog by remember { mutableStateOf(false) }
    var sequenceToEdit by remember { mutableStateOf<com.example.autosim.db.Sequence?>(null) }

    Scaffold(
        topBar = {
            Text(
                text = "Sequence Builder",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    sequenceToEdit = null
                    showAddSequenceDialog = true 
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Sequence")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (sequences.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No sequences yet. Tap + to create one.")
                }
            } else {
                Text(
                    text = "Select Sequence:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(sequences) { sequence ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { selectedSequenceId = sequence.id },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(sequence.name)
                            }
                            IconButton(onClick = {
                                sequenceToEdit = sequence
                                showAddSequenceDialog = true
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit Sequence")
                            }
                            IconButton(onClick = {
                                scope.launch { 
                                    sequenceDao.delete(sequence)
                                    if (selectedSequenceId == sequence.id) {
                                        selectedSequenceId = null
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Sequence")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                selectedSequenceId?.let { seqId ->
                    Spacer(modifier = Modifier.height(16.dp))
                    val selectedSequence = sequences.find { it.id == seqId }
                    if (selectedSequence != null) {
                        Text("Editing: ${selectedSequence.name}", style = MaterialTheme.typography.titleSmall)
                        SequenceStepsList(
                            sequenceId = seqId,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        selectedSequenceId = null
                    }
                }
            }
        }
    }

    if (showAddSequenceDialog) {
        com.example.autosim.ui.dialogs.SequenceDialog(
            db = db,
            sequence = sequenceToEdit,
            onDismiss = { 
                showAddSequenceDialog = false 
                sequenceToEdit = null
            },
            onSave = { 
                showAddSequenceDialog = false 
                sequenceToEdit = null
            }
        )
    }
}

@Composable
fun SequenceStepsList(sequenceId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val stepDao = db.sequenceStepDao()
    val steps by stepDao.getStepsForSequence(sequenceId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddStepDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Steps:",
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = { showAddStepDialog = true }) {
                Text("Add Step")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (steps.isEmpty()) {
            Text("No steps yet. Add a step to get started.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(steps.sortedBy { it.stepNumber }) { step ->
                    SequenceStepItem(
                        step = step,
                        db = db,
                        onEdit = { },
                        onMoveUp = {
                            scope.launch {
                                val index = steps.indexOf(step)
                                if (index > 0) {
                                    val mutableList = steps.toMutableList()
                                    mutableList[index] = mutableList[index - 1].also { mutableList[index - 1] = mutableList[index] }
                                    
                                    mutableList.forEachIndexed { newIndex, s ->
                                        stepDao.update(s.copy(stepNumber = newIndex + 1))
                                    }
                                }
                            }
                        },
                        onMoveDown = {
                            scope.launch {
                                val index = steps.indexOf(step)
                                if (index < steps.size - 1) {
                                    val mutableList = steps.toMutableList()
                                    mutableList[index] = mutableList[index + 1].also { mutableList[index + 1] = mutableList[index] }
                                    
                                    mutableList.forEachIndexed { newIndex, s ->
                                        stepDao.update(s.copy(stepNumber = newIndex + 1))
                                    }
                                }
                            }
                        },
                        onDelete = {
                            scope.launch { stepDao.delete(step) }
                        }
                    )
                }
            }
        }
    }

    if (showAddStepDialog) {
        val nextStepNumber = (steps.maxOfOrNull { it.stepNumber } ?: 0) + 1
        com.example.autosim.ui.dialogs.SequenceStepDialog(
            db = db,
            sequenceId = sequenceId,
            step = null,
            stepNumber = nextStepNumber,
            onDismiss = { showAddStepDialog = false },
            onSave = { showAddStepDialog = false }
        )
    }
}

@Composable
fun SequenceStepItem(
    step: SequenceStep,
    db: AppDatabase,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${step.stepNumber}: ${step.type.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                when (step.type) {
                    StepType.CLICK -> step.targetId?.let { Text("Click Spot ID: $it", style = MaterialTheme.typography.bodySmall) }
                    StepType.OCR_REGION_SCAN -> step.targetId?.let { Text("OCR Region ID: $it", style = MaterialTheme.typography.bodySmall) }
                    StepType.WAIT -> step.delay?.let { Text("Wait: ${it}ms", style = MaterialTheme.typography.bodySmall) }
                    else -> {}
                }
                Text("Delay After: ${step.delayAfter}ms", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up")
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
    if (showEditDialog) {
        com.example.autosim.ui.dialogs.SequenceStepDialog(
            db = db,
            sequenceId = step.sequenceId,
            step = step,
            stepNumber = step.stepNumber,
            onDismiss = { showEditDialog = false },
            onSave = { showEditDialog = false }
        )
    }
}
