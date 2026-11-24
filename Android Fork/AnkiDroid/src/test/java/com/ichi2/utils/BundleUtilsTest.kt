// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.utils

import android.os.Bundle
import com.ichi2.anki.utils.ext.getLongOrNull
import com.ichi2.anki.utils.ext.requireBoolean
import com.ichi2.anki.utils.ext.requireLong
import com.ichi2.anki.utils.ext.requireLongArray
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import kotlin.random.Random
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BundleUtilsTest {
    @Test
    fun test_GetNullableLong_NotFound_ReturnsNull() {
        val b = mock(Bundle::class.java)

        whenever(b.containsKey(anyString())).thenReturn(false)

        val value = b.getLongOrNull(KEY)

        verify(b, times(0)).getLong(eq(KEY))

        assertNull(value)
    }

    @Test
    fun test_GetNullableLong_Found_ReturnIt() {
        val expected = Random.nextLong()
        val b = mock(Bundle::class.java)

        whenever(b.containsKey(anyString())).thenReturn(true)

        whenever(b.getLong(anyString())).thenReturn(expected)

        val value = b.getLongOrNull(KEY)

        verify(b).getLong(eq(KEY))

        assertEquals(expected, value)
    }

    @Test
    fun test_RequireLong_NotFound_ThrowsException() {
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(false)

        assertFailsWith<IllegalStateException> { mockedBundle.requireLong(KEY) }

        verify(mockedBundle).containsKey(eq(KEY))
    }

    @Test
    fun test_RequireLong_Found_ReturnIt() {
        val expected = Random.nextLong()
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(true)
        whenever(mockedBundle.getLong(anyString())).thenReturn(expected)

        val value = mockedBundle.requireLong(KEY)

        verify(mockedBundle).containsKey(eq(KEY))
        verify(mockedBundle).getLong(eq(KEY))

        assertEquals(expected, value)
    }

    @Test
    fun test_RequireBoolean_NotFound_ThrowsException() {
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(false)

        val exception = assertFailsWith<IllegalStateException> { mockedBundle.requireBoolean(KEY) }

        assertThat(exception.message, equalTo("key: 'KEY' not found"))
        verify(mockedBundle).containsKey(eq(KEY))
    }

    @Test
    fun test_RequireBoolean_Found_ReturnIt() {
        val expected = true
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(true)
        whenever(mockedBundle.getBoolean(anyString())).thenReturn(expected)

        val value = mockedBundle.requireBoolean(KEY)

        verify(mockedBundle).containsKey(eq(KEY))
        verify(mockedBundle).getBoolean(eq(KEY))

        assertEquals(expected, value)
    }

    @Test
    fun test_RequireLongArray_NotFound_ThrowsException() {
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(false)

        val exception = assertFailsWith<IllegalStateException> { mockedBundle.requireLongArray(KEY) }

        assertThat(exception.message, equalTo("key: 'KEY' not found"))
        verify(mockedBundle).containsKey(eq(KEY))
    }

    @Test
    fun test_RequireLongArray_Found_ReturnIt() {
        val expected = longArrayOf(Random.nextLong())
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(true)
        whenever(mockedBundle.getLongArray(anyString())).thenReturn(expected)

        val value = mockedBundle.requireLongArray(KEY)

        verify(mockedBundle).containsKey(eq(KEY))
        verify(mockedBundle).getLongArray(eq(KEY))

        assertEquals(expected, value)
    }

    companion object {
        const val KEY = "KEY"
    }
}
