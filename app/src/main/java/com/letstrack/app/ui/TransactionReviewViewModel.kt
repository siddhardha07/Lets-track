package com.letstrack.app.ui

import androidx.lifecycle.ViewModel
import com.letstrack.app.service.TransactionReviewService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel to provide TransactionReviewService to Composables
 */
@HiltViewModel
class TransactionReviewViewModel @Inject constructor(
    val service: TransactionReviewService
) : ViewModel()
