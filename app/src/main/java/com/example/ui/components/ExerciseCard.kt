package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.workout.WorkoutExerciseUiState
import kotlin.math.roundToInt

@Composable
fun ExerciseCard(
    item: WorkoutExerciseUiState,
    isWeightInKg: Boolean,
    onNoteChange: (String) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateSet: (setIndex: Int, weight: Double, reps: Int) -> Unit,
    onToggleSetCompleted: (setIndex: Int) -> Unit,
    onToggleExerciseCompleted: () -> Unit = {},
    onRemoveExercise: (() -> Unit)? = null,
    onOpenExerciseStats: ((com.example.data.entity.Exercise) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isLocked = item.isCompleted

    // When checked, the entire workout box turns to a prominent green
    val cardBgColor = if (isLocked) Color(0xFFDCFCE7) else Color.White
    val cardBorderColor = if (isLocked) Color(0xFF16A34A) else Color(0xFFCBD5E1)
    val cardBorderWidth = if (isLocked) 2.5.dp else 1.5.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("exercise_card_${item.exercise.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        border = BorderStroke(
            width = cardBorderWidth,
            color = cardBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLocked) 3.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
        ) {
            // Header Row: Exercise Name on the left with PR tag; Muscle Group Tag before Checkbox on the right
            val unitStr = if (isWeightInKg) "kg" else "lbs"
            val prWeight = item.stats.maxWeight ?: 0.0
            val prReps = item.stats.maxReps ?: 0
            val hasPr = (item.stats.maxWeight != null && item.stats.maxWeight > 0) || (prReps > 0) || (item.stats.maxWeight == -2.0)
            val prWeightFormatted = when {
                prWeight == -2.0 -> "BW"
                prWeight < 0.0 -> "MAX"
                prWeight > 0 -> if (prWeight % 1.0 == 0.0) prWeight.toInt().toString() else prWeight.toString()
                else -> "0"
            }
            val recordDisplay = when {
                !hasPr -> "No PR yet"
                prWeight == -2.0 -> if (prReps > 0) "PR: BW × $prReps" else "PR: Bodyweight"
                prWeight > 0 && prReps > 0 -> "PR: ${prWeightFormatted}${unitStr} × $prReps"
                prWeight > 0 -> "PR: ${prWeightFormatted}${unitStr}"
                prReps > 0 -> "PR: $prReps reps"
                else -> "No PR yet"
            }

            // Header Row 1: Exercise Name inline with Checkbox and Muscle Group Tag (aligned at top)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = onOpenExerciseStats != null) {
                            onOpenExerciseStats?.invoke(item.exercise)
                        }
                        .padding(end = 6.dp)
                ) {
                    Text(
                        text = item.exercise.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 16.5.sp
                        ),
                        color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Muscle Group Tag on the left, Checkbox on the right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    MuscleGroupTag(
                        category = item.exercise.category
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Checkbox(
                        checked = isLocked,
                        onCheckedChange = { onToggleExerciseCompleted() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF16A34A),
                            uncheckedColor = Color(0xFF64748B),
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("exercise_checkbox_${item.exercise.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Trophy Record Tag on its own line directly under exercise name
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (!hasPr) Color(0xFFF1F5F9) else if (isLocked) Color(0xFFFEF9C3) else Color(0xFFFEFCE8),
                border = BorderStroke(1.dp, if (!hasPr) Color(0xFFE2E8F0) else Color(0xFFFEF08A))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Personal Record",
                        tint = if (hasPr) Color(0xFFCA8A04) else Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = recordDisplay,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 9.5.sp
                        ),
                        color = if (hasPr) Color(0xFF854D0E) else Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Table Header: SET (28dp) | REP (32dp) | WEIGHT (96dp) | REPS (96dp) | LAST (64dp)
            // Columns are aligned wider and spaced across the card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SET",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "REP",
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isWeightInKg) "KG" else "LBS",
                    modifier = Modifier.width(96.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "REPS",
                    modifier = Modifier.width(96.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "LAST",
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
            }

            HorizontalDivider(
                color = if (isLocked) Color(0xFF86EFAC) else Color(0xFFF1F5F9),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Sets List
            item.sets.forEachIndexed { index, setItem ->
                val previousSessionSet = item.stats.lastSessionSets.find { it.setNumber == setItem.setNumber }

                // The fixed rep value strictly comes from the workout plan and does not change
                val targetRepsDisplay = if (item.targetReps < 1) "MAX" else "${item.targetReps}"

                val lastStatsText = when {
                    previousSessionSet != null && previousSessionSet.weight > 0 -> {
                        val w = if (previousSessionSet.weight % 1.0 == 0.0) previousSessionSet.weight.toInt().toString() else previousSessionSet.weight.toString()
                        val r = if (previousSessionSet.reps <= 0) "MAX" else "${previousSessionSet.reps}"
                        "$w x $r"
                    }
                    previousSessionSet != null && previousSessionSet.weight == -2.0 -> {
                        val r = if (previousSessionSet.reps <= 0) "MAX" else "${previousSessionSet.reps}"
                        "BW x $r"
                    }
                    previousSessionSet != null && previousSessionSet.weight < 0.0 -> {
                        val r = if (previousSessionSet.reps <= 0) "MAX" else "${previousSessionSet.reps}"
                        "MAX x $r"
                    }
                    item.stats.lastWeight != null && item.stats.lastWeight > 0 -> {
                        val w = if (item.stats.lastWeight % 1.0 == 0.0) item.stats.lastWeight.toInt().toString() else item.stats.lastWeight.toString()
                        val r = item.stats.lastReps ?: setItem.reps
                        val rStr = if (r <= 0) "MAX" else "$r"
                        "$w x $rStr"
                    }
                    item.stats.lastWeight != null && item.stats.lastWeight == -2.0 -> {
                        val r = item.stats.lastReps ?: setItem.reps
                        val rStr = if (r <= 0) "MAX" else "$r"
                        "BW x $rStr"
                    }
                    item.stats.lastWeight != null && item.stats.lastWeight < 0.0 -> {
                        val r = item.stats.lastReps ?: setItem.reps
                        val rStr = if (r <= 0) "MAX" else "$r"
                        "MAX x $rStr"
                    }
                    else -> "--"
                }

                SetRowItem(
                    setNumber = setItem.setNumber,
                    targetRepsText = targetRepsDisplay,
                    weight = setItem.weight,
                    reps = setItem.reps,
                    lastText = lastStatsText,
                    isLocked = isLocked,
                    onWeightChange = { newWeight ->
                        if (!isLocked) {
                            onUpdateSet(index, newWeight, setItem.reps)
                        }
                    },
                    onRepsChange = { newReps ->
                        if (!isLocked) {
                            onUpdateSet(index, setItem.weight, newReps)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Note Input Field (Locked when exercise is completed)
            var noteText by remember(item.note) { mutableStateOf(item.note) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.StickyNote2,
                    contentDescription = "Note",
                    tint = if (isLocked) Color(0xFF15803D) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLocked) Color(0xFFF0FDF4) else Color(0xFFF8FAFC))
                        .border(
                            1.dp,
                            if (isLocked) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = noteText,
                        onValueChange = {
                            if (!isLocked) {
                                noteText = it
                                onNoteChange(it)
                            }
                        },
                        enabled = !isLocked,
                        readOnly = isLocked,
                        textStyle = TextStyle(
                            color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFFFF6B00)),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_note_input_${item.exercise.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun SetRowItem(
    setNumber: Int,
    targetRepsText: String,
    weight: Double,
    reps: Int,
    lastText: String,
    isLocked: Boolean,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isWeightFocused by remember { mutableStateOf(false) }
    var isRepsFocused by remember { mutableStateOf(false) }

    fun formatWeight(w: Double): String {
        return when {
            w == -2.0 -> "BW"
            w == -1.0 -> "MAX"
            w < 0.0 -> "MAX"
            w == 0.0 -> ""
            w % 1.0 == 0.0 -> w.toInt().toString()
            else -> {
                val rounded = ((w * 10).roundToInt() / 10.0)
                if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
            }
        }
    }

    var weightInput by remember { mutableStateOf(formatWeight(weight)) }
    var repsInput by remember { mutableStateOf(if (reps <= 0) "MAX" else "$reps") }

    // Only update from external state when NOT actively typing/focused!
    LaunchedEffect(weight, isWeightFocused) {
        if (!isWeightFocused) {
            weightInput = formatWeight(weight)
        }
    }

    LaunchedEffect(reps, isRepsFocused) {
        if (!isRepsFocused) {
            repsInput = if (reps <= 0) "MAX" else "$reps"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Set Number Column - Centered text (28dp)
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$setNumber",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                ),
                color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        }

        // 2. Read-only Rep Column - Looks just like set column (32dp)
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = targetRepsText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                ),
                color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        // 3. Weight Stepper Box: Width 96.dp with Down/Up arrows stepping by 5
        // Text field supports max 3 digits + 1 decimal place (e.g. 154.5) and displays "MAX" if goes under 0, "BW" if under MAX
        Row(
            modifier = Modifier
                .width(96.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isLocked) Color(0xFFF0FDF4) else Color.White)
                .border(
                    1.dp,
                    if (isLocked) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                    RoundedCornerShape(10.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement Weight Button (-5; goes down to 0, under 0 -> MAX, under MAX -> BW)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (!isLocked) {
                            Modifier.clickable {
                                focusManager.clearFocus()
                                val cur = weightInput.toDoubleOrNull()
                                when {
                                    weightInput == "BW" || weight == -2.0 -> {
                                        // Bottom reached (BW)
                                    }
                                    weightInput == "MAX" || weight == -1.0 -> {
                                        weightInput = "BW"
                                        onWeightChange(-2.0)
                                    }
                                    weightInput == "0" || weightInput.isEmpty() || weight == 0.0 || (cur != null && cur <= 0.0) -> {
                                        weightInput = "MAX"
                                        onWeightChange(-1.0)
                                    }
                                    cur != null && cur > 0.0 -> {
                                        val next = maxOf(0.0, ((cur - 5.0) * 10).roundToInt() / 10.0)
                                        val formatted = if (next == 0.0) "0" else formatWeight(next)
                                        weightInput = formatted
                                        onWeightChange(next)
                                    }
                                    else -> {
                                        val base = maxOf(0.0, weight)
                                        val next = maxOf(0.0, ((base - 5.0) * 10).roundToInt() / 10.0)
                                        val formatted = if (next == 0.0) "0" else formatWeight(next)
                                        weightInput = formatted
                                        onWeightChange(next)
                                    }
                                }
                            }
                        } else Modifier
                    )
                    .testTag("decrement_weight_set_${setNumber}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Decrease weight by 5",
                    tint = if (isLocked) Color(0xFF9CA3AF) else Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Divider Line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(if (isLocked) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
            )

            // Weight Input Box: Max 3 digits + 1 decimal place (e.g. 154.5) - Stays black text for 0, MAX, and BW
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = weightInput,
                    onValueChange = { str ->
                        if (!isLocked) {
                            val upper = str.uppercase().trim()
                            if (str.isEmpty()) {
                                weightInput = ""
                                onWeightChange(0.0)
                                return@BasicTextField
                            }
                            if (upper == "BW" || upper == "B") {
                                weightInput = upper
                                if (upper == "BW") {
                                    onWeightChange(-2.0)
                                }
                                return@BasicTextField
                            }
                            if (upper == "MAX" || upper == "M" || upper == "MA") {
                                weightInput = upper
                                if (upper == "MAX") {
                                    onWeightChange(-1.0)
                                }
                                return@BasicTextField
                            }
                            if (str.startsWith("-")) {
                                weightInput = "MAX"
                                onWeightChange(-1.0)
                                return@BasicTextField
                            }
                            // Restrict to max 3 digits before decimal, max 1 decimal place
                            val normalized = str.replace(',', '.')
                            val parts = normalized.split('.')
                            if (parts.size <= 2) {
                                val intDigits = parts[0].filter { it.isDigit() }.take(3)
                                val hasDot = normalized.contains('.')
                                val decDigits = if (parts.size == 2) parts[1].filter { it.isDigit() }.take(1) else ""
                                val formatted = when {
                                    hasDot && parts.size == 2 && decDigits.isNotEmpty() -> "$intDigits.$decDigits"
                                    hasDot -> "$intDigits."
                                    else -> intDigits
                                }
                                weightInput = formatted
                                val parsed = formatted.toDoubleOrNull()
                                if (parsed != null) {
                                    onWeightChange(parsed)
                                } else if (formatted.isEmpty()) {
                                    onWeightChange(0.0)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isWeightFocused = focusState.isFocused
                            if (!focusState.isFocused) {
                                if (weightInput.isBlank()) {
                                    weightInput = ""
                                    onWeightChange(0.0)
                                } else if (weightInput == "BW") {
                                    onWeightChange(-2.0)
                                } else if (weightInput == "MAX") {
                                    onWeightChange(-1.0)
                                } else {
                                    val p = weightInput.toDoubleOrNull() ?: 0.0
                                    val clean = minOf(999.9, maxOf(0.0, ((p * 10).roundToInt() / 10.0)))
                                    weightInput = formatWeight(clean)
                                    onWeightChange(clean)
                                }
                            }
                        }
                        .testTag("weight_input_set_${setNumber}"),
                    enabled = !isLocked,
                    readOnly = isLocked,
                    textStyle = TextStyle(
                        color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                        fontSize = if (weightInput == "MAX" || weightInput == "BW") 12.sp else 13.5.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    cursorBrush = SolidColor(if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A))
                )
            }

            // Divider Line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(if (isLocked) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
            )

            // Increment Weight Button (+5; BW -> MAX -> 0 -> 5 -> 10...)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (!isLocked) {
                            Modifier.clickable {
                                focusManager.clearFocus()
                                when {
                                    weightInput == "BW" || weight == -2.0 -> {
                                        weightInput = "MAX"
                                        onWeightChange(-1.0)
                                    }
                                    weightInput == "MAX" || weight == -1.0 -> {
                                        weightInput = "0"
                                        onWeightChange(0.0)
                                    }
                                    else -> {
                                        val cur = weightInput.toDoubleOrNull() ?: if (weight < 0.0) 0.0 else weight
                                        val next = minOf(999.9, ((cur + 5.0) * 10).roundToInt() / 10.0)
                                        val formatted = formatWeight(next)
                                        weightInput = formatted
                                        onWeightChange(next)
                                    }
                                }
                            }
                        } else Modifier
                    )
                    .testTag("increment_weight_set_${setNumber}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Increase weight by 5",
                    tint = if (isLocked) Color(0xFF9CA3AF) else Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // 4. Reps Stepper Box: Width 96.dp (Wider box), rounded 10.dp
        // Only 2 digits allowed (0 to 99, or MAX if <= 0) - Stays black text for 0 and MAX
        Row(
            modifier = Modifier
                .width(96.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isLocked) Color(0xFFF0FDF4) else Color.White)
                .border(
                    1.dp,
                    if (isLocked) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                    RoundedCornerShape(10.dp)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement Reps Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (!isLocked) {
                            Modifier.clickable {
                                focusManager.clearFocus()
                                val cur = repsInput.toIntOrNull() ?: if (reps <= 0) 0 else reps
                                if (cur <= 1) {
                                    repsInput = "MAX"
                                    onRepsChange(0)
                                } else {
                                    val next = cur - 1
                                    repsInput = "$next"
                                    onRepsChange(next)
                                }
                            }
                        } else Modifier
                    )
                    .testTag("decrement_reps_set_${setNumber}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Decrease reps",
                    tint = if (isLocked) Color(0xFF9CA3AF) else Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Divider Line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(if (isLocked) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
            )

            // Reps Value Direct Input: Only 2 digits allowed!
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = repsInput,
                    onValueChange = { str ->
                        if (!isLocked) {
                            val upper = str.uppercase()
                            if (upper == "MAX" || upper == "M" || upper == "MA") {
                                repsInput = upper
                                if (upper == "MAX") {
                                    onRepsChange(0)
                                }
                                return@BasicTextField
                            }
                            val cleaned = str.filter { it.isDigit() }.take(2)
                            repsInput = cleaned
                            val parsed = cleaned.toIntOrNull()
                            if (parsed != null) {
                                onRepsChange(parsed)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isRepsFocused = focusState.isFocused
                            if (!focusState.isFocused) {
                                if (repsInput.isBlank()) {
                                    val fallback = targetRepsText.toIntOrNull() ?: 10
                                    repsInput = "$fallback"
                                    onRepsChange(fallback)
                                } else if (repsInput == "MAX") {
                                    onRepsChange(0)
                                } else {
                                    val p = repsInput.toIntOrNull()?.coerceIn(0, 99) ?: 10
                                    repsInput = if (p <= 0) "MAX" else "$p"
                                    onRepsChange(p)
                                }
                            }
                        }
                        .testTag("reps_input_set_${setNumber}"),
                    enabled = !isLocked,
                    readOnly = isLocked,
                    textStyle = TextStyle(
                        color = if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A),
                        fontSize = if (repsInput == "MAX") 12.sp else 13.5.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    cursorBrush = SolidColor(if (isLocked) Color(0xFF14532D) else Color(0xFF0F172A))
                )
            }

            // Divider Line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(if (isLocked) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
            )

            // Increment Reps Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (!isLocked) {
                            Modifier.clickable {
                                focusManager.clearFocus()
                                val cur = repsInput.toIntOrNull() ?: if (reps <= 0) 0 else reps
                                val next = if (cur <= 0 || repsInput == "MAX") 1 else minOf(99, cur + 1)
                                repsInput = "$next"
                                onRepsChange(next)
                            }
                        } else Modifier
                    )
                    .testTag("increment_reps_set_${setNumber}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Increase reps",
                    tint = if (isLocked) Color(0xFF9CA3AF) else Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // 5. Last Column with border: Width 64.dp (Wider box)
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isLocked) Color(0xFFF0FDF4) else Color(0xFFF8FAFC))
                .border(
                    1.dp,
                    if (isLocked) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lastText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                ),
                color = if (isLocked) Color(0xFF15803D) else Color(0xFF64748B),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
