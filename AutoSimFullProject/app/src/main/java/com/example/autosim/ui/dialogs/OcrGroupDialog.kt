package com.example.autosim.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.ClickSpot
import com.example.autosim.db.OcrGroup
import com.example.autosim.db.OcrPhrase
import com.example.autosim.db.Swipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrGroupDialog(
    db: AppDatabase,
    regionId: Int,
    group: OcrGroup?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var groupName by remember { mutableStateOf(group?.name ?: "") }
    var phrases by remember { mutableStateOf<List<OcrPhrase>>(emptyList()) }
    var showAddPhrase by remember { mutableStateOf(false) }
    var editingPhrase by remember { mutableStateOf<OcrPhrase?>(null) }
    val scope = rememberCoroutineScope()
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val clickSpots = db.clickSpotDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val swipes = db.swipeDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    
    
    LaunchedEffect(group) {
        if (group != null) {
            phrases = db.ocrPhraseDao().getPhrasesForGroup(group.id).first()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Add OCR Group" else "Edit OCR Group") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                TextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Phrases:")
                Button(
                    onClick = { showAddPhrase = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add Phrase")
                }
                
                // Use regular Column instead of LazyColumn to avoid nesting scrollable containers
                phrases.forEach { phrase ->
                    PhraseItem(
                        phrase = phrase,
                        clickSpots = clickSpots,
                        swipes = swipes,
                        onDelete = {
                            scope.launch {
                                db.ocrPhraseDao().delete(phrase)
                                phrases = db.ocrPhraseDao().getPhrasesForGroup(group!!.id).first()
                            }
                        },
                        onEdit = {
                            editingPhrase = phrase
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val newGroup = group?.copy(name = groupName) 
                            ?: OcrGroup(name = groupName, regionId = regionId)
                        
                        if (group == null) {
                            db.ocrGroupDao().insert(newGroup)
                        } else {
                            db.ocrGroupDao().update(newGroup)
                        }
                        onSave()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    if (showAddPhrase && group != null) {
        AddPhraseDialog(
            groupId = group.id,
            db = db,
            onDismiss = { showAddPhrase = false },
            onSave = {
                scope.launch {
                    phrases = db.ocrPhraseDao().getPhrasesForGroup(group.id).first()
                    showAddPhrase = false
                }
            }
        )
    }

    if (editingPhrase != null && group != null) {
        EditPhraseDialog(
            phrase = editingPhrase,
            groupId = group.id,
            db = db,
            onDismiss = { editingPhrase = null },
            onSave = {
                scope.launch {
                    phrases = db.ocrPhraseDao().getPhrasesForGroup(group.id).first()
                    editingPhrase = null
                }
            }
        )
    }
}

@Composable
fun PhraseItem(
    phrase: OcrPhrase,
    clickSpots: List<ClickSpot>,
    swipes: List<Swipe>,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Text: ${phrase.text}")
                val actionText = when (phrase.actionType) {
                    "CLICK" -> phrase.actionId?.let { clickSpots.find { s -> s.id == it }?.name ?: "None" } ?: "None"
                    "SWIPE" -> phrase.swipeId?.let { swipes.find { s -> s.id == it }?.name ?: "None" } ?: "None"
                    else -> "None"
                }
                Text("Action: ${phrase.actionType} - $actionText")
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPhraseDialog(
    groupId: Int,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    EditPhraseDialog(
        phrase = null,
        groupId = groupId,
        db = db,
        onDismiss = onDismiss,
        onSave = onSave
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhraseDialog(
    phrase: OcrPhrase?,
    groupId: Int,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var phraseText by remember { mutableStateOf(phrase?.text ?: "") }
    var actionType by remember { mutableStateOf(phrase?.actionType ?: "CLICK") }
    var selectedActionId by remember { mutableStateOf(phrase?.actionId) }
    var selectedSwipeId by remember { mutableStateOf(phrase?.swipeId) }
    var failureActionType by remember { mutableStateOf(phrase?.failureActionType ?: "NONE") }
    var failureActionId by remember { mutableStateOf(phrase?.failureActionId) }
    
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val clickSpots = db.clickSpotDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val swipes = db.swipeDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (phrase == null) "Add Phrase" else "Edit Phrase") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TextField(
                    value = phraseText,
                    onValueChange = { phraseText = it },
                    label = { Text("Phrase Text") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("On Success (Text Detected):")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { actionType = "CLICK" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (actionType == "CLICK") "✓ Click" else "Click")
                    }
                    Button(
                        onClick = { actionType = "SWIPE" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (actionType == "SWIPE") "✓ Swipe" else "Swipe")
                    }
                }
                
                if (actionType == "CLICK") {
                    if (clickSpots.isEmpty()) {
                        Text(
                            text = "No click spots available. Please create a click spot first.",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        clickSpots.forEach { spot ->
                            Button(
                                onClick = { selectedActionId = spot.id },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (selectedActionId == spot.id) 
                                    androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                                    ) 
                                else 
                                    androidx.compose.material3.ButtonDefaults.buttonColors()
                            ) {
                                Text(if (selectedActionId == spot.id) "✓ ${spot.name}" else spot.name)
                            }
                        }
                        Button(
                            onClick = { selectedActionId = null },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selectedActionId == null) 
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                                ) 
                            else 
                                androidx.compose.material3.ButtonDefaults.buttonColors()
                        ) {
                            Text(if (selectedActionId == null) "✓ None" else "None")
                        }
                    }
                } else if (actionType == "SWIPE") {
                    if (swipes.isEmpty()) {
                        Text(
                            text = "No swipes available. Please create a swipe first.",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        swipes.forEach { swipe ->
                            Button(
                                onClick = { selectedSwipeId = swipe.id },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (selectedSwipeId == swipe.id) 
                                    androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                                    ) 
                                else 
                                    androidx.compose.material3.ButtonDefaults.buttonColors()
                            ) {
                                Text(if (selectedSwipeId == swipe.id) "✓ ${swipe.name}" else swipe.name)
                            }
                        }
                        Button(
                            onClick = { selectedSwipeId = null },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selectedSwipeId == null) 
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                                ) 
                            else 
                                androidx.compose.material3.ButtonDefaults.buttonColors()
                        ) {
                            Text(if (selectedSwipeId == null) "✓ None" else "None")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("On Failure (Text NOT Detected):")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { failureActionType = "NONE"; failureActionId = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (failureActionType == "NONE") "✓ None" else "None")
                    }
                    Button(
                        onClick = { failureActionType = "CLICK" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (failureActionType == "CLICK") "✓ Click" else "Click")
                    }
                    Button(
                        onClick = { failureActionType = "SWIPE" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (failureActionType == "SWIPE") "✓ Swipe" else "Swipe")
                    }
                }
                
                if (failureActionType == "CLICK") {
                    clickSpots.forEach { spot ->
                        Button(
                            onClick = { failureActionId = spot.id },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (failureActionId == spot.id) "✓ ${spot.name}" else spot.name)
                        }
                    }
                } else if (failureActionType == "SWIPE") {
                    swipes.forEach { swipe ->
                        Button(
                            onClick = { failureActionId = swipe.id },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (failureActionId == swipe.id) "✓ ${swipe.name}" else swipe.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (phrase == null) {
                            db.ocrPhraseDao().insert(
                                OcrPhrase(
                                    groupId = groupId,
                                    text = phraseText,
                                    actionId = if (actionType == "CLICK") selectedActionId else null,
                                    actionType = actionType,
                                    swipeId = if (actionType == "SWIPE") selectedSwipeId else null,
                                    failureActionType = if (failureActionType != "NONE") failureActionType else null,
                                    failureActionId = failureActionId
                                )
                            )
                        } else {
                            db.ocrPhraseDao().update(
                                phrase.copy(
                                    text = phraseText,
                                    actionId = if (actionType == "CLICK") selectedActionId else null,
                                    actionType = actionType,
                                    swipeId = if (actionType == "SWIPE") selectedSwipeId else null,
                                    failureActionType = if (failureActionType != "NONE") failureActionType else null,
                                    failureActionId = failureActionId
                                )
                            )
                        }
                        onSave()
                    }
                },
                enabled = phraseText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

