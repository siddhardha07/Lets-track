package com.letstrack.app.domain.budget

import com.letstrack.app.domain.repository.BudgetRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class CategoryBudgetStatus(
    val categoryId: Long,
    val budgetAmount: Double,
    val spentAmount: Double
) {
    val isOverBudget: Boolean get() = spentAmount > budgetAmount
}

data class BudgetSummary(
    val overallBudgetAmount: Double?,
    val overallSpentAmount: Double,
    // Only categories that actually have a budget configured -- unbudgeted categories have
    // nothing to compare spend against, so they're left out rather than shown with a meaningless
    // "0 of null" status.
    val categoryStatuses: List<CategoryBudgetStatus>
) {
    val isOverallOverBudget: Boolean get() = overallBudgetAmount != null && overallSpentAmount > overallBudgetAmount
}

/**
 * Single source of truth for "is this category/overall spend over its monthly budget" --
 * Home's Budget graph card, the Expenses tab's red-highlighting, and (once wired) the system
 * overlay's status dot all read from the same [summary]/[overBudgetCategoryIds] instead of each
 * re-deriving "current month" + "spend so far" themselves.
 *
 * Monthly-only and calendar-month-based (see BudgetEntity's doc comment) -- deliberately
 * independent of whatever time filter Home's own charts happen to have selected.
 */
@Singleton
class BudgetStatusProvider @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val expenseRepository: ExpenseRepository
) {

    val summary: Flow<BudgetSummary> = combine(
        budgetRepository.getAllBudgets(),
        expenseRepository.getAllExpenses()
    ) { budgets, allExpenses ->
        val (monthStart, monthEnd) = currentMonthRange()
        val monthExpenses = allExpenses.filter {
            it.transactionType == "DEBIT" && it.date >= monthStart && it.date <= monthEnd
        }
        val spendByCategory = monthExpenses.groupBy { it.categoryId }
            .mapValues { (_, expenses) -> expenses.sumOf { kotlin.math.abs(it.amount) } }
        val totalSpent = monthExpenses.sumOf { kotlin.math.abs(it.amount) }

        val overallBudget = budgets.find { it.categoryId == null }?.amount
        val categoryStatuses = budgets
            .filter { it.categoryId != null }
            .map { budget ->
                CategoryBudgetStatus(
                    categoryId = budget.categoryId!!,
                    budgetAmount = budget.amount,
                    spentAmount = spendByCategory[budget.categoryId] ?: 0.0
                )
            }

        BudgetSummary(overallBudget, totalSpent, categoryStatuses)
    }

    /** Category ids that are over budget for the current month -- what Expenses/overlay
     * highlighting actually needs, without pulling in the full summary. */
    val overBudgetCategoryIds: Flow<Set<Long>> = summary.map { s ->
        s.categoryStatuses.filter { it.isOverBudget }.map { it.categoryId }.toSet()
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }
}
