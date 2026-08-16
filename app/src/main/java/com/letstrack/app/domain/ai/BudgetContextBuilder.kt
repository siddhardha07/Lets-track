package com.letstrack.app.domain.ai

import com.letstrack.app.domain.budget.BudgetSummary
import com.letstrack.app.domain.model.Category
import kotlin.math.roundToInt

/**
 * Builds the system-prompt context sent to the AI for budget questions -- deliberately
 * percentages/ratios only, never raw currency amounts, merchant names, account numbers, or
 * anything else that could identify the user or their real financial figures. This is the data-
 * minimization design agreed on when AI was first discussed: the assistant can reason about "90%
 * of Food budget used" just as well as "₹1,800 of ₹2,000", but the second one is the actual
 * number leaving the device and the first one isn't reversible back into it.
 *
 * Deliberately not a guarantee of privacy overall (a third-party API is still a third party) --
 * just the concrete minimization this app controls.
 */
fun buildBudgetContext(summary: BudgetSummary, categories: List<Category>): String {
    val lines = mutableListOf(
        "You are a budgeting assistant inside a personal finance app called Lets Track. " +
            "You only ever receive spending as percentages of budget, never actual currency amounts, " +
            "merchant names, or account details -- answer using percentages/relative terms, not made-up rupee figures."
    )

    if (summary.overallBudgetAmount != null) {
        val pct = (summary.overallSpentAmount / summary.overallBudgetAmount * 100).roundToInt()
        lines += "Overall monthly budget: $pct% used."
    } else {
        lines += "No overall budget is set."
    }

    if (summary.categoryStatuses.isEmpty()) {
        lines += "No per-category budgets are set."
    } else {
        summary.categoryStatuses.forEach { status ->
            val name = categories.find { it.id == status.categoryId }?.name ?: "Unknown category"
            val pct = (status.spentAmount / status.budgetAmount * 100).roundToInt()
            lines += "$name: $pct% of budget used${if (status.isOverBudget) " (over budget)" else ""}."
        }
    }

    return lines.joinToString("\n")
}
