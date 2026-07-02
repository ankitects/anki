/*
 Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

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
