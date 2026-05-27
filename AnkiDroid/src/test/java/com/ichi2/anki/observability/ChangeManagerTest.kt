/*
 Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

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
package com.ichi2.anki.observability

import anki.collection.OpChanges
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.javaType
import kotlin.reflect.jvm.isAccessible

@RunWith(RobolectricTestRunner::class)
class ChangeManagerExceptionHandlingTest : JvmTest() {
    @Test
    fun `all subscribers notified even if one throws exception`() {
        var firstCalled = false
        var secondCalled = false

        // First subscriber throws an exception
        val throwingSubscriber =
            object : ChangeManager.Subscriber {
                override fun opExecuted(
                    changes: OpChanges,
                    handler: Any?,
                ) {
                    firstCalled = true
                    throw RuntimeException("Test exception")
                }
            }

        // Second subscriber should still be called
        val normalSubscriber =
            object : ChangeManager.Subscriber {
                override fun opExecuted(
                    changes: OpChanges,
                    handler: Any?,
                ) {
                    secondCalled = true
                }
            }

        ChangeManager.subscribe(throwingSubscriber)
        ChangeManager.subscribe(normalSubscriber)

        ChangeManager.notifySubscribersAllValuesChanged()

        assertThat("First subscriber should be called", firstCalled, equalTo(true))
        assertThat("Second subscriber should be called despite first throwing", secondCalled, equalTo(true))
    }
}

@RunWith(Parameterized::class)
class ChangeManagerTest {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    var property: KProperty1<OpChanges, *>? = null

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var name: String? = null

    @Test
    fun `Property is set in ALL object`() {
        assertThat(name, property!!.call(ChangeManager.ALL), equalTo(true))
    }

    companion object {
        @Parameterized.Parameters(name = "{1}")
        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<out Any>> {
            val props =
                OpChanges::class.memberProperties.filter { it.returnType.javaType == Boolean::class.java }
            assertThat(props.size, greaterThan(0))

            props.forEach { it.isAccessible = true }
            return props.map {
                arrayOf(it, it.name)
            }
        }
    }
}
