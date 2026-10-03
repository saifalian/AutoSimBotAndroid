package com.example.autosim.engine

import android.util.Log
import com.example.autosim.accessibility.AutomationAccessibilityService
import com.example.autosim.db.AppDatabase
import com.example.autosim.db.LogHistory
import com.example.autosim.db.OcrRegionTextHistory
import com.example.autosim.db.StepType
import android.graphics.BitmapFactory
import com.example.autosim.overlay.fromCanonical
import com.example.autosim.utils.ImageMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object SequenceRunner {
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()
    
    private val _logs = MutableStateFlow<LogHistory?>(null)
    val logs = _logs.asStateFlow()

    private var job: Job? = null
    private var accessibilityService: AutomationAccessibilityService? = null
    private var db: AppDatabase? = null
    private val runnerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun setAccessibilityService(service: AutomationAccessibilityService) {
        accessibilityService = service
    }

    fun setDatabase(database: AppDatabase) {
        db = database
    }

    suspend fun requiresScreenCapture(sequenceId: Int): Boolean {
        val steps = db?.sequenceStepDao()?.getStepsForSequence(sequenceId)?.first() ?: emptyList()
        return steps.any { 
            it.type == StepType.OCR_REGION_SCAN || 
            it.type == StepType.IMAGE_DETECTION || 
            it.type == StepType.TEXT_DETECTION 
        }
    }

    fun startSequence(sequenceId: Int) {
        if (_isRunning.value) return
        Log.i("SequenceRunner", "Starting sequence with ID: $sequenceId")
        
        job = runnerScope.launch {
            _isRunning.value = true
            log("Sequence started.", "SUCCESS")

            try {
                val sequence = db?.sequenceDao()?.getById(sequenceId)
                if (sequence == null) {
                    log("Sequence with ID $sequenceId not found.", "ERROR")
                    return@launch
                }
                
                executeSequenceInternal(sequence)
            } catch (e: Exception) {
                log("Error during sequence execution: ${e.message}", "ERROR")
            } finally {
                _isRunning.value = false
                log("Sequence finished.", "SUCCESS")
            }
        }
    }

    fun pause() {
        _isPaused.value = true
        log("Sequence paused.")
    }

    fun resume() {
        _isPaused.value = false
        log("Sequence resumed.")
    }

    fun stop() {
        stopSequence()
    }
    
    fun stopSequence() {
        _isRunning.value = false
        _isPaused.value = false
        job?.cancel()
        job = null
        log("Sequence execution stopped.", "SUCCESS")
    }
    
    fun runPlaylist(
        context: android.content.Context,
        sequences: List<com.example.autosim.db.Sequence>,
        globalLoopCount: Int,
        onComplete: () -> Unit
    ) {
        if (_isRunning.value) return
        Log.i("SequenceRunner", "Starting playlist with ${sequences.size} sequences, loop: $globalLoopCount")
        
        job = runnerScope.launch {
            _isRunning.value = true
            log("Playlist started. Global Loop: ${if (globalLoopCount == -1) "Infinite" else globalLoopCount}", "SUCCESS")
            
            try {
                // Track total runs for each sequence across ALL global loops
                val sequenceTotalRuns = mutableMapOf<Int, Int>()
                sequences.forEach { sequenceTotalRuns[it.id] = 0 }
                
                var currentGlobalLoop = 0
                while (_isRunning.value && (globalLoopCount == -1 || currentGlobalLoop < globalLoopCount)) {
                    log("Starting Global Loop #${currentGlobalLoop + 1}")
                    
                    for (sequence in sequences) {
                        if (!_isRunning.value) break
                        if (!sequence.isEnabled) continue
                        
                        val playlistRepeats = sequence.playlistRepeatCount
                        val totalRuns = sequenceTotalRuns[sequence.id] ?: 0
                        
                        // If playlistRepeatCount is 0, run once per global loop
                        if (playlistRepeats == 0) {
                            log("Processing Sequence: ${sequence.name} (Global Loop Mode)")
                            executeSequenceInternal(sequence)
                        } else {
                            // Check if this sequence has already completed its total runs
                            if (totalRuns >= playlistRepeats) {
                                log("Skipping Sequence: ${sequence.name} (Already completed $totalRuns/$playlistRepeats runs)")
                                continue
                            }
                            
                            log("Processing Sequence: ${sequence.name} (Total Runs: $totalRuns/$playlistRepeats)")
                            executeSequenceInternal(sequence)
                            sequenceTotalRuns[sequence.id] = totalRuns + 1
                        }
                        
                        if (sequence.stopAfter) {
                            log("Stopping playlist after sequence: ${sequence.name} (Stop After enabled)", "SUCCESS")
                            _isRunning.value = false
                            break
                        }
                        
                        // Apply delay between sequences
                        val delayMs = if (sequence.playlistDelay != -1L) sequence.playlistDelay else com.example.autosim.utils.GlobalSettings.globalSequenceDelay
                        if (delayMs > 0) {
                            log("Waiting ${delayMs}ms before next sequence...")
                            delay(delayMs)
                        }
                    }
                    
                    currentGlobalLoop++
                }
            } catch (e: Exception) {
                log("Error during playlist execution: ${e.message}", "ERROR")
            } finally {
                _isRunning.value = false
                log("Playlist finished.", "SUCCESS")
                launch(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }
    
    private suspend fun executeSequenceInternal(sequence: com.example.autosim.db.Sequence) {
        val steps = db?.sequenceStepDao()?.getStepsForSequence(sequence.id)?.first() ?: emptyList()
        var repeatCount = sequence.repeatCount
        
        log("Executing sequence '${sequence.name}' with repeat count: ${if (repeatCount == -1) "Infinite" else repeatCount}")

        while (_isRunning.value && (repeatCount == -1 || repeatCount > 0)) {
            var i = 0
            while (i < steps.size) {
                if (!_isRunning.value) break
                
                while (_isPaused.value) {
                    delay(100)
                }
                
                val step = steps[i]
                log("Executing step ${step.stepNumber}: ${step.type}")
                
                // Retry loop
                var success = false
                while (true) {
                    if (!_isRunning.value) break
                    
                    success = executeStep(step)
                    
                    if (success) {
                        break
                    } else {
                        if (step.retryOnFailure) {
                            log("Step failed. Retrying in ${step.retryDelay}ms...", "WARN")
                            delay(step.retryDelay)
                        } else {
                            break
                        }
                    }
                }
                
                // Use the custom delay if available, 0 means no delay (operations work together)
                val delayAmount = step.delayAfter
                if (delayAmount > 0) {
                    log("Waiting for ${delayAmount}ms")
                    delay(delayAmount)
                } else {
                    log("No delay (operations work together)")
                }
                i++
            }
            if (repeatCount > 0) {
                repeatCount--
            }
        }
    }
    
    private suspend fun executeStep(step: com.example.autosim.db.SequenceStep): Boolean {
        return when (step.type) {
            StepType.CLICK -> {
                val clickSpotId = step.targetId
                if (clickSpotId != null) {
                    log("Looking for click spot with ID: $clickSpotId")
                    val spot = db?.clickSpotDao()?.getById(clickSpotId)
                    if (spot != null) {
                        log("Click spot found: ${spot.name}")
                        if (accessibilityService != null) {
                            // Convert canonical coordinates to screen coordinates before tapping
                            val (screenX, screenY) = fromCanonical(accessibilityService!!, spot.x, spot.y)
                            val success = accessibilityService?.performTap(screenX.toInt(), screenY.toInt())
                            if (success == true) {
                                log("Click successful at ($screenX, $screenY)")
                                true
                            } else {
                                log("Click failed", "ERROR")
                                false
                            }
                        } else {
                            log("Accessibility service is not available.", "ERROR")
                            false
                        }
                    } else {
                        log("Click spot with ID $clickSpotId not found.", "ERROR")
                        false
                    }
                } else {
                    false
                }
            }
            StepType.SWIPE -> {
                val swipeId = step.targetId
                if (swipeId != null) {
                    log("Looking for swipe with ID: $swipeId")
                    val swipe = db?.swipeDao()?.getById(swipeId)
                    if (swipe != null) {
                        log("Executing swipe: ${swipe.name}")
                        if (accessibilityService != null) {
                            val (startX, startY) = fromCanonical(accessibilityService!!, swipe.startX, swipe.startY)
                            val (endX, endY) = fromCanonical(accessibilityService!!, swipe.endX, swipe.endY)
                            val success = accessibilityService?.performSwipe(
                                startX.toInt(), startY.toInt(), 
                                endX.toInt(), endY.toInt(), 
                                swipe.duration
                            )
                            if (success == true) {
                                log("Swipe successful", "SUCCESS")
                                true
                            } else {
                                log("Swipe failed", "ERROR")
                                false
                            }
                        } else {
                            log("Accessibility service not available", "ERROR")
                            false
                        }
                    } else {
                        log("Swipe with ID $swipeId not found", "ERROR")
                        false
                    }
                } else {
                    false
                }
            }
            StepType.MACRO -> {
                val macroId = step.targetId
                if (macroId != null) {
                    log("Looking for macro with ID: $macroId")
                    val macro = db?.macroDao()?.getById(macroId)
                    if (macro != null) {
                        log("Executing macro: ${macro.name}")
                        val actions = db?.macroActionDao()?.getActionsForMacroSync(macroId) ?: emptyList()
                        log("Found ${actions.size} actions in macro")
                        
                        var allSuccess = true
                        for (action in actions) {
                            if (!_isRunning.value) {
                                allSuccess = false
                                break
                            }
                            
                            if (action.delayBefore > 0) {
                                delay(action.delayBefore)
                            }
                            
                            when (action.type) {
                                "CLICK" -> {
                                    if (accessibilityService != null) {
                                        val success = accessibilityService?.performTap(action.x, action.y)
                                        if (success != true) allSuccess = false
                                    }
                                }
                                "SWIPE" -> {
                                    if (accessibilityService != null) {
                                        val success = accessibilityService?.performSwipe(
                                            action.x, action.y,
                                            action.endX, action.endY,
                                            action.duration
                                        )
                                        if (success != true) allSuccess = false
                                    }
                                }
                            }
                        }
                        
                        if (allSuccess) {
                            log("Macro executed successfully", "SUCCESS")
                            true
                        } else {
                            log("Macro execution failed or interrupted", "ERROR")
                            false
                        }
                    } else {
                        log("Macro with ID $macroId not found", "ERROR")
                        false
                    }
                } else {
                    false
                }
            }
            StepType.IMAGE_DETECTION -> {
                val templateId = step.targetId
                if (templateId != null) {
                    log("Looking for image template with ID: $templateId")
                    val template = db?.imageTemplateDao()?.getById(templateId)
                    if (template != null) {
                        log("Searching for image: ${template.name} (threshold: ${(template.threshold * 100).toInt()}%)")
                        val rawScreenBitmap = ScreenCaptureService.getLatestBitmap()
                        if (rawScreenBitmap != null) {
                            log("Raw screen bitmap size: ${rawScreenBitmap.width}x${rawScreenBitmap.height}")
                            
                            // Crop to actual screen size (in case VirtualDisplay is larger)
                            val metrics = accessibilityService?.resources?.displayMetrics
                            val screenBitmap = if (metrics != null && 
                                (rawScreenBitmap.width > metrics.widthPixels || rawScreenBitmap.height > metrics.heightPixels)) {
                                log("Cropping to actual screen size: ${metrics.widthPixels}x${metrics.heightPixels}")
                                android.graphics.Bitmap.createBitmap(
                                    rawScreenBitmap, 
                                    0, 0, 
                                    metrics.widthPixels, 
                                    metrics.heightPixels
                                )
                            } else {
                                rawScreenBitmap
                            }
                            
                            var templateBitmap = BitmapFactory.decodeFile(template.imagePath)
                            if (templateBitmap != null) {
                                log("Template bitmap size: ${templateBitmap.width}x${templateBitmap.height}")
                                
                                var searchBitmap = screenBitmap
                                var offsetX = 0
                                var offsetY = 0
                                var shouldRecycleSearch = false
                                
                                if (template.regionId != null) {
                                    val region = db?.ocrRegionDao()?.getById(template.regionId)
                                    if (region != null) {
                                        log("Searching in region: ${region.name} (${region.left},${region.top},${region.width}x${region.height})")
                                        val cropped = ScreenCaptureService.cropBitmapForRegion(
                                            screenBitmap,
                                            region.left, region.top,
                                            region.width, region.height
                                        )
                                        if (cropped != null) {
                                            searchBitmap = cropped
                                            offsetX = region.left
                                            offsetY = region.top
                                            shouldRecycleSearch = true
                                            log("Region cropped bitmap size: ${searchBitmap.width}x${searchBitmap.height}")
                                        } else {
                                            log("Failed to crop region", "ERROR")
                                        }
                                    }
                                } else {
                                    log("Searching full screen")
                                }
                                
                                // Check if template is larger than search area and resize if needed
                                var shouldRecycleTemplate = false
                                if (templateBitmap.width > searchBitmap.width || templateBitmap.height > searchBitmap.height) {
                                    log("Template is larger than search area, resizing to fit", "WARN")
                                    val scale = kotlin.math.min(
                                        searchBitmap.width.toFloat() / templateBitmap.width,
                                        searchBitmap.height.toFloat() / templateBitmap.height
                                    )
                                    val newWidth = (templateBitmap.width * scale).toInt()
                                    val newHeight = (templateBitmap.height * scale).toInt()
                                    log("Resizing template from ${templateBitmap.width}x${templateBitmap.height} to ${newWidth}x${newHeight}")
                                    val resized = android.graphics.Bitmap.createScaledBitmap(templateBitmap, newWidth, newHeight, true)
                                    templateBitmap.recycle()
                                    templateBitmap = resized
                                    shouldRecycleTemplate = true
                                }
                                
                                val match = ImageMatcher.findTemplate(searchBitmap, templateBitmap, template.threshold.toDouble())
                                val result = if (match != null) {
                                    log("✓ Image found at (${match.x + offsetX}, ${match.y + offsetY})", "SUCCESS")
                                    // Calculate center of match
                                    val centerX = match.x + offsetX + (templateBitmap.width / 2)
                                    val centerY = match.y + offsetY + (templateBitmap.height / 2)
                                    
                                    // Execute success action
                                    executeAction(step.successActionType, step.successActionId, "Image Detection Success", centerX, centerY)
                                    true
                                } else {
                                    log("✗ Image not found (check ImageMatcher logs for details)", "WARN")
                                    // Execute failure action
                                    executeAction(step.failureActionType, step.failureActionId, "Image Detection Failure")
                                    false
                                }
                                
                                if (shouldRecycleTemplate) templateBitmap.recycle()
                                if (shouldRecycleSearch) searchBitmap.recycle()
                                if (screenBitmap != rawScreenBitmap) screenBitmap.recycle()
                                
                                result
                            } else {
                                log("Failed to load template image from ${template.imagePath}", "ERROR")
                                false
                            }
                        } else {
                            log("Screen capture not available", "ERROR")
                            false
                        }
                    } else {
                        log("Image template with ID $templateId not found", "ERROR")
                        false
                    }
                } else {
                    false
                }
            }
            StepType.OCR_REGION_SCAN -> { 
                val regionId = step.targetId
                if (regionId != null) {
                    log("Looking for OCR region with ID: $regionId")
                    val region = db?.ocrRegionDao()?.getById(regionId)
                    if (region != null) {
                        log("OCR region found: ${region.name}")
                        
                        // Get latest screen capture
                        val screenBitmap = ScreenCaptureService.getLatestBitmap()
                        if (screenBitmap != null) {
                            log("Screen captured, cropping to region bounds")
                            
                            // Crop to region
                            val croppedBitmap = ScreenCaptureService.cropBitmapForRegion(
                                screenBitmap,
                                region.left,
                                region.top,
                                region.width,
                                region.height
                            )
                            
                            if (croppedBitmap != null) {
                                log("Performing OCR on region")
                                val recognizedText = OcrProcessor.recognizeBitmap(croppedBitmap)
                                log("Recognized text: $recognizedText")
                                
                                // Save to history
                                db?.ocrRegionTextHistoryDao()?.insert(
                                    OcrRegionTextHistory(
                                        regionId = region.id,
                                        recognizedText = recognizedText
                                    )
                                )
                                
                                // Get all groups for this region
                                val groups = db?.ocrGroupDao()?.getGroupsForRegion(region.id)?.first() ?: emptyList()
                                log("Found ${groups.size} groups for region")
                                
                                // Check each group's phrases
                                var matchFound = false
                                for (group in groups) {
                                    if (matchFound) break
                                    
                                    val phrases = db?.ocrPhraseDao()?.getPhrasesForGroup(group.id)?.first() ?: emptyList()
                                    log("Checking ${phrases.size} phrases in group: ${group.name}")
                                    
                                    for (phrase in phrases) {
                                        if (recognizedText.contains(phrase.text, ignoreCase = true)) {
                                            log("Phrase matched: '${phrase.text}'", "SUCCESS")
                                            
                                            // Execute success action based on actionType
                                            when (phrase.actionType) {
                                                "CLICK" -> {
                                                    executeAction("CLICK", phrase.actionId, "OCR Match")
                                                    matchFound = true
                                                    break
                                                }
                                                "SWIPE" -> {
                                                    executeAction("SWIPE", phrase.swipeId, "OCR Match")
                                                    matchFound = true
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                if (!matchFound) {
                                    log("No matching phrases found in recognized text")
                                    // Execute failure action if any phrase has one
                                    val firstPhrase = groups.firstOrNull()?.let { group ->
                                        db?.ocrPhraseDao()?.getPhrasesForGroup(group.id)?.first()?.firstOrNull()
                                    }
                                    if (firstPhrase != null) {
                                        executeAction(firstPhrase.failureActionType, firstPhrase.failureActionId, "OCR No Match")
                                    }
                                }
                                
                                croppedBitmap.recycle()
                                matchFound
                            } else {
                                log("Failed to crop bitmap for region", "ERROR")
                                false
                            }
                        } else {
                            log("⚠️ Screen Capture Not Available", "ERROR")
                            log("To use OCR scanning, you must enable screen capture:", "ERROR")
                            log("1. Go to Settings tab", "ERROR")
                            log("2. Tap 'Request Screen Capture Permission'", "ERROR")
                            log("3. Allow the permission", "ERROR")
                            log("4. Run your sequence again", "ERROR")
                            false
                        }
                    } else {
                        log("OCR region with ID $regionId not found", "ERROR")
                        false
                    }
                } else {
                    log("No region ID specified for OCR_REGION_SCAN step", "ERROR")
                    false
                }
            }
            StepType.TEXT_DETECTION -> {
                log("Starting Global Text Detection")
                
                // Get latest screen capture
                val screenBitmap = ScreenCaptureService.getLatestBitmap()
                if (screenBitmap != null) {
                    log("Screen captured for text detection")
                    
                    // Perform OCR on full screen
                    val recognizedText = OcrProcessor.recognizeBitmap(screenBitmap)
                    log("Recognized text: $recognizedText")
                    
                    val phrases = db?.textDetectionPhraseDao()?.getAllNoFlow() ?: emptyList()
                    log("Checking against ${phrases.size} phrases")
                    
                    var matchFound = false
                    for (phrase in phrases) {
                        if (recognizedText.contains(phrase.text, ignoreCase = true)) {
                            log("Phrase matched: '${phrase.text}'", "SUCCESS")
                            matchFound = true
                            
                            // Execute action if defined
                            if (phrase.actionId != null) {
                                executeAction("CLICK", phrase.actionId, "Text Detection Match")
                            }
                            break
                        }
                    }
                    
                    if (!matchFound) {
                        log("No matching text phrases found")
                    }
                    
                    matchFound
                } else {
                    log("⚠️ Screen Capture Not Available", "ERROR")
                    log("To use Text Detection, you must enable screen capture in Settings.", "ERROR")
                    false
                }
            }
            StepType.WAIT -> {
                step.delay?.let { delay(it) }
                true
            }
            StepType.REPEAT -> {
                // TODO: Implement repeat
                true
            }
            StepType.CONDITIONAL -> {
                // TODO: Implement conditional
                true
            }
        }
    }
    
    private fun log(message: String, type: String = "INFO") {
        runnerScope.launch {
            val formattedMessage = if (type == "INFO") message else "[$type] $message"
            val logEntry = LogHistory(timestamp = System.currentTimeMillis(), message = formattedMessage)
            db?.logHistoryDao()?.insert(logEntry)
            _logs.value = logEntry
        }
    }
    
    private suspend fun executeAction(actionType: String?, actionId: Int?, context: String, matchX: Int = 0, matchY: Int = 0) {
        if (actionType == null) return
        
        when (actionType) {
            "CLICK" -> {
                if (actionId == null) return
                val clickSpot = db?.clickSpotDao()?.getById(actionId)
                if (clickSpot != null) {
                    log("$context: Executing click action: ${clickSpot.name}")
                    if (accessibilityService != null) {
                        val (screenX, screenY) = fromCanonical(accessibilityService!!, clickSpot.x, clickSpot.y)
                        val success = accessibilityService?.performTap(screenX.toInt(), screenY.toInt())
                        if (success == true) {
                            log("$context: Click successful at ($screenX, $screenY)", "SUCCESS")
                        } else {
                            log("$context: Click failed", "ERROR")
                        }
                    } else {
                        log("Accessibility service not available", "ERROR")
                    }
                } else {
                    log("$context: Click spot with ID $actionId not found", "ERROR")
                }
            }
            "CLICK_MATCH" -> {
                log("$context: Executing click on match at ($matchX, $matchY)")
                if (accessibilityService != null) {
                    val success = accessibilityService?.performTap(matchX, matchY)
                    if (success == true) {
                        log("$context: Click successful", "SUCCESS")
                    } else {
                        log("$context: Click failed", "ERROR")
                    }
                } else {
                    log("Accessibility service not available", "ERROR")
                }
            }
            "SWIPE" -> {
                if (actionId == null) return
                val swipe = db?.swipeDao()?.getById(actionId)
                if (swipe != null) {
                    log("$context: Executing swipe action: ${swipe.name}")
                    if (accessibilityService != null) {
                        val (startX, startY) = fromCanonical(accessibilityService!!, swipe.startX, swipe.startY)
                        val (endX, endY) = fromCanonical(accessibilityService!!, swipe.endX, swipe.endY)
                        val success = accessibilityService?.performSwipe(
                            startX.toInt(), startY.toInt(),
                            endX.toInt(), endY.toInt(),
                            swipe.duration
                        )
                        if (success == true) {
                            log("$context: Swipe successful", "SUCCESS")
                        } else {
                            log("$context: Swipe failed", "ERROR")
                        }
                    } else {
                        log("Accessibility service not available", "ERROR")
                    }
                } else {
                    log("$context: Swipe with ID $actionId not found", "ERROR")
                }
            }
            "SWIPE_MATCH" -> {
                if (actionId == null) return
                val swipe = db?.swipeDao()?.getById(actionId)
                if (swipe != null) {
                    log("$context: Executing swipe relative to match")
                    if (accessibilityService != null) {
                        // Swipe vector is (endX, endY) because startX, startY are 0
                        // But we should respect swipe.startX/Y if they are offsets?
                        // In recording, we set startX=0, startY=0, endX=vecX, endY=vecY
                        // So start point is matchX, matchY
                        // End point is matchX + endX, matchY + endY
                        
                        val startX = matchX + swipe.startX
                        val startY = matchY + swipe.startY
                        val endX = matchX + swipe.endX
                        val endY = matchY + swipe.endY
                        
                        val success = accessibilityService?.performSwipe(
                            startX, startY,
                            endX, endY,
                            swipe.duration
                        )
                        if (success == true) {
                            log("$context: Swipe successful", "SUCCESS")
                        } else {
                            log("$context: Swipe failed", "ERROR")
                        }
                    } else {
                        log("Accessibility service not available", "ERROR")
                    }
                } else {
                    log("$context: Swipe with ID $actionId not found", "ERROR")
                }
            }
        }
    }
}
