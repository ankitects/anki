// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.core.app.ActivityScenario
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.CardInfoDestination.EntryPoint
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitForPageCondition
import org.json.JSONObject
import org.junit.Test

class CardInfoTest : InstrumentedTest() {
    @Test
    fun rendersReviewedFsrsCard() {
        val fsrsWasEnabled = col.config.get<Boolean>("fsrs") ?: false
        val note = addNoteUsingBasicNoteType()
        try {
            col.config.set("fsrs", true)
            val card = note.firstCard(col)
            card.startTimer()
            col.sched.answerCard(card, Rating.GOOD)

            val intent = CardInfoDestination(card.id, EntryPoint.CURRENT_CARD_STUDY).toIntent(testContext)
            ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
                lateinit var page: CardInfoFragment
                scenario.onActivity { page = it.fragment as CardInfoFragment }
                page.waitForPageCondition(
                    """
                    (() => {
                        const stats = document.querySelector('.stats-table');
                        return stats?.innerText.includes('${card.id}')
                            && stats.innerText.includes(${JSONObject.quote(TR.cardStatsFsrsStability())})
                            && stats.innerText.includes(${JSONObject.quote(TR.cardStatsFsrsDifficulty())})
                            && document.querySelector('path.forgetting-curve-line')?.getAttribute('d')?.length > 0;
                    })();
                    """.trimIndent(),
                    "Card Info did not render the card's FSRS statistics and forgetting curve",
                )
            }
        } finally {
            col.backend.removeNotes(noteIds = listOf(note.id), cardIds = emptyList())
            col.config.set("fsrs", fsrsWasEnabled)
        }
    }
}
