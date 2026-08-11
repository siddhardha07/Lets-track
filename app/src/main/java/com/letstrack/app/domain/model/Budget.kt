package com.letstrack.app.domain.model

/** A monthly spending limit. `categoryId == null` means the overall budget across everything;
 * otherwise it's the limit for that one category. See BudgetEntity for the monthly-only
 * rationale. */
data class Budget(
    val id: Long = 0,
    val categoryId: Long? = null,
    val amount: Double
)
