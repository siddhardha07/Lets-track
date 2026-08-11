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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
import com.letstrack.app.ui.overlay.OverlayCardTheme
import com.letstrack.app.ui.overlay.TransactionReviewForm
import com.letstrack.app.ui.overlay.defaultOverlayCategories
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

    @Inject
    lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var currentTransactionId: Long? = null // Track currently displayed transaction
    private var overlayParams: WindowManager.LayoutParams? = null

    // showOverlay() reliably gets called twice in quick succession for the same transaction:
    // handleIncomingSms's pre-start AND TransactionReviewService.showReview()'s own
    // startForegroundService() call both trigger onStartCommand(), which calls showOverlay()
    // again if a transaction is already pending. The old duplicate-guard checked
    // `currentTransactionId == id && composeView != null` - but composeView is only set at the
    // very end, after category/theme lookups that suspend - so a second call arriving *during*
    // that window sailed straight past the guard and built a second, competing view. Set this
    // synchronously, before any suspending work, so the second call is caught immediately.
    private var inFlightTransactionId: Long? = null

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_channel"
        const val EXTRA_TRANSACTION = "extra_transaction"

        // Flags the overlay always has, regardless of edit state. Deliberately does NOT
        // include FLAG_LAYOUT_IN_SCREEN: that flag lets the window extend under the status/nav
        // bar decor, which this bottom-anchored WRAP_CONTENT card never needs, and on some OEM
        // skins a decor-overlapping alert window forces whatever's fullscreen underneath (e.g. a
        // WhatsApp call) out of immersive mode -- dropping it removes that whole class of risk.
        private val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎯 OverlayService created")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Watches systemOverlayTransaction specifically (a single real-time slot), NOT the
        // in-app backlog queue (pendingTransactions/pendingTransaction) - see that flow's doc
        // comment. Watching the queue here used to mean that after acting on one card, the
        // *next* backlogged transaction would automatically pop up outside the app too, i.e.
        // exactly the "multiple overlays outside the app" behavior that shouldn't happen.
        scope.launch {
            transactionReviewService.systemOverlayTransaction.collectLatest { transaction ->
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
            // Fully silent -- this notification exists only to satisfy the foreground-service
            // requirement and should never make a sound, vibrate, or heads-up over whatever
            // app (e.g. a WhatsApp call) is currently on screen.
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🎯 OverlayService started, flags=$flags")

        // Check if a real-time transaction is already pending (from the Flow)
        val pendingTransaction = transactionReviewService.systemOverlayTransaction.value
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

        // Skip if already showing this transaction, OR already in the middle of building its
        // view (see inFlightTransactionId's doc comment for why the composeView check alone
        // isn't enough to catch the second of two near-simultaneous calls).
        if ((currentTransactionId == transaction.expenseId && composeView != null) ||
            inFlightTransactionId == transaction.expenseId
        ) {
            Log.d(TAG, "⚠️ Already showing/building this transaction, skipping duplicate")
            return
        }
        inFlightTransactionId = transaction.expenseId

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "❌ No overlay permission! Please enable 'Display over other apps' in settings.")
            inFlightTransactionId = null
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

        // Match whatever accent color (and, via OverlayCardTheme, the real light/dark ColorScheme)
        // the user picked in Settings, so the overlay actually looks like the rest of the app
        // instead of a hardcoded palette.
        val accentTheme = try {
            val stored = dataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("accent_theme")]
            stored?.let { runCatching { com.letstrack.app.ui.theme.AccentTheme.valueOf(it) }.getOrNull() }
                ?: com.letstrack.app.ui.theme.AccentTheme.GREEN
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read accent theme, defaulting to green: ${e.message}")
            com.letstrack.app.ui.theme.AccentTheme.GREEN
        }

        hideOverlay()

        currentTransactionId = transaction.expenseId
        // Committed to showing this one now - the currentTransactionId+composeView check a few
        // lines up covers duplicate-detection from here on, once composeView is set below.
        inFlightTransactionId = null

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

                OverlayCardTheme(accentTheme) {
                    val cardShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.75f)
                            .shadow(elevation = 24.dp, shape = cardShape),
                        shape = cardShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                    TransactionReviewForm(
                        transaction = transaction,
                        availableCategories = categoryNames.ifEmpty { null }
                            ?: defaultOverlayCategories,
                        showSuccessMessage = showSuccess.value,
                        successMessage = successMsg.value,
                        onConfirm = { category, subCategory, notes ->
                            scope.launch {
                                // Show success message in overlay
                                successMsg.value = "✓ Saved: ${transaction.merchantName} → $category"
                                showSuccess.value = true

                                // confirmTransaction() clears this card from the system overlay
                                // slot (and the in-app backlog) once done. The system overlay
                                // never auto-advances to a *different*, older backlog item -
                                // only a genuinely new real-time transaction shows up here next.
                                transactionReviewService.confirmTransaction(
                                    transaction,
                                    category,
                                    subCategory,
                                    notes
                                )

                                // Let the success message be visible briefly before the card
                                // disappears.
                                kotlinx.coroutines.delay(1000)
                            }
                        },
                        onDismiss = {
                            // The one way to close a card without confirming - guarantees it's
                            // flagged needsReview (findable later in Notifications and as an
                            // in-app stack card), then just hides this one card outside the app,
                            // without pulling up a different, older backlog item to replace it.
                            // Used to have a separate "Skip" button wired to the exact same
                            // outcome as this; collapsed since they never differed in practice.
                            scope.launch {
                                transactionReviewService.dismissSystemOverlayCard()
                            }
                        },
                        onEditingChanged = { isEditing -> setOverlayFocusable(isEditing) }
                    )
                    }
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
