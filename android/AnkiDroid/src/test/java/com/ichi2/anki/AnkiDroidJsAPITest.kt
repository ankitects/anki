/*
 * Copyright (c) 2020 Mani infinyte01@gmail.com
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPI.Companion.SUCCESS_KEY
import com.ichi2.anki.AnkiDroidJsAPI.Companion.VALUE_KEY
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.testutils.ext.BASIC_NOTE_TYPE_NAME
import com.ichi2.anki.libanki.testutils.ext.setFlag
import net.ankiweb.rsdroid.withoutUnicodeIsolation
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AnkiDroidJsAPITest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun ankiGetNextTimeTest() =
        runTest {
            val didA = addDeck("Test", setAsSelected = true)
            val basic = col.notetypes.byName(BASIC_NOTE_TYPE_NAME)
            basic!!.did = didA
            addBasicNote("foo", "bar")

            val reviewer: Reviewer = startReviewer()
            val jsapi = reviewer.jsApi

            reviewer.displayCardAnswer()

            advanceRobolectricLooper()

            assertThat(
                getDataFromRequest("nextTime1", jsapi).withoutUnicodeIsolation(),
                equalTo(formatApiResult("<1m")),
            )
            assertThat(
                getDataFromRequest("nextTime2", jsapi).withoutUnicodeIsolation(),
                equalTo(formatApiResult("<6m")),
            )
            assertThat(
                getDataFromRequest("nextTime3", jsapi).withoutUnicodeIsolation(),
                equalTo(formatApiResult("<10m")),
            )
            assertThat(
                getDataFromRequest("nextTime4", jsapi).withoutUnicodeIsolation(),
                equalTo(formatApiResult("4d")),
            )
        }

    @Test
    fun ankiTestCurrentCard() =
        runTest {
            val didA = addDeck("Test", setAsSelected = true)
            val basic = col.notetypes.byName(BASIC_NOTE_TYPE_NAME)
            basic!!.did = didA
            addBasicNote("foo", "bar")

            val reviewer: Reviewer = startReviewer()
            val jsapi = reviewer.jsApi
            reviewer.displayCardAnswer()

            advanceRobolectricLooper()

            val currentCard = reviewer.currentCard!!

            // Card Did
            assertThat(
                getDataFromRequest("cardDid", jsapi),
                equalTo(formatApiResult(currentCard.did)),
            )
            // Card Id
            assertThat(
                getDataFromRequest("cardId", jsapi),
                equalTo(formatApiResult(currentCard.id)),
            )
            // Card Nid
            assertThat(
                getDataFromRequest("cardNid", jsapi),
                equalTo(formatApiResult(currentCard.nid)),
            )
            // Card ODid
            assertThat(
                getDataFromRequest("cardODid", jsapi),
                equalTo(formatApiResult(currentCard.oDid)),
            )
            // Card Type
            assertThat(
                getDataFromRequest("cardType", jsapi),
                equalTo(formatApiResult(currentCard.type.code)),
            )
            // Card ODue
            assertThat(
                getDataFromRequest("cardODue", jsapi),
                equalTo(formatApiResult(currentCard.oDue)),
            )
            // Card Due
            assertThat(
                getDataFromRequest("cardDue", jsapi),
                equalTo(formatApiResult(currentCard.due)),
            )
            // Card Factor
            assertThat(
                getDataFromRequest("cardFactor", jsapi),
                equalTo(formatApiResult(currentCard.factor)),
            )
            // Card Lapses
            assertThat(
                getDataFromRequest("cardLapses", jsapi),
                equalTo(formatApiResult(currentCard.lapses)),
            )
            // Card Ivl
            assertThat(
                getDataFromRequest("cardInterval", jsapi),
                equalTo(formatApiResult(currentCard.ivl)),
            )
            // Card mod
            assertThat(
                getDataFromRequest("cardMod", jsapi),
                equalTo(formatApiResult(currentCard.mod)),
            )
            // Card Queue
            assertThat(
                getDataFromRequest("cardQueue", jsapi),
                equalTo(formatApiResult(currentCard.queue.code)),
            )
            // Card Reps
            assertThat(
                getDataFromRequest("cardReps", jsapi),
                equalTo(formatApiResult(currentCard.reps)),
            )
            // Card left
            assertThat(
                getDataFromRequest("cardLeft", jsapi),
                equalTo(formatApiResult(currentCard.left)),
            )

            // Card Flag
            assertThat(
                getDataFromRequest("cardFlag", jsapi),
                equalTo(formatApiResult(0)),
            )
            reviewer.currentCard!!.setFlag(Flag.RED.ordinal)
            assertThat(
                getDataFromRequest("cardFlag", jsapi),
                equalTo(formatApiResult(1)),
            )

            // Card Mark
            assertThat(
                getDataFromRequest("cardMark", jsapi),
                equalTo(formatApiResult(false)),
            )
            reviewer.currentCard!!.note().addTag("marked")
            assertThat(
                getDataFromRequest("cardMark", jsapi),
                equalTo(formatApiResult(true)),
            )
        }

    @Test
    fun ankiJsUiTest() =
        runTest {
            val didA = addDeck("Test", setAsSelected = true)
            val basic = col.notetypes.byName(BASIC_NOTE_TYPE_NAME)
            basic!!.did = didA
            addBasicNote("foo", "bar")

            val reviewer: Reviewer = startReviewer()
            val jsapi = reviewer.jsApi

            advanceRobolectricLooper()

            // Displaying question
            assertThat(
                getDataFromRequest("isDisplayingAnswer", jsapi),
                equalTo(formatApiResult(reviewer.isDisplayingAnswer)),
            )
            reviewer.displayCardAnswer()
            assertThat(
                getDataFromRequest("isDisplayingAnswer", jsapi),
                equalTo(formatApiResult(reviewer.isDisplayingAnswer)),
            )

            // Full Screen
            assertThat(
                getDataFromRequest("isInFullscreen", jsapi),
                equalTo(formatApiResult(reviewer.isFullscreen)),
            )
            // Top bar
            assertThat(
                getDataFromRequest("isTopbarShown", jsapi),
                equalTo(formatApiResult(reviewer.prefShowTopbar)),
            )
            // Night Mode
            assertThat(
                getDataFromRequest("isInNightMode", jsapi),
                equalTo(formatApiResult(reviewer.isInNightMode)),
            )
        }

    @Test
    fun ankiMarkAndFlagCardTest() =
        runTest {
            // js api test for marking and flagging card
            val didA = addDeck("Test", setAsSelected = true)
            val basic = col.notetypes.byName(BASIC_NOTE_TYPE_NAME)
            basic!!.did = didA
            addBasicNote("foo", "bar")

            val reviewer: Reviewer = startReviewer()
            val jsapi = reviewer.jsApi

            advanceRobolectricLooper()

            // ---------------
            // Card mark test
            // ---------------
            // Before marking card
            assertThat(
                getDataFromRequest("cardMark", jsapi),
                equalTo(formatApiResult(false)),
            )

            // Mark card
            assertThat(
                getDataFromRequest("markCard", jsapi, "true"),
                equalTo(formatApiResult(true)),
            )

            // After marking card
            assertThat(
                getDataFromRequest("cardMark", jsapi),
                equalTo(formatApiResult(true)),
            )

            // ---------------
            // Card flag test
            // ---------------
            // before toggling flag
            assertThat(
                getDataFromRequest("cardFlag", jsapi),
                equalTo(formatApiResult(0)),
            )

            // call javascript function to toggle flag
            assertThat(
                getDataFromRequest("toggleFlag", jsapi, "red"),
                equalTo(formatApiResult(true)),
            )

            // after toggling flag
            assertThat(
                getDataFromRequest("cardFlag", jsapi),
                equalTo(formatApiResult(1)),
            )
        }

    @Ignore("the test need to be updated")
    fun ankiBurySuspendTest() =
        runTest {
            // js api test for bury and suspend notes and cards
            // add five notes, four will be buried and suspended
            // count number of notes, if buried or suspended then
            // in scheduling the count will be less than previous scheduling
            val didA = addDeck("Test", setAsSelected = true)
            val basic = col.notetypes.byName(BASIC_NOTE_TYPE_NAME)
            basic!!.did = didA
            addBasicNote("foo", "bar")
            addBasicNote("baz", "bak")
            addBasicNote("Anki", "Droid")
            addBasicNote("Test Card", "Bury and Suspend Card")
            addBasicNote("Test Note", "Bury and Suspend Note")

            val reviewer: Reviewer = startReviewer()
            val jsapi = reviewer.jsApi

            // ----------
            // Bury Card
            // ----------
            // call script to bury current card
            assertThat(
                getDataFromRequest("buryCard", jsapi),
                equalTo(formatApiResult(true)),
            )

            // count number of notes
            val sched = reviewer.getColUnsafe
            assertThat(sched.cardCount(), equalTo(4))

            // ----------
            // Bury Note
            // ----------
            // call script to bury current note
            assertThat(
                getDataFromRequest("buryNote", jsapi),
                equalTo(formatApiResult(true)),
            )

            // count number of notes
            assertThat(sched.cardCount(), equalTo(3))

            // -------------
            // Suspend Card
            // -------------
            // call script to suspend current card
            assertThat(
                getDataFromRequest("suspendCard", jsapi),
                equalTo(formatApiResult(true)),
            )

            // count number of notes
            assertThat(sched.cardCount(), equalTo(2))

            // -------------
            // Suspend Note
            // -------------
            // call script to suspend current note
            assertThat(
                getDataFromRequest("suspendNote", jsapi),
                equalTo(formatApiResult(true)),
            )

            // count number of notes
            assertThat(sched.cardCount(), equalTo(1))
        }

    private fun startReviewer(): Reviewer = ReviewerTest.startReviewer(this)

    @Test
    fun ankiSetCardDueTest() =
        runTest {
            TimeManager.reset()
            val didA = addDeck("Test", setAsSelected = true)
            val basic = col.notetypes.byName(BASIC_NOTE_TYPE_NAME)
            basic!!.did = didA
            addBasicNote("foo", "bar")
            addBasicNote("baz", "bak")

            val reviewer: Reviewer = startReviewer()
            advanceRobolectricLooper()

            val jsapi = reviewer.jsApi
            // get card id for testing due
            val cardIdRes = getDataFromRequest("cardId", jsapi)
            val jsonObject = JSONObject(cardIdRes)
            val cardId = jsonObject.get(VALUE_KEY).toString().toLong()

            // test that card rescheduled for 15 days interval and returned true
            assertThat(getDataFromRequest("setCardDue", jsapi, "15"), equalTo(formatApiResult(true)))
            advanceRobolectricLooper()

            // verify that it did get rescheduled
            // --------------------------------
            val cardToBeReschedule = col.getCard(cardId)
            assertEquals(15 + col.sched.today, cardToBeReschedule.due, "Card is rescheduled")
        }

    @Test
    fun ankiResetProgressTest() =
        runTest {
            val n = addBasicNote("Front", "Back")
            val c = n.firstCard()

            // Make card review with 28L due and 280% ease
            c.type = CardType.Rev
            c.due = 28
            c.factor = 2800
            c.ivl = 8

            // before reset
            assertEquals(28, c.due, "Card due before reset")
            assertEquals(8, c.ivl, "Card interval before reset")
            assertEquals(2800, c.factor, "Card ease before reset")
            assertEquals(CardType.Rev, c.type, "Card type before reset")

            val reviewer: Reviewer = startReviewer()
            advanceRobolectricLooper()

            val jsapi = reviewer.jsApi

            // test that card reset
            assertThat(getDataFromRequest("resetProgress", jsapi), equalTo(formatApiResult(true)))
            advanceRobolectricLooper()

            // verify that card progress reset
            // --------------------------------
            val cardAfterReset = col.getCard(reviewer.currentCard!!.id)
            assertEquals(2, cardAfterReset.due, "Card due after reset")
            assertEquals(0, cardAfterReset.ivl, "Card interval after reset")
            assertEquals(CardType.New, cardAfterReset.type, "Card type after reset")
        }

    @Test
    fun ankiGetNoteTagsTest() =
        runTest {
            val n =
                addBasicNote("Front", "Back").update {
                    tags = mutableListOf("tag1", "tag2", "tag3")
                }

            val reviewer: Reviewer = startReviewer()
            advanceRobolectricLooper()

            val jsapi = reviewer.jsApi

            // test get tags for note
            val expectedTags = n.tags
            val response = getDataFromRequest("getNoteTags", jsapi)
            val jsonResponse = JSONObject(response)
            val actualTags = JSONArray(jsonResponse.getString("value"))

            assertEquals(expectedTags.size, actualTags.length())
            for (i in 0 until actualTags.length()) {
                assertEquals(expectedTags[i], actualTags.getString(i))
            }
        }

    companion object {
        fun jsApiContract(data: String = ""): ByteArray =
            JSONObject()
                .apply {
                    put("version", "0.0.3")
                    put("developer", "test@example.com")
                    put("data", data)
                }.toString()
                .toByteArray()

        private inline fun formatSuccessfulApiResult(block: JSONObject.() -> Unit) =
            JSONObject()
                .apply {
                    put(SUCCESS_KEY, true)
                    block(this)
                }.toString()

        fun formatApiResult(res: Boolean) = formatSuccessfulApiResult { put(VALUE_KEY, res) }

        fun formatApiResult(res: Int) = formatSuccessfulApiResult { put(VALUE_KEY, res) }

        fun formatApiResult(res: Long) = formatSuccessfulApiResult { put(VALUE_KEY, res) }

        fun formatApiResult(res: String) = formatSuccessfulApiResult { put(VALUE_KEY, res) }

        suspend fun getDataFromRequest(
            methodName: String,
            jsAPI: AnkiDroidJsAPI,
            apiData: String = "",
        ): String =
            jsAPI
                .handleJsApiRequest(methodName, jsApiContract(apiData), false)
                .decodeToString()
    }
}
