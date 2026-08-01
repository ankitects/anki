/*
 *  Copyright (c) 2024 WPum <27683756+WPum@users.noreply.github.com>
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

package com.ichi2.anki

import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsRecyclerItem
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
        assertConstantUniqueness(ReviewerMenuSettingsRecyclerItem.Companion::class)
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
