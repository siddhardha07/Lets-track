package com.letstrack.app.domain.model

data class Goal(
    val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    val photoUri: String? = null,
    val link: String? = null,
    val linkedAccountId: Long? = null,
    val sortOrder: Int? = null,
    val isAchieved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val achievedAt: Long? = null
)

data class GoalContribution(
    val id: Long = 0,
    val goalId: Long,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val note: String? = null
)
