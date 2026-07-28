package com.letstrack.app.ui.home

import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InsightKind {
    PERIOD_COMPARISON, TOP_CATEGORY, AUTO_CATEGORIZATION, TIME_OF_DAY_PATTERN, RECURRING_MERCHANTS
}

enum class InsightTone { POSITIVE, NEGATIVE, NEUTRAL, WARNING }

data class Insight(
    val kind: InsightKind,
    val tone: InsightTone,
    val message: String
)

/**
 * Small, real (non-ML) aggregation over data HomeViewModel already exposes, plus two
 * new pattern detectors (time-of-day, recurring merchants) computed here from raw
 * [Expense] rows. No insight is decorative copy — every card either reads directly from
 * computed StateFlow values or from one of the pure functions below.
 */
object InsightsEngine {

    fun buildInsights(
        expenses: List<Expense>,
        categories: List<Category>,
        categorySpending: List<CategorySpending>,
        metrics: KeyMetrics
    ): List<Insight> = listOfNotNull(
        periodComparison(metrics),
        topCategory(categorySpending),
        recurringMerchants(expenses),
        timeOfDayPattern(expenses, categories),
        autoCategorization(expenses)
    )

    fun periodComparison(metrics: KeyMetrics): Insight? {
        val change = metrics.expensesVsPreviousPeriod
        if (change == 0f) return null
        val magnitude = abs(change).roundToInt()
        if (magnitude == 0) return null
        return if (change < 0) {
            Insight(InsightKind.PERIOD_COMPARISON, InsightTone.POSITIVE, "You spent $magnitude% less than last period. Nice work.")
        } else {
            Insight(InsightKind.PERIOD_COMPARISON, InsightTone.NEGATIVE, "You spent $magnitude% more than last period.")
        }
    }

    fun topCategory(categorySpending: List<CategorySpending>): Insight? {
        val top = categorySpending.firstOrNull() ?: return null
        if (top.percentage < 20f) return null
        return Insight(
            InsightKind.TOP_CATEGORY,
            InsightTone.NEUTRAL,
            "${top.category.name} is your biggest category at ${top.percentage.roundToInt()}% of spending."
        )
    }

    fun autoCategorization(expenses: List<Expense>): Insight? {
        val debits = expenses.filter { it.transactionType == "DEBIT" }
        if (debits.isEmpty()) return null
        val rate = debits.count { it.isAiCategorized } * 100f / debits.size
        if (rate <= 0f) return null
        return Insight(
            InsightKind.AUTO_CATEGORIZATION,
            InsightTone.NEUTRAL,
            "${rate.roundToInt()}% of your transactions were categorized automatically."
        )
    }

    /** Flags a category where most spending clusters in a late-night window. */
    fun timeOfDayPattern(expenses: List<Expense>, categories: List<Category>): Insight? {
        val byCategory = expenses.filter { it.transactionType == "DEBIT" }.groupBy { it.categoryId }
        for ((categoryId, transactions) in byCategory) {
            if (transactions.size < 4) continue
            val category = categories.find { it.id == categoryId } ?: continue
            val lateNightCount = transactions.count { expense ->
                val hour = expense.getLocalDateTime().hour
                hour >= 21 || hour < 4
            }
            val ratio = lateNightCount.toFloat() / transactions.size
            if (ratio >= 0.6f) {
                return Insight(
                    InsightKind.TIME_OF_DAY_PATTERN,
                    InsightTone.NEUTRAL,
                    "You usually spend on ${category.name} after 9 PM."
                )
            }
        }
        return null
    }

    /**
     * A merchant is treated as "recurring" when it appears in at least 3 distinct months
     * with amounts that stay within 15% of their own average -- a lightweight proxy for
     * subscriptions/bills without needing a dedicated recurring-transaction model.
     */
    fun recurringMerchants(expenses: List<Expense>): Insight? {
        val named = expenses.filter { it.transactionType == "DEBIT" && it.merchantName.isNotBlank() }
        val byMerchant = named.groupBy { it.merchantName.trim().lowercase() }

        val recurringGroups = byMerchant.values.filter { transactions ->
            val distinctMonths = transactions.map { monthKey(it.date) }.distinct()
            if (distinctMonths.size < 3) return@filter false
            val amounts = transactions.map { abs(it.amount) }
            val average = amounts.average()
            if (average <= 0.0) return@filter false
            amounts.all { abs(it - average) / average <= 0.15 }
        }

        if (recurringGroups.isEmpty()) return null

        val monthlyTotal = recurringGroups.sumOf { transactions -> transactions.map { abs(it.amount) }.average() }
        val names = recurringGroups.mapNotNull { it.firstOrNull()?.merchantName?.trim() }.take(3)

        return Insight(
            InsightKind.RECURRING_MERCHANTS,
            InsightTone.WARNING,
            "${recurringGroups.size} recurring charges (${names.joinToString(", ")}) cost about " +
                "${formatCurrency(monthlyTotal)}/month. Worth a review?"
        )
    }

    private fun monthKey(epochMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
    }
}
