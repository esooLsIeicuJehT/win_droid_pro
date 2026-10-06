package com.windroidpro.native_bridge

import timber.log.Timber

/**
 * Native bridge for the WinDroid runtime and USB integration.
 *
 * The bridge deliberately fails closed. Callers must never receive a success
 * result when the JNI library itself could not be loaded.
 */
object NativeBridge {
    private val libraryLoadResult: Result<Unit> = runCatching {
        System.loadLibrary("windroidpro")
    }.onSuccess {
        Timber.d("Native library loaded successfully")
    }.onFailure { error ->
        Timber.e(error, "Failed to load native library")
    }

    val isAvailable: Boolean
        get() = libraryLoadResult.isSuccess

    private fun requireAvailable() {
        libraryLoadResult.getOrElse { error ->
            throw IllegalStateException("WinDroid native runtime is unavailable", error)
        }
    }

    private external fun nativeGetVersion(): String
    private external fun nativeInitializeWine(winePrefix: String, wineArch: String): Boolean
    private external fun nativeExecuteWineApp(exePath: String, args: String, workingDir: String): Int
    private external fun nativeInitializeBox64(libPath: String): Boolean
    private external fun nativeInitializeUSB(): Boolean
    private external fun nativeAttachUSBDevice(vendorId: Int, productId: Int, fd: Int): Boolean
    private external fun nativeDetachUSBDevice(deviceId: Int): Boolean
    private external fun nativeSetBox64Config(preset: String): Boolean
    private external fun nativeOptimizeMemory(): Boolean
    private external fun nativeCleanup()

    fun getVersion(): String {
        requireAvailable()
        return nativeGetVersion()
    }

    fun initializeWine(winePrefix: String, wineArch: String): Boolean {
        requireAvailable()
        require(winePrefix.isNotBlank()) { "Wine prefix must not be blank" }
        require(wineArch == "win32" || wineArch == "win64") {
            "Unsupported Wine architecture: $wineArch"
        }
        return nativeInitializeWine(winePrefix, wineArch)
    }

    fun executeWineApp(exePath: String, args: String, workingDir: String): Int {
        requireAvailable()
        require(exePath.isNotBlank()) { "Executable path must not be blank" }
        return nativeExecuteWineApp(exePath, args, workingDir)
    }

    fun initializeBox64(libPath: String): Boolean {
        requireAvailable()
        require(libPath.isNotBlank()) { "Box64 path must not be blank" }
        return nativeInitializeBox64(libPath)
    }

    fun initializeUSB(): Boolean {
        requireAvailable()
        return nativeInitializeUSB()
    }

    fun attachUSBDevice(vendorId: Int, productId: Int, fd: Int): Boolean {
        requireAvailable()
        require(vendorId in 0..0xffff) { "Invalid USB vendor ID: $vendorId" }
        require(productId in 0..0xffff) { "Invalid USB product ID: $productId" }
        require(fd >= 0) { "Invalid USB file descriptor: $fd" }
        return nativeAttachUSBDevice(vendorId, productId, fd)
    }

    fun detachUSBDevice(deviceId: Int): Boolean {
        requireAvailable()
        require(deviceId > 0) { "Invalid USB device ID: $deviceId" }
        return nativeDetachUSBDevice(deviceId)
    }

    fun setBox64Config(preset: String): Boolean {
        requireAvailable()
        require(preset.lowercase() in setOf("performance", "balanced", "stability")) {
            "Unknown Box64 preset: $preset"
        }
        return nativeSetBox64Config(preset)
    }

    fun optimizeMemory(): Boolean {
        requireAvailable()
        return nativeOptimizeMemory()
    }

    fun cleanup() {
        requireAvailable()
        nativeCleanup()
    }

    /**
     * Initialize all native components. This does not install runtime assets;
     * the caller must provide a real Wine/Box64 runtime first.
     */
    fun initialize(
        winePrefix: String,
        wineArch: String,
        box64LibPath: String
    ): Boolean {
        Timber.d("Initializing native bridge")

        if (!initializeWine(winePrefix, wineArch)) {
            Timber.e("Failed to initialize Wine")
            return false
        }

        if (!initializeBox64(box64LibPath)) {
            Timber.e("Failed to initialize Box64")
            return false
        }

        if (!initializeUSB()) {
            Timber.e("Failed to initialize USB")
            return false
        }

        Timber.d("Native bridge initialized successfully")
        return true
    }

    fun executeApp(
        exePath: String,
        args: String = "",
        workingDir: String = ""
    ): Int {
        Timber.d("Executing app: $exePath")
        return executeWineApp(exePath, args, workingDir)
    }

    fun attachDevice(vendorId: Int, productId: Int, fd: Int): Boolean {
        Timber.d("Attaching USB device: VID=$vendorId, PID=$productId")
        return attachUSBDevice(vendorId, productId, fd)
    }

    fun detachDevice(deviceId: Int): Boolean {
        Timber.d("Detaching USB device: $deviceId")
        return detachUSBDevice(deviceId)
    }

    fun cleanupResources() {
        Timber.d("Cleaning up native resources")
        cleanup()
    }
}
