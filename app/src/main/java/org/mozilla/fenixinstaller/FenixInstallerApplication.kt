package org.mozilla.fenixinstaller

import android.app.Application
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.mozilla.fenixinstaller.di.appModules

class FenixInstallerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FenixInstallerApplication)
            modules(appModules)
        }

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)
    }
}
