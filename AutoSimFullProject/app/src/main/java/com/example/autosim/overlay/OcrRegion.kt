package com.example.autosim.overlay

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_regions")
data class OcrRegion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)