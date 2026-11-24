// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Paul Tietz <tietz.paul@gmail.com>

package com.ichi2.anki

class FlagToDisplay(
    private val actualFlag: Flag,
    private val isOnAppBar: Boolean,
    private val isFullscreen: Boolean,
) {
    fun get(): Flag =
        when {
            !isOnAppBar -> actualFlag
            isFullscreen -> actualFlag
            else -> Flag.NONE
        }
}
