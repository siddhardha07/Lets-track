package com.letstrack.app.ui.overlay

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.letstrack.app.domain.model.PendingTransaction

/**
 * In-app review sheet - a thin ModalBottomSheet wrapper around the shared TransactionReviewForm.
 * See TransactionReviewForm's doc comment: this used to be its own separately hand-built form
 * (duplicating the system overlay's amount/merchant/category/confirm-skip UI with a different
 * look), now both surfaces share one implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewOverlay(
    pendingTransaction: PendingTransaction?,
    isVisible: Boolean,
    pendingCount: Int = if (pendingTransaction != null) 1 else 0,
    availableCategories: List<String> = defaultOverlayCategories,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit = {}
) {
    if (!isVisible || pendingTransaction == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.75f)
    ) {
        TransactionReviewForm(
            transaction = pendingTransaction,
            availableCategories = availableCategories,
            pendingCount = pendingCount,
            onClearAll = onClearAll,
            onConfirm = { category, subCategory, _ -> onConfirm(category, subCategory) },
            onDismiss = onDismiss
        )
    }
}
