/*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General private License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General private License for more details.
 *
 * You should have received a copy of the GNU General private License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
