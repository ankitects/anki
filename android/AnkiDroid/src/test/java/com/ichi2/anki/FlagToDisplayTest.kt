//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class FlagToDisplayTest {
    @ParameterizedTest
    @EnumSource(value = Flag::class)
    fun `is hidden if flag is on app bar and fullscreen is disabled`(actualFlag: Flag) {
        assertEquals(Flag.NONE, FlagToDisplay(actualFlag, isOnAppBar = true, isFullscreen = false).get())
    }

    @ParameterizedTest
    @EnumSource(value = Flag::class)
    fun `is not hidden if flag is not on app bar and fullscreen is disabled`(actualFlag: Flag) {
        assertEquals(actualFlag, FlagToDisplay(actualFlag, isOnAppBar = false, isFullscreen = false).get())
    }

    @ParameterizedTest
    @EnumSource(value = Flag::class)
    fun `is not hidden if flag is not on app bar and fullscreen is enabled`(actualFlag: Flag) {
        assertEquals(actualFlag, FlagToDisplay(actualFlag, isOnAppBar = false, isFullscreen = true).get())
    }

    @ParameterizedTest
    @EnumSource(value = Flag::class)
    fun `is not hidden if flag is on app bar and fullscreen is enabled`(actualFlag: Flag) {
        assertEquals(actualFlag, FlagToDisplay(actualFlag, isOnAppBar = true, isFullscreen = true).get())
    }
}
