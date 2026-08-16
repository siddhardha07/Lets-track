package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One manual "added ₹X toward this goal" entry -- see GoalEntity's doc comment for why
 * savedAmount is derived from these instead of being its own column. */
@Entity(tableName = "goal_contributions")
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalId: Long,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val note: String? = null
)
