// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Vedant Kakade <vedantkakade05@gmail.com>

package com.ichi2.widget

import com.google.common.reflect.ClassPath
import com.ichi2.anki.widget.NON_RECURRING_WIDGETS
import com.ichi2.anki.widget.RECURRING_WIDGETS
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
