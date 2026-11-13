package com.example.data.repository

import com.example.data.backup.DataBackupManager
import com.example.data.database.AppDatabase
import com.example.data.entity.Exercise
import com.example.data.entity.PlanExercise
import com.example.data.entity.WorkoutPlan
import com.example.data.entity.WorkoutSession
import com.example.data.entity.WorkoutSetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ExerciseStats(
    val lastWeight: Double? = null,
    val lastReps: Int? = null,
    val maxWeight: Double? = null,
    val maxReps: Int? = null,
    val lastSessionSets: List<WorkoutSetLog> = emptyList()
)

data class ExerciseWorkoutHistoryItem(
    val sessionId: Long,
    val planName: String,
    val timestamp: Long,
    val sets: List<WorkoutSetLog>,
    val maxWeight: Double,
    val totalReps: Int,
    val totalVolume: Double
)

class WorkoutRepository(
    private val database: AppDatabase,
    private val context: android.content.Context? = null
) {
    private val exerciseDao = database.exerciseDao()
    private val planDao = database.workoutPlanDao()
    private val logDao = database.workoutLogDao()
    private val backupManager = DataBackupManager(database, context)

    val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()
    val allPlans: Flow<List<WorkoutPlan>> = planDao.getAllPlans()
    val allSessions: Flow<List<WorkoutSession>> = logDao.getAllSessions()

    fun getPlanExercises(planId: Long): Flow<List<PlanExercise>> {
        return planDao.getPlanExercises(planId)
    }

    suspend fun getAllExercisesList(): List<Exercise> {
        return withContext(Dispatchers.IO) { exerciseDao.getAllExercisesList() }
    }

    suspend fun getAllPlansList(): List<WorkoutPlan> = withContext(Dispatchers.IO) {
        planDao.getAllPlansList()
    }

    suspend fun getAllPlanExercisesList(): List<PlanExercise> = withContext(Dispatchers.IO) {
        planDao.getAllPlanExercisesList()
    }

    suspend fun getExerciseById(id: Long): Exercise? = withContext(Dispatchers.IO) {
        exerciseDao.getExerciseById(id)
    }

    @Volatile
    private var cachedStatsMap: Map<Long, ExerciseStats>? = null

    fun invalidateStatsCache() {
        cachedStatsMap = null
    }

    suspend fun getExerciseStats(exerciseId: Long): ExerciseStats = withContext(Dispatchers.IO) {
        val statsMap = getAllExerciseStatsMap()
        statsMap[exerciseId] ?: ExerciseStats()
    }

    private val prPrefs by lazy {
        context?.getSharedPreferences("exercise_pr_prefs", android.content.Context.MODE_PRIVATE)
    }

    private fun getPrResetTimestamp(exerciseId: Long): Long {
        return prPrefs?.getLong("pr_reset_ts_$exerciseId", 0L) ?: 0L
    }

    private fun setPrResetTimestamp(exerciseId: Long, ts: Long) {
        prPrefs?.edit()?.putLong("pr_reset_ts_$exerciseId", ts)?.apply()
    }

    private fun clearPrResetTimestamp(exerciseId: Long) {
        prPrefs?.edit()?.remove("pr_reset_ts_$exerciseId")?.apply()
    }

    private fun getPreservedPr(exerciseId: Long): Pair<Double?, Int?>? {
        val prefs = prPrefs ?: return null
        if (!prefs.getBoolean("has_preserved_pr_$exerciseId", false)) return null
        val wStr = prefs.getString("preserved_pr_weight_$exerciseId", null)
        val weight = wStr?.toDoubleOrNull()
        val repsRaw = prefs.getInt("preserved_pr_reps_$exerciseId", -1)
        val reps = if (repsRaw > 0) repsRaw else null
        return Pair(weight, reps)
    }

    private fun setPreservedPr(exerciseId: Long, maxWeight: Double?, maxReps: Int?) {
        val prefs = prPrefs ?: return
        prefs.edit()
            .putBoolean("has_preserved_pr_$exerciseId", true)
            .apply {
                if (maxWeight != null) putString("preserved_pr_weight_$exerciseId", maxWeight.toString())
                else remove("preserved_pr_weight_$exerciseId")
                if (maxReps != null && maxReps > 0) putInt("preserved_pr_reps_$exerciseId", maxReps)
                else remove("preserved_pr_reps_$exerciseId")
            }
            .apply()
    }

    private fun clearPreservedPr(exerciseId: Long) {
        prPrefs?.edit()
            ?.remove("has_preserved_pr_$exerciseId")
            ?.remove("preserved_pr_weight_$exerciseId")
            ?.remove("preserved_pr_reps_$exerciseId")
            ?.apply()
    }

    private fun getAllPreservedExerciseIds(): Set<Long> {
        val prefs = prPrefs ?: return emptySet()
        return prefs.all.keys
            .filter { it.startsWith("has_preserved_pr_") && prefs.getBoolean(it, false) }
            .mapNotNull { it.removePrefix("has_preserved_pr_").toLongOrNull() }
            .toSet()
    }

    private fun clearAllPrOverrides() {
        prPrefs?.edit()?.clear()?.apply()
    }

    suspend fun getAllExerciseStatsMap(forceRefresh: Boolean = false): Map<Long, ExerciseStats> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedStatsMap?.let { return@withContext it }
        }
        val allLogs = logDao.getAllSetLogsList()
        val preservedIds = getAllPreservedExerciseIds()
        if (allLogs.isEmpty() && preservedIds.isEmpty()) {
            val empty = emptyMap<Long, ExerciseStats>()
            cachedStatsMap = empty
            return@withContext empty
        }

        val grouped = allLogs.groupBy { it.exerciseId }
        val allExerciseIds = grouped.keys + preservedIds
        val result = mutableMapOf<Long, ExerciseStats>()

        for (exId in allExerciseIds) {
            val logs = grouped[exId] ?: emptyList()
            val completedLogs = logs.filter { it.isCompleted }
            val lastSet = completedLogs.firstOrNull()

            val prResetTs = getPrResetTimestamp(exId)
            val prCandidateLogs = if (prResetTs > 0L) {
                completedLogs.filter { it.timestamp > prResetTs }
            } else {
                completedLogs
            }

            val maxPrSet = prCandidateLogs
                .filter { it.weight > 0 }
                .maxWithOrNull(compareBy({ it.weight }, { it.reps }))

            val fallbackSet = if (maxPrSet == null) {
                prCandidateLogs
                    .filter { it.reps > 0 || it.weight != 0.0 }
                    .maxByOrNull { it.reps }
            } else null

            val logsMaxWeight = when {
                maxPrSet != null && maxPrSet.weight > 0 -> maxPrSet.weight
                fallbackSet != null && fallbackSet.weight != 0.0 -> fallbackSet.weight
                else -> null
            }

            val logsMaxReps = when {
                maxPrSet != null && maxPrSet.reps > 0 -> maxPrSet.reps
                fallbackSet != null && fallbackSet.reps > 0 -> fallbackSet.reps
                else -> null
            }

            // Combine with preserved PR if user previously reset stats while keeping PR
            val preserved = getPreservedPr(exId)
            val (effectiveMaxWeight, effectiveMaxReps) = if (preserved != null) {
                val pW = preserved.first
                val pR = preserved.second
                when {
                    logsMaxWeight != null && logsMaxWeight > 0 && (pW == null || pW <= 0 || logsMaxWeight > pW || (logsMaxWeight == pW && (logsMaxReps ?: 0) > (pR ?: 0))) -> {
                        Pair(logsMaxWeight, logsMaxReps)
                    }
                    pW != null && pW > 0 -> {
                        Pair(pW, pR)
                    }
                    logsMaxWeight == -2.0 || pW == -2.0 -> {
                        val bestReps = maxOf(logsMaxReps ?: 0, pR ?: 0).takeIf { it > 0 }
                        Pair(-2.0, bestReps)
                    }
                    else -> {
                        val bestReps = maxOf(logsMaxReps ?: 0, pR ?: 0).takeIf { it > 0 }
                        val bestWeight = logsMaxWeight ?: pW
                        Pair(bestWeight, bestReps)
                    }
                }
            } else {
                Pair(logsMaxWeight, logsMaxReps)
            }

            val lastSessionId = completedLogs.firstOrNull()?.sessionId
            val lastSessionSets = if (lastSessionId != null && lastSessionId > 0) {
                completedLogs.filter { it.sessionId == lastSessionId }.sortedBy { it.setNumber }
            } else {
                emptyList()
            }

            result[exId] = ExerciseStats(
                lastWeight = lastSet?.weight,
                lastReps = lastSet?.reps,
                maxWeight = effectiveMaxWeight,
                maxReps = effectiveMaxReps,
                lastSessionSets = lastSessionSets
            )
        }

        cachedStatsMap = result
        result
    }

    suspend fun resetExercisePR(exerciseId: Long) {
        resetExerciseData(exerciseId = exerciseId, resetPr = true, resetStats = true)
    }

    suspend fun resetExerciseData(
        exerciseId: Long,
        resetPr: Boolean,
        resetStats: Boolean
    ) = withContext(Dispatchers.IO) {
        if (!resetPr && !resetStats) return@withContext

        if (resetPr && resetStats) {
            clearPreservedPr(exerciseId)
            clearPrResetTimestamp(exerciseId)
            val affectedSessionIds = logDao.getAllSetLogsForExercise(exerciseId)
                .map { it.sessionId }
                .filter { it > 0 }
                .distinct()
            logDao.deleteSetLogsForExercise(exerciseId)
            for (sid in affectedSessionIds) {
                if (logDao.getSetLogCountForSession(sid) == 0) {
                    logDao.deleteSessionById(sid)
                }
            }
        } else if (resetPr) {
            // Reset PR only: keep workout logs/stats intact, ignore PRs prior to now
            clearPreservedPr(exerciseId)
            setPrResetTimestamp(exerciseId, System.currentTimeMillis())
        } else {
            // Reset Stats only: preserve current PR baseline, wipe workout logs/stats
            val currentStats = getAllExerciseStatsMap(forceRefresh = true)[exerciseId]
            if (currentStats != null && (currentStats.maxWeight != null || currentStats.maxReps != null)) {
                setPreservedPr(exerciseId, currentStats.maxWeight, currentStats.maxReps)
            }
            clearPrResetTimestamp(exerciseId)
            val affectedSessionIds = logDao.getAllSetLogsForExercise(exerciseId)
                .map { it.sessionId }
                .filter { it > 0 }
                .distinct()
            logDao.deleteSetLogsForExercise(exerciseId)
            for (sid in affectedSessionIds) {
                if (logDao.getSetLogCountForSession(sid) == 0) {
                    logDao.deleteSessionById(sid)
                }
            }
        }
        invalidateStatsCache()
    }

    suspend fun deleteExerciseHistoryItem(
        exerciseId: Long,
        item: ExerciseWorkoutHistoryItem
    ) = withContext(Dispatchers.IO) {
        val setIds = item.sets.map { it.id }.filter { it > 0 }
        if (setIds.isNotEmpty()) {
            logDao.deleteSetLogsByIds(setIds)
        } else if (item.sessionId > 0) {
            logDao.deleteSetLogsForExerciseAndSession(exerciseId, item.sessionId)
        }

        if (item.sessionId > 0) {
            if (logDao.getSetLogCountForSession(item.sessionId) == 0) {
                logDao.deleteSessionById(item.sessionId)
            }
        }
        invalidateStatsCache()
    }

    suspend fun getExerciseWorkoutHistory(exerciseId: Long, limit: Int = 10): List<ExerciseWorkoutHistoryItem> {
        val sessions = logDao.getRecentSessionsForExercise(exerciseId, limit)
        if (sessions.isNotEmpty()) {
            return sessions.map { session ->
                val sets = logDao.getSetLogsForExerciseAndSession(exerciseId, session.id)
                val completedSets = sets.filter { it.isCompleted }
                val maxWeight = when {
                    completedSets.any { it.weight > 0 } -> completedSets.filter { it.weight > 0 }.maxOfOrNull { it.weight } ?: 0.0
                    completedSets.any { it.weight == -2.0 } -> -2.0
                    else -> 0.0
                }
                val totalReps = completedSets.sumOf { if (it.reps > 0) it.reps else 0 }
                val totalVolume = completedSets.filter { it.weight > 0 }.sumOf { it.weight * it.reps }
                ExerciseWorkoutHistoryItem(
                    sessionId = session.id,
                    planName = session.planName,
                    timestamp = session.timestamp,
                    sets = sets,
                    maxWeight = maxWeight,
                    totalReps = totalReps,
                    totalVolume = totalVolume
                )
            }
        }

        val allLogs = logDao.getAllSetLogsForExercise(exerciseId)
        if (allLogs.isEmpty()) return emptyList()

        val grouped = allLogs.groupBy { it.sessionId.takeIf { s -> s > 0 } ?: (it.timestamp / (1000 * 60 * 60 * 24)) }
            .entries
            .take(limit)

        return grouped.map { entry ->
            val sets = entry.value.sortedBy { it.setNumber }
            val firstLog = sets.first()
            val completedSets = sets.filter { it.isCompleted }
            val maxWeight = when {
                completedSets.any { it.weight > 0 } -> completedSets.filter { it.weight > 0 }.maxOfOrNull { it.weight } ?: 0.0
                completedSets.any { it.weight == -2.0 } -> -2.0
                else -> 0.0
            }
            val totalReps = completedSets.sumOf { if (it.reps > 0) it.reps else 0 }
            val totalVolume = completedSets.filter { it.weight > 0 }.sumOf { it.weight * it.reps }
            ExerciseWorkoutHistoryItem(
                sessionId = firstLog.sessionId,
                planName = "Saved Workout",
                timestamp = firstLog.timestamp,
                sets = sets,
                maxWeight = maxWeight,
                totalReps = totalReps,
                totalVolume = totalVolume
            )
        }
    }

    suspend fun clearAllData() {
        clearAllPrOverrides()
        invalidateStatsCache()
        database.clearAllTables()
    }

    suspend fun insertExercise(exercise: Exercise): Long {
        return exerciseDao.insertExercise(exercise)
    }

    suspend fun updateExercise(exercise: Exercise) {
        exerciseDao.updateExercise(exercise)
    }

    suspend fun deleteExercise(exercise: Exercise) {
        clearPreservedPr(exercise.id)
        clearPrResetTimestamp(exercise.id)
        invalidateStatsCache()
        exerciseDao.deleteExercise(exercise)
    }

    suspend fun createPlan(
        name: String,
        description: String = "",
        iconName: String = "FitnessCenter",
        colorHex: String = "#FF6B00",
        orderIndex: Int = 0
    ): Long {
        return planDao.insertPlan(
            WorkoutPlan(
                name = name,
                description = description,
                iconName = iconName,
                colorHex = colorHex,
                orderIndex = orderIndex
            )
        )
    }

    suspend fun updatePlan(plan: WorkoutPlan) {
        planDao.updatePlan(plan)
    }

    suspend fun updatePlans(plans: List<WorkoutPlan>) {
        planDao.updatePlans(plans)
    }

    suspend fun deletePlan(plan: WorkoutPlan) {
        planDao.deletePlan(plan)
    }

    suspend fun getPlanExercisesList(planId: Long): List<PlanExercise> {
        return planDao.getPlanExercisesList(planId)
    }

    suspend fun setPlanExercises(planId: Long, exercises: List<PlanExercise>) {
        planDao.setPlanExercises(planId, exercises)
    }

    suspend fun deletePlanExercisesByPlanId(planId: Long) {
        planDao.deletePlanExercisesByPlanId(planId)
    }

    suspend fun addExerciseToPlan(
        planId: Long,
        exerciseId: Long,
        targetSets: Int = 3,
        targetReps: Int = 10,
        targetWeight: Double = 0.0,
        note: String = ""
    ): Long {
        val currentList = planDao.getPlanExercisesList(planId)
        val nextOrder = currentList.size
        return planDao.insertPlanExercise(
            PlanExercise(
                planId = planId,
                exerciseId = exerciseId,
                orderIndex = nextOrder,
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeight = targetWeight,
                note = note
            )
        )
    }

    suspend fun updatePlanExercise(planExercise: PlanExercise) {
        planDao.updatePlanExercise(planExercise)
    }

    suspend fun deletePlanExercise(planExercise: PlanExercise) {
        planDao.deletePlanExercise(planExercise)
    }

    suspend fun pruneAllExercisesHistoryToLast10() = withContext(Dispatchers.IO) {
        val exerciseIds = logDao.getAllExerciseIdsWithLogs()
        var deletedAny = false
        for (exId in exerciseIds) {
            val keepIds = logDao.getRecentSessionIdsForExercise(exId, 10)
            if (keepIds.isNotEmpty()) {
                logDao.deleteSetLogsForExerciseExceptSessions(exId, keepIds)
                deletedAny = true
            }
        }
        if (deletedAny) {
            logDao.deleteOrphanedSessions()
            invalidateStatsCache()
        }
    }

    private suspend fun pruneExerciseHistoryToLimit(exerciseIds: Collection<Long>, limit: Int = 10) {
        var pruned = false
        for (exId in exerciseIds) {
            val keepIds = logDao.getRecentSessionIdsForExercise(exId, limit)
            if (keepIds.isNotEmpty()) {
                logDao.deleteSetLogsForExerciseExceptSessions(exId, keepIds)
                pruned = true
            }
        }
        if (pruned) {
            logDao.deleteOrphanedSessions()
        }
    }

    suspend fun logWorkoutSession(
        planId: Long?,
        planName: String,
        generalNote: String,
        setLogs: List<WorkoutSetLog>
    ): Long {
        val sessionId = logDao.insertSession(
            WorkoutSession(
                planId = planId,
                planName = planName,
                note = generalNote,
                timestamp = System.currentTimeMillis(),
                isCompleted = true
            )
        )
        val logsWithSession = setLogs.map { it.copy(sessionId = sessionId) }
        logDao.insertSetLogs(logsWithSession)
        val exerciseIds = logsWithSession.map { it.exerciseId }.distinct()
        pruneExerciseHistoryToLimit(exerciseIds, limit = 10)
        invalidateStatsCache()
        return sessionId
    }

    suspend fun exportJson(): String = backupManager.exportToJsonString()

    suspend fun exportCsv(): String = backupManager.exportToCsvString()

    suspend fun importData(content: String, replace: Boolean): Result<Int> {
        if (replace) {
            clearAllPrOverrides()
        }
        val result = backupManager.importData(content, replace)
        if (result.isSuccess) {
            pruneAllExercisesHistoryToLast10()
        }
        invalidateStatsCache()
        return result
    }

    suspend fun importJson(json: String, replace: Boolean): Result<Int> {
        return importData(json, replace)
    }

    suspend fun seedSampleTestDataIfEmpty() {
        val existingPlans = planDao.getAllPlansList()
        val existingExercises = exerciseDao.getAllExercisesList()
        if (existingPlans.isNotEmpty() || existingExercises.isNotEmpty()) return

        // Create 5 testing exercises
        val benchPress = Exercise(
            name = "Bench Press",
            category = "Chest",
            equipment = "Barbell",
            isCustom = false,
            defaultNotes = "[TEST_SAMPLE]"
        )
        val squat = Exercise(
            name = "Barbell Squat",
            category = "Legs",
            equipment = "Barbell",
            isCustom = false,
            defaultNotes = "[TEST_SAMPLE]"
        )
        val pullups = Exercise(
            name = "Pull-ups",
            category = "Back",
            equipment = "Bodyweight",
            isCustom = false,
            defaultNotes = "[TEST_SAMPLE]"
        )
        val ohp = Exercise(
            name = "Overhead Press",
            category = "Shoulders",
            equipment = "Barbell",
            isCustom = false,
            defaultNotes = "[TEST_SAMPLE]"
        )
        val row = Exercise(
            name = "Barbell Row",
            category = "Back",
            equipment = "Barbell",
            isCustom = false,
            defaultNotes = "[TEST_SAMPLE]"
        )

        val bpId = exerciseDao.insertExercise(benchPress)
        val squatId = exerciseDao.insertExercise(squat)
        val pullupsId = exerciseDao.insertExercise(pullups)
        val ohpId = exerciseDao.insertExercise(ohp)
        val rowId = exerciseDao.insertExercise(row)

        // Create 1 testing workout plan
        val planId = planDao.insertPlan(
            WorkoutPlan(
                name = "Full Body (Test)",
                description = "Sample test workout plan",
                iconName = "FitnessCenter",
                colorHex = "#FF6B00",
                orderIndex = 0
            )
        )

        // Link the 5 exercises to this plan
        val planExercises = listOf(
            PlanExercise(planId = planId, exerciseId = bpId, orderIndex = 0, targetSets = 3, targetReps = 10, targetWeight = 60.0),
            PlanExercise(planId = planId, exerciseId = squatId, orderIndex = 1, targetSets = 3, targetReps = 8, targetWeight = 80.0),
            PlanExercise(planId = planId, exerciseId = pullupsId, orderIndex = 2, targetSets = 3, targetReps = 10, targetWeight = -2.0),
            PlanExercise(planId = planId, exerciseId = ohpId, orderIndex = 3, targetSets = 3, targetReps = 10, targetWeight = 40.0),
            PlanExercise(planId = planId, exerciseId = rowId, orderIndex = 4, targetSets = 3, targetReps = 10, targetWeight = 50.0)
        )
        planDao.insertPlanExercises(planExercises)
    }
}
