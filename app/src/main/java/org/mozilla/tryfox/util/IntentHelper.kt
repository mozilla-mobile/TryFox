package org.mozilla.tryfox.util

import android.content.Context

interface IntentHelper {
    fun launchApp(appName: String)
}

class DefaultIntentHelper(private val applicationContext: Context) : IntentHelper {

    override fun launchApp(appName: String) {
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(appName)
        intent?.let(applicationContext::startActivity)
    }
}
