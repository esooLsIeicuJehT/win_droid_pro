package com.windroidpro.runtime

import android.content.Context
import android.os.Build
import com.winlator.SettingsFragment
import com.winlator.core.TarCompressorUtils
import com.winlator.xenvironment.RootFS
import com.winlator.xenvironment.RootFSInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class RuntimeState(val ready: Boolean = false, val installing: Boolean = false,
    val progress: Int = 0, val message: String = "Windows runtime needs setup")

@Singleton
class RuntimeInstaller @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(RuntimeState())
    val state = mutableState.asStateFlow()

    init { scope.launch { refresh() } }

    private fun valid(root: File): Boolean =
        runCatching { File(root, ".winlator/.rfs_version").readText().trim().toInt() }.getOrNull() == RootFSInstaller.LATEST_VERSION.toInt() &&
        File(root, "opt/wine/bin/wine").isFile && File(root, "usr/lib/libc.so.6").isFile

    @Synchronized
    fun refresh() {
        if (state.value.installing) return
        val ready = valid(RootFS.find(context).rootDir)
        mutableState.value = RuntimeState(ready = ready,
            message = if (ready) "Windows runtime ready" else "Install Windows runtime to get started")
    }

    @Synchronized
    fun install() {
        if (state.value.installing || state.value.ready) return
        mutableState.value = RuntimeState(installing = true, message = "Preparing Windows runtime…")
        scope.launch {
            val root = RootFS.find(context).rootDir
            val staging = File(context.filesDir, "rfs-installing")
            val backup = File(context.filesDir, "rfs-backup")
            try {
                check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "This runtime needs a 64-bit ARM Android device." }
                // Recover a process death between backing up the old runtime and
                // publishing the new one. Never remove the user's only copy.
                if (!root.exists() && backup.exists()) check(backup.renameTo(root)) { "Could not restore previous runtime." }
                if (backup.exists() && !valid(root)) error("Previous runtime backup found. Restore it before setup.")
                RuntimeFiles.deleteTree(staging)
                check(staging.mkdirs()) { "Could not create runtime directory." }
                val total = TarCompressorUtils.getContentLength(TarCompressorUtils.Type.ZSTD,
                    context, RootFSInstaller.FILENAME, staging)
                check(total > 0) { "Runtime archive is missing or damaged." }
                check(context.filesDir.usableSpace > total + 512L * 1024 * 1024) {
                    "Free at least ${(total / (1024 * 1024)) + 512} MB, then retry setup."
                }
                val extracted = AtomicLong()
                val success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD,
                    context, RootFSInstaller.FILENAME, staging) { file, size ->
                    if (size > 0) {
                        val percent = (extracted.addAndGet(size) * 95 / total).toInt().coerceIn(0, 95)
                        mutableState.value = RuntimeState(installing = true, progress = percent,
                            message = "Installing Windows runtime… $percent%")
                    }
                    file
                }
                check(success && File(staging, "opt/wine/bin/wine").isFile &&
                    File(staging, "usr/lib/libc.so.6").isFile) { "Runtime extraction failed. Retry setup." }
                // Preserve Wine containers and separately installed Wine versions.
                for (relative in listOf("home", "opt/installed-wine")) {
                    val previous = File(root, relative)
                    if (previous.isDirectory) {
                        val next = File(staging, relative)
                        RuntimeFiles.deleteTree(next)
                        RuntimeFiles.copyTree(previous, next)
                    }
                }
                File(staging, ".winlator").mkdirs()
                File(staging, ".winlator/.rfs_version").writeText(RootFSInstaller.LATEST_VERSION.toString())
                if (root.exists()) {
                    RuntimeFiles.deleteTree(backup)
                    check(root.renameTo(backup)) { "Could not back up existing runtime." }
                }
                if (!staging.renameTo(root)) {
                    backup.renameTo(root)
                    error("Could not finish runtime setup.")
                }
                SettingsFragment.resetPreferenceVersions(context)
                runCatching { RuntimeFiles.deleteTree(backup) }
                mutableState.value = RuntimeState(ready = true, progress = 100, message = "Windows runtime ready")
            } catch (exception: Exception) {
                runCatching { RuntimeFiles.deleteTree(staging) }
                mutableState.value = RuntimeState(message = exception.message ?: "Runtime setup failed. Retry setup.")
            }
        }
    }
}
