/*
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
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

package com.ichi2.anki.dialogs

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.getInputField
import com.ichi2.utils.getInputTextLayout
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import timber.log.Timber

/**
 * A dialog which manages the creation of decks, subdecks and filtered decks.
 *
 * Also used for deck renames: [DeckDialogType.RENAME_DECK]
 *
 * required property: [onNewDeckCreated]. Called on successful creation of a deck
 */
class CreateDeckDialog(
    private val context: Context,
    private val title: Int,
    private val deckDialogType: DeckDialogType,
    private val parentId: DeckId?,
) {
    private var previousDeckName: String? = null
    lateinit var onNewDeckCreated: ((DeckId) -> Unit)
    private var initialDeckName = ""
    private var shownDialog: AlertDialog? = null

    enum class DeckDialogType {
        DECK,
        SUB_DECK,
        RENAME_DECK,
    }

    private val getColUnsafe
        get() = CollectionManager.getColUnsafe()

    /** Used for rename  */
    var deckName: String
        get() = shownDialog!!.getInputField().text.toString()
        set(deckName) {
            previousDeckName = deckName
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
                    title(title)
                    // Resource ID for the dialog's positive action button text.
                    // Uses "Rename" for rename deck dialogs and "Create" for all other deck-related dialogs.
                    val positiveButtonTextRes =
                        when (deckDialogType) {
                            DeckDialogType.RENAME_DECK -> R.string.rename

                            DeckDialogType.DECK,
                            DeckDialogType.SUB_DECK,
                            -> R.string.dialog_positive_create
                        }
                    positiveButton(positiveButtonTextRes) {
                        onPositiveButtonClicked()
                    }
                    negativeButton(R.string.dialog_cancel)
                    setView(R.layout.dialog_generic_text_input)
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
                    // we need the fully-qualified name for subdecks
                    val maybeDeckName = fullyQualifyDeckName(dialogText = text)
                    // if the name is empty, it seems distracting to show an error
                    if (maybeDeckName == null || !Decks.isValidDeckName(maybeDeckName)) {
                        dialog.positiveButton.isEnabled = false
                        return@input
                    }
                    if (!maybeDeckName.equals(initialDeckName, ignoreCase = true) && deckExists(getColUnsafe, maybeDeckName)) {
                        dialog.getInputTextLayout().error = context.getString(R.string.error_name_exists)
                        dialog.positiveButton.isEnabled = false
                        return@input
                    }
                    dialog.getInputTextLayout().error = null
                    dialog.positiveButton.isEnabled = true

                    // Users expect the ordering [1, 2, 10], but get [1, 10, 2]
                    // To fix: they need [01, 02, 10]. Show a hint to help them
                    dialog.getInputTextLayout().helperText =
                        if (text.containsNumberLargerThanNine()) {
                            context.getString(R.string.create_deck_numeric_hint)
                        } else {
                            null
                        }
                }
        shownDialog = dialog
        return dialog
    }

    /**
     * @return true if the collection contains a deck with the given name
     */
    private fun deckExists(
        col: Collection,
        name: String,
    ): Boolean = col.decks.byName(name) != null

    /**
     * Returns the fully qualified deck name for the provided input
     * @param dialogText The user supplied text in the dialog
     * @return [dialogText], or the deck name containing `::` in case of [DeckDialogType.SUB_DECK]
     */
    private fun fullyQualifyDeckName(dialogText: CharSequence) =
        when (deckDialogType) {
            DeckDialogType.DECK, DeckDialogType.RENAME_DECK -> dialogText.toString()
            DeckDialogType.SUB_DECK -> getColUnsafe.decks.getSubdeckName(parentId!!, dialogText.toString())
        }

    fun createSubDeck(
        did: DeckId,
        deckName: String?,
    ) {
        val deckNameWithParentName = getColUnsafe.decks.getSubdeckName(did, deckName)
        createDeck(deckNameWithParentName!!)
    }

    fun createDeck(deckName: String) {
        if (Decks.isValidDeckName(deckName)) {
            createNewDeck(deckName)
            // 11668: Display feedback if a deck is created
            displayFeedback(context.getString(R.string.deck_created))
        } else {
            Timber.d("CreateDeckDialog::createDeck - Not creating invalid deck name '%s'", deckName)
            displayFeedback(context.getString(R.string.invalid_deck_name), Snackbar.LENGTH_LONG)
        }
        // AlertDialog should be dismissed after the Keyboard 'Done' or Deck 'Ok' button is pressed
        shownDialog?.dismiss()
    }

    private fun createNewDeck(deckName: String): Boolean {
        try {
            // create normal deck or sub deck
            Timber.i("CreateDeckDialog::createNewDeck")
            val newDeckId = getColUnsafe.decks.id(deckName)
            Timber.d("Created deck '%s'; id: %d", deckName, newDeckId)
            onNewDeckCreated(newDeckId)
        } catch (filteredAncestor: BackendDeckIsFilteredException) {
            Timber.w(filteredAncestor)
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
            displayFeedback(context.getString(R.string.invalid_deck_name), Snackbar.LENGTH_LONG)
        } else if (newDeckName != previousDeckName) {
            try {
                val decks = getColUnsafe.decks
                val deckId = decks.id(previousDeckName!!)
                decks.rename(decks.getLegacy(deckId)!!, newDeckName)
                onNewDeckCreated(deckId)
                // 11668: Display feedback if a deck is renamed
                displayFeedback(context.getString(R.string.deck_renamed))
            } catch (e: BackendDeckIsFilteredException) {
                Timber.w(e)
                // We get a localized string from libanki to explain the error
                displayFeedback(e.localizedMessage ?: e.message ?: "", Snackbar.LENGTH_LONG)
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
