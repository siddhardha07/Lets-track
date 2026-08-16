package com.letstrack.app.data.repository

import com.letstrack.app.data.local.dao.GoalDao
import com.letstrack.app.data.local.entity.GoalEntity
import com.letstrack.app.domain.model.Goal
import com.letstrack.app.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao
) : GoalRepository {

    override fun getAllGoals(): Flow<List<Goal>> =
        goalDao.getAllGoals().map { entities -> entities.map { it.toDomainModel() } }

    override suspend fun getGoalById(id: Long): Goal? = goalDao.getGoalById(id)?.toDomainModel()

    override suspend fun insertGoal(goal: Goal): Long = goalDao.insertGoal(goal.toEntity())

    override suspend fun updateGoal(goal: Goal) = goalDao.updateGoal(goal.toEntity())

    override suspend fun deleteGoal(goal: Goal) = goalDao.deleteGoal(goal.toEntity())

    private fun GoalEntity.toDomainModel() = Goal(
        id = id,
        name = name,
        targetAmount = targetAmount,
        photoUri = photoUri,
        link = link,
        linkedAccountId = linkedAccountId,
        sortOrder = sortOrder,
        isAchieved = isAchieved,
        createdAt = createdAt,
        achievedAt = achievedAt
    )

    private fun Goal.toEntity() = GoalEntity(
        id = id,
        name = name,
        targetAmount = targetAmount,
        photoUri = photoUri,
        link = link,
        linkedAccountId = linkedAccountId,
        sortOrder = sortOrder,
        isAchieved = isAchieved,
        createdAt = createdAt,
        achievedAt = achievedAt
    )
}
