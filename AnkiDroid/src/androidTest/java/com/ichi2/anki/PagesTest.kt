/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("UnusedReceiverParameter")

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.CardInfoDestination.EntryPoint
import com.ichi2.anki.common.destinations.StatisticsDestination
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.anki.pages.PageFragment
import com.ichi2.anki.pages.toIntent
import com.ichi2.anki.tests.InstrumentedTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assume.assumeThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@NeedsTest("extend this for all activities - Issue 15009")
class PagesTest : InstrumentedTest() {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    var intentBuilder: (PagesTest.(Context) -> Intent)? = null

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var name: String? = null

    var card: Card? = null

    @Test
    fun activityOpens() {
        val intent = intentBuilder!!.invoke(this, testContext)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { activity ->
            // this can fail on a real device if the screen is off
            assertThat("state is RESUMED", activity.state == Lifecycle.State.RESUMED)
        }
        card?.let {
            col.backend.removeNotes(noteIds = listOf(it.nid), cardIds = listOf(it.id))
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{1}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<out Any>> {
            /** See [PageFragment] */
            val intents =
                listOf<Pair<PagesTest.(Context) -> Intent, String>>(
                    Pair(PagesTest::getStatistics, "Statistics"),
                    Pair(PagesTest::getCardInfo, "CardInfo"),
                    Pair(PagesTest::getCongratsPage, "CongratsPage"),
                    Pair(PagesTest::getDeckOptions, "DeckOptions"),
                    // the following need a file path
                    Pair(PagesTest::needsPath, "AnkiPackageImporterFragment"),
                    Pair(PagesTest::needsPath, "CsvImporter"),
                    Pair(PagesTest::needsPath, "ImageOcclusion"),
                )

            return intents.map { arrayOf(it.first, it.second) }
        }
    }
}

fun PagesTest.getStatistics(context: Context): Intent = StatisticsDestination.toIntent(context)

fun PagesTest.getCardInfo(context: Context): Intent =
    addNoteUsingBasicNoteType().firstCard(col).let { card ->
        this.card = card
        CardInfoDestination(card.id, EntryPoint.CURRENT_CARD_STUDY).toIntent(context)
    }

fun PagesTest.getCongratsPage(context: Context): Intent =
    addNoteUsingBasicNoteType().firstCard(col).let { card ->
        this.card = card
        CardInfoDestination(card.id, EntryPoint.CURRENT_CARD_STUDY).toIntent(context)
    }

fun PagesTest.getDeckOptions(context: Context): Intent =
    DeckOptions.getIntent(
        context,
        col.decks
            .allNamesAndIds()
            .first()
            .id,
    )

fun PagesTest.needsPath(
    @Suppress("UNUSED_PARAMETER") context: Context,
): Intent {
    assumeThat("not implemented: path needed", false, equalTo(true))
    TODO()
}
