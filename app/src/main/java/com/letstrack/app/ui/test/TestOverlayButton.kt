package com.letstrack.app.ui.test

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.letstrack.app.domain.model.PendingTransaction
import com.letstrack.app.service.TransactionReviewService
import kotlinx.coroutines.launch

/**
 * TEST BUTTON - Add this temporarily to any screen to test the overlay
 *
 * Usage in any screen:
 * ```kotlin
 * TestOverlayButton(transactionReviewService = transactionReviewService)
 * ```
 *
 * DELETE THIS FILE after testing!
 */
@Composable
fun TestOverlayButton(
    transactionReviewService: TransactionReviewService
) {
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            // Simulate a transaction with MEDIUM confidence
            transactionReviewService.showInAppReview(
                PendingTransaction(
                    expenseId = 999L, // Dummy ID for testing
                    amount = 599.0,
                    merchantName = "TEST MERCHANT",
                    date = System.currentTimeMillis(),
                    suggestedCategory = "Shopping",
                    suggestedSubCategory = null,
                    confidence = 0.75, // 75% - MEDIUM confidence
                    fullSmsMessage = "Your A/c debited by Rs. 599 at TEST MERCHANT on 26-Jul-2026",
                    transactionType = "DEBIT"
                )
            )
        }
    }) {
        Text("🧪 Test Overlay (75% confidence)")
    }
}

/**
 * TEMPORARY - tests the SYSTEM overlay (OverlayService/WindowManager), not the
 * in-app ModalBottomSheet. Use this to check the bottom overlay card without
 * needing a fresh, non-duplicate bank SMS. DELETE after testing!
 */
@Composable
fun TestSystemOverlayButton(
    transactionReviewService: TransactionReviewService
) {
    Button(onClick = {
        transactionReviewService.showReview(
            PendingTransaction(
                expenseId = 996L,
                amount = 249.0,
                merchantName = "TEST SYSTEM OVERLAY",
                date = System.currentTimeMillis(),
                suggestedCategory = "Shopping",
                suggestedSubCategory = null,
                confidence = 0.7,
                fullSmsMessage = "Your A/c debited by Rs. 249 at TEST SYSTEM OVERLAY",
                transactionType = "DEBIT"
            )
        )
    }) {
        Text("🧪 Test SYSTEM Overlay (bottom card)")
    }
}

/**
 * Test with LOW confidence (should show different UI)
 */
@Composable
fun TestOverlayButtonLowConfidence(
    transactionReviewService: TransactionReviewService
) {
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            transactionReviewService.showInAppReview(
                PendingTransaction(
                    expenseId = 998L,
                    amount = 1299.0,
                    merchantName = "UNKNOWN STORE",
                    date = System.currentTimeMillis(),
                    suggestedCategory = "Other",
                    suggestedSubCategory = null,
                    confidence = 0.3, // 30% - LOW confidence
                    fullSmsMessage = "Your A/c debited by Rs. 1299 at UNKNOWN STORE on 26-Jul-2026",
                    transactionType = "DEBIT"
                )
            )
        }
    }) {
        Text("🧪 Test Overlay (30% confidence)")
    }
}

/**
 * Test with HIGH confidence (should NOT show overlay in real app)
 */
@Composable
fun TestOverlayButtonHighConfidence(
    transactionReviewService: TransactionReviewService
) {
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            transactionReviewService.showInAppReview(
                PendingTransaction(
                    expenseId = 997L,
                    amount = 350.0,
                    merchantName = "SWIGGY",
                    date = System.currentTimeMillis(),
                    suggestedCategory = "Food",
                    suggestedSubCategory = null,
                    confidence = 0.95, // 95% - HIGH confidence (normally auto-categorized)
                    fullSmsMessage = "Your A/c debited by Rs. 350 at SWIGGY on 26-Jul-2026",
                    transactionType = "DEBIT"
                )
            )
        }
    }) {
        Text("🧪 Test Overlay (95% confidence)")
    }
}
