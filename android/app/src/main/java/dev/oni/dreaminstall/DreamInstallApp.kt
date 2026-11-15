package dev.oni.dreaminstall

import android.app.Application

class DreamInstallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Language.applyOnStart(this)
    }
}
