package com.example.ui.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Exercise
import com.example.ui.components.MuscleGroupTag
import com.example.ui.workout.WorkoutUiState
import com.example.ui.workout.WorkoutViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesPage(
    viewModel: WorkoutViewModel,
    uiState: WorkoutUiState,
    onOpenDrawer: () -> Unit,
    onNavigateToActiveWorkout: () -> Unit,
    onOpenExerciseStats: ((Exercise) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showExerciseDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }
    var exerciseToResetPr by remember { mutableStateOf<Exercise?>(null) }

    val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core", "Cardio")

    val filteredExercises = uiState.allExercises.filter { ex ->
        val matchesCategory = (selectedCategory == "All" || ex.category.equals(selectedCategory, ignoreCase = true))
        val matchesSearch = searchQuery.isBlank() || ex.name.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    val hasActiveSubState = showExerciseDialog || exerciseToDelete != null || exerciseToResetPr != null || searchQuery.isNotEmpty() || selectedCategory != "All"
    BackHandler(enabled = hasActiveSubState) {
        when {
            showExerciseDialog -> {
                showExerciseDialog = false
                editingExercise = null
            }
            exerciseToDelete != null -> exerciseToDelete = null
            exerciseToResetPr != null -> exerciseToResetPr = null
            searchQuery.isNotEmpty() -> searchQuery = ""
            selectedCategory != "All" -> selectedCategory = "All"
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color(0xFF0F172A)
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 1.dp
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Page Title Row matching Workouts style: "Exercises" on left, "+" button on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exercises",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp
                    ),
                    color = Color(0xFF0F172A)
                )

                FilledIconButton(
                    onClick = {
                        editingExercise = null
                        showExerciseDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFFF6B00),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("add_exercise_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Exercise",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Search Bar styled like the other cards/boxes
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search exercises...",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = TextStyle(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exercise_search_bar"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6B00),
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color(0xFF0F172A),
                    unfocusedTextColor = Color(0xFF0F172A)
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exercises List
            if (filteredExercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No exercises found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try adjusting your search or add a new exercise",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredExercises, key = { it.id }) { exercise ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // First half of the box: Clickable to open exercise stats
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = onOpenExerciseStats != null) {
                                            onOpenExerciseStats?.invoke(exercise)
                                        }
                                        .padding(end = 8.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = exercise.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = Color(0xFF0F172A),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val stats = uiState.exerciseStatsMap[exercise.id]
                                    val prWeight = stats?.maxWeight ?: 0.0
                                    val prReps = stats?.maxReps ?: 0
                                    val unitStr = if (uiState.isWeightInKg) "kg" else "lbs"
                                    val prWeightFormatted = when {
                                        prWeight == -2.0 -> "BW"
                                        prWeight < 0.0 -> "MAX"
                                        prWeight > 0 -> if (prWeight % 1.0 == 0.0) prWeight.toInt().toString() else prWeight.toString()
                                        else -> "0"
                                    }
                                    val hasPr = (stats?.maxWeight != null && stats.maxWeight > 0) || (prReps > 0) || (stats?.maxWeight == -2.0)
                                    val prDisplay = when {
                                        !hasPr -> "No PR yet"
                                        prWeight == -2.0 -> if (prReps > 0) "PR: BW × $prReps" else "PR: Bodyweight"
                                        prWeight > 0 && prReps > 0 -> "PR: ${prWeightFormatted}${unitStr} × $prReps"
                                        prWeight > 0 -> "PR: ${prWeightFormatted}${unitStr}"
                                        prReps > 0 -> "PR: $prReps reps"
                                        else -> "No PR yet"
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (hasPr) Color(0xFFFEFCE8) else Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, if (hasPr) Color(0xFFFEF08A) else Color(0xFFE2E8F0))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EmojiEvents,
                                                contentDescription = "Personal Record",
                                                tint = if (hasPr) Color(0xFFCA8A04) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = prDisplay,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 9.5.sp
                                                ),
                                                color = if (hasPr) Color(0xFF854D0E) else Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }

                                // Action Buttons: Muscle Tag inline with Reset PR, Edit, and Delete (Stats button removed)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    MuscleGroupTag(
                                        category = exercise.category
                                    )

                                    IconButton(
                                        onClick = { exerciseToResetPr = exercise },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("reset_pr_exercise_${exercise.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = "Reset PR",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            editingExercise = exercise
                                            showExerciseDialog = true
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("edit_exercise_${exercise.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit Exercise",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { exerciseToDelete = exercise },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_exercise_${exercise.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Delete Exercise",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
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

    // Add / Edit Exercise Dialog (Only Name and Muscle Group)
    if (showExerciseDialog) {
        ExerciseFormDialog(
            exercise = editingExercise,
            categories = categories.filter { it != "All" },
            onDismiss = {
                showExerciseDialog = false
                editingExercise = null
            },
            onConfirm = { name, category ->
                if (editingExercise != null) {
                    viewModel.updateExercise(
                        editingExercise!!.copy(
                            name = name.trim(),
                            category = category.trim()
                        )
                    )
                } else {
                    viewModel.createCustomExercise(
                        name = name.trim(),
                        category = category.trim()
                    )
                }
                showExerciseDialog = false
                editingExercise = null
            }
        )
    }

    // Delete Exercise Confirmation
    exerciseToDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = {
                Text(
                    "Delete Exercise?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${exercise.name}\"? All history logs for this exercise will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExercise(exercise)
                        exerciseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    // Reset Personal Record and/or Stats Confirmation
    exerciseToResetPr?.let { exercise ->
        var resetPrChecked by remember(exercise.id) { mutableStateOf(false) }
        var resetStatsChecked by remember(exercise.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { exerciseToResetPr = null },
            title = {
                Text(
                    "Reset \"${exercise.name}\"?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose what you want to reset:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (resetPrChecked) Color(0xFFFFF7ED) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.dp,
                            if (resetPrChecked) Color(0xFFFF6B00).copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { resetPrChecked = !resetPrChecked }
                            .testTag("reset_option_pr")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = resetPrChecked,
                                onCheckedChange = { resetPrChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFFF6B00),
                                    uncheckedColor = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "PR (Personal Record)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Reset PR badge back to \"No PR yet\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (resetStatsChecked) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.dp,
                            if (resetStatsChecked) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { resetStatsChecked = !resetStatsChecked }
                            .testTag("reset_option_stats")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = resetStatsChecked,
                                onCheckedChange = { resetStatsChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFEF4444),
                                    uncheckedColor = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Stats & Workout History",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Clear progress chart, saved workouts & LAST stats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetExerciseData(
                            exerciseId = exercise.id,
                            resetPr = resetPrChecked,
                            resetStats = resetStatsChecked
                        )
                        exerciseToResetPr = null
                    },
                    enabled = resetPrChecked || resetStatsChecked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (resetStatsChecked) Color(0xFFEF4444) else Color(0xFFFF6B00)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_reset_exercise_button")
                ) {
                    Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToResetPr = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

/**
 * Clean and compact dialog that ONLY asks for:
 * 1. Name
 * 2. Muscle Group
 */
@Composable
fun ExerciseFormDialog(
    exercise: Exercise?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf(exercise?.name ?: "") }
    var selectedCategory by remember {
        mutableStateOf(exercise?.category ?: categories.firstOrNull() ?: "Chest")
    }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (exercise == null) "New Exercise" else "Edit Exercise",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                ),
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        text = "Exercise Name",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (nameError && it.isNotBlank()) nameError = false
                        },
                        placeholder = {
                            Text(
                                "e.g., Incline Dumbbell Press",
                                fontSize = 13.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        textStyle = TextStyle(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("Exercise name is required", color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp) }
                        } else null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6B00),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color(0xFFFAFAFA),
                            unfocusedContainerColor = Color(0xFFFAFAFA)
                        )
                    )
                }

                Column {
                    Text(
                        text = "Muscle Group",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = {
                                    Text(
                                        cat,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(100.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6B00),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF1F5F9),
                                    labelColor = Color(0xFF475569)
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onConfirm(name, selectedCategory)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                modifier = Modifier.testTag("save_exercise_button")
            ) {
                Text(
                    text = if (exercise == null) "Create Exercise" else "Save Changes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White
    )
}
