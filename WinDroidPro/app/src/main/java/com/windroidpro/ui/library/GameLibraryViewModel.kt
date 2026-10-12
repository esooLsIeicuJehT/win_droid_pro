package com.windroidpro.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windroidpro.data.Container
import com.windroidpro.data.ContainerDao
import com.windroidpro.runtime.GameImporter
import com.windroidpro.runtime.RuntimeInstaller
import com.windroidpro.runtime.RuntimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GameLibraryViewModel @Inject constructor(private val importer: GameImporter,
    private val repository: RuntimeRepository, dao: ContainerDao, installer: RuntimeInstaller) : ViewModel() {
    val containers = dao.getAllContainers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val runtime = installer.state
    private val mutableExecutables = MutableStateFlow<List<File>>(emptyList())
    val executables = mutableExecutables.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    val gamesDirectory get() = repository.gamesDirectory

    init { refresh() }

    fun clearMessage() { mutableMessage.value = null }
    fun refresh() { viewModelScope.launch {
        runCatching { repository.synchronize(); refreshFiles() }
            .onFailure { mutableMessage.value = it.message ?: "Could not refresh game library." }
    } }
    private suspend fun refreshFiles() = withContext(Dispatchers.IO) {
        mutableExecutables.value = repository.gamesDirectory.listFiles().orEmpty()
            .filter { !it.name.startsWith('.') }.flatMap { folder ->
                folder.walkTopDown().maxDepth(64).filter { it.isFile && it.extension.equals("exe", true) }.toList()
            }.sortedBy { it.absolutePath.lowercase() }
    }

    fun importFolder(uri: Uri) = operate {
        importer.importFolder(uri) { mutableMessage.value = it }
        refreshFiles()
    }
    fun importFiles(uris: List<Uri>) = operate {
        importer.importFiles(uris) { mutableMessage.value = it }
        refreshFiles()
    }
    fun launch(file: File, container: Container, callback: (Int) -> Unit) = operate {
        check(file.isFile && file.canonicalPath.startsWith(repository.gamesDirectory.canonicalPath + File.separator)) {
            "This executable is no longer in the game library."
        }
        callback(repository.resolve(container))
    }
    private fun operate(action: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { action() }
            catch (exception: Exception) { mutableMessage.value = exception.message ?: "Operation failed." }
            finally { mutableBusy.value = false }
        }
    }
}
