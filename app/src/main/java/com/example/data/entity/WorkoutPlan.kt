package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "workout_plans")
data class WorkoutPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val iconName: String = "FitnessCenter",
    val colorHex: String = "#FF6B00",
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

