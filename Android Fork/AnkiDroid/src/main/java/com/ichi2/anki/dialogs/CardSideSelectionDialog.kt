// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CommonString
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.utils.show
import com.ichi2.utils.title

/** Allows selecting between [CardSide.QUESTION], [CardSide.ANSWER] or [CardSide.BOTH] */
class CardSideSelectionDialog {
    companion object {
        @SuppressLint("CheckResult")
        fun displayInstance(
            ctx: Context,
            callback: (c: CardSide) -> Unit,
        ) {
            val items =
                listOf(
                    CommonString.card_side_both,
                    CommonString.card_side_question,
                    CommonString.card_side_answer,
                )

            AlertDialog.Builder(ctx).show {
                title(CommonString.card_side_selection_title)
                setItems(items.map { ctx.getString(it) }.toTypedArray()) { _, index ->
                    when (items[index]) {
                        CommonString.card_side_both -> callback(CardSide.BOTH)
                        CommonString.card_side_question -> callback(CardSide.QUESTION)
                        CommonString.card_side_answer -> callback(CardSide.ANSWER)
                    }
                }
            }
        }
    }
}
