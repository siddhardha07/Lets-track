package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.GoalContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {

    // All contributions for all goals in one query -- GoalProgressProvider sums per-goal itself,
    // cheaper than one Flow per goal card when the whole list re-combines on every change anyway.
    @Query("SELECT * FROM goal_contributions ORDER BY date DESC")
    fun getAllContributions(): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY date DESC")
    fun getContributionsForGoal(goalId: Long): Flow<List<GoalContributionEntity>>

    @Insert
    suspend fun insertContribution(contribution: GoalContributionEntity): Long

    @Delete
    suspend fun deleteContribution(contribution: GoalContributionEntity)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId")
    suspend fun deleteContributionsForGoal(goalId: Long)
}
