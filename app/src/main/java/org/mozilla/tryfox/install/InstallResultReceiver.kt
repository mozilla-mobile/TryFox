package org.mozilla.tryfox.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GlobalContext.get().get<ApkInstallCoordinator>().onInstallResult(intent)
    }
}
