package com.letstrack.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.letstrack.app.R
import com.letstrack.app.domain.model.PendingTransaction
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.ui.overlay.SystemOverlayCard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Background service that shows the transaction review card over all apps.
 * The window is sized to the card (not full-screen) and anchored to the bottom,
 * so touches outside the card pass through to whatever app the user was using.
 * Requires SYSTEM_ALERT_WINDOW permission.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    @Inject
    lateinit var transactionReviewService: TransactionReviewService

    @Inject
    lateinit var categoryRepository: CategoryRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var currentTransactionId: Long? = null // Track currently displayed transaction
    private var overlayParams: WindowManager.LayoutParams? = null

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_channel"
        const val EXTRA_TRANSACTION = "extra_transaction"

        // Flags the overlay always has, regardless of edit state.
        private val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎯 OverlayService created")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        scope.launch {
            transactionReviewService.pendingTransaction.collectLatest { transaction ->
                if (transaction != null) {
                    Log.d(TAG, "🎯 Showing system overlay for: ${transaction.merchantName}")
                    showOverlay(transaction)
                } else {
                    Log.d(TAG, "🎯 Hiding system overlay (transaction cleared)")
                    hideOverlay()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transaction Review",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoring transactions")
            .setContentText("Ready to show transaction review overlay")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🎯 OverlayService started, flags=$flags")

        // Check if transaction is already pending (from the Flow)
        val pendingTransaction = transactionReviewService.pendingTransaction.value
        if (pendingTransaction != null) {
            Log.d(TAG, "🎯 Transaction available immediately - showing overlay")
            scope.launch { showOverlay(pendingTransaction) }
        } else {
            Log.d(TAG, "🎯 No transaction yet - waiting for Flow listener")
        }

        return START_STICKY
    }

    private suspend fun showOverlay(transaction: PendingTransaction) {
        Log.d(TAG, "📱 showOverlay called for: ${transaction.merchantName}, ₹${transaction.amount}")

        // Skip if already showing this transaction
        if (currentTransactionId == transaction.expenseId && composeView != null) {
            Log.d(TAG, "⚠️ Already showing this transaction, skipping duplicate")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "❌ No overlay permission! Please enable 'Display over other apps' in settings.")
            return
        }

        // Fetch the live category list so categories added from a previous overlay
        // (or anywhere else in the app) show up as chips here too.
        val categoryNames = try {
            categoryRepository.getAllCategories().first().map { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load categories, falling back to defaults: ${e.message}")
            emptyList()
        }

        hideOverlay()

        currentTransactionId = transaction.expenseId

        val lifecycleOwner = OverlayLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        overlayLifecycleOwner = lifecycleOwner

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val showSuccess = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                val successMsg = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

                OverlayTheme {
                    SystemOverlayCard(
                        transaction = transaction,
                        availableCategories = categoryNames.ifEmpty { null }
                            ?: com.letstrack.app.ui.overlay.defaultOverlayCategories,
                        showSuccessMessage = showSuccess.value,
                        successMessage = successMsg.value,
                        onConfirm = { category, subCategory, notes ->
                            scope.launch {
                                // Show success message in overlay
                                successMsg.value = "✓ Saved: ${transaction.merchantName} → $category"
                                showSuccess.value = true

                                transactionReviewService.confirmTransaction(
                                    transaction,
                                    category,
                                    subCategory,
                                    notes
                                )

                                // Wait for toast to show, then dismiss
                                kotlinx.coroutines.delay(1000)
                                transactionReviewService.dismissReview()
                            }
                        },
                        onSkip = {
                            scope.launch {
                                transactionReviewService.rejectTransaction(transaction)
                                transactionReviewService.dismissReview()
                            }
                        },
                        onDismiss = {
                            transactionReviewService.dismissReview()
                        },
                        onEditingChanged = { isEditing -> setOverlayFocusable(isEditing) }
                    )
                }
            }
        }
        composeView = view

        // Starts non-focusable (touches pass through, doesn't steal focus from the
        // app underneath). setOverlayFocusable() flips this on/off on demand while
        // the user is actually editing a field - see the class doc comment there
        // for why it can't just be one or the other permanently.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        overlayParams = params

        try {
            windowManager?.addView(view, params)
            Log.d(TAG, "✅ Overlay shown successfully over all apps!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show overlay: ${e.message}", e)
        }
    }

    /**
     * The overlay window is FLAG_NOT_FOCUSABLE by default so it never disturbs
     * whatever app the user was in (no stolen back-button/input focus). But a
     * window that can never take focus can also never be the IME's target, so
     * no text field inside it can bring up the keyboard. There is no in-between
     * flag - it's binary - so we flip this on for the brief window while the
     * user is actually editing a field, then flip it back off afterwards.
     */
    private fun setOverlayFocusable(focusable: Boolean) {
        val view = composeView ?: return
        val params = overlayParams ?: return

        params.flags = if (focusable) {
            BASE_FLAGS
        } else {
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle overlay focusability: ${e.message}")
            return
        }

        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        if (focusable) {
            // The field was tapped while the window still couldn't take focus,
            // so the original IME request never landed - ask again now that
            // the window is eligible. Posted so it runs after the pending
            // layout pass from updateViewLayout actually lands.
            view.post {
                imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun hideOverlay() {
        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.d(TAG, "✅ Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding overlay: ${e.message}")
            }
        }
        overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayLifecycleOwner = null
        composeView = null
        currentTransactionId = null
        overlayParams = null
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        scope.cancel()
        Log.d(TAG, "🎯 OverlayService destroyed")
    }
}

/**
 * Minimal MaterialTheme wrapper for the overlay. Deliberately doesn't reuse
 * LetsTrackTheme, which touches the hosting Activity's window (status bar color) -
 * this view is hosted by a Service, not an Activity, so there is no such window.
 */
@Composable
private fun OverlayTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(),
        content = content
    )
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
