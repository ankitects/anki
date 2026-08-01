/*
 *  Copyright (c) 2026 Vedant Kakade <vedantkakade05@gmail.com>
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

package com.ichi2.widget

import com.google.common.reflect.ClassPath
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.test.assertEquals

class WidgetAlarmTest {
    @Test
    fun `all AnalyticsWidgetProvider subclasses are categorized`() {
        val classLoader = Thread.currentThread().contextClassLoader ?: return

        val allWidgetClasses =
            ClassPath
                .from(classLoader)
                .getTopLevelClassesRecursive("com.ichi2.widget")
                .map { it.load() }
                .filter { AnalyticsWidgetProvider::class.java.isAssignableFrom(it) }
                .filter { !Modifier.isAbstract(it.modifiers) }
                .toSet()

        val expectedWidgets = (RECURRING_WIDGETS + NON_RECURRING_WIDGETS).toSet()

        assertEquals(
            expectedWidgets,
            allWidgetClasses,
            "all AnalyticsWidgetProvider subclasses must be included in either RECURRING_WIDGETS or NON_RECURRING_WIDGETS",
        )
    }
}
