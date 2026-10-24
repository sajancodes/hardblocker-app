package dev.sajancodes.hardblock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HardBlockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        dev.sajancodes.hardblock.scheduler.ProtectionWorker.schedule(this)
    }
}
