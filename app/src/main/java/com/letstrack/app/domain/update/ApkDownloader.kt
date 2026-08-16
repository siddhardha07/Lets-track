package com.letstrack.app.domain.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a release APK, then hands it to the system's own installer -- this is as far as any
 * app (short of the Play Store or a rooted/device-owner setup, neither of which this touches)
 * can take an update. The FileProvider indirection is required, not optional: Android 7+ blocks
 * handing a raw file:// Uri to another app (including the system installer) over
 * FileUriExposedException, hence the content:// Uri via the FileProvider this app already
 * declares in its manifest.
 *
 * Download and install are separate steps (not one downloadAndInstall() call) because the
 * install half can be blocked by a missing "allow installs from this app" permission, which
 * sends the user out to Settings and back. The downloaded file is cached on disk per version so
 * that round trip doesn't re-download anything -- see [download]'s already-downloaded check.
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    /** Whether this app is currently allowed to launch the package installer. Always true
     * below API 26, where this permission model doesn't exist yet. */
    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants (or already has) the "install unknown apps"
     * toggle for this app specifically. */
    fun installPermissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads [downloadUrl] to a per-version file, skipping the network entirely if that
     * exact version was already downloaded (e.g. the user already fetched it once, then had to
     * go grant the install permission and come back). */
    suspend fun download(
        downloadUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val outputFile = File(outputDir, "letstrack-update-$versionName.apk")

            if (outputFile.exists() && outputFile.length() > 0) {
                onProgress(1f)
                return@withContext Result.success(outputFile)
            }

            // Clear out any older cached update APKs before writing the new one.
            outputDir.listFiles()?.forEach { it.delete() }

            val request = Request.Builder().url(downloadUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Download failed (${response.code})")
                val body = response.body ?: throw Exception("Empty response body")
                val total = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead = input.read(buffer)
                        while (bytesRead >= 0) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (total > 0) onProgress(downloaded.toFloat() / total)
                            bytesRead = input.read(buffer)
                        }
                    }
                }
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Hands the already-downloaded [apkFile] to the system installer. Only call this once
     * [canRequestPackageInstalls] is true, or the OS just shows its own "blocked" screen. */
    fun install(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
