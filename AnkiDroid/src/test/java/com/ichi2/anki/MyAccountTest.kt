/*
 * Copyright (c) 2022 Ali Ahnaf <aliahnaf327@gmail.com>
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
 * this program.  If not, see http://www.gnu.org/licenses/>.
 *
 */

package com.ichi2.anki

import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.account.LoginFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.launchFragmentInContainer
import junit.framework.TestCase.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MyAccountTest : RobolectricTest() {
    @Before
    fun setup() {
        Prefs.username = ""
        Prefs.hkey = ""
    }

    @Test
    fun testLoginEmailPasswordProvided() {
        launchFragmentInContainer<LoginFragment>().use { scenario ->
            scenario.onFragment { fragment ->
                val testPassword = "randomStrongPassword"
                val testEmail = "random.email@example.com"

                fragment.view?.findViewById<TextInputEditText>(R.id.username)?.setText(testEmail)
                fragment.view?.findViewById<TextInputEditText>(R.id.password)?.setText(testPassword)

                val loginButton = fragment.view?.findViewById<Button>(R.id.login_button)
                assertEquals(loginButton?.isEnabled, true)
            }
        }
    }

    @Test
    fun testLoginFailsNoEmailProvided() {
        launchFragmentInContainer<LoginFragment>().use { scenario ->
            scenario.onFragment { fragment ->
                val testPassword = "randomStrongPassword"

                fragment.view?.findViewById<TextInputEditText>(R.id.password)?.setText(testPassword)
                val loginButton = fragment.view?.findViewById<Button>(R.id.login_button)
                assertFalse(loginButton?.isEnabled == true)
            }
        }
    }

    @Test
    fun testLoginFailsNoPasswordProvided() {
        launchFragmentInContainer<LoginFragment>().use { scenario ->
            scenario.onFragment { fragment ->
                val testEmail = "random.email@example.com"

                fragment.view?.findViewById<TextInputEditText>(R.id.username)?.setText(testEmail)
                val loginButton = fragment.view?.findViewById<Button>(R.id.login_button)
                assertFalse(loginButton?.isEnabled == true)
            }
        }
    }
}
