package com.example.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entity.Exercise
import com.example.data.entity.PlanExercise
import com.example.data.entity.WorkoutPlan
import com.example.data.entity.WorkoutSession
import com.example.data.entity.WorkoutSetLog
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class WorkoutExportData(
    val exportVersion: Int = 1,
    val exportTimestamp: Long = System.currentTimeMillis(),
    val exercises: List<Exercise> = emptyList(),
    val workoutPlans: List<WorkoutPlan> = emptyList(),
    val planExercises: List<PlanExercise> = emptyList(),
    val workoutSessions: List<WorkoutSession> = emptyList(),
    val workoutSetLogs: List<WorkoutSetLog> = emptyList(),
    val activeLockers: List<Int> = emptyList()
)

class DataBackupManager(
    private val database: AppDatabase,
    private val context: Context? = null
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(WorkoutExportData::class.java).indent("  ")

    private fun getActiveLockersFromPrefs(): List<Int> {
        return try {
            val ctx = context ?: return emptyList()
            val prefs = ctx.getSharedPreferences("gym_lockers_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getStringSet("active_lockers_set", emptySet()) ?: emptySet()
            saved.mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..60 }.sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveActiveLockersToPrefs(lockers: List<Int>) {
        try {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("gym_lockers_prefs", Context.MODE_PRIVATE)
            val cleanSet = lockers.filter { it in 1..60 }.map { it.toString() }.toSet()
            prefs.edit().putStringSet("active_lockers_set", cleanSet).commit()
        } catch (_: Exception) {}
    }

    private fun clearActiveLockersInPrefs() {
        try {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("gym_lockers_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("active_lockers_set").commit()
        } catch (_: Exception) {}
    }

    suspend fun exportToJsonString(): String = withContext(Dispatchers.IO) {
        val exercises = database.exerciseDao().getAllExercisesList()
        val plans = database.workoutPlanDao().getAllPlansList()
        val planExercises = database.workoutPlanDao().getAllPlanExercisesList()
        val sessions = database.workoutLogDao().getAllSessionsList()
        val logs = database.workoutLogDao().getAllSetLogsList()
        val lockers = getActiveLockersFromPrefs()

        val exportData = WorkoutExportData(
            exportVersion = 1,
            exportTimestamp = System.currentTimeMillis(),
            exercises = exercises,
            workoutPlans = plans,
            planExercises = planExercises,
            workoutSessions = sessions,
            workoutSetLogs = logs,
            activeLockers = lockers
        )
        adapter.toJson(exportData)
    }

    suspend fun exportToCsvString(): String = withContext(Dispatchers.IO) {
        val exercises = database.exerciseDao().getAllExercisesList()
        val exercisesMap = exercises.associateBy { it.id }
        val sessions = database.workoutLogDao().getAllSessionsList()
        val sessionsMap = sessions.associateBy { it.id }
        val logs = database.workoutLogDao().getAllSetLogsList()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val sb = StringBuilder()
        // CSV Header for workout history
        sb.append("--- WORKOUT HISTORY ---\n")
        sb.append("Session ID,Date,Time,Workout Plan,Exercise Name,Category,Equipment,Set Number,Weight (kg),Reps,Total Volume (kg),Set Note,Session Note,Completed\n")

        if (logs.isNotEmpty()) {
            for (log in logs) {
                val session = sessionsMap[log.sessionId]
                val exercise = exercisesMap[log.exerciseId]
                val dateStr = session?.timestamp?.let { dateFormat.format(Date(it)) } ?: ""
                val timeStr = session?.timestamp?.let { timeFormat.format(Date(it)) } ?: ""
                val planName = escapeCsv(session?.planName ?: "Custom Workout")
                val exName = escapeCsv(exercise?.name ?: "Exercise #${log.exerciseId}")
                val category = escapeCsv(exercise?.category ?: "")
                val equipment = escapeCsv(exercise?.equipment ?: "")
                val setNum = log.setNumber
                val weight = log.weight
                val reps = log.reps
                val volume = weight * reps
                val setNote = escapeCsv(log.note)
                val sessionNote = escapeCsv(session?.note ?: "")
                val completed = if (log.isCompleted) "YES" else "NO"

                sb.append("${log.sessionId},$dateStr,$timeStr,$planName,$exName,$category,$equipment,$setNum,$weight,$reps,$volume,$setNote,$sessionNote,$completed\n")
            }
        } else {
            sb.append(",,,No workout sets logged yet,,,,,,,,,\n")
        }

        // Section 2: Exercise Progress & PR Records
        sb.append("\n--- EXERCISE PROGRESS & PR RECORDS ---\n")
        sb.append("Exercise ID,Exercise Name,Category,Equipment,Personal Record Weight (kg),Max PR Reps,Last Logged Weight (kg),Last Logged Reps,Total Sets Logged\n")

        for (ex in exercises) {
            val exLogs = logs.filter { it.exerciseId == ex.id }
            val maxWeight = exLogs.maxOfOrNull { it.weight } ?: 0.0
            val maxWeightSets = exLogs.filter { it.weight == maxWeight }
            val maxRepsAtMaxWeight = maxWeightSets.maxOfOrNull { it.reps } ?: 0
            val lastSet = exLogs.maxByOrNull { it.id }

            val exName = escapeCsv(ex.name)
            val category = escapeCsv(ex.category)
            val equipment = escapeCsv(ex.equipment)
            val maxWeightStr = if (maxWeight > 0) maxWeight.toString() else "N/A"
            val maxRepsStr = if (maxRepsAtMaxWeight > 0) maxRepsAtMaxWeight.toString() else "N/A"
            val lastWeightStr = lastSet?.weight?.toString() ?: "N/A"
            val lastRepsStr = lastSet?.reps?.toString() ?: "N/A"
            val totalSets = exLogs.size

            sb.append("${ex.id},$exName,$category,$equipment,$maxWeightStr,$maxRepsStr,$lastWeightStr,$lastRepsStr,$totalSets\n")
        }

        // Section 3: Workout Plans & Exercises
        val plans = database.workoutPlanDao().getAllPlansList()
        val allPlanExercises = database.workoutPlanDao().getAllPlanExercisesList()
        sb.append("\n--- WORKOUT PLANS & EXERCISES ---\n")
        sb.append("Plan Name,Plan Description,Plan Icon,Plan Color,Exercise Order,Exercise Name,Category,Equipment,Target Sets,Target Reps,Target Weight,Note\n")
        for (plan in plans) {
            val pExercises = allPlanExercises.filter { it.planId == plan.id }.sortedBy { it.orderIndex }
            if (pExercises.isNotEmpty()) {
                for (pe in pExercises) {
                    val ex = exercisesMap[pe.exerciseId]
                    val pName = escapeCsv(plan.name)
                    val pDesc = escapeCsv(plan.description)
                    val pIcon = escapeCsv(plan.iconName)
                    val pColor = escapeCsv(plan.colorHex)
                    val peOrder = pe.orderIndex
                    val exName = escapeCsv(ex?.name ?: "")
                    val exCat = escapeCsv(ex?.category ?: "")
                    val exEquip = escapeCsv(ex?.equipment ?: "")
                    val peSets = pe.targetSets
                    val peReps = pe.targetReps
                    val peWeight = pe.targetWeight
                    val peNote = escapeCsv(pe.note)
                    sb.append("$pName,$pDesc,$pIcon,$pColor,$peOrder,$exName,$exCat,$exEquip,$peSets,$peReps,$peWeight,$peNote\n")
                }
            } else {
                val pName = escapeCsv(plan.name)
                val pDesc = escapeCsv(plan.description)
                val pIcon = escapeCsv(plan.iconName)
                val pColor = escapeCsv(plan.colorHex)
                sb.append("$pName,$pDesc,$pIcon,$pColor,0,,,,3,10,0.0,\n")
            }
        }

        // Section 4: Gym Lockers
        val lockers = getActiveLockersFromPrefs()
        sb.append("\n--- LOCKERS ---\n")
        sb.append("Locker Number,Status\n")
        if (lockers.isNotEmpty()) {
            for (l in lockers) {
                sb.append("$l,UNLOCKED\n")
            }
        }

        sb.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    /**
     * Unified importer that handles both JSON backup files and CSV spreadsheets.
     */
    suspend fun importData(rawContent: String, replaceExisting: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = cleanContent(rawContent)
            if (content.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Import file is empty"))
            }

            val exportData = if (isJsonFormat(content)) {
                parseJsonExportData(content)
            } else if (isCsvFormat(content)) {
                parseCsvExportData(content)
            } else {
                // Try JSON first then CSV as fallback
                try {
                    parseJsonExportData(content)
                } catch (e: Exception) {
                    parseCsvExportData(content)
                }
            }

            importWorkoutExportData(exportData, replaceExisting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromJsonString(jsonString: String, replaceExisting: Boolean = false): Result<Int> =
        importData(jsonString, replaceExisting)

    private fun cleanContent(raw: String): String {
        var text = raw.trim()
        // Remove UTF-8 BOM if present
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1).trim()
        }
        // Unwrap markdown code blocks e.g. ```json ... ``` or ```csv ... ``` or ``` ... ```
        if (text.startsWith("```")) {
            text = text.replaceFirst(Regex("^```[a-zA-Z0-9_-]*\\R"), "")
            if (text.endsWith("```")) {
                text = text.substring(0, text.length - 3).trim()
            }
        }
        return text.trim()
    }

    private fun isJsonFormat(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun isCsvFormat(text: String): Boolean {
        return text.contains("--- WORKOUT HISTORY ---") ||
                text.contains("Session ID,Date") ||
                text.contains("Exercise Name,Category") ||
                text.contains("Personal Record Weight") ||
                text.lines().any { it.contains(",") && !it.trimStart().startsWith("{") }
    }

    private fun parseJsonExportData(jsonString: String): WorkoutExportData {
        // Try with Moshi adapter first
        try {
            val result = adapter.fromJson(jsonString)
            if (result != null && (result.exercises.isNotEmpty() || result.workoutPlans.isNotEmpty() || result.workoutSessions.isNotEmpty() || result.workoutSetLogs.isNotEmpty())) {
                return result
            }
        } catch (_: Exception) {
            // Fallback to manual JSONObject parsing
        }

        // Resilient Fallback using org.json.JSONObject
        val root = JSONObject(jsonString)
        val exportVersion = root.optInt("exportVersion", 1)
        val exportTimestamp = root.optLong("exportTimestamp", System.currentTimeMillis())

        val exercises = mutableListOf<Exercise>()
        val exArray = root.optJSONArray("exercises")
        if (exArray != null) {
            for (i in 0 until exArray.length()) {
                val obj = exArray.optJSONObject(i) ?: continue
                exercises.add(
                    Exercise(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Exercise"),
                        category = obj.optString("category", "General"),
                        equipment = obj.optString("equipment", "Barbell"),
                        isCustom = obj.optBoolean("isCustom", false),
                        defaultNotes = obj.optString("defaultNotes", "")
                    )
                )
            }
        }

        val workoutPlans = mutableListOf<WorkoutPlan>()
        val planArray = root.optJSONArray("workoutPlans")
        if (planArray != null) {
            for (i in 0 until planArray.length()) {
                val obj = planArray.optJSONObject(i) ?: continue
                workoutPlans.add(
                    WorkoutPlan(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Workout Plan"),
                        description = obj.optString("description", ""),
                        iconName = obj.optString("iconName", "FitnessCenter"),
                        colorHex = obj.optString("colorHex", "#FF6B00"),
                        orderIndex = obj.optInt("orderIndex", i),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val planExercises = mutableListOf<PlanExercise>()
        val peArray = root.optJSONArray("planExercises")
        if (peArray != null) {
            for (i in 0 until peArray.length()) {
                val obj = peArray.optJSONObject(i) ?: continue
                planExercises.add(
                    PlanExercise(
                        id = obj.optLong("id", 0L),
                        planId = obj.optLong("planId", 0L),
                        exerciseId = obj.optLong("exerciseId", 0L),
                        orderIndex = obj.optInt("orderIndex", i),
                        targetSets = obj.optInt("targetSets", 3),
                        targetReps = obj.optInt("targetReps", 10),
                        targetWeight = obj.optDouble("targetWeight", 0.0),
                        note = obj.optString("note", "")
                    )
                )
            }
        }

        val workoutSessions = mutableListOf<WorkoutSession>()
        val wsArray = root.optJSONArray("workoutSessions")
        if (wsArray != null) {
            for (i in 0 until wsArray.length()) {
                val obj = wsArray.optJSONObject(i) ?: continue
                val planIdVal = if (obj.has("planId") && !obj.isNull("planId")) obj.optLong("planId") else null
                workoutSessions.add(
                    WorkoutSession(
                        id = obj.optLong("id", 0L),
                        planId = planIdVal,
                        planName = obj.optString("planName", "Custom Workout"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        note = obj.optString("note", ""),
                        isCompleted = obj.optBoolean("isCompleted", true)
                    )
                )
            }
        }

        val workoutSetLogs = mutableListOf<WorkoutSetLog>()
        val slArray = root.optJSONArray("workoutSetLogs")
        if (slArray != null) {
            for (i in 0 until slArray.length()) {
                val obj = slArray.optJSONObject(i) ?: continue
                workoutSetLogs.add(
                    WorkoutSetLog(
                        id = obj.optLong("id", 0L),
                        sessionId = obj.optLong("sessionId", 0L),
                        exerciseId = obj.optLong("exerciseId", 0L),
                        setNumber = obj.optInt("setNumber", 1),
                        reps = obj.optInt("reps", 10),
                        weight = obj.optDouble("weight", 0.0),
                        note = obj.optString("note", ""),
                        isCompleted = obj.optBoolean("isCompleted", true),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        val lockersList = mutableListOf<Int>()
        val lockersArray = root.optJSONArray("activeLockers")
        if (lockersArray != null) {
            for (i in 0 until lockersArray.length()) {
                val num = lockersArray.optInt(i, -1)
                if (num in 1..60) {
                    lockersList.add(num)
                }
            }
        }

        return WorkoutExportData(
            exportVersion = exportVersion,
            exportTimestamp = exportTimestamp,
            exercises = exercises,
            workoutPlans = workoutPlans,
            planExercises = planExercises,
            workoutSessions = workoutSessions,
            workoutSetLogs = workoutSetLogs,
            activeLockers = lockersList.distinct()
        )
    }

    /**
     * Parses a CSV spreadsheet file exported by FitTracker or similar app into WorkoutExportData.
     */
    private fun parseCsvExportData(csvString: String): WorkoutExportData {
        val lines = csvString.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            throw IllegalArgumentException("CSV file contains no data")
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val exerciseList = mutableListOf<Exercise>()
        val exerciseMap = mutableMapOf<String, Exercise>() // name.lowercase() -> Exercise
        var nextExerciseId = 1L

        val sessionsList = mutableListOf<WorkoutSession>()
        val sessionKeyMap = mutableMapOf<String, Long>() // "date_time_plan" or raw session ID -> synthetic session ID
        var nextSessionId = 1L

        val setLogsList = mutableListOf<WorkoutSetLog>()
        var nextSetLogId = 1L

        var inHistorySection = false
        var inPrSection = false
        var inPlansSection = false
        var inLockersSection = false
        var historyHeaderMap = mapOf<String, Int>()
        var prHeaderMap = mapOf<String, Int>()
        var plansHeaderMap = mapOf<String, Int>()
        var lockersHeaderMap = mapOf<String, Int>()
        val lockersList = mutableListOf<Int>()

        val workoutPlansList = mutableListOf<WorkoutPlan>()
        val planMap = mutableMapOf<String, WorkoutPlan>() // lowercase name -> WorkoutPlan
        val planExercisesList = mutableListOf<PlanExercise>()
        var nextPlanId = 1L
        var nextPlanExerciseId = 1L

        for (line in lines) {
            if (line.startsWith("--- WORKOUT HISTORY ---")) {
                inHistorySection = true
                inPrSection = false
                inPlansSection = false
                inLockersSection = false
                historyHeaderMap = emptyMap()
                continue
            }
            if (line.startsWith("--- EXERCISE PROGRESS")) {
                inHistorySection = false
                inPrSection = true
                inPlansSection = false
                inLockersSection = false
                prHeaderMap = emptyMap()
                continue
            }
            if (line.startsWith("--- WORKOUT PLANS")) {
                inHistorySection = false
                inPrSection = false
                inPlansSection = true
                inLockersSection = false
                plansHeaderMap = emptyMap()
                continue
            }
            if (line.startsWith("--- LOCKERS")) {
                inHistorySection = false
                inPrSection = false
                inPlansSection = false
                inLockersSection = true
                lockersHeaderMap = emptyMap()
                continue
            }

            val tokens = parseCsvLine(line)
            if (tokens.isEmpty()) continue

            // Detect headers
            if (tokens.any { it.equals("Session ID", ignoreCase = true) || it.equals("Exercise Name", ignoreCase = true) || it.equals("Plan Name", ignoreCase = true) || it.equals("Locker Number", ignoreCase = true) || it.equals("Locker", ignoreCase = true) }) {
                if (inPlansSection || tokens.any { it.equals("Plan Name", ignoreCase = true) }) {
                    plansHeaderMap = tokens.mapIndexed { idx, s -> s.trim().lowercase() to idx }.toMap()
                    inPlansSection = true
                    inHistorySection = false
                    inPrSection = false
                    inLockersSection = false
                } else if (inPrSection || tokens.any { it.equals("Personal Record Weight (kg)", ignoreCase = true) }) {
                    prHeaderMap = tokens.mapIndexed { idx, s -> s.trim().lowercase() to idx }.toMap()
                    inPrSection = true
                    inHistorySection = false
                    inPlansSection = false
                    inLockersSection = false
                } else if (inLockersSection || tokens.any { it.equals("Locker Number", ignoreCase = true) || it.equals("Locker", ignoreCase = true) }) {
                    lockersHeaderMap = tokens.mapIndexed { idx, s -> s.trim().lowercase() to idx }.toMap()
                    inLockersSection = true
                    inHistorySection = false
                    inPrSection = false
                    inPlansSection = false
                } else {
                    historyHeaderMap = tokens.mapIndexed { idx, s -> s.trim().lowercase() to idx }.toMap()
                    inHistorySection = true
                    inPrSection = false
                    inPlansSection = false
                    inLockersSection = false
                }
                continue
            }

            if (inPlansSection && plansHeaderMap.isNotEmpty()) {
                val planName = getCsvValue(tokens, plansHeaderMap, "plan name").ifEmpty { getCsvValue(tokens, plansHeaderMap, "workout plan") }
                val planDesc = getCsvValue(tokens, plansHeaderMap, "plan description").ifEmpty { getCsvValue(tokens, plansHeaderMap, "description") }
                val planIcon = getCsvValue(tokens, plansHeaderMap, "plan icon").ifEmpty { getCsvValue(tokens, plansHeaderMap, "icon") }.ifEmpty { "FitnessCenter" }
                val planColor = getCsvValue(tokens, plansHeaderMap, "plan color").ifEmpty { getCsvValue(tokens, plansHeaderMap, "color") }.ifEmpty { "#FF6B00" }
                val exName = getCsvValue(tokens, plansHeaderMap, "exercise name")
                val category = getCsvValue(tokens, plansHeaderMap, "category").ifEmpty { "General" }
                val equipment = getCsvValue(tokens, plansHeaderMap, "equipment").ifEmpty { "Barbell" }
                val orderIndex = getCsvValue(tokens, plansHeaderMap, "exercise order").toIntOrNull()
                    ?: getCsvValue(tokens, plansHeaderMap, "order index").toIntOrNull() ?: 0
                val targetSets = getCsvValue(tokens, plansHeaderMap, "target sets").toIntOrNull() ?: 3
                val targetReps = getCsvValue(tokens, plansHeaderMap, "target reps").toIntOrNull() ?: 10
                val targetWeight = getCsvValue(tokens, plansHeaderMap, "target weight").toDoubleOrNull() ?: 0.0
                val note = getCsvValue(tokens, plansHeaderMap, "note")

                if (planName.isNotBlank()) {
                    val planKey = planName.trim().lowercase()
                    val plan = planMap.getOrPut(planKey) {
                        val p = WorkoutPlan(
                            id = nextPlanId++,
                            name = planName.trim(),
                            description = planDesc,
                            iconName = planIcon,
                            colorHex = planColor,
                            orderIndex = workoutPlansList.size,
                            createdAt = System.currentTimeMillis()
                        )
                        workoutPlansList.add(p)
                        p
                    }

                    if (exName.isNotBlank()) {
                        val exKey = exName.trim().lowercase()
                        val exercise = exerciseMap.getOrPut(exKey) {
                            val ex = Exercise(
                                id = nextExerciseId++,
                                name = exName.trim(),
                                category = category,
                                equipment = equipment,
                                isCustom = true
                            )
                            exerciseList.add(ex)
                            ex
                        }
                        planExercisesList.add(
                            PlanExercise(
                                id = nextPlanExerciseId++,
                                planId = plan.id,
                                exerciseId = exercise.id,
                                orderIndex = orderIndex,
                                targetSets = targetSets,
                                targetReps = targetReps,
                                targetWeight = targetWeight,
                                note = note
                            )
                        )
                    }
                }
            } else if (inHistorySection && historyHeaderMap.isNotEmpty()) {
                val rawSessionId = getCsvValue(tokens, historyHeaderMap, "session id")
                val dateStr = getCsvValue(tokens, historyHeaderMap, "date")
                val timeStr = getCsvValue(tokens, historyHeaderMap, "time")
                val planName = getCsvValue(tokens, historyHeaderMap, "workout plan").ifEmpty { "Workout" }
                val exName = getCsvValue(tokens, historyHeaderMap, "exercise name")
                val category = getCsvValue(tokens, historyHeaderMap, "category").ifEmpty { "General" }
                val equipment = getCsvValue(tokens, historyHeaderMap, "equipment").ifEmpty { "Barbell" }
                val setNum = getCsvValue(tokens, historyHeaderMap, "set number").toIntOrNull() ?: 1
                val weight = getCsvValue(tokens, historyHeaderMap, "weight (kg)").toDoubleOrNull()
                    ?: getCsvValue(tokens, historyHeaderMap, "weight").toDoubleOrNull() ?: 0.0
                val reps = getCsvValue(tokens, historyHeaderMap, "reps").toIntOrNull() ?: 0
                val setNote = getCsvValue(tokens, historyHeaderMap, "set note")
                val sessionNote = getCsvValue(tokens, historyHeaderMap, "session note")
                val completed = getCsvValue(tokens, historyHeaderMap, "completed").equals("YES", ignoreCase = true) ||
                        getCsvValue(tokens, historyHeaderMap, "completed").equals("TRUE", ignoreCase = true)

                if (exName.isBlank()) continue

                // Register exercise
                val exKey = exName.trim().lowercase()
                val exercise = exerciseMap.getOrPut(exKey) {
                    val ex = Exercise(
                        id = nextExerciseId++,
                        name = exName.trim(),
                        category = category,
                        equipment = equipment,
                        isCustom = true
                    )
                    exerciseList.add(ex)
                    ex
                }

                // Register session
                val sessionKey = if (rawSessionId.isNotBlank() && rawSessionId.toLongOrNull() != null) {
                    "sid_${rawSessionId}"
                } else {
                    "${dateStr}_${timeStr}_${planName}"
                }

                var timestamp = System.currentTimeMillis()
                try {
                    if (dateStr.isNotBlank()) {
                        val fullDateStr = if (timeStr.isNotBlank()) "$dateStr $timeStr" else "$dateStr 12:00:00"
                        val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(fullDateStr)
                        if (parsed != null) timestamp = parsed.time
                    }
                } catch (_: Exception) {}

                val sessionId = sessionKeyMap.getOrPut(sessionKey) {
                    val sid = nextSessionId++
                    sessionsList.add(
                        WorkoutSession(
                            id = sid,
                            planId = null,
                            planName = planName,
                            timestamp = timestamp,
                            note = sessionNote,
                            isCompleted = true
                        )
                    )
                    sid
                }

                // Add Set Log
                setLogsList.add(
                    WorkoutSetLog(
                        id = nextSetLogId++,
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        setNumber = setNum,
                        reps = reps,
                        weight = weight,
                        note = setNote,
                        isCompleted = completed,
                        timestamp = timestamp
                    )
                )
            } else if (inPrSection && prHeaderMap.isNotEmpty()) {
                val exName = getCsvValue(tokens, prHeaderMap, "exercise name")
                val category = getCsvValue(tokens, prHeaderMap, "category").ifEmpty { "General" }
                val equipment = getCsvValue(tokens, prHeaderMap, "equipment").ifEmpty { "Barbell" }
                if (exName.isNotBlank()) {
                    val exKey = exName.trim().lowercase()
                    if (!exerciseMap.containsKey(exKey)) {
                        val ex = Exercise(
                            id = nextExerciseId++,
                            name = exName.trim(),
                            category = category,
                            equipment = equipment,
                            isCustom = true
                        )
                        exerciseMap[exKey] = ex
                        exerciseList.add(ex)
                    }
                }
            } else if (inLockersSection) {
                val lockerStr = if (lockersHeaderMap.isNotEmpty()) {
                    getCsvValue(tokens, lockersHeaderMap, "locker number").ifEmpty {
                        getCsvValue(tokens, lockersHeaderMap, "locker").ifEmpty { tokens[0].trim() }
                    }
                } else {
                    tokens[0].trim()
                }
                val lockerNum = lockerStr.toIntOrNull()
                if (lockerNum != null && lockerNum in 1..60) {
                    lockersList.add(lockerNum)
                }
            }
        }

        // Legacy CSV Auto-Recovery: Reconstruct workout plans and plan exercises from history sessions!
        if (workoutPlansList.isEmpty() && sessionsList.isNotEmpty()) {
            val planNames = sessionsList
                .map { it.planName.trim() }
                .filter { it.isNotBlank() && !it.equals("Custom Workout", ignoreCase = true) && !it.equals("Saved Workout", ignoreCase = true) }
                .distinct()

            for ((pIdx, pName) in planNames.withIndex()) {
                val iconName = when {
                    pName.contains("PUSH", ignoreCase = true) -> "Push"
                    pName.contains("PULL", ignoreCase = true) -> "Pull"
                    pName.contains("LÁB", ignoreCase = true) || pName.contains("LEG", ignoreCase = true) -> "Legs"
                    else -> "FitnessCenter"
                }
                val colorHex = when {
                    pName.contains("PUSH", ignoreCase = true) -> "#FF6B00"
                    pName.contains("PULL", ignoreCase = true) -> "#3B82F6"
                    pName.contains("LÁB", ignoreCase = true) || pName.contains("LEG", ignoreCase = true) -> "#10B981"
                    else -> "#8B5CF6"
                }
                val planId = nextPlanId++
                val plan = WorkoutPlan(
                    id = planId,
                    name = pName,
                    description = "",
                    iconName = iconName,
                    colorHex = colorHex,
                    orderIndex = pIdx,
                    createdAt = System.currentTimeMillis()
                )
                workoutPlansList.add(plan)

                // Match sessions to this plan
                val planSessions = sessionsList.filter { it.planName.equals(pName, ignoreCase = true) }
                    .sortedByDescending { it.timestamp }
                val planSessionIds = planSessions.map { it.id }.toSet()

                for (i in sessionsList.indices) {
                    if (sessionsList[i].planName.equals(pName, ignoreCase = true)) {
                        sessionsList[i] = sessionsList[i].copy(planId = planId)
                    }
                }

                val latestSession = planSessions.firstOrNull()
                val latestSessionLogs = if (latestSession != null) {
                    setLogsList.filter { it.sessionId == latestSession.id }.sortedBy { it.setNumber }
                } else emptyList()

                val orderedExerciseIds = mutableListOf<Long>()
                // Collect distinct exercises in order of appearance in the latest session
                for (log in latestSessionLogs) {
                    if (!orderedExerciseIds.contains(log.exerciseId)) {
                        orderedExerciseIds.add(log.exerciseId)
                    }
                }

                // Also collect any other exercises ever done under this plan
                val allPlanLogs = setLogsList.filter { it.sessionId in planSessionIds }
                for (log in allPlanLogs) {
                    if (!orderedExerciseIds.contains(log.exerciseId)) {
                        orderedExerciseIds.add(log.exerciseId)
                    }
                }

                for ((exOrder, exId) in orderedExerciseIds.withIndex()) {
                    val exLogsLatest = latestSessionLogs.filter { it.exerciseId == exId }
                    val targetSets = if (exLogsLatest.isNotEmpty()) exLogsLatest.size else 3
                    val targetReps = if (exLogsLatest.isNotEmpty()) (exLogsLatest.maxOfOrNull { it.reps } ?: 10) else 10
                    val targetWeight = if (exLogsLatest.isNotEmpty()) (exLogsLatest.lastOrNull()?.weight ?: 0.0) else 0.0
                    val note = exLogsLatest.firstOrNull { it.note.isNotBlank() }?.note ?: ""

                    planExercisesList.add(
                        PlanExercise(
                            id = nextPlanExerciseId++,
                            planId = planId,
                            exerciseId = exId,
                            orderIndex = exOrder,
                            targetSets = targetSets,
                            targetReps = targetReps,
                            targetWeight = targetWeight,
                            note = note
                        )
                    )
                }
            }
        }

        return WorkoutExportData(
            exportVersion = 1,
            exportTimestamp = System.currentTimeMillis(),
            exercises = exerciseList,
            workoutPlans = workoutPlansList,
            planExercises = planExercisesList,
            workoutSessions = sessionsList,
            workoutSetLogs = setLogsList,
            activeLockers = lockersList.distinct()
        )
    }

    private fun getCsvValue(tokens: List<String>, headerMap: Map<String, Int>, headerName: String): String {
        val col = headerMap[headerName.lowercase()] ?: return ""
        return if (col < tokens.size) tokens[col].trim() else ""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private suspend fun deleteSampleTestData() {
        val samplePlans = database.workoutPlanDao().getAllPlansList().filter {
            it.name.equals("Full Body (Test)", ignoreCase = true) ||
            it.description.contains("Sample test workout plan", ignoreCase = true)
        }
        for (plan in samplePlans) {
            database.workoutPlanDao().deletePlan(plan)
        }

        val sampleExercises = database.exerciseDao().getAllExercisesList().filter {
            it.defaultNotes == "[TEST_SAMPLE]" ||
            (it.defaultNotes.contains("SAMPLE") && it.name in listOf("Bench Press", "Barbell Squat", "Pull-ups", "Overhead Press", "Barbell Row"))
        }
        for (ex in sampleExercises) {
            database.exerciseDao().deleteExercise(ex)
        }
    }

    private suspend fun importWorkoutExportData(importData: WorkoutExportData, replaceExisting: Boolean): Result<Int> {
        return try {
            database.withTransaction {
                // When backup data is loaded, always delete sample test data
                deleteSampleTestData()

                if (replaceExisting) {
                    // Safe foreign key deletion order
                    database.workoutLogDao().clearSetLogs()
                    database.workoutPlanDao().clearPlanExercises()
                    database.workoutLogDao().clearSessions()
                    database.workoutPlanDao().clearPlans()
                    database.exerciseDao().clearAll()

                    // Direct insertion
                    if (importData.exercises.isNotEmpty()) {
                        database.exerciseDao().insertExercises(importData.exercises)
                    }
                    if (importData.workoutPlans.isNotEmpty()) {
                        database.workoutPlanDao().insertPlans(importData.workoutPlans)
                    }

                    val validExIds = importData.exercises.map { it.id }.toSet()
                    val validPlanIds = importData.workoutPlans.map { it.id }.toSet()

                    val validPlanExercises = importData.planExercises.filter {
                        validPlanIds.contains(it.planId) && validExIds.contains(it.exerciseId)
                    }
                    if (validPlanExercises.isNotEmpty()) {
                        database.workoutPlanDao().insertPlanExercises(validPlanExercises)
                    }

                    if (importData.workoutSessions.isNotEmpty()) {
                        database.workoutLogDao().insertSessions(importData.workoutSessions)
                    }

                    val validSessionIds = importData.workoutSessions.map { it.id }.toSet()
                    val validSetLogs = importData.workoutSetLogs.filter {
                        validSessionIds.contains(it.sessionId) && validExIds.contains(it.exerciseId)
                    }
                    if (validSetLogs.isNotEmpty()) {
                        database.workoutLogDao().insertSetLogs(validSetLogs)
                    }
                } else {
                    // Merge Mode: ID Remapping to protect foreign keys and prevent collisions
                    val existingExercises = database.exerciseDao().getAllExercisesList()
                    val exerciseNameToId = existingExercises.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
                    val exerciseIdMap = mutableMapOf<Long, Long>()

                    for (ex in importData.exercises) {
                        val key = ex.name.trim().lowercase()
                        val existingId = exerciseNameToId[key]
                        if (existingId != null) {
                            exerciseIdMap[ex.id] = existingId
                        } else {
                            val newId = database.exerciseDao().insertExercise(ex.copy(id = 0))
                            exerciseNameToId[key] = newId
                            exerciseIdMap[ex.id] = newId
                        }
                    }

                    val existingPlans = database.workoutPlanDao().getAllPlansList()
                    val planNameToId = existingPlans.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
                    val planIdMap = mutableMapOf<Long, Long>()

                    for (plan in importData.workoutPlans) {
                        val key = plan.name.trim().lowercase()
                        val existingId = planNameToId[key]
                        if (existingId != null) {
                            planIdMap[plan.id] = existingId
                        } else {
                            val newId = database.workoutPlanDao().insertPlan(plan.copy(id = 0))
                            planNameToId[key] = newId
                            planIdMap[plan.id] = newId
                        }
                    }

                    // Plan Exercises
                    val existingPlanExercises = database.workoutPlanDao().getAllPlanExercisesList()
                    val existingPlanExSet = existingPlanExercises.map { "${it.planId}_${it.exerciseId}" }.toSet()
                    val planExercisesToInsert = mutableListOf<PlanExercise>()

                    for (pe in importData.planExercises) {
                        val mappedPlanId = planIdMap[pe.planId]
                        val mappedExId = exerciseIdMap[pe.exerciseId]
                        if (mappedPlanId != null && mappedExId != null) {
                            val comboKey = "${mappedPlanId}_${mappedExId}"
                            if (!existingPlanExSet.contains(comboKey)) {
                                planExercisesToInsert.add(
                                    pe.copy(
                                        id = 0,
                                        planId = mappedPlanId,
                                        exerciseId = mappedExId
                                    )
                                )
                            }
                        }
                    }
                    if (planExercisesToInsert.isNotEmpty()) {
                        database.workoutPlanDao().insertPlanExercises(planExercisesToInsert)
                    }

                    // Workout Sessions & Set Logs
                    val sessionIdMap = mutableMapOf<Long, Long>()
                    for (session in importData.workoutSessions) {
                        val mappedPlanId = session.planId?.let { planIdMap[it] }
                        val newSessionId = database.workoutLogDao().insertSession(
                            session.copy(
                                id = 0,
                                planId = mappedPlanId
                            )
                        )
                        sessionIdMap[session.id] = newSessionId
                    }

                    val setLogsToInsert = mutableListOf<WorkoutSetLog>()
                    for (log in importData.workoutSetLogs) {
                        val mappedSessionId = sessionIdMap[log.sessionId]
                        val mappedExId = exerciseIdMap[log.exerciseId]
                        if (mappedSessionId != null && mappedExId != null) {
                            setLogsToInsert.add(
                                log.copy(
                                    id = 0,
                                    sessionId = mappedSessionId,
                                    exerciseId = mappedExId
                                )
                            )
                        }
                    }
                    if (setLogsToInsert.isNotEmpty()) {
                        database.workoutLogDao().insertSetLogs(setLogsToInsert)
                    }
                }
            }

            // Restore active lockers if present in backup data
            if (importData.activeLockers.isNotEmpty()) {
                saveActiveLockersToPrefs(importData.activeLockers)
            } else if (replaceExisting) {
                clearActiveLockersInPrefs()
            }

            val totalRecords = importData.exercises.size +
                    importData.workoutPlans.size +
                    importData.planExercises.size +
                    importData.workoutSessions.size +
                    importData.workoutSetLogs.size +
                    importData.activeLockers.size

            Result.success(totalRecords)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

