package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A savings goal (the thing you want to buy). [savedAmount] is deliberately NOT stored here --
 * it's always derived by summing [GoalContributionEntity] rows for this goal (see
 * GoalProgressProvider), so progress has real history instead of being a single field that's
 * easy for two writers (manual edit + a linked-account sync) to stomp on each other.
 *
 * [linkedAccountId] is optional and never locks out manual editing (see GoalProgressProvider's
 * doc comment) -- it's a reference balance the user can sync a contribution from, not a
 * replacement for the contribution log.
 *
 * [sortOrder] is null until the user manually drag-reorders the Home card stack at least once;
 * until then goals sort by percent-complete (see GoalProgressProvider).
 *
 * [isAchieved]/[achievedAt] are persisted (not just computed from savedAmount >= targetAmount) so
 * the celebration fires exactly once when the threshold is first crossed, and a goal stays in the
 * Achieved list even if a later contribution edit dips the running total back down.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
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
