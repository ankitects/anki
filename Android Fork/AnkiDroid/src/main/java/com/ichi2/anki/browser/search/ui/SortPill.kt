// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RadioButton
import androidx.annotation.VisibleForTesting
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.AbsoluteCornerSize
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.browser.ColumnType
import com.ichi2.anki.common.utils.dp
import com.ichi2.utils.updatePaddingRelative

/** Direction expressed by a [SortPill] half — which side is the "active" sort. */
enum class SortDirection {
    Ascending,
    Descending,

    ;

    /** The opposite direction. */
    fun flipped(): SortDirection = if (this == Ascending) Descending else Ascending

    /** Maps to Anki's `reverse: Boolean` convention (`Descending == reverse`). */
    val isReverse: Boolean get() = this == Descending
}

/** Maps Anki's `reverse: Boolean` convention to a [SortDirection]. */
fun Boolean.toSortDirection(): SortDirection = if (this) SortDirection.Descending else SortDirection.Ascending

/**
 * Segmented toggle for selecting a sort direction (ascending / descending).
 *
 * Contains two buttons. At most button may be selected ([activeDirection]).
 * - [standardButton] (ascending)
 * - [reverseButton] (descending)
 *
 * The button content is driven by [columnType]
 *
 * @see onDirectionClicked
 */
class SortPill
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = com.google.android.material.R.attr.materialButtonToggleGroupStyle,
    ) : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {
        // The visible pill is shorter than the touch target.
        // draw their backgrounds within an 8dp top/bottom inset (set in the button style),
        // giving a 32dp visible band centered in the row.
        private val visiblePillHeightPx = 32.dp.toPx(context)
        private val elevationPx = 1.dp.toPx(context).toFloat()
        private val iconPaddingPx = 2.dp.toPx(context)
        private val dividerWidthPx = 1.dp.toPx(context).toFloat()

        private val dividerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MaterialColors.getColor(this@SortPill, com.google.android.material.R.attr.colorOutlineVariant)
                strokeWidth = dividerWidthPx
            }

        @VisibleForTesting
        @Suppress("UNNECESSARY_LATEINIT") // necessary: super.init calls onEnabled
        lateinit var standardButton: MaterialButton

        @VisibleForTesting
        @Suppress("UNNECESSARY_LATEINIT") // necessary: super.init calls onEnabled
        lateinit var reverseButton: MaterialButton

        /** Drives the arrow drawable and label text on each half. */
        var columnType: ColumnType = ColumnType.UNSPECIFIED
            set(value) {
                if (field == value) return
                field = value
                applyColumnType()
            }

        /**
         * The currently active sort direction, or `null` when neither half is checked.
         */
        var activeDirection: SortDirection? = null
            set(value) {
                if (field == value) return
                field = value
                when (value) {
                    null -> clearChecked()
                    SortDirection.Ascending -> check(R.id.sort_pill_standard)
                    SortDirection.Descending -> check(R.id.sort_pill_reverse)
                }
                updateA11yState()
            }

        /**
         * Invoked when the user taps a half that wasn't already checked.
         */
        var onDirectionClicked: ((SortDirection) -> Unit)? = null

        /**
         * The host's column display name, prepended to each half's [contentDescription] so
         * TalkBack reads e.g. "Front, ascending" / "Front, descending" rather than just "A".
         * Set this when the host knows the column the pill represents; defaults to no prefix.
         */
        var columnContentDescription: CharSequence? = null
            set(value) {
                if (field == value) return
                field = value
                updateA11yDescriptions()
            }

        init {
            isSingleSelection = true
            // only allow de-selection programmatically
            isSelectionRequired = true
            // Ensure the two buttons are flush
            setInnerCornerSize(AbsoluteCornerSize(0f))
            background = null

            LayoutInflater.from(context).inflate(R.layout.view_sort_pill, this, true)

            standardButton = findViewById(R.id.sort_pill_standard)
            reverseButton = findViewById(R.id.sort_pill_reverse)

            setupShadow()
            setupClickListeners()
            setupA11yRoles()

            applyColumnType()
            updateA11yDescriptions()
            updateA11yState()
        }

        private fun setupA11yRoles() {
            for (button in listOf(standardButton, reverseButton)) {
                ViewCompat.setAccessibilityDelegate(button, RadioButtonAccessibilityDelegate())
            }
        }

        private fun setupShadow() {
            elevation = elevationPx
            outlineProvider = VisiblePillOutlineProvider()
        }

        private fun setupClickListeners() {
            standardButton.setOnClickListener {
                // Tapping the already-active half is a no-op
                if (activeDirection == SortDirection.Ascending) return@setOnClickListener
                onDirectionClicked?.invoke(SortDirection.Ascending)
            }
            reverseButton.setOnClickListener {
                if (activeDirection == SortDirection.Descending) return@setOnClickListener
                onDirectionClicked?.invoke(SortDirection.Descending)
            }
        }

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            // Guard: MaterialButtonGroup's constructor calls this before our init runs.
            if (this::standardButton.isInitialized) {
                standardButton.isEnabled = enabled
                reverseButton.isEnabled = enabled
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (!this::standardButton.isInitialized) return

            // draw a vertical divider between the two buttons
            val x = (standardButton.right + reverseButton.left) / 2f
            val top = (height - visiblePillHeightPx) / 2f
            val bottom = top + visiblePillHeightPx
            canvas.drawLine(x, top, x, bottom, dividerPaint)
        }

        private fun applyColumnType() {
            fun setIconAndLabel(
                button: MaterialButton,
                iconRes: Int,
                label: String?,
            ) {
                button.setIconResource(iconRes)
                button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
                if (label != null) {
                    button.text = label
                    button.iconPadding = iconPaddingPx
                } else {
                    // center the icon if icon-only
                    button.text = ""
                    button.iconPadding = 0
                }
                if (button === standardButton) {
                    // nudge the label right to visually center the control
                    val start = if (label != null) 4.dp else 0.dp
                    button.updatePaddingRelative(start = start)
                }
            }

            when (columnType) {
                ColumnType.TEXT -> {
                    setIconAndLabel(
                        standardButton,
                        R.drawable.ic_sort_pill_arrow_down_label,
                        context.getString(CommonString.sort_pill_label_text_ascending),
                    )
                    setIconAndLabel(
                        reverseButton,
                        R.drawable.ic_sort_pill_arrow_down_label,
                        context.getString(CommonString.sort_pill_label_text_descending),
                    )
                }
                ColumnType.NUMERIC -> {
                    setIconAndLabel(
                        standardButton,
                        R.drawable.ic_sort_pill_arrow_down_label,
                        context.getString(CommonString.sort_pill_label_numeric_ascending),
                    )
                    setIconAndLabel(
                        reverseButton,
                        R.drawable.ic_sort_pill_arrow_down_label,
                        context.getString(CommonString.sort_pill_label_numeric_descending),
                    )
                }
                ColumnType.DATE, ColumnType.UNSPECIFIED -> {
                    setIconAndLabel(standardButton, R.drawable.outline_arrow_downward_alt_24, label = null)
                    setIconAndLabel(reverseButton, R.drawable.outline_arrow_upward_alt_24, label = null)
                }
            }
        }

        private fun updateA11yDescriptions() {
            // set contentDescription to "<column>, ascending" / "<column>, descending"
            val ascending = context.getString(CommonString.sort_pill_a11y_ascending)
            val descending = context.getString(CommonString.sort_pill_a11y_descending)
            val prefix = columnContentDescription
            standardButton.contentDescription = if (prefix != null) "$prefix, $ascending" else ascending
            reverseButton.contentDescription = if (prefix != null) "$prefix, $descending" else descending
        }

        private fun updateA11yState() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            val selected = context.getString(CommonString.sort_pill_a11y_selected)
            standardButton.stateDescription = if (activeDirection == SortDirection.Ascending) selected else null
            reverseButton.stateDescription = if (activeDirection == SortDirection.Descending) selected else null
        }

        /**
         * Elevation follows the visible pill, not the touch target
         */
        inner class VisiblePillOutlineProvider : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                val top = ((view.height - visiblePillHeightPx) / 2).coerceAtLeast(0)
                outline.setRoundRect(
                    view.paddingLeft,
                    top,
                    view.width - view.paddingRight,
                    top + visiblePillHeightPx,
                    visiblePillHeightPx / 2f,
                )
            }
        }
    }

class RadioButtonAccessibilityDelegate : AccessibilityDelegateCompat() {
    override fun onInitializeAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfoCompat,
    ) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = RadioButton::class.java.name
    }
}
