package com.example.autosim.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.TextDetectionPhrase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextDetectionDialog(
    db: AppDatabase,
    phrase: TextDetectionPhrase?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var phraseText by remember { mutableStateOf(phrase?.text ?: "") }
    var selectedActionId by remember { mutableStateOf<Int?>(phrase?.actionId) }
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val clickSpots = db.clickSpotDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (phrase == null) "Add Text Detection Phrase" else "Edit Phrase") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                TextField(
                    value = phraseText,
                    onValueChange = { phraseText = it },
                    label = { Text("Phrase Text") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Action (Click Spot):")
                clickSpots.forEach { spot ->
                    Button(
                        onClick = { selectedActionId = spot.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedActionId == spot.id) "✓ ${spot.name}" else spot.name)
                    }
                }
                Button(
                    onClick = { selectedActionId = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedActionId == null) "✓ None" else "None")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val newPhrase = if (phrase == null) {
                            TextDetectionPhrase(
                                profileId = currentProfileId,
                                text = phraseText,
                                actionId = selectedActionId
                            )
                        } else {
                            phrase.copy(
                                text = phraseText,
                                actionId = selectedActionId
                            )
                        }
                        
                        if (phrase == null) {
                            db.textDetectionPhraseDao().insert(newPhrase)
                        } else {
                            db.textDetectionPhraseDao().update(newPhrase)
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
