package com.windroidpro.utils

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ArchiveUtils {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 100_000
    private const val MAX_EXTRACTED_BYTES = 8L * 1024L * 1024L * 1024L

    /**
     * Extract a trusted runtime tar.xz archive without allowing entries to escape
     * [destinationDir]. Symlinks, hard links, device nodes, archive bombs, and
     * path traversal are rejected rather than silently materialized.
     */
    fun extractTarXz(inputStream: InputStream, destinationDir: File) {
        val destinationRoot = destinationDir.canonicalFile
        if (!destinationRoot.exists() && !destinationRoot.mkdirs()) {
            throw IllegalStateException("Unable to create extraction directory: $destinationRoot")
        }
        if (!destinationRoot.isDirectory) {
            throw IllegalArgumentException("Extraction destination is not a directory: $destinationRoot")
        }

        try {
            XZInputStream(BufferedInputStream(inputStream)).use { xzInput ->
                TarArchiveInputStream(xzInput).use { tarInput ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var entryCount = 0
                    var extractedBytes = 0L

                    while (true) {
                        val entry = tarInput.nextTarEntry ?: break
                        entryCount += 1
                        if (entryCount > MAX_ARCHIVE_ENTRIES) {
                            throw SecurityException("Archive contains too many entries")
                        }

                        if (entry.isSymbolicLink || entry.isLink) {
                            throw SecurityException("Archive links are not allowed: ${entry.name}")
                        }

                        val outputFile = File(destinationRoot, entry.name).canonicalFile
                        ensureInsideDestination(destinationRoot, outputFile, entry.name)

                        when {
                            entry.isDirectory -> {
                                if (!outputFile.exists() && !outputFile.mkdirs()) {
                                    throw IllegalStateException("Unable to create directory: $outputFile")
                                }
                            }

                            entry.isFile -> {
                                val parent = outputFile.parentFile
                                    ?: throw SecurityException("Archive entry has no valid parent: ${entry.name}")
                                ensureInsideDestination(destinationRoot, parent.canonicalFile, entry.name)
                                if (!parent.exists() && !parent.mkdirs()) {
                                    throw IllegalStateException("Unable to create directory: $parent")
                                }

                                FileOutputStream(outputFile).use { outputStream ->
                                    while (true) {
                                        val bytesRead = tarInput.read(buffer)
                                        if (bytesRead == -1) break

                                        extractedBytes += bytesRead.toLong()
                                        if (extractedBytes > MAX_EXTRACTED_BYTES) {
                                            throw SecurityException("Archive expands beyond the allowed size")
                                        }
                                        outputStream.write(buffer, 0, bytesRead)
                                    }
                                }

                                // Preserve the executable bit needed by Wine/Box64 runtime binaries.
                                if ((entry.mode and 0b001001001) != 0 && !outputFile.setExecutable(true, false)) {
                                    Timber.w("Unable to mark extracted runtime file executable: $outputFile")
                                }
                            }

                            else -> throw SecurityException(
                                "Unsupported archive entry type: ${entry.name}"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting tar.xz into $destinationRoot")
            throw e
        }
    }

    private fun ensureInsideDestination(root: File, candidate: File, entryName: String) {
        val rootPath = root.path
        val candidatePath = candidate.path
        val isRoot = candidatePath == rootPath
        val isChild = candidatePath.startsWith(rootPath + File.separator)
        if (!isRoot && !isChild) {
            throw SecurityException("Archive entry escapes destination: $entryName")
        }
    }
}
