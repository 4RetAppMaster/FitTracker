package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long? = null,
    val planName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val isCompleted: Boolean = true
)
