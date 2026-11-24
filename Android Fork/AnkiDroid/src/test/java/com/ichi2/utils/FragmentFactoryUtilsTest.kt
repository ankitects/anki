// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class FragmentFactoryUtilsTest {
    private class TestFragment : Fragment()

    @Test
    fun test_instantiate() {
        val activity = mock(FragmentActivity::class.java)
        val manager = mock(FragmentManager::class.java)
        val factory = mock(FragmentFactory::class.java)
        val classLoader = mock(ClassLoader::class.java)

        val testFragment = TestFragment()

        whenever(activity.supportFragmentManager).thenReturn(manager)
        whenever(activity.classLoader).thenReturn(classLoader)

        whenever(manager.fragmentFactory).thenReturn(factory)
        whenever(factory.instantiate(classLoader, testFragment.javaClass.name))
            .thenReturn(testFragment)

        val result: Fragment = FragmentFactoryUtils.instantiate(activity, TestFragment::class.java)
        Assert.assertEquals(testFragment, result)
        verify(factory, times(1)).instantiate(classLoader, testFragment.javaClass.name)
    }
}
