package com.letstrack.app.data.repository

import com.letstrack.app.data.local.dao.GoalContributionDao
import com.letstrack.app.data.local.entity.GoalContributionEntity
import com.letstrack.app.domain.model.GoalContribution
import com.letstrack.app.domain.repository.GoalContributionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GoalContributionRepositoryImpl @Inject constructor(
    private val dao: GoalContributionDao
) : GoalContributionRepository {

    override fun getAllContributions(): Flow<List<GoalContribution>> =
        dao.getAllContributions().map { entities -> entities.map { it.toDomainModel() } }

    override fun getContributionsForGoal(goalId: Long): Flow<List<GoalContribution>> =
        dao.getContributionsForGoal(goalId).map { entities -> entities.map { it.toDomainModel() } }

    override suspend fun addContribution(contribution: GoalContribution) {
        dao.insertContribution(contribution.toEntity())
    }

    override suspend fun deleteContribution(contribution: GoalContribution) {
        dao.deleteContribution(contribution.toEntity())
    }

    override suspend fun deleteContributionsForGoal(goalId: Long) {
        dao.deleteContributionsForGoal(goalId)
    }

    private fun GoalContributionEntity.toDomainModel() =
        GoalContribution(id = id, goalId = goalId, amount = amount, date = date, note = note)

    private fun GoalContribution.toEntity() =
        GoalContributionEntity(id = id, goalId = goalId, amount = amount, date = date, note = note)
}
