// SPDX-License-Identifier: GPL-3.0-or-later

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
