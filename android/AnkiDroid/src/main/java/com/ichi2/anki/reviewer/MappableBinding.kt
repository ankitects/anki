/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.CheckResult
import com.ichi2.anki.reviewer.Binding.KeyBinding
import timber.log.Timber
import java.util.Objects

/**
 * Binding + additional contextual information
 */
open class MappableBinding(
    val binding: Binding,
) {
    val isKey: Boolean get() = binding is KeyBinding

    override fun equals(other: Any?): Boolean = this === other || (other is MappableBinding && other.binding == binding)

    override fun hashCode(): Int = Objects.hash(binding)

    open fun toDisplayString(context: Context): String = binding.toDisplayString(context)

    open fun toPreferenceString(): String? = binding.toString()

    companion object {
        const val PREF_SEPARATOR = '|'
        private const val VERSION_PREFIX = "1/"

        @CheckResult
        fun List<MappableBinding>.toPreferenceString(): String =
            this
                .mapNotNull { it.toPreferenceString() }
                .joinToString(prefix = VERSION_PREFIX, separator = PREF_SEPARATOR.toString())

        /**
         * @param string preference value containing mapped bindings
         * @return a list with each individual binding substring
         */
        @CheckResult
        fun getPreferenceSubstrings(string: String): List<String> {
            if (string.isEmpty()) return emptyList()
            if (!string.startsWith(VERSION_PREFIX)) {
                Timber.w("cannot handle version of string %s", string)
                return emptyList()
            }
            return string.substring(VERSION_PREFIX.length).split(PREF_SEPARATOR).filter { it.isNotEmpty() }
        }

        @CheckResult
        fun fromPreferenceString(string: String?): List<MappableBinding> {
            if (string.isNullOrEmpty()) return emptyList()
            return getPreferenceSubstrings(string).map {
                val binding = Binding.fromString(it)
                MappableBinding(binding)
            }
        }
    }
}

/**
 * Action that can be triggered through a [Binding], like a gesture or a key press.
 *
 * The bindings can be configured with a [MappableBinding] ([B]) in
 * the settings, by changing the preference with the corresponding [preferenceKey].
 */
interface MappableAction<B : MappableBinding> {
    val preferenceKey: String

    fun getBindings(prefs: SharedPreferences): List<B>
}

fun interface BindingProcessor<B : MappableBinding, A : MappableAction<B>> {
    fun processAction(
        action: A,
        binding: B,
    ): Boolean
}
