package org.mozilla.tryfox.lan

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DefaultLanMessageHistoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "tryfox_lan_history_${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun upgradeFromVersionOnePreservesRowsAndAddsNewColumns() = runBlocking {
        createVersionOneDatabase(
            LanReceivedMessage(
                receivedAt = 1_760_000_000_000L,
                accepted = true,
                messageId = "message-1",
                extensionId = "extension-id",
                sourceUrl = "https://treeherder.mozilla.org/jobs?repo=try&revision=abc123",
                tryfoxDeepLink = "tryfox://jobs?repo=try&revision=abc123",
                repo = "try",
                revision = "abc123",
                author = "dev@mozilla.com",
                bodyHash = "body-hash",
            ),
        )
        val repository = repository()

        repository.refresh()

        val storedMessage = repository.history.value.single()
        assertEquals("message-1", storedMessage.messageId)
        assertEquals("abc123", storedMessage.revision)
        assertEquals("dev@mozilla.com", storedMessage.author)
        assertNull(storedMessage.title)
        assertNull(storedMessage.pushComment)
        assertNull(storedMessage.pushTimestamp)
    }

    @Test
    fun replaceAllInsertsBatchAndDeleteRemovesSelectedMessage() = runBlocking {
        val repository = repository()
        val first = LanReceivedMessage(
            receivedAt = 1_760_000_000_000L,
            accepted = true,
            messageId = "message-1",
            revision = "abc123",
            pushComment = "Bug 123 - First push",
        )
        val second = LanReceivedMessage(
            receivedAt = 1_760_000_001_000L,
            accepted = true,
            messageId = "message-2",
            revision = "def456",
            pushComment = "Bug 456 - Second push",
        )

        val storedMessages = repository.replaceAll(listOf(first, second))

        assertEquals(2, storedMessages.size)
        assertTrue(storedMessages.all { it.id > 0L })
        assertEquals(listOf("message-2", "message-1"), repository.history.value.map { it.messageId })

        repository.delete(storedMessages.first().id)

        assertEquals(listOf("message-2"), repository.history.value.map { it.messageId })
    }

    private fun repository() =
        DefaultLanMessageHistoryRepository(
            context = context,
            ioDispatcher = Dispatchers.Unconfined,
            databaseName = databaseName,
        )

    private fun createVersionOneDatabase(message: LanReceivedMessage) {
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE received_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    received_at INTEGER NOT NULL,
                    accepted INTEGER NOT NULL,
                    error TEXT,
                    message_id TEXT,
                    extension_id TEXT,
                    source_url TEXT,
                    deep_link TEXT,
                    repo TEXT,
                    revision TEXT,
                    author TEXT,
                    body_hash TEXT
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO received_messages (
                    received_at,
                    accepted,
                    error,
                    message_id,
                    extension_id,
                    source_url,
                    deep_link,
                    repo,
                    revision,
                    author,
                    body_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    message.receivedAt,
                    if (message.accepted) 1 else 0,
                    message.error,
                    message.messageId,
                    message.extensionId,
                    message.sourceUrl,
                    message.tryfoxDeepLink,
                    message.repo,
                    message.revision,
                    message.author,
                    message.bodyHash,
                ),
            )
            database.version = 1
        }
    }
}
