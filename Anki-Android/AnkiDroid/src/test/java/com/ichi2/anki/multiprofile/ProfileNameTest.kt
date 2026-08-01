/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import com.ichi2.anki.multiprofile.ProfileName.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameTest {
    @Test
    fun `blank input returns Empty`() {
        assertEquals(ValidationResult.Empty, ProfileName.validate(""))
    }

    @Test
    fun `whitespace-only input returns Empty`() {
        assertEquals(ValidationResult.Empty, ProfileName.validate("   "))
    }

    @Test
    fun `tabs and newlines only return Empty`() {
        assertEquals(ValidationResult.Empty, ProfileName.validate("\t\n  "))
    }

    @Test
    fun `simple valid name returns Ok`() {
        val result = ProfileName.validate("Mike")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Mike", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `name exactly at MAX_LENGTH is accepted`() {
        val input = "a".repeat(ProfileName.MAX_LENGTH)
        val result = ProfileName.validate(input)
        assertTrue(result is ValidationResult.Valid)
        assertEquals(input, (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `name one character over MAX_LENGTH returns TooLong`() {
        val input = "a".repeat(ProfileName.MAX_LENGTH + 1)
        val result = ProfileName.validate(input)
        assertEquals(ValidationResult.TooLong(ProfileName.MAX_LENGTH + 1), result)
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        val result = ProfileName.validate("  David  ")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("David", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `two-word name is trimmed at start and end`() {
        val result = ProfileName.validate("  Ashish Yadav  ")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Ashish Yadav", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `interior whitespace is preserved`() {
        val result = ProfileName.validate("Ashish    Yadav")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Ashish    Yadav", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `only leading and trailing whitespace is removed for multi-word names`() {
        val result = ProfileName.validate("  a  b  c  ")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("a  b  c", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `unicode letters are accepted`() {
        val result = ProfileName.validate("日本語")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("日本語", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `accented letters are accepted`() {
        val result = ProfileName.validate("José")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("José", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `digits hyphens and underscores are accepted`() {
        val result = ProfileName.validate("user_1-profile")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("user_1-profile", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `punctuation is accepted`() {
        val result = ProfileName.validate("India!")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("India!", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `mixed punctuation is accepted`() {
        val result = ProfileName.validate("Bob!@#")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Bob!@#", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `repeated special characters are preserved`() {
        val result = ProfileName.validate("a!!!b")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("a!!!b", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `emoji is accepted`() {
        val result = ProfileName.validate("Ashish 🎉")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Ashish 🎉", (result as ValidationResult.Valid).name.value)
    }

    @Test
    fun `fromTrustedSource preserves value untouched`() {
        val untouched = "  weird!! value  "
        assertEquals(untouched, ProfileName.fromTrustedSource(untouched).value)
    }
}
