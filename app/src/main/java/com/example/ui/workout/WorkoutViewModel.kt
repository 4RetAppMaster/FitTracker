package com.example.ui.workout

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.Exercise
import com.example.data.entity.PlanExercise
import com.example.data.entity.WorkoutPlan
import com.example.data.entity.WorkoutSetLog
import com.example.data.repository.ExerciseStats
import com.example.data.repository.ExerciseWorkoutHistoryItem
import com.example.data.repository.WorkoutRepository
import com.example.data.update.GitHubUpdateInfo
import com.example.data.update.GitHubUpdateManager
import com.example.data.update.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class WorkoutSetUiState(
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean = false
)

data class WorkoutExerciseUiState(
    val planExerciseId: Long = 0,
    val exercise: Exercise,
    val note: String = "",
    val sets: List<WorkoutSetUiState> = emptyList(),
    val stats: ExerciseStats = ExerciseStats(),
    val targetReps: Int = 10,
    val isCompleted: Boolean = false
)

data class PlanExerciseDraft(
    val exercise: Exercise,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeight: Double = 0.0,
    val note: String = ""
)

data class ActiveSetDraft(
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean = false
)

data class ActiveExerciseDraft(
    val exerciseId: Long,
    val planExerciseId: Long = 0L,
    val note: String = "",
    val sets: List<ActiveSetDraft> = emptyList(),
    val isCompleted: Boolean = false,
    val targetReps: Int = 10
)

data class ActiveWorkoutDraft(
    val planId: Long,
    val workoutNote: String = "",
    val exercises: List<ActiveExerciseDraft> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class WorkoutUiState(
    val currentScreen: AppScreen = AppScreen.WORKOUTS,
    val screenStack: List<AppScreen> = listOf(AppScreen.WORKOUTS),
    val editingPlanId: Long? = null,
    val timerSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val plans: List<WorkoutPlan> = emptyList(),
    val selectedPlan: WorkoutPlan? = null,
    val lastOpenedPlanId: Long? = null,
    val lastSavedPlanId: Long? = null,
    val workoutNote: String = "",
    val exercisesInPlan: List<WorkoutExerciseUiState> = emptyList(),
    val allExercises: List<Exercise> = emptyList(),
    val exerciseStatsMap: Map<Long, ExerciseStats> = emptyMap(),
    val isWeightInKg: Boolean = true,
    val isSaving: Boolean = false,
    val userNotification: String? = null,
    val showExerciseManager: Boolean = false,
    val showImportExport: Boolean = false,
    val showAddExercisePicker: Boolean = false,
    val showCreatePlanSheet: Boolean = false,
    val exportDataString: String = "",
    val exportCsvString: String = "",
    val isImporting: Boolean = false,
    val selectedExerciseForStats: Exercise? = null,
    val exerciseHistory: List<ExerciseWorkoutHistoryItem> = emptyList(),
    val loadedPlanId: Long? = null,
    val githubRepo: String = "",
    val availableUpdate: GitHubUpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float? = null,
    val pendingUpdateApkPath: String? = null,
    val needsInstallPermissionForUpdate: Boolean = false,
    val updateStatusMessage: String? = null
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = WorkoutRepository(database, application)

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var timerBaseSeconds: Long = 0L
    private var timerStartTimeMs: Long = 0L

    private val planExercisesCache = java.util.concurrent.ConcurrentHashMap<Long, List<WorkoutExerciseUiState>>()
    private val planDraftNotesCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
            val savedLastOpenedId = prefs.getLong("last_opened_plan_id", -1L)
            val savedLastSavedId = prefs.getLong("last_saved_plan_id", -1L)
            val savedWeightInKg = prefs.getBoolean("is_weight_in_kg", true)

            // Restore persistent navigation state
            val savedScreenName = prefs.getString("saved_current_screen", null)
            val savedStackStr = prefs.getString("saved_screen_stack", null)
            val savedEditingPlanId = prefs.getLong("editing_plan_id", -1L)

            val restoredScreen = if (savedScreenName != null) {
                try { AppScreen.valueOf(savedScreenName) } catch (e: Exception) { AppScreen.WORKOUTS }
            } else AppScreen.WORKOUTS

            val restoredStack = if (!savedStackStr.isNullOrBlank()) {
                val list = savedStackStr.split(",").mapNotNull { name ->
                    try { AppScreen.valueOf(name) } catch (e: Exception) { null }
                }
                if (list.isEmpty()) listOf(AppScreen.WORKOUTS) else list
            } else listOf(AppScreen.WORKOUTS)

            // Restore persistent workout timer state
            val isTimerRunning = prefs.getBoolean("timer_is_running", false)
            val timerBaseSec = prefs.getLong("timer_base_seconds", 0L)
            val timerStartMs = prefs.getLong("timer_start_timestamp_ms", 0L)

            timerBaseSeconds = timerBaseSec
            timerStartTimeMs = timerStartMs

            val initialElapsed = if (isTimerRunning && timerStartMs > 0) {
                (timerBaseSec + (System.currentTimeMillis() - timerStartMs) / 1000).toInt()
            } else {
                timerBaseSec.toInt()
            }

            _uiState.update { current ->
                current.copy(
                    lastOpenedPlanId = if (savedLastOpenedId > 0) savedLastOpenedId else current.lastOpenedPlanId,
                    lastSavedPlanId = if (savedLastSavedId > 0) savedLastSavedId else current.lastSavedPlanId,
                    isWeightInKg = savedWeightInKg,
                    currentScreen = restoredScreen,
                    screenStack = restoredStack,
                    editingPlanId = if (savedEditingPlanId > 0) savedEditingPlanId else null,
                    isTimerRunning = isTimerRunning,
                    timerSeconds = initialElapsed
                )
            }

            if (isTimerRunning) {
                withContext(Dispatchers.Main) {
                    startTimerTicker()
                }
            }
            val hasDatabaseData = repository.getAllPlansList().isNotEmpty() ||
                repository.getAllExercisesList().isNotEmpty()
            val hasImportedUserFile = prefs.getBoolean("user_backup_csv_imported_v2", false)
            if (hasDatabaseData) {
                // Existing database on the phone found: preserve and use it across app updates
                if (!hasImportedUserFile) {
                    prefs.edit().putBoolean("user_backup_csv_imported_v2", true).apply()
                }
            } else if (!hasImportedUserFile) {
                try {
                    val assetStream = getApplication<Application>().assets.open("default_backup.csv")
                    val csvData = assetStream.bufferedReader().use { it.readText() }
                    if (csvData.isNotBlank()) {
                        val res = repository.importData(csvData, replace = false)
                        if (res.isSuccess) {
                            prefs.edit().putBoolean("user_backup_csv_imported_v2", true).apply()
                        } else {
                            repository.seedSampleTestDataIfEmpty()
                        }
                    } else {
                        repository.seedSampleTestDataIfEmpty()
                    }
                } catch (_: Exception) {
                    repository.seedSampleTestDataIfEmpty()
                }
            } else {
                repository.seedSampleTestDataIfEmpty()
            }
            repository.pruneAllExercisesHistoryToLast10()

            val configuredRepo = GitHubUpdateManager.getConfiguredRepo(getApplication())
            _uiState.update { it.copy(githubRepo = configuredRepo) }
            if (configuredRepo.isNotBlank()) {
                checkForGitHubUpdatesInternal(repo = configuredRepo, manual = false)
            }
        }
        observeAllPlans()
        observeAllExercises()
        observeAllSessionsForLastSaved()
    }

    private fun observeAllSessionsForLastSaved() {
        viewModelScope.launch {
            repository.allSessions.collectLatest { sessions ->
                val latestSessionWithPlan = sessions.filter { it.planId != null }.maxByOrNull { it.timestamp }
                if (latestSessionWithPlan?.planId != null && _uiState.value.lastSavedPlanId == null) {
                    _uiState.update { it.copy(lastSavedPlanId = latestSessionWithPlan.planId) }
                }
            }
        }
    }

    private fun observeAllPlans() {
        viewModelScope.launch {
            repository.allPlans.collectLatest { plans ->
                val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
                val activeWorkoutPlanId = prefs.getLong("current_active_workout_plan_id", -1L)

                _uiState.update { current ->
                    val lastOpenedId = current.lastOpenedPlanId
                    val currentSelected = current.selectedPlan
                    val newSelected = when {
                        plans.isEmpty() -> null
                        currentSelected != null && plans.any { it.id == currentSelected.id } -> plans.first { it.id == currentSelected.id }
                        activeWorkoutPlanId > 0 && plans.any { it.id == activeWorkoutPlanId } -> plans.first { it.id == activeWorkoutPlanId }
                        lastOpenedId != null && plans.any { it.id == lastOpenedId } -> plans.first { it.id == lastOpenedId }
                        else -> plans.firstOrNull()
                    }
                    val effectiveLastOpened = lastOpenedId ?: newSelected?.id
                    current.copy(plans = plans, selectedPlan = newSelected, lastOpenedPlanId = effectiveLastOpened)
                }
                _uiState.value.selectedPlan?.let { loadPlanExercises(it.id) }
            }
        }
    }

    private fun observeAllExercises() {
        viewModelScope.launch {
            repository.allExercises.collectLatest { exercises ->
                val statsMap = repository.getAllExerciseStatsMap()
                _uiState.update { it.copy(allExercises = exercises, exerciseStatsMap = statsMap) }
                refreshCurrentExerciseStats()
            }
        }
    }

    fun refreshAllExerciseStats() {
        viewModelScope.launch {
            val statsMap = repository.getAllExerciseStatsMap()
            _uiState.update { it.copy(exerciseStatsMap = statsMap) }
            refreshCurrentExerciseStats()
        }
    }

    fun resetExercisePR(exerciseId: Long) {
        resetExerciseData(exerciseId = exerciseId, resetPr = true, resetStats = true)
    }

    fun resetExerciseData(exerciseId: Long, resetPr: Boolean, resetStats: Boolean) {
        if (!resetPr && !resetStats) return
        viewModelScope.launch {
            repository.resetExerciseData(exerciseId, resetPr = resetPr, resetStats = resetStats)
            val statsMap = repository.getAllExerciseStatsMap(forceRefresh = true)
            val updatedStats = statsMap[exerciseId] ?: ExerciseStats()
            val updatedHistory = if (_uiState.value.selectedExerciseForStats?.id == exerciseId) {
                repository.getExerciseWorkoutHistory(exerciseId, limit = 10)
            } else {
                _uiState.value.exerciseHistory
            }

            val notificationMsg = when {
                resetPr && resetStats -> "Personal record and stats reset"
                resetPr -> "Personal record reset to No PR yet"
                else -> "Exercise stats and history reset"
            }

            _uiState.update { current ->
                val updatedExercisesInPlan = current.exercisesInPlan.map { exUi ->
                    if (exUi.exercise.id == exerciseId) exUi.copy(stats = updatedStats) else exUi
                }
                current.copy(
                    exerciseStatsMap = statsMap,
                    exercisesInPlan = updatedExercisesInPlan,
                    exerciseHistory = updatedHistory,
                    userNotification = notificationMsg
                )
            }
            refreshCurrentExerciseStats()
            preloadAllPlansInBackground()
        }
    }

    fun deleteExerciseHistoryItem(exerciseId: Long, historyItem: ExerciseWorkoutHistoryItem) {
        viewModelScope.launch {
            repository.deleteExerciseHistoryItem(exerciseId, historyItem)
            val statsMap = repository.getAllExerciseStatsMap(forceRefresh = true)
            val updatedStats = statsMap[exerciseId] ?: ExerciseStats()
            val updatedHistory = repository.getExerciseWorkoutHistory(exerciseId, limit = 10)

            _uiState.update { current ->
                val updatedExercisesInPlan = current.exercisesInPlan.map { exUi ->
                    if (exUi.exercise.id == exerciseId) exUi.copy(stats = updatedStats) else exUi
                }
                current.copy(
                    exerciseStatsMap = statsMap,
                    exercisesInPlan = updatedExercisesInPlan,
                    exerciseHistory = updatedHistory,
                    userNotification = "Saved workout removed from history"
                )
            }
            refreshCurrentExerciseStats()
            preloadAllPlansInBackground()
        }
    }

    fun selectPlan(plan: WorkoutPlan) {
        val preloadedExercises = planExercisesCache[plan.id]
        val preloadedNote = planDraftNotesCache[plan.id] ?: ""
        val isDifferent = _uiState.value.loadedPlanId != plan.id || _uiState.value.selectedPlan?.id != plan.id

        if (isDifferent) {
            _uiState.update { current ->
                current.copy(
                    selectedPlan = plan,
                    loadedPlanId = if (preloadedExercises != null) plan.id else null,
                    exercisesInPlan = preloadedExercises ?: current.exercisesInPlan,
                    workoutNote = if (preloadedExercises != null) preloadedNote else "",
                    lastOpenedPlanId = plan.id
                )
            }
        } else {
            _uiState.update { it.copy(selectedPlan = plan, lastOpenedPlanId = plan.id) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putLong("last_opened_plan_id", plan.id).apply()
        }
        loadPlanExercises(plan.id)
    }

    fun loadPlanExercises(planId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            loadPlanExercisesInternal(planId)
        }
    }

    private suspend fun loadPlanExercisesInternal(planId: Long) = withContext(Dispatchers.IO) {
        val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
        val draftJson = prefs.getString("active_workout_draft_$planId", null)
        val activeDraft = if (!draftJson.isNullOrBlank()) deserializeWorkoutDraft(draftJson) else null

        val planExercises = repository.getPlanExercisesList(planId)
        val currentExercises = _uiState.value.exercisesInPlan
        val isSamePlan = _uiState.value.loadedPlanId == planId
        val allExercisesMap = _uiState.value.allExercises.associateBy { it.id }.ifEmpty {
            repository.getAllExercisesList().associateBy { it.id }
        }
        val cachedStatsMap = repository.getAllExerciseStatsMap()
        val list = mutableListOf<WorkoutExerciseUiState>()

        for (pe in planExercises) {
            val exercise = allExercisesMap[pe.exerciseId] ?: repository.getExerciseById(pe.exerciseId)
            if (exercise != null) {
                val stats = cachedStatsMap[exercise.id] ?: ExerciseStats()
                val targetCount = maxOf(1, pe.targetSets)
                val defaultWeight = stats.lastWeight ?: if (pe.targetWeight > 0) pe.targetWeight else 20.0
                val defaultReps = if (pe.targetReps <= 0) 0 else (stats.lastReps ?: pe.targetReps)

                val existingItem = if (isSamePlan) currentExercises.find { it.exercise.id == exercise.id } else null
                val draftExercise = activeDraft?.exercises?.find { it.exerciseId == exercise.id }

                val setsList: List<WorkoutSetUiState> = when {
                    // Priority 1: Keep in-memory sets if already editing the same plan in this session
                    existingItem != null && existingItem.sets.isNotEmpty() -> {
                        val currentSets = existingItem.sets.toMutableList()
                        if (currentSets.size < targetCount) {
                            val lastKnownWeight = currentSets.lastOrNull()?.weight ?: defaultWeight
                            val lastKnownReps = currentSets.lastOrNull()?.reps ?: defaultReps
                            for (setNum in (currentSets.size + 1)..targetCount) {
                                val prevSet = stats.lastSessionSets.getOrNull(setNum - 1)
                                currentSets.add(
                                    WorkoutSetUiState(
                                        setNumber = setNum,
                                        weight = prevSet?.weight ?: lastKnownWeight,
                                        reps = prevSet?.reps ?: lastKnownReps,
                                        isCompleted = false
                                    )
                                )
                            }
                            currentSets
                        } else if (currentSets.size > targetCount) {
                            val completedCount = currentSets.count { it.isCompleted }
                            if (completedCount <= targetCount) {
                                currentSets.take(targetCount)
                            } else {
                                currentSets
                            }
                        } else {
                            currentSets
                        }
                    }
                    // Priority 2: Restore from persisted active workout draft (e.g. after switching apps)
                    draftExercise != null && draftExercise.sets.isNotEmpty() -> {
                        draftExercise.sets.map { s ->
                            WorkoutSetUiState(
                                setNumber = s.setNumber,
                                weight = s.weight,
                                reps = s.reps,
                                isCompleted = s.isCompleted
                            )
                        }
                    }
                    // Priority 3: Fresh load for this exercise
                    else -> {
                        (1..targetCount).map { setNum ->
                            val setIdx = setNum - 1
                            val prevSet = stats.lastSessionSets.getOrNull(setIdx)
                            val setWeight = prevSet?.weight ?: stats.lastSessionSets.lastOrNull()?.weight ?: defaultWeight
                            val setReps = prevSet?.reps ?: stats.lastSessionSets.lastOrNull()?.reps ?: defaultReps
                            WorkoutSetUiState(
                                setNumber = setNum,
                                weight = setWeight,
                                reps = setReps,
                                isCompleted = false
                            )
                        }
                    }
                }

                val effectiveNote = existingItem?.note ?: draftExercise?.note ?: pe.note
                val isDone = draftExercise?.isCompleted ?: (existingItem?.isCompleted ?: false)

                list.add(
                    WorkoutExerciseUiState(
                        planExerciseId = pe.id,
                        exercise = exercise,
                        note = effectiveNote,
                        sets = setsList,
                        stats = stats,
                        targetReps = pe.targetReps,
                        isCompleted = isDone
                    )
                )
            }
        }

        val effectiveWorkoutNote = if (activeDraft != null && _uiState.value.workoutNote.isBlank()) {
            activeDraft.workoutNote
        } else {
            _uiState.value.workoutNote
        }

        planExercisesCache[planId] = list
        if (effectiveWorkoutNote.isNotBlank()) {
            planDraftNotesCache[planId] = effectiveWorkoutNote
        }

        _uiState.update { it.copy(exercisesInPlan = list, loadedPlanId = planId, workoutNote = effectiveWorkoutNote) }
    }

    private fun preloadAllPlansInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            val plans = repository.getAllPlansList()
            if (plans.isEmpty()) return@launch
            val allPlanExercises = repository.getAllPlanExercisesList()
            val allExercisesMap = repository.getAllExercisesList().associateBy { it.id }
            val statsMap = repository.getAllExerciseStatsMap()
            val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", Context.MODE_PRIVATE)

            val peByPlan = allPlanExercises.groupBy { it.planId }
            for (plan in plans) {
                val peList = peByPlan[plan.id] ?: emptyList()
                val draftJson = prefs.getString("active_workout_draft_${plan.id}", null)
                val activeDraft = if (!draftJson.isNullOrBlank()) deserializeWorkoutDraft(draftJson) else null

                val uiList = mutableListOf<WorkoutExerciseUiState>()
                for (pe in peList) {
                    val exercise = allExercisesMap[pe.exerciseId]
                    if (exercise != null) {
                        val stats = statsMap[exercise.id] ?: ExerciseStats()
                        val targetCount = maxOf(1, pe.targetSets)
                        val defaultWeight = stats.lastWeight ?: if (pe.targetWeight > 0) pe.targetWeight else 20.0
                        val defaultReps = if (pe.targetReps <= 0) 0 else (stats.lastReps ?: pe.targetReps)
                        val draftExercise = activeDraft?.exercises?.find { it.exerciseId == exercise.id }

                        val setsList: List<WorkoutSetUiState> = if (draftExercise != null && draftExercise.sets.isNotEmpty()) {
                            draftExercise.sets.map { s ->
                                WorkoutSetUiState(
                                    setNumber = s.setNumber,
                                    weight = s.weight,
                                    reps = s.reps,
                                    isCompleted = s.isCompleted
                                )
                            }
                        } else {
                            (1..targetCount).map { setNum ->
                                val setIdx = setNum - 1
                                val prevSet = stats.lastSessionSets.getOrNull(setIdx)
                                val setWeight = prevSet?.weight ?: stats.lastSessionSets.lastOrNull()?.weight ?: defaultWeight
                                val setReps = prevSet?.reps ?: stats.lastSessionSets.lastOrNull()?.reps ?: defaultReps
                                WorkoutSetUiState(
                                    setNumber = setNum,
                                    weight = setWeight,
                                    reps = setReps,
                                    isCompleted = false
                                )
                            }
                        }
                        val effectiveNote = draftExercise?.note ?: pe.note
                        val isDone = draftExercise?.isCompleted ?: false

                        uiList.add(
                            WorkoutExerciseUiState(
                                planExerciseId = pe.id,
                                exercise = exercise,
                                note = effectiveNote,
                                sets = setsList,
                                stats = stats,
                                targetReps = pe.targetReps,
                                isCompleted = isDone
                            )
                        )
                    }
                }
                planExercisesCache[plan.id] = uiList
                if (activeDraft != null && activeDraft.workoutNote.isNotBlank()) {
                    planDraftNotesCache[plan.id] = activeDraft.workoutNote
                }
            }

            val selected = _uiState.value.selectedPlan
            if (selected != null && _uiState.value.loadedPlanId != selected.id) {
                val preloaded = planExercisesCache[selected.id]
                if (preloaded != null) {
                    val note = planDraftNotesCache[selected.id] ?: ""
                    _uiState.update { it.copy(exercisesInPlan = preloaded, loadedPlanId = selected.id, workoutNote = note) }
                }
            }
        }
    }

    // --- Navigation State Management (persisted across app switches) ---
    fun navigateToScreen(screen: AppScreen) {
        val current = _uiState.value.currentScreen
        if (current != screen) {
            val newStack = _uiState.value.screenStack.filter { it != screen } + screen
            _uiState.update { it.copy(currentScreen = screen, screenStack = newStack) }
            saveNavigationState(screen, newStack)
        }
    }

    fun navigateBack() {
        val stack = _uiState.value.screenStack
        if (stack.size > 1) {
            val newStack = stack.dropLast(1)
            val newScreen = newStack.last()
            _uiState.update { it.copy(currentScreen = newScreen, screenStack = newStack) }
            saveNavigationState(newScreen, newStack)
        } else if (_uiState.value.currentScreen != AppScreen.WORKOUTS) {
            val newStack = listOf(AppScreen.WORKOUTS)
            _uiState.update { it.copy(currentScreen = AppScreen.WORKOUTS, screenStack = newStack) }
            saveNavigationState(AppScreen.WORKOUTS, newStack)
        }
    }

    fun openExerciseStats(exercise: Exercise) {
        viewModelScope.launch {
            val currentStack = _uiState.value.screenStack
            val newStack = currentStack.filter { it != AppScreen.STATISTICS } + AppScreen.STATISTICS
            _uiState.update { current ->
                current.copy(
                    selectedExerciseForStats = exercise,
                    currentScreen = AppScreen.STATISTICS,
                    screenStack = newStack
                )
            }
            saveNavigationState(AppScreen.STATISTICS, newStack)

            val history = repository.getExerciseWorkoutHistory(exercise.id, limit = 10)
            val stats = repository.getExerciseStats(exercise.id)
            _uiState.update { state ->
                val newMap = state.exerciseStatsMap.toMutableMap()
                newMap[exercise.id] = stats
                state.copy(exerciseHistory = history, exerciseStatsMap = newMap)
            }
        }
    }

    fun setEditingPlan(plan: WorkoutPlan?) {
        _uiState.update { it.copy(editingPlanId = plan?.id) }
        val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", Context.MODE_PRIVATE)
        if (plan != null) {
            prefs.edit().putLong("editing_plan_id", plan.id).apply()
        } else {
            prefs.edit().remove("editing_plan_id").apply()
        }
    }

    private fun saveNavigationState(screen: AppScreen, stack: List<AppScreen>) {
        val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", Context.MODE_PRIVATE)
        val stackStr = stack.joinToString(",") { it.name }
        prefs.edit()
            .putString("saved_current_screen", screen.name)
            .putString("saved_screen_stack", stackStr)
            .apply()
    }

    // --- Timer Management (accurate wall-clock elapsed time across app switches) ---
    fun toggleTimer() {
        if (_uiState.value.isTimerRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    fun startTimer() {
        timerStartTimeMs = System.currentTimeMillis()
        _uiState.update { it.copy(isTimerRunning = true) }
        saveTimerStateToPrefs()
        startTimerTicker()
    }

    fun pauseTimer() {
        if (timerStartTimeMs > 0) {
            val additional = (System.currentTimeMillis() - timerStartTimeMs) / 1000
            timerBaseSeconds += additional
            timerStartTimeMs = 0L
        }
        _uiState.update { it.copy(isTimerRunning = false, timerSeconds = timerBaseSeconds.toInt()) }
        saveTimerStateToPrefs()
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        timerBaseSeconds = 0L
        timerStartTimeMs = 0L
        _uiState.update { it.copy(isTimerRunning = false, timerSeconds = 0) }
        saveTimerStateToPrefs()
        timerJob?.cancel()
        timerJob = null
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isTimerRunning) {
                val currentElapsed = (if (timerStartTimeMs > 0) {
                    timerBaseSeconds + ((System.currentTimeMillis() - timerStartTimeMs) / 1000)
                } else {
                    timerBaseSeconds
                }).toInt()
                if (_uiState.value.timerSeconds != currentElapsed) {
                    _uiState.update { it.copy(timerSeconds = currentElapsed) }
                }
                delay(500)
            }
        }
    }

    private fun saveTimerStateToPrefs() {
        val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("timer_is_running", _uiState.value.isTimerRunning)
            .putLong("timer_base_seconds", timerBaseSeconds)
            .putLong("timer_start_timestamp_ms", timerStartTimeMs)
            .apply()
    }

    // --- Active Workout Draft Persistence (Auto-Save on every change) ---
    fun persistActiveWorkoutDraft() {
        val currentPlan = _uiState.value.selectedPlan ?: return
        val loadedPlanId = _uiState.value.loadedPlanId
        if (loadedPlanId != null && loadedPlanId != currentPlan.id) return
        val exercises = _uiState.value.exercisesInPlan
        if (exercises.isEmpty()) return

        val draft = ActiveWorkoutDraft(
            planId = currentPlan.id,
            workoutNote = _uiState.value.workoutNote,
            exercises = exercises.map { ex ->
                ActiveExerciseDraft(
                    exerciseId = ex.exercise.id,
                    planExerciseId = ex.planExerciseId,
                    note = ex.note,
                    isCompleted = ex.isCompleted,
                    targetReps = ex.targetReps,
                    sets = ex.sets.map { s ->
                        ActiveSetDraft(
                            setNumber = s.setNumber,
                            weight = s.weight,
                            reps = s.reps,
                            isCompleted = s.isCompleted
                        )
                    }
                )
            }
        )
        viewModelScope.launch(Dispatchers.IO) {
            val json = serializeWorkoutDraft(draft)
            val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("active_workout_draft_${currentPlan.id}", json)
                .putLong("current_active_workout_plan_id", currentPlan.id)
                .apply()
        }
    }

    fun clearActiveWorkoutDraft(planId: Long? = null) {
        val targetId = planId ?: _uiState.value.selectedPlan?.id
        val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (targetId != null) {
            editor.remove("active_workout_draft_$targetId")
        }
        editor.remove("current_active_workout_plan_id")
        editor.apply()
    }

    fun discardActiveWorkout() {
        val planId = _uiState.value.selectedPlan?.id
        clearActiveWorkoutDraft(planId)
        resetTimer()
        _uiState.update { current ->
            current.copy(
                workoutNote = "",
                exercisesInPlan = current.exercisesInPlan.map { ex ->
                    ex.copy(isCompleted = false, sets = ex.sets.map { it.copy(isCompleted = false) })
                }
            )
        }
        if (planId != null) {
            viewModelScope.launch {
                loadFreshPlanExercises(planId)
            }
        }
        navigateToScreen(AppScreen.WORKOUTS)
    }

    private suspend fun loadFreshPlanExercises(planId: Long) {
        val planExercises = repository.getPlanExercisesList(planId)
        val list = mutableListOf<WorkoutExerciseUiState>()
        for (pe in planExercises) {
            val exercise = repository.getExerciseById(pe.exerciseId)
            if (exercise != null) {
                val stats = repository.getExerciseStats(exercise.id)
                val targetCount = maxOf(1, pe.targetSets)
                val defaultWeight = stats.lastWeight ?: if (pe.targetWeight > 0) pe.targetWeight else 20.0
                val defaultReps = if (pe.targetReps <= 0) 0 else (stats.lastReps ?: pe.targetReps)
                val sets = (1..targetCount).map { setNum ->
                    val setIdx = setNum - 1
                    val prevSet = stats.lastSessionSets.getOrNull(setIdx)
                    val setWeight = prevSet?.weight ?: stats.lastSessionSets.lastOrNull()?.weight ?: defaultWeight
                    val setReps = prevSet?.reps ?: stats.lastSessionSets.lastOrNull()?.reps ?: defaultReps
                    WorkoutSetUiState(
                        setNumber = setNum,
                        weight = setWeight,
                        reps = setReps,
                        isCompleted = false
                    )
                }
                list.add(
                    WorkoutExerciseUiState(
                        planExerciseId = pe.id,
                        exercise = exercise,
                        note = pe.note,
                        sets = sets,
                        stats = stats,
                        targetReps = pe.targetReps,
                        isCompleted = false
                    )
                )
            }
        }
        _uiState.update { it.copy(exercisesInPlan = list) }
    }

    fun flushStateToStorage() {
        persistActiveWorkoutDraft()
        saveTimerStateToPrefs()
        saveNavigationState(_uiState.value.currentScreen, _uiState.value.screenStack)
    }

    private fun serializeWorkoutDraft(draft: ActiveWorkoutDraft): String {
        val json = JSONObject()
        json.put("planId", draft.planId)
        json.put("workoutNote", draft.workoutNote)
        json.put("timestamp", draft.timestamp)
        val exArray = JSONArray()
        for (ex in draft.exercises) {
            val exObj = JSONObject()
            exObj.put("exerciseId", ex.exerciseId)
            exObj.put("planExerciseId", ex.planExerciseId)
            exObj.put("note", ex.note)
            exObj.put("isCompleted", ex.isCompleted)
            exObj.put("targetReps", ex.targetReps)
            val setArray = JSONArray()
            for (set in ex.sets) {
                val setObj = JSONObject()
                setObj.put("setNumber", set.setNumber)
                setObj.put("weight", set.weight)
                setObj.put("reps", set.reps)
                setObj.put("isCompleted", set.isCompleted)
                setArray.put(setObj)
            }
            exObj.put("sets", setArray)
            exArray.put(exObj)
        }
        json.put("exercises", exArray)
        return json.toString()
    }

    private fun deserializeWorkoutDraft(jsonStr: String): ActiveWorkoutDraft? {
        return try {
            val json = JSONObject(jsonStr)
            val planId = json.getLong("planId")
            val workoutNote = json.optString("workoutNote", "")
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val exArray = json.optJSONArray("exercises") ?: JSONArray()
            val exercises = mutableListOf<ActiveExerciseDraft>()
            for (i in 0 until exArray.length()) {
                val exObj = exArray.getJSONObject(i)
                val exerciseId = exObj.getLong("exerciseId")
                val planExerciseId = exObj.optLong("planExerciseId", 0L)
                val note = exObj.optString("note", "")
                val isCompleted = exObj.optBoolean("isCompleted", false)
                val targetReps = exObj.optInt("targetReps", 10)
                val setArray = exObj.optJSONArray("sets") ?: JSONArray()
                val sets = mutableListOf<ActiveSetDraft>()
                for (j in 0 until setArray.length()) {
                    val setObj = setArray.getJSONObject(j)
                    sets.add(
                        ActiveSetDraft(
                            setNumber = setObj.getInt("setNumber"),
                            weight = setObj.getDouble("weight"),
                            reps = setObj.getInt("reps"),
                            isCompleted = setObj.optBoolean("isCompleted", false)
                        )
                    )
                }
                exercises.add(
                    ActiveExerciseDraft(
                        exerciseId = exerciseId,
                        planExerciseId = planExerciseId,
                        note = note,
                        sets = sets,
                        isCompleted = isCompleted,
                        targetReps = targetReps
                    )
                )
            }
            ActiveWorkoutDraft(planId, workoutNote, exercises, timestamp)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun refreshCurrentExerciseStats() {
        val currentList = _uiState.value.exercisesInPlan
        val updated = currentList.map { item ->
            val stats = repository.getExerciseStats(item.exercise.id)
            item.copy(stats = stats)
        }
        _uiState.update { it.copy(exercisesInPlan = updated) }
    }

    fun reorderPlans(fromIndex: Int, toIndex: Int) {
        val currentPlans = _uiState.value.plans
        if (fromIndex in currentPlans.indices && toIndex in currentPlans.indices && fromIndex != toIndex) {
            val updated = currentPlans.toMutableList()
            val item = updated.removeAt(fromIndex)
            updated.add(toIndex, item)
            val reordered = updated.mapIndexed { idx, p -> p.copy(orderIndex = idx) }
            _uiState.update { it.copy(plans = reordered) }
            viewModelScope.launch(Dispatchers.IO) {
                repository.updatePlans(reordered)
            }
        }
    }

    fun movePlanUp(plan: WorkoutPlan) {
        val currentPlans = _uiState.value.plans
        val index = currentPlans.indexOfFirst { it.id == plan.id }
        if (index > 0) {
            val updated = currentPlans.toMutableList()
            val item = updated.removeAt(index)
            updated.add(index - 1, item)
            val reordered = updated.mapIndexed { idx, p -> p.copy(orderIndex = idx) }
            _uiState.update { it.copy(plans = reordered) }
            viewModelScope.launch(Dispatchers.IO) {
                repository.updatePlans(reordered)
            }
        }
    }

    fun movePlanDown(plan: WorkoutPlan) {
        val currentPlans = _uiState.value.plans
        val index = currentPlans.indexOfFirst { it.id == plan.id }
        if (index in 0 until currentPlans.size - 1) {
            val updated = currentPlans.toMutableList()
            val item = updated.removeAt(index)
            updated.add(index + 1, item)
            val reordered = updated.mapIndexed { idx, p -> p.copy(orderIndex = idx) }
            _uiState.update { it.copy(plans = reordered) }
            viewModelScope.launch(Dispatchers.IO) {
                repository.updatePlans(reordered)
            }
        }
    }

    fun moveExerciseUpInPlan(index: Int) {
        _uiState.update { current ->
            if (index > 0 && index < current.exercisesInPlan.size) {
                val list = current.exercisesInPlan.toMutableList()
                val item = list.removeAt(index)
                list.add(index - 1, item)
                current.copy(exercisesInPlan = list)
            } else current
        }
        persistCurrentPlanExercisesOrder()
    }

    fun moveExerciseDownInPlan(index: Int) {
        _uiState.update { current ->
            if (index >= 0 && index < current.exercisesInPlan.size - 1) {
                val list = current.exercisesInPlan.toMutableList()
                val item = list.removeAt(index)
                list.add(index + 1, item)
                current.copy(exercisesInPlan = list)
            } else current
        }
        persistCurrentPlanExercisesOrder()
    }

    private fun persistCurrentPlanExercisesOrder() {
        val planId = _uiState.value.selectedPlan?.id ?: return
        val currentList = _uiState.value.exercisesInPlan
        viewModelScope.launch(Dispatchers.IO) {
            currentList.forEachIndexed { idx, item ->
                if (item.planExerciseId > 0) {
                    repository.updatePlanExercise(
                        PlanExercise(
                            id = item.planExerciseId,
                            planId = planId,
                            exerciseId = item.exercise.id,
                            orderIndex = idx,
                            targetSets = item.sets.size,
                            targetReps = item.sets.firstOrNull()?.reps ?: 10,
                            targetWeight = item.sets.firstOrNull()?.weight ?: 0.0,
                            note = item.note
                        )
                    )
                }
            }
        }
    }

    fun updateWorkoutNote(note: String) {
        _uiState.update { it.copy(workoutNote = note) }
        persistActiveWorkoutDraft()
    }

    fun updateExerciseNote(exerciseId: Long, note: String) {
        _uiState.update { current ->
            val updated = current.exercisesInPlan.map { item ->
                if (item.exercise.id == exerciseId) {
                    item.copy(note = note)
                } else item
            }
            current.copy(exercisesInPlan = updated)
        }
        persistActiveWorkoutDraft()
        syncPlanExerciseDraftImmediate(exerciseId)
    }

    fun addSetToExercise(exerciseId: Long) {
        _uiState.update { current ->
            val updated = current.exercisesInPlan.map { item ->
                if (item.exercise.id == exerciseId) {
                    val lastSet = item.sets.lastOrNull()
                    val newSet = WorkoutSetUiState(
                        setNumber = item.sets.size + 1,
                        weight = lastSet?.weight ?: 20.0,
                        reps = lastSet?.reps ?: 10,
                        isCompleted = false
                    )
                    item.copy(sets = item.sets + newSet)
                } else item
            }
            current.copy(exercisesInPlan = updated)
        }
        persistActiveWorkoutDraft()
        syncPlanExerciseDraftImmediate(exerciseId)
    }

    fun removeSetFromExercise(exerciseId: Long, setIndex: Int) {
        _uiState.update { current ->
            val updated = current.exercisesInPlan.map { item ->
                if (item.exercise.id == exerciseId && item.sets.size > 1) {
                    val mutable = item.sets.toMutableList()
                    if (setIndex in mutable.indices) {
                        mutable.removeAt(setIndex)
                    }
                    val reindexed = mutable.mapIndexed { idx, s -> s.copy(setNumber = idx + 1) }
                    item.copy(sets = reindexed)
                } else item
            }
            current.copy(exercisesInPlan = updated)
        }
        persistActiveWorkoutDraft()
        syncPlanExerciseDraftImmediate(exerciseId)
    }

    fun updateSet(exerciseId: Long, setIndex: Int, weight: Double, reps: Int) {
        val sanitizedWeight = if (weight == -2.0) -2.0 else if (weight < 0.0) -1.0 else minOf(999.9, ((weight * 10).roundToInt() / 10.0))
        val sanitizedReps = reps.coerceIn(0, 99)
        _uiState.update { current ->
            val updated = current.exercisesInPlan.map { item ->
                if (item.exercise.id == exerciseId) {
                    val mutable = item.sets.toMutableList()
                    if (setIndex in mutable.indices) {
                        mutable[setIndex] = mutable[setIndex].copy(
                            weight = sanitizedWeight,
                            reps = sanitizedReps
                        )
                    }
                    item.copy(sets = mutable)
                } else item
            }
            current.copy(exercisesInPlan = updated)
        }
        persistActiveWorkoutDraft()
        syncPlanExerciseDraftImmediate(exerciseId)
    }

    fun toggleSetCompleted(exerciseId: Long, setIndex: Int) {
        _uiState.update { current ->
            val updated = current.exercisesInPlan.map { item ->
                if (item.exercise.id == exerciseId) {
                    val mutable = item.sets.toMutableList()
                    if (setIndex in mutable.indices) {
                        val currentSet = mutable[setIndex]
                        mutable[setIndex] = currentSet.copy(isCompleted = !currentSet.isCompleted)
                    }
                    item.copy(sets = mutable)
                } else item
            }
            current.copy(exercisesInPlan = updated)
        }
        persistActiveWorkoutDraft()
        syncPlanExerciseDraftImmediate(exerciseId)
    }

    fun toggleExerciseCompleted(exerciseId: Long) {
        _uiState.update { current ->
            val updated = current.exercisesInPlan.map { item ->
                if (item.exercise.id == exerciseId) {
                    val newStatus = !item.isCompleted
                    val updatedSets = if (newStatus) {
                        item.sets.map { it.copy(isCompleted = true) }
                    } else {
                        item.sets
                    }
                    item.copy(isCompleted = newStatus, sets = updatedSets)
                } else item
            }
            current.copy(exercisesInPlan = updated)
        }
        persistActiveWorkoutDraft()
        syncPlanExerciseDraftImmediate(exerciseId)
    }

    /**
     * Immediately syncs in-progress draft changes to PlanExercise in Room database
     * without modifying PRs. PRs ONLY update when "Save Workout" is pressed.
     */
    private fun syncPlanExerciseDraftImmediate(exerciseId: Long) {
        val currentPlan = _uiState.value.selectedPlan ?: return
        val loadedPlanId = _uiState.value.loadedPlanId
        if (loadedPlanId == null || loadedPlanId != currentPlan.id) return
        val exUi = _uiState.value.exercisesInPlan.find { it.exercise.id == exerciseId } ?: return
        if (exUi.planExerciseId <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            val existingPe = repository.getPlanExercisesList(currentPlan.id).find { it.id == exUi.planExerciseId }
            if (existingPe != null) {
                val lastWeight = exUi.sets.lastOrNull { it.weight != 0.0 }?.weight ?: exUi.sets.firstOrNull()?.weight ?: 0.0
                val bestReps = exUi.sets.maxOfOrNull { it.reps } ?: exUi.targetReps
                repository.updatePlanExercise(
                    existingPe.copy(
                        targetSets = exUi.sets.size,
                        targetReps = bestReps,
                        targetWeight = lastWeight,
                        note = exUi.note
                    )
                )
            }
        }
    }

    fun addExerciseToCurrentPlan(
        exercise: Exercise,
        targetSets: Int = 3,
        targetReps: Int = 10,
        targetWeight: Double = 0.0,
        note: String = ""
    ) {
        addMultipleExercisesToCurrentPlan(listOf(exercise))
    }

    fun addMultipleExercisesToCurrentPlan(exercises: List<Exercise>) {
        val currentPlan = _uiState.value.selectedPlan ?: return
        val loadedPlanId = _uiState.value.loadedPlanId
        if (loadedPlanId != null && loadedPlanId != currentPlan.id) return
        if (exercises.isEmpty()) return

        viewModelScope.launch {
            val newExerciseUiList = mutableListOf<WorkoutExerciseUiState>()
            for (exercise in exercises) {
                if (_uiState.value.exercisesInPlan.any { it.exercise.id == exercise.id } || newExerciseUiList.any { it.exercise.id == exercise.id }) {
                    continue
                }
                val stats = repository.getExerciseStats(exercise.id)
                val initialWeight = stats.lastWeight ?: 20.0
                val initialReps = stats.lastReps ?: 10

                val peId = repository.addExerciseToPlan(
                    planId = currentPlan.id,
                    exerciseId = exercise.id,
                    targetSets = 3,
                    targetReps = initialReps,
                    targetWeight = initialWeight,
                    note = ""
                )

                val newSets = (1..3).map { sNum ->
                    WorkoutSetUiState(
                        setNumber = sNum,
                        weight = initialWeight,
                        reps = initialReps,
                        isCompleted = false
                    )
                }

                newExerciseUiList.add(
                    WorkoutExerciseUiState(
                        planExerciseId = peId,
                        exercise = exercise,
                        note = "",
                        sets = newSets,
                        stats = stats,
                        targetReps = initialReps,
                        isCompleted = false
                    )
                )
            }

            if (newExerciseUiList.isNotEmpty()) {
                _uiState.update { current ->
                    current.copy(
                        exercisesInPlan = current.exercisesInPlan + newExerciseUiList,
                        showAddExercisePicker = false,
                        userNotification = "Added ${newExerciseUiList.size} exercise${if (newExerciseUiList.size > 1) "s" else ""} to plan!"
                    )
                }
                persistActiveWorkoutDraft()
            }
        }
    }

    fun removeExerciseFromPlan(item: WorkoutExerciseUiState) {
        viewModelScope.launch {
            if (item.planExerciseId > 0 && _uiState.value.selectedPlan != null) {
                repository.deletePlanExercise(
                    PlanExercise(
                        id = item.planExerciseId,
                        planId = _uiState.value.selectedPlan!!.id,
                        exerciseId = item.exercise.id
                    )
                )
            }
            _uiState.update { current ->
                current.copy(
                    exercisesInPlan = current.exercisesInPlan.filterNot { it.exercise.id == item.exercise.id },
                    userNotification = "Removed ${item.exercise.name}"
                )
            }
            persistActiveWorkoutDraft()
        }
    }

    fun saveFullWorkoutPlan(
        planId: Long?,
        name: String,
        description: String = "",
        iconName: String = "FitnessCenter",
        colorHex: String = "#FF6B00",
        exerciseDrafts: List<PlanExerciseDraft>,
        onComplete: ((WorkoutPlan) -> Unit)? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val finalPlan: WorkoutPlan
            val targetPlanId: Long
            if (planId != null && planId > 0) {
                targetPlanId = planId
                val existing = _uiState.value.plans.firstOrNull { it.id == planId }
                val updated = (existing ?: WorkoutPlan(id = planId, name = name.trim(), description = description.trim()))
                    .copy(
                        name = name.trim(),
                        description = description.trim(),
                        iconName = iconName,
                        colorHex = colorHex
                    )
                repository.updatePlan(updated)
                finalPlan = updated
            } else {
                val newPlanOrder = _uiState.value.plans.size
                targetPlanId = repository.createPlan(
                    name = name.trim(),
                    description = description.trim(),
                    iconName = iconName,
                    colorHex = colorHex,
                    orderIndex = newPlanOrder
                )
                finalPlan = WorkoutPlan(
                    id = targetPlanId,
                    name = name.trim(),
                    description = description.trim(),
                    iconName = iconName,
                    colorHex = colorHex,
                    orderIndex = newPlanOrder
                )
            }

            // Save ordered plan exercises atomically
            val entities = exerciseDrafts.mapIndexed { idx, draft ->
                PlanExercise(
                    planId = targetPlanId,
                    exerciseId = draft.exercise.id,
                    orderIndex = idx,
                    targetSets = draft.targetSets,
                    targetReps = draft.targetReps,
                    targetWeight = draft.targetWeight,
                    note = draft.note
                )
            }
            repository.setPlanExercises(targetPlanId, entities)

            val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putLong("last_opened_plan_id", finalPlan.id).apply()

            loadPlanExercisesInternal(targetPlanId)
            _uiState.update { current ->
                current.copy(
                    isSaving = false,
                    selectedPlan = finalPlan,
                    lastOpenedPlanId = finalPlan.id,
                    userNotification = if (planId != null && planId > 0) "Updated \"${finalPlan.name}\"" else "Created plan \"${finalPlan.name}\""
                )
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(finalPlan)
            }
        }
    }

    suspend fun loadPlanDrafts(planId: Long): List<PlanExerciseDraft> = withContext(Dispatchers.IO) {
        val planExercises = repository.getPlanExercisesList(planId)
        val allExercisesMap = _uiState.value.allExercises.associateBy { it.id }.ifEmpty {
            repository.getAllExercisesList().associateBy { it.id }
        }
        val drafts = mutableListOf<PlanExerciseDraft>()
        for (pe in planExercises) {
            val ex = allExercisesMap[pe.exerciseId] ?: repository.getExerciseById(pe.exerciseId)
            if (ex != null) {
                drafts.add(
                    PlanExerciseDraft(
                        exercise = ex,
                        targetSets = pe.targetSets,
                        targetReps = pe.targetReps,
                        targetWeight = pe.targetWeight,
                        note = pe.note
                    )
                )
            }
        }
        drafts
    }

    fun createNewPlan(
        name: String,
        description: String = "",
        iconName: String = "FitnessCenter",
        colorHex: String = "#FF6B00"
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newOrder = _uiState.value.plans.size
            val newId = repository.createPlan(
                name = name.trim(),
                description = description.trim(),
                iconName = iconName,
                colorHex = colorHex,
                orderIndex = newOrder
            )
            val plan = WorkoutPlan(
                id = newId,
                name = name.trim(),
                description = description.trim(),
                iconName = iconName,
                colorHex = colorHex,
                orderIndex = newOrder
            )
            _uiState.update {
                it.copy(
                    selectedPlan = plan,
                    exercisesInPlan = emptyList(),
                    showCreatePlanSheet = false,
                    userNotification = "Created plan \"${name.trim()}\""
                )
            }
        }
    }

    fun deletePlan(plan: WorkoutPlan) {
        viewModelScope.launch {
            repository.deletePlan(plan)
            _uiState.update { it.copy(userNotification = "Deleted \"${plan.name}\"") }
        }
    }

    fun updatePlan(plan: WorkoutPlan, newName: String, newDescription: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = plan.copy(name = newName.trim(), description = newDescription.trim())
            repository.updatePlan(updated)
            _uiState.update { current ->
                val selected = if (current.selectedPlan?.id == plan.id) updated else current.selectedPlan
                current.copy(
                    selectedPlan = selected,
                    userNotification = "Updated \"${updated.name}\""
                )
            }
        }
    }

    fun deleteCurrentPlan() {
        val selected = _uiState.value.selectedPlan ?: return
        deletePlan(selected)
    }

    fun saveWorkoutSession(onSuccess: (() -> Unit)? = null) {
        val currentPlan = _uiState.value.selectedPlan
        val planName = currentPlan?.name ?: "Workout Session"
        val exercises = _uiState.value.exercisesInPlan
        val workoutNote = _uiState.value.workoutNote

        if (exercises.isEmpty()) {
            _uiState.update { it.copy(userNotification = "Add at least one exercise before saving!") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val setLogs = mutableListOf<WorkoutSetLog>()
            var completedCount = 0

            exercises.forEach { exUi ->
                exUi.sets.forEach { setUi ->
                    if (setUi.isCompleted || setUi.weight != 0.0) {
                        completedCount++
                    }
                    setLogs.add(
                        WorkoutSetLog(
                            sessionId = 0,
                            exerciseId = exUi.exercise.id,
                            setNumber = setUi.setNumber,
                            reps = setUi.reps,
                            weight = setUi.weight,
                            note = exUi.note,
                            isCompleted = true,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            repository.logWorkoutSession(
                planId = currentPlan?.id,
                planName = planName,
                generalNote = workoutNote,
                setLogs = setLogs
            )

            // Update plan exercises with latest target sets and weights, strictly preserving fixed targetReps
            if (currentPlan != null) {
                val currentPlanExercises = repository.getPlanExercisesList(currentPlan.id).associateBy { it.id }
                exercises.forEach { exUi ->
                    val pe = currentPlanExercises[exUi.planExerciseId]
                    if (pe != null) {
                        val lastWeight = exUi.sets.lastOrNull { it.weight != 0.0 }?.weight ?: exUi.sets.firstOrNull()?.weight ?: 0.0
                        repository.updatePlanExercise(
                            pe.copy(
                                targetSets = exUi.sets.size,
                                targetWeight = lastWeight,
                                note = exUi.note
                            )
                        )
                    }
                }
            }

            // Refresh stats for all exercises across the entire database in real time
            val updatedStatsMap = repository.getAllExerciseStatsMap(forceRefresh = true)
            _uiState.update { it.copy(exerciseStatsMap = updatedStatsMap) }
            preloadAllPlansInBackground()
            refreshCurrentExerciseStats()

            // Clear the active draft and reset timer
            clearActiveWorkoutDraft(currentPlan?.id)
            resetTimer()

            val savedPlanId = currentPlan?.id
            if (savedPlanId != null) {
                withContext(Dispatchers.IO) {
                    val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_saved_plan_id", savedPlanId).apply()
                }
            }

            _uiState.update { current ->
                current.copy(
                    isSaving = false,
                    lastSavedPlanId = savedPlanId ?: current.lastSavedPlanId,
                    workoutNote = "",
                    // Reset set checkboxes and unlock all exercise boxes for next session
                    exercisesInPlan = current.exercisesInPlan.map { ex ->
                        ex.copy(isCompleted = false, sets = ex.sets.map { it.copy(isCompleted = false) })
                    },
                    userNotification = "Workout saved! $completedCount sets logged successfully. Stats updated!"
                )
            }

            navigateToScreen(AppScreen.WORKOUTS)

            withContext(Dispatchers.Main) {
                onSuccess?.invoke()
            }
        }
    }

    fun createCustomExercise(name: String, category: String, equipment: String = "Other", defaultNote: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val ex = Exercise(
                name = name.trim(),
                category = category.trim(),
                equipment = equipment.trim(),
                isCustom = true,
                defaultNotes = defaultNote.trim()
            )
            repository.insertExercise(ex)
            _uiState.update { it.copy(userNotification = "Created exercise \"${name.trim()}\"") }
        }
    }

    fun updateExercise(exercise: Exercise) {
        if (exercise.name.isBlank()) return
        viewModelScope.launch {
            repository.updateExercise(exercise)
            _uiState.update { current ->
                current.copy(
                    exercisesInPlan = current.exercisesInPlan.map { exUi ->
                        if (exUi.exercise.id == exercise.id) exUi.copy(exercise = exercise) else exUi
                    },
                    userNotification = "Updated exercise \"${exercise.name.trim()}\""
                )
            }
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch {
            repository.deleteExercise(exercise)
            _uiState.update { current ->
                current.copy(
                    exercisesInPlan = current.exercisesInPlan.filterNot { it.exercise.id == exercise.id },
                    userNotification = "Deleted exercise ${exercise.name}"
                )
            }
        }
    }

    fun toggleWeightUnit() {
        val newUnit = !_uiState.value.isWeightInKg
        _uiState.update { it.copy(isWeightInKg = newUnit) }
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_weight_in_kg", newUnit).apply()
        }
    }

    fun prepareExportData() {
        viewModelScope.launch {
            flushStateToStorage()
            val json = repository.exportJson()
            val csv = repository.exportCsv()
            _uiState.update {
                it.copy(
                    exportDataString = json,
                    exportCsvString = csv,
                    showImportExport = true
                )
            }
        }
    }

    fun exportAndShareCsv(context: android.content.Context) {
        viewModelScope.launch {
            val csv = repository.exportCsv()
            _uiState.update { it.copy(exportCsvString = csv) }
            
            withContext(Dispatchers.Main) {
                try {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, csv)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "FitTracker Workout History & Records.csv")
                        type = "text/csv"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Download / Share Workout CSV")
                    shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                } catch (e: Exception) {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, csv)
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Download / Share Workout CSV")
                    shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                }
            }
        }
    }

    fun importData(dataString: String, replace: Boolean) {
        if (dataString.isBlank()) {
            _uiState.update { it.copy(userNotification = "Import error: File/data cannot be empty.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = repository.importData(dataString, replace)
            result.onSuccess { count ->
                val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
                // Clear any lingering active drafts from previous workouts
                prefs.all.keys.filter { it.startsWith("active_workout_draft_") }.forEach { key ->
                    prefs.edit().remove(key).apply()
                }

                val allPlans = repository.getAllPlansList()
                val targetPlan = allPlans.firstOrNull()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        showImportExport = false,
                        plans = allPlans,
                        selectedPlan = targetPlan,
                        lastOpenedPlanId = targetPlan?.id,
                        userNotification = "Successfully imported $count records from backup!"
                    )
                }
                if (targetPlan != null) {
                    loadPlanExercisesInternal(targetPlan.id)
                }
                refreshAllExerciseStats()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        userNotification = "Import failed: ${error.localizedMessage ?: "Invalid or corrupted backup file"}"
                    )
                }
            }
        }
    }

    fun dismissNotification() {
        _uiState.update { it.copy(userNotification = null) }
    }

    fun setShowExerciseManager(show: Boolean) {
        _uiState.update { it.copy(showExerciseManager = show) }
    }

    fun setShowImportExport(show: Boolean) {
        if (show) {
            prepareExportData()
        } else {
            _uiState.update { it.copy(showImportExport = false) }
        }
    }

    fun setShowAddExercisePicker(show: Boolean) {
        _uiState.update { it.copy(showAddExercisePicker = show) }
    }

    fun setShowCreatePlanSheet(show: Boolean) {
        _uiState.update { it.copy(showCreatePlanSheet = show) }
    }

    fun restoreUserUploadedBackup() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isImporting = true) }
                val assetStream = getApplication<Application>().assets.open("default_backup.csv")
                val csvData = assetStream.bufferedReader().use { it.readText() }
                val res = repository.importData(csvData, replace = true)
                res.onSuccess { count ->
                    val prefs = getApplication<Application>().getSharedPreferences("fit_tracker_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("user_backup_csv_imported_v2", true).apply()
                    prefs.all.keys.filter { it.startsWith("active_workout_draft_") }.forEach { k ->
                        prefs.edit().remove(k).apply()
                    }
                    val allPlans = repository.getAllPlansList()
                    val targetPlan = allPlans.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            showImportExport = false,
                            plans = allPlans,
                            selectedPlan = targetPlan,
                            loadedPlanId = targetPlan?.id,
                            lastOpenedPlanId = targetPlan?.id,
                            userNotification = "Successfully imported $count records from uploaded backup!"
                        )
                    }
                    if (targetPlan != null) {
                        loadPlanExercisesInternal(targetPlan.id)
                    }
                    refreshAllExerciseStats()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            userNotification = "Import failed: ${err.localizedMessage ?: "Invalid backup file"}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        userNotification = "Could not open uploaded file: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // --- GitHub App Self-Updater ---
    fun checkForGitHubUpdates(manual: Boolean = false) {
        val repo = _uiState.value.githubRepo.ifBlank {
            GitHubUpdateManager.getConfiguredRepo(getApplication())
        }
        if (repo.isBlank()) {
            if (manual) {
                _uiState.update {
                    it.copy(updateStatusMessage = "Enter a GitHub repository (e.g. owner/repo) first.")
                }
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            checkForGitHubUpdatesInternal(repo = repo, manual = manual)
        }
    }

    fun saveGitHubRepoAndCheck(repoInput: String) {
        val normalized = GitHubUpdateManager.saveConfiguredRepo(getApplication(), repoInput)
        _uiState.update {
            it.copy(
                githubRepo = normalized,
                availableUpdate = null,
                updateStatusMessage = if (normalized.isBlank()) "Invalid repository format. Use owner/repo." else null
            )
        }
        if (normalized.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                checkForGitHubUpdatesInternal(repo = normalized, manual = true)
            }
        }
    }

    private suspend fun checkForGitHubUpdatesInternal(repo: String, manual: Boolean) {
        _uiState.update {
            it.copy(
                isCheckingUpdate = true,
                updateStatusMessage = if (manual) "Checking GitHub for updates..." else it.updateStatusMessage
            )
        }
        when (val result = GitHubUpdateManager.checkForUpdate(repo)) {
            is UpdateCheckResult.UpdateAvailable -> {
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        availableUpdate = result.info,
                        updateStatusMessage = if (manual) "New version available: Ver ${result.info.latestVersion}" else null
                    )
                }
            }
            is UpdateCheckResult.UpToDate -> {
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        availableUpdate = null,
                        updateStatusMessage = if (manual) "You are on the latest version (Ver ${com.example.BuildConfig.VERSION_NAME})." else null
                    )
                }
            }
            is UpdateCheckResult.Error -> {
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateStatusMessage = if (manual) "Check failed: ${result.message}" else null
                    )
                }
            }
        }
    }

    fun startAppUpdate() {
        val updateInfo = _uiState.value.availableUpdate ?: return
        if (_uiState.value.isDownloadingUpdate) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateDownloadProgress = 0f,
                    pendingUpdateApkPath = null,
                    needsInstallPermissionForUpdate = false
                )
            }

            val result = GitHubUpdateManager.downloadApk(
                context = getApplication(),
                downloadUrl = updateInfo.apkDownloadUrl,
                onProgress = { progress ->
                    _uiState.update { it.copy(updateDownloadProgress = progress) }
                }
            )

            result.onSuccess { apkFile ->
                val appCtx = getApplication<Application>()
                if (GitHubUpdateManager.canInstallPackages(appCtx)) {
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadProgress = null,
                            pendingUpdateApkPath = apkFile.absolutePath,
                            needsInstallPermissionForUpdate = false
                        )
                    }
                    val launchRes = GitHubUpdateManager.launchApkInstaller(appCtx, apkFile)
                    launchRes.onFailure { err ->
                        _uiState.update {
                            it.copy(userNotification = "Could not start installer: ${err.localizedMessage}")
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadProgress = null,
                            pendingUpdateApkPath = apkFile.absolutePath,
                            needsInstallPermissionForUpdate = true
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadProgress = null,
                        userNotification = "Update download failed: ${err.localizedMessage ?: "Network error"}"
                    )
                }
            }
        }
    }

    fun onInstallPermissionResult() {
        val appCtx = getApplication<Application>()
        val path = _uiState.value.pendingUpdateApkPath
        if (GitHubUpdateManager.canInstallPackages(appCtx) && !path.isNullOrBlank()) {
            val file = java.io.File(path)
            if (file.exists()) {
                _uiState.update { it.copy(needsInstallPermissionForUpdate = false) }
                val res = GitHubUpdateManager.launchApkInstaller(appCtx, file)
                res.onFailure { err ->
                    _uiState.update {
                        it.copy(userNotification = "Could not launch installer: ${err.localizedMessage}")
                    }
                }
                return
            }
        }
        _uiState.update {
            it.copy(
                needsInstallPermissionForUpdate = false,
                userNotification = "Install permission is required to apply the update."
            )
        }
    }

    fun dismissUpdatePermissionPrompt() {
        _uiState.update { it.copy(needsInstallPermissionForUpdate = false) }
    }

    fun clearUpdateStatusMessage() {
        _uiState.update { it.copy(updateStatusMessage = null) }
    }
}

