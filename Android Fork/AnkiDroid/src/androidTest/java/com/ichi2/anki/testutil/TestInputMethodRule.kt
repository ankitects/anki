// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Temporarily selects the test APK's IME and restores the previous keyboard even on failure. */
class TestInputMethodRule(
    private val executeShellCommand: (String) -> String = ::executeImeCommand,
) : TestRule {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val testPackage get() = instrumentation.context.packageName
    private val uri get() = "content://$testPackage.ime".toUri()

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val previous = executeShellCommand("settings get secure default_input_method").trim()
                val ime = "$testPackage/${TestInputMethod::class.java.name}"
                val wasEnabled = executeShellCommand("ime list -s").lineSequence().any { it.trim() == ime }
                val failures = mutableListOf<Throwable>()
                try {
                    val enableOutput = executeShellCommand("ime enable $ime")
                    val selectOutput = executeShellCommand("ime set $ime")
                    assertEquals(
                        ime,
                        executeShellCommand("settings get secure default_input_method").trim(),
                        "Failed to select test keyboard $ime: $enableOutput$selectOutput",
                    )
                    base.evaluate()
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                // Attempt each cleanup independently, preserving the original test failure.
                try {
                    if (previous.isNotEmpty() && previous != "null") {
                        val restoreOutput = executeShellCommand("ime set $previous")
                        assertEquals(
                            previous,
                            executeShellCommand("settings get secure default_input_method").trim(),
                            "Failed to restore keyboard $previous: $restoreOutput",
                        )
                    }
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                try {
                    if (!wasEnabled) {
                        val disableOutput = executeShellCommand("ime disable $ime")
                        assertFalse(
                            executeShellCommand("ime list -s").lineSequence().any { it.trim() == ime },
                            "Failed to disable test keyboard $ime: $disableOutput",
                        )
                    }
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                MultipleFailureException.assertEmpty(failures)
            }
        }

    private fun callIme(
        action: String,
        text: String? = null,
    ): Bundle? = instrumentation.context.contentResolver.call(uri, action, text, null)

    /** Wait for a responsive input connection and matching metadata in the same IME snapshot. */
    fun awaitEditor(
        inputType: Int? = null,
        imeAction: Int? = null,
    ): EditorInfo {
        var response = ImeResponse()
        waitUntil(message = {
            "IME expected inputType=$inputType, action=$imeAction; received ${response.describeEditor()}"
        }) {
            response = ImeResponse(assertNotNull(callIme("editor")))
            response.ready && response.editor?.matches(inputType, imeAction) == true
        }
        return assertNotNull(response.editor)
    }

    fun perform(
        action: String,
        text: String? = null,
    ) {
        var response = ImeResponse()
        waitUntil(message = { "IME connection did not become ready for $action" }) {
            response = ImeResponse(callIme(action, text))
            response.ready
        }
        assertTrue(response.accepted, "IME rejected $action")
    }

    private fun EditorInfo.matches(
        inputType: Int?,
        imeAction: Int?,
    ): Boolean =
        packageName == instrumentation.targetContext.packageName && matchesInputType(inputType) &&
            (imeAction == null || imeOptions and EditorInfo.IME_MASK_ACTION == imeAction)

    private fun EditorInfo.matchesInputType(expected: Int?): Boolean =
        if (expected == null) {
            inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
        } else {
            inputType == expected
        }

    /** Metadata and readiness must come from the same call to the IME. */
    private class ImeResponse(
        bundle: Bundle? = null,
    ) {
        val editor = bundle?.let { BundleCompat.getParcelable(it, "editor", EditorInfo::class.java) }
        val ready = bundle?.getBoolean("ready") == true
        val accepted = bundle?.getBoolean("accepted") == true

        fun describeEditor(): String {
            val action = editor?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            return "inputType=${editor?.inputType}, action=$action, package=${editor?.packageName}, ready=$ready"
        }
    }
}

// Commands need no shell syntax or stdin; the rule verifies the resulting keyboard state.
@SuppressLint("DiscouragedApi")
private fun executeImeCommand(command: String): String =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(command)
