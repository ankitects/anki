// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import com.ichi2.anki.common.utils.firstGraphemeOrNull
import com.ichi2.anki.multiprofile.ProfileId

/** UI model for a row in the profile list. */
data class ProfileItem(
    val id: ProfileId,
    val name: String,
) {
    /** Uppercased first character of the name, shown in the avatar circle. */
    val initial: String
        get() = name.firstGraphemeOrNull()?.uppercase() ?: "?"
}
