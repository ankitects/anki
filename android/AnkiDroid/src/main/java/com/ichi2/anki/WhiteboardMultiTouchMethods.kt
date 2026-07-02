/*
 Copyright (c) 2021 Akshay Jadhav <jadhavAkshay0701@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
