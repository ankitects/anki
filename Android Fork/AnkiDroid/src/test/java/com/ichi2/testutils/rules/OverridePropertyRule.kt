// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.rules

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.reflect.KMutableProperty0

/**
 * Temporarily overrides mutable properties and restores their values on test completion.
 *
 * ```kotlin
 * @get:Rule
 * val notificationPreference =
 *     OverridePropertyRule(Prefs::reminderNotifsRequestShown to false)
 *
 * // Use as normal, the original value is restored after the test.
 * Prefs.reminderNotifsRequestShown = true
 * ```
 *
 * @param overrides Pairs of mutable property references and their temporary values.
 * All properties in one rule must share the same value type [T].
 */
class OverridePropertyRule<T>(
    private vararg val overrides: Pair<KMutableProperty0<T>, T>,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val originalValues = overrides.map { (property, _) -> property to property.get() }
                try {
                    overrides.forEach { (property, value) -> property.set(value) }
                    base.evaluate()
                } finally {
                    originalValues.forEach { (property, value) -> property.set(value) }
                }
            }
        }
}
