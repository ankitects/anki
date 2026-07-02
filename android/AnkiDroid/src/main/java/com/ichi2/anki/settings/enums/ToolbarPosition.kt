/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.settings.enums

import com.ichi2.anki.R

/** [R.array.reviewer_toolbar_position_values] */
enum class ToolbarPosition(
    override val entryResId: Int,
) : PrefEnum {
    TOP(R.string.reviewer_toolbar_value_top),
    BOTTOM(R.string.reviewer_toolbar_value_bottom),
    NONE(R.string.reviewer_toolbar_value_none),
}
