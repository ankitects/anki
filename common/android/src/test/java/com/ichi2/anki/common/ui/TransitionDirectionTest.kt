/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.common.ui

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class TransitionDirectionTest {
    @ParameterizedTest
    @EnumSource(
        value = TransitionDirection::class,
        mode = EnumSource.Mode.EXCLUDE,
        names = ["START", "END", "UP", "DOWN", "RIGHT", "LEFT"],
    )
    fun invert_returns_same_input_for_not_directional_params(direction: TransitionDirection) {
        assertEquals(direction, direction.invert())
    }

    @ParameterizedTest
    @MethodSource("invert_returns_inverse_direction_args")
    fun invert_returns_inverse_direction(
        first: TransitionDirection,
        second: TransitionDirection,
    ) {
        assertEquals(second, first.invert())
        assertEquals(first, second.invert())
    }

    companion object {
        @JvmStatic // used in @MethodSource
        fun invert_returns_inverse_direction_args(): Stream<Arguments> =
            Stream.of(
                Arguments.of(TransitionDirection.START, TransitionDirection.END),
                Arguments.of(TransitionDirection.UP, TransitionDirection.DOWN),
                Arguments.of(TransitionDirection.RIGHT, TransitionDirection.LEFT),
            )
    }
}
