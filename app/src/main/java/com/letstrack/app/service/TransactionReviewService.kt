package com.letstrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.letstrack.app.MainActivity
import com.letstrack.app.domain.model.CategoryPrediction
import com.letstrack.app.domain.model.PendingTransaction
import com.letstrack.app.domain.model.UserCorrection
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.ml.SmartCategorizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global service to manage transaction review overlay state
 * Can be triggered from anywhere in the app (SMS, notification, import, etc.)
 */
@Singleton
class TransactionReviewService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val smartCategorizer: SmartCategorizer
) {

    companion object {
        private const val TAG = "TransactionReviewService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // A backlog, not a single slot. Used to be a single MutableStateFlow<PendingTransaction?>
    // that showReview() simply overwrote - so if several SMS landed while the app was
    // backgrounded (or the system overlay failed to draw at all, e.g. under battery saver),
    // only the very last one survived in memory; everything before it was still saved to the
    // DB with needsReview=true, but silently dropped from the interactive review flow, only
    // reachable later via the Notifications screen. Now every incoming transaction is enqueued,
    // and seedQueueFromDatabase() backfills anything that was written to the DB but never made
    // it into this in-memory queue (e.g. because the process was killed and restarted between
    // then and now) - see MainActivity's call to it on start.
    private val _pendingTransactions = MutableStateFlow<List<PendingTransaction>>(emptyList())
    val pendingTransactions: StateFlow<List<PendingTransaction>> = _pendingTransactions.asStateFlow()

    // Convenience view of the queue for existing single-item UI bindings: whatever's at the
    // front is "the" current transaction being shown.
    val pendingTransaction: StateFlow<PendingTransaction?> = _pendingTransactions
        .map { it.firstOrNull() }
        .stateIn(serviceScope, SharingStarted.Eagerly, null)

    val pendingCount: StateFlow<Int> = _pendingTransactions
        .map { it.size }
        .stateIn(serviceScope, SharingStarted.Eagerly, 0)

    // The system (outside-app, WindowManager-drawn) overlay is deliberately NOT driven by the
    // queue above. It shows exactly one real-time transaction at a time and never cycles
    // through a backlog on its own - stacking multiple cards outside the app (e.g. while the
    // user is in a different app entirely) is the wrong experience; the backlog/"N to review"
    // stack is specifically an in-app thing you see when you open Lets Track, not something
    // that should follow you into other apps. Only showReview() (a genuinely new, real-time
    // transaction) sets this; seedQueueFromDatabase()'s backfill of missed/old transactions
    // deliberately never touches it.
    private val _systemOverlayTransaction = MutableStateFlow<PendingTransaction?>(null)
    val systemOverlayTransaction: StateFlow<PendingTransaction?> = _systemOverlayTransaction.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    // The live category list for the in-app review sheet's picker - MainActivity used to pass
    // nothing here at all, so TransactionReviewOverlay silently fell back to its hardcoded
    // defaultOverlayCategories (a stale list with names like "Health & Fitness" and
    // "Investments" that don't match any real category). Picking one of those in the sheet
    // then created a brand-new category on confirm instead of reusing an existing one - see
    // TransactionReviewForm's doc comment on the category-duplication bug this contributed to.
    val categoryNames: StateFlow<List<String>> = categoryRepository.getAllCategories()
        .map { categories -> categories.map { it.name } }
        .stateIn(serviceScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks whether MainActivity is currently resumed, so showReview() knows whether to bother
    // with the system overlay at all - see onAppForegrounded/onAppBackgrounded and showReview.
    @Volatile
    private var isAppForegrounded = false

    /**
     * Show transaction review overlay - enqueues; doesn't replace whatever's already pending.
     */
    fun showReview(transaction: PendingTransaction) {
        Log.d(TAG, "🎯 QUEUEING REVIEW for transaction: ${transaction.merchantName}, amount: Rs.${transaction.amount}, confidence: ${transaction.confidence}")
        Log.d(TAG, "🎯 Suggested category: ${transaction.suggestedCategory}")
        enqueue(transaction)
        if (isAppForegrounded) {
            // The app is already open -- just queue it silently and wait for the explicit
            // "Review N transactions now" button in Notifications, same as the backlog path
            // (seedQueueFromDatabase) already does. This used to auto-pop _isOverlayVisible
            // here, which was the exact "irritating auto-popup" behavior that button was built
            // to replace -- it just never got removed from this real-time path too.
            Log.d(TAG, "🎯 App is foregrounded - queuing silently, not auto-showing the review stack")
        } else {
            // This is a real-time transaction (not a backfilled/missed one), so it's also the
            // one card the system overlay is allowed to show right now.
            _systemOverlayTransaction.value = transaction
        }
        Log.d(TAG, "🎯 Transaction queued (${_pendingTransactions.value.size} pending), starting persistent service")

        // Start overlay service to show system-wide overlay
        // No need to pass transaction in Intent - it's available via Flow
        try {
            val intent = Intent(context, OverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
                Log.d(TAG, "🎯 Started OverlayService (foreground) to show system overlay")
            } else {
                context.startService(intent)
                Log.d(TAG, "🎯 Started OverlayService to show system overlay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start OverlayService: ${e.message}", e)
        }
    }

    private fun enqueue(transaction: PendingTransaction) {
        _pendingTransactions.update { current ->
            if (current.any { it.expenseId == transaction.expenseId }) current
            else current + transaction
        }
    }

    /**
     * Backfills the in-memory queue from any expense the DB already has flagged needsReview
     * but that isn't in the queue yet - covers the case where the process was killed (battery
     * saver, OEM background limits) between when a transaction was saved and now, so it never
     * got a chance to be enqueued via showReview(). Safe to call repeatedly (e.g. every app
     * open/resume, not just cold start) - it only adds expenses not already queued.
     */
    suspend fun seedQueueFromDatabase() {
        try {
            val queuedIds = _pendingTransactions.value.map { it.expenseId }.toSet()
            val categories = categoryRepository.getAllCategories().first()
            val toAdd = expenseRepository.getAllExpenses().first()
                .filter { it.needsReview && it.id !in queuedIds }
                .sortedBy { it.date } // oldest first - chronological review order
                .map { expense ->
                    PendingTransaction(
                        expenseId = expense.id,
                        amount = expense.amount,
                        merchantName = expense.merchantName.ifBlank { expense.title },
                        date = expense.date,
                        suggestedCategory = categories.find { it.id == expense.categoryId }?.name ?: "Other",
                        suggestedSubCategory = expense.subCategory,
                        confidence = expense.confidenceScore / 100.0,
                        fullSmsMessage = expense.description,
                        transactionType = expense.transactionType
                    )
                }
            if (toAdd.isNotEmpty()) {
                Log.d(TAG, "🎯 Backfilled ${toAdd.size} needs-review transaction(s) from DB into queue")
                _pendingTransactions.update { current -> current + toAdd }
            }
            // Deliberately does NOT set isOverlayVisible here. This used to auto-pop the review
            // stack open every single time the app launched (MainActivity calls this on every
            // start) whenever anything needed review - reported as "irritating" since it forces
            // a modal in front of you just for opening the app to, say, check your balance.
            // Showing the stack is now an explicit action - see showPendingReviewStack(), wired
            // to a button on the Notifications screen.
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding review queue from database: ${e.message}", e)
        }
    }

    /**
     * Explicit "review everything now" entry point - the Notifications screen's button calls
     * this. Refreshes the queue from the DB first (in case something's needsReview there isn't
     * reflected in memory yet) and only then opens the in-app stack.
     */
    suspend fun showPendingReviewStack() {
        seedQueueFromDatabase()
        if (_pendingTransactions.value.isNotEmpty()) {
            _isOverlayVisible.value = true
        }
    }

    /**
     * Show in-app review overlay (for test buttons or when app is already open)
     */
    fun showInAppReview(transaction: PendingTransaction) {
        Log.d(TAG, "🎯 Showing in-app review for: ${transaction.merchantName}")
        enqueue(transaction)
        _isOverlayVisible.value = true
    }

    /**
     * Show in-app detail overlay (when user clicks Edit from system overlay)
     */
    fun showDetailOverlay() {
        Log.d(TAG, "🎯 Showing in-app detail overlay")
        _isOverlayVisible.value = true
    }

    /**
     * Confirm or correct transaction category
     * Updates expense and teaches SmartCategorizer
     */
    suspend fun confirmTransaction(
        transaction: PendingTransaction,
        selectedCategory: String,
        selectedSubCategory: String?,
        notes: String? = null
    ) {
        try {
            Log.d(TAG, "Confirming transaction: ${transaction.merchantName} -> $selectedCategory")

            // Get the expense
            val expense = expenseRepository.getExpenseById(transaction.expenseId)
            if (expense == null) {
                Log.e(TAG, "Expense not found: ${transaction.expenseId}")
                return
            }

            // Find category ID by name, creating it if the user typed a brand new one
            val categories = categoryRepository.getAllCategories().first()
            val existingCategory = categories.find { it.name.equals(selectedCategory, ignoreCase = true) }
            val categoryId = existingCategory?.id
                ?: categoryRepository.insertCategory(
                    com.letstrack.app.domain.model.Category(name = selectedCategory)
                )

            // Update expense with confirmed category
            val updatedExpense = expense.copy(
                categoryId = categoryId,
                subCategory = selectedSubCategory,
                notes = notes ?: expense.notes,
                needsReview = false
            )
            expenseRepository.updateExpense(updatedExpense)

            // Teach SmartCategorizer
            val originalPrediction = CategoryPrediction(
                category = transaction.suggestedCategory,
                subCategory = transaction.suggestedSubCategory,
                confidence = transaction.confidence,
                source = "ml-model"
            )

            val userCorrection = UserCorrection(
                merchantName = transaction.merchantName,
                category = selectedCategory,
                subCategory = selectedSubCategory,
                isCorrect = selectedCategory.equals(transaction.suggestedCategory, ignoreCase = true)
            )

            smartCategorizer.learnFromCorrection(
                merchantName = transaction.merchantName,
                amount = transaction.amount,
                originalPrediction = originalPrediction,
                userCorrection = userCorrection
            )

            Log.d(TAG, "✅ Transaction confirmed and learned: ${transaction.merchantName} -> $selectedCategory")

            // Show immediate confirmation toast using Handler for reliability
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "✓ Saved: ${transaction.merchantName} → $selectedCategory",
                    Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "🍞 Toast shown: Saved ${transaction.merchantName}")
            }

            // Update similar past transactions from bulk imports
            updateSimilarPastTransactions(transaction.merchantName, categoryId, selectedSubCategory)

        } catch (e: Exception) {
            Log.e(TAG, "Error confirming transaction: ${e.message}", e)
        } finally {
            advanceQueue(transaction.expenseId)
        }
    }

    /**
     * Find and update similar past transactions from bulk imports
     * Called after user categorizes a real-time transaction
     */
    private suspend fun updateSimilarPastTransactions(
        merchantName: String,
        categoryId: Long,
        subCategory: String?
    ) {
        try {
            Log.d(TAG, "🔍 Searching for similar past transactions for: $merchantName")

            // Get all expenses and categories
            val allExpenses = expenseRepository.getAllExpenses().first()
            val categories = categoryRepository.getAllCategories().first()
            val otherCategory = categories.find { it.name.equals("Other", ignoreCase = true) }

            // Find uncategorized expenses with similar merchant names
            val similarExpenses = allExpenses.filter { expense ->
                val isSameMerchant = expense.title.equals(merchantName, ignoreCase = true) ||
                                    expense.title.contains(merchantName, ignoreCase = true) ||
                                    merchantName.contains(expense.title, ignoreCase = true)
                val isUncategorized = expense.categoryId == null ||
                                     expense.categoryId == otherCategory?.id ||
                                     expense.needsReview

                isSameMerchant && isUncategorized
            }

            if (similarExpenses.isNotEmpty()) {
                Log.d(TAG, "📝 Found ${similarExpenses.size} similar past transaction(s) to update")

                similarExpenses.forEach { expense ->
                    val noteAddition = if (expense.notes.isNullOrBlank()) "Auto-categorized" else " | Auto-categorized"
                    val updated = expense.copy(
                        categoryId = categoryId,
                        subCategory = subCategory,
                        needsReview = false,
                        notes = (expense.notes ?: "") + noteAddition
                    )
                    expenseRepository.updateExpense(updated)
                    Log.d(TAG, "   ✓ Updated: ${expense.title} (₹${expense.amount})")
                }

                Log.d(TAG, "✅ Updated ${similarExpenses.size} similar past transaction(s)")

                // Show toast notification to user (only if there are past transactions)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "✓ Also updated ${similarExpenses.size} similar past transaction${if (similarExpenses.size > 1) "s" else ""}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d(TAG, "🍞 Toast shown: Updated ${similarExpenses.size} past transactions")
                }

            } else {
                Log.d(TAG, "ℹ️ No similar past transactions found to update")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating similar past transactions: ${e.message}", e)
        }
    }

    /**
     * There used to be two separate buttons for "not confirming this card" - a "Skip" text
     * button that explicitly re-marked the expense needsReview, and an "X" close button that
     * just hid the card without touching the DB. In practice they looked identical to use (the
     * expense was already needsReview in the vast majority of cases, since that's *why* the
     * card was showing), so they've been collapsed into one: closing a card (X) always ensures
     * the expense is flagged for review, the same guarantee "Skip" used to provide alone.
     */
    private suspend fun markNeedsReview(expenseId: Long) {
        try {
            val expense = expenseRepository.getExpenseById(expenseId)
            if (expense != null && !expense.needsReview) {
                expenseRepository.updateExpense(expense.copy(needsReview = true))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking expense $expenseId for review: ${e.message}", e)
        }
    }

    /**
     * Closing the current card in the in-app stack without confirming - marks it needsReview
     * (see markNeedsReview) and moves the stack along.
     */
    suspend fun skipCurrentToReview() {
        val current = _pendingTransactions.value.firstOrNull() ?: return
        Log.d(TAG, "Skipping to review: ${current.merchantName}")
        markNeedsReview(current.expenseId)
        advanceQueue(current.expenseId)
    }

    /**
     * "Clear all" - whatever's left in the stack goes to Notifications/review as a batch and
     * the whole queue is dismissed at once, instead of clicking through one at a time.
     */
    suspend fun clearAllToReview() {
        val remaining = _pendingTransactions.value
        Log.d(TAG, "Clearing all ${remaining.size} pending transaction(s) to review")
        try {
            remaining.forEach { transaction ->
                val expense = expenseRepository.getExpenseById(transaction.expenseId)
                if (expense != null && !expense.needsReview) {
                    expenseRepository.updateExpense(expense.copy(needsReview = true))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all to review: ${e.message}", e)
        }
        val clearedIds = remaining.map { it.expenseId }.toSet()
        if (_systemOverlayTransaction.value?.expenseId in clearedIds) {
            _systemOverlayTransaction.value = null
        }
        _pendingTransactions.value = emptyList()
        hideOverlay()
    }

    /**
     * Pops [expenseId] off the front of the queue (if it's actually there) and either shows
     * the next card or, if the queue is now empty, hides the overlay and stops the service.
     * Also clears the system overlay's card if it happens to be the same transaction, so
     * acting on a card in-app doesn't leave a stale copy showing outside the app too.
     */
    private fun advanceQueue(expenseId: Long) {
        _pendingTransactions.update { current -> current.filterNot { it.expenseId == expenseId } }
        if (_systemOverlayTransaction.value?.expenseId == expenseId) {
            _systemOverlayTransaction.value = null
        }
        if (_pendingTransactions.value.isEmpty()) {
            hideOverlay()
        }
    }

    /**
     * Hides the system (outside-app) overlay's current card without touching the in-app
     * backlog queue at all - the expense stays needsReview=true and findable later, both in
     * Notifications and as a card in the in-app stack next time the app is opened. This is
     * deliberately NOT the same as skipCurrentToReview(): dismissing the system overlay should
     * never cause a *different*, older backlog item to pop up and take its place outside the
     * app - see the class doc on systemOverlayTransaction for why.
     */
    suspend fun dismissSystemOverlayCard() {
        val current = _systemOverlayTransaction.value
        Log.d(TAG, "Dismissing system overlay card: ${current?.merchantName}")
        if (current != null) markNeedsReview(current.expenseId)
        _systemOverlayTransaction.value = null
    }

    /**
     * Deletes the current top-of-stack transaction outright (spam or a misparsed SMS that was
     * never really a transaction) -- unlike skipCurrentToReview, this doesn't mark needsReview to
     * come back to later, it removes the underlying expense row entirely.
     */
    suspend fun deleteCurrentToReview() {
        val current = _pendingTransactions.value.firstOrNull() ?: return
        Log.d(TAG, "Deleting spam/misparsed transaction: ${current.merchantName}")
        try {
            expenseRepository.deleteExpenseById(current.expenseId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting expense ${current.expenseId}: ${e.message}", e)
        }
        advanceQueue(current.expenseId)
    }

    /** Same as [deleteCurrentToReview] but for the single card the system overlay is showing. */
    suspend fun deleteSystemOverlayCard() {
        val current = _systemOverlayTransaction.value
        Log.d(TAG, "Deleting spam/misparsed system overlay card: ${current?.merchantName}")
        if (current != null) {
            try {
                expenseRepository.deleteExpenseById(current.expenseId)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting expense ${current.expenseId}: ${e.message}", e)
            }
        }
        _systemOverlayTransaction.value = null
    }

    /**
     * Called when MainActivity comes to the foreground (ON_RESUME). The system overlay window
     * is drawn via WindowManager independently of which app has focus, so it doesn't
     * automatically get out of the way just because our own app is now on screen - without
     * this, opening the app while a system-overlay card was showing left that single, no-count
     * card visually on top of the in-app stack view underneath it, making the stack/"Clear all"
     * UI look like it never rendered at all. The transaction stays needsReview and is already
     * represented in the in-app queue (pendingTransactions), so nothing is lost by hiding it here.
     */
    fun onAppForegrounded() {
        isAppForegrounded = true
        if (_systemOverlayTransaction.value != null) {
            Log.d(TAG, "App foregrounded - hiding system overlay, in-app stack takes over")
        }
        _systemOverlayTransaction.value = null
    }

    /** Called on ON_PAUSE - once the app isn't visible, real-time transactions go back to
     *  using the system overlay again (see showReview). */
    fun onAppBackgrounded() {
        isAppForegrounded = false
    }

    /**
     * Dismiss overlay entirely - e.g. back button/scrim tap with nothing queued, or an
     * unexpected empty-queue edge case. Does NOT advance the queue itself; see
     * skipCurrentToReview() for "close this one card and move to the next."
     */
    fun dismissReview() {
        Log.d(TAG, "Dismissing review overlay")
        _pendingTransactions.value = emptyList()
        _systemOverlayTransaction.value = null
        hideOverlay()
    }

    // Used to also stopService() here every time the queue emptied - but OverlayService now
    // runs continuously by design (started from LetsTrackApp.onCreate/BootReceiver, never
    // reactively stopped) specifically so the process stays in the foreground-service priority
    // class and doesn't get frozen by Android's background broadcast-deferral. Confirmed live
    // on-device: a real SMS_RECEIVED broadcast sat deferred for 2+ hours while the process was
    // a plain cached background process. This now just hides the visible card; the underlying
    // service (and its permanent "Monitoring transactions" notification) keeps running.
    private fun hideOverlay() {
        _isOverlayVisible.value = false
    }

    /**
     * Check if overlay is currently showing
     */
    fun isShowing(): Boolean = _isOverlayVisible.value
}
