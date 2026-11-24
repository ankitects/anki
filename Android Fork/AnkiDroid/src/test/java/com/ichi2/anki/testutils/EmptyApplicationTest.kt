// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutils

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.activitySetup
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.dialogs.databaseErrorDialogs
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class EmptyApplicationTest {
    @Test
    fun `Animations provider is registered`() {
        // Must not throw UninitializedPropertyAccessException
        Animations.areAnimationsEnabled(getApplicationContext())
    }

    @Test
    fun `database error dialogs are registered`() {
        // Must not throw UninitializedPropertyAccessException
        databaseErrorDialogs.canConfirmDatabaseCheck(getApplicationContext())
    }

    @Test
    fun `activity setup is registered`() {
        assertNotNull(activitySetup)
    }
}
