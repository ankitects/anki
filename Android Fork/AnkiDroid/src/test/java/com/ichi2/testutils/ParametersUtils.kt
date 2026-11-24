// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.testutils

object ParametersUtils {
    /**
     * Used to satisfy a parameter with a null in a declarative way
     */
    fun <T> whatever(): T? = null
}
