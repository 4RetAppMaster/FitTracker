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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.entity.WorkoutPlan
import com.example.ui.workout.WorkoutUiState
import com.example.ui.workout.WorkoutViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WorkoutsPage(
    viewModel: WorkoutViewModel,
    uiState: WorkoutUiState,
    onOpenDrawer: () -> Unit,
    onOpenWorkout: (WorkoutPlan) -> Unit,
    onOpenPlanEditor: (WorkoutPlan?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 90.dp.toPx() }
    var planToDelete by remember { mutableStateOf<WorkoutPlan?>(null) }
    var draggedPlanIndex by remember { mutableStateOf<Int?>(null) }
    val planDragAnimY = remember { Animatable(0f) }

    BackHandler(enabled = planToDelete != null) {
        planToDelete = null
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
                        titleContentColor = Color(0xFF0F172A),
                        actionIconContentColor = Color.White
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
            // Header: "Workouts" on the left, "+" button in line with it right aligned
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workouts",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp
                    ),
                    color = Color(0xFF0F172A)
                )

                FilledIconButton(
                    onClick = { onOpenPlanEditor(null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFFF6B00),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("add_workout_plan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Workout Plan",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (uiState.plans.isEmpty()) {
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
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF7ED)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = Color(0xFFFF6B00),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Workout Plans Yet",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create your first workout plan using the + button above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { onOpenPlanEditor(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Workout Plan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(uiState.plans, key = { _, plan -> plan.id }) { index, plan ->
                    val isDragging = draggedPlanIndex == index
                    val translationY = if (isDragging) planDragAnimY.value else 0f
                    val zIndex = if (isDragging) 10f else 1f
                    val animatedElevation by animateDpAsState(
                        targetValue = if (isDragging) 12.dp else 2.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "card_elevation"
                    )
                    val animatedBorderColor by animateColorAsState(
                        targetValue = if (isDragging) Color(0xFFFF6B00) else Color(0xFFCBD5E1),
                        animationSpec = spring(),
                        label = "card_border"
                    )

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Drag Handle to reorder Workout Plans continuously across all items
                            val currentPlansState = rememberUpdatedState(uiState.plans)
                            val currentPlanIndexState = rememberUpdatedState(index)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDragging) Color(0xFFFFF7ED) else Color.Transparent)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggedPlanIndex = currentPlanIndexState.value
                                                coroutineScope.launch {
                                                    planDragAnimY.snapTo(0f)
                                                }
                                            },
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    planDragAnimY.animateTo(
                                                        0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                    draggedPlanIndex = null
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    planDragAnimY.animateTo(
                                                        0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    )
                                                    draggedPlanIndex = null
                                                }
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                val currentIdx = draggedPlanIndex ?: return@detectVerticalDragGestures
                                                val plansList = currentPlansState.value
                                                if (plansList.size <= 1) return@detectVerticalDragGestures

                                                val slotHeight = itemHeightPx
                                                val threshold = slotHeight * 0.5f

                                                val newOffset = planDragAnimY.value + dragAmount
                                                coroutineScope.launch {
                                                    planDragAnimY.snapTo(newOffset)
                                                }

                                                if (newOffset >= threshold && currentIdx < plansList.size - 1) {
                                                    viewModel.reorderPlans(currentIdx, currentIdx + 1)
                                                    draggedPlanIndex = currentIdx + 1
                                                    coroutineScope.launch {
                                                        planDragAnimY.snapTo(newOffset - slotHeight)
                                                    }
                                                } else if (newOffset <= -threshold && currentIdx > 0) {
                                                    viewModel.reorderPlans(currentIdx, currentIdx - 1)
                                                    draggedPlanIndex = currentIdx - 1
                                                    coroutineScope.launch {
                                                        planDragAnimY.snapTo(newOffset + slotHeight)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .testTag("drag_handle_plan_${plan.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to move workout plan",
                                    tint = if (isDragging) Color(0xFFFF6B00) else Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            val isLastSaved = (uiState.lastSavedPlanId != null && plan.id == uiState.lastSavedPlanId)

                            // Workout Plan Content - Displays custom icon and color
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.selectPlan(plan)
                                        onOpenWorkout(plan)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("workout_plan_button_${plan.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val planIcon = getWorkoutPlanIcon(plan.iconName)
                                val planColor = parsePlanColor(plan.colorHex)

                                if (planIcon != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(planColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = planIcon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                }

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = plan.name,
                                        modifier = Modifier.weight(1f, fill = false),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 22.sp
                                        ),
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (isLastSaved) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "LAST",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = Color(0xFF16A34A),
                                            modifier = Modifier
                                                .background(
                                                    color = Color(0xFF22C55E).copy(alpha = 0.16f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                .testTag("last_saved_indicator_${plan.id}")
                                        )
                                    }
                                }
                            }

                            // Pencil icon to edit in full editor page
                            IconButton(
                                onClick = { onOpenPlanEditor(plan) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("edit_plan_${plan.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit Workout Plan",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Delete Plan Confirmation Dialog
    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete Workout Plan?") },
            text = { Text("Are you sure you want to delete \"${plan.name}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlan(plan)
                        planToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

