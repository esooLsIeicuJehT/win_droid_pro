package com.windroidpro.core

import com.windroidpro.data.Container
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RegistryManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val registryManager = RegistryManager()

    @Test
    fun generateRegContent_handlesDword() {
        val content = registryManager.generateRegContent(
            "HKEY_CURRENT_USER\\Software\\Test",
            "TestDword",
            "dword:00000001"
        )

        val expected = """
            Windows Registry Editor Version 5.00

            [HKEY_CURRENT_USER\Software\Test]
            "TestDword"=dword:00000001
        """.trimIndent()

        assertEquals(expected + "\n", content)
    }

    @Test
    fun generateRegContent_handlesString() {
        val content = registryManager.generateRegContent(
            "HKEY_CURRENT_USER\\Software\\Test",
            "TestString",
            "Hello World"
        )

        val expected = """
            Windows Registry Editor Version 5.00

            [HKEY_CURRENT_USER\Software\Test]
            "TestString"="Hello World"
        """.trimIndent()

        assertEquals(expected + "\n", content)
    }

    @Test
    fun generateRegContent_handlesEscaping() {
        val content = registryManager.generateRegContent(
            "HKEY_CURRENT_USER\\Software\\Test",
            "TestPath",
            "C:\\Windows\\System32"
        )

        val expected = """
            Windows Registry Editor Version 5.00

            [HKEY_CURRENT_USER\Software\Test]
            "TestPath"="C:\\Windows\\System32"
        """.trimIndent()

        assertEquals(expected + "\n", content)
    }

    @Test
    fun generateRegContent_handlesDefaultValue() {
        val content = registryManager.generateRegContent(
            "HKEY_CURRENT_USER\\Software\\Test",
            "@",
            "DefaultValue"
        )

        val expected = """
            Windows Registry Editor Version 5.00

            [HKEY_CURRENT_USER\Software\Test]
            @="DefaultValue"
        """.trimIndent()

        assertEquals(expected + "\n", content)
    }

    @Test
    fun generateRegContent_handlesEmptyValueNameAsDefault() {
        val content = registryManager.generateRegContent(
            "HKEY_CURRENT_USER\\Software\\Test",
            "",
            "DefaultValue"
        )

        val expected = """
            Windows Registry Editor Version 5.00

            [HKEY_CURRENT_USER\Software\Test]
            @="DefaultValue"
        """.trimIndent()

        assertEquals(expected + "\n", content)
    }

    @Test
    fun generateRegContent_handlesHex() {
        val content = registryManager.generateRegContent(
            "HKEY_CURRENT_USER\\Software\\Test",
            "BinaryData",
            "hex:01,02,03,04"
        )

        val expected = """
            Windows Registry Editor Version 5.00

            [HKEY_CURRENT_USER\Software\Test]
            "BinaryData"=hex:01,02,03,04
        """.trimIndent()

        assertEquals(expected + "\n", content)
    }

    @Test
    fun getRegistryValue_readsStringValue() = runBlocking {
        val containerDir = tempFolder.newFolder("container_prefix")
        val userReg = File(containerDir, "user.reg")
        userReg.writeText("""
            WINE REGISTRY Version 2

            [Software\\Test] 123456
            "TestString"="Hello World"
            "TestEscaped"="Line1\"Line2"
            "TestBackslash"="C:\\Windows"
        """.trimIndent())

        // Create a dummy container with minimal required fields
        val container = Container(
            name = "TestContainer",
            prefixPath = containerDir.absolutePath
        )

        val value = registryManager.getRegistryValue(container, "HKCU\\Software\\Test", "TestString")
        assertEquals("Hello World", value)

        val escaped = registryManager.getRegistryValue(container, "HKCU\\Software\\Test", "TestEscaped")
        assertEquals("Line1\"Line2", escaped) // Check unescaping logic

        val backslash = registryManager.getRegistryValue(container, "HKCU\\Software\\Test", "TestBackslash")
        assertEquals("C:\\Windows", backslash)
    }

    @Test
    fun getRegistryValue_readsDwordValue() = runBlocking {
        val containerDir = tempFolder.newFolder("container_prefix_dword")
        val systemReg = File(containerDir, "system.reg")
        systemReg.writeText("""
            WINE REGISTRY Version 2

            [Software\\Test] 123456
            "TestDword"=dword:00000001
        """.trimIndent())

        val container = Container(
            name = "TestContainer",
            prefixPath = containerDir.absolutePath
        )

        val value = registryManager.getRegistryValue(container, "HKLM\\Software\\Test", "TestDword")
        assertEquals("dword:00000001", value)
    }

    @Test
    fun getRegistryValue_readsDefaultValue() = runBlocking {
        val containerDir = tempFolder.newFolder("container_prefix_default")
        val userReg = File(containerDir, "user.reg")
        userReg.writeText("""
            WINE REGISTRY Version 2

            [Software\\Test] 123456
            @="DefaultVal"
        """.trimIndent())

        val container = Container(
            name = "TestContainer",
            prefixPath = containerDir.absolutePath
        )

        val value = registryManager.getRegistryValue(container, "HKCU\\Software\\Test", "")
        assertEquals("DefaultVal", value)

        val valueAt = registryManager.getRegistryValue(container, "HKCU\\Software\\Test", "@")
        assertEquals("DefaultVal", valueAt)
    }

    @Test
    fun getRegistryValue_returnsNullForMissingKey() = runBlocking {
        val containerDir = tempFolder.newFolder("container_prefix_missing")
        val userReg = File(containerDir, "user.reg")
        userReg.writeText("""
            WINE REGISTRY Version 2

            [Software\\Test] 123456
            "Existing"="Value"
        """.trimIndent())

        val container = Container(
            name = "TestContainer",
            prefixPath = containerDir.absolutePath
        )

        val value = registryManager.getRegistryValue(container, "HKCU\\Software\\Test", "NonExistent")
        assertEquals(null, value)

        val valueSection = registryManager.getRegistryValue(container, "HKCU\\Software\\NonExistent", "Existing")
        assertEquals(null, valueSection)
    }

    @Test
    fun getRegistryValue_isCaseInsensitive() = runBlocking {
        val containerDir = tempFolder.newFolder("container_prefix_case")
        val userReg = File(containerDir, "user.reg")
        userReg.writeText("""
            WINE REGISTRY Version 2

            [Software\\Test] 123456
            "TestString"="Hello World"
        """.trimIndent())

        val container = Container(
            name = "TestContainer",
            prefixPath = containerDir.absolutePath
        )

        // Test with different casing in key path
        val valueKey = registryManager.getRegistryValue(container, "HKCU\\SOFTWARE\\TEST", "TestString")
        assertEquals("Hello World", valueKey)

        // Test with different casing in value name
        val valueName = registryManager.getRegistryValue(container, "HKCU\\Software\\Test", "TESTSTRING")
        assertEquals("Hello World", valueName)
    }
}
