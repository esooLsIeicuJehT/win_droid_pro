package com.windroidpro.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class GameFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun acceptsRealGameFilenames() {
        listOf("Monster Hunter.exe", "data-01.pak", "résumé.txt", "my.game.dll").forEach {
            assertEquals(it, GameFiles.validateName(it))
        }
    }

    @Test fun rejectsTraversalAndWindowsReservedNames() {
        listOf("", " ", ".", "..", "../game.exe", "folder/game.exe", "C:\\game.exe",
            "file\u0000.exe", "CON", "con.exe", "LPT9.dll", "aux.txt", "trailing.", "space ", "bad?.exe").forEach {
            assertThrows("Filename: $it", IllegalArgumentException::class.java) { GameFiles.validateName(it) }
        }
    }

    @Test fun keepsRepeatedImportsSeparate() {
        val parent = temporary.newFolder()
        File(parent, "Game").mkdir()
        File(parent, "Game (2)").mkdir()
        assertEquals(File(parent, "Game (3)"), GameFiles.uniqueDirectory(parent, "Game"))
    }

    @Test fun rejectsSymlinkEscape() {
        val parent = temporary.newFolder("games")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(File(parent, "redirect").toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { GameFiles.destination(parent, "redirect") }
    }
}
