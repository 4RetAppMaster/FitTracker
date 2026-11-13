package com.example.data.dao

import androidx.room.*
import com.example.data.entity.WorkoutSession
import com.example.data.entity.WorkoutSetLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<WorkoutSession>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLog(log: WorkoutSetLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLogs(logs: List<WorkoutSetLog>)

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessionsList(): List<WorkoutSession>

    @Query("SELECT * FROM workout_set_logs ORDER BY timestamp DESC")
    fun getAllSetLogs(): Flow<List<WorkoutSetLog>>

    @Query("SELECT * FROM workout_set_logs ORDER BY timestamp DESC")
    suspend fun getAllSetLogsList(): List<WorkoutSetLog>

    @Query("SELECT * FROM workout_set_logs WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber ASC")
    fun getSetLogsForSession(sessionId: Long): Flow<List<WorkoutSetLog>>

    // Last completed set or entry for an exercise
    @Query("SELECT * FROM workout_set_logs WHERE exerciseId = :exerciseId AND isCompleted = 1 ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLastSetLogForExercise(exerciseId: Long): WorkoutSetLog?

    // Last session's sets for an exercise
    @Query("""
        SELECT * FROM workout_set_logs 
        WHERE exerciseId = :exerciseId AND isCompleted = 1 
          AND sessionId = (
              SELECT sessionId FROM workout_set_logs 
              WHERE exerciseId = :exerciseId AND isCompleted = 1 
              ORDER BY timestamp DESC LIMIT 1
          )
        ORDER BY setNumber ASC
    """)
    suspend fun getLastSessionSetsForExercise(exerciseId: Long): List<WorkoutSetLog>

    // Overall max weight recorded for an exercise
    @Query("SELECT MAX(weight) FROM workout_set_logs WHERE exerciseId = :exerciseId AND isCompleted = 1 AND weight > 0")
    suspend fun getMaxWeightForExercise(exerciseId: Long): Double?

    // Best PR set (highest weight, highest reps)
    @Query("SELECT * FROM workout_set_logs WHERE exerciseId = :exerciseId AND isCompleted = 1 AND weight > 0 ORDER BY weight DESC, reps DESC LIMIT 1")
    suspend fun getMaxPrSetForExercise(exerciseId: Long): WorkoutSetLog?

    // Recent distinct sessions containing this exercise (up to 10)
    @Query("""
        SELECT DISTINCT s.* FROM workout_sessions s
        INNER JOIN workout_set_logs l ON s.id = l.sessionId
        WHERE l.exerciseId = :exerciseId
        ORDER BY s.timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentSessionsForExercise(exerciseId: Long, limit: Int = 10): List<WorkoutSession>

    @Query("""
        SELECT * FROM workout_set_logs 
        WHERE exerciseId = :exerciseId AND sessionId = :sessionId
        ORDER BY setNumber ASC
    """)
    suspend fun getSetLogsForExerciseAndSession(exerciseId: Long, sessionId: Long): List<WorkoutSetLog>

    @Query("""
        SELECT * FROM workout_set_logs 
        WHERE exerciseId = :exerciseId
        ORDER BY timestamp DESC
        LIMIT 100
    """)
    suspend fun getAllSetLogsForExercise(exerciseId: Long): List<WorkoutSetLog>

    @Query("DELETE FROM workout_set_logs WHERE exerciseId = :exerciseId")
    suspend fun deleteSetLogsForExercise(exerciseId: Long)

    @Query("DELETE FROM workout_set_logs WHERE id IN (:logIds)")
    suspend fun deleteSetLogsByIds(logIds: List<Long>)

    @Query("DELETE FROM workout_set_logs WHERE exerciseId = :exerciseId AND sessionId = :sessionId")
    suspend fun deleteSetLogsForExerciseAndSession(exerciseId: Long, sessionId: Long)

    @Query("SELECT COUNT(*) FROM workout_set_logs WHERE sessionId = :sessionId")
    suspend fun getSetLogCountForSession(sessionId: Long): Int

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("""
        SELECT s.id FROM workout_sessions s
        INNER JOIN workout_set_logs l ON s.id = l.sessionId
        WHERE l.exerciseId = :exerciseId
        GROUP BY s.id
        ORDER BY s.timestamp DESC, s.id DESC
        LIMIT :limit
    """)
    suspend fun getRecentSessionIdsForExercise(exerciseId: Long, limit: Int = 10): List<Long>

    @Query("DELETE FROM workout_set_logs WHERE exerciseId = :exerciseId AND sessionId NOT IN (:keepSessionIds)")
    suspend fun deleteSetLogsForExerciseExceptSessions(exerciseId: Long, keepSessionIds: List<Long>)

    @Query("SELECT DISTINCT exerciseId FROM workout_set_logs")
    suspend fun getAllExerciseIdsWithLogs(): List<Long>

    @Query("DELETE FROM workout_sessions WHERE id NOT IN (SELECT DISTINCT sessionId FROM workout_set_logs WHERE sessionId IS NOT NULL)")
    suspend fun deleteOrphanedSessions()

    @Query("DELETE FROM workout_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM workout_set_logs")
    suspend fun clearSetLogs()
}
