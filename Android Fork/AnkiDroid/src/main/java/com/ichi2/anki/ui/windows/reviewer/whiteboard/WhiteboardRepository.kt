// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit

/**
 * Repository for handling data operations, specifically for saving and loading
 * whiteboard settings from SharedPreferences.
 */
class WhiteboardRepository(
    private val sharedPreferences: SharedPreferences,
) {
    fun saveBrushes(
        brushes: List<WhiteboardTool.Brush>,
        isDarkMode: Boolean,
    ) {
        val key = if (isDarkMode) PREF_BRUSHES_DARK else PREF_BRUSHES_LIGHT
        sharedPreferences.edit { putString(key, brushes.toPreferenceString()) }
    }

    fun loadBrushes(isDarkMode: Boolean): List<WhiteboardTool.Brush> {
        val key = if (isDarkMode) PREF_BRUSHES_DARK else PREF_BRUSHES_LIGHT
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
        val key = if (isDarkMode) PREF_LAST_ACTIVE_BRUSH_INDEX_DARK else PREF_LAST_ACTIVE_BRUSH_INDEX_LIGHT
        sharedPreferences.edit { putInt(key, index) }
    }

    fun loadLastActiveBrushIndex(isDarkMode: Boolean): Int {
        val key = if (isDarkMode) PREF_LAST_ACTIVE_BRUSH_INDEX_DARK else PREF_LAST_ACTIVE_BRUSH_INDEX_LIGHT
        return sharedPreferences.getInt(key, 0)
    }

    var inkEraserWidth: Float
        get() = sharedPreferences.getFloat(PREF_INK_ERASER_WIDTH, DEFAULT_ERASER_WIDTH)
        set(value) = sharedPreferences.edit { putFloat(PREF_INK_ERASER_WIDTH, value) }

    var strokeEraserWidth: Float
        get() = sharedPreferences.getFloat(PREF_STROKE_ERASER_WIDTH, DEFAULT_ERASER_WIDTH)
        set(value) = sharedPreferences.edit { putFloat(PREF_STROKE_ERASER_WIDTH, value) }

    var eraserMode: EraserMode
        get() {
            val value = sharedPreferences.getString(PREF_ERASER_MODE, EraserMode.INK.name) ?: EraserMode.INK.name
            return EraserMode.entries.firstOrNull { it.name == value } ?: EraserMode.INK
        }
        set(value) = sharedPreferences.edit { putString(PREF_ERASER_MODE, value.name) }

    var stylusOnlyMode: Boolean
        get() = sharedPreferences.getBoolean(PREF_STYLUS_ONLY_MODE, false)
        set(value) = sharedPreferences.edit { putBoolean(PREF_STYLUS_ONLY_MODE, value) }

    var toolbarAlignment: ToolbarAlignment
        get() {
            val value = sharedPreferences.getString(PREF_TOOLBAR_ALIGNMENT, ToolbarAlignment.BOTTOM.name) ?: ToolbarAlignment.BOTTOM.name
            return ToolbarAlignment.entries.firstOrNull { it.name == value } ?: ToolbarAlignment.BOTTOM
        }
        set(value) = sharedPreferences.edit { putString(PREF_TOOLBAR_ALIGNMENT, value.name) }

    var isToolbarShown: Boolean
        get() = sharedPreferences.getBoolean(PREF_IS_TOOLBAR_SHOWN, true)
        set(value) = sharedPreferences.edit { putBoolean(PREF_IS_TOOLBAR_SHOWN, value) }

    private fun List<WhiteboardTool.Brush>.toPreferenceString(): String = this.joinToString(",") { "${it.color}|${it.width}" }

    private fun String.fromPreferenceString(): List<WhiteboardTool.Brush> =
        this.split(',').mapNotNull {
            val parts = it.split('|')
            if (parts.size == 2) {
                try {
                    WhiteboardTool.Brush(color = parts[0].toInt(), width = parts[1].toFloat())
                } catch (_: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        }

    companion object {
        private const val PREF_BRUSHES_LIGHT = "brushes_light"
        private const val PREF_LAST_ACTIVE_BRUSH_INDEX_LIGHT = "last_active_brush_index_light"
        private const val PREF_BRUSHES_DARK = "brushes_dark"
        private const val PREF_LAST_ACTIVE_BRUSH_INDEX_DARK = "last_active_brush_index_dark"
        private const val PREF_INK_ERASER_WIDTH = "ink_eraser_width"
        private const val PREF_STROKE_ERASER_WIDTH = "stroke_eraser_width"
        private const val PREF_ERASER_MODE = "eraser_mode"
        private const val PREF_STYLUS_ONLY_MODE = "stylus_only_mode"
        private const val PREF_TOOLBAR_ALIGNMENT = "toolbar_alignment"
        private const val PREF_IS_TOOLBAR_SHOWN = "is_toolbar_shown"
        const val DEFAULT_STROKE_WIDTH = 10f
        const val DEFAULT_ERASER_WIDTH = 30f

        fun getDefaultBrushes(isDarkMode: Boolean): List<WhiteboardTool.Brush> =
            if (isDarkMode) {
                listOf(
                    WhiteboardTool.Brush(color = Color.WHITE, width = DEFAULT_STROKE_WIDTH),
                )
            } else {
                listOf(
                    WhiteboardTool.Brush(color = Color.BLACK, width = DEFAULT_STROKE_WIDTH),
                )
            }
    }
}
