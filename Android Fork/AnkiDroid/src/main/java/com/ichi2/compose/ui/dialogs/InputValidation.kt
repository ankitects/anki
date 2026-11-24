// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

/**
 * Validation policy for [TextInputDialog]. Checks return a plain or annotated error message,
 * or null when the input is valid.
 */
sealed interface InputValidation {
    /** When false, checks run only after a confirmation attempt, rather than while editing. */
    val validateOnInput: Boolean

    /** Allows any input without running validation. */
    data object None : InputValidation {
        override val validateOnInput: Boolean = true
    }

    /**
     * Validates synchronously, including immediately before confirmation.
     *
     * @param check must be fast and free of side effects; it can run during composition
     */
    class Immediate(
        override val validateOnInput: Boolean = true,
        val check: (String) -> CharSequence?,
    ) : InputValidation

    /**
     * Runs [precheck] synchronously, then [check] only if the precheck succeeds.
     * The suspending check is re-run before confirmation, even if it passed while editing.
     *
     * @param precheck must be fast and free of side effects; it can run during composition
     * @param invalidationKey change this when external data used by [check] changes to invalidate
     *   pending results and restart validation while editing
     * @param check runs in the dialog's coroutine scope; blocking work must switch dispatcher
     */
    class Async(
        val precheck: (String) -> CharSequence? = { null },
        val invalidationKey: Any? = null,
        override val validateOnInput: Boolean = true,
        val check: suspend (String) -> CharSequence?,
    ) : InputValidation
}
