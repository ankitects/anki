// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.ichi2.anki.SingleFragmentActivity
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * A [SingleFragmentActivity] for which no configuration changes handling is declared in the
 * manifest.
 *
 * Note: do NOT add any configuration changes in the manifest for this activity. Either use [SingleFragmentActivity]
 * or declare your own copy.
 */
class ConfigAwareSingleFragmentActivity : SingleFragmentActivity() {
    companion object {
        fun getIntent(
            context: Context,
            fragmentClass: KClass<out Fragment>,
            arguments: Bundle? = null,
            intentAction: String? = null,
        ): Intent =
            Intent(context, ConfigAwareSingleFragmentActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_NAME, fragmentClass.jvmName)
                putExtra(EXTRA_FRAGMENT_ARGS, arguments)
                action = intentAction
            }
    }
}
