package org.mozilla.fenixinstaller.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppState(
    val name: String,
    val packageName: String,
    val version: String?,
    val installDateMillis: Long?
) {
    val isInstalled: Boolean
        get() = installDateMillis != null

    val formattedInstallDate: String?
        get() = installDateMillis?.let {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(it))
        }
}
