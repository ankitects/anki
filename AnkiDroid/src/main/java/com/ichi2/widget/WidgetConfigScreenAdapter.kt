// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.ext.indexOfOrNull
import com.ichi2.anki.databinding.ItemWidgetDeckConfigBinding
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter class for displaying and managing a list of selectable decks in a RecyclerView.
 *
 * @property coroutineScope lifecycle-bound scope used for deck name lookups
 * @property onDeleteDeck a function to call when a deck is removed
 */
class WidgetConfigScreenAdapter(
    private val coroutineScope: CoroutineScope,
    private val onDeleteDeck: (SelectableDeck.Deck, Int) -> Unit,
) : RecyclerView.Adapter<WidgetConfigScreenAdapter.DeckViewHolder>() {
    private val decks: MutableList<SelectableDeck.Deck> = mutableListOf()

    // Property to get the list of deck IDs
    val deckIds: List<Long> get() = decks.map { it.deckId }

    class DeckViewHolder(
        val binding: ItemWidgetDeckConfigBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    /** Creates and inflates the view for each item in the RecyclerView
     * @param parent the parent ViewGroup
     * @param viewType the type of the view
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DeckViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return DeckViewHolder(ItemWidgetDeckConfigBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(
        holder: DeckViewHolder,
        position: Int,
    ) {
        val deck = decks[position]

        coroutineScope.launch {
            val deckName =
                withContext(Dispatchers.IO) {
                    withCol { decks.getLegacy(deck.deckId)!!.name }
                }
            holder.binding.deckNameTextView.text = deckName
        }

        holder.binding.removeDeckButton.setOnClickListener {
            onDeleteDeck(deck, position)
        }
    }

    override fun getItemCount(): Int = decks.size

    fun addDeck(deck: SelectableDeck.Deck) {
        decks.add(deck)
        notifyItemInserted(decks.size - 1)
    }

    fun removeDeck(deckId: DeckId) {
        val position = decks.indexOfOrNull { it.deckId == deckId } ?: return
        decks.removeAt(position)
        notifyItemRemoved(position)
    }

    fun moveDeck(
        fromPosition: Int,
        toPosition: Int,
    ) {
        val deck = decks.removeAt(fromPosition)
        decks.add(toPosition, deck)
        notifyItemMoved(fromPosition, toPosition)
    }
}
