package com.windroidpro

import android.app.Application
import com.windroidpro.usb.NativeUsbManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WinDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        NativeUsbManager.loadLibraryAsync()
        Timber.d("WinDroid Pro application initialized")
    }
}
