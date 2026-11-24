// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
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
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { view, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout())
            view.updatePadding(left = constraints.left, right = constraints.right, top = constraints.top)
            // don't mark as CONSUMED, it breaks the insets for the next view on lower API versions
            insets
        }
        // handle the content, adds the ime in the constraints so the user can also scroll the
        // content while keyboard is on
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentScroller) { view, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout() or ime())
            view.updatePadding(left = constraints.left, right = constraints.right, bottom = constraints.bottom)
            insets
        }
        when {
            savedInstanceState != null ->
                initializeUi(savedInstanceState.getString(STATE_QUESTION_FORMAT), savedInstanceState.getString(STATE_ANSWER_FORMAT))
            intent.extras != null ->
                initializeUi(intent.getStringExtra(EXTRA_QUESTION_FORMAT), intent.getStringExtra(EXTRA_ANSWER_FORMAT))
            else -> {
                showThemedToast(this, getString(CommonString.something_wrong), true)
                finish()
                return
            }
        }
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
            positiveButton(CommonString.restore) {
                restoreDefaultAndClose()
            }
            negativeButton(CommonString.dialog_cancel)
            message(CommonString.card_template_browser_appearance_restore_default_dialog)
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_QUESTION_FORMAT, questionFormat)
        outState.putString(STATE_ANSWER_FORMAT, answerFormat)
        super.onSaveInstanceState(outState)
    }

    private fun initializeUi(
        questionFormat: String?,
        answerFormat: String?,
    ) {
        binding.questionFormat.setText(questionFormat)
        binding.answerFormat.setText(answerFormat)

        discardChangesCallback.isEnabled = hasChanges()

        enableToolbar()
        title = TR.sentenceCase.browserAppearance
    }

    private fun answerHasChanged(intent: Intent): Boolean = intent.getStringExtra(EXTRA_ANSWER_FORMAT) != answerFormat

    private fun questionHasChanged(intent: Intent): Boolean = intent.getStringExtra(EXTRA_QUESTION_FORMAT) != questionFormat

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
                putExtra(EXTRA_QUESTION_FORMAT, questionFormat)
                putExtra(EXTRA_ANSWER_FORMAT, answerFormat)
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
                        val question = intent.getStringExtra(EXTRA_QUESTION_FORMAT)
                        val answer = intent.getStringExtra(EXTRA_ANSWER_FORMAT)
                        Result(question, answer)
                    } catch (e: Exception) {
                        Timber.w(e, "Could not read result from intent")
                        null
                    }
                }
        }
    }

    companion object {
        const val EXTRA_QUESTION_FORMAT = "bqfmt"
        const val EXTRA_ANSWER_FORMAT = "bafmt"
        private const val STATE_QUESTION_FORMAT = "bqfmt"
        private const val STATE_ANSWER_FORMAT = "bafmt"

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
                putExtra(EXTRA_QUESTION_FORMAT, questionFormat)
                putExtra(EXTRA_ANSWER_FORMAT, answerFormat)
            }
    }
}
