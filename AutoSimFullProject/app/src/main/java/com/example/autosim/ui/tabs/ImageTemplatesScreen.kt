package com.example.autosim.ui.tabs

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.ImageTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImageTemplatesScreen(regionOnly: Boolean = false) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val allTemplates by db.imageTemplateDao().getAll(currentProfileId).collectAsState(initial = emptyList())
    val templates = if (regionOnly) {
        allTemplates.filter { it.regionId != null }
    } else {
        allTemplates.filter { it.regionId == null }
    }
    val scope = rememberCoroutineScope()
    
    var editingTemplate by remember { mutableStateOf<ImageTemplate?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                importImageFromGallery(context, db, uri, regionOnly, currentProfileId)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (regionOnly) "Image Templates (Region)" else "Image Templates (Global)",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (regionOnly) "Detect images within specific regions" else "Detect images anywhere on screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            com.example.autosim.overlay.PreviewOverlayService.showImageTemplates = !com.example.autosim.overlay.PreviewOverlayService.showImageTemplates
                            if (com.example.autosim.overlay.PreviewOverlayService.showImageTemplates) {
                                androidx.core.content.ContextCompat.startForegroundService(context, android.content.Intent(context, com.example.autosim.overlay.PreviewOverlayService::class.java))
                            } else {
                                context.stopService(android.content.Intent(context, com.example.autosim.overlay.PreviewOverlayService::class.java))
                            }
                        }
                    ) {
                        Text(if (com.example.autosim.overlay.PreviewOverlayService.showImageTemplates) "Stop Overlay" else "Start Overlay")
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(Icons.Filled.Add, contentDescription = "Import from Gallery")
            }
        }
    ) { paddingValues ->
        if (templates.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No image templates yet. Import from gallery or capture from overlay.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates) { template ->
                    ImageTemplateItem(
                        template = template,
                        onEdit = { editingTemplate = template },
                        onDelete = {
                            scope.launch {
                                File(template.imagePath).delete()
                                db.imageTemplateDao().delete(template)
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (editingTemplate != null) {
        AddEditImageTemplateDialog(
            template = editingTemplate!!,
            db = db,
            onDismiss = { editingTemplate = null },
            onSave = { updatedTemplate ->
                scope.launch {
                    db.imageTemplateDao().update(updatedTemplate)
                    editingTemplate = null
                }
            }
        )
    }
}

@Composable
fun ImageTemplateItem(
    template: ImageTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    
    LaunchedEffect(template.imagePath) {
        withContext(Dispatchers.IO) {
            try {
                bitmap = BitmapFactory.decodeFile(template.imagePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showPreview && bitmap != null) {
        FullImagePreviewDialog(
            bitmap = bitmap!!,
            name = template.name,
            onDismiss = { showPreview = false }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Template Preview",
                    modifier = Modifier
                        .size(60.dp)
                        .padding(end = 16.dp)
                        .clickable { showPreview = true },
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    modifier = Modifier
                        .size(60.dp)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No Img", style = MaterialTheme.typography.bodySmall)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Threshold: ${(template.threshold * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (template.regionId != null) "Region-based" else "Full Screen",
                    style = MaterialTheme.typography.bodySmall
                )
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

@Composable
fun FullImagePreviewDialog(
    bitmap: android.graphics.Bitmap,
    name: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Size: ${bitmap.width}x${bitmap.height}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditImageTemplateDialog(
    template: ImageTemplate,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onSave: (ImageTemplate) -> Unit
) {
    var name by remember { mutableStateOf(template.name) }
    var threshold by remember { mutableFloatStateOf(template.threshold) }
    var regionId by remember { mutableStateOf(template.regionId) }
    
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val regions by db.ocrRegionDao().getAll(currentProfileId).collectAsState(initial = emptyList())
    
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Image Template") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Column {
                    Text("Threshold: ${(threshold * 100).toInt()}%")
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = 0.1f..1.0f
                    )
                    Text(
                        "Higher threshold means stricter matching.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = if (regionId == null) "Full Screen" else regions.find { it.id == regionId }?.name ?: "Unknown Region",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Search Region") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Full Screen") },
                            onClick = {
                                regionId = null
                                expanded = false
                            }
                        )
                        regions.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region.name) },
                                onClick = {
                                    regionId = region.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(template.copy(
                    name = name,
                    threshold = threshold,
                    regionId = regionId,
                    isFullScreen = regionId == null
                ))
            }) {
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

private suspend fun importImageFromGallery(context: Context, db: AppDatabase, uri: Uri, regionOnly: Boolean, profileId: Int) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap != null) {
            val templates = db.imageTemplateDao().getAll(profileId).first()
            val nextName = "Gallery ${templates.size + 1}"
            val filename = "template_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            val newTemplate = ImageTemplate(
                profileId = profileId,
                name = nextName,
                imagePath = file.absolutePath,
                isFullScreen = !regionOnly,
                regionId = null
            )
            db.imageTemplateDao().insert(newTemplate)
            bitmap.recycle()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
