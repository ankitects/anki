/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
 *  Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.widget.cardanalysis

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.os.BundleCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.utils.ext.unregisterReceiverSilently
import com.ichi2.anki.databinding.ActivityCardAnalysisWidgetConfigBinding
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.withProgress
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.cardanalysis.CardAnalysisWidget.Companion.EXTRA_SELECTED_DECK_ID
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Configuration activity for [CardAnalysisWidget]. Only allows selecting a deck.
 *
 * Behavior:
 *  - shows a single centered card with the selected deck name(if any) and a button to trigger the
 *    deck selection dialog
 *  - when the user first adds the widget this activity will start with the deck selection dialog
 *    already open, if there is a deck selected then, the activity will start without the selection
 *    dialog
 *  - storing the user selection is done automatically on every deck change
 *  - handles user not selecting anything(widget also handles this state)
 *  - finishes immediately when the collection is empty and shows a toast('Collection is empty')
 *  - shows loading state if querying the collection takes time
 *
 * @see CardAnalysisWidget
 * @see CardAnalysisWidgetPreferences
 */
class CardAnalysisWidgetConfig : AnkiActivity(R.layout.activity_card_analysis_widget_config) {
    private val binding by viewBinding(ActivityCardAnalysisWidgetConfigBinding::bind)

    private var appWidgetId = INVALID_APPWIDGET_ID
    private var deck: SelectableDeck.Deck? = null
    private lateinit var preferences: CardAnalysisWidgetPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)

        if (!ensureStoragePermissions()) {
            return
        }

        preferences = CardAnalysisWidgetPreferences(this)
        appWidgetId = intent.getAppWidgetId()
        if (appWidgetId == INVALID_APPWIDGET_ID) {
            Timber.v("Invalid App Widget ID")
            finish()
            return
        }
        if (savedInstanceState != null) {
            deck =
                BundleCompat.getParcelable(
                    savedInstanceState,
                    KEY_DECK,
                    SelectableDeck.Deck::class.java,
                )
            binding.deckName.text = deck?.name
        } else {
            loadContent()
        }
        binding.changeBtn.setOnClickListener {
            launchCatchingTask {
                withProgress { showDeckSelectionDialog() }
            }
        }
        binding.doneBtn.setOnClickListener { close() }
        registerReceiver(
            widgetRemovedReceiver,
            IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED),
        )
        registerDeckSelectedHandler(action = ::onDeckSelected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_DECK, deck)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSilently(widgetRemovedReceiver)
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null || deck !is SelectableDeck.Deck?) {
            showThemedToast(this, R.string.something_wrong, false)
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        // if the deck was null before the selection then the widget was just added so update the
        // widget and finish
        val shouldClose = this.deck == null
        this.deck = deck
        binding.deckName.text = deck.name
        preferences.saveSelectedDeck(appWidgetId, deck.deckId)
        updateWidget()
        if (shouldClose) {
            close()
        }
    }

    private fun loadContent() {
        launchCatchingTask {
            withProgress {
                if (isCollectionEmpty()) {
                    Timber.w("CardAnalysisWidgetConfig: collection is empty")
                    showThemedToast(
                        this@CardAnalysisWidgetConfig,
                        R.string.no_cards_placeholder_title,
                        false,
                    )
                    finish()
                    return@withProgress
                }
                val selectedDeckId = preferences.getSelectedDeckIdFromPreferences(appWidgetId)
                if (selectedDeckId == null) {
                    showDeckSelectionDialog()
                } else {
                    deck = SelectableDeck.Deck.fromId(selectedDeckId)
                    binding.deckName.text = deck?.name ?: getString(R.string.select_deck)
                }
            }
        }
    }

    private suspend fun showDeckSelectionDialog() {
        val decks = SelectableDeck.fromCollection(includeFiltered = true)
        val dialog =
            DeckSelectionDialog.newInstance(
                title = getString(R.string.select_deck_title),
                decks = decks,
            )
        if (!supportFragmentManager.isStateSaved) {
            dialog.show(supportFragmentManager, "DeckSelectionDialog")
        }
    }

    private fun updateWidget() {
        val updateIntent =
            Intent(this, CardAnalysisWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId.id))
                putExtra(EXTRA_SELECTED_DECK_ID, deck?.deckId)
            }

        sendBroadcast(updateIntent)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        CardAnalysisWidget.updateWidget(this, appWidgetManager, appWidgetId)
    }

    private fun close() {
        val intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)
        setResult(RESULT_OK, intent)
        finish()
    }

    /** BroadcastReceiver to handle widget removal. */
    private val widgetRemovedReceiver =
        object : AnkiBroadcastReceiver() {
            override fun onReceiveBroadcast(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != AppWidgetManager.ACTION_APPWIDGET_DELETED) {
                    return
                }

                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId == INVALID_APPWIDGET_ID) {
                    return
                }

                preferences.deleteDeckData(appWidgetId)
            }
        }

    companion object {
        private const val KEY_DECK = "key_deck"
    }
}
