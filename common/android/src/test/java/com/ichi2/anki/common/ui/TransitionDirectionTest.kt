// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

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
