// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.profiles.RenameProfileDialog
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.testutils.ShadowLowerPaneView
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TextInputDialogTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [29, Config.TARGET_SDK], qualifiers = "w411dp-h400dp-mdpi", shadows = [ShadowLowerPaneView::class])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `text input stays usable in the lower split screen pane`() {
        var submitted: String? = null
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog("Add profile", "Profile name", "Add", { submitted = it }, {})
            }
        }
        compose.onNodeWithText("Add profile").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().performTextInput("Work")
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val add = compose.onNodeWithText("Add").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        add.performTouchInput { click() }
        assertEquals("Work", submitted)
    }

    @Test
    fun `multiline Enter inserts a newline without submitting`() {
        var submitted: String? = null
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Certificate",
                    label = "PEM",
                    confirmText = "Save",
                    singleLine = false,
                    onConfirm = { submitted = it },
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("first")
        field.performKeyInput { pressKey(Key.Enter) }
        field.performTextInput("second")

        assertNull(submitted)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("first\nsecond")))
        compose.onNodeWithText("Save").performClick()
        assertEquals("first\nsecond", submitted)
    }

    @Test
    fun `async validation ignores older text and rechecks before keyboard submission`() {
        val slowResult = CompletableDeferred<CharSequence?>()
        val submitted = mutableListOf<String>()
        var taken = false
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    onConfirm = { submitted.add(it) },
                    onDismissRequest = {},
                    validation =
                        InputValidation.Async { text ->
                            if (text == "slow") {
                                slowResult.await()
                            } else if (taken) {
                                "Already exists"
                            } else {
                                null
                            }
                        },
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("slow")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        field.performTextReplacement("current")
        compose.onNodeWithText("Confirm").assertIsEnabled()
        compose.runOnIdle { slowResult.complete("Old error") }
        compose.onNodeWithText("Old error").assertDoesNotExist()
        compose.runOnIdle { taken = true }
        field.performImeAction()
        compose.onNodeWithText("Already exists").assertIsDisplayed()
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `editing while async submission is pending does not submit the new text`() {
        val confirmation = CompletableDeferred<CharSequence?>()
        var validateSubmission = false
        val submitted = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    onConfirm = { submitted.add(it) },
                    onDismissRequest = {},
                    validation =
                        InputValidation.Async { text ->
                            if (validateSubmission &&
                                text == "first"
                            ) {
                                confirmation.await()
                            } else {
                                null
                            }
                        },
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("first")
        compose.runOnIdle { validateSubmission = true }
        field.performImeAction()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        field.performTextReplacement("second")
        compose.runOnIdle { confirmation.complete(null) }
        compose.onNodeWithText("Confirm").assertIsEnabled()
        assertTrue(submitted.isEmpty())
        field.performImeAction()
        assertEquals(listOf("second"), submitted)
    }

    @Test
    fun `cancelling during the confirmation check does not confirm`() {
        var shown by mutableStateOf(true)
        val check = CompletableDeferred<CharSequence?>()
        var checkSubmission = false
        val submitted = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                if (shown) {
                    TextInputDialog(
                        title = "Rename",
                        label = "Name",
                        confirmText = "Confirm",
                        validation = InputValidation.Async { if (checkSubmission) check.await() else null },
                        onConfirm = { submitted.add(it) },
                        onDismissRequest = {
                            shown = false
                            // The check finishes before the next frame removes the dialog.
                            check.complete(null)
                        },
                    )
                }
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("new")
        compose.runOnIdle { checkSubmission = true }
        field.performImeAction()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        assertTrue(submitted.isEmpty())

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), submitted)
    }

    @Test
    fun `an ignored dismissal does not stop later confirmations`() {
        var shown by mutableStateOf(true)
        var busy = true
        val submitted = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                if (shown) {
                    TextInputDialog(
                        title = "Rename",
                        label = "Name",
                        confirmText = "Confirm",
                        validation = InputValidation.Async { null },
                        onConfirm = { submitted.add(it) },
                        onDismissRequest = { if (!busy) shown = false },
                    )
                }
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("new")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { busy = false }

        compose.onNodeWithText("Confirm").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf("new"), submitted)
    }

    @Test
    fun `a new validation key discards a pending confirmation check`() {
        var key by mutableStateOf(0)
        val held = CompletableDeferred<CharSequence?>()
        var holdNext = false
        var taken = false
        val submitted = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    validation =
                        InputValidation.Async(invalidationKey = key) {
                            if (holdNext) {
                                holdNext = false
                                held.await()
                            } else if (taken) {
                                "Exists"
                            } else {
                                null
                            }
                        },
                    onConfirm = { submitted.add(it) },
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("new")
        compose.runOnIdle { holdNext = true }
        field.performImeAction()

        compose.runOnIdle {
            taken = true
            key = 1
        }
        compose.onNodeWithText("Exists").assertIsDisplayed()
        compose.runOnIdle { held.complete(null) }
        compose.waitForIdle()
        assertEquals(emptyList(), submitted)
        compose.onNodeWithText("Exists").assertIsDisplayed()
    }

    @Test
    fun `deferred validation waits for confirmation before showing async errors`() {
        val checked = mutableListOf<String>()
        val submitted = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    validation =
                        InputValidation.Async(
                            precheck = { if (it.isBlank()) "Required" else null },
                            validateOnInput = false,
                        ) { text ->
                            checked.add(text)
                            if (text == "dup") "Exists" else null
                        },
                    onConfirm = { submitted.add(it) },
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText("Confirm").assertIsEnabled()
        field.performTextReplacement("dup")
        compose.onNodeWithText("Exists").assertDoesNotExist()
        field.performTextReplacement(" ")
        assertTrue(checked.isEmpty())
        field.performImeAction()
        compose.onNodeWithText("Required").assertIsDisplayed()
        assertTrue(checked.isEmpty())

        field.performTextReplacement("dup")
        compose.onNodeWithText("Required").assertDoesNotExist()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Exists").assertIsDisplayed()
        assertEquals(listOf("dup"), checked)
        assertTrue(submitted.isEmpty())

        field.performTextReplacement("available")
        compose.onNodeWithText("Exists").assertDoesNotExist()
        assertEquals(listOf("dup"), checked)
        field.performImeAction()
        assertEquals(listOf("dup", "available"), checked)
        assertEquals(listOf("available"), submitted)
    }

    @Test
    fun `switching to no validation clears the async error`() {
        var validation by mutableStateOf<InputValidation>(InputValidation.Async { if (it == "dup") "Exists" else null })
        val submitted = mutableListOf<String>()
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    validation = validation,
                    onConfirm = { submitted.add(it) },
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("dup")
        compose.onNodeWithText("Exists").assertIsDisplayed()

        compose.runOnIdle { validation = InputValidation.None }
        compose.onNodeWithText("Exists").assertDoesNotExist()
        compose.onNodeWithText("Confirm").assertIsEnabled().performClick()
        assertEquals(listOf("dup"), submitted)
        field.performImeAction()
        assertEquals(listOf("dup", "dup"), submitted)
    }

    @Test
    fun `field semantics retain the label and editable value`() {
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename tag",
                    label = "Tag name",
                    confirmText = "Rename",
                    initialText = "History",
                    initialSelection = TextRange(1, 3),
                    validation = InputValidation.Immediate { if (it.isBlank()) "Name required" else null },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }

        val field = compose.onNode(hasSetTextAction())
        field.assertTextContains("Tag name")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("History")))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(1, 3)))
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("text_input_label", useUnmergedTree = true).assertTextEquals("Tag name")

        field.performTextReplacement("")

        field.assertTextContains("Tag name")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithText("Name required").assertIsDisplayed()
    }

    @Test
    fun `the length counter uses the given length function`() {
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Title",
                    label = "Label",
                    confirmText = "OK",
                    onConfirm = {},
                    onDismissRequest = {},
                    maxLengthCounter = 3,
                    lengthOf = { it.trim().length },
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextReplacement(" abc ")

        compose.onNodeWithText("3/3").assertIsDisplayed()
    }

    @Test
    fun `input transformation runs only for text edits`() {
        var transformations = 0
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    initialText = "Initial",
                    transformInput = {
                        transformations++
                        it.copy(text = it.text.uppercase())
                    },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInputSelection(TextRange.Zero)
        assertEquals(0, transformations)

        field.performTextReplacement("mixed")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("MIXED")))
        assertEquals(1, transformations)

        field.performTextInputSelection(TextRange(1, 3))
        assertEquals(1, transformations)
    }

    @Test
    fun `a deferred async error survives state restoration`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    validation = InputValidation.Async(validateOnInput = false) { if (it == "dup") "Exists" else null },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("dup")
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Exists").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Exists").assertIsDisplayed()
    }

    @Test
    fun `deferred validation rejects invalid submission and allows retry`() {
        var validations = 0
        var submitted: String? = null
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    initialText = "Invalid",
                    validation =
                        InputValidation.Immediate(validateOnInput = false) {
                            validations++
                            if (it == "Allowed") null else "Invalid name"
                        },
                    onConfirm = { submitted = it },
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText("Confirm").assertIsEnabled()
        compose.onNodeWithText("Invalid name").assertDoesNotExist()
        field.performTextReplacement("Still invalid")
        assertEquals(0, validations)

        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Invalid name").assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsEnabled()
        assertNull(submitted)

        val validationsAfterSubmission = validations
        field.performTextReplacement("Allowed")
        compose.onNodeWithText("Invalid name").assertDoesNotExist()
        assertEquals(validationsAfterSubmission, validations)
        field.performImeAction()
        assertEquals("Allowed", submitted)
    }

    @Test
    fun `disabled confirmation also blocks the keyboard action`() {
        var confirmations = 0
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Confirm",
                    initialText = "Valid",
                    enabled = false,
                    onConfirm = { confirmations++ },
                    onDismissRequest = {},
                )
            }
        }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(0, confirmations)
    }

    @Test
    fun `initial selection can place the cursor at the start`() {
        assertInitialSelection("Existing", TextRange.Zero)
    }

    @Test
    fun `initial selection can select all text`() {
        val text = "Existing"
        assertInitialSelection(text, TextRange(0, text.length))
    }

    @Test
    fun `the cursor starts at the end of the profile name`() {
        val name = "Work"
        compose.setContent {
            AnkiDroidTheme {
                RenameProfileDialog(currentName = name, existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
            }
        }

        val field = compose.onNode(hasSetTextAction())
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(name.length)))
    }

    @Test
    fun `edited text and cursor survive state restoration`() {
        assertSelectionRestored(TextRange(3))
    }

    @Test
    fun `edited text and selected range survive state restoration`() {
        assertSelectionRestored(TextRange(1, 5))
    }

    @Test
    fun `changing selection does not reveal validation errors before editing`() {
        val validationError = "Name already exists"
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Rename",
                    initialText = "Existing",
                    validation = InputValidation.Immediate { validationError },
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        compose.onNodeWithText(validationError).assertDoesNotExist()

        for (selection in listOf(TextRange(2), TextRange(1, 4))) {
            field.performTextInputSelection(selection)
            field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
            compose.onNodeWithText(validationError).assertDoesNotExist()
        }

        field.performTextReplacement("Changed")
        compose.onNodeWithText(validationError).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h320dp-port")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `large text can scroll the input without losing the action buttons`() {
        RuntimeEnvironment.setFontScale(2f)
        var confirmed = false
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename tag",
                    description = "Source tag: parent::Science",
                    label = "Tag name",
                    confirmText = "Rename",
                    initialText = "History",
                    onConfirm = { confirmed = true },
                    onDismissRequest = {},
                    validation = InputValidation.Immediate(validateOnInput = false) { null },
                )
            }
        }
        val move = compose.onNodeWithText("Rename").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        val originalBounds = move.fetchSemanticsNode().boundsInWindow
        compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
        assertEquals(originalBounds, move.fetchSemanticsNode().boundsInWindow)
        move.performTouchInput { click() }
        assertTrue(confirmed)
    }

    @Test
    fun `dialog opening and closing fade without moving the card`() {
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(title = "Rename tag", label = "Tag name", confirmText = "OK", onConfirm = {}, onDismissRequest = {})
            }
        }
        compose.waitForIdle()
        val window = requireNotNull(ShadowDialog.getLatestDialog().window)
        val animations =
            targetContext.obtainStyledAttributes(
                window.attributes.windowAnimations,
                intArrayOf(
                    android.R.attr.windowEnterAnimation,
                    android.R.attr.windowExitAnimation,
                ),
            )
        try {
            for (index in 0..1) {
                val animation = AnimationUtils.loadAnimation(targetContext, animations.getResourceId(index, 0))
                animation.initialize(320, 240, 320, 640)
                animation.startTime = 0
                val frame = Transformation()
                for (time in listOf(0L, animation.duration / 2, animation.duration)) {
                    animation.getTransformation(time, frame)
                    assertTrue(frame.matrix.isIdentity, "Dialog moved at $time ms")
                    if (time == 0L) assertEquals(if (index == 0) 0f else 1f, frame.alpha)
                    if (time == animation.duration) assertEquals(if (index == 0) 1f else 0f, frame.alpha)
                    frame.clear()
                }
            }
        } finally {
            animations.recycle()
        }
    }

    private fun assertInitialSelection(
        text: String,
        selection: TextRange,
    ) {
        compose.setContent {
            AnkiDroidTheme {
                TextInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmText = "Rename",
                    initialText = text,
                    initialSelection = selection,
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }

    private fun assertSelectionRestored(selection: TextRange) {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AnkiDroidTheme {
                RenameProfileDialog(currentName = "Work", existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("Personal")
        field.performTextInputSelection(selection)

        restoration.emulateSavedInstanceStateRestore()

        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Personal")))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }
}
