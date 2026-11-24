// SPDX-License-Identifier: GPL-3.0-or-later

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
    private var brushes: List<WhiteboardTool.Brush> = emptyList()
    private var activeIndex: Int = -1
    private var isEraserActive: Boolean = false

    fun updateData(
        newBrushes: List<WhiteboardTool.Brush>,
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
        if (activeIndex == newActiveIndex && isEraserActive == eraserActive) return
        val oldIndex = activeIndex
        activeIndex = newActiveIndex
        isEraserActive = eraserActive

        if (oldIndex in brushes.indices) notifyItemChanged(oldIndex)
        if (newActiveIndex in brushes.indices && newActiveIndex != oldIndex) {
            notifyItemChanged(newActiveIndex)
        }
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
            brush: WhiteboardTool.Brush,
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
