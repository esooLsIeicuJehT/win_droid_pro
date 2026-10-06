package com.windroidpro.usb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Low-level USB Manager interfacing with native USB code.
 */
object NativeUsbManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isLibraryLoaded = MutableStateFlow(false)
    val isLibraryLoaded: StateFlow<Boolean> = _isLibraryLoaded

    private val libraryLoadResult = CompletableDeferred<Result<Unit>>()

    @Volatile
    private var loadStarted = false

    @Synchronized
    fun loadLibraryAsync() {
        if (loadStarted) return
        loadStarted = true

        scope.launch {
            val result = runCatching {
                System.loadLibrary("windroidpro")
            }

            result.onSuccess {
                _isLibraryLoaded.value = true
                Timber.d("Native library loaded successfully.")
            }.onFailure { error ->
                _isLibraryLoaded.value = false
                Timber.e(error, "Failed to load native library.")
            }

            libraryLoadResult.complete(result)
        }
    }

    external fun nativeOpenDevice(devicePath: String): Int
    external fun nativeCloseDevice(fd: Int)
    external fun nativeReadDevice(fd: Int, buffer: ByteArray, length: Int): Int
    external fun nativeWriteDevice(fd: Int, buffer: ByteArray, length: Int): Int

    private suspend fun awaitLibraryLoad() {
        if (!loadStarted) {
            loadLibraryAsync()
        }

        libraryLoadResult.await().getOrElse { error ->
            throw IllegalStateException("WinDroid native library is unavailable", error)
        }
    }

    suspend fun openDevice(devicePath: String): Int = withContext(Dispatchers.IO) {
        require(devicePath.isNotBlank()) { "USB device path must not be blank" }
        awaitLibraryLoad()
        nativeOpenDevice(devicePath)
    }

    suspend fun closeDevice(fd: Int) = withContext(Dispatchers.IO) {
        awaitLibraryLoad()
        nativeCloseDevice(fd)
    }

    suspend fun readDevice(fd: Int, buffer: ByteArray, length: Int): Int = withContext(Dispatchers.IO) {
        validateBufferLength(buffer, length)
        awaitLibraryLoad()
        nativeReadDevice(fd, buffer, length)
    }

    suspend fun writeDevice(fd: Int, buffer: ByteArray, length: Int): Int = withContext(Dispatchers.IO) {
        validateBufferLength(buffer, length)
        awaitLibraryLoad()
        nativeWriteDevice(fd, buffer, length)
    }

    external fun nativeControlTransfer(
        fd: Int,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        timeout: Int
    ): Int

    external fun nativeBulkTransfer(
        fd: Int,
        endpoint: Int,
        buffer: ByteArray,
        length: Int,
        timeout: Int
    ): Int

    external fun nativeClaimInterface(fd: Int, interfaceNumber: Int): Boolean
    external fun nativeReleaseInterface(fd: Int, interfaceNumber: Int): Boolean

    suspend fun controlTransfer(
        fd: Int,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        timeout: Int
    ): Int = withContext(Dispatchers.IO) {
        require(length >= 0) { "Transfer length must be non-negative" }
        if (buffer == null) {
            require(length == 0) { "A null transfer buffer requires length 0" }
        } else {
            validateBufferLength(buffer, length)
        }
        require(timeout >= 0) { "Transfer timeout must be non-negative" }

        awaitLibraryLoad()
        nativeControlTransfer(fd, requestType, request, value, index, buffer, length, timeout)
    }

    suspend fun bulkTransfer(
        fd: Int,
        endpoint: Int,
        buffer: ByteArray,
        length: Int,
        timeout: Int
    ): Int = withContext(Dispatchers.IO) {
        validateBufferLength(buffer, length)
        require(timeout >= 0) { "Transfer timeout must be non-negative" }

        awaitLibraryLoad()
        nativeBulkTransfer(fd, endpoint, buffer, length, timeout)
    }

    suspend fun claimInterface(fd: Int, interfaceNumber: Int): Boolean = withContext(Dispatchers.IO) {
        require(interfaceNumber >= 0) { "USB interface number must be non-negative" }
        awaitLibraryLoad()
        nativeClaimInterface(fd, interfaceNumber)
    }

    suspend fun releaseInterface(fd: Int, interfaceNumber: Int): Boolean = withContext(Dispatchers.IO) {
        require(interfaceNumber >= 0) { "USB interface number must be non-negative" }
        awaitLibraryLoad()
        nativeReleaseInterface(fd, interfaceNumber)
    }

    private fun validateBufferLength(buffer: ByteArray, length: Int) {
        require(length in 0..buffer.size) {
            "Transfer length $length exceeds buffer size ${buffer.size}"
        }
    }
}
