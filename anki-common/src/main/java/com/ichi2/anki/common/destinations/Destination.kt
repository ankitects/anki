// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

/**
 * A target screen that can be navigated to.
 *
 * Concrete destinations are grouped under a [Destination] subclass per screen.
 *
 * To navigate to a destination, call [navigate].
 *
 * @see Navigator - singleton registration and intent building
 */
sealed class Destination
