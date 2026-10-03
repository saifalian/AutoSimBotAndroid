package com.example.autosim.utils

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.autosim.db.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ImportExportManager {

    data class ExportData(
        val profiles: List<Profile>,
        val clickSpots: List<ClickSpot>,
        val ocrRegions: List<OcrRegion>,
        val ocrGroups: List<OcrGroup>,
        val ocrPhrases: List<OcrPhrase>,
        val swipes: List<Swipe>,
        val imageTemplates: List<ImageTemplate>,
        val textDetectionPhrases: List<TextDetectionPhrase>,
        val sequences: List<Sequence>,
        val sequenceSteps: List<SequenceStep>
    )

    suspend fun exportData(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                
                val profiles = db.profileDao().getAll().first()
                val allClickSpots = mutableListOf<ClickSpot>()
                val allOcrRegions = mutableListOf<OcrRegion>()
                val allSwipes = mutableListOf<Swipe>()
                val allImageTemplates = mutableListOf<ImageTemplate>()
                val allTextDetectionPhrases = mutableListOf<TextDetectionPhrase>()
                val allSequences = mutableListOf<Sequence>()
                
                for (profile in profiles) {
                    allClickSpots.addAll(db.clickSpotDao().getAll(profile.id).first())
                    allOcrRegions.addAll(db.ocrRegionDao().getAll(profile.id).first())
                    allSwipes.addAll(db.swipeDao().getAll(profile.id).first())
                    allImageTemplates.addAll(db.imageTemplateDao().getAll(profile.id).first())
                    allTextDetectionPhrases.addAll(db.textDetectionPhraseDao().getAll(profile.id).first())
                    allSequences.addAll(db.sequenceDao().getAll(profile.id).first())
                }
                
                val allOcrGroups = mutableListOf<OcrGroup>()
                val allOcrPhrases = mutableListOf<OcrPhrase>()
                val allSequenceSteps = mutableListOf<SequenceStep>()
                
                for (region in allOcrRegions) {
                    val groups = db.ocrGroupDao().getGroupsForRegion(region.id).first()
                    allOcrGroups.addAll(groups)
                    for (group in groups) {
                        allOcrPhrases.addAll(db.ocrPhraseDao().getPhrasesForGroup(group.id).first())
                    }
                }
                
                for (sequence in allSequences) {
                    allSequenceSteps.addAll(db.sequenceStepDao().getStepsForSequence(sequence.id).first())
                }

                val exportData = ExportData(
                    profiles = profiles,
                    clickSpots = allClickSpots,
                    ocrRegions = allOcrRegions,
                    ocrGroups = allOcrGroups,
                    ocrPhrases = allOcrPhrases,
                    swipes = allSwipes,
                    imageTemplates = allImageTemplates,
                    textDetectionPhrases = allTextDetectionPhrases,
                    sequences = allSequences,
                    sequenceSteps = allSequenceSteps
                )

                val gson = Gson()
                val json = gson.toJson(exportData)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun importData(context: Context, uri: Uri, replaceExisting: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            sb.append(line)
                            line = reader.readLine()
                        }
                    }
                }

                val gson = Gson()
                val exportData = gson.fromJson(sb.toString(), ExportData::class.java) ?: return@withContext false

                val db = AppDatabase.getDatabase(context)
                val currentProfileId = GlobalSettings.currentProfileId.value
                
                // Using withTransaction for atomic operation
                db.withTransaction {
                    // If replace mode, delete existing data for current profile
                    if (replaceExisting) {
                        // Get all items for current profile and delete them
                        val existingSequences = db.sequenceDao().getAll(currentProfileId).first()
                        existingSequences.forEach { sequence ->
                            db.sequenceStepDao().getStepsForSequence(sequence.id).first().forEach { step ->
                                db.sequenceStepDao().delete(step)
                            }
                            db.sequenceDao().delete(sequence)
                        }
                        
                        val existingRegions = db.ocrRegionDao().getAll(currentProfileId).first()
                        existingRegions.forEach { region ->
                            db.ocrGroupDao().getGroupsForRegion(region.id).first().forEach { group ->
                                db.ocrPhraseDao().getPhrasesForGroup(group.id).first().forEach { phrase ->
                                    db.ocrPhraseDao().delete(phrase)
                                }
                                db.ocrGroupDao().delete(group)
                            }
                            db.ocrRegionDao().delete(region)
                        }
                        
                        db.clickSpotDao().getAll(currentProfileId).first().forEach { db.clickSpotDao().delete(it) }
                        db.swipeDao().getAll(currentProfileId).first().forEach { db.swipeDao().delete(it) }
                        db.imageTemplateDao().getAll(currentProfileId).first().forEach { db.imageTemplateDao().delete(it) }
                        db.textDetectionPhraseDao().getAll(currentProfileId).first().forEach { db.textDetectionPhraseDao().delete(it) }
                    }
                    
                    // Import strategy: Merge into current profile
                    // We'll remap all profile IDs to the current profile
                    
                    // Import click spots
                    exportData.clickSpots.forEach { spot ->
                        val newSpot = spot.copy(id = 0, profileId = currentProfileId)
                        db.clickSpotDao().insert(newSpot)
                    }
                    
                    // Import swipes
                    exportData.swipes.forEach { swipe ->
                        val newSwipe = swipe.copy(id = 0, profileId = currentProfileId)
                        db.swipeDao().insert(newSwipe)
                    }
                    
                    // Import OCR regions with groups and phrases
                    val regionIdMap = mutableMapOf<Int, Int>()
                    exportData.ocrRegions.forEach { region ->
                        val newRegion = region.copy(id = 0, profileId = currentProfileId)
                        val newId = db.ocrRegionDao().insert(newRegion).toInt()
                        regionIdMap[region.id] = newId
                    }
                    
                    // Import OCR groups
                    val groupIdMap = mutableMapOf<Int, Int>()
                    exportData.ocrGroups.forEach { group ->
                        val newRegionId = regionIdMap[group.regionId] ?: return@forEach
                        val newGroup = group.copy(id = 0, regionId = newRegionId)
                        val newId = db.ocrGroupDao().insert(newGroup).toInt()
                        groupIdMap[group.id] = newId
                    }
                    
                    // Import OCR phrases
                    exportData.ocrPhrases.forEach { phrase ->
                        val newGroupId = groupIdMap[phrase.groupId] ?: return@forEach
                        val newPhrase = phrase.copy(id = 0, groupId = newGroupId)
                        db.ocrPhraseDao().insert(newPhrase)
                    }
                    
                    // Import image templates
                    exportData.imageTemplates.forEach { template ->
                        val newRegionId = template.regionId?.let { regionIdMap[it] }
                        val newTemplate = template.copy(id = 0, profileId = currentProfileId, regionId = newRegionId)
                        db.imageTemplateDao().insert(newTemplate)
                    }
                    
                    // Import text detection phrases
                    exportData.textDetectionPhrases.forEach { phrase ->
                        val newPhrase = phrase.copy(id = 0, profileId = currentProfileId)
                        db.textDetectionPhraseDao().insert(newPhrase)
                    }
                    
                    // Import sequences
                    val sequenceIdMap = mutableMapOf<Int, Int>()
                    exportData.sequences.forEach { sequence ->
                        val newSequence = sequence.copy(id = 0, profileId = currentProfileId)
                        val newId = db.sequenceDao().insert(newSequence).toInt()
                        sequenceIdMap[sequence.id] = newId
                    }
                    
                    // Import sequence steps
                    exportData.sequenceSteps.forEach { step ->
                        val newSequenceId = sequenceIdMap[step.sequenceId] ?: return@forEach
                        val newStep = step.copy(id = 0, sequenceId = newSequenceId)
                        db.sequenceStepDao().insert(newStep)
                    }
                }
                
                return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
