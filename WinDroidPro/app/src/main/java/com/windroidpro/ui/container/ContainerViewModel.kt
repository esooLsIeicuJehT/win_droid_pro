package com.windroidpro.ui.container

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windroidpro.data.Container
import com.windroidpro.data.ContainerDao
import com.windroidpro.runtime.RuntimeInstaller
import com.windroidpro.runtime.RuntimeProfile
import com.windroidpro.runtime.RuntimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContainerViewModel @Inject constructor(dao: ContainerDao,
    private val repository: RuntimeRepository, installer: RuntimeInstaller) : ViewModel() {
    val containers = dao.getAllContainers().stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), emptyList())
    val runtime = installer.state
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun clearError() { mutableError.value = null }

    fun refresh() { viewModelScope.launch { runCatching { repository.synchronize() }
        .onFailure { mutableError.value = it.message } } }

    fun createContainer(name: String, description: String, profile: RuntimeProfile) = runOperation {
        repository.create(name, description, profile)
    }

    fun deleteContainer(container: Container) = runOperation { repository.delete(container) }

    fun launch(container: Container, onReady: (Int) -> Unit) = runOperation {
        onReady(repository.resolve(container))
    }

    private fun runOperation(action: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { action() }
            catch (exception: Exception) { mutableError.value = exception.message ?: "Operation failed." }
            finally { mutableBusy.value = false }
        }
    }
}
