package com.windroidpro.ui

import androidx.lifecycle.ViewModel
import com.windroidpro.runtime.RuntimeInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RuntimeViewModel @Inject constructor(private val installer: RuntimeInstaller) : ViewModel() {
    val state = installer.state
    fun install() = installer.install()
}
