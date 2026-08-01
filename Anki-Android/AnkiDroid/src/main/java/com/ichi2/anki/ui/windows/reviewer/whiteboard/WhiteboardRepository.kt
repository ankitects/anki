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

import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import com.ichi2.utils.toRGBAHex

/**
 * Holds the configuration for a single brush.
 */
data class BrushInfo(
    val color: Int,
    val width: Float,
) {
    override fun toString(): String = "BrushInfo(color=${color.toRGBAHex()}, width=${"%.1f".format(width)})"
}

/**
 * Repository for handling data operations, specifically for saving and loading
 * whiteboard settings from SharedPreferences.
 */
class WhiteboardRepository(
    private val sharedPreferences: SharedPreferences,
) {
    fun saveBrushes(
        brushes: List<BrushInfo>,
        isDarkMode: Boolean,
    ) {
        val key = if (isDarkMode) KEY_BRUSHES_DARK else KEY_BRUSHES_LIGHT
        sharedPreferences.edit { putString(key, brushes.toPreferenceString()) }
    }

    fun loadBrushes(isDarkMode: Boolean): List<BrushInfo> {
        val key = if (isDarkMode) KEY_BRUSHES_DARK else KEY_BRUSHES_LIGHT
        val saved = sharedPreferences.getString(key, null)
        return if (saved.isNullOrEmpty()) {
            getDefaultBrushes(isDarkMode)
        } else {
            saved.fromPreferenceString()
        }
    }

    fun saveLastActiveBrushIndex(
        index: Int,
        isDarkMode: Boolean,
    ) {
        val key = if (isDarkMode) KEY_LAST_ACTIVE_BRUSH_INDEX_DARK else KEY_LAST_ACTIVE_BRUSH_INDEX_LIGHT
        sharedPreferences.edit { putInt(key, index) }
    }

    fun loadLastActiveBrushIndex(isDarkMode: Boolean): Int {
        val key = if (isDarkMode) KEY_LAST_ACTIVE_BRUSH_INDEX_DARK else KEY_LAST_ACTIVE_BRUSH_INDEX_LIGHT
        return sharedPreferences.getInt(key, 0)
    }

    var inkEraserWidth: Float
        get() = sharedPreferences.getFloat(KEY_INK_ERASER_WIDTH, DEFAULT_ERASER_WIDTH)
        set(value) = sharedPreferences.edit { putFloat(KEY_INK_ERASER_WIDTH, value) }

    var strokeEraserWidth: Float
        get() = sharedPreferences.getFloat(KEY_STROKE_ERASER_WIDTH, DEFAULT_ERASER_WIDTH)
        set(value) = sharedPreferences.edit { putFloat(KEY_STROKE_ERASER_WIDTH, value) }

    var eraserMode: EraserMode
        get() {
            val value = sharedPreferences.getString(KEY_ERASER_MODE, EraserMode.INK.name) ?: EraserMode.INK.name
            return EraserMode.entries.firstOrNull { it.name == value } ?: EraserMode.INK
        }
        set(value) = sharedPreferences.edit { putString(KEY_ERASER_MODE, value.name) }

    var stylusOnlyMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_STYLUS_ONLY_MODE, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_STYLUS_ONLY_MODE, value) }

    var toolbarAlignment: ToolbarAlignment
        get() {
            val value = sharedPreferences.getString(KEY_TOOLBAR_ALIGNMENT, ToolbarAlignment.BOTTOM.name) ?: ToolbarAlignment.BOTTOM.name
            return ToolbarAlignment.entries.firstOrNull { it.name == value } ?: ToolbarAlignment.BOTTOM
        }
        set(value) = sharedPreferences.edit { putString(KEY_TOOLBAR_ALIGNMENT, value.name) }

    var isToolbarShown: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_TOOLBAR_SHOWN, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_IS_TOOLBAR_SHOWN, value) }

    private fun List<BrushInfo>.toPreferenceString(): String = this.joinToString(",") { "${it.color}|${it.width}" }

    private fun String.fromPreferenceString(): List<BrushInfo> =
        this.split(',').mapNotNull {
            val parts = it.split('|')
            if (parts.size == 2) {
                try {
                    BrushInfo(color = parts[0].toInt(), width = parts[1].toFloat())
                } catch (_: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        }

    companion object {
        private const val KEY_BRUSHES_LIGHT = "brushes_light"
        private const val KEY_LAST_ACTIVE_BRUSH_INDEX_LIGHT = "last_active_brush_index_light"
        private const val KEY_BRUSHES_DARK = "brushes_dark"
        private const val KEY_LAST_ACTIVE_BRUSH_INDEX_DARK = "last_active_brush_index_dark"
        private const val KEY_INK_ERASER_WIDTH = "ink_eraser_width"
        private const val KEY_STROKE_ERASER_WIDTH = "stroke_eraser_width"
        private const val KEY_ERASER_MODE = "eraser_mode"
        private const val KEY_STYLUS_ONLY_MODE = "stylus_only_mode"
        private const val KEY_TOOLBAR_ALIGNMENT = "toolbar_alignment"
        private const val KEY_IS_TOOLBAR_SHOWN = "is_toolbar_shown"
        const val DEFAULT_STROKE_WIDTH = 10f
        const val DEFAULT_ERASER_WIDTH = 30f

        fun getDefaultBrushes(isDarkMode: Boolean): List<BrushInfo> =
            if (isDarkMode) {
                listOf(
                    BrushInfo(color = Color.WHITE, width = DEFAULT_STROKE_WIDTH),
                )
            } else {
                listOf(
                    BrushInfo(color = Color.BLACK, width = DEFAULT_STROKE_WIDTH),
                )
            }
    }
}
