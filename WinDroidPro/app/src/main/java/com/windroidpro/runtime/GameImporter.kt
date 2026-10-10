package com.windroidpro.runtime

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class GameImporter @Inject constructor(@ApplicationContext private val context: Context,
    private val repository: RuntimeRepository) {
    suspend fun importFolder(uri: Uri, progress: (String) -> Unit): File = withContext(Dispatchers.IO) {
        val document = DocumentFile.fromTreeUri(context, uri)
            ?: error("Could not open selected folder.")
        require(document.isDirectory) { "Select a game folder." }
        check(document.canRead()) { "Android did not grant access to this folder. Select it again." }
        importStaged(document.name ?: "Imported game", progress) { staging ->
            copyChildren(document, staging, 0, progress)
        }
    }

    suspend fun importFiles(uris: List<Uri>, progress: (String) -> Unit): File = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Select at least one file." }
        importStaged("Imported files", progress) { staging ->
            val names = HashSet<String>()
            for (uri in uris) {
                val document = DocumentFile.fromSingleUri(context, uri) ?: error("Could not open selected file.")
                val name = GameFiles.validateName(document.name ?: "program.exe")
                require(names.add(name.lowercase(java.util.Locale.ROOT))) { "Two files have the same Windows filename: $name" }
                copyFile(document, GameFiles.destination(staging, name), progress)
            }
        }
    }

    private suspend fun importStaged(name: String, progress: (String) -> Unit,
        copy: suspend (File) -> Unit): File {
        val root = repository.gamesDirectory
        val destination = GameFiles.uniqueDirectory(root, name)
        val staging = File(root, ".import-${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Could not create import directory." }
        try {
            copy(staging)
            currentCoroutineContext().ensureActive()
            check(staging.renameTo(destination)) { "Could not finish importing game." }
            progress("Imported ${destination.name}")
            return destination
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private suspend fun copyChildren(source: DocumentFile, target: File, depth: Int, progress: (String) -> Unit) {
        require(depth <= 64) { "Folder nesting is too deep." }
        currentCoroutineContext().ensureActive()
        val children = source.listFiles()
        val names = HashSet<String>()
        for (child in children) {
            val name = GameFiles.validateName(child.name ?: error("A file has no name."))
            require(names.add(name.lowercase(java.util.Locale.ROOT))) { "Two files have the same Windows filename: $name" }
            val destination = GameFiles.destination(target, name)
            if (child.isDirectory) {
                check(destination.mkdir()) { "Could not create folder $name." }
                copyChildren(child, destination, depth + 1, progress)
            } else copyFile(child, destination, progress)
        }
    }

    private suspend fun copyFile(source: DocumentFile, destination: File, progress: (String) -> Unit) {
        currentCoroutineContext().ensureActive()
        check(source.isFile && source.canRead()) { "Could not read ${destination.name}. Select the files again." }
        check(context.filesDir.usableSpace > source.length() + 128L * 1024 * 1024) { "Not enough free storage to import ${destination.name}." }
        progress("Importing ${destination.name}…")
        context.contentResolver.openInputStream(source.uri).use { input ->
            checkNotNull(input) { "Could not read ${destination.name}." }
            destination.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
    }
}
