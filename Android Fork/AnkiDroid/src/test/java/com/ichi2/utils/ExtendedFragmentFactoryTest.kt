// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import com.ichi2.testutils.MockFragmentClassLoader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class ExtendedFragmentFactoryTest {
    internal class TestFragmentFactoryTest : ExtendedFragmentFactory {
        constructor()

        constructor(baseFactory: FragmentFactory) : super(baseFactory)
    }

    @Test
    fun willCallBaseFactory() {
        val baseFF = mock(FragmentFactory::class.java)
        val testFF: ExtendedFragmentFactory = TestFragmentFactoryTest(baseFF)

        testFF.instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME)

        verify(baseFF, times(1)).instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME)
    }

    @Test
    fun testAttachToActivity() {
        val activity = mock(AppCompatActivity::class.java)
        val fragmentManager = mock(FragmentManager::class.java)
        val baseFactory = mock(FragmentFactory::class.java)

        whenever(activity.supportFragmentManager).thenReturn(fragmentManager)
        whenever(fragmentManager.fragmentFactory).thenReturn(baseFactory)

        val testFF: ExtendedFragmentFactory = TestFragmentFactoryTest()
        val result = testFF.attachToActivity<ExtendedFragmentFactory>(activity)

        assertEquals(testFF, result)

        verify(fragmentManager, times(1)).fragmentFactory = testFF

        testFF.instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME)

        verify(baseFactory, times(1)).instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME)
    }

    @Test
    fun testAttachToFragmentManager() {
        val fragmentManager = mock(FragmentManager::class.java)
        val baseFactory = mock(FragmentFactory::class.java)

        whenever(fragmentManager.fragmentFactory).thenReturn(baseFactory)

        val testFF: ExtendedFragmentFactory = TestFragmentFactoryTest()

        val result = testFF.attachToFragmentManager<ExtendedFragmentFactory>(fragmentManager)

        assertEquals(testFF, result)

        verify(fragmentManager, times(1)).fragmentFactory = testFF

        testFF.instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME)

        verify(baseFactory, times(1)).instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME)
    }

    companion object {
        private val fakeClassLoader: ClassLoader = MockFragmentClassLoader()
    }
}
