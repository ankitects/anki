// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.lint.utils

import org.jetbrains.uast.UClass

object LintUtils {
    /**
     * A helper method to check for special classes(Time and SystemTime) where the rules related to time apis shouldn't
     * be applied.
     *
     * @param classes the list of classes to look through
     * @param allowedClasses  the list of classes where the checks should be ignored
     * @return true if this is a class where the checks should not be applied, false otherwise
     */
    fun isAnAllowedClass(
        classes: List<UClass>,
        vararg allowedClasses: String,
    ): Boolean = classes.any { uClass -> uClass.name!! in allowedClasses }
}
