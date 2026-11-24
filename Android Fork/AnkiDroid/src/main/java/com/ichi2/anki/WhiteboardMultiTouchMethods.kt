// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavAkshay0701@gmail.com>

package com.ichi2.anki

/**
 * Provides callbacks for multi touch handling on a whiteboard
 */
interface WhiteboardMultiTouchMethods {
    /** Tap onto the currently shown flashcard at position x and y
     *
     * @param x horizontal position of the event
     * @param y vertical position of the event
     */
    fun tapOnCurrentCard(
        x: Int,
        y: Int,
    )

    /** Scroll the currently shown flashcard vertically
     *
     * @param dy amount to be scrolled
     */
    fun scrollCurrentCardBy(dy: Int)
}
