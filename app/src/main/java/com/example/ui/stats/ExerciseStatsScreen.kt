package com.example.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Exercise
import com.example.data.repository.ExerciseWorkoutHistoryItem
import com.example.ui.components.MuscleGroupTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class ChartMetric {
    MAX_WEIGHT,
    TOTAL_VOLUME,
    TOTAL_REPS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseStatsScreen(
    exercise: Exercise,
    history: List<ExerciseWorkoutHistoryItem>,
    exerciseStats: com.example.data.repository.ExerciseStats? = null,
    isWeightInKg: Boolean,
    onBack: () -> Unit,
    onDeleteHistoryItem: (ExerciseWorkoutHistoryItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    var selectedMetric by remember { mutableStateOf(ChartMetric.MAX_WEIGHT) }
    var historyItemToDelete by remember { mutableStateOf<ExerciseWorkoutHistoryItem?>(null) }
    val unitStr = if (isWeightInKg) "kg" else "lbs"

    // All time PR calculated from repository exerciseStats (respects PR resets & preserved PRs)
    val allTimeMaxWeight = remember(history, exerciseStats) {
        if (exerciseStats != null) {
            exerciseStats.maxWeight ?: 0.0
        } else {
            history.flatMap { it.sets }
                .filter { it.isCompleted && it.weight > 0 }
                .maxOfOrNull { it.weight } ?: 0.0
        }
    }
    val allTimeMaxReps = remember(history, exerciseStats) {
        if (exerciseStats != null) {
            exerciseStats.maxReps ?: 0
        } else {
            history.flatMap { it.sets }
                .filter { it.isCompleted }
                .maxOfOrNull { it.reps } ?: 0
        }
    }
    val isBodyweight = remember(history, exerciseStats) {
        if (exerciseStats != null) {
            exerciseStats.maxWeight == -2.0
        } else {
            history.flatMap { it.sets }.any { it.isCompleted && it.weight == -2.0 }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            ),
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Exercise Statistics & History",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("stats_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    MuscleGroupTag(
                        category = exercise.category,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Stat Card: Personal Record
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PERSONAL RECORD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color(0xFFB45309)
                                )
                                val prText = when {
                                    allTimeMaxWeight > 0.0 -> {
                                        val prFormatted = if (allTimeMaxWeight % 1.0 == 0.0) allTimeMaxWeight.toInt().toString() else allTimeMaxWeight.toString()
                                        "$prFormatted $unitStr"
                                    }
                                    isBodyweight && allTimeMaxReps > 0 -> "BW × $allTimeMaxReps reps"
                                    isBodyweight -> "Bodyweight"
                                    allTimeMaxReps > 0 -> "$allTimeMaxReps reps"
                                    else -> "No PR yet"
                                }
                                Text(
                                    text = prText,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    ),
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                        if (allTimeMaxReps > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Text(
                                    text = "Max: $allTimeMaxReps reps",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Progression Graph Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFF7ED)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B00),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Progress",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    ),
                                    color = Color(0xFF0F172A)
                                )
                            }

                            // Metric Selector Pills (Fixed: single line, no wrapping / no extra enter on REPS button)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val metricList = listOf(
                                    ChartMetric.MAX_WEIGHT to unitStr.uppercase(),
                                    ChartMetric.TOTAL_VOLUME to "VOL",
                                    ChartMetric.TOTAL_REPS to "REPS"
                                )
                                metricList.forEach { (metric, label) ->
                                    val isSelected = selectedMetric == metric
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFFFF6B00) else Color(0xFFF1F5F9))
                                            .clickable { selectedMetric = metric }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isSelected) Color.White else Color(0xFF64748B),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Custom Line Chart Canvas
                        ExercisePerformanceChart(
                            history = history,
                            metric = selectedMetric,
                            unitStr = unitStr,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        )
                    }
                }
            }

            // Header for Previous Saved Workouts
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PREVIOUS SAVED WORKOUTS (${history.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF64748B)
                    )
                }
            }

            if (history.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFFCBD5E1))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Workout History Yet",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Save a workout containing ${exercise.name} to see sets, weight progression, and analytics here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(history, key = { index, item -> "${item.sessionId}_${item.timestamp}_$index" }) { index, item ->
                    WorkoutHistorySessionCard(
                        sessionNumber = history.size - index,
                        isLatest = index == 0,
                        historyItem = item,
                        unitStr = unitStr,
                        onDelete = { historyItemToDelete = item }
                    )
                }
            }
        }
    }

    historyItemToDelete?.let { itemToDelete ->
        AlertDialog(
            onDismissRequest = { historyItemToDelete = null },
            title = {
                Text(
                    "Delete Saved Workout?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    "Remove this saved workout from ${exercise.name}'s history? PR, progress chart, and stats will be recalculated without it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteHistoryItem(itemToDelete)
                        historyItemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_history_item_button")
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { historyItemToDelete = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@Composable
fun ExercisePerformanceChart(
    history: List<ExerciseWorkoutHistoryItem>,
    metric: ChartMetric,
    unitStr: String,
    modifier: Modifier = Modifier
) {
    if (history.size < 2) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF8FAFC)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (history.isEmpty()) "No history recorded yet" else "1 workout logged (${history.first().maxWeight} $unitStr)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF334155)
                )
                Text(
                    text = "Save at least 2 workouts with this exercise to visualize your progression line.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Chronological order: oldest to newest (left to right)
    val chronological = remember(history) { history.reversed() }
    val dataPoints = remember(chronological, metric) {
        chronological.map { item ->
            when (metric) {
                ChartMetric.MAX_WEIGHT -> if (item.maxWeight < 0) 0f else item.maxWeight.toFloat()
                ChartMetric.TOTAL_VOLUME -> item.totalVolume.toFloat()
                ChartMetric.TOTAL_REPS -> item.totalReps.toFloat()
            }
        }
    }

    val maxVal = remember(dataPoints) { (dataPoints.maxOrNull() ?: 10f).coerceAtLeast(1f) }
    val minVal = remember(dataPoints) { (dataPoints.minOrNull() ?: 0f).coerceAtLeast(0f) }
    val valRange = (maxVal - minVal).coerceAtLeast(1f)

    val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.US) }
    val dateLabels = remember(chronological) {
        chronological.map { dateFormat.format(Date(it.timestamp)) }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingLeft = 32.dp.toPx()
        val paddingRight = 24.dp.toPx()
        val paddingTop = 28.dp.toPx()
        val paddingBottom = 28.dp.toPx()

        val usableWidth = width - paddingLeft - paddingRight
        val usableHeight = height - paddingTop - paddingBottom

        val stepX = usableWidth / (dataPoints.size - 1).coerceAtLeast(1)

        // Draw 3 horizontal gridlines
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = paddingTop + (usableHeight / gridLines) * i
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Compute points coordinates
        val points = dataPoints.mapIndexed { index, value ->
            val normalized = (value - minVal) / valRange
            val x = paddingLeft + index * stepX
            val y = paddingTop + (1f - normalized) * usableHeight
            Offset(x, y)
        }

        // Draw Area Fill Under Curve
        val fillPath = Path().apply {
            moveTo(points.first().x, paddingTop + usableHeight)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, paddingTop + usableHeight)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF6B00).copy(alpha = 0.25f), Color.Transparent),
                startY = paddingTop,
                endY = paddingTop + usableHeight
            )
        )

        // Draw Line Connecting Points
        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        drawPath(
            path = strokePath,
            color = Color(0xFFFF6B00),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw glowing circular points and labels
        points.forEachIndexed { index, point ->
            // Outer ring
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = point
            )
            // Inner dot
            drawCircle(
                color = Color(0xFFFF6B00),
                radius = 3.5.dp.toPx(),
                center = point
            )

            // Value text above dot
            val valText = when (metric) {
                ChartMetric.MAX_WEIGHT -> {
                    val origW = chronological.getOrNull(index)?.maxWeight ?: 0.0
                    if (origW == -2.0) {
                        "BW"
                    } else {
                        val v = dataPoints[index]
                        if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)
                    }
                }
                ChartMetric.TOTAL_VOLUME -> "${dataPoints[index].roundToInt()}"
                ChartMetric.TOTAL_REPS -> "${dataPoints[index].toInt()}"
            }

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0F172A")
                    textSize = 10.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(valText, point.x, point.y - 8.dp.toPx(), paint)
            }

            // Date label below X axis
            drawContext.canvas.nativeCanvas.apply {
                val datePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#64748B")
                    textSize = 9.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(dateLabels[index], point.x, paddingTop + usableHeight + 16.dp.toPx(), datePaint)
            }
        }
    }
}

@Composable
fun WorkoutHistorySessionCard(
    sessionNumber: Int,
    isLatest: Boolean,
    historyItem: ExerciseWorkoutHistoryItem,
    unitStr: String,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sdf = remember { SimpleDateFormat("EEEE, MMM dd, yyyy • HH:mm", Locale.US) }
    val formattedDate = remember(historyItem.timestamp) { sdf.format(Date(historyItem.timestamp)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Plan name + Top weight badge + Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isLatest) Color(0xFF16A34A) else Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$sessionNumber",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isLatest) Color.White else Color(0xFF475569)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = historyItem.planName.uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            ),
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Session Max Weight Pill + Delete Button
                val maxW = historyItem.maxWeight
                val maxWFormatted = when {
                    maxW == -2.0 -> "BW"
                    maxW < 0 -> "MAX"
                    maxW > 0 -> if (maxW % 1.0 == 0.0) "${maxW.toInt()} $unitStr" else "$maxW $unitStr"
                    else -> "0 $unitStr"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF7ED),
                        border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                    ) {
                        Text(
                            text = "Top: $maxWFormatted",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 11.5.sp
                            ),
                            color = Color(0xFFEA580C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_history_session_${historyItem.sessionId}_${historyItem.timestamp}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete Saved Workout",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Completed Sets Table
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    historyItem.sets.forEach { setLog ->
                        val weightStr = when {
                            setLog.weight == -2.0 -> "BW"
                            setLog.weight < 0 -> "MAX"
                            setLog.weight > 0 -> if (setLog.weight % 1.0 == 0.0) "${setLog.weight.toInt()} $unitStr" else "${setLog.weight} $unitStr"
                            else -> "0 $unitStr"
                        }
                        val repsStr = if (setLog.reps <= 0) "MAX reps" else "${setLog.reps} reps"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Set ${setLog.setNumber}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF475569)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$weightStr  ×  $repsStr",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    ),
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Volume & Reps Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isPureBwSession = historyItem.sets.isNotEmpty() && historyItem.sets.all { it.weight == -2.0 || it.weight <= 0.0 }
                val volFormatted = if (historyItem.totalVolume % 1.0 == 0.0) {
                    historyItem.totalVolume.toInt().toString()
                } else {
                    "%.1f".format(historyItem.totalVolume)
                }
                val volText = if (isPureBwSession && historyItem.totalReps > 0) {
                    "Total Volume: Bodyweight (${historyItem.totalReps} reps)"
                } else {
                    "Total Volume: $volFormatted $unitStr"
                }
                Text(
                    text = volText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "Total Reps: ${historyItem.totalReps}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
