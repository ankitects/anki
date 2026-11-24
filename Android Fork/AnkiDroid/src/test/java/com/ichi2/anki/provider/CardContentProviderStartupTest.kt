// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.grantPermissions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardContentProviderStartupTest : RobolectricTest() {
    private lateinit var provider: CardContentProvider
    private val executor = Executors.newSingleThreadExecutor()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setUpProvider() {
        grantPermissions(FlashCardsContract.READ_WRITE_PERMISSION)
        // Model a provider created before application startup has configured storage.
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        provider = Robolectric.buildContentProvider(CardContentProvider::class.java).create(FlashCardsContract.AUTHORITY).get()
    }

    @After
    fun stopQueryWorker() {
        executor.shutdownNow()
    }

    @Test
    fun `binder query waits until application initialization has configured storage`() {
        val query = queryDeckCountInBackground()
        // Leave the startup completion callback queued while the Binder query waits.
        assertFailsWith<TimeoutException> { query.get(200, TimeUnit.MILLISECONDS) }

        configureStorage()
        assertEquals(1, awaitQueryResult(query))
    }

    @Test
    fun `main thread query does not wait for its own message queue`() {
        configureStorage()
        assertEquals(1, queryDeckCount())
    }

    @Test
    fun `startup completion does not hide an unconfigured collection`() {
        val query = queryDeckCountInBackground()
        val failure = assertFailsWith<IllegalStateException> { awaitQueryResult(query) }
        assertEquals("AnkiDroid storage is not configured", failure.message)
    }

    private fun configureStorage() {
        targetContext.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, File(targetContext.filesDir, "api-startup").path)
        }
    }

    private fun queryDeckCountInBackground(): Future<Int> {
        val started = CountDownLatch(1)
        val query =
            executor.submit<Int> {
                started.countDown()
                queryDeckCount()
            }
        assertTrue(started.await(5, TimeUnit.SECONDS), "Query worker did not start")
        return query
    }

    private fun awaitQueryResult(query: Future<Int>): Int {
        advanceRobolectricLooperUntil { query.isDone }
        return try {
            query.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun queryDeckCount(): Int =
        assertNotNull(provider.query(FlashCardsContract.Deck.CONTENT_ALL_URI, null, null, null, null)).use { it.count }
}
