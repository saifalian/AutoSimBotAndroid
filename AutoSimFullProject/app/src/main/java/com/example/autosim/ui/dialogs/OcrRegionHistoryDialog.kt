package com.example.autosim.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.OcrRegion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OcrRegionHistoryDialog(
    region: OcrRegion,
    db: AppDatabase,
    onDismiss: () -> Unit
) {
    val history by db.ocrRegionTextHistoryDao().getHistoryForRegion(region.id).collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text History: ${region.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (history.isEmpty()) {
                    Text("No history yet")
                } else {
                    LazyColumn {
                        items(history) { item ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = dateFormat.format(Date(item.timestamp)),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = item.recognizedText,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

