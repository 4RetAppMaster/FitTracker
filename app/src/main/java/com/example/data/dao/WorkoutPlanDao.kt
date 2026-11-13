package com.example.data.dao

import androidx.room.*
import com.example.data.entity.PlanExercise
import com.example.data.entity.WorkoutPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {
    @Query("SELECT * FROM workout_plans ORDER BY orderIndex ASC, id ASC")
    fun getAllPlans(): Flow<List<WorkoutPlan>>

    @Query("SELECT * FROM workout_plans ORDER BY orderIndex ASC, id ASC")
    suspend fun getAllPlansList(): List<WorkoutPlan>

    @Query("SELECT * FROM workout_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: Long): WorkoutPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlan): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<WorkoutPlan>)

    @Update
    suspend fun updatePlan(plan: WorkoutPlan)

    @Update
    suspend fun updatePlans(plans: List<WorkoutPlan>)

    @Delete
    suspend fun deletePlan(plan: WorkoutPlan)

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC, id ASC")
    fun getPlanExercises(planId: Long): Flow<List<PlanExercise>>

    @Query("SELECT * FROM plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC, id ASC")
    suspend fun getPlanExercisesList(planId: Long): List<PlanExercise>

    @Query("SELECT * FROM plan_exercises ORDER BY planId ASC, orderIndex ASC, id ASC")
    suspend fun getAllPlanExercisesList(): List<PlanExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanExercise(planExercise: PlanExercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanExercises(planExercises: List<PlanExercise>)

    @Transaction
    suspend fun setPlanExercises(planId: Long, exercises: List<PlanExercise>) {
        deletePlanExercisesByPlanId(planId)
        if (exercises.isNotEmpty()) {
            insertPlanExercises(exercises)
        }
    }

    @Update
    suspend fun updatePlanExercise(planExercise: PlanExercise)

    @Delete
    suspend fun deletePlanExercise(planExercise: PlanExercise)

    @Query("DELETE FROM plan_exercises WHERE planId = :planId")
    suspend fun deletePlanExercisesByPlanId(planId: Long)

    @Query("DELETE FROM workout_plans")
    suspend fun clearPlans()

    @Query("DELETE FROM plan_exercises")
    suspend fun clearPlanExercises()
}
