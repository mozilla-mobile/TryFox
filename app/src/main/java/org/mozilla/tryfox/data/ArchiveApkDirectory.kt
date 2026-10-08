package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.MozillaArchiveApk

/**
 * One APK build directory on archive.mozilla.org. Nightly and release listings share the naming
 * scheme `[<timestamp>-]<app>-<version>-android[-<abi>]/`, where only nightlies have a timestamp
 * and a directory without an ABI suffix holds the universal build.
 *
 * The APK file name is derived from the directory name and may not actually be published (e.g.
 * some universal directories only hold an `.aab`).
 */
data class ArchiveApkDirectory(
    val directory: String,
    val timestamp: String?,
    val appName: String,
    val version: String,
    val abi: String,
) {
    val fileName: String get() = "$appName-$version.multi.android-$abi.apk"

    /**
     * @param listingUrl The URL of the listing this directory was found in.
     * @param appName The app the APK is shown under, e.g. `fenix-release` for a `fenix` directory.
     * @param displayVersion The version shown to the user, e.g. `157.0-RC2` for a candidate build.
     * @param buildKey Isolates the download cache per build; nightlies use their timestamp.
     */
    fun toApk(
        listingUrl: String,
        appName: String = this.appName,
        displayVersion: String = version,
        buildKey: String? = timestamp,
    ) = MozillaArchiveApk(
        originalString = directory,
        rawDateString = buildKey,
        appName = appName,
        version = displayVersion,
        abiName = abi,
        fullUrl = "$listingUrl$directory$fileName",
        fileName = fileName,
    )
}
