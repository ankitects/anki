/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.isLoggedIn

class AccountActivity : SingleFragmentActivity() {
    companion object {
        /** Sees if we want to go back to the DeckPicker after login*/
        const val START_FROM_DECKPICKER = "START_FOR_RESULT"

        /**
         * Returns an [Intent] to launch either [LoggedInFragment] or [LoginFragment]
         * based on the current login state.
         *
         * @param context The context used to create the intent.
         * @param forResult Indicates whether the calling component expects a result.
         * This is used to distinguish if the screen was launched from DeckPicker
         * or any other screen that needs a result back.
         *
         * @return An [Intent] to start the appropriate fragment.
         */
        fun getIntent(
            context: Context,
            forResult: Boolean = false,
        ): Intent =
            getIntent(
                context = context,
                fragmentClass = if (isLoggedIn()) LoggedInFragment::class else LoginFragment::class,
                arguments =
                    Bundle().apply {
                        putBoolean(START_FROM_DECKPICKER, forResult)
                    },
            )
    }
}
