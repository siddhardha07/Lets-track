package com.letstrack.app.ui.goals

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.domain.goal.GoalProgress
import com.letstrack.app.domain.goal.GoalProgressProvider
import com.letstrack.app.domain.model.BankAccount
import com.letstrack.app.domain.model.Goal
import com.letstrack.app.domain.model.GoalContribution
import com.letstrack.app.domain.repository.BankAccountRepository
import com.letstrack.app.domain.repository.GoalContributionRepository
import com.letstrack.app.domain.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class GoalFormState(
    val name: String = "",
    val targetAmount: String = "",
    val alreadySaved: String = "",
    val link: String = "",
    val photoUri: String? = null,
    val linkedAccountId: Long? = null
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val contributionRepository: GoalContributionRepository,
    private val goalProgressProvider: GoalProgressProvider,
    private val bankAccountRepository: BankAccountRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val bankAccounts: StateFlow<List<BankAccount>> = bankAccountRepository.getAllActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goalProgress: StateFlow<List<GoalProgress>> = goalProgressProvider.goalProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formState = MutableStateFlow(GoalFormState())
    val formState: StateFlow<GoalFormState> = _formState.asStateFlow()

    // One-shot: set the instant a contribution/sync crosses the target, cleared once the
    // celebration UI has shown it, so it doesn't refire on every recomposition.
    private val _justAchievedGoal = MutableStateFlow<Goal?>(null)
    val justAchievedGoal: StateFlow<Goal?> = _justAchievedGoal.asStateFlow()

    fun onNameChange(value: String) { _formState.value = _formState.value.copy(name = value) }
    fun onTargetAmountChange(value: String) { _formState.value = _formState.value.copy(targetAmount = value) }
    fun onAlreadySavedChange(value: String) { _formState.value = _formState.value.copy(alreadySaved = value) }
    fun onLinkChange(value: String) { _formState.value = _formState.value.copy(link = value) }
    fun onPhotoPicked(uri: String?) { _formState.value = _formState.value.copy(photoUri = uri) }
    fun onLinkedAccountChange(accountId: Long?) { _formState.value = _formState.value.copy(linkedAccountId = accountId) }

    fun resetForm() {
        _formState.value = GoalFormState()
    }

    fun loadGoalForEdit(goalId: Long) {
        viewModelScope.launch {
            val goal = goalRepository.getGoalById(goalId) ?: return@launch
            _formState.value = GoalFormState(
                name = goal.name,
                targetAmount = formatPlainAmount(goal.targetAmount),
                link = goal.link ?: "",
                photoUri = goal.photoUri,
                linkedAccountId = goal.linkedAccountId
            )
        }
    }

    fun saveNewGoal(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _formState.value
            val target = state.targetAmount.toDoubleOrNull() ?: return@launch
            val savedPhoto = state.photoUri?.let { persistPhotoIfNeeded(it) }
            val goalId = goalRepository.insertGoal(
                Goal(
                    name = state.name,
                    targetAmount = target,
                    photoUri = savedPhoto,
                    link = state.link.ifBlank { null },
                    linkedAccountId = state.linkedAccountId
                )
            )
            val alreadySaved = state.alreadySaved.toDoubleOrNull()
            if (alreadySaved != null && alreadySaved > 0) {
                contributionRepository.addContribution(
                    GoalContribution(goalId = goalId, amount = alreadySaved, note = "Already saved")
                )
                checkAchievement(goalId)
            }
            resetForm()
            onSuccess()
        }
    }

    fun updateGoal(goalId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _formState.value
            val existing = goalRepository.getGoalById(goalId) ?: return@launch
            val target = state.targetAmount.toDoubleOrNull() ?: return@launch
            val savedPhoto = state.photoUri?.let { persistPhotoIfNeeded(it) } ?: existing.photoUri
            goalRepository.updateGoal(
                existing.copy(
                    name = state.name,
                    targetAmount = target,
                    photoUri = savedPhoto,
                    link = state.link.ifBlank { null },
                    linkedAccountId = state.linkedAccountId
                )
            )
            resetForm()
            onSuccess()
        }
    }

    fun deleteGoal(goal: Goal, onSuccess: () -> Unit) {
        viewModelScope.launch {
            contributionRepository.deleteContributionsForGoal(goal.id)
            goalRepository.deleteGoal(goal)
            onSuccess()
        }
    }

    fun getContributionsForGoal(goalId: Long): Flow<List<GoalContribution>> =
        contributionRepository.getContributionsForGoal(goalId)

    /** One-shot, awaiting the real DB read -- see GoalDetailScreen's doc comment on why deciding
     * "this goal doesn't exist" off [goalProgress]'s StateFlow directly was wrong (that one's
     * seeded with emptyList() and looks exactly like "goal not found" for a moment on every
     * fresh navigation to the detail screen, not just when a goal is genuinely deleted). */
    suspend fun loadInitialProgress(goalId: Long) = goalProgressProvider.currentProgressFor(goalId)

    fun addContribution(goalId: Long, amount: Double, note: String? = null) {
        viewModelScope.launch {
            contributionRepository.addContribution(GoalContribution(goalId = goalId, amount = amount, note = note))
            checkAchievement(goalId)
        }
    }

    fun clearJustAchieved() {
        _justAchievedGoal.value = null
    }

    // A fresh one-shot read (GoalProgressProvider.currentProgressFor), not this ViewModel's own
    // goalProgress StateFlow -- that one can still be holding the pre-write value for a beat
    // right after addContribution, which would make this check compare against a stale total.
    private suspend fun checkAchievement(goalId: Long) {
        val progress = goalProgressProvider.currentProgressFor(goalId) ?: return
        if (progress.goal.isAchieved || progress.goal.targetAmount <= 0) return
        if (progress.savedAmount >= progress.goal.targetAmount) {
            val achieved = progress.goal.copy(isAchieved = true, achievedAt = System.currentTimeMillis())
            goalRepository.updateGoal(achieved)
            _justAchievedGoal.value = achieved
        }
    }

    // Same save-to-internal-storage pattern CategoryManagementViewModel uses for custom category
    // icons -- copies whatever the picker handed back (a content:// Uri) into app-private storage
    // so it survives after the picker's own Uri permission expires. A no-op if this photo was
    // already persisted (editing a goal without changing its photo).
    private fun persistPhotoIfNeeded(uriString: String): String {
        if (uriString.startsWith("file://")) return uriString
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val dir = File(context.filesDir, "goal_photos")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "goal_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
            "file://${file.absolutePath}"
        } catch (e: Exception) {
            e.printStackTrace()
            uriString
        }
    }
}

fun formatPlainAmount(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
