// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.content.ContentValues
import android.os.Process
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Utils
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.grantPermissions
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Run on a parallel build to check an authority that differs from the API library's default:
 * `TEST_RELEASE_BUILD=true ./gradlew testFullReleaseUnitTest -PcustomSuffix=a --tests '*CardContentProviderTest'`
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class CardContentProviderTest : RobolectricTest() {
    private lateinit var provider: CardContentProvider
    private val authority = "${BuildConfig.APPLICATION_ID}.flashcards"

    @Before
    fun setUpProvider() {
        CollectionManager.setColForTests(col)
        grantPermissions("${BuildConfig.APPLICATION_ID}.permission.READ_WRITE_DATABASE")
        provider = Robolectric.buildContentProvider(CardContentProvider::class.java).create(authority).get()
        // Release providers require a call from another process, even when the permission is granted.
        ShadowBinder.setCallingPid(Process.myPid() + 1)
    }

    @Test
    fun `inserted note can be queried through the returned URI`() {
        val values =
            ContentValues().apply {
                put(FlashCardsContract.Note.MID, col.notetypes.basic.id)
                put(FlashCardsContract.Note.FLDS, Utils.joinFields(arrayOf("front", "back")))
            }
        assertInsertReturnsProviderUri("notes", values)
    }

    @Test
    fun `inserted model can be queried through the returned URI`() {
        val values =
            ContentValues().apply {
                put(FlashCardsContract.Model.NAME, "Parallel model")
                put(FlashCardsContract.Model.FIELD_NAMES, Utils.joinFields(arrayOf("Front", "Back")))
                put(FlashCardsContract.Model.NUM_CARDS, 1)
            }
        assertInsertReturnsProviderUri("models", values)
    }

    @Test
    fun `inserted deck can be queried through the returned URI`() {
        val values = ContentValues().apply { put(FlashCardsContract.Deck.DECK_NAME, "Parallel deck") }
        assertInsertReturnsProviderUri("decks", values)
    }

    private fun assertInsertReturnsProviderUri(
        path: String,
        values: ContentValues,
    ) {
        val requestUri = "content://$authority/$path".toUri()
        val insertedUri = assertNotNull(provider.insert(requestUri, values))
        assertEquals(requestUri.authority, insertedUri.authority)
        assertNotNull(provider.query(insertedUri, null, null, null, null)).use { cursor ->
            assertEquals(1, cursor.count)
        }
    }
}
