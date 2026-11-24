// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.getSystemService
import com.ichi2.anki.common.android.appContext

object NetworkUtils {
    private val connectivityManager: ConnectivityManager?
        get() = appContext.getSystemService()

    /**
     * @return whether the active network is metered
     * or false in case internet cannot be accessed
     */
    fun isActiveNetworkMetered(): Boolean =
        isOnline &&
            connectivityManager
                ?.isActiveNetworkMetered
                ?: true

    /**
     * @return whether is possible to access the internet
     */
    val isOnline: Boolean
        get() {
            val cm = connectivityManager ?: return false

            // ConnectivityManager.activeNetwork is for SDK ≥ 23
            val networkCapabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            val isOnline =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            // on SDK ≥ 29, it can be checked if internet is temporarily disabled as well
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isOnline && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            } else {
                isOnline
            }
        }
}
