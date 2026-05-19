// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.compat

import android.annotation.SuppressLint
import com.ichi2.anki.compat.BaseCompat
import com.ichi2.anki.compat.Compat
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.compat.CompatV26
import com.ichi2.anki.model.Directory
import com.ichi2.testutils.createTransientDirectory
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import kotlin.test.assertFailsWith

/**
 * Allows to test with CompatV24 (originally 21, thus the class name) and V26.
 * In particular it allows to test version of the code that uses [java.nio.file.Files] and [java.nio.file.Path] classes.
 * And versions that must restrict themselves to [File].
 */
@RunWith(Parameterized::class)
abstract class Test21And26 {
    companion object {
        @SuppressLint("NewApi")
        @JvmStatic // required for Parameters
        @Parameterized.Parameters(name = "{1}")
        fun data(): Iterable<Array<Any>> =
            sequence {
                yield(arrayOf(BaseCompat(), "BaseCompat"))
                yield(arrayOf(CompatV26(), "CompatV26"))
            }.asIterable()

        lateinit var staticCompat: Compat

        @BeforeClass
        @JvmStatic // required for @BeforeClass
        fun setupClass() {
            mockkObject(CompatHelper)
            every { CompatHelper.compat } answers { staticCompat }
        }

        @AfterClass
        @JvmStatic // required for @AfterClass
        fun tearDownClass() {
            unmockkObject(CompatHelper)
        }
    }

    @Parameterized.Parameter(0)
    lateinit var compat: Compat

    /** Used in the "Test Results" Window */
    @Parameterized.Parameter(1)
    @Suppress("unused")
    lateinit var unitTestDescription: String

    val isV26: Boolean
        get() = compat is CompatV26

    @Before
    open fun setup() {
        staticCompat = compat
    }

    /**
     * Represents structure and compat required to simulate https://github.com/ankidroid/Anki-Android/issues/10358
     * This is a bug that occurred in a smartphone, where listFiles returned `null` on an existing directory.
     */
    class PermissionDenied(
        val directory: Directory,
        val compat: Compat,
    ) {
        /**
         * This run test, ensuring that [java.nio.file.Files.newDirectoryStream] throws on [directory].
         * This is useful in the case where we can't directly access the directory or compat
         */
        fun <T> runWithPermissionDenied(test: () -> T): T = runUsingCompat(compat, test)

        /** Runs a provided action having [CompatHelper.compat] return the provided compat */
        private fun <T> runUsingCompat(
            compatOverride: Compat,
            test: () -> T,
        ): T {
            val originalValue = staticCompat
            staticCompat = compatOverride
            try {
                return test()
            } finally {
                staticCompat = originalValue
            }
        }

        /**
         * Allow to ensure that [test] throw an IOException.
         * We plan to use it to ensure that if we don't have permission to read the directory
         * the exception is not caught.
         */
        fun assertThrowsWhenPermissionDenied(test: () -> Unit): IOException = runWithPermissionDenied { assertFailsWith { test() } }
    }

    /**
     * Create a directory `directory`. Ensures that `directory.hasFile` returns `null`,
     * which simulates to simulate https://github.com/ankidroid/Anki-Android/issues/10358.
     * Also ensure that [java.nio.file.Files.newDirectoryStream] fails on this directory.
     */
    @SuppressLint("NewApi") // File.toPath = only called if sending API 26
    fun createPermissionDenied(): PermissionDenied {
        val directory = createTransientDirectory()
        val compat = CompatHelper.compat
        val directoryWithPermissionDenied =
            spy(directory) {
                on { listFiles() } doReturn null
            }
        val compatWithPermissionDenied =
            if (compat is CompatV26) {
                // Closest to simulate [newDirectoryStream] throwing [AccessDeniedException]
                // since this method calls toPath.
                spy(compat) {
                    doThrow(AccessDeniedException(directory)).whenever(it).newDirectoryStream(eq(directory.toPath()))
                }
            } else {
                compat
            }
        return PermissionDenied(Directory.createInstance(directoryWithPermissionDenied)!!, compatWithPermissionDenied)
    }
}
