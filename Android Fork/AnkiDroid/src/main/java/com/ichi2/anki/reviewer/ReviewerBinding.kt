// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.CheckResult
import com.ichi2.anki.CommonString
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import java.util.Objects

class ReviewerBinding(
    binding: Binding,
    val side: CardSide,
) : MappableBinding(binding) {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is ReviewerBinding) return false

        return side === CardSide.BOTH ||
            other.side === CardSide.BOTH ||
            side === other.side
    }

    override fun hashCode(): Int = Objects.hash(binding, PREFIX)

    override fun toPreferenceString(): String? {
        if (!binding.isValid) {
            return null
        }
        val s =
            StringBuilder()
                .append(PREFIX)
                .append(binding.toString())
        // don't serialise problematic bindings
        if (s.isEmpty()) {
            return null
        }
        when (side) {
            CardSide.QUESTION -> s.append(QUESTION_SUFFIX)
            CardSide.ANSWER -> s.append(ANSWER_SUFFIX)
            CardSide.BOTH -> s.append(QUESTION_AND_ANSWER_SUFFIX)
        }
        return s.toString()
    }

    override fun toDisplayString(context: Context): String {
        val bindingString = binding.toDisplayString(context)
        return when (side) {
            CardSide.QUESTION -> context.getString(CommonString.display_binding_card_side_question, bindingString)
            CardSide.ANSWER -> context.getString(CommonString.display_binding_card_side_answer, bindingString)
            CardSide.BOTH -> bindingString
        }
    }

    companion object {
        const val PREFIX = 'r'
        private const val QUESTION_SUFFIX = '0'
        private const val ANSWER_SUFFIX = '1'
        private const val QUESTION_AND_ANSWER_SUFFIX = '2'

        fun fromPreferenceString(prefString: String?): List<ReviewerBinding> {
            if (prefString.isNullOrEmpty()) return emptyList()

            fun fromString(string: String): ReviewerBinding? {
                if (string.isEmpty()) return null
                val bindingString =
                    StringBuilder(string)
                        .substring(0, string.length - 1)
                        .removePrefix(PREFIX.toString())
                val binding = Binding.fromString(bindingString) ?: return null
                val side =
                    when (string.last()) {
                        QUESTION_SUFFIX -> CardSide.QUESTION
                        ANSWER_SUFFIX -> CardSide.ANSWER
                        else -> CardSide.BOTH
                    }
                return ReviewerBinding(binding, side)
            }

            return getPreferenceSubstrings(prefString).mapNotNull { fromString(it) }
        }

        @CheckResult
        fun fromGesture(gesture: Gesture): ReviewerBinding = ReviewerBinding(Binding.GestureInput(gesture), CardSide.BOTH)

        @CheckResult
        fun fromPreference(
            prefs: SharedPreferences,
            command: ViewerCommand,
        ): MutableList<MappableBinding> {
            val value = prefs.getString(command.preferenceKey, null) ?: return command.defaultValue.toMutableList()
            return fromPreferenceString(value).toMutableList()
        }
    }
}
