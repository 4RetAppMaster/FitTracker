package com.example.ui.workout

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.entity.WorkoutPlan
import com.example.data.update.GitHubUpdateManager
import com.example.ui.backup.BackupDialog
import com.example.ui.components.CreatePlanDialog
import com.example.ui.components.ExerciseCard
import com.example.ui.exercises.ExerciseLibraryDialog
import com.example.ui.exercises.ExercisesPage
import com.example.ui.lockers.LockersPage
import com.example.ui.theme.SidebarDarkBlue
import com.example.ui.theme.SidebarDarkBlueCard
import com.example.ui.theme.SidebarDarkBlueOutline
import com.example.ui.theme.SidebarDarkBlueTextMuted
import com.example.ui.stats.ExerciseStatsScreen
import com.example.ui.workouts.PlanEditorScreen
import com.example.ui.workouts.WorkoutsPage
import com.example.ui.workouts.getWorkoutPlanIcon
import com.example.ui.workouts.parsePlanColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppScreen {
    WORKOUTS,
    EXERCISES,
    LOCKERS,
    ACTIVE_WORKOUT,
    PLAN_EDITOR,
    STATISTICS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showGitHubRepoDialog by remember { mutableStateOf(false) }
    var lastDrawerUpdateCheckMs by remember { mutableLongStateOf(0L) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onInstallPermissionResult()
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen && uiState.githubRepo.isNotBlank() && !uiState.isCheckingUpdate && !uiState.isDownloadingUpdate) {
            val now = System.currentTimeMillis()
            if (now - lastDrawerUpdateCheckMs > 30_000L) {
                lastDrawerUpdateCheckMs = now
                viewModel.checkForGitHubUpdates(manual = false)
            }
        }
    }

    val currentScreen = uiState.currentScreen
    val screenStack = uiState.screenStack
    val planToEditInEditor = remember(uiState.plans, uiState.editingPlanId) {
        uiState.plans.find { it.id == uiState.editingPlanId }
    }

    val navigateTo: (AppScreen) -> Unit = { screen ->
        viewModel.navigateToScreen(screen)
    }

    val navigateBack: () -> Unit = {
        viewModel.navigateBack()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.flushStateToStorage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Intercept phone back button so it acts like a back button and doesn't exit the app
    var lastBackPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> {
                coroutineScope.launch { drawerState.close() }
            }
            uiState.showImportExport -> {
                viewModel.setShowImportExport(false)
            }
            uiState.showAddExercisePicker -> {
                viewModel.setShowAddExercisePicker(false)
            }
            uiState.showCreatePlanSheet -> {
                viewModel.setShowCreatePlanSheet(false)
            }
            screenStack.size > 1 -> {
                navigateBack()
            }
            currentScreen != AppScreen.WORKOUTS -> {
                viewModel.navigateToScreen(AppScreen.WORKOUTS)
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressedTime < 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressedTime = now
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Press back again to exit",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    // Live Workout Timer State formatted from ViewModel's persistent timer
    val timerFormatted = remember(uiState.timerSeconds) {
        val hours = uiState.timerSeconds / 3600
        val mins = (uiState.timerSeconds % 3600) / 60
        val secs = uiState.timerSeconds % 60
        if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    LaunchedEffect(uiState.userNotification) {
        uiState.userNotification?.let {
            // Popup notifications disabled per user request
            viewModel.dismissNotification()
        }
    }

    val unitLabel = if (uiState.isWeightInKg) "kg" else "lbs"

    // Live Workout Metrics Computation
    val totalSets = remember(uiState.exercisesInPlan) {
        uiState.exercisesInPlan.sumOf { it.sets.size }
    }
    val completedSets = remember(uiState.exercisesInPlan) {
        uiState.exercisesInPlan.sumOf { it.sets.count { s -> s.isCompleted } }
    }
    val totalVolume = remember(uiState.exercisesInPlan) {
        val completedVol = uiState.exercisesInPlan.sumOf { ex ->
            ex.sets.filter { it.isCompleted }.sumOf { (maxOf(0.0, it.weight) * it.reps).toLong() }
        }
        if (completedVol > 0) completedVol else {
            uiState.exercisesInPlan.sumOf { ex ->
                ex.sets.sumOf { (maxOf(0.0, it.weight) * it.reps).toLong() }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SidebarDarkBlue,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header in Drawer - Dumbbell Logo & Title near top + Update Button when available
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 20.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFF6B00)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = "FitTracker Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "FitTracker",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        fontSize = 22.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ".",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        fontSize = 22.sp
                                    ),
                                    color = Color(0xFFFF6B00)
                                )
                            }
                        }

                        if (uiState.availableUpdate != null) {
                            Surface(
                                onClick = {
                                    coroutineScope.launch { drawerState.close() }
                                    viewModel.startAppUpdate()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF10B981),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .testTag("drawer_update_app_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = "Update App",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Update",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = SidebarDarkBlueOutline, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1st menupoint: Workouts
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = null,
                                tint = if (currentScreen == AppScreen.WORKOUTS || currentScreen == AppScreen.ACTIVE_WORKOUT) Color.White else SidebarDarkBlueTextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                "Workouts",
                                fontWeight = FontWeight.Bold,
                                color = if (currentScreen == AppScreen.WORKOUTS || currentScreen == AppScreen.ACTIVE_WORKOUT) Color.White else Color(0xFFE2E8F0)
                            )
                        },
                        selected = currentScreen == AppScreen.WORKOUTS || currentScreen == AppScreen.ACTIVE_WORKOUT,
                        badge = {
                            Badge(
                                containerColor = if (currentScreen == AppScreen.WORKOUTS || currentScreen == AppScreen.ACTIVE_WORKOUT) Color.White else MaterialTheme.colorScheme.primary,
                                contentColor = if (currentScreen == AppScreen.WORKOUTS || currentScreen == AppScreen.ACTIVE_WORKOUT) MaterialTheme.colorScheme.primary else Color.White
                            ) {
                                Text(
                                    text = "${uiState.plans.size}",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        onClick = {
                            navigateTo(AppScreen.WORKOUTS)
                            coroutineScope.launch {
                                drawerState.close()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color(0xFFE2E8F0),
                            unselectedIconColor = SidebarDarkBlueTextMuted
                        ),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("drawer_menu_workouts")
                    )

                    // 2nd menupoint: Exercises
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = if (currentScreen == AppScreen.EXERCISES) Color.White else SidebarDarkBlueTextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                "Exercises",
                                fontWeight = FontWeight.Bold,
                                color = if (currentScreen == AppScreen.EXERCISES) Color.White else Color(0xFFE2E8F0)
                            )
                        },
                        selected = currentScreen == AppScreen.EXERCISES,
                        badge = {
                            Badge(
                                containerColor = if (currentScreen == AppScreen.EXERCISES) Color.White else MaterialTheme.colorScheme.primary,
                                contentColor = if (currentScreen == AppScreen.EXERCISES) MaterialTheme.colorScheme.primary else Color.White
                            ) {
                                Text(
                                    text = "${uiState.allExercises.size}",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        onClick = {
                            navigateTo(AppScreen.EXERCISES)
                            coroutineScope.launch {
                                drawerState.close()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color(0xFFE2E8F0),
                            unselectedIconColor = SidebarDarkBlueTextMuted
                        ),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("drawer_menu_exercises")
                    )

                    // Lockers Drawer Item
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = if (currentScreen == AppScreen.LOCKERS) Color.White else SidebarDarkBlueTextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                "Lockers",
                                fontWeight = FontWeight.Bold,
                                color = if (currentScreen == AppScreen.LOCKERS) Color.White else Color(0xFFE2E8F0)
                            )
                        },
                        selected = currentScreen == AppScreen.LOCKERS,
                        onClick = {
                            navigateTo(AppScreen.LOCKERS)
                            coroutineScope.launch {
                                drawerState.close()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color(0xFFE2E8F0),
                            unselectedIconColor = SidebarDarkBlueTextMuted
                        ),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("drawer_menu_lockers")
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Export & Import Data (CSV / JSON) Button in Drawer
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                viewModel.prepareExportData()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SidebarDarkBlueCard,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, SidebarDarkBlueOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drawer_menu_import_export")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SyncAlt,
                            contentDescription = null,
                            tint = Color(0xFF0D9488),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Export & Import Data",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // App version text left aligned without background, matching sidebar button text
                    Text(
                        text = "Ver ${com.example.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                viewModel.clearUpdateStatusMessage()
                                showGitHubRepoDialog = true
                            }
                            .padding(vertical = 2.dp)
                            .testTag("drawer_app_version_text")
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    ) {
        when (currentScreen) {
            AppScreen.WORKOUTS -> {
                WorkoutsPage(
                    viewModel = viewModel,
                    uiState = uiState,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onOpenWorkout = { plan ->
                        viewModel.selectPlan(plan)
                        navigateTo(AppScreen.ACTIVE_WORKOUT)
                    },
                    onOpenPlanEditor = { plan ->
                        viewModel.setEditingPlan(plan)
                        navigateTo(AppScreen.PLAN_EDITOR)
                    }
                )
            }

            AppScreen.EXERCISES -> {
                ExercisesPage(
                    viewModel = viewModel,
                    uiState = uiState,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onNavigateToActiveWorkout = {
                        navigateTo(AppScreen.ACTIVE_WORKOUT)
                    },
                    onOpenExerciseStats = { exercise ->
                        viewModel.openExerciseStats(exercise)
                    }
                )
            }

            AppScreen.STATISTICS -> {
                val exercise = uiState.selectedExerciseForStats
                if (exercise != null) {
                    ExerciseStatsScreen(
                        exercise = exercise,
                        history = uiState.exerciseHistory,
                        exerciseStats = uiState.exerciseStatsMap[exercise.id],
                        isWeightInKg = uiState.isWeightInKg,
                        onBack = { navigateBack() },
                        onDeleteHistoryItem = { item ->
                            viewModel.deleteExerciseHistoryItem(exercise.id, item)
                        }
                    )
                } else {
                    navigateBack()
                }
            }

            AppScreen.LOCKERS -> {
                LockersPage(
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                )
            }

            AppScreen.PLAN_EDITOR -> {
                PlanEditorScreen(
                    planToEdit = planToEditInEditor,
                    viewModel = viewModel,
                    uiState = uiState,
                    onDismiss = {
                        viewModel.setEditingPlan(null)
                        navigateBack()
                    },
                    onSaved = { savedPlan ->
                        viewModel.setEditingPlan(null)
                        navigateBack()
                    }
                )
            }

            AppScreen.ACTIVE_WORKOUT -> {
                ActiveWorkoutContent(
                    viewModel = viewModel,
                    uiState = uiState,
                    timerFormatted = timerFormatted,
                    isTimerRunning = uiState.isTimerRunning,
                    onToggleTimer = { viewModel.toggleTimer() },
                    totalSets = totalSets,
                    completedSets = completedSets,
                    totalVolume = totalVolume,
                    unitLabel = unitLabel,
                    snackbarHostState = snackbarHostState,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onNavigateToWorkouts = { navigateTo(AppScreen.WORKOUTS) },
                    onDiscardWorkout = { viewModel.discardActiveWorkout() },
                    modifier = modifier
                )
            }
        }
    }

    // Exercise Library Picker Dialog (When adding exercise from inside active workout)
    if (uiState.showAddExercisePicker) {
        ExerciseLibraryDialog(
            exercises = uiState.allExercises,
            onDismiss = { viewModel.setShowAddExercisePicker(false) },
            onAddCustomExercise = { name, cat, equip, note ->
                viewModel.createCustomExercise(name, cat, equip, note)
            },
            onDeleteExercise = { ex ->
                viewModel.deleteExercise(ex)
            },
            onSelectExerciseToAdd = { selectedExercise ->
                viewModel.addExerciseToCurrentPlan(selectedExercise)
            },
            onSelectMultipleExercisesToAdd = { selectedList ->
                viewModel.addMultipleExercisesToCurrentPlan(selectedList)
            }
        )
    }

    // Import / Export Dialog
    if (uiState.showImportExport) {
        BackupDialog(
            exportDataString = uiState.exportDataString,
            exportCsvString = uiState.exportCsvString,
            isImporting = uiState.isImporting,
            onDismiss = { viewModel.setShowImportExport(false) },
            onImportData = { json, replace ->
                viewModel.importData(json, replace)
            },
            onRestoreUploadedBackup = {
                viewModel.restoreUserUploadedBackup()
            }
        )
    }

    // Create New Plan Dialog
    if (uiState.showCreatePlanSheet) {
        CreatePlanDialog(
            onDismiss = { viewModel.setShowCreatePlanSheet(false) },
            onCreatePlan = { name, desc ->
                viewModel.createNewPlan(name, desc)
            }
        )
    }

    // Downloading Update Progress Dialog
    if (uiState.isDownloadingUpdate) {
        val progress = uiState.updateDownloadProgress
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Updating to Ver ${uiState.availableUpdate?.latestVersion ?: ""}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Downloading update from GitHub...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }
            },
            confirmButton = {},
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    // Install Unknown Apps Permission Prompt
    if (uiState.needsInstallPermissionForUpdate) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdatePermissionPrompt() },
            title = {
                Text(
                    text = "Allow App Update Install",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "Android needs permission for FitTracker to install the downloaded update APK. Tap Allow to open settings, enable \"Allow from this source\", then return.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        installPermissionLauncher.launch(GitHubUpdateManager.createUnknownSourcesIntent(context))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("allow_install_permission_button")
                ) {
                    Text("Allow & Install", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdatePermissionPrompt() }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    // GitHub Repository Configuration & Manual Update Check Dialog (opened by tapping Ver label)
    if (showGitHubRepoDialog) {
        var repoInput by remember(uiState.githubRepo) { mutableStateOf(uiState.githubRepo) }

        AlertDialog(
            onDismissRequest = { showGitHubRepoDialog = false },
            title = {
                Text(
                    text = "GitHub Auto-Update",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Current version: Ver ${com.example.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF0F172A)
                    )
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("GitHub Repo (owner/repo)") },
                        placeholder = { Text("username/FitTracker") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (uiState.isCheckingUpdate) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Checking GitHub...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    } else if (!uiState.updateStatusMessage.isNullOrBlank()) {
                        Text(
                            text = uiState.updateStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (uiState.availableUpdate != null) Color(0xFF059669) else Color(0xFF475569)
                        )
                    }
                    if (uiState.availableUpdate != null) {
                        Button(
                            onClick = {
                                showGitHubRepoDialog = false
                                viewModel.startAppUpdate()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Install Ver ${uiState.availableUpdate!!.latestVersion}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveGitHubRepoAndCheck(repoInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("save_github_repo_button")
                ) {
                    Text("Save & Check", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitHubRepoDialog = false }) {
                    Text("Close", color = Color(0xFF64748B))
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutContent(
    viewModel: WorkoutViewModel,
    uiState: WorkoutUiState,
    timerFormatted: String,
    isTimerRunning: Boolean,
    onToggleTimer: () -> Unit,
    totalSets: Int,
    completedSets: Int,
    totalVolume: Long,
    unitLabel: String,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onNavigateToWorkouts: () -> Unit,
    onDiscardWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFF6B00)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "FitTracker",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp
                                    ),
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = ".",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp
                                    ),
                                    color = Color(0xFFFF6B00)
                                )
                            }
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = isScrolled,
                            enter = fadeIn() + slideInHorizontally { it / 2 },
                            exit = fadeOut() + slideOutHorizontally { it / 2 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                // 1. Timer Chip (swapped before title, yellow when paused, no "Timer paused" text)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isTimerRunning) Color(0xFFDCFCE7) else Color(0xFFFEF08A),
                                    border = BorderStroke(1.dp, if (isTimerRunning) Color(0xFF86EFAC) else Color(0xFFFACC15)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onToggleTimer() }
                                        .testTag("top_bar_timer_chip")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = "Workout Timer",
                                            tint = if (isTimerRunning) Color(0xFF15803D) else Color(0xFF854D0E),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = timerFormatted,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp
                                            ),
                                            color = if (isTimerRunning) Color(0xFF15803D) else Color(0xFF854D0E),
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 2. Scrolled Workout Title (custom badge + shortened title with ellipsis to prevent collision)
                                val scrolledPlanIcon = getWorkoutPlanIcon(uiState.selectedPlan?.iconName) ?: Icons.Default.FitnessCenter
                                val scrolledPlanColor = parsePlanColor(uiState.selectedPlan?.colorHex)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(scrolledPlanColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = scrolledPlanIcon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = (uiState.selectedPlan?.name ?: "WORKOUT").uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 90.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets
                )
                HorizontalDivider(
                    color = Color(0xFFE2E8F0),
                    thickness = 1.dp
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Workout Plan Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan_header_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headerPlanIcon = getWorkoutPlanIcon(uiState.selectedPlan?.iconName) ?: Icons.Default.FitnessCenter
                        val headerPlanColor = parsePlanColor(uiState.selectedPlan?.colorHex)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(headerPlanColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = headerPlanIcon,
                                contentDescription = "Plan Logo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = (uiState.selectedPlan?.name ?: "A PUSH").uppercase(),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            ),
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            // Dedicated Workout Timer Box (Under the Plan Title Box)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workout_timer_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isTimerRunning) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Workout Stopwatch",
                                    tint = if (isTimerRunning) Color(0xFF16A34A) else Color(0xFFD97706),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = timerFormatted,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = if (isTimerRunning) Color(0xFF16A34A) else Color(0xFF0F172A)
                                )
                                Text(
                                    text = if (isTimerRunning) "Timer running" else "Timer paused",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (isTimerRunning) Color(0xFF16A34A) else Color(0xFFD97706)
                                )
                            }
                        }

                        Button(
                            onClick = onToggleTimer,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTimerRunning) Color(0xFFEF4444) else Color(0xFF16A34A),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("workout_timer_toggle_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isTimerRunning) "Pause Timer" else "Start Timer",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTimerRunning) "PAUSE" else "START",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Exercise Cards for the Plan
            itemsIndexed(
                items = uiState.exercisesInPlan,
                key = { _, item -> item.planExerciseId.toString() + "_" + item.exercise.id }
            ) { index, item ->
                ExerciseCard(
                    item = item,
                    isWeightInKg = uiState.isWeightInKg,
                    onNoteChange = { note ->
                        viewModel.updateExerciseNote(item.exercise.id, note)
                    },
                    onAddSet = {
                        viewModel.addSetToExercise(item.exercise.id)
                    },
                    onRemoveSet = { setIndex ->
                        viewModel.removeSetFromExercise(item.exercise.id, setIndex)
                    },
                    onUpdateSet = { setIndex, weight, reps ->
                        viewModel.updateSet(item.exercise.id, setIndex, weight, reps)
                    },
                    onToggleSetCompleted = { setIndex ->
                        viewModel.toggleSetCompleted(item.exercise.id, setIndex)
                    },
                    onToggleExerciseCompleted = {
                        viewModel.toggleExerciseCompleted(item.exercise.id)
                    },
                    onOpenExerciseStats = { exercise ->
                        viewModel.openExerciseStats(exercise)
                    }
                )
            }

            // Bottom Action Card: DISCARD | SAVE WORKOUT
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("bottom_action_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDiscardWorkout,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("discard_workout_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF334155)
                            )
                        ) {
                            Text(
                                text = "DISCARD",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF334155)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveWorkoutSession(onSuccess = onNavigateToWorkouts)
                            },
                            enabled = !uiState.isSaving,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("save_workout_session_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B00),
                                contentColor = Color.White
                            )
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SAVE WORKOUT",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.5.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
