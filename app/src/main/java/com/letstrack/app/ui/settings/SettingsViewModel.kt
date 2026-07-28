package com.letstrack.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.letstrack.app.data.local.LetsTrackDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

private const val DATABASE_FILE_NAME = "letstrack_database"
private const val MANIFEST_ENTRY_NAME = "manifest.json"
private const val DATABASE_ENTRY_NAME = "letstrack_database"
private const val CURRENT_SCHEMA_VERSION = 6

private data class BackupManifest(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String
)

sealed class BackupState {
    data object Idle : BackupState()
    data object Working : BackupState()
    data class ExportSuccess(val message: String) : BackupState()
    data object ImportSuccessRestartRequired : BackupState()
    data class Failure(val message: String) : BackupState()
}

/**
 * Backup = a raw copy of the whole Room database file (all tables in one shot), not a
 * per-table JSON re-export -- chosen for completeness/speed over cross-schema-version safety.
 * A restore that doesn't match [CURRENT_SCHEMA_VERSION] is refused rather than risked.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LetsTrackDatabase
) : ViewModel() {

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun clearBackupState() {
        _backupState.value = BackupState.Idle
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Working
            _backupState.value = withContext(Dispatchers.IO) {
                try {
                    // Flush WAL into the main file so a single-file copy is self-contained.
                    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)", emptyArray<Any?>()).close()

                    val dbFile = context.getDatabasePath(DATABASE_FILE_NAME)
                    val manifest = BackupManifest(
                        schemaVersion = CURRENT_SCHEMA_VERSION,
                        exportedAt = System.currentTimeMillis(),
                        appVersion = "1.0.0"
                    )

                    val opened = context.contentResolver.openOutputStream(uri)?.use { output ->
                        ZipOutputStream(output).use { zip ->
                            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                            zip.write(Gson().toJson(manifest).toByteArray())
                            zip.closeEntry()

                            zip.putNextEntry(ZipEntry(DATABASE_ENTRY_NAME))
                            dbFile.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                        true
                    } ?: false

                    if (opened) {
                        BackupState.ExportSuccess("Backup saved.")
                    } else {
                        BackupState.Failure("Couldn't open the selected file for writing.")
                    }
                } catch (e: Exception) {
                    BackupState.Failure(e.message ?: "Export failed.")
                }
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Working
            _backupState.value = withContext(Dispatchers.IO) {
                val tempDbFile = File(context.cacheDir, "restore_$DATABASE_FILE_NAME")
                try {
                    var manifest: BackupManifest? = null

                    val opened = context.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry: ZipEntry? = zip.nextEntry
                            while (entry != null) {
                                val currentEntry = entry
                                when (currentEntry.name) {
                                    MANIFEST_ENTRY_NAME -> {
                                        manifest = Gson().fromJson(zip.reader(), BackupManifest::class.java)
                                    }
                                    DATABASE_ENTRY_NAME -> {
                                        tempDbFile.outputStream().use { output -> zip.copyTo(output) }
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                        true
                    } ?: false

                    if (!opened) return@withContext BackupState.Failure("Couldn't open the selected file.")

                    val backupManifest = manifest
                    if (backupManifest == null || !tempDbFile.exists()) {
                        return@withContext BackupState.Failure("Not a valid Let's Track backup file.")
                    }
                    if (backupManifest.schemaVersion != CURRENT_SCHEMA_VERSION) {
                        return@withContext BackupState.Failure(
                            "This backup is from an incompatible app version and can't be restored."
                        )
                    }

                    database.close()
                    val dbFile = context.getDatabasePath(DATABASE_FILE_NAME)
                    tempDbFile.copyTo(dbFile, overwrite = true)
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()

                    BackupState.ImportSuccessRestartRequired
                } catch (e: Exception) {
                    BackupState.Failure(e.message ?: "Import failed.")
                } finally {
                    tempDbFile.delete()
                }
            }
        }
    }

    /** Room's DI-injected repositories can't safely pick up a swapped db file mid-process. */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent?.let { context.startActivity(it) }
        Process.killProcess(Process.myPid())
    }
}
