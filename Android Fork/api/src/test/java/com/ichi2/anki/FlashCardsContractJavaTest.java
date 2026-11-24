// SPDX-License-Identifier: LGPL-3.0-or-later

package com.ichi2.anki;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class FlashCardsContractJavaTest {
    @Test
    @SuppressWarnings("deprecation") // Verify the original Java fields remain accessible.
    public void defaultColumnsAreAccessibleFromJava() {
        assertArrayEquals(FlashCardsContract.Note.DEFAULT_PROJECTION, FlashCardsContract.Note.getDefaultColumns());
        assertArrayEquals(FlashCardsContract.Model.DEFAULT_PROJECTION, FlashCardsContract.Model.getDefaultColumns());
        assertArrayEquals(FlashCardsContract.CardTemplate.DEFAULT_PROJECTION, FlashCardsContract.CardTemplate.getDefaultColumns());
        assertArrayEquals(FlashCardsContract.Card.DEFAULT_PROJECTION, FlashCardsContract.Card.getDefaultColumns());
        assertArrayEquals(FlashCardsContract.ReviewInfo.DEFAULT_PROJECTION, FlashCardsContract.ReviewInfo.getDefaultColumns());
        assertArrayEquals(FlashCardsContract.Deck.DEFAULT_PROJECTION, FlashCardsContract.Deck.getDefaultColumns());
    }
}
