/*
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.snackbar

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
internal class SensibleSwipeDismissBehaviorTest {
    private val behavior =
        SensibleSwipeDismissBehavior().ViewDragHelperCallback().apply {
            initialChildLeft = 0
        }

    private val snackbar =
        mock(View::class.java).apply {
            whenever(width).thenReturn(100)
        }

    private fun positionSnackbarAtX(x: Int) {
        whenever(snackbar.left).thenReturn(x)
    }

    @Test fun `Stationary snackbar isn't dismissed if it hasn't traveled`() {
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 0f))
    }

    @Test fun `Stationary snackbar isn't dismissed if it hasn't traveled far`() {
        positionSnackbarAtX(-5)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 0f))

        positionSnackbarAtX(+5)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 0f))
    }

    @Test fun `Stationary snackbar is dismissed if it has traveled far`() {
        positionSnackbarAtX(+80)
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, 0f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.ToTheLeft, behavior.shouldDismiss(snackbar, 0f))
    }

    @Test fun `Moving snackbar isn't dismissed if it hasn't traveled far and is moving slowly`() {
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 100f))
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, -100f))

        positionSnackbarAtX(+5)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 100f))
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, -100f))
    }

    @Test fun `Moving snackbar isn't dismissed if it has traveled far, but is moving slowly towards initial position`() {
        positionSnackbarAtX(+80)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, -100f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, +100f))
    }

    @Test fun `Moving snackbar is dismissed if it has traveled far and is moving towards the closest edge, even if slowly`() {
        positionSnackbarAtX(+80)
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, +100f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.ToTheLeft, behavior.shouldDismiss(snackbar, -100f))
    }

    @Test fun `Moving snackbar is dismissed if it is moving fast`() {
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, +1000000f))

        positionSnackbarAtX(+80)
        assertEquals(Dismiss.ToTheLeft, behavior.shouldDismiss(snackbar, -1000000f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, +1000000f))
    }
}
