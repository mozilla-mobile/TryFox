package org.mozilla.tryfox.lan

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

class DefaultLanMessageHistoryRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LanMessageHistoryRepository {
    private val dbHelper = MessageHistoryDatabaseHelper(context.applicationContext)
    private val _history = MutableStateFlow<List<LanReceivedMessage>>(emptyList())
    override val history: StateFlow<List<LanReceivedMessage>> = _history.asStateFlow()

    override suspend fun refresh() {
        _history.value = loadEntries()
    }

    override suspend fun record(message: LanReceivedMessage): LanReceivedMessage = withContext(ioDispatcher) {
        val id = dbHelper.writableDatabase.insert(TABLE_HISTORY, null, message.toContentValues())
        val storedMessage = message.copy(id = id)
        _history.value = loadEntries()
        storedMessage
    }

    private suspend fun loadEntries(): List<LanReceivedMessage> = withContext(ioDispatcher) {
        dbHelper.readableDatabase.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_RECEIVED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLanReceivedMessage())
                }
            }
        }
    }

    private fun LanReceivedMessage.toContentValues(): ContentValues =
        ContentValues().apply {
            put(COLUMN_RECEIVED_AT, receivedAt)
            put(COLUMN_ACCEPTED, if (accepted) 1 else 0)
            put(COLUMN_ERROR, error)
            put(COLUMN_MESSAGE_ID, messageId)
            put(COLUMN_EXTENSION_ID, extensionId)
            put(COLUMN_SOURCE_URL, sourceUrl)
            put(COLUMN_DEEP_LINK, tryfoxDeepLink)
            put(COLUMN_REPO, repo)
            put(COLUMN_REVISION, revision)
            put(COLUMN_AUTHOR, author)
            put(COLUMN_BODY_HASH, bodyHash)
        }

    private fun Cursor.toLanReceivedMessage(): LanReceivedMessage =
        LanReceivedMessage(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            receivedAt = getLong(getColumnIndexOrThrow(COLUMN_RECEIVED_AT)),
            accepted = getInt(getColumnIndexOrThrow(COLUMN_ACCEPTED)) == 1,
            error = getNullableString(COLUMN_ERROR),
            messageId = getNullableString(COLUMN_MESSAGE_ID),
            extensionId = getNullableString(COLUMN_EXTENSION_ID),
            sourceUrl = getNullableString(COLUMN_SOURCE_URL),
            tryfoxDeepLink = getNullableString(COLUMN_DEEP_LINK),
            repo = getNullableString(COLUMN_REPO),
            revision = getNullableString(COLUMN_REVISION),
            author = getNullableString(COLUMN_AUTHOR),
            bodyHash = getNullableString(COLUMN_BODY_HASH),
        )

    private fun Cursor.getNullableString(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private class MessageHistoryDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_HISTORY (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_RECEIVED_AT INTEGER NOT NULL,
                    $COLUMN_ACCEPTED INTEGER NOT NULL,
                    $COLUMN_ERROR TEXT,
                    $COLUMN_MESSAGE_ID TEXT,
                    $COLUMN_EXTENSION_ID TEXT,
                    $COLUMN_SOURCE_URL TEXT,
                    $COLUMN_DEEP_LINK TEXT,
                    $COLUMN_REPO TEXT,
                    $COLUMN_REVISION TEXT,
                    $COLUMN_AUTHOR TEXT,
                    $COLUMN_BODY_HASH TEXT
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
        const val DATABASE_NAME = "tryfox_lan_history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_HISTORY = "received_messages"
        const val COLUMN_ID = "id"
        const val COLUMN_RECEIVED_AT = "received_at"
        const val COLUMN_ACCEPTED = "accepted"
        const val COLUMN_ERROR = "error"
        const val COLUMN_MESSAGE_ID = "message_id"
        const val COLUMN_EXTENSION_ID = "extension_id"
        const val COLUMN_SOURCE_URL = "source_url"
        const val COLUMN_DEEP_LINK = "deep_link"
        const val COLUMN_REPO = "repo"
        const val COLUMN_REVISION = "revision"
        const val COLUMN_AUTHOR = "author"
        const val COLUMN_BODY_HASH = "body_hash"
    }
}
