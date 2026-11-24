// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts

internal fun RobolectricTest.startDeckOptionsController() =
    startActivityControllerNormallyOpenCollectionWithIntent(
        SingleFragmentActivity::class.java,
        DeckOptions.getIntent(targetContext, Consts.DEFAULT_DECK_ID),
    )
