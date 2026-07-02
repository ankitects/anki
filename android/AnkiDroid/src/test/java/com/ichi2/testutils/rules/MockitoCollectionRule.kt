/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils.rules

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.Collection
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.Mockito
import timber.log.Timber

/**
 * Mocks [Collection] using [Mockito.mock]
 *
 * usage:
 *
 * ```
 *      @get:Rule
 *      val mockColRule = MockitoCollectionRule()
 *      override val col: Collection get() = mockColRule.col
 * ```
 */
class MockitoCollectionRule : TestRule {
    val col: Collection = Mockito.mock(Collection::class.java)

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    mockCollection()
                    base.evaluate()
                } finally {
                    removeCollectionMock()
                }
            }
        }

    private fun removeCollectionMock() {
        Timber.v("removing collection mock")
        CollectionManager.setColForTests(null)
    }

    private fun mockCollection() {
        Timber.v("mocking collection")
        CollectionManager.setColForTests(col)
    }
}
