// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.timebudget

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId

/**
 * The time per top-level deck, with the limits shared by all enabled decks behind a toggle.
 * The Android counterpart of TimeBudgetDialog in qt/aqt/time_budget.py.
 *
 * @param onSaved called after the settings were saved, eg. to refresh the deck list
 */
fun FragmentActivity.showTimeBudgetDialog(onSaved: () -> Unit) {
    launchCatchingTask {
        val (settings, decks) =
            withCol {
                val topLevelDecks =
                    decks
                        .allNamesAndIds(skipEmptyDefault = true, includeFiltered = false)
                        .filter { "::" !in it.name }
                        .map { it.id to it.name }
                loadTimeBudgetSettings() to topLevelDecks
            }
        TimeBudgetDialog(this@showTimeBudgetDialog, settings, decks) { updated ->
            launchCatchingTask {
                withCol { saveTimeBudgetSettings(updated) }
                onSaved()
            }
        }.show()
    }
}

private class TimeBudgetDialog(
    private val context: Context,
    private val settings: TimeBudgetSettings,
    private val decks: List<Pair<DeckId, String>>,
    private val onSave: (TimeBudgetSettings) -> Unit,
) {
    private val padding = 16.dp.toPx(context)
    private val enabledByDeck = mutableMapOf<DeckId, CheckBox>()
    private val minutesByDeck = mutableMapOf<DeckId, EditText>()
    private lateinit var advanced: LinearLayout
    private lateinit var advancedToggle: TextView
    private lateinit var maxReviews: EditText
    private lateinit var minDr: EditText
    private lateinit var maxDr: EditText
    private lateinit var orderList: LinearLayout
    private val order = settings.order.toMutableList()

    fun show() {
        val dialog =
            AlertDialog
                .Builder(context)
                .setTitle(R.string.time_budget_title)
                .setView(buildContent())
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnShowListener {
            // validate before closing
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = settingsFromDialog() ?: return@setOnClickListener
                dialog.dismiss()
                onSave(updated)
            }
        }
        dialog.show()
    }

    private fun buildContent(): View {
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, 0)
            }
        content.addView(text(context.getString(R.string.time_budget_intro)))

        if (decks.isEmpty()) {
            content.addView(text(context.getString(R.string.time_budget_no_decks)))
        }
        for ((did, name) in decks) {
            content.addView(deckRow(did, name, settings.decks[did]))
        }

        advancedToggle =
            TextView(context).apply {
                setPadding(0, padding, 0, padding / 2)
                setTextColor(
                    context.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent)).let {
                        val colour = it.getColor(0, currentTextColor)
                        it.recycle()
                        colour
                    },
                )
                setOnClickListener { setAdvancedVisible(!advanced.isVisible) }
            }
        content.addView(advancedToggle)

        advanced =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
        advanced.addView(text(context.getString(R.string.time_budget_advanced_intro)))
        maxReviews = number(settings.maxReviewsPerCard)
        minDr = number(settings.minDr)
        maxDr = number(settings.maxDr)
        advanced.addView(labelledField(R.string.time_budget_max_reviews, R.string.time_budget_help_max_reviews, maxReviews))
        advanced.addView(labelledField(R.string.time_budget_min_dr, R.string.time_budget_help_min_dr, minDr))
        advanced.addView(labelledField(R.string.time_budget_max_dr, R.string.time_budget_help_max_dr, maxDr))

        advanced.addView(
            text(context.getString(R.string.time_budget_order)).apply {
                setPadding(0, padding, 0, 0)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        advanced.addView(text(context.getString(R.string.time_budget_order_help)).apply { alpha = 0.7f })
        orderList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        advanced.addView(orderList)
        renderOrder()
        content.addView(advanced)
        setAdvancedVisible(false)

        return ScrollView(context).apply { addView(content) }
    }

    private fun deckRow(
        did: DeckId,
        name: String,
        deck: DeckBudget?,
    ): View {
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val enabled =
            CheckBox(context).apply {
                text = name
                isChecked = deck != null && deck.enabled
                contentDescription = context.getString(R.string.time_budget_enabled) + ": " + name
            }
        val minutes =
            number((deck ?: DeckBudget()).minutes).apply {
                contentDescription = context.getString(R.string.time_budget_minutes) + ": " + name
            }
        val minutesLabel = text(context.getString(R.string.time_budget_minutes)).apply { alpha = 0.7f }

        fun update(checked: Boolean) {
            // unticked rows are faded, and time only applies to enabled decks
            enabled.alpha = if (checked) 1f else 0.5f
            minutes.isVisible = checked
            minutesLabel.isVisible = checked
        }
        update(enabled.isChecked)
        enabled.setOnCheckedChangeListener { _, checked -> update(checked) }

        row.addView(enabled, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(minutesLabel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        row.addView(minutes, LinearLayout.LayoutParams(72.dp.toPx(context), WRAP_CONTENT))
        enabledByDeck[did] = enabled
        minutesByDeck[did] = minutes
        return row
    }

    private fun renderOrder() {
        orderList.removeAllViews()
        order.forEachIndexed { index, group ->
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            row.addView(
                text("${index + 1}. ${context.getString(group.label)}"),
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
            )
            row.addView(
                moveButton(R.drawable.ic_expand_less_black_24dp, R.string.time_budget_move_up, enabled = index > 0) {
                    order.add(index - 1, order.removeAt(index))
                    renderOrder()
                },
            )
            row.addView(
                moveButton(R.drawable.ic_expand_more_black_24dp_xml, R.string.time_budget_move_down, enabled = index < order.lastIndex) {
                    order.add(index + 1, order.removeAt(index))
                    renderOrder()
                },
            )
            orderList.addView(row)
        }
    }

    private fun moveButton(
        icon: Int,
        description: Int,
        enabled: Boolean,
        onClick: () -> Unit,
    ) = ImageButton(context).apply {
        setImageResource(icon)
        // the icons are black: use the theme's text colour so they show in dark mode
        setColorFilter(TextView(context).currentTextColor)
        contentDescription = context.getString(description)
        background = null
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.3f
        minimumWidth = 48.dp.toPx(context)
        minimumHeight = 48.dp.toPx(context)
        setOnClickListener { onClick() }
    }

    private fun setAdvancedVisible(visible: Boolean) {
        advanced.isVisible = visible
        advancedToggle.setText(if (visible) R.string.time_budget_hide_advanced else R.string.time_budget_show_advanced)
    }

    private fun text(value: String) =
        TextView(context).apply {
            text = value
            setPadding(0, padding / 4, 0, padding / 4)
        }

    private fun number(value: Int) =
        EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            gravity = Gravity.END
            setSelectAllOnFocus(true)
        }

    private fun labelledField(
        label: Int,
        help: Int,
        field: EditText,
    ): View {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        row.addView(text(context.getString(label)), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(field, LinearLayout.LayoutParams(72.dp.toPx(context), WRAP_CONTENT))
        column.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        column.addView(text(context.getString(help)).apply { alpha = 0.7f })
        return column
    }

    /** A number in [min, max], or null after flagging the field. */
    private fun EditText.intIn(
        label: String,
        min: Int,
        max: Int,
    ): Int? {
        val value = text.toString().trim().toIntOrNull()
        if (value == null || value < min || value > max) {
            error = context.getString(R.string.time_budget_out_of_range, label, min, max)
            requestFocus()
            return null
        }
        error = null
        return value
    }

    /** Updated settings, or null if invalid. Settings of decks that are not shown are kept. */
    private fun settingsFromDialog(): TimeBudgetSettings? {
        val updatedDecks = settings.decks.toMutableMap()
        for ((did, _) in decks) {
            val enabled = enabledByDeck.getValue(did).isChecked
            val minutesField = minutesByDeck.getValue(did)
            val minutes =
                if (enabled) {
                    minutesField.intIn(context.getString(R.string.time_budget_minutes), 1, 24 * 60) ?: return null
                } else {
                    // hidden: keep what was there, if it was valid
                    minutesField.text
                        .toString()
                        .toIntOrNull()
                        ?.coerceIn(1, 24 * 60) ?: settings.decks[did]?.minutes ?: DEFAULT_MINUTES
                }
            if (enabled || did in updatedDecks) {
                updatedDecks[did] = DeckBudget(enabled = enabled, minutes = minutes)
            }
        }

        fun advancedField(
            field: EditText,
            label: Int,
            min: Int,
            max: Int,
        ): Int? {
            val value = field.intIn(context.getString(label), min, max)
            if (value == null) setAdvancedVisible(true)
            return value
        }
        val maxReviewsValue = advancedField(maxReviews, R.string.time_budget_max_reviews, 1, 99) ?: return null
        val minDrValue = advancedField(minDr, R.string.time_budget_min_dr, 1, 99) ?: return null
        val maxDrValue = advancedField(maxDr, R.string.time_budget_max_dr, 1, 100) ?: return null
        if (minDrValue > maxDrValue) {
            setAdvancedVisible(true)
            minDr.error = context.getString(R.string.time_budget_min_above_max)
            minDr.requestFocus()
            return null
        }
        return TimeBudgetSettings(
            decks = updatedDecks,
            maxReviewsPerCard = maxReviewsValue,
            minDr = minDrValue,
            maxDr = maxDrValue,
            order = CardGroup.completeOrder(order),
        )
    }
}

private val CardGroup.label: Int
    get() =
        when (this) {
            CardGroup.FIRST_REVIEW -> R.string.time_budget_first_review
            CardGroup.NEW_CARDS -> R.string.time_budget_new_cards
            CardGroup.RE_REVIEW -> R.string.time_budget_re_review
        }
