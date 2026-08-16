package com.letstrack.app.domain.goal

import com.letstrack.app.domain.model.BankAccount
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.model.Goal
import com.letstrack.app.domain.model.GoalContribution
import com.letstrack.app.domain.repository.BankAccountRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.domain.repository.GoalContributionRepository
import com.letstrack.app.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [savedAmount] = [linkedAccountBalance] (if the goal is linked to an account) + the sum of
 * manual contributions logged on top -- linking counts toward progress automatically, no manual
 * "sync" tap required, while manual contributions still add on top for cash or anything the
 * linked account doesn't cover (per the user's explicit "I should still be able to edit even if
 * it's linked" call).
 */
data class GoalProgress(
    val goal: Goal,
    val savedAmount: Double,
    val linkedAccountBalance: Double?,
    val manualContributionTotal: Double
) {
    val percent: Float get() = if (goal.targetAmount > 0) (savedAmount / goal.targetAmount).toFloat().coerceAtLeast(0f) else 0f
    val isFullyFunded: Boolean get() = goal.targetAmount > 0 && savedAmount >= goal.targetAmount
}

/**
 * Single source of truth for "how much has been saved toward each goal" -- the Home card stack,
 * the goal detail screen, and the achievement check in GoalsViewModel all read from either
 * [goalProgress] (continuous) or [currentProgressFor] (one-shot, right after logging a
 * contribution) instead of each re-deriving the contribution sum and account-balance lookup
 * themselves.
 */
@Singleton
class GoalProgressProvider @Inject constructor(
    private val goalRepository: GoalRepository,
    private val contributionRepository: GoalContributionRepository,
    private val expenseRepository: ExpenseRepository,
    private val bankAccountRepository: BankAccountRepository
) {

    val goalProgress: Flow<List<GoalProgress>> = combine(
        goalRepository.getAllGoals(),
        contributionRepository.getAllContributions(),
        expenseRepository.getAllExpenses(),
        bankAccountRepository.getAllActiveAccounts()
    ) { goals, contributions, expenses, accounts ->
        goals.map { goal -> computeProgress(goal, contributions, expenses, accounts) }
    }

    /** One-shot version for right after a write (e.g. addContribution) where waiting on the
     * StateFlow to catch up would be racy -- pulls a fresh snapshot of each input instead. */
    suspend fun currentProgressFor(goalId: Long): GoalProgress? {
        val goal = goalRepository.getGoalById(goalId) ?: return null
        val contributions = contributionRepository.getContributionsForGoal(goalId).first()
        val expenses = expenseRepository.getAllExpenses().first()
        val accounts = bankAccountRepository.getAllActiveAccounts().first()
        return computeProgress(goal, contributions, expenses, accounts)
    }

    private fun computeProgress(
        goal: Goal,
        contributions: List<GoalContribution>,
        expenses: List<Expense>,
        accounts: List<BankAccount>
    ): GoalProgress {
        val manualTotal = contributions.filter { it.goalId == goal.id }.sumOf { it.amount }

        // Same suffix-match logic HomeViewModel/ExpensesViewModel use to line up an expense's
        // bankReference with an account's stored number (full vs masked account number,
        // depending on the source).
        val linkedBalance = goal.linkedAccountId?.let { accountId ->
            val account = accounts.find { it.id == accountId } ?: return@let null
            expenses
                .filter { expense ->
                    expense.balanceAfterTransaction != null &&
                        expense.bankReference.isNotBlank() &&
                        (expense.bankReference.endsWith(account.accountNumber) ||
                            account.accountNumber.endsWith(expense.bankReference))
                }
                .maxByOrNull { it.date }
                ?.balanceAfterTransaction
        }

        return GoalProgress(
            goal = goal,
            savedAmount = (linkedBalance ?: 0.0) + manualTotal,
            linkedAccountBalance = linkedBalance,
            manualContributionTotal = manualTotal
        )
    }
}
