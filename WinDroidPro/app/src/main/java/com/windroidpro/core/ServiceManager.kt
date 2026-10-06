package com.windroidpro.core

import com.windroidpro.data.Container
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceManager @Inject constructor(
    private val commandExecutor: CommandExecutor
) {

    fun startServices(container: Container) {
        if (!container.enableServices) {
            Timber.d("Services disabled for container ${container.name}")
            return
        }

        val startupDir = File(container.prefixPath, "drive_c/windows/Start Menu/Programs/Startup")
        if (!startupDir.exists() && !startupDir.mkdirs()) {
            throw IllegalStateException("Unable to create startup directory: $startupDir")
        }

        val batchFile = File(startupDir, "services_startup.bat")
        val content = buildString {
            append("@echo off\r\n")
            container.servicesList.forEach { service ->
                append("net start \"")
                append(escapeQuotedArgument(service))
                append("\"\r\n")
            }
        }

        batchFile.writeText(content)
        Timber.i("Created service startup script for ${container.name}: ${batchFile.absolutePath}")
    }

    fun startService(container: Container, serviceName: String) {
        executeServiceCommand(container, "start", serviceName)
    }

    fun stopService(container: Container, serviceName: String) {
        executeServiceCommand(container, "stop", serviceName)
    }

    private fun executeServiceCommand(container: Container, action: String, serviceName: String) {
        require(serviceName.isNotBlank()) { "Service name must not be blank" }

        val workingDir = File(container.prefixPath, "drive_c/windows/system32").absolutePath
        val escapedServiceName = escapeQuotedArgument(serviceName)
        Timber.d("${action.replaceFirstChar { it.uppercase() }}ing service $serviceName in ${container.name}")

        val result = commandExecutor.execute(
            exe = "net",
            args = "$action \"$escapedServiceName\"",
            workingDir = workingDir
        )

        if (result == 0) {
            Timber.i("Service $serviceName ${if (action == "start") "started" else "stopped"} successfully")
        } else {
            throw IllegalStateException(
                "Failed to $action service '$serviceName' in ${container.name}; exit code $result"
            )
        }
    }

    private fun escapeQuotedArgument(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
