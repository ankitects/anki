/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.databinding.ActivityCardBrowserAppearanceBinding
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.libanki.CardTemplate
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import dev.androidbroadcast.vbpd.viewBinding
import org.jetbrains.annotations.Contract
import timber.log.Timber

/** Allows specification of the Question and Answer format of a card template in the Card Browser
 * This is known as "Browser Appearance" in Anki
 * We do not allow the user to change fonts as Android only has a handful
 * We do not allow the user to change the font size as this can be done in the Appearance settings.
 */
class CardTemplateBrowserAppearanceEditor : AnkiActivity(R.layout.activity_card_browser_appearance) {
    private val binding by viewBinding(ActivityCardBrowserAppearanceBinding::bind)

    // start with the callback disabled as there aren't any changes yet
    private val discardChangesCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showDiscardChangesDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        val bundle = savedInstanceState ?: intent.extras
        if (bundle == null) {
            showThemedToast(this, getString(R.string.something_wrong), true)
            finish()
            return
        }
        initializeUiFromBundle(bundle)
        // default result, only changed to RESULT_OK if actually saving changes
        setResult(RESULT_CANCELED)
        onBackPressedDispatcher.addCallback(discardChangesCallback)
        binding.questionFormat.doAfterTextChanged { _ ->
            discardChangesCallback.isEnabled = hasChanges()
        }
        binding.answerFormat.doAfterTextChanged { _ ->
            discardChangesCallback.isEnabled = hasChanges()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.card_template_browser_appearance_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_confirm -> {
                Timber.i("Save pressed")
                saveAndExit()
                return true
            }
            R.id.action_restore_default -> {
                Timber.i("Restore Default pressed")
                showRestoreDefaultDialog()
                return true
            }
            android.R.id.home -> {
                Timber.i("Back Pressed")
                if (hasChanges()) {
                    showDiscardChangesDialog()
                } else {
                    finish() // the result was already set to RESULT_CANCELLED
                }
                return true
            }
            else -> {}
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDiscardChangesDialog() {
        DiscardChangesDialog.showDialog(this) {
            Timber.i("Changes discarded, finishing...")
            finish()
        }
    }

    private fun showRestoreDefaultDialog() {
        AlertDialog.Builder(this).show {
            setTitle(TR.sentenceCase.restoreToDefault)
            positiveButton(R.string.restore) {
                restoreDefaultAndClose()
            }
            negativeButton(R.string.dialog_cancel)
            message(R.string.card_template_browser_appearance_restore_default_dialog)
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(INTENT_QUESTION_FORMAT, questionFormat)
        outState.putString(INTENT_ANSWER_FORMAT, answerFormat)
        super.onSaveInstanceState(outState)
    }

    private fun initializeUiFromBundle(bundle: Bundle) {
        binding.questionFormat.setText(bundle.getString(INTENT_QUESTION_FORMAT))
        binding.answerFormat.setText(bundle.getString(INTENT_ANSWER_FORMAT))

        discardChangesCallback.isEnabled = hasChanges()

        enableToolbar()
        title = TR.sentenceCase.browserAppearance
    }

    private fun answerHasChanged(intent: Intent): Boolean = intent.getStringExtra(INTENT_ANSWER_FORMAT) != answerFormat

    private fun questionHasChanged(intent: Intent): Boolean = intent.getStringExtra(INTENT_QUESTION_FORMAT) != questionFormat

    private val questionFormat: String
        get() = getTextValue(binding.questionFormat)
    private val answerFormat: String
        get() = getTextValue(binding.answerFormat)

    private fun getTextValue(editText: EditText): String = editText.text.toString()

    private fun restoreDefaultAndClose() {
        Timber.i("Restoring Default and Closing")
        binding.questionFormat.setText(VALUE_USE_DEFAULT)
        binding.answerFormat.setText(VALUE_USE_DEFAULT)
        saveAndExit()
    }

    private fun saveAndExit() {
        Timber.i("Save and Exit")
        val data =
            Intent().apply {
                putExtra(INTENT_QUESTION_FORMAT, questionFormat)
                putExtra(INTENT_ANSWER_FORMAT, answerFormat)
            }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun hasChanges(): Boolean =
        try {
            questionHasChanged(intent) || answerHasChanged(intent)
        } catch (e: Exception) {
            Timber.w(e, "Failed to detect changes. Assuming true")
            true
        }

    class Result private constructor(
        question: String?,
        answer: String?,
    ) {
        val question: String = question ?: VALUE_USE_DEFAULT
        val answer: String = answer ?: VALUE_USE_DEFAULT

        fun applyTo(template: CardTemplate) {
            template.bqfmt = question
            template.bafmt = answer
        }

        companion object {
            @Contract("null -> null")
            fun fromIntent(intent: Intent?): Result? =
                if (intent == null) {
                    null
                } else {
                    try {
                        val question = intent.getStringExtra(INTENT_QUESTION_FORMAT)
                        val answer = intent.getStringExtra(INTENT_ANSWER_FORMAT)
                        Result(question, answer)
                    } catch (e: Exception) {
                        Timber.w(e, "Could not read result from intent")
                        null
                    }
                }
        }
    }

    companion object {
        const val INTENT_QUESTION_FORMAT = "bqfmt"
        const val INTENT_ANSWER_FORMAT = "bafmt"

        /** Specified the card browser should use the default template formatter  */
        const val VALUE_USE_DEFAULT = ""

        @CheckResult
        fun getIntentFromTemplate(
            context: Context,
            template: CardTemplate,
        ): Intent = getIntent(context, template.bqfmt, template.bafmt)

        @CheckResult
        fun getIntent(
            context: Context,
            questionFormat: String,
            answerFormat: String,
        ): Intent =
            Intent(context, CardTemplateBrowserAppearanceEditor::class.java).apply {
                putExtra(INTENT_QUESTION_FORMAT, questionFormat)
                putExtra(INTENT_ANSWER_FORMAT, answerFormat)
            }
    }
}
