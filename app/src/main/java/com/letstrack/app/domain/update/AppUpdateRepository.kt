package com.letstrack.app.domain.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.gson.Gson
import com.letstrack.app.di.UpdateDataStoreQualifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

private data class GithubRelease(
    val tag_name: String?,
    val body: String?,
    val assets: List<GithubAsset>?
)

private data class GithubAsset(
    val name: String,
    val browser_download_url: String
)

private val LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
private val CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)

/**
 * Checks GitHub Releases for a newer version -- the app can never update itself (only the Play
 * Store has that privilege), so this only ever gets as far as "a newer version exists, here's
 * where to get it"; see ApkDownloader for the download-then-hand-to-system-installer half.
 *
 * The repo is public, so this needs no auth token embedded in the app (which would be a real
 * secrets-in-a-client-app problem otherwise).
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    @UpdateDataStoreQualifier private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/siddhardha07/Lets-track/releases/latest"
    }

    private val _latestRelease = MutableStateFlow<ReleaseInfo?>(null)
    val latestRelease: StateFlow<ReleaseInfo?> = _latestRelease.asStateFlow()

    /** [force] = true bypasses the 7-day throttle -- used by Settings' manual "Check for
     * updates" button, since a deliberate tap should always actually check. */
    suspend fun checkForUpdate(currentVersionName: String, force: Boolean = false): Result<Boolean> {
        if (!force) {
            val lastChecked = dataStore.data.map { it[LAST_CHECKED_AT] ?: 0L }.first()
            if (System.currentTimeMillis() - lastChecked < CHECK_INTERVAL_MS) {
                return Result.success(_latestRelease.value != null)
            }
        }

        val result = fetchLatestRelease()
        dataStore.edit { it[LAST_CHECKED_AT] = System.currentTimeMillis() }

        return result.map { release ->
            val hasUpdate = release != null && isNewerVersion(release.versionName, currentVersionName)
            _latestRelease.value = if (hasUpdate) release else null
            hasUpdate
        }
    }

    fun dismiss() {
        _latestRelease.value = null
    }

    private suspend fun fetchLatestRelease(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub request failed (${response.code})"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.success(null)
                val release = gson.fromJson(bodyString, GithubRelease::class.java)
                val tagName = release.tag_name ?: return@withContext Result.success(null)
                val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext Result.success(null)
                Result.success(
                    ReleaseInfo(
                        versionName = tagName.removePrefix("v"),
                        downloadUrl = apkAsset.browser_download_url,
                        releaseNotes = release.body ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * True if [remote] is a strictly newer version than [current] -- compares each dot-separated
 * numeric segment in turn rather than the raw strings, since a plain string compare gets
 * "1.10.0" vs "1.9.0" backwards (alphabetically "1.10.0" < "1.9.0").
 */
fun isNewerVersion(remote: String, current: String): Boolean {
    val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val length = maxOf(remoteParts.size, currentParts.size)
    for (i in 0 until length) {
        val remotePart = remoteParts.getOrElse(i) { 0 }
        val currentPart = currentParts.getOrElse(i) { 0 }
        if (remotePart != currentPart) return remotePart > currentPart
    }
    return false
}
