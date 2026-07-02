/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import kotlin.math.roundToInt

/**
 * Adapter for displaying a list of brushes in a RecyclerView.
 *
 * @param onBrushClick Callback when a brush color is clicked.
 * @param onBrushLongClick Callback when a brush color is long-clicked.
 */
class BrushAdapter(
    private val onBrushClick: (View, Int) -> Unit,
    private val onBrushLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<BrushAdapter.BrushViewHolder>() {
    private var brushes: List<BrushInfo> = emptyList()
    private var activeIndex: Int = -1
    private var isEraserActive: Boolean = false

    fun updateData(
        newBrushes: List<BrushInfo>,
        newActiveIndex: Int,
        eraserActive: Boolean,
    ) {
        brushes = newBrushes
        activeIndex = newActiveIndex
        isEraserActive = eraserActive
        notifyDataSetChanged()
    }

    fun updateSelection(
        newActiveIndex: Int,
        eraserActive: Boolean,
    ) {
        val oldIndex = activeIndex
        activeIndex = newActiveIndex
        isEraserActive = eraserActive

        if (oldIndex in brushes.indices) notifyItemChanged(oldIndex)
        if (newActiveIndex in brushes.indices) notifyItemChanged(newActiveIndex)
    }

    override fun getItemCount(): Int = brushes.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BrushViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.item_button_color_brush, parent, false)
        return BrushViewHolder(itemView)
    }

    override fun onBindViewHolder(
        holder: BrushViewHolder,
        position: Int,
    ) {
        val brush = brushes[position]
        holder.bind(brush, position == activeIndex && !isEraserActive)
    }

    inner class BrushViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val button: MaterialButton = itemView as MaterialButton

        fun bind(
            brush: BrushInfo,
            isSelected: Boolean,
        ) = button.apply {
            isCheckable = true
            isChecked = isSelected
            text = brush.width.roundToInt().toString()
            iconTint = null

            val layer = icon?.mutate() as? LayerDrawable
            val fill = layer?.findDrawableByLayerId(R.id.brush_preview_fill) as? GradientDrawable
            fill?.setColor(brush.color)

            setOnClickListener { onBrushClick(it, bindingAdapterPosition) }
            setOnLongClickListener {
                onBrushLongClick(bindingAdapterPosition)
                true
            }
        }
    }
}
