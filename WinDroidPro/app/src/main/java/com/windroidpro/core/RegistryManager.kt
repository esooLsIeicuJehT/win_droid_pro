package com.windroidpro.core

import com.windroidpro.data.Container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegistryManager @Inject constructor(
    private val commandExecutor: CommandExecutor
) {
    companion object {
        private const val MAX_PATCH_BYTES = 4L * 1024L * 1024L
    }

    suspend fun applyRegistryPatch(container: Container, patchFile: File) = withContext(Dispatchers.IO) {
        require(patchFile.isFile) { "Registry patch does not exist: $patchFile" }
        require(patchFile.length() in 1L..MAX_PATCH_BYTES) {
            "Registry patch must be between 1 byte and $MAX_PATCH_BYTES bytes"
        }

        Timber.d("Applying registry patch ${patchFile.name} to container ${container.name}")
        importRegistryContent(container, patchFile.readText())
    }

    internal fun generateRegContent(keyPath: String, valueName: String, value: String): String {
        require(keyPath.isNotBlank()) { "Registry key path must not be blank" }
        return ("Windows Registry Editor Version 5.00\n" +
            generateRegFragment(keyPath, valueName, value)).trimEnd()
    }

    suspend fun setRegistryValue(
        container: Container,
        keyPath: String,
        valueName: String,
        value: String
    ) = withContext(Dispatchers.IO) {
        Timber.d("Setting registry value $keyPath\\$valueName for container ${container.name}")
        importRegistryContent(container, generateRegContent(keyPath, valueName, value))
    }

    suspend fun getRegistryValue(
        container: Container,
        keyPath: String,
        valueName: String
    ): String? = withContext(Dispatchers.IO) {
        val (hive, subKey) = splitHiveAndSubKey(keyPath) ?: return@withContext null
        val regFileName = when (hive.uppercase()) {
            "HKEY_LOCAL_MACHINE", "HKLM" -> "system.reg"
            "HKEY_CURRENT_USER", "HKCU" -> "user.reg"
            else -> return@withContext null
        }

        val regFile = File(container.prefixPath, regFileName)
        if (!regFile.isFile) {
            Timber.d("Registry file not found: ${regFile.absolutePath}")
            return@withContext null
        }

        val cleanSubKey = subKey.trimEnd('\\')
        val wineSubKey = cleanSubKey.replace("\\", "\\\\")
        val sectionHeaderStart = "[$wineSubKey]"
        val targetPrefix = if (valueName.isEmpty() || valueName == "@") "@=" else "\"$valueName\"="

        regFile.useLines { lines ->
            var insideTargetSection = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[")) {
                    if (trimmed.startsWith(sectionHeaderStart, ignoreCase = true)) {
                        insideTargetSection = true
                    } else {
                        if (insideTargetSection) return@useLines null
                        insideTargetSection = false
                    }
                } else if (insideTargetSection && trimmed.startsWith(targetPrefix, ignoreCase = true)) {
                    val rawValue = trimmed.substring(targetPrefix.length)
                    return@useLines if (rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length >= 2) {
                        unescapeRegistryString(rawValue.substring(1, rawValue.length - 1))
                    } else {
                        rawValue
                    }
                }
            }
            null
        }
    }

    private fun importRegistryContent(container: Container, content: String) {
        require(content.isNotBlank()) { "Registry patch content must not be blank" }
        require(content.toByteArray(Charsets.UTF_8).size.toLong() <= MAX_PATCH_BYTES) {
            "Registry patch exceeds $MAX_PATCH_BYTES bytes"
        }

        val importDir = File(container.prefixPath, "drive_c/windroid/imports")
        if (!importDir.exists() && !importDir.mkdirs()) {
            throw IllegalStateException("Unable to create registry import directory: $importDir")
        }

        val patchFile = File(importDir, "registry_${UUID.randomUUID()}.reg")
        patchFile.writeText(content)

        val windowsPatchPath = "C:\\windroid\\imports\\${patchFile.name}"
        val workingDir = File(container.prefixPath, "drive_c/windows/system32").absolutePath

        try {
            val result = commandExecutor.execute(
                exe = "regedit",
                args = "/S \"$windowsPatchPath\"",
                workingDir = workingDir
            )
            if (result != 0) {
                throw IllegalStateException(
                    "Registry import failed for ${container.name}; regedit exit code $result"
                )
            }
            Timber.i("Registry patch applied successfully to ${container.name}")
        } finally {
            if (patchFile.exists() && !patchFile.delete()) {
                Timber.w("Unable to delete temporary registry patch: ${patchFile.absolutePath}")
            }
        }
    }

    private fun generateRegFragment(keyPath: String, valueName: String, value: String): String {
        val escapedValueName = valueName.replace("\\", "\\\\").replace("\"", "\\\"")
        val finalValueName = if (valueName.isEmpty() || valueName == "@") "@" else "\"$escapedValueName\""

        val finalValue = if (value.startsWith("dword:") || value.startsWith("hex:")) {
            value
        } else {
            val escapedValue = value.replace("\\", "\\\\").replace("\"", "\\\"")
            "\"$escapedValue\""
        }

        return "\n[$keyPath]\n$finalValueName=$finalValue\n"
    }

    private fun splitHiveAndSubKey(keyPath: String): Pair<String, String>? {
        val parts = keyPath.split('\\', limit = 2)
        if (parts.size < 2) return null
        return parts[0] to parts[1]
    }

    private fun unescapeRegistryString(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    '\\' -> {
                        result.append('\\')
                        index += 2
                    }
                    '"' -> {
                        result.append('"')
                        index += 2
                    }
                    else -> {
                        result.append(current)
                        result.append(next)
                        index += 2
                    }
                }
            } else {
                result.append(current)
                index++
            }
        }
        return result.toString()
    }
}
