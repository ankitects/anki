// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 WPum <27683756+WPum@users.noreply.github.com>

package com.ichi2.anki

import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.anki.worker.UniqueWorkNames
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ConstantUniquenessTest {
    @Test
    fun testConstantUniqueness() {
        assertConstantUniqueness(NotificationId::class)
        assertConstantUniqueness(UniqueWorkNames::class)
        assertConstantUniqueness(BrowserColumnSelectionRecyclerItem.Companion::class)
    }

    companion object {
        /**
         * To check whether all PUBLIC CONST values in an object are unique.
         */
        fun <T : Any> assertConstantUniqueness(clazz: KClass<T>) {
            assertNotNull(clazz.objectInstance, "Can only check objects for uniqueness")
            val valueSet = HashSet<Any?>()
            for (prop in clazz.declaredMemberProperties) {
                if (!prop.isConst || prop.visibility != KVisibility.PUBLIC) {
                    continue
                }
                val value = prop.javaField?.get(null)
                assertFalse(valueSet.contains(value), "Duplicate value ('$value') for constant in ${clazz.qualifiedName}")
                valueSet.add(value)
            }
        }
    }
}
