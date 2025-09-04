package org.mozilla.fenixinstaller.model

data class ParsedNightlyApk(
    val originalString: String,
    val rawDateString: String, // Format: "yyyy-MM-dd-HH-mm-ss"
    val appName: String,
    val version: String,
    val abiName: String,
    val fullUrl: String,
    val fileName: String
)
