/*
 *
 * Copyright (c) 2018 Mike Hardy <github@mikehardy.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tests.libanki

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Retry a test maxTries times, only failing if zero successes.
 * Defaults to 3 tries.
 *
 * Usage: @Rule public final RetryRule retry = new RetryRule(3);
 * @param i how many times to try
 * @throws IllegalArgumentException if maxTries is less than 1
 */
class RetryRule(
    i: Int,
) : TestRule {
    /**
     * How many times to try a test
     */
    private var maxTries = 3

    /**
     * @param i number of times to try
     * @throws IllegalArgumentException if i is less than 1
     */
    private fun setMaxTries(i: Int) {
        require(i >= 1) { "iterations < 1: $i" }
        maxTries = i
    }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement = statement(base, description)

    private fun statement(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                var caughtThrowable: Throwable? = null

                // implement retry logic here
                for (i in 0 until maxTries) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        caughtThrowable = t
                        System.err.println(description.displayName + ": run " + (i + 1) + " failed")
                        t.printStackTrace(System.err)
                    }
                }
                System.err.println(description.displayName + ": giving up after " + maxTries + " failures")
                throw caughtThrowable!!
            }
        }
    }

    init {
        setMaxTries(i)
    }
}
