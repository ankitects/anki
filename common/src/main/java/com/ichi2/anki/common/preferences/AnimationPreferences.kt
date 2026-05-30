// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.common.preferences

/**
 * Preferences controlling how the app displays animations.
 */
interface AnimationPreferences {
    /**
     * Whether the user has opted to remove app animations
     * (the 'Safe display mode' / 'Remove animations' accessibility setting).
     */
    val removeAppAnimations: Boolean
}
