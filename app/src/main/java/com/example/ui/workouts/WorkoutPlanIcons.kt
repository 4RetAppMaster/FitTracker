package com.example.ui.workouts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class PlanIconOption(
    val id: String,
    val label: String,
    val icon: ImageVector?
)

/**
 * 10 fitness-relevant simple icons + 1 blank/none option
 */
val PLAN_ICON_OPTIONS = listOf(
    PlanIconOption(id = "", label = "None", icon = null),
    PlanIconOption(id = "Push", label = "Push", icon = Icons.Default.SportsMartialArts),
    PlanIconOption(id = "Pull", label = "Pull", icon = Icons.Default.Rowing),
    PlanIconOption(id = "Legs", label = "Legs", icon = Icons.Default.DirectionsWalk),
    PlanIconOption(id = "FitnessCenter", label = "Weights", icon = Icons.Default.FitnessCenter),
    PlanIconOption(id = "DirectionsRun", label = "Run", icon = Icons.Default.DirectionsRun),
    PlanIconOption(id = "Bolt", label = "Power", icon = Icons.Default.Bolt),
    PlanIconOption(id = "LocalFireDepartment", label = "Burn", icon = Icons.Default.LocalFireDepartment),
    PlanIconOption(id = "Timer", label = "HIIT", icon = Icons.Default.Timer),
    PlanIconOption(id = "SelfImprovement", label = "Mobility", icon = Icons.Default.SelfImprovement),
    PlanIconOption(id = "SportsGymnastics", label = "Bodyweight", icon = Icons.Default.SportsGymnastics),
    PlanIconOption(id = "Speed", label = "Speed", icon = Icons.Default.Speed),
    PlanIconOption(id = "Favorite", label = "Cardio", icon = Icons.Default.Favorite),
    PlanIconOption(id = "EmojiEvents", label = "Trophy", icon = Icons.Default.EmojiEvents)
)

/**
 * Vibrant clean fitness color palette
 */
val PLAN_COLOR_OPTIONS = listOf(
    "#FF6B00", // Orange (Primary)
    "#EF4444", // Red
    "#3B82F6", // Blue
    "#10B981", // Green
    "#8B5CF6", // Purple
    "#F59E0B", // Amber
    "#0D9488", // Teal
    "#EC4899", // Pink
    "#475569"  // Slate
)

fun getWorkoutPlanIcon(iconName: String?): ImageVector? {
    if (iconName.isNullOrBlank()) return null
    return when (iconName) {
        "Push" -> Icons.Default.SportsMartialArts
        "Pull" -> Icons.Default.Rowing
        "Legs", "Leg" -> Icons.Default.DirectionsWalk
        "FitnessCenter" -> Icons.Default.FitnessCenter
        "DirectionsRun" -> Icons.Default.DirectionsRun
        "Bolt" -> Icons.Default.Bolt
        "LocalFireDepartment" -> Icons.Default.LocalFireDepartment
        "Timer" -> Icons.Default.Timer
        "SelfImprovement" -> Icons.Default.SelfImprovement
        "SportsGymnastics" -> Icons.Default.SportsGymnastics
        "Speed" -> Icons.Default.Speed
        "Favorite" -> Icons.Default.Favorite
        "EmojiEvents" -> Icons.Default.EmojiEvents
        else -> null
    }
}

fun parsePlanColor(colorHex: String?, defaultColor: Color = Color(0xFFFF6B00)): Color {
    if (colorHex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        defaultColor
    }
}
