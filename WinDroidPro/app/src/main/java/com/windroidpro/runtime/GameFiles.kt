package com.windroidpro.runtime

import java.io.File
import java.util.Locale

object GameFiles {
    fun validateName(name: String): String {
        require(name.isNotBlank() && name != "." && name != ".." &&
            name.none { it.isISOControl() || it in "\\/:*?\"<>|" } &&
            !name.endsWith('.') && !name.endsWith(' ')) { "Windows cannot use this filename: $name" }
        val stem = name.substringBefore('.').uppercase(Locale.ROOT)
        require(stem !in setOf("CON", "PRN", "AUX", "NUL") &&
            !stem.matches(Regex("(COM|LPT)[1-9]"))) { "Windows reserves this filename: $name" }
        return name
    }

    fun destination(parent: File, name: String): File {
        val target = File(parent, validateName(name))
        require(target.canonicalFile.parentFile == parent.canonicalFile) { "Invalid import path." }
        return target
    }

    fun uniqueDirectory(parent: File, name: String): File {
        validateName(name)
        var target = destination(parent, name)
        var suffix = 2
        while (target.exists()) target = destination(parent, "$name (${suffix++})")
        return target
    }
}
