package org.mozilla.fenixinstaller.ui.models

import org.mozilla.fenixinstaller.data.DownloadState
import java.io.File

data class ApkUiModel(
    val originalString: String,
    val date: String,
    val appName: String,
    val version: String,
    val abi: AbiUiModel,
    val url: String,
    val fileName: String,
    var downloadState: DownloadState = DownloadState.NotDownloaded,
    val uniqueKey: String, // e.g., "appName/date(YYYY-MM-DD)/fileName"
    val apkDir: File,
)
