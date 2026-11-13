package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String, // e.g. "Chest", "Back", "Legs", "Shoulders", "Arms", "Core"
    val equipment: String = "Barbell", // "Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight"
    val isCustom: Boolean = false,
    val defaultNotes: String = ""
)
