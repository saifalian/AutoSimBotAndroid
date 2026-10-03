package com.example.autosim.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.Sequence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequenceDialog(
    db: AppDatabase,
    sequence: Sequence?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var sequenceName by remember { mutableStateOf(sequence?.name ?: "") }
    var repeatOption by remember { mutableStateOf(if (sequence == null) RepeatOption.ONCE else RepeatOption.fromInt(sequence.repeatCount)) }
    var repeatCount by remember { mutableStateOf(sequence?.repeatCount?.toString() ?: "1") }
    val scope = rememberCoroutineScope()
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sequence == null) "Add Sequence" else "Edit Sequence") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                TextField(
                    value = sequenceName,
                    onValueChange = { sequenceName = it },
                    label = { Text("Sequence Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Repeat Options:")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = repeatOption == RepeatOption.ONCE,
                        onClick = { repeatOption = RepeatOption.ONCE }
                    )
                    Text("One Time")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = repeatOption == RepeatOption.INFINITE,
                        onClick = { repeatOption = RepeatOption.INFINITE }
                    )
                    Text("Infinite")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = repeatOption == RepeatOption.LIMITED,
                        onClick = { repeatOption = RepeatOption.LIMITED }
                    )
                    Text("Limited")
                    if (repeatOption == RepeatOption.LIMITED) {
                        TextField(
                            value = repeatCount,
                            onValueChange = { repeatCount = it },
                            label = { Text("Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val finalRepeatCount = when (repeatOption) {
                            RepeatOption.ONCE -> 1
                            RepeatOption.INFINITE -> -1
                            RepeatOption.LIMITED -> repeatCount.toIntOrNull() ?: 1
                        }
                        val newSequence = if (sequence == null) {
                            val sequences = db.sequenceDao().getAll(currentProfileId).first()
                            val nextOrder = sequences.size
                            
                            Sequence(
                                profileId = currentProfileId,
                                name = sequenceName,
                                repeatCount = finalRepeatCount,
                                executionOrder = nextOrder
                            )
                        } else {
                            sequence.copy(
                                name = sequenceName,
                                repeatCount = finalRepeatCount
                            )
                        }
                        
                        if (sequence == null) {
                            db.sequenceDao().insert(newSequence)
                        } else {
                            db.sequenceDao().update(newSequence)
                        }
                        onSave()
                    }
                },
                enabled = sequenceName.isNotBlank()
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

enum class RepeatOption {
    ONCE,
    INFINITE,
    LIMITED;

    companion object {
        fun fromInt(value: Int): RepeatOption {
            return when (value) {
                -1 -> INFINITE
                1 -> ONCE
                else -> LIMITED
            }
        }
    }
}
