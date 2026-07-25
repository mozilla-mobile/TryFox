package org.mozilla.tryfox.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppState(
    val name: String,
    val packageName: String,
    val version: String?,
    val installDateMillis: Long?,
    val installingPackageName: String? = null,
    val versionCode: Long? = null,
    val splitNames: List<String> = emptyList(),
) {
    val isInstalled: Boolean
        get() = installDateMillis != null

    /** Whether the app was installed by the Google Play Store. */
    val isFromPlayStore: Boolean
        get() = installingPackageName == PLAY_STORE_PACKAGE

    val formattedInstallDate: String?
        get() = installDateMillis?.let {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(it))
        }

    companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
