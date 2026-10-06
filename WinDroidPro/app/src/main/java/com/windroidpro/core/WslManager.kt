package com.windroidpro.core

import com.windroidpro.data.Container
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WSL support is intentionally disabled until a real Linux userspace backend is
 * integrated. Previous builds created a batch file that only echoed a WSL-like
 * message, which made the feature appear functional when it was not.
 */
@Singleton
class WslManager @Inject constructor() {

    fun configureWsl(container: Container) {
        if (!container.enableWSL) return

        val message =
            "WSL is enabled for ${container.name}, but no Linux userspace backend is installed. " +
                "Refusing to start simulated WSL mode."
        Timber.e(message)
        throw UnsupportedOperationException(message)
    }

    fun launchWslShell(container: Container) {
        if (!container.enableWSL) {
            Timber.w("WSL is not enabled for container ${container.name}")
            return
        }

        val message =
            "Cannot launch WSL for ${container.name}: the production WSL backend is not implemented."
        Timber.e(message)
        throw UnsupportedOperationException(message)
    }
}
