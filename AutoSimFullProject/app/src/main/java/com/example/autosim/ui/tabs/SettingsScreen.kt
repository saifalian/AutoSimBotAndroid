package com.example.autosim.ui.tabs

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autosim.MainActivity
import androidx.navigation.NavController
import com.example.autosim.Screen
import com.example.autosim.db.AppDatabase
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.widget.Toast
import com.example.autosim.utils.ImportExportManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)

    Scaffold(
        topBar = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "App Management",
                style = MaterialTheme.typography.titleLarge
            )

            val overlayRunning by com.example.autosim.overlay.ExecutionOverlayService.isRunning.collectAsState()
            
            Button(
                onClick = { navController.navigate(Screen.Profile.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Profiles")
            }

            // Overlay Controls button removed as overlay management is now in Global Runner


            val logsAvailable: List<com.example.autosim.db.LogHistory> by db.logHistoryDao().getAll().collectAsState(initial = emptyList())
            
            Button(
                onClick = { navController.navigate(Screen.Logs.route) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (logsAvailable.isNotEmpty()) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (logsAvailable.isNotEmpty()) "Logs Available (${logsAvailable.size})" else "View Logs")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleLarge
            )
            
            var isAccessibilityEnabled by remember { 
                mutableStateOf(
                    run {
                        val expectedComponentName = "${context.packageName}/${com.example.autosim.accessibility.AutomationAccessibilityService::class.java.canonicalName}"
                        val enabledServices = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                        enabledServices?.contains(expectedComponentName) == true
                    }
                )
            }
            
            var isOverlayPermissionGranted by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
            
            var isScreenCaptureActive by remember { mutableStateOf(com.example.autosim.engine.ScreenCaptureService.isServiceActive()) }
            
            // Update states when the screen is visible
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(500)
                    val expectedComponentName = "${context.packageName}/${com.example.autosim.accessibility.AutomationAccessibilityService::class.java.canonicalName}"
                    val enabledServices = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    isAccessibilityEnabled = enabledServices?.contains(expectedComponentName) == true
                    isOverlayPermissionGranted = android.provider.Settings.canDrawOverlays(context)
                    isScreenCaptureActive = com.example.autosim.engine.ScreenCaptureService.isServiceActive()
                }
            }
            
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isAccessibilityEnabled) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isAccessibilityEnabled) "Stop Accessibility Service" else "Open Accessibility Settings")
            }
            
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isOverlayPermissionGranted) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isOverlayPermissionGranted) "Stop Overlay Permission Service" else "Open Overlay Permission Settings")
            }
            
            Button(
                onClick = {
                    if (isScreenCaptureActive) {
                        com.example.autosim.engine.ScreenCaptureService.stopService()
                    } else {
                        (context as? MainActivity)?.startScreenCaptureIntent()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isScreenCaptureActive) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isScreenCaptureActive) "Stop Screen Capture Service" else "Request Screen Capture Permission")
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Capture Settings",
                style = MaterialTheme.typography.titleLarge
            )
            
            var detectActionsWithScreenCapture by remember { mutableStateOf(com.example.autosim.utils.GlobalSettings.detectActionsWithScreenCapture) }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Detect Actions with Screen Capture")
                androidx.compose.material3.Switch(
                    checked = detectActionsWithScreenCapture,
                    onCheckedChange = { 
                        detectActionsWithScreenCapture = it
                        com.example.autosim.utils.GlobalSettings.detectActionsWithScreenCapture = it
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleLarge
            )
            
            var showImportExport by remember { mutableStateOf(false) }
            
            Button(
                onClick = { showImportExport = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import/Export Configuration")
            }
            
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
            var showImportModeDialog by remember { mutableStateOf(false) }
            
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                uri?.let {
                    scope.launch {
                        val success = ImportExportManager.exportData(context, it)
                        if (success) {
                            Toast.makeText(context, "Export Successful", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    // Check if current profile has data
                    scope.launch {
                        val db = AppDatabase.getDatabase(context)
                        val currentProfileId = com.example.autosim.utils.GlobalSettings.currentProfileId.value
                        val hasData = db.clickSpotDao().getAll(currentProfileId).first().isNotEmpty() ||
                                     db.ocrRegionDao().getAll(currentProfileId).first().isNotEmpty() ||
                                     db.swipeDao().getAll(currentProfileId).first().isNotEmpty() ||
                                     db.sequenceDao().getAll(currentProfileId).first().isNotEmpty()
                        
                        if (hasData) {
                            // Show dialog to ask user
                            pendingImportUri = it
                            showImportModeDialog = true
                        } else {
                            // No existing data, just import
                            val success = ImportExportManager.importData(context, it, replaceExisting = false)
                            if (success) {
                                Toast.makeText(context, "Import Successful", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Import Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            
            if (showImportExport) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showImportExport = false },
                    title = { Text("Import / Export") },
                    text = { 
                        Column {
                            Text("Export: Backs up all data from all profiles.")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Import: Merges backup data into the current profile.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    exportLauncher.launch("autosim_backup_$timeStamp.json")
                                    showImportExport = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export Backup")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { 
                                    importLauncher.launch(arrayOf("application/json"))
                                    showImportExport = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Import Backup")
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showImportExport = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            
            // Import mode selection dialog
            if (showImportModeDialog && pendingImportUri != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { 
                        showImportModeDialog = false
                        pendingImportUri = null
                    },
                    title = { Text("Import Mode") },
                    text = { 
                        Column {
                            Text("This profile already has data. How would you like to import?")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("• Merge: Keep existing data and add imported items", style = MaterialTheme.typography.bodySmall)
                            Text("• Replace: Delete existing data and use only imported items", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { 
                                scope.launch {
                                    val success = ImportExportManager.importData(context, pendingImportUri!!, replaceExisting = false)
                                    if (success) {
                                        Toast.makeText(context, "Import Successful (Merged)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Import Failed", Toast.LENGTH_SHORT).show()
                                    }
                                    showImportModeDialog = false
                                    pendingImportUri = null
                                }
                            }
                        ) {
                            Text("Merge")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { 
                                scope.launch {
                                    val success = ImportExportManager.importData(context, pendingImportUri!!, replaceExisting = true)
                                    if (success) {
                                        Toast.makeText(context, "Import Successful (Replaced)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Import Failed", Toast.LENGTH_SHORT).show()
                                    }
                                    showImportModeDialog = false
                                    pendingImportUri = null
                                }
                            }
                        ) {
                            Text("Replace")
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge
            )
            
            var showGuide by remember { mutableStateOf(false) }
            
            Button(
                onClick = { showGuide = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("About & User Guide")
            }
            
            Text(
                text = "BOT v1.0\nDeveloped by Saif Ali",
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (showGuide) {
                AboutAndGuideDialog(onDismiss = { showGuide = false })
            }
        }
    }
}

@Composable
fun AboutAndGuideDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About & User Guide") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                item {
                    GuideSection(
                        title = "Welcome to BOT",
                        content = """
                            BOT is a powerful automation tool for Android that helps you automate repetitive tasks.
                            
                            This guide will walk you through the basics of setting up and using BOT.
                        """.trimIndent()
                    )
                }
                
                item {
                    GuideSection(
                        title = "Getting Started",
                        content = """
                            1. Enable Accessibility Service
                            2. Grant Overlay Permission
                            3. Grant Screen Capture Permission (for OCR features)
                            
                            All permissions can be managed from the Settings tab.
                        """.trimIndent()
                    )
                }
                
                item {
                    GuideSection(
                        title = "Creating Actions",
                        content = """
                            Use the overlay to record actions:
                            • Add Click: Tap to add a click point
                            • Add Swipe: Draw a swipe gesture
                            • Add Region: Define an area for text detection
                            • Add Image: Capture a screen area for image detection
                        """.trimIndent()
                    )
                }
                
                item {
                    GuideSection(
                        title = "Building Sequences",
                        content = """
                            1. Go to Sequences tab
                            2. Create a new sequence
                            3. Add steps (clicks, swipes, waits, detections)
                            4. Reorder steps as needed
                            5. Run your sequence!
                        """.trimIndent()
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Developed with ❤️ by Saif Ali",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun GuideSection(title: String, content: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
                lineHeight = 20.sp
            )
        }
    }
}
