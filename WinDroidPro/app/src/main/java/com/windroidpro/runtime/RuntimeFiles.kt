package com.windroidpro.runtime

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/** Runtime trees contain symlinks to game drives; never follow them during cleanup. */
object RuntimeFiles {
    fun deleteTree(directory: File) {
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exception: java.io.IOException?): FileVisitResult {
                if (exception != null) throw exception
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun copyTree(source: File, destination: File) {
        val root = source.toPath()
        val target = destination.toPath()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(target.resolve(root.relativize(dir)))
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.copy(file, target.resolve(root.relativize(file)),
                    LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exception: java.io.IOException?): FileVisitResult {
                if (exception != null) throw exception
                val next = target.resolve(root.relativize(dir))
                Files.setPosixFilePermissions(next, Files.getPosixFilePermissions(dir))
                return FileVisitResult.CONTINUE
            }
        })
    }
}
