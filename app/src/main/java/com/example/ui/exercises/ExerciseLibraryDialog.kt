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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.entity.Exercise
import com.example.ui.components.MuscleGroupTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryDialog(
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onAddCustomExercise: (name: String, category: String, equipment: String, note: String) -> Unit,
    onDeleteExercise: (Exercise) -> Unit,
    onSelectExerciseToAdd: ((Exercise) -> Unit)? = null,
    onSelectMultipleExercisesToAdd: ((List<Exercise>) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCreateForm by remember { mutableStateOf(false) }
    var selectedExerciseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val isSelectionMode = onSelectMultipleExercisesToAdd != null || onSelectExerciseToAdd != null
    val categories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core", "Cardio")

    val filteredExercises = exercises.filter { ex ->
        val matchesCategory = (selectedCategory == "All" || ex.category.equals(selectedCategory, ignoreCase = true))
        val matchesSearch = searchQuery.isBlank() || ex.name.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(enabled = true) {
            when {
                showCreateForm -> showCreateForm = false
                selectedExerciseIds.isNotEmpty() -> selectedExerciseIds = emptySet()
                searchQuery.isNotEmpty() -> searchQuery = ""
                selectedCategory != "All" -> selectedCategory = "All"
                else -> onDismiss()
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            shadowElevation = 8.dp
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (isSelectionMode) "Add Exercises to Plan" else "Exercises Library",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                ),
                                color = Color(0xFF0F172A)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF0F172A))
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showCreateForm = true },
                                modifier = Modifier.testTag("new_custom_exercise_header_button")
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "New Exercise",
                                    tint = Color(0xFFFF6B00)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = Color(0xFF0F172A)
                        )
                    )
                },
                floatingActionButton = {
                    if (isSelectionMode) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (selectedExerciseIds.isNotEmpty()) {
                                    val selectedList = selectedExerciseIds.mapNotNull { id -> exercises.find { it.id == id } }
                                    if (onSelectMultipleExercisesToAdd != null) {
                                        onSelectMultipleExercisesToAdd(selectedList)
                                    } else {
                                        selectedList.forEach { onSelectExerciseToAdd?.invoke(it) }
                                    }
                                    onDismiss()
                                }
                            },
                            containerColor = if (selectedExerciseIds.isNotEmpty()) Color(0xFFFF6B00) else Color(0xFF94A3B8),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 40.dp, end = 8.dp)
                                .testTag("add_selected_exercises_button"),
                            icon = {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            },
                            text = {
                                Text(
                                    text = if (selectedExerciseIds.isEmpty()) "Add" else "Add (${selectedExerciseIds.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        )
                    }
                },
                floatingActionButtonPosition = FabPosition.End,
                containerColor = Color.White
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    // Search Bar with Sentence Capitalization
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search exercises...", fontSize = 13.5.sp, color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF64748B))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6B00),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color(0xFFFAFAFA),
                            unfocusedContainerColor = Color(0xFFFAFAFA)
                        )
                    )

                    // Category Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
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
                                        fontSize = 12.5.sp
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

                    // List of Exercises
                    if (filteredExercises.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_dumbbell),
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No exercises found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    } else {
                        // Generous bottom padding (120.dp) ensures the last item is fully visible above the elevated FAB
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 120.dp),
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            items(filteredExercises, key = { it.id }) { exercise ->
                                val isSelected = selectedExerciseIds.contains(exercise.id)
                                ExerciseItemRow(
                                    exercise = exercise,
                                    isSelected = isSelected,
                                    onSelect = {
                                        if (onSelectMultipleExercisesToAdd != null) {
                                            selectedExerciseIds = if (isSelected) {
                                                selectedExerciseIds - exercise.id
                                            } else {
                                                selectedExerciseIds + exercise.id
                                            }
                                        } else if (onSelectExerciseToAdd != null) {
                                            onSelectExerciseToAdd(exercise)
                                            onDismiss()
                                        }
                                    },
                                    onDelete = { onDeleteExercise(exercise) },
                                    isSelectionMode = isSelectionMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateForm) {
        CreateExerciseDialog(
            onDismiss = { showCreateForm = false },
            onCreate = { name, cat, equip, note ->
                onAddCustomExercise(name, cat, equip, note)
                showCreateForm = false
            }
        )
    }
}

@Composable
fun ExerciseItemRow(
    exercise: Exercise,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean
) {
    val borderColor = if (isSelected) Color(0xFFFF6B00) else Color(0xFFCBD5E1)
    val bgColor = if (isSelected) Color(0xFFFFF7ED) else Color.White

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.5.dp, borderColor),
        shadowElevation = if (isSelected) 4.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("exercise_item_${exercise.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = Color(0xFF0F172A),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            MuscleGroupTag(
                category = exercise.category
            )

            if (!isSelectionMode && exercise.isCustom) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Custom Exercise",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, category: String, equipment: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Chest") }
    var nameError by remember { mutableStateOf(false) }

    val categories = listOf("Chest", "Back", "Legs", "Shoulders", "Biceps", "Triceps", "Core", "Cardio")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Exercise",
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
                                "e.g., Incline Hammer Press",
                                fontSize = 13.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        ),
                        singleLine = true,
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("Exercise name is required", color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp) }
                        } else null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_exercise_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6B00),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color(0xFFFAFAFA),
                            unfocusedContainerColor = Color(0xFFFAFAFA)
                        )
                    )
                }

                // Category selector
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
                        onCreate(name.trim(), selectedCategory, "Other", "")
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                modifier = Modifier.testTag("save_custom_exercise_button")
            ) {
                Text("Create Exercise", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
