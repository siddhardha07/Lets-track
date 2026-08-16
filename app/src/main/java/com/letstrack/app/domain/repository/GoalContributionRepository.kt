package com.letstrack.app.domain.repository

import com.letstrack.app.domain.model.GoalContribution
import kotlinx.coroutines.flow.Flow

interface GoalContributionRepository {
    fun getAllContributions(): Flow<List<GoalContribution>>
    fun getContributionsForGoal(goalId: Long): Flow<List<GoalContribution>>
    suspend fun addContribution(contribution: GoalContribution)
    suspend fun deleteContribution(contribution: GoalContribution)
    suspend fun deleteContributionsForGoal(goalId: Long)
}
