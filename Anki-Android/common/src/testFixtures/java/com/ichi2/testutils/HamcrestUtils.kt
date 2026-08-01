// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.hamcrest.Matcher
import org.hamcrest.collection.IsIterableContainingInAnyOrder

object HamcrestUtils {
    /** containsInAnyOrder over a collection, rather than an array */
    inline fun <reified T> containsInAnyOrder(items: Collection<T>): Matcher<Iterable<T>?>? =
        IsIterableContainingInAnyOrder.containsInAnyOrder(*items.toTypedArray())
}
