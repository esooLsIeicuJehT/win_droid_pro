package com.windroidpro.utils

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class ArchiveUtilsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun extractsNormalFileInsideDestination() {
        val destination = temporaryFolder.newFolder("runtime")
        val archive = createTarXz("bin/wine" to "runtime")

        ArchiveUtils.extractTarXz(ByteArrayInputStream(archive), destination)

        val extracted = File(destination, "bin/wine")
        assertEquals("runtime", extracted.readText())
    }

    @Test
    fun rejectsPathTraversal() {
        val destination = temporaryFolder.newFolder("runtime")
        val escapedFile = File(destination.parentFile, "escape.txt")
        val archive = createTarXz("../escape.txt" to "owned")

        assertThrows(SecurityException::class.java) {
            ArchiveUtils.extractTarXz(ByteArrayInputStream(archive), destination)
        }

        assertFalse(escapedFile.exists())
    }

    @Test
    fun rejectsSymlinkEntries() {
        val destination = temporaryFolder.newFolder("runtime")
        val bytes = ByteArrayOutputStream().use { output ->
            XZOutputStream(output, LZMA2Options()).use { xzOutput ->
                TarArchiveOutputStream(xzOutput).use { tarOutput ->
                    val entry = TarArchiveEntry("link")
                    entry.linkName = "/system/build.prop"
                    entry.linkFlag = TarArchiveEntry.LF_SYMLINK
                    tarOutput.putArchiveEntry(entry)
                    tarOutput.closeArchiveEntry()
                    tarOutput.finish()
                }
            }
            output.toByteArray()
        }

        assertThrows(SecurityException::class.java) {
            ArchiveUtils.extractTarXz(ByteArrayInputStream(bytes), destination)
        }
    }

    private fun createTarXz(vararg files: Pair<String, String>): ByteArray {
        return ByteArrayOutputStream().use { output ->
            XZOutputStream(output, LZMA2Options()).use { xzOutput ->
                TarArchiveOutputStream(xzOutput).use { tarOutput ->
                    files.forEach { (path, contents) ->
                        val data = contents.toByteArray()
                        val entry = TarArchiveEntry(path).apply {
                            size = data.size.toLong()
                        }
                        tarOutput.putArchiveEntry(entry)
                        tarOutput.write(data)
                        tarOutput.closeArchiveEntry()
                    }
                    tarOutput.finish()
                }
            }
            output.toByteArray()
        }
    }
}
