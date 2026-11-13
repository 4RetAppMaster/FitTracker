package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MuscleGroupColorStyle(
    val background: Color,
    val border: Color,
    val text: Color
)

fun getMuscleGroupColorStyle(category: String): MuscleGroupColorStyle {
    val norm = category.trim().lowercase()
    return when {
        norm.contains("chest") || norm.contains("pec") -> MuscleGroupColorStyle(
            background = Color(0xFFFEF2F2),
            border = Color(0xFFFECACA),
            text = Color(0xFFDC2626) // Red / Coral
        )
        norm.contains("back") || norm.contains("lat") || norm.contains("trap") -> MuscleGroupColorStyle(
            background = Color(0xFFEFF6FF),
            border = Color(0xFFBFDBFE),
            text = Color(0xFF2563EB) // Electric Blue
        )
        norm.contains("leg") || norm.contains("quad") || norm.contains("hamstring") || norm.contains("calf") || norm.contains("calves") || norm.contains("glute") || norm.contains("squat") -> MuscleGroupColorStyle(
            background = Color(0xFFF0FDF4),
            border = Color(0xFFBBF7D0),
            text = Color(0xFF16A34A) // Emerald Green
        )
        norm.contains("shoulder") || norm.contains("delt") -> MuscleGroupColorStyle(
            background = Color(0xFFFAF5FF),
            border = Color(0xFFE9D5FF),
            text = Color(0xFF9333EA) // Purple / Violet
        )
        norm.contains("bicep") -> MuscleGroupColorStyle(
            background = Color(0xFFFFFBEB),
            border = Color(0xFFFDE68A),
            text = Color(0xFFD97706) // Warm Amber / Gold
        )
        norm.contains("tricep") -> MuscleGroupColorStyle(
            background = Color(0xFFEEF2FF),
            border = Color(0xFFC7D2FE),
            text = Color(0xFF4F46E5) // Indigo / Royal Violet
        )
        norm.contains("arm") || norm.contains("forearm") -> MuscleGroupColorStyle(
            background = Color(0xFFFFFBEB),
            border = Color(0xFFFDE68A),
            text = Color(0xFFD97706) // Warm Amber / Gold
        )
        norm.contains("core") || norm.contains("ab") || norm.contains("oblique") -> MuscleGroupColorStyle(
            background = Color(0xFFECFEFF),
            border = Color(0xFFA5F3FC),
            text = Color(0xFF0891B2) // Cyan / Teal
        )
        norm.contains("cardio") || norm.contains("run") || norm.contains("hiit") -> MuscleGroupColorStyle(
            background = Color(0xFFFFF1F2),
            border = Color(0xFFFECDD3),
            text = Color(0xFFE11D48) // Rose Pink
        )
        else -> MuscleGroupColorStyle(
            background = Color(0xFFF8FAFC),
            border = Color(0xFFCBD5E1),
            text = Color(0xFF475569) // Neutral Slate
        )
    }
}

@Composable
fun MuscleGroupTag(
    category: String,
    modifier: Modifier = Modifier
) {
    val style = getMuscleGroupColorStyle(category)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = style.background,
        border = BorderStroke(1.dp, style.border),
        modifier = modifier
    ) {
        Text(
            text = category.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                fontSize = 9.5.sp,
                letterSpacing = 0.5.sp
            ),
            color = style.text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
