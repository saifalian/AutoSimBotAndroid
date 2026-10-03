
package com.example.autosim.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val isDefault: Boolean = false,
    val created: Long = System.currentTimeMillis()
)

@Entity(tableName = "click_spots")
data class ClickSpot(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @androidx.room.ColumnInfo(name = "profileId")
    val profileId: Int = 1, // Default to 1 for migration
    val name: String,
    val x: Int,
    val y: Int,
    val delay: Long = 0,
    val repeat: Int = 1
)

@Entity(tableName = "ocr_regions")
data class OcrRegion(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @androidx.room.ColumnInfo(name = "profileId")
    val profileId: Int = 1,
    val name: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

@Entity(tableName = "ocr_groups")
data class OcrGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val regionId: Int
)

@Entity(tableName = "ocr_phrases")
data class OcrPhrase(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val groupId: Int,
    val text: String,
    val actionId: Int? = null, // ClickSpot id for action (legacy, for CLICK)
    val actionType: String = "CLICK", // CLICK or SWIPE
    val swipeId: Int? = null, // Swipe id if actionType is SWIPE
    val failureActionType: String? = null, // CLICK or SWIPE for when NOT detected
    val failureActionId: Int? = null // ClickSpot or Swipe id for failure
)

@Entity(tableName = "swipes")
data class Swipe(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @androidx.room.ColumnInfo(name = "profileId")
    val profileId: Int = 1,
    val name: String,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val duration: Long = 300
)

@Entity(tableName = "image_templates")
data class ImageTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @androidx.room.ColumnInfo(name = "profileId")
    val profileId: Int = 1,
    val name: String,
    val imagePath: String,
    val threshold: Float = 0.9f,
    val isFullScreen: Boolean = false,
    val regionId: Int? = null // If not full screen, which region to search in
)

@Entity(tableName = "text_detection_phrases")
data class TextDetectionPhrase(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @androidx.room.ColumnInfo(name = "profileId")
    val profileId: Int = 1,
    val text: String,
    val actionId: Int? = null // ClickSpot id for action
)

@Entity(tableName = "sequences")
data class Sequence(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @androidx.room.ColumnInfo(name = "profileId")
    val profileId: Int = 1,
    val name: String,
    val repeatCount: Int = 1, // 1 for one time, -1 for infinite, N for N times
    val isEnabled: Boolean = true,
    val executionOrder: Int = 0,
    val stopAfter: Boolean = false, // Stop Global Runner after this sequence
    val playlistRepeatCount: Int = 1, // How many times this sequence runs within one global loop
    val playlistDelay: Long = 500, // Delay after this sequence in playlist (-1 = use global default, otherwise use this value)
    val isVisualMacro: Boolean = false // Whether this sequence was created via Visual Recorder
)

enum class StepType {
    CLICK, SWIPE, MACRO, IMAGE_DETECTION, OCR_REGION_SCAN, TEXT_DETECTION, WAIT, REPEAT, CONDITIONAL
}

class StepTypeConverter {
    @TypeConverter
    fun fromStepType(value: StepType): String = value.name
    
    @TypeConverter
    fun toStepType(value: String): StepType = StepType.valueOf(value)
}

@Entity(tableName = "sequence_steps")
data class SequenceStep(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sequenceId: Int,
    val stepNumber: Int,
    val type: StepType,
    val targetId: Int? = null, // Can be ClickSpot id, OcrRegion id, ImageTemplate id, etc.
    val delay: Long? = null, // For WAIT step
    val delayAfter: Long = 500, // Delay after this step (0 = no delay, operations work together)
    val repeatCount: Int? = null, // For REPEAT step
    val jumpToStep: Int? = null, // For CONDITIONAL step
    val conditionalPhrase: String? = null, // For CONDITIONAL step
    val successActionType: String? = null, // CLICK or SWIPE when detected
    val successActionId: Int? = null, // ClickSpot or Swipe id for success
    val failureActionType: String? = null, // CLICK or SWIPE when NOT detected
    val failureActionId: Int? = null, // ClickSpot or Swipe id for failure
    // New fields
    val retryOnFailure: Boolean = false,
    val retryDelay: Long = 1000
)

@Entity(tableName = "ocr_region_text_history")
data class OcrRegionTextHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val regionId: Int,
    val recognizedText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "logs_history")
data class LogHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "macros")
data class Macro(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @androidx.room.ColumnInfo(name = "profileId")
    val profileId: Int = 1,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "macro_actions")
data class MacroAction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val macroId: Int,
    val order: Int,
    val type: String, // CLICK, SWIPE, DELAY
    val x: Int = 0,
    val y: Int = 0,
    val endX: Int = 0,
    val endY: Int = 0,
    val duration: Long = 0,
    val delayBefore: Long = 0
)

// DAOs
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAll(): Flow<List<Profile>>
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): Profile?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile): Long
    
    @Update
    suspend fun update(profile: Profile)
    
    @Delete
    suspend fun delete(profile: Profile)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

@Dao
interface ClickSpotDao {
    @Query("SELECT * FROM click_spots WHERE `profileId` = :profileId")
    fun getAll(profileId: Int): Flow<List<ClickSpot>>
    
    @Query("SELECT * FROM click_spots WHERE id = :id")
    suspend fun getById(id: Int): ClickSpot?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clickSpot: ClickSpot): Long
    
    @Update
    suspend fun update(clickSpot: ClickSpot)
    
    @Delete
    suspend fun delete(clickSpot: ClickSpot)
    
    // Delete sequence steps that reference this click spot
    @Query("DELETE FROM sequence_steps WHERE (type = 'CLICK' AND targetId = :clickSpotId) OR successActionId = :clickSpotId OR failureActionId = :clickSpotId")
    suspend fun deleteRelatedSequenceSteps(clickSpotId: Int)

    @Query("DELETE FROM click_spots")
    suspend fun deleteAll()
}

@Dao
interface OcrRegionDao {
    @Query("SELECT * FROM ocr_regions WHERE `profileId` = :profileId")
    fun getAll(profileId: Int): Flow<List<OcrRegion>>
    
    @Query("SELECT * FROM ocr_regions WHERE id = :id")
    suspend fun getById(id: Int): OcrRegion?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(region: OcrRegion): Long
    
    @Update
    suspend fun update(region: OcrRegion)
    
    @Delete
    suspend fun delete(region: OcrRegion)
    
    // Delete sequence steps that reference this OCR region
    @Query("DELETE FROM sequence_steps WHERE type = 'OCR_REGION_SCAN' AND targetId = :regionId")
    suspend fun deleteRelatedSequenceSteps(regionId: Int)

    @Query("DELETE FROM ocr_regions")
    suspend fun deleteAll()
}

@Dao
interface OcrGroupDao {
    @Query("SELECT * FROM ocr_groups WHERE regionId = :regionId")
    fun getGroupsForRegion(regionId: Int): Flow<List<OcrGroup>>
    
    @Query("SELECT * FROM ocr_groups WHERE id = :id")
    suspend fun getById(id: Int): OcrGroup?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: OcrGroup): Long
    
    @Update
    suspend fun update(group: OcrGroup)
    
    @Delete
    suspend fun delete(group: OcrGroup)

    @Query("DELETE FROM ocr_groups")
    suspend fun deleteAll()
}

@Dao
interface OcrPhraseDao {
    @Query("SELECT * FROM ocr_phrases WHERE groupId = :groupId")
    fun getPhrasesForGroup(groupId: Int): Flow<List<OcrPhrase>>
    
    @Query("SELECT * FROM ocr_phrases WHERE id = :id")
    suspend fun getById(id: Int): OcrPhrase?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(phrase: OcrPhrase): Long
    
    @Update
    suspend fun update(phrase: OcrPhrase)
    
    @Delete
    suspend fun delete(phrase: OcrPhrase)

    @Query("DELETE FROM ocr_phrases")
    suspend fun deleteAll()
}

@Dao
interface SwipeDao {
    @Query("SELECT * FROM swipes WHERE `profileId` = :profileId")
    fun getAll(profileId: Int): Flow<List<Swipe>>
    
    @Query("SELECT * FROM swipes WHERE id = :id")
    suspend fun getById(id: Int): Swipe?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(swipe: Swipe): Long
    
    @Update
    suspend fun update(swipe: Swipe)
    
    @Delete
    suspend fun delete(swipe: Swipe)
    
    // Delete sequence steps that reference this swipe
    @Query("DELETE FROM sequence_steps WHERE (type = 'SWIPE' AND targetId = :swipeId) OR successActionId = :swipeId OR failureActionId = :swipeId")
    suspend fun deleteRelatedSequenceSteps(swipeId: Int)

    @Query("DELETE FROM swipes")
    suspend fun deleteAll()
}

@Dao
interface ImageTemplateDao {
    @Query("SELECT * FROM image_templates WHERE `profileId` = :profileId")
    fun getAll(profileId: Int): Flow<List<ImageTemplate>>
    
    @Query("SELECT * FROM image_templates WHERE id = :id")
    suspend fun getById(id: Int): ImageTemplate?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: ImageTemplate): Long
    
    @Update
    suspend fun update(template: ImageTemplate)
    
    @Delete
    suspend fun delete(template: ImageTemplate)
    
    // Delete sequence steps that reference this image template
    @Query("DELETE FROM sequence_steps WHERE type = 'IMAGE_DETECTION' AND targetId = :templateId")
    suspend fun deleteRelatedSequenceSteps(templateId: Int)

    @Query("DELETE FROM image_templates")
    suspend fun deleteAll()
}

@Dao
interface TextDetectionPhraseDao {
    @Query("SELECT * FROM text_detection_phrases WHERE `profileId` = :profileId")
    fun getAll(profileId: Int): Flow<List<TextDetectionPhrase>>

    @Query("SELECT * FROM text_detection_phrases")
    suspend fun getAllNoFlow(): List<TextDetectionPhrase>
    
    @Query("SELECT * FROM text_detection_phrases WHERE id = :id")
    suspend fun getById(id: Int): TextDetectionPhrase?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(phrase: TextDetectionPhrase): Long
    
    @Update
    suspend fun update(phrase: TextDetectionPhrase)
    
    @Delete
    suspend fun delete(phrase: TextDetectionPhrase)

    @Query("DELETE FROM text_detection_phrases")
    suspend fun deleteAll()
}

@Dao
interface SequenceDao {
    @Query("SELECT * FROM sequences WHERE `profileId` = :profileId")
    fun getAll(profileId: Int): Flow<List<Sequence>>
    
    @Query("SELECT * FROM sequences WHERE id = :id")
    suspend fun getById(id: Int): Sequence?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sequence: Sequence): Long
    
    @Update
    suspend fun update(sequence: Sequence)
    
    @Delete
    suspend fun delete(sequence: Sequence)

    @Query("DELETE FROM sequences")
    suspend fun deleteAll()
}

@Dao
interface SequenceStepDao {
    @Query("SELECT * FROM sequence_steps WHERE sequenceId = :sequenceId ORDER BY stepNumber")
    fun getStepsForSequence(sequenceId: Int): Flow<List<SequenceStep>>
    
    @Query("SELECT * FROM sequence_steps WHERE id = :id")
    suspend fun getById(id: Int): SequenceStep?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: SequenceStep): Long
    
    @Update
    suspend fun update(step: SequenceStep)
    
    @Delete
    suspend fun delete(step: SequenceStep)

    @Query("DELETE FROM sequence_steps")
    suspend fun deleteAll()
}

@Dao
interface OcrRegionTextHistoryDao {
    @Query("SELECT * FROM ocr_region_text_history WHERE regionId = :regionId ORDER BY timestamp DESC")
    fun getHistoryForRegion(regionId: Int): Flow<List<OcrRegionTextHistory>>
    
    @Insert
    suspend fun insert(history: OcrRegionTextHistory)
    
    @Query("DELETE FROM ocr_region_text_history WHERE regionId = :regionId")
    suspend fun deleteForRegion(regionId: Int)
}

@Dao
interface LogHistoryDao {
    @Query("SELECT * FROM logs_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LogHistory>>
    
    @Insert
    suspend fun insert(log: LogHistory)
    
    @Query("DELETE FROM logs_history")
    suspend fun deleteAll()
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros WHERE `profileId` = :profileId")
    fun getAll(profileId: Int): Flow<List<Macro>>
    
    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getById(id: Int): Macro?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(macro: Macro): Long
    
    @Update
    suspend fun update(macro: Macro)
    
    @Delete
    suspend fun delete(macro: Macro)

    @Query("DELETE FROM macros")
    suspend fun deleteAll()
}

@Dao
interface MacroActionDao {
    @Query("SELECT * FROM macro_actions WHERE macroId = :macroId ORDER BY `order` ASC")
    fun getActionsForMacro(macroId: Int): Flow<List<MacroAction>>
    
    @Query("SELECT * FROM macro_actions WHERE macroId = :macroId ORDER BY `order` ASC")
    suspend fun getActionsForMacroSync(macroId: Int): List<MacroAction>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: MacroAction): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actions: List<MacroAction>)
    
    @Query("DELETE FROM macro_actions WHERE macroId = :macroId")
    suspend fun deleteForMacro(macroId: Int)

    @Query("DELETE FROM macro_actions")
    suspend fun deleteAll()
}

@Database(
    entities = [
        Profile::class,
        ClickSpot::class,
        OcrRegion::class,
        OcrGroup::class,
        OcrPhrase::class,
        Swipe::class,
        ImageTemplate::class,
        TextDetectionPhrase::class,
        Sequence::class,
        SequenceStep::class,
        OcrRegionTextHistory::class,
        LogHistory::class,
        Macro::class,
        MacroAction::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(StepTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun clickSpotDao(): ClickSpotDao
    abstract fun ocrRegionDao(): OcrRegionDao
    abstract fun ocrGroupDao(): OcrGroupDao
    abstract fun ocrPhraseDao(): OcrPhraseDao
    abstract fun swipeDao(): SwipeDao
    abstract fun imageTemplateDao(): ImageTemplateDao
    abstract fun textDetectionPhraseDao(): TextDetectionPhraseDao
    abstract fun sequenceDao(): SequenceDao
    abstract fun sequenceStepDao(): SequenceStepDao
    abstract fun ocrRegionTextHistoryDao(): OcrRegionTextHistoryDao
    abstract fun logHistoryDao(): LogHistoryDao
    abstract fun macroDao(): MacroDao
    abstract fun macroActionDao(): MacroActionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autosim_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
