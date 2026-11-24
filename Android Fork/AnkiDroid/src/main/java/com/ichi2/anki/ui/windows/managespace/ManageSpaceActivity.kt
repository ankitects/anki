// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>                          *

package com.ichi2.anki.ui.windows.managespace

import androidx.fragment.app.Fragment
import com.ichi2.anki.SingleFragmentActivity

/**
 * This activity is called by the system from the app settings to let the user manage the app's
 * used space. The actual work is done in [ManageSpaceFragment] and the fragment is bound to this
 * activity automatically in [SingleFragmentActivity].
 *
 * @see ManageSpaceFragment
 * @see SingleFragmentActivity.onCreate
 */
class ManageSpaceActivity : SingleFragmentActivity() {
    override fun createFragment(): Fragment = ManageSpaceFragment()
}
