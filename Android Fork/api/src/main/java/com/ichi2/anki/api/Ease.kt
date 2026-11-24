// SPDX-FileCopyrightText: Copyright (c) 2024 Ben Wicks <benjaminlwicks@gmail.com>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

/*
 * [value] should be kept in sync with the [com.ichi2.anki.Ease] enum.
 *
 * param value The so called value of the button. For the sake of consistency with upstream and our API
 * the buttons are numbered from 1 to 4.
 */
public enum class Ease(
    public val value: Int,
) {
    EASE_1(1),
    EASE_2(2),
    EASE_3(3),
    EASE_4(4),
}
