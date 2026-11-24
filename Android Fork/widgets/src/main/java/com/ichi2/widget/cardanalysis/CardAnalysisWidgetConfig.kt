// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget.cardanalysis

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateMargins
import com.ichi2.anki.BaseAnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.unregisterReceiverSilently
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.widgets.R
import com.ichi2.anki.widgets.databinding.ActivityCardAnalysisWidgetConfigBinding
import com.ichi2.anki.withProgress
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.cardanalysis.CardAnalysisWidget.Companion.EXTRA_SELECTED_DECK_ID
import com.ichi2.widget.getAppWidgetManager
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
class CardAnalysisWidgetConfig : BaseAnkiActivity(R.layout.activity_card_analysis_widget_config) {
    private val binding by viewBinding(ActivityCardAnalysisWidgetConfigBinding::bind)

    private var appWidgetId = INVALID_APPWIDGET_ID
    private var deck: SelectableDeck.Deck? = null
    private lateinit var preferences: CardAnalysisWidgetPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)

        if (!ensureStorageIsReady()) {
            return
        }
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { _, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout())
            val params = binding.content.layoutParams as ViewGroup.MarginLayoutParams
            params.updateMargins(left = constraints.left, right = constraints.right, bottom = constraints.bottom)
            WindowInsetsCompat.CONSUMED
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
                    STATE_DECK,
                    SelectableDeck.Deck::class.java,
                )
            binding.deckName.text = deck?.name
        } else {
            loadContent()
        }
        binding.changeBtn.text = TR.sentenceCase.selectDeck
        binding.changeBtn.setOnClickListener { showDeckSelectionDialog() }
        binding.doneBtn.setOnClickListener { close() }
        registerReceiver(
            widgetRemovedReceiver,
            IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED),
        )
        registerDeckSelectedHandler(action = ::onDeckSelected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_DECK, deck)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSilently(widgetRemovedReceiver)
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null || deck !is SelectableDeck.Deck?) {
            showThemedToast(this, CommonString.something_wrong, false)
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
                        CommonString.no_cards_placeholder_title,
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
                    binding.deckName.text = deck?.name ?: TR.sentenceCase.selectDeck
                }
            }
        }
    }

    private fun showDeckSelectionDialog() {
        startDeckSelection(
            title = getString(CommonString.select_deck_title),
            allowAll = false,
            skipEmptyDefault = true,
        )
    }

    private fun updateWidget() {
        val appWidgetManager = getAppWidgetManager(this) ?: return
        val updateIntent =
            Intent(this, CardAnalysisWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId.id))
                putExtra(EXTRA_SELECTED_DECK_ID, deck?.deckId)
            }

        sendBroadcast(updateIntent)

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
        private const val STATE_DECK = "key_deck"
    }
}
