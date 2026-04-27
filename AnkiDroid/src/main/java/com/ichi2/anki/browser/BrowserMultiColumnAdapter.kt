/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.ThemeUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import anki.search.BrowserRow.Color
import com.ichi2.anki.AnkiDroidApp.Companion.sharedPrefs
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.darkenColor
import com.ichi2.anki.common.utils.android.lightenColorAbsolute
import com.ichi2.anki.common.utils.ext.replaceWith
import com.ichi2.anki.databinding.ItemCardBrowserBinding
import com.ichi2.anki.databinding.ViewBrowserColumnCellBinding
import com.ichi2.themes.Themes
import com.ichi2.utils.removeChildren
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import kotlin.math.abs

typealias RowIsSelected = Boolean

/**
 * An adapter over a list of [CardOrNoteId].
 *
 * This has two states: regular and multi-select
 *
 * @see R.layout.item_card_browser
 */
class BrowserMultiColumnAdapter(
    private val context: Context,
    private val viewModel: CardBrowserViewModel,
    private val onLongPress: (CardOrNoteId) -> Unit,
    private val onTap: (CardOrNoteId) -> Unit,
    private val onRightClick: (CardOrNoteId) -> Unit = onLongPress,
) : RecyclerView.Adapter<BrowserMultiColumnAdapter.MultiColumnViewHolder>() {
    val fontSizeScalePercent =
        sharedPrefs().getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO)

    private val rowCollection: BrowserRowCollection
        get() = viewModel.cards

    private var originalTextSize = -1.0f

    inner class MultiColumnViewHolder(
        private val binding: ItemCardBrowserBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        var id: CardOrNoteId? = null

        val columnViews = mutableListOf<TextView>()

        var numberOfColumns: Int = 0
            set(value) {
                if (field == value) return
                field = value
                // remove the past set of columns
                binding.root.removeChildren { it !is CheckBox }

                val layoutInflater = LayoutInflater.from(context)

                // recreate the columns and the dividers
                columnViews.replaceWith(
                    (1..value).map { index ->
                        ViewBrowserColumnCellBinding.inflate(layoutInflater, binding.root, true).root
                    },
                )

                columnViews.forEach { it.setupTextSize() }
            }

        init {
            this.itemView.setOnClickListener {
                id?.let { id ->
                    Timber.d("Tapped: %s", id)
                    onTap(id)
                }
            }
            this.itemView.setOnLongClickListener {
                val id = id ?: return@setOnLongClickListener false
                Timber.d("Long press: %s", id)
                onLongPress(id)
                return@setOnLongClickListener true
            }

            this.itemView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN &&
                    event.buttonState and MotionEvent.BUTTON_SECONDARY != 0
                ) {
                    return@setOnTouchListener onRightClick()
                }
                return@setOnTouchListener false
            }
            binding.rowSelectedCheckBox.setOnClickListener {
                id?.let { id ->
                    Timber.d("Tapped on checkbox: %s", id)
                    onTap(id)
                }
            }
        }

        /**
         * Handle right-click event by delegating to onRightClick handler from constructor
         * @return true if the event was handled, false otherwise
         */
        private fun onRightClick(): Boolean =
            id?.let {
                Timber.d("Right Click: %s", it)
                onRightClick(it)
                true
            } ?: false

        fun setInMultiSelect(inMultiSelect: Boolean) {
            binding.rowSelectedCheckBox.isVisible = inMultiSelect
        }

        fun setIsSelected(value: RowIsSelected) {
            binding.rowSelectedCheckBox.isChecked = value
        }

        @NeedsTest("17731 - maybe check all activities load in dark mode, at least check this code")
        fun setColor(
            @ColorInt color: Int,
        ) {
            val nightMode = Themes.isNightTheme
            val pressedColor: Int
            val focusedColor: Int

            if (nightMode) {
                // 25% was determined by visual inspection
                pressedColor = lightenColorAbsolute(color, 0.25f)
                focusedColor = pressedColor
            } else {
                pressedColor = darkenColor(color, 0.85f)
                focusedColor = darkenColor(color, 0.4f)
            }

            require(pressedColor != color)
            val rippleDrawable =
                RippleDrawable(
                    ColorStateList.valueOf(pressedColor),
                    color.toDrawable(),
                    null,
                )

            // When a row gains keyboard focus, apply a static background color instead of RippleDrawable
            // to avoid the brief dark flicker caused by Ripple's focus exit animation.
            // For the other states (pressed/normal), apply the RippleDrawable.
            val background =
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focusedColor.toDrawable())
                    addState(intArrayOf(), rippleDrawable)
                }
            itemView.background = background
        }

        fun setIsTruncated(truncated: Boolean) {
            columnViews.forEach { it.setIsTruncated(truncated) }
        }

        fun setIsDeleted(isDeleted: Boolean) {
            columnViews.forEach { it.setStrikeThrough(isDeleted) }
        }

        private fun TextView.setIsTruncated(isTruncated: Boolean) {
            if (isTruncated) {
                maxLines = LINES_VISIBLE_WHEN_COLLAPSED
                ellipsize = TextUtils.TruncateAt.END
            } else {
                maxLines = Int.MAX_VALUE
                ellipsize = null
            }
        }

        private fun TextView.setStrikeThrough(strikeThrough: Boolean) {
            paintFlags =
                if (strikeThrough) {
                    paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
        }

        private fun TextView.setupTextSize() {
            // Set the font and font size for a TextView v
            val currentSize = textSize
            if (originalTextSize < 0) {
                originalTextSize = currentSize
            }
            // do nothing when pref is 100% and apply scaling only once
            if (fontSizeScalePercent != 100 && abs(originalTextSize - currentSize) < 0.1) {
                // getTextSize returns value in absolute PX so use that in the setter
                setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize * (fontSizeScalePercent / 100.0f))
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MultiColumnViewHolder {
        val layoutInflater = LayoutInflater.from(context)
        val binding = ItemCardBrowserBinding.inflate(layoutInflater, parent, false)
        return MultiColumnViewHolder(binding)
    }

    override fun getItemCount(): Int = rowCollection.size

    override fun onBindViewHolder(
        holder: MultiColumnViewHolder,
        position: Int,
    ) {
        val id =
            try {
                rowCollection[position]
            } catch (e: Exception) {
                Timber.w(e)
                return
            }

        try {
            val (row, isSelected) = viewModel.transformBrowserRow(id)

            // PERF: removeSounds only needs to be performed on QUESTION/ANSWER columns
            fun renderColumn(columnIndex: Int): String =
                removeSounds(
                    input = row.getCells(columnIndex).text,
                    showMediaFilenames = viewModel.showMediaFilenames,
                )
            holder.numberOfColumns = row.cellsCount

            for (i in 0 until row.cellsCount) {
                holder.columnViews[i].text = renderColumn(i)
            }
            holder.setIsSelected(isSelected)
            val rowColor =
                if (viewModel.isFragmented && viewModel.focusedRow == id) {
                    ThemeUtils.getThemeAttrColor(context, R.attr.focusedRowBackgroundColor)
                } else {
                    backendColorToColor(row.color)
                }
            holder.setColor(rowColor)
            holder.setIsDeleted(false)
        } catch (e: BackendException) {
            holder.columnViews.forEach { it.text = e.localizedMessage }
            // deleted rows cannot be selected
            holder.setColor(backendColorToColor(Color.UNRECOGNIZED))
            // deleted rows may not be selected
            holder.setIsSelected(false)
            holder.setIsDeleted(true)
        }

        holder.setInMultiSelect(viewModel.isInMultiSelectMode)
        holder.setIsTruncated(viewModel.isTruncated)
        holder.id = id
    }

    private fun backendColorToColor(color: Color): Int =
        when (color) {
            Color.COLOR_FLAG_RED -> context.getColor(Flag.RED.browserColorRes!!)
            Color.COLOR_FLAG_ORANGE -> context.getColor(Flag.ORANGE.browserColorRes!!)
            Color.COLOR_FLAG_GREEN -> context.getColor(Flag.GREEN.browserColorRes!!)
            Color.COLOR_FLAG_BLUE -> context.getColor(Flag.BLUE.browserColorRes!!)
            Color.COLOR_FLAG_PINK -> context.getColor(Flag.PINK.browserColorRes!!)
            Color.COLOR_FLAG_TURQUOISE -> context.getColor(Flag.TURQUOISE.browserColorRes!!)
            Color.COLOR_FLAG_PURPLE -> context.getColor(Flag.PURPLE.browserColorRes!!)

            Color.COLOR_SUSPENDED -> ThemeUtils.getThemeAttrColor(context, R.attr.suspendedColor)
            Color.COLOR_MARKED -> ThemeUtils.getThemeAttrColor(context, R.attr.markedColor)
            Color.COLOR_BURIED -> ThemeUtils.getThemeAttrColor(context, R.attr.buriedColor)

            Color.COLOR_DEFAULT, Color.UNRECOGNIZED ->
                ThemeUtils.getThemeAttrColor(context, android.R.attr.colorBackground)
        }

    companion object {
        private val mediaFilenameRegex = Regex("\uD83D\uDD09(.*?)\uD83D\uDD09") // 🔉(.*?)🔉

        /**
         * Strips instances of '🔉filename.mp3🔉' if [showMediaFilenames] is not set
         */
        @VisibleForTesting
        fun removeSounds(
            input: String,
            showMediaFilenames: Boolean,
        ): String {
            if (showMediaFilenames) return input
            return mediaFilenameRegex.replace(input, "")
        }

        private const val DEFAULT_FONT_SIZE_RATIO = 100

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val LINES_VISIBLE_WHEN_COLLAPSED = 3
    }
}
