// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import anki.scheduler.CardAnswer
import anki.scheduler.CardAnswer.Rating
import anki.scheduler.SchedulingStates
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.sched.Scheduler

fun Scheduler.answerCard(
    card: Card,
    states: SchedulingStates,
    rating: Rating,
): CardAnswer =
    buildAnswer(card, states, rating).also {
        numberOfAnswersRecorded += 1
    }
