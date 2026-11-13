package com.example.ui.workouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.entity.WorkoutPlan
import com.example.ui.components.MuscleGroupTag
import com.example.ui.exercises.ExerciseLibraryDialog
import com.example.ui.workout.PlanExerciseDraft
import com.example.ui.workout.WorkoutUiState
import com.example.ui.workout.WorkoutViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlanEditorScreen(
    planToEdit: WorkoutPlan?,
    viewModel: WorkoutViewModel,
    uiState: WorkoutUiState,
    onDismiss: () -> Unit,
    onSaved: (WorkoutPlan) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 140.dp.toPx() }
    var name by remember(planToEdit?.id) { mutableStateOf(planToEdit?.name ?: "") }
    var selectedIconName by remember(planToEdit?.id) { mutableStateOf(planToEdit?.iconName ?: "FitnessCenter") }
    var selectedColorHex by remember(planToEdit?.id) { mutableStateOf(planToEdit?.colorHex ?: "#FF6B00") }
    var isPickerExpanded by remember(planToEdit?.id) { mutableStateOf(false) }

    var exerciseDrafts by remember(planToEdit?.id) { mutableStateOf<List<PlanExerciseDraft>>(emptyList()) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var draggedExerciseIndex by remember { mutableStateOf<Int?>(null) }
    val exerciseDragAnimY = remember { Animatable(0f) }

    // Load initial drafts if editing an existing plan, or ensure empty list for a new plan
    LaunchedEffect(planToEdit?.id) {
        if (planToEdit != null && planToEdit.id > 0) {
            val drafts = viewModel.loadPlanDrafts(planToEdit.id)
            exerciseDrafts = drafts
        } else {
            exerciseDrafts = emptyList()
        }
    }

    val isEditing = planToEdit != null
    val unitStr = if (uiState.isWeightInKg) "kg" else "lbs"

    BackHandler(enabled = true) {
        when {
            showDeleteConfirmDialog -> showDeleteConfirmDialog = false
            showExercisePicker -> showExercisePicker = false
            isPickerExpanded -> isPickerExpanded = false
            else -> onDismiss()
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
                        Text(
                            text = if (isEditing) "Edit Workout Plan" else "New Workout Plan",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            ),
                            color = Color(0xFF0F172A)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("plan_editor_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF0F172A)
                            )
                        }
                    },
                    actions = {
                        if (isEditing && planToEdit != null) {
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("delete_plan_in_editor_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Workout Plan",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    nameError = true
                                    return@Button
                                }
                                viewModel.saveFullWorkoutPlan(
                                    planId = planToEdit?.id,
                                    name = name,
                                    description = "",
                                    iconName = selectedIconName,
                                    colorHex = selectedColorHex,
                                    exerciseDrafts = exerciseDrafts,
                                    onComplete = onSaved
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B00),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .height(38.dp)
                                .testTag("save_plan_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SAVE", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 1.dp
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plan Info Card (Icon Chooser + Workout Name + Color Chooser)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(20f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Row: Icon Badge (click to toggle chooser) + Name Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Icon Preview Button
                            val currentPlanColor = parsePlanColor(selectedColorHex)
                            val currentIcon = getWorkoutPlanIcon(selectedIconName)

                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selectedIconName.isNotBlank()) currentPlanColor else Color(0xFFF1F5F9))
                                    .border(
                                        width = if (selectedIconName.isBlank()) 1.5.dp else 0.dp,
                                        color = if (selectedIconName.isBlank()) Color(0xFFCBD5E1) else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { isPickerExpanded = !isPickerExpanded }
                                    .testTag("plan_icon_preview_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentIcon != null) {
                                    Icon(
                                        imageVector = currentIcon,
                                        contentDescription = "Workout Plan Icon",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    // Blank option indicator
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = "No Icon",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "None",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }

                            // Plan Name TextField
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    if (nameError && it.isNotBlank()) nameError = false
                                },
                                placeholder = {
                                    Text(
                                        "Workout plan name...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                ),
                                isError = nameError,
                                supportingText = if (nameError) {
                                    { Text("Workout name cannot be empty", color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp) }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("plan_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFF6B00),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color(0xFFFAFAFA),
                                    unfocusedContainerColor = Color(0xFFFAFAFA)
                                )
                            )
                        }

                        // Icon and Color Chooser Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Icon Selection Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Choose Icon",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFF475569)
                                )
                                if (selectedIconName.isBlank()) {
                                    Text(
                                        text = "Blank / No Icon",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // 10 Fitness Icons + Blank Grid/Row
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(PLAN_ICON_OPTIONS.size) { optIdx ->
                                    val opt = PLAN_ICON_OPTIONS[optIdx]
                                    val isSelected = selectedIconName == opt.id
                                    val activeColor = parsePlanColor(selectedColorHex)

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) {
                                            if (opt.id.isBlank()) Color(0xFFE2E8F0) else activeColor
                                        } else Color.White,
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) {
                                                if (opt.id.isBlank()) Color(0xFF64748B) else activeColor
                                            } else Color(0xFFCBD5E1)
                                        ),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedIconName = opt.id }
                                            .testTag("plan_icon_option_${if (opt.id.isBlank()) "blank" else opt.id}")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (opt.icon != null) {
                                                Icon(
                                                    imageVector = opt.icon,
                                                    contentDescription = opt.label,
                                                    tint = if (isSelected) Color.White else Color(0xFF475569),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Block,
                                                    contentDescription = "Blank",
                                                    tint = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                            // Color Selection Header
                            Text(
                                text = "Choose Color",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF475569)
                            )

                            // Color Swatches Row
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(PLAN_COLOR_OPTIONS.size) { colorIdx ->
                                    val hex = PLAN_COLOR_OPTIONS[colorIdx]
                                    val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                                    val color = parsePlanColor(hex)

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColorHex = hex }
                                            .testTag("plan_color_option_$colorIdx"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Exercises Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .zIndex(20f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Exercises",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            ),
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFFFF7ED),
                            border = BorderStroke(1.dp, Color(0xFFFED7AA))
                        ) {
                            Text(
                                text = "${exerciseDrafts.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFFFF6B00),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { showExercisePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B00),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_exercise_to_editor_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Exercise", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Exercise Cards in Plan (Drag & Drop Reorderable)
            if (exerciseDrafts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFF7ED)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B00),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Exercises Added",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use \"+ Add Exercise\" above to select movements from the library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(exerciseDrafts, key = { _, draft -> draft.exercise.id }) { index, draft ->
                    val stats = uiState.exerciseStatsMap[draft.exercise.id]
                    val prWeight = stats?.maxWeight ?: 0.0
                    val prReps = stats?.maxReps ?: 0
                    val prWeightFormatted = if (prWeight % 1.0 == 0.0) prWeight.toInt().toString() else prWeight.toString()

                    val isDragging = draggedExerciseIndex == index
                    val translationY = if (isDragging) exerciseDragAnimY.value else 0f
                    val zIndex = if (isDragging) 10f else 1f
                    val animatedElevation by animateDpAsState(
                        targetValue = if (isDragging) 12.dp else 2.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "plan_exercise_elevation"
                    )
                    val animatedBorderColor by animateColorAsState(
                        targetValue = if (isDragging) Color(0xFFFF6B00) else Color(0xFFCBD5E1),
                        animationSpec = spring(),
                        label = "plan_exercise_border"
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(if (isDragging) 2.dp else 1.5.dp, animatedBorderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!isDragging) {
                                    Modifier.animateItemPlacement(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else Modifier
                            )
                            .zIndex(zIndex)
                            .graphicsLayer { this.translationY = translationY }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Header Row: Drag Handle + Move Buttons + Exercise Name with PR tag + Category Tag & Delete on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Drag Handle for easy touch & drag across all items
                                    val currentDraftsState = rememberUpdatedState(exerciseDrafts)
                                    val currentIndexState = rememberUpdatedState(index)
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp, top = 2.dp)
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDragging) Color(0xFFFFF7ED) else Color.Transparent)
                                            .pointerInput(Unit) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        draggedExerciseIndex = currentIndexState.value
                                                        coroutineScope.launch {
                                                            exerciseDragAnimY.snapTo(0f)
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        coroutineScope.launch {
                                                            exerciseDragAnimY.animateTo(
                                                                0f,
                                                                animationSpec = spring(
                                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                                    stiffness = Spring.StiffnessMedium
                                                                )
                                                            )
                                                            draggedExerciseIndex = null
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        coroutineScope.launch {
                                                            exerciseDragAnimY.animateTo(
                                                                0f,
                                                                animationSpec = spring(
                                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                                    stiffness = Spring.StiffnessMedium
                                                                )
                                                            )
                                                            draggedExerciseIndex = null
                                                        }
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val currentIdx = draggedExerciseIndex ?: return@detectVerticalDragGestures
                                                        val list = currentDraftsState.value.toMutableList()
                                                        if (list.size <= 1) return@detectVerticalDragGestures

                                                        val slotHeight = itemHeightPx
                                                        val threshold = slotHeight * 0.5f

                                                        val newOffset = exerciseDragAnimY.value + dragAmount
                                                        coroutineScope.launch {
                                                            exerciseDragAnimY.snapTo(newOffset)
                                                        }

                                                        if (newOffset >= threshold && currentIdx < list.size - 1) {
                                                            val item = list.removeAt(currentIdx)
                                                            list.add(currentIdx + 1, item)
                                                            exerciseDrafts = list
                                                            draggedExerciseIndex = currentIdx + 1
                                                            coroutineScope.launch {
                                                                exerciseDragAnimY.snapTo(newOffset - slotHeight)
                                                            }
                                                        } else if (newOffset <= -threshold && currentIdx > 0) {
                                                            val item = list.removeAt(currentIdx)
                                                            list.add(currentIdx - 1, item)
                                                            exerciseDrafts = list
                                                            draggedExerciseIndex = currentIdx - 1
                                                            coroutineScope.launch {
                                                                exerciseDragAnimY.snapTo(newOffset + slotHeight)
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Hold to move exercise",
                                            tint = if (isDragging) Color(0xFFFF6B00) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Exercise Name on the left with PR tag directly under it in the same Column
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = draft.exercise.name,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp
                                            ),
                                            color = Color(0xFF0F172A),
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable {
                                                viewModel.openExerciseStats(draft.exercise)
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Global PR trophy tag directly under exercise name, perfectly left-aligned
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (prWeight > 0) Color(0xFFFEFCE8) else Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, if (prWeight > 0) Color(0xFFFEF08A) else Color(0xFFE2E8F0))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EmojiEvents,
                                                    contentDescription = "Personal Record",
                                                    tint = if (prWeight > 0) Color(0xFFCA8A04) else Color(0xFF94A3B8),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (prWeight > 0) "PR: ${prWeightFormatted}${unitStr} × $prReps" else "No PR yet",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 9.5.sp
                                                    ),
                                                    color = if (prWeight > 0) Color(0xFF854D0E) else Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Muscle category tag inline with top of exercise name with custom category color
                                MuscleGroupTag(
                                    category = draft.exercise.category,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                // Delete exercise from draft plan
                                IconButton(
                                    onClick = {
                                        val mutable = exerciseDrafts.toMutableList()
                                        mutable.removeAt(index)
                                        exerciseDrafts = mutable
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("delete_draft_exercise_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Color(0xFFF1F5F9),
                                thickness = 1.dp
                            )

                            // Steppers for Target Sets and Target Reps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sets Stepper
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Target Sets",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                            .padding(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (draft.targetSets > 1) {
                                                    val mutable = exerciseDrafts.toMutableList()
                                                    mutable[index] = draft.copy(targetSets = draft.targetSets - 1)
                                                    exerciseDrafts = mutable
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease Sets", modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = "${draft.targetSets}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier
                                                .weight(1f)
                                                .wrapContentWidth(Alignment.CenterHorizontally)
                                        )
                                        IconButton(
                                            onClick = {
                                                val mutable = exerciseDrafts.toMutableList()
                                                mutable[index] = draft.copy(targetSets = draft.targetSets + 1)
                                                exerciseDrafts = mutable
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase Sets", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                // Reps Stepper
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Target Reps",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                            .padding(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (draft.targetReps > 0) {
                                                    val mutable = exerciseDrafts.toMutableList()
                                                    mutable[index] = draft.copy(targetReps = draft.targetReps - 1)
                                                    exerciseDrafts = mutable
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease Reps", modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = if (draft.targetReps < 1) "MAX" else "${draft.targetReps}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = if (draft.targetReps < 1) 13.5.sp else 15.sp,
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier
                                                .weight(1f)
                                                .wrapContentWidth(Alignment.CenterHorizontally)
                                        )
                                        IconButton(
                                            onClick = {
                                                val mutable = exerciseDrafts.toMutableList()
                                                mutable[index] = draft.copy(targetReps = minOf(99, draft.targetReps + 1))
                                                exerciseDrafts = mutable
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase Reps", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Save Action at bottom
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                            return@Button
                        }
                        viewModel.saveFullWorkoutPlan(
                            planId = planToEdit?.id,
                            name = name,
                            description = "",
                            iconName = selectedIconName,
                            colorHex = selectedColorHex,
                            exerciseDrafts = exerciseDrafts,
                            onComplete = onSaved
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B00),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_workout_plan_bottom_btn")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditing) "SAVE CHANGES" else "SAVE WORKOUT PLAN",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }

    // Exercise Picker Dialog (from Exercises Menu)
    if (showExercisePicker) {
        ExerciseLibraryDialog(
            exercises = uiState.allExercises,
            onDismiss = { showExercisePicker = false },
            onAddCustomExercise = { exName, exCat, exEquip, exNote ->
                viewModel.createCustomExercise(exName, exCat, exEquip, exNote)
            },
            onDeleteExercise = { ex ->
                viewModel.deleteExercise(ex)
            },
            onSelectMultipleExercisesToAdd = { selectedList ->
                val newDrafts = mutableListOf<PlanExerciseDraft>()
                for (selectedEx in selectedList) {
                    if (!exerciseDrafts.any { it.exercise.id == selectedEx.id } && !newDrafts.any { it.exercise.id == selectedEx.id }) {
                        val stats = uiState.exerciseStatsMap[selectedEx.id]
                        val initialWeight = stats?.lastWeight ?: 20.0
                        val initialReps = stats?.lastReps ?: 10
                        newDrafts.add(
                            PlanExerciseDraft(
                                exercise = selectedEx,
                                targetSets = 3,
                                targetReps = initialReps,
                                targetWeight = initialWeight
                            )
                        )
                    }
                }
                if (newDrafts.isNotEmpty()) {
                    exerciseDrafts = exerciseDrafts + newDrafts
                }
                showExercisePicker = false
            }
        )
    }

    // Delete Confirmation Dialog inside Plan Editor
    if (showDeleteConfirmDialog && planToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Workout Plan?") },
            text = { Text("Are you sure you want to delete \"${planToEdit.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlan(planToEdit)
                        showDeleteConfirmDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
