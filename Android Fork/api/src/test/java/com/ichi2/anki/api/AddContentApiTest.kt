// SPDX-FileCopyrightText: Copyright (c) 2026 ColgateTotal77 <serega2005n@gmail.com>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import com.ichi2.anki.FlashCardsContract
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class FakeCardContentProvider : ContentProvider() {
    companion object {
        const val NOTES_ID_CARDS = 1003

        val CARD_PROJECTION =
            arrayOf(
                FlashCardsContract.Card.NOTE_ID,
                FlashCardsContract.Card.CARD_ORD,
                FlashCardsContract.Card.FLAGS,
            )

        val uriMatcher =
            UriMatcher(UriMatcher.NO_MATCH).apply {
                addURI(FlashCardsContract.AUTHORITY, "notes/#/cards", NOTES_ID_CARDS)
            }
    }

    private data class FakeCard(
        val noteId: Long,
        val ord: Int,
        var flags: Int = Flag.NONE.code,
    )

    private val cards = mutableListOf<FakeCard>()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ) = when (uriMatcher.match(uri)) {
        NOTES_ID_CARDS -> {
            val noteId = uri.pathSegments[1].toLong()
            MatrixCursor(CARD_PROJECTION).apply {
                cards
                    .filter { it.noteId == noteId }
                    .forEach { addRow(listOf(it.noteId, it.ord, it.flags and 7)) }
            }
        }

        else -> throw IllegalArgumentException("uri $uri is not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        var updated = 0
        val valueSet = values!!.valueSet()
        val card = getCardFromUri(uri)
        for ((key) in valueSet) {
            when (key) {
                FlashCardsContract.Card.FLAGS -> {
                    val flags = values.getAsInteger(key)

                    if (flags == null || flags < Flag.MIN_CODE || flags > Flag.MAX_CODE) {
                        throw IllegalArgumentException("Flags value must be in the range from ${Flag.MIN_CODE} to ${Flag.MAX_CODE}")
                    }

                    card.flags = (flags and 7)
                    updated++
                }
                else -> throw IllegalArgumentException("uri $uri is not supported")
            }
        }
        return updated
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    fun addNote(
        noteId: Long,
        cardCount: Int,
    ) {
        repeat(cardCount) { ord -> cards.add(FakeCard(noteId, ord)) }
    }

    private fun getCardFromUri(uri: Uri): FakeCard {
        val noteId = uri.pathSegments[1].toLong()
        val ord = uri.pathSegments[3].toInt()
        return cards.firstOrNull { it.noteId == noteId && it.ord == ord }
            ?: throw IllegalArgumentException("Card with ord $ord does not exist for note $noteId")
    }

    fun getNoteFlags(noteId: Long): List<Int> = cards.filter { it.noteId == noteId }.map { it.flags }
}

/**
 * This module's manifest registers no content provider, so by default Robolectric's
 * [android.content.pm.PackageManager] behaves as if AnkiDroid were not installed.
 *
 * Robolectric only shadows `resolveContentProvider(String, int)`, so the Tiramisu+ overload throws
 * `UnsupportedOperationException`. Still the case on Robolectric master and no upstream issue
 * tracks it, so these pin to the pre-Tiramisu branch. Both branches share the same null handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
internal class AddContentApiTest {
    private val api get() = AddContentApi(RuntimeEnvironment.getApplication())

    @Test
    fun apiHostSpecVersionIsMinusOneWhenAnkiDroidIsNotInstalled() {
        assertEquals(-1, api.apiHostSpecVersion)
    }

    @Test
    fun apiHostSpecVersionIsReadFromProviderMetaData() {
        installAnkiDroid(Bundle().apply { putInt("com.ichi2.anki.provider.spec", 2) })
        assertEquals(2, api.apiHostSpecVersion)
    }

    /** An installed AnkiDroid without the meta-data key predates it, so it's spec 1 */
    @Test
    fun apiHostSpecVersionIsOneWhenProviderHasNoMetaData() {
        installAnkiDroid(metaData = null)
        assertEquals(1, api.apiHostSpecVersion)
    }

    @Test
    fun testSetFlagRaw() {
        val provider = installFakeCardProvider()

        val updated = api.setFlagRaw(NOTE_ID, Flag.MAX_CODE)

        assertThat("should return true if updated", updated, equalTo(true))
        assertThat(
            "should return updated flag codes",
            provider.getNoteFlags(NOTE_ID),
            equalTo(listOf(Flag.MAX_CODE, Flag.MAX_CODE)),
        )

        val tooLargeValue = assertFailsWith<IllegalArgumentException> { api.setFlagRaw(NOTE_ID, Flag.MAX_CODE + 1) }
        assertThat(tooLargeValue.message, equalTo("Flags value must be in the range from ${Flag.MIN_CODE} to ${Flag.MAX_CODE}"))

        val tooSmallValue = assertFailsWith<IllegalArgumentException> { api.setFlagRaw(NOTE_ID, Flag.MIN_CODE - 1) }
        assertThat(tooSmallValue.message, equalTo("Flags value must be in the range from ${Flag.MIN_CODE} to ${Flag.MAX_CODE}"))
    }

    @Test
    fun testSetFlag() {
        val provider = installFakeCardProvider()

        val updated = api.setFlag(NOTE_ID, Flag.RED)

        assertThat("should return true if updated", updated, equalTo(true))
        assertThat(
            "should return updated flag codes",
            provider.getNoteFlags(NOTE_ID),
            equalTo(listOf(Flag.RED.code, Flag.RED.code)),
        )
    }

    private fun installAnkiDroid(metaData: Bundle?) {
        val provider =
            ProviderInfo().apply {
                authority = FlashCardsContract.AUTHORITY
                packageName = "com.ichi2.anki"
                name = "com.ichi2.anki.provider.CardContentProvider"
                this.metaData = metaData
            }
        shadowOf(RuntimeEnvironment.getApplication().packageManager).addOrUpdateProvider(provider)
    }

    private fun installFakeCardProvider(): FakeCardContentProvider =
        Robolectric
            .buildContentProvider(FakeCardContentProvider::class.java)
            .create(FlashCardsContract.AUTHORITY)
            .get()
            .apply { addNote(NOTE_ID, CARD_COUNT) }

    companion object {
        private const val NOTE_ID = 1L
        private const val CARD_COUNT = 2
    }
}
