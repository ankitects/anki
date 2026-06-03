/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
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
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.sync.SyncAuth
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber

/**
 * ViewModel that manages the state for user login. It handles the login process,
 * validates input fields, and provides results for login operations.
 */
class LoginViewModel : ViewModel() {
    val loginButtonEnabled: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val userNameError: StateFlow<LoginError?>
        field = MutableStateFlow<LoginError?>(null)

    val passwordError: StateFlow<LoginError?>
        field = MutableStateFlow<LoginError?>(null)

    val loginState: StateFlow<LoginState>
        field = MutableStateFlow<LoginState>(LoginState.Idle)

    fun onUserNameFocusChange(
        hasFocus: Boolean,
        userName: String,
    ) {
        userNameError.value = if (!hasFocus && userName.isEmpty()) LoginError.EMPTY_USERNAME else null
    }

    fun onPasswordFocusChange(
        hasFocus: Boolean,
        password: String,
    ) {
        passwordError.value = if (!hasFocus && password.isEmpty()) LoginError.EMPTY_PASSWORD else null
    }

    fun onTextChanged(
        userName: String,
        password: String,
    ) {
        loginButtonEnabled.value = userName.isNotEmpty() && password.isNotEmpty()
    }

    /**
     * Handles the login process by attempting to authenticate the user with the provided credentials.
     * If authentication is successful, it updates the login state; otherwise, it handles authentication failure.
     *
     * @param username The username entered by the user.
     * @param password The password entered by the user.
     * @param endpoint An endpoint for authentication.
     */
    @NeedsTest("updateLogin/_loginState changes after an exception")
    fun handleLogin(
        username: String,
        password: String,
        endpoint: String?,
    ) {
        Timber.i("Logging in")
        viewModelScope.launch {
            try {
                val auth = syncLogin(username, password, endpoint)
                Timber.i("Login success")
                updateLogin(username, auth.hkey)
                loginState.value = LoginState.Success
            } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
                Timber.i("Login auth failed")
                updateLogin("", "")
                loginState.value = LoginState.Error(exc)
            } catch (exc: Exception) {
                // do not log the error, can contain PII
                Timber.w("Login error")
                loginState.value = LoginState.Error(exc)
            }
        }
    }

    private suspend fun syncLogin(
        username: String,
        password: String,
        endpoint: String?,
    ): SyncAuth =
        withCol {
            syncLogin(username, password, endpoint)
        }

    private fun updateLogin(
        username: String,
        hkey: String,
    ) {
        Prefs.username = username
        Prefs.hkey = hkey
    }
}

/**
 * Enum representing the possible error states for the login fields.
 * It is used to track errors related to the username and password fields.
 */
enum class LoginError(
    @StringRes private val messageResId: Int,
) {
    EMPTY_USERNAME(R.string.invalid_email),
    EMPTY_PASSWORD(R.string.password_empty),
    ;

    fun toHumanReadableString(context: Context): String = context.getString(this.messageResId)
}

/** Handles the Login State */
sealed class LoginState {
    data object Idle : LoginState()

    data object Success : LoginState()

    /**
     * The error here is an exception from the login attempt itself i.e. [net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncAuthFailedException]
     *
     * This may contain PII as it comes from the backend
     */
    data class Error(
        val exception: Exception,
    ) : LoginState()
}
