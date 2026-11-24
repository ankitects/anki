// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class TestInputMethodRuleTest {
    @Test
    fun testFailureSurvivesSuccessfulCleanup() {
        val shell = KeyboardShell()
        val original = AssertionError("test failed")

        val failure = assertFailsWith<AssertionError> { shell.evaluate { throw original } }

        assertSame(original, failure)
        assertEquals(shell.previous, shell.selected)
        assertNull(shell.enabledIme)
    }

    @Test
    fun testAndBothCleanupFailuresAreReported() {
        val original = AssertionError("test failed")
        val restoreFailure = IllegalStateException("restore failed")
        val disableFailure = IllegalStateException("disable failed")
        val shell = KeyboardShell(restoreFailure, disableFailure)

        val failure = assertFailsWith<MultipleFailureException> { shell.evaluate { throw original } }

        assertEquals(listOf(original, restoreFailure, disableFailure), failure.failures)
    }

    @Test
    fun selectionFailureReportsImeOutput() {
        val shell = KeyboardShell(ignoresSelection = true)

        val failure = assertFailsWith<AssertionError> { shell.evaluate {} }

        val message = assertNotNull(failure.message)
        assertContains(message, "now enabled for user #0")
        assertContains(message, "selected for user #0")
    }

    /** Models keyboard selection without changing the emulator's keyboard. */
    private class KeyboardShell(
        private val restoreFailure: Throwable? = null,
        private val disableFailure: Throwable? = null,
        private val ignoresSelection: Boolean = false,
    ) {
        val previous = "previous.ime/.Keyboard"
        var selected = previous
        var enabledIme: String? = null

        fun evaluate(block: () -> Unit) {
            val statement =
                object : Statement() {
                    override fun evaluate() = block()
                }
            TestInputMethodRule(::execute).apply(statement, Description.EMPTY).evaluate()
        }

        private fun execute(command: String): String {
            when {
                command == "settings get secure default_input_method" -> return selected
                command == "ime list -s" -> return listOfNotNull(previous, enabledIme).joinToString("\n")
                command.startsWith("ime enable ") -> {
                    val ime = command.removePrefix("ime enable ")
                    enabledIme = ime
                    return "Input method $ime: now enabled for user #0\n"
                }
                command.startsWith("ime set ") -> {
                    val ime = command.removePrefix("ime set ")
                    if (ime == previous) restoreFailure?.let { throw it }
                    if (!ignoresSelection) selected = ime
                    return "Input method $ime selected for user #0\n"
                }
                command.startsWith("ime disable ") -> {
                    disableFailure?.let { throw it }
                    enabledIme = null
                }
                else -> error("Unexpected shell command: $command")
            }
            return ""
        }
    }
}
