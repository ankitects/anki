/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.testutils

import androidx.recyclerview.widget.RecyclerView

object RecyclerViewUtils {
    inline fun <reified VH : RecyclerView.ViewHolder?> viewHolderAt(
        recyclerView: RecyclerView,
        position: Int,
    ): VH = recyclerView.findViewHolderForAdapterPosition(position) as VH
}
