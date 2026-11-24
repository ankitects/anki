// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.EspressoKey
import androidx.test.espresso.action.ViewActions

/** Presses a key with named arguments for keyboard modifiers. */
fun ViewInteraction.pressKey(
    keyCode: Int,
    ctrl: Boolean = false,
    alt: Boolean = false,
    shift: Boolean = false,
): ViewInteraction =
    perform(
        ViewActions.pressKey(
            EspressoKey
                .Builder()
                .withKeyCode(keyCode)
                .withCtrlPressed(ctrl)
                .withAltPressed(alt)
                .withShiftPressed(shift)
                .build(),
        ),
    )
