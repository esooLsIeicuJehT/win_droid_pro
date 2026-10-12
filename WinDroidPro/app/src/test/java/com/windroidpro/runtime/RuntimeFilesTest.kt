package com.windroidpro.runtime

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class RuntimeFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cleaningRuntimeKeepsMappedGameDrive() {
        val games = temporary.newFolder("games")
        val executable = File(games, "game.exe").apply { writeText("Keep me") }
        val runtime = temporary.newFolder("runtime")
        Files.createSymbolicLink(File(runtime, "d:").toPath(), games.toPath())
        RuntimeFiles.deleteTree(runtime)
        assertFalse(runtime.exists())
        assertEquals("Keep me", executable.readText())
    }

    @Test fun copyingRuntimePreservesLinksAndExecutableMode() {
        val source = temporary.newFolder("source")
        val binary = File(source, "wine").apply { writeText("ELF"); setExecutable(true) }
        Files.createSymbolicLink(File(source, "wine64").toPath(), binary.toPath().fileName)
        Files.createSymbolicLink(File(source, "missing-drive").toPath(), File(source, "absent").toPath())
        val destination = File(temporary.root, "copy")
        RuntimeFiles.copyTree(source, destination)
        assertTrue(Files.isSymbolicLink(File(destination, "wine64").toPath()))
        assertEquals(binary.toPath().fileName, Files.readSymbolicLink(File(destination, "wine64").toPath()))
        assertTrue(Files.isSymbolicLink(File(destination, "missing-drive").toPath()))
        assertTrue(Files.getPosixFilePermissions(File(destination, "wine").toPath()).contains(PosixFilePermission.OWNER_EXECUTE))
    }
}
