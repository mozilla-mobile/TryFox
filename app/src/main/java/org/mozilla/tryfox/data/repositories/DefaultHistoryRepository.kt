package org.mozilla.tryfox.data.repositories

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry

class DefaultHistoryRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HistoryRepository {

    private val dbHelper = HistoryDatabaseHelper(context.applicationContext)
    private val _historyEntries = MutableStateFlow<List<TreeherderInstallHistoryEntry>>(emptyList())
    override val historyEntries: StateFlow<List<TreeherderInstallHistoryEntry>> = _historyEntries.asStateFlow()

    override suspend fun refresh() {
        val entries = loadEntries()
        logcat(LogPriority.DEBUG, TAG) {
            "refresh loaded count=${entries.size}, keys=${entries.joinToString { it.uniqueKey }}"
        }
        _historyEntries.value = entries
    }

    override suspend fun recordInstallerLaunch(entry: TreeherderInstallHistoryEntry) {
        logcat(LogPriority.DEBUG, TAG) {
            "recordInstallerLaunch uniqueKey=${entry.uniqueKey}, taskId=${entry.taskId}, " +
                "artifactFileName=${entry.artifactFileName}, jobSymbol=${entry.jobSymbol}, " +
                "cacheRelativePath=${entry.cacheRelativePath}"
        }
        withContext(ioDispatcher) {
            dbHelper.writableDatabase.insertWithOnConflict(
                TABLE_HISTORY,
                null,
                entry.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        refresh()
    }

    override suspend fun delete(uniqueKey: String) {
        logcat(LogPriority.DEBUG, TAG) { "delete uniqueKey=$uniqueKey" }
        withContext(ioDispatcher) {
            dbHelper.writableDatabase.delete(
                TABLE_HISTORY,
                "$COLUMN_ID = ?",
                arrayOf(uniqueKey),
            )
        }
        refresh()
    }

    private suspend fun loadEntries(): List<TreeherderInstallHistoryEntry> = withContext(ioDispatcher) {
        dbHelper.readableDatabase.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_LAST_INSTALLER_LAUNCH_TIMESTAMP DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toHistoryEntry())
                }
            }
        }
    }

    private fun TreeherderInstallHistoryEntry.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_ID, uniqueKey)
            put(COLUMN_PROJECT, project)
            put(COLUMN_REVISION, revision)
            put(COLUMN_COMMIT_MESSAGE, commitMessage)
            put(COLUMN_AUTHOR, author)
            put(COLUMN_PUSH_TIMESTAMP, pushTimestamp)
            put(COLUMN_APP_NAME, appName)
            put(COLUMN_JOB_NAME, jobName)
            put(COLUMN_JOB_SYMBOL, jobSymbol)
            put(COLUMN_TASK_ID, taskId)
            put(COLUMN_ARTIFACT_NAME, artifactName)
            put(COLUMN_ARTIFACT_FILE_NAME, artifactFileName)
            put(COLUMN_DOWNLOAD_URL, downloadUrl)
            put(COLUMN_ABI_NAME, abiName)
            put(COLUMN_ABI_SUPPORTED, if (abiSupported) 1 else 0)
            put(COLUMN_EXPIRES, expires)
            put(COLUMN_CACHE_RELATIVE_PATH, cacheRelativePath)
            put(COLUMN_LAST_INSTALLER_LAUNCH_TIMESTAMP, lastInstallerLaunchTimestamp)
        }

    private fun Cursor.toHistoryEntry(): TreeherderInstallHistoryEntry =
        TreeherderInstallHistoryEntry(
            project = getStringValue(COLUMN_PROJECT),
            revision = getStringValue(COLUMN_REVISION),
            commitMessage = getStringValue(COLUMN_COMMIT_MESSAGE),
            author = getNullableStringValue(COLUMN_AUTHOR),
            pushTimestamp = getLongValue(COLUMN_PUSH_TIMESTAMP),
            appName = getStringValue(COLUMN_APP_NAME),
            jobName = getStringValue(COLUMN_JOB_NAME),
            jobSymbol = getStringValue(COLUMN_JOB_SYMBOL),
            taskId = getStringValue(COLUMN_TASK_ID),
            artifactName = getStringValue(COLUMN_ARTIFACT_NAME),
            artifactFileName = getStringValue(COLUMN_ARTIFACT_FILE_NAME),
            downloadUrl = getStringValue(COLUMN_DOWNLOAD_URL),
            abiName = getNullableStringValue(COLUMN_ABI_NAME),
            abiSupported = getIntValue(COLUMN_ABI_SUPPORTED) == 1,
            expires = getStringValue(COLUMN_EXPIRES),
            cacheRelativePath = getStringValue(COLUMN_CACHE_RELATIVE_PATH),
            lastInstallerLaunchTimestamp = getLongValue(COLUMN_LAST_INSTALLER_LAUNCH_TIMESTAMP),
        )

    private fun Cursor.getStringValue(columnName: String): String =
        getString(getColumnIndexOrThrow(columnName))

    private fun Cursor.getNullableStringValue(columnName: String): String? =
        if (isNull(getColumnIndexOrThrow(columnName))) {
            null
        } else {
            getString(getColumnIndexOrThrow(columnName))
        }

    private fun Cursor.getLongValue(columnName: String): Long =
        getLong(getColumnIndexOrThrow(columnName))

    private fun Cursor.getIntValue(columnName: String): Int =
        getInt(getColumnIndexOrThrow(columnName))

    private class HistoryDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_HISTORY (
                    $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_PROJECT TEXT NOT NULL,
                    $COLUMN_REVISION TEXT NOT NULL,
                    $COLUMN_COMMIT_MESSAGE TEXT NOT NULL,
                    $COLUMN_AUTHOR TEXT,
                    $COLUMN_PUSH_TIMESTAMP INTEGER NOT NULL,
                    $COLUMN_APP_NAME TEXT NOT NULL,
                    $COLUMN_JOB_NAME TEXT NOT NULL,
                    $COLUMN_JOB_SYMBOL TEXT NOT NULL,
                    $COLUMN_TASK_ID TEXT NOT NULL,
                    $COLUMN_ARTIFACT_NAME TEXT NOT NULL,
                    $COLUMN_ARTIFACT_FILE_NAME TEXT NOT NULL,
                    $COLUMN_DOWNLOAD_URL TEXT NOT NULL,
                    $COLUMN_ABI_NAME TEXT,
                    $COLUMN_ABI_SUPPORTED INTEGER NOT NULL,
                    $COLUMN_EXPIRES TEXT NOT NULL,
                    $COLUMN_CACHE_RELATIVE_PATH TEXT NOT NULL,
                    $COLUMN_LAST_INSTALLER_LAUNCH_TIMESTAMP INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
            onCreate(db)
        }
    }

    private companion object {
        const val DATABASE_NAME = "tryfox_history.db"
        const val DATABASE_VERSION = 1

        const val TABLE_HISTORY = "treeherder_install_history"
        const val COLUMN_ID = "id"
        const val COLUMN_PROJECT = "project"
        const val COLUMN_REVISION = "revision"
        const val COLUMN_COMMIT_MESSAGE = "commit_message"
        const val COLUMN_AUTHOR = "author"
        const val COLUMN_PUSH_TIMESTAMP = "push_timestamp"
        const val COLUMN_APP_NAME = "app_name"
        const val COLUMN_JOB_NAME = "job_name"
        const val COLUMN_JOB_SYMBOL = "job_symbol"
        const val COLUMN_TASK_ID = "task_id"
        const val COLUMN_ARTIFACT_NAME = "artifact_name"
        const val COLUMN_ARTIFACT_FILE_NAME = "artifact_file_name"
        const val COLUMN_DOWNLOAD_URL = "download_url"
        const val COLUMN_ABI_NAME = "abi_name"
        const val COLUMN_ABI_SUPPORTED = "abi_supported"
        const val COLUMN_EXPIRES = "expires"
        const val COLUMN_CACHE_RELATIVE_PATH = "cache_relative_path"
        const val COLUMN_LAST_INSTALLER_LAUNCH_TIMESTAMP = "last_installer_launch_timestamp"
        const val TAG = "DefaultHistoryRepository"
    }
}
