// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.os.LocaleList
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.ext.createInputConnection
import com.ichi2.testutils.targetContext
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.robolectric.ParameterizedRobolectricTestRunner as Parameterized

@RunWith(Parameterized::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class TypeAnswerEditTextTest : AndroidTest {
    enum class AnswerField {
        FIXED_EDIT_TEXT,
        TYPE_ANSWER_EDIT_TEXT,
    }

    @JvmField // required for Parameter
    @Parameterized.Parameter
    var answerField = AnswerField.FIXED_EDIT_TEXT

    private lateinit var field: EditText

    @Before
    fun setUpAnswerField() {
        targetContext.setTheme(DayTheme.LIGHT.styleResId)
        field =
            when (answerField) {
                AnswerField.FIXED_EDIT_TEXT -> FixedEditText(targetContext)
                AnswerField.TYPE_ANSWER_EDIT_TEXT -> TypeAnswerEditText(targetContext, null)
            }.apply {
                inputType = INPUT_TYPE
                imeOptions = EditorInfo.IME_ACTION_DONE
                imeHintLocales = LocaleList.forLanguageTags("fr")
                setNoSuggest(true)
            }
    }

    @Test
    fun `nosuggest preserves the editor and keyboard metadata`() {
        val info = EditorInfo()
        field.createInputConnection(info)

        // TYPE_NULL on the view itself makes this false, suppressing the cursor and preventing
        // the keyboard from reopening after a tap or returning from the keyboard picker.
        assertTrue(field.onCheckIsTextEditor())
        assertEquals(INPUT_TYPE, field.inputType)
        assertEquals(InputType.TYPE_NULL, info.inputType)
        assertEquals(EditorInfo.IME_ACTION_DONE, info.imeOptions and EditorInfo.IME_MASK_ACTION)
        assertEquals(LocaleList.forLanguageTags("fr"), info.hintLocales)
    }

    @Test
    fun `nosuggest accepts accented text from the keyboard`() {
        field.createInputConnection().commitText("été", 1)

        assertEquals("été", field.text.toString())
    }

    @Test
    fun `nosuggest delivers the Done action`() {
        var receivedAction: Int? = null
        field.setOnEditorActionListener { _, actionId, _ ->
            receivedAction = actionId
            true
        }

        field.createInputConnection().performEditorAction(EditorInfo.IME_ACTION_DONE)

        assertEquals(EditorInfo.IME_ACTION_DONE, receivedAction)
    }

    @Test
    fun `disabling nosuggest restores keyboard metadata without losing text or selection`() {
        field.setText("été")
        field.setSelection(1)
        field.createInputConnection()

        field.setNoSuggest(false)
        val info = EditorInfo()
        field.createInputConnection(info)

        assertEquals(INPUT_TYPE, info.inputType)
        assertEquals("été", field.text.toString())
        assertEquals(1, field.selectionStart)
        assertEquals(1, info.initialSelStart)
    }

    private fun EditText.setNoSuggest(enabled: Boolean) {
        when (this) {
            is FixedEditText -> noSuggest = enabled
            is TypeAnswerEditText -> noSuggest = enabled
            else -> error("Unsupported answer field: $this")
        }
    }

    companion object {
        private const val INPUT_TYPE = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic // required for Parameters
        fun answerFields(): Collection<AnswerField> = AnswerField.entries
    }
}
