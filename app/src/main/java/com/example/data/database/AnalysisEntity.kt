package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analyses")
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imagePath: String, // Path to local saved image file
    val overallScore: Int,
    val verdict: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val style: String,
    val mode: String,
    val analysisJson: String // Serialized full AnalysisResult
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Photographer",
    val selectedMode: String = "COACH", // COACH, ROAST, PROFESSIONAL, BEGINNER
    val xp: Int = 0,
    val level: Int = 1,
    val streak: Int = 0,
    val lastAnalysisTimestamp: Long = 0L,
    val isOnboarded: Boolean = false
)
