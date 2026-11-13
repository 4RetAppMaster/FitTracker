package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.theme.WorkoutTrackerTheme
import com.example.ui.workout.WorkoutScreen
import com.example.ui.workout.WorkoutViewModel

class MainActivity : ComponentActivity() {
  private val workoutViewModel: WorkoutViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      WorkoutTrackerTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          WorkoutScreen(viewModel = workoutViewModel)
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    workoutViewModel.flushStateToStorage()
  }

  override fun onStop() {
    super.onStop()
    workoutViewModel.flushStateToStorage()
  }
}

