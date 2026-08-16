package com.letstrack.app.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.BuildConfig
import com.letstrack.app.domain.update.ApkDownloader
import com.letstrack.app.domain.update.AppUpdateRepository
import com.letstrack.app.domain.update.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float) : UpdateDownloadState()
    /** Downloaded, but this app isn't currently allowed to launch the installer -- the user
     * needs to flip the "allow installs from this app" toggle in Settings first. */
    data object AwaitingInstallPermission : UpdateDownloadState()
    data class Failed(val message: String) : UpdateDownloadState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val apkDownloader: ApkDownloader
) : ViewModel() {

    val latestRelease: StateFlow<ReleaseInfo?> = appUpdateRepository.latestRelease

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val _checkResultMessage = MutableStateFlow<String?>(null)
    val checkResultMessage: StateFlow<String?> = _checkResultMessage.asStateFlow()

    // Cached in memory so returning from the "allow installs" Settings screen can go straight
    // to the installer instead of re-downloading (download() also caches to disk per-version,
    // covering the case where the process itself got killed while the user was in Settings).
    private var downloadedFile: File? = null

    /** The manual "Check for updates" button in Settings -- always bypasses the 7-day throttle
     * and always reports something back, unlike the silent startup check. */
    fun checkNow() {
        viewModelScope.launch {
            val result = appUpdateRepository.checkForUpdate(BuildConfig.VERSION_NAME, force = true)
            _checkResultMessage.value = result.fold(
                onSuccess = { hasUpdate -> if (hasUpdate) null else "You're on the latest version" },
                onFailure = { "Couldn't check for updates: ${it.message ?: "unknown error"}" }
            )
        }
    }

    fun clearCheckResultMessage() {
        _checkResultMessage.value = null
    }

    fun startUpdate(release: ReleaseInfo) {
        viewModelScope.launch {
            _downloadState.value = UpdateDownloadState.Downloading(0f)
            val result = apkDownloader.download(release.downloadUrl, release.versionName) { progress ->
                _downloadState.value = UpdateDownloadState.Downloading(progress)
            }
            result.fold(
                onSuccess = { file ->
                    downloadedFile = file
                    proceedToInstall()
                },
                onFailure = { _downloadState.value = UpdateDownloadState.Failed(it.message ?: "Download failed") }
            )
        }
    }

    private fun proceedToInstall() {
        val file = downloadedFile ?: return
        if (apkDownloader.canRequestPackageInstalls()) {
            apkDownloader.install(file)
            _downloadState.value = UpdateDownloadState.Idle
        } else {
            _downloadState.value = UpdateDownloadState.AwaitingInstallPermission
        }
    }

    /** Called from MainActivity's ON_RESUME observer -- re-checks the install permission after
     * the user comes back from the Settings screen, and installs the already-cached APK
     * directly if it's now granted, instead of restarting the whole flow. */
    fun onAppResumed() {
        if (_downloadState.value is UpdateDownloadState.AwaitingInstallPermission) {
            proceedToInstall()
        }
    }

    fun installPermissionIntent(): Intent = apkDownloader.installPermissionSettingsIntent()

    fun dismiss() {
        appUpdateRepository.dismiss()
        _downloadState.value = UpdateDownloadState.Idle
    }
}
