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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.ClickSpot
import com.example.autosim.db.ImageTemplate
import com.example.autosim.db.OcrRegion
import com.example.autosim.db.SequenceStep
import com.example.autosim.db.StepType
import com.example.autosim.db.Swipe
import com.example.autosim.db.Macro
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequenceStepDialog(
    db: AppDatabase,
    sequenceId: Int,
    step: SequenceStep?,
    stepNumber: Int,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    // Basic step type selection
    var selectedType by remember { mutableStateOf(step?.type ?: StepType.CLICK) }
    var selectedClickSpot by remember { mutableStateOf<Int?>(step?.targetId) }
    var selectedOcrRegion by remember { mutableStateOf<Int?>(step?.targetId) }
    var selectedSwipe by remember { mutableStateOf<Int?>(step?.targetId) }
    var selectedImageTemplate by remember { mutableStateOf<Int?>(step?.targetId) }
    var selectedMacro by remember { mutableStateOf<Int?>(step?.targetId) }
    var detectionScopeIsGlobal by remember { mutableStateOf(true) } // true = global, false = region
    var waitDelay by remember { mutableStateOf(step?.delay?.toString() ?: "1000") }
    var delayAfter by remember { mutableStateOf(step?.delayAfter?.toString() ?: "500") }
    var repeatCount by remember { mutableStateOf(step?.repeatCount?.toString() ?: "1") }
    var conditionalPhrase by remember { mutableStateOf(step?.conditionalPhrase ?: "") }
    var jumpToStep by remember { mutableStateOf(step?.jumpToStep?.toString() ?: "") }
    
    // Retry options
    var retryOnFailure by remember { mutableStateOf(step?.retryOnFailure ?: false) }
    var retryDelay by remember { mutableStateOf(step?.retryDelay?.toString() ?: "1000") }

    // Load data from DB
    val currentProfileId by com.example.autosim.utils.GlobalSettings.currentProfileId.collectAsState()
    val clickSpots = db.clickSpotDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val ocrRegions = db.ocrRegionDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val swipes = db.swipeDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val imageTemplates = db.imageTemplateDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value
    val macros = db.macroDao().getAll(currentProfileId).collectAsState(initial = emptyList()).value

    // Success/Failure actions for detection steps
    var successActionType by remember { mutableStateOf(step?.successActionType ?: "NONE") }
    var successActionId by remember { mutableStateOf<Int?>(step?.successActionId) }
    var failureActionType by remember { mutableStateOf(step?.failureActionType ?: "NONE") }
    var failureActionId by remember { mutableStateOf<Int?>(step?.failureActionId) }

    val scope = rememberCoroutineScope()

    // Initialize detectionScopeIsGlobal based on the existing step's target
    androidx.compose.runtime.LaunchedEffect(step, imageTemplates) {
        if (step != null && step.type == StepType.IMAGE_DETECTION && step.targetId != null) {
            val targetTemplate = imageTemplates.find { it.id == step.targetId }
            if (targetTemplate != null) {
                detectionScopeIsGlobal = (targetTemplate.regionId == null)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == null) "Add Step" else "Edit Step") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Step type selection
                Text("Step Type:")
                StepType.values().forEach { type ->
                    Button(
                        onClick = { selectedType = type },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedType == type) "✓ ${type.name}" else type.name)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                when (selectedType) {
                    StepType.CLICK -> {
                        Text("Select Click Spot:")
                        clickSpots.forEach { spot ->
                            Button(
                                onClick = { selectedClickSpot = spot.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (selectedClickSpot == spot.id) "✓ ${spot.name}" else spot.name)
                            }
                        }
                    }
                    StepType.SWIPE -> {
                        Text("Select Swipe:")
                        swipes.forEach { swipe ->
                            Button(
                                onClick = { selectedSwipe = swipe.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (selectedSwipe == swipe.id) "✓ ${swipe.name}" else swipe.name)
                            }
                        }
                    }
                    StepType.MACRO -> {
                        Text("Select Macro:")
                        macros.forEach { macro ->
                            Button(
                                onClick = { selectedMacro = macro.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (selectedMacro == macro.id) "✓ ${macro.name}" else macro.name)
                            }
                        }
                    }
                    StepType.IMAGE_DETECTION -> {
                        // Choose detection scope first
                        Text("Detection Scope:")
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = detectionScopeIsGlobal,
                                onClick = { detectionScopeIsGlobal = true }
                            )
                            Text("Global (Full Screen)")
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = !detectionScopeIsGlobal,
                                onClick = { detectionScopeIsGlobal = false }
                            )
                            Text("Region Based")
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Filter templates based on scope
                        val filteredTemplates = if (detectionScopeIsGlobal) {
                            imageTemplates.filter { it.regionId == null }
                        } else {
                            imageTemplates.filter { it.regionId != null }
                        }
                        Text("Select Image Template:")
                        if (filteredTemplates.isEmpty()) {
                            Text(
                                if (detectionScopeIsGlobal) "No global templates. Add them in the 'Image Global' tab."
                                else "No region templates. Add them in the 'Image Region' tab.",
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            filteredTemplates.forEach { template ->
                                Button(
                                    onClick = { selectedImageTemplate = template.id },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (selectedImageTemplate == template.id) "✓ ${template.name}" else template.name)
                                }
                            }
                        }
                    }
                    StepType.OCR_REGION_SCAN -> {
                        Text("Select OCR Region:")
                        ocrRegions.forEach { region ->
                            Button(
                                onClick = { selectedOcrRegion = region.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (selectedOcrRegion == region.id) "✓ ${region.name}" else region.name)
                            }
                        }
                    }
                    StepType.WAIT -> {
                        TextField(
                            value = waitDelay,
                            onValueChange = { waitDelay = it },
                            label = { Text("Delay (ms)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    StepType.REPEAT -> {
                        TextField(
                            value = repeatCount,
                            onValueChange = { repeatCount = it },
                            label = { Text("Repeat Count") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    StepType.CONDITIONAL -> {
                        TextField(
                            value = conditionalPhrase,
                            onValueChange = { conditionalPhrase = it },
                            label = { Text("Phrase to Match") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = jumpToStep,
                            onValueChange = { jumpToStep = it },
                            label = { Text("Jump to Step Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }

                // Success/Failure actions for detection steps
                if (selectedType == StepType.IMAGE_DETECTION || selectedType == StepType.OCR_REGION_SCAN) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("On Success (Detected):")
                    ActionSelector(
                        actionType = successActionType,
                        actionId = successActionId,
                        clickSpots = clickSpots,
                        swipes = swipes,
                        onActionTypeChange = { successActionType = it },
                        onActionIdChange = { successActionId = it }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("On Failure (Not Detected):")
                    ActionSelector(
                        actionType = failureActionType,
                        actionId = failureActionId,
                        clickSpots = clickSpots,
                        swipes = swipes,
                        onActionTypeChange = { failureActionType = it },
                        onActionIdChange = { failureActionId = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Retry settings
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = retryOnFailure,
                        onCheckedChange = { retryOnFailure = it }
                    )
                    Text("Retry on Failure")
                }
                
                if (retryOnFailure) {
                    TextField(
                        value = retryDelay,
                        onValueChange = { retryDelay = it },
                        label = { Text("Retry Delay (ms)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = delayAfter,
                    onValueChange = { delayAfter = it },
                    label = { Text("Delay After (ms)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val newStep = SequenceStep(
                            id = step?.id ?: 0,
                            sequenceId = sequenceId,
                            stepNumber = stepNumber,
                            type = selectedType,
                            targetId = when (selectedType) {
                                StepType.CLICK -> selectedClickSpot
                                StepType.SWIPE -> selectedSwipe
                                StepType.MACRO -> selectedMacro
                                StepType.IMAGE_DETECTION -> selectedImageTemplate
                                StepType.OCR_REGION_SCAN -> selectedOcrRegion
                                else -> null
                            },
                            delay = if (selectedType == StepType.WAIT) waitDelay.toLongOrNull() else null,
                            delayAfter = delayAfter.toLongOrNull() ?: 500,
                            repeatCount = if (selectedType == StepType.REPEAT) repeatCount.toIntOrNull() else null,
                            jumpToStep = if (selectedType == StepType.CONDITIONAL) jumpToStep.toIntOrNull() else null,
                            conditionalPhrase = if (selectedType == StepType.CONDITIONAL) conditionalPhrase else null,
                            successActionType = if (successActionType != "NONE") successActionType else null,
                            successActionId = successActionId,
                            failureActionType = if (failureActionType != "NONE") failureActionType else null,
                            failureActionId = failureActionId,
                            retryOnFailure = retryOnFailure,
                            retryDelay = retryDelay.toLongOrNull() ?: 1000
                        )
                        if (step == null) {
                            db.sequenceStepDao().insert(newStep)
                        } else {
                            db.sequenceStepDao().update(newStep)
                        }
                        onSave()
                    }
                },
                enabled = when (selectedType) {
                    StepType.CLICK -> selectedClickSpot != null
                    StepType.SWIPE -> selectedSwipe != null
                    StepType.MACRO -> selectedMacro != null
                    StepType.IMAGE_DETECTION -> selectedImageTemplate != null
                    StepType.OCR_REGION_SCAN -> selectedOcrRegion != null
                    StepType.CONDITIONAL -> conditionalPhrase.isNotBlank() && jumpToStep.toIntOrNull() != null
                    else -> true
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
}

@Composable
fun ActionSelector(
    actionType: String,
    actionId: Int?,
    clickSpots: List<ClickSpot>,
    swipes: List<Swipe>,
    onActionTypeChange: (String) -> Unit,
    onActionIdChange: (Int?) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = { onActionTypeChange("NONE"); onActionIdChange(null) },
            modifier = Modifier.weight(1f)
        ) {
            Text(if (actionType == "NONE") "✓ None" else "None")
        }
        Button(
            onClick = { onActionTypeChange("CLICK") },
            modifier = Modifier.weight(1f)
        ) {
            Text(if (actionType == "CLICK") "✓ Click" else "Click")
        }
        Button(
            onClick = { onActionTypeChange("SWIPE") },
            modifier = Modifier.weight(1f)
        ) {
            Text(if (actionType == "SWIPE") "✓ Swipe" else "Swipe")
        }
    }
    if (actionType == "CLICK") {
        clickSpots.forEach { spot ->
            Button(
                onClick = { onActionIdChange(spot.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (actionId == spot.id) "✓ ${spot.name}" else spot.name)
            }
        }
    } else if (actionType == "SWIPE") {
        swipes.forEach { swipe ->
            Button(
                onClick = { onActionIdChange(swipe.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (actionId == swipe.id) "✓ ${swipe.name}" else swipe.name)
            }
        }
    }
}
