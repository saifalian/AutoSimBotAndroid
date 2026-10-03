package com.example.autosim.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val profiles by db.profileDao().getAll().collectAsState(initial = emptyList())
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var profileToDelete by remember { mutableStateOf<com.example.autosim.db.Profile?>(null) }
    var profileToRename by remember { mutableStateOf<com.example.autosim.db.Profile?>(null) }
    var renameProfileName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Profiles", style = MaterialTheme.typography.headlineMedium)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, "Add Profile")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles) { profile ->
                    val isCurrent = profile.id == currentProfileId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                                if (profile.isDefault) {
                                    Text(
                                        text = "Default Profile",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (isCurrent) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Row {
                                if (!isCurrent) {
                                    TextButton(onClick = { 
                                        com.example.autosim.utils.GlobalSettings.currentProfileId.value = profile.id
                                    }) {
                                        Text("Switch")
                                    }
                                }
                                
                                if (!profile.isDefault) {
                                    IconButton(onClick = {
                                        profileToRename = profile
                                        renameProfileName = profile.name
                                    }) {
                                        Icon(Icons.Filled.Edit, "Rename")
                                    }
                                }
                                
                                if (!profile.isDefault && !isCurrent) {
                                    IconButton(onClick = {
                                        profileToDelete = profile
                                    }) {
                                        Icon(Icons.Filled.Delete, "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add Profile Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create New Profile") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            scope.launch {
                                val newProfile = com.example.autosim.db.Profile(name = newProfileName)
                                db.profileDao().insert(newProfile)
                                showAddDialog = false
                                newProfileName = ""
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Profile") },
            text = { 
                Text("Are you sure you want to delete \"${profileToDelete?.name}\"? All data in this profile will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            profileToDelete?.let { db.profileDao().delete(it) }
                            profileToDelete = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Rename Profile Dialog
    if (profileToRename != null) {
        AlertDialog(
            onDismissRequest = { 
                profileToRename = null
                renameProfileName = ""
            },
            title = { Text("Rename Profile") },
            text = {
                OutlinedTextField(
                    value = renameProfileName,
                    onValueChange = { renameProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameProfileName.isNotBlank()) {
                            scope.launch {
                                profileToRename?.let { profile ->
                                    val updatedProfile = profile.copy(name = renameProfileName)
                                    db.profileDao().update(updatedProfile)
                                }
                                profileToRename = null
                                renameProfileName = ""
                            }
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    profileToRename = null
                    renameProfileName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
