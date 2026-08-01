/*
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>
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

package com.ichi2.anki.widgets

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.content.res.getDrawableOrThrow
import androidx.core.content.withStyledAttributes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemDeckBinding
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.RustCleanup
import com.ichi2.anki.common.android.R as CommonR

/**
 * A [RecyclerView.Adapter] used to show the list of decks inside [com.ichi2.anki.DeckPicker].
 *
 * @param onDeckSelected callback triggered when the user selects a deck
 * @param onDeckCountsSelected callback triggered when the user selects the counts of a deck
 * @param onDeckChildrenToggled callback triggered when the user toggles the visibility of its
 * children to show/hide the children. Only for decks that have children.
 * @param onDeckContextRequested callback triggered when the user requested to see extra actions for
 * a deck. This consists in a context menu brought in by either a long touch or a right click.
 * @param onDeckRightClick callback triggered when the user right-clicks on a deck with a mouse
 */
@RustCleanup("Differs from legacy backend: Create deck 'One', create deck 'One::two'. 'One::two' was not expanded")
class DeckAdapter(
    context: Context,
    private val onDeckSelected: (DeckId) -> Unit,
    private val onDeckCountsSelected: (DeckId) -> Unit,
    private val onDeckChildrenToggled: (DeckId) -> Unit,
    private val onDeckContextRequested: (DeckId) -> Unit,
    private val onDeckRightClick: (DeckId, Float, Float) -> Unit,
) : ListAdapter<DisplayDeckNode, DeckAdapter.ViewHolder>(deckNodeDiffCallback) {
    private val layoutInflater = LayoutInflater.from(context)
    private val zeroCountColor: Int
    private val newCountColor: Int
    private val learnCountColor: Int
    private val reviewCountColor: Int
    private val rowCurrentDrawable: Int
    private val deckNameDefaultColor: Int
    private val deckNameDynColor: Int
    private val expandImage: Drawable
    private val collapseImage: Drawable

    // intended to be `val` but was declared a `var` to allow initialization from the init {} block
    private var selectableItemBackground: Int = 0
    private val endPadding: Int = context.resources.getDimension(R.dimen.deck_picker_right_padding).toInt()
    private val startPadding: Int = context.resources.getDimension(R.dimen.deck_picker_left_padding).toInt()
    private val startPaddingSmall: Int = context.resources.getDimension(R.dimen.deck_picker_left_padding_small).toInt()
    private val nestedIndent = context.resources.getDimension(R.dimen.keyline_1).toInt()

    // Flags
    private var hasSubdecks = false

    /**
     * Flag to indicate if the activity has a background set. If true the adapter will make the rows
     * transparent so the background can be seen.
     */
    var activityHasBackground: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    class ViewHolder(
        val binding: ItemDeckBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    /**
     * Set new data in the adapter. This should be used instead of [submitList] (which is called
     * by this method) so there's no need to call [notifyDataSetChanged] on the adapter.
     */
    fun submit(
        data: List<DisplayDeckNode>,
        hasSubDecks: Boolean,
    ) {
        // force refresh when sub decks status changes as this info isn't encapsulated in the
        // adapter's items so there wouldn't be an ui refresh just from using submitList()
        val forceRefresh = this.hasSubdecks != hasSubDecks
        this.hasSubdecks = hasSubDecks
        submitList(data)
        if (forceRefresh) notifyDataSetChanged()
    }

    /**
     * Update the current selected deck so the adapter shows the proper backgrounds.
     * Calls [notifyDataSetChanged].
     */
    fun updateSelectedDeck(deckId: DeckId) {
        submitList(
            this.currentList.map { it.withUpdatedDeckId(deckId) },
        )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(ItemDeckBinding.inflate(layoutInflater, parent, false))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val binding = holder.binding
        val node = getItem(position)
        // Set the expander icon and padding according to whether or not there are any subdecks
        if (hasSubdecks) {
            binding.deckLayout.setPaddingRelative(startPaddingSmall, 0, endPadding, 0)
            binding.deckExpander.visibility = View.VISIBLE
            // Create the correct expander for this deck
            runBlocking { setDeckExpander(binding.deckExpander, holder.binding.indentView, node) }
        } else {
            binding.deckExpander.visibility = View.GONE
            binding.indentView.minimumWidth = 0
            binding.deckLayout.setPaddingRelative(startPadding, 0, endPadding, 0)
        }
        if (node.canCollapse) {
            binding.deckExpander.setOnClickListener {
                onDeckChildrenToggled(node.did)
                notifyItemChanged(position) // Ensure UI updates
            }
        } else {
            binding.deckExpander.isClickable = false
            binding.deckExpander.setOnClickListener(null)
        }
        holder.binding.deckLayout.setBackgroundResource(rowCurrentDrawable)
        // set a different background color for the current selected deck
        if (node.isSelected) {
            holder.binding.deckLayout.setBackgroundResource(rowCurrentDrawable)
            if (activityHasBackground) {
                val background =
                    holder.binding.deckLayout.background
                        .mutate()
                background.alpha = (255 * SELECTED_DECK_ALPHA_AGAINST_BACKGROUND).toInt()
                holder.binding.deckLayout.background = background
            }
        } else {
            holder.binding.deckLayout.setBackgroundResource(selectableItemBackground)
        }
        // Set deck name and colour. Filtered decks have their own colour
        binding.deckName.text = node.lastDeckNameComponent
        binding.deckName.setTextColor(if (node.filtered) deckNameDynColor else deckNameDefaultColor)

        // Set the card counts and their colors
        binding.deckNew.text = node.newCount.toString()
        binding.deckNew.setTextColor(if (node.newCount == 0) zeroCountColor else newCountColor)
        binding.deckLearn.text = node.lrnCount.toString()
        binding.deckLearn.setTextColor(if (node.lrnCount == 0) zeroCountColor else learnCountColor)
        binding.deckReview.text = node.revCount.toString()
        binding.deckReview.setTextColor(if (node.revCount == 0) zeroCountColor else reviewCountColor)

        holder.binding.deckLayout.setOnClickListener { onDeckSelected(node.did) }
        holder.binding.deckLayout.setOnLongClickListener {
            onDeckContextRequested(node.did)
            true
        }
        binding.countsLayout.setOnClickListener { onDeckCountsSelected(node.did) }

        // Right click listener for right click context menus
        holder.binding.deckLayout.setOnGenericMotionListener { _, motionEvent ->
            if (motionEvent.action == android.view.MotionEvent.ACTION_BUTTON_PRESS &&
                motionEvent.buttonState and android.view.MotionEvent.BUTTON_SECONDARY != 0
            ) {
                onDeckRightClick(node.did, motionEvent.x, motionEvent.y)
                true
            } else {
                false
            }
        }
    }

    private fun setDeckExpander(
        expander: ImageButton,
        indent: ImageButton,
        node: DisplayDeckNode,
    ) {
        // Apply the correct expand/collapse drawable
        if (node.canCollapse) {
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            if (node.collapsed) {
                expander.setImageDrawable(expandImage)
                expander.contentDescription = expander.context.getString(R.string.expand)
            } else {
                expander.setImageDrawable(collapseImage)
                expander.contentDescription = expander.context.getString(R.string.collapse)
            }
        } else {
            expander.visibility = View.INVISIBLE
            expander.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Add some indenting for each nested level
        indent.minimumWidth = nestedIndent * node.depth
    }

    companion object {
        // Make the selected deck roughly half transparent if there is a background
        private const val SELECTED_DECK_ALPHA_AGAINST_BACKGROUND = 0.45
    }

    init {
        // Get the colors from the theme attributes
        val attrs =
            intArrayOf(
                R.attr.zeroCountColor,
                R.attr.newCountColor,
                R.attr.learnCountColor,
                R.attr.reviewCountColor,
                R.attr.currentDeckBackground,
                android.R.attr.textColor,
                R.attr.dynDeckColor,
                R.attr.expandRef,
                R.attr.collapseRef,
            )
        val ta = context.obtainStyledAttributes(attrs)
        zeroCountColor = ta.getColor(0, context.getColor(R.color.black))
        newCountColor = ta.getColor(1, context.getColor(R.color.black))
        learnCountColor = ta.getColor(2, context.getColor(R.color.black))
        reviewCountColor = ta.getColor(3, context.getColor(R.color.black))
        rowCurrentDrawable = ta.getResourceId(4, 0)
        deckNameDefaultColor = ta.getColor(5, context.getColor(R.color.black))
        deckNameDynColor = ta.getColor(6, context.getColor(CommonR.color.material_blue_A700))
        expandImage = ta.getDrawableOrThrow(7)
        expandImage.isAutoMirrored = true
        collapseImage = ta.getDrawableOrThrow(8)
        collapseImage.isAutoMirrored = true
        ta.recycle()
        context.withStyledAttributes(attrs = intArrayOf(android.R.attr.selectableItemBackground)) {
            selectableItemBackground = ta.getResourceId(0, 0)
        }
    }
}

private val deckNodeDiffCallback =
    object : DiffUtil.ItemCallback<DisplayDeckNode>() {
        override fun areItemsTheSame(
            oldItem: DisplayDeckNode,
            newItem: DisplayDeckNode,
        ): Boolean = oldItem.did == newItem.did

        override fun areContentsTheSame(
            oldItem: DisplayDeckNode,
            newItem: DisplayDeckNode,
        ): Boolean = oldItem == newItem
    }
