/*                                                                                      *
 * Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 */

package com.ichi2.anki.ui.windows.managespace

import com.ichi2.anki.SingleFragmentActivity

/**
 * This activity is called by the system from the app settings to let the user manage the app's
 * used space. The actual work is done in [ManageSpaceFragment] and the fragment is bound to this
 * activity automatically in [SingleFragmentActivity].
 *
 * @see ManageSpaceFragment
 * @see SingleFragmentActivity.onCreate
 */
class ManageSpaceActivity : SingleFragmentActivity()
