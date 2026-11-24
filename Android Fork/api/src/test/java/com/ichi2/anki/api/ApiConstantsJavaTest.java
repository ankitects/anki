// SPDX-License-Identifier: LGPL-3.0-or-later

package com.ichi2.anki.api;

import static org.junit.Assert.assertEquals;

import com.ichi2.anki.FlashCardsContract;
import org.junit.Test;

/** Verifies the fields used by existing Java API clients remain accessible. */
public class ApiConstantsJavaTest {
    @Test
    public void exposesLegacyConstants() {
        String ankiPackageName = BuildConfig.DEBUG ? "com.ichi2.anki.debug" : "com.ichi2.anki";
        assertEquals(ankiPackageName + ".flashcards", FlashCardsContract.AUTHORITY);
        assertEquals(
                ankiPackageName + ".permission.READ_WRITE_DATABASE",
                FlashCardsContract.READ_WRITE_PERMISSION);
        assertEquals(FlashCardsContract.READ_WRITE_PERMISSION, AddContentApi.READ_WRITE_PERMISSION);
    }
}
