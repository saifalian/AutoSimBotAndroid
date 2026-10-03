package com.example.autosim.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.ClickSpot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditClickSpotScreen(
    navController: NavController,
    spotId: Int? = null
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var delay by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("1") }

    var isDrawing by remember { mutableStateOf(false) }

    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()

    if (isDrawing) {
        ClickSpotDrawingScreen(
            onSave = { newX, newY ->
                x = newX.toString()
                y = newY.toString()
                isDrawing = false
            },
            onCancel = { isDrawing = false },
            useScreenCapture = com.example.autosim.utils.GlobalSettings.detectActionsWithScreenCapture
        )
    } else {
        LaunchedEffect(spotId) {
            spotId?.let { id ->
                scope.launch {
                    val spot = db.clickSpotDao().getById(id)
                    spot?.let {
                        name = it.name
                        x = it.x.toString()
                        y = it.y.toString()
                        delay = it.delay.toString()
                        repeat = it.repeat.toString()
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                Text(
                    text = if (spotId == null) "Add Click Spot" else "Edit Click Spot",
                    modifier = Modifier.padding(16.dp)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { isDrawing = true }) {
                    Text("Draw Spot")
                }
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = x,
                    onValueChange = { x = it },
                    label = { Text("X Coordinate") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = y,
                    onValueChange = { y = it },
                    label = { Text("Y Coordinate") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = delay,
                    onValueChange = { delay = it },
                    label = { Text("Delay (ms)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Repeat Count") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val spot = if (spotId == null) {
                                ClickSpot(
                                    profileId = currentProfileId,
                                    name = name,
                                    x = x.toIntOrNull() ?: 0,
                                    y = y.toIntOrNull() ?: 0,
                                    delay = delay.toLongOrNull() ?: 0,
                                    repeat = repeat.toIntOrNull() ?: 1
                                )
                            } else {
                                // Fetch existing to keep profileId or update it? 
                                // For now, let's assume we keep the existing profileId unless we want to move it.
                                // But wait, we don't have the existing object here easily without fetching again or storing it.
                                // Let's fetch it again or just assume currentProfileId if we want to "move" it to current profile on edit?
                                // Better to keep it in the same profile it was created in.
                                val existing = db.clickSpotDao().getById(spotId)
                                ClickSpot(
                                    id = spotId,
                                    profileId = existing?.profileId ?: currentProfileId,
                                    name = name,
                                    x = x.toIntOrNull() ?: 0,
                                    y = y.toIntOrNull() ?: 0,
                                    delay = delay.toLongOrNull() ?: 0,
                                    repeat = repeat.toIntOrNull() ?: 1
                                )
                            }

                            if (spotId == null) {
                                db.clickSpotDao().insert(spot)
                            } else {
                                db.clickSpotDao().update(spot)
                            }
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
