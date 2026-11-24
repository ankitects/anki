// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.findActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.getInputField
import com.ichi2.utils.getInputTextLayout
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import kotlinx.coroutines.Job
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

/**
 * A dialog which manages the creation of decks, subdecks and filtered decks.
 *
 * Also used for deck renames: [DeckDialogType.RENAME_DECK]
 *
 * required property: [onNewDeckCreated]. Called on successful creation of a deck
 */
class CreateDeckDialog(
    private val context: Context,
    private val title: CharSequence,
    private val deckDialogType: DeckDialogType,
    private val parentId: DeckId?,
    /** The deck to rename. Only used by [DeckDialogType.RENAME_DECK] */
    private val renamedDeckId: DeckId? = null,
) {
    lateinit var onNewDeckCreated: ((DeckId) -> Unit)
    private var initialDeckName = ""
    private var shownDialog: AlertDialog? = null

    init {
        require(deckDialogType != DeckDialogType.RENAME_DECK || renamedDeckId != null) {
            "renamedDeckId is required to rename a deck"
        }
    }

    enum class DeckDialogType {
        DECK,
        SUB_DECK,
        RENAME_DECK,
    }

    private val activity: FragmentActivity
        get() = requireNotNull(context.findActivity<FragmentActivity>())

    /** The job running [validateDeckName] for the latest input */
    private var validation: Job? = null

    /** Used for rename  */
    var deckName: String
        get() = shownDialog!!.getInputField().text.toString()
        set(deckName) {
            initialDeckName = deckName
        }

    fun showDialog(): AlertDialog {
        val textInputHint =
            when (deckDialogType) {
                DeckDialogType.RENAME_DECK -> TR.actionsNewName().dropLastWhile { it == ':' }

                DeckDialogType.DECK,
                DeckDialogType.SUB_DECK,
                -> TR.actionsName().dropLastWhile { it == ':' }
            }
        val dialog =
            AlertDialog
                .Builder(context)
                .show {
                    setTitle(title)
                    // Resource ID for the dialog's positive action button text.
                    // Uses "Rename" for rename deck dialogs and "Create" for all other deck-related dialogs.
                    val positiveButtonTextRes =
                        when (deckDialogType) {
                            DeckDialogType.RENAME_DECK -> CommonString.rename

                            DeckDialogType.DECK,
                            DeckDialogType.SUB_DECK,
                            -> CommonString.dialog_positive_create
                        }
                    positiveButton(positiveButtonTextRes) {
                        onPositiveButtonClicked()
                    }
                    negativeButton(CommonString.dialog_cancel)
                    setView(CommonR.layout.dialog_generic_text_input)
                }.input(
                    hint = textInputHint,
                    prefill = initialDeckName,
                    displayKeyboard = true,
                    waitForPositiveButton = false,
                ) { dialog, text ->

                    // defining the action of done button in ImeKeyBoard and enter button in physical keyBoard
                    val inputField = dialog.getInputField()
                    inputField.setOnEditorActionListener { _, actionId, event ->
                        if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                            if (dialog.positiveButton.isEnabled) {
                                onPositiveButtonClicked()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    validation?.cancel()
                    dialog.positiveButton.isEnabled = false
                    validation = activity.launchCatchingTask { validateDeckName(dialog, text) }
                }
        shownDialog = dialog
        return dialog
    }

    private suspend fun validateDeckName(
        dialog: AlertDialog,
        text: CharSequence,
    ) {
        // we need the fully-qualified name for subdecks
        val maybeDeckName = fullyQualifyDeckName(dialogText = text)
        // if the name is empty, it seems distracting to show an error
        if (maybeDeckName == null || !Decks.isValidDeckName(maybeDeckName)) {
            dialog.positiveButton.isEnabled = false
            return
        }
        val existingDeckId = withCol { decks.idForName(maybeDeckName) }
        if (!dialog.getInputField().text.contentEquals(text)) return
        if (existingDeckId != null && existingDeckId != renamedDeckId) {
            dialog.getInputTextLayout().error = context.getString(CommonString.error_name_exists)
            dialog.positiveButton.isEnabled = false
            return
        }
        dialog.getInputTextLayout().error = null
        dialog.positiveButton.isEnabled = true

        // Users expect the ordering [1, 2, 10], but get [1, 10, 2]
        // To fix: they need [01, 02, 10]. Show a hint to help them
        dialog.getInputTextLayout().helperText =
            if (text.containsNumberLargerThanNine()) {
                context.getString(CommonString.create_deck_numeric_hint)
            } else {
                null
            }
    }

    /**
     * Returns the fully qualified deck name for the provided input
     * @param dialogText The user supplied text in the dialog
     * @return [dialogText], or the deck name containing `::` in case of [DeckDialogType.SUB_DECK]
     */
    private suspend fun fullyQualifyDeckName(dialogText: CharSequence) =
        when (deckDialogType) {
            DeckDialogType.DECK, DeckDialogType.RENAME_DECK -> dialogText.toString()
            DeckDialogType.SUB_DECK -> withCol { decks.getSubdeckName(parentId!!, dialogText.toString()) }
        }

    fun createSubDeck(
        did: DeckId,
        deckName: String?,
    ) {
        shownDialog?.dismiss()
        activity.launchCatchingTask {
            val deckNameWithParentName = withCol { decks.getSubdeckName(did, deckName) }
            if (deckNameWithParentName == null) {
                Timber.d("CreateDeckDialog::createSubDeck - Not creating invalid subdeck name '%s'", deckName)
                displayFeedback(context.getString(CommonString.invalid_deck_name), Snackbar.LENGTH_LONG)
                return@launchCatchingTask
            }
            createDeck(deckNameWithParentName)
        }
    }

    fun createDeck(deckName: String) {
        if (Decks.isValidDeckName(deckName)) {
            activity.launchCatchingTask {
                if (createNewDeck(deckName)) {
                    // 11668: Display feedback if a deck is created
                    displayFeedback(context.getString(CommonString.deck_created))
                }
            }
        } else {
            Timber.d("CreateDeckDialog::createDeck - Not creating invalid deck name '%s'", deckName)
            displayFeedback(context.getString(CommonString.invalid_deck_name), Snackbar.LENGTH_LONG)
        }
        // AlertDialog should be dismissed after the Keyboard 'Done' or Deck 'Ok' button is pressed
        shownDialog?.dismiss()
    }

    private suspend fun createNewDeck(deckName: String): Boolean {
        try {
            // create normal deck or sub deck
            Timber.i("CreateDeckDialog::createNewDeck")
            val newDeckId = withCol { decks.id(deckName) }
            Timber.d("Created deck '%s'; id: %d", deckName, newDeckId)
            onNewDeckCreated(newDeckId)
        } catch (filteredAncestor: BackendDeckIsFilteredException) {
            Timber.w(filteredAncestor)
            displayFeedback(filteredAncestor.localizedMessage ?: filteredAncestor.message ?: "", Snackbar.LENGTH_LONG)
            return false
        }
        return true
    }

    private fun onPositiveButtonClicked() {
        if (deckName.isNotEmpty()) {
            when (deckDialogType) {
                DeckDialogType.DECK -> {
                    // create deck
                    createDeck(deckName)
                }
                DeckDialogType.RENAME_DECK -> {
                    renameDeck(deckName)
                }
                DeckDialogType.SUB_DECK -> {
                    // create sub deck
                    createSubDeck(parentId!!, deckName)
                }
            }
        }
    }

    fun renameDeck(newDeckName: String) {
        if (!Decks.isValidDeckName(newDeckName)) {
            Timber.w("CreateDeckDialog::renameDeck not renaming deck to invalid name")
            Timber.d("invalid deck name: %s", newDeckName)
            displayFeedback(context.getString(CommonString.invalid_deck_name), Snackbar.LENGTH_LONG)
        } else {
            val deckId = renamedDeckId!!
            activity.launchCatchingTask {
                try {
                    // the backend reports no change if the new name normalises to the old one
                    if (withCol { decks.rename(deckId, newDeckName) }.deck) {
                        onNewDeckCreated(deckId)
                        // 11668: Display feedback if a deck is renamed
                        displayFeedback(context.getString(CommonString.deck_renamed))
                    }
                } catch (e: BackendDeckIsFilteredException) {
                    Timber.w(e)
                    // We get a localized string from libanki to explain the error
                    displayFeedback(e.localizedMessage ?: e.message ?: "", Snackbar.LENGTH_LONG)
                } catch (e: BackendNotFoundException) {
                    Timber.w(e, "not renaming deck %d", deckId)
                }
            }
        }
        // AlertDialog should be dismissed after the Keyboard 'Done' or Deck 'Ok' button is pressed
        shownDialog?.dismiss()
    }

    private fun displayFeedback(
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT,
    ) {
        if (context is Activity) {
            context.showSnackbar(message, duration)
        } else {
            showThemedToast(context, message, duration == Snackbar.LENGTH_SHORT)
        }
    }
}

// to not match times. Example: "12:34:56"
// we use (?:[^:]|^) to ensure ":56" doesn't match
// we use (?:[^:]|$) to ensure "12:" doesn't match
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun CharSequence.containsNumberLargerThanNine(): Boolean = Regex("""(?:[^:]|^)[1-9]\d+(?:[^:]|$)""").find(this) != null
