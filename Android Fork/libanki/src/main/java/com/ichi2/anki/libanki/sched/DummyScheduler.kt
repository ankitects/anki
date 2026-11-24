// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>

package com.ichi2.anki.libanki.sched

import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection

class DummyScheduler(
    col: Collection,
) : Scheduler(col) {
    override val card: Card? = null

    override fun answerCard(
        card: Card,
        rating: Rating,
    ): Unit = throw Exception("v1/v2 scheduler not supported")
}
