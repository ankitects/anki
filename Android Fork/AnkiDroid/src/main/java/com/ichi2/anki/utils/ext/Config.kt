// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config

// Extensions for com.ichi2.anki.libanki.Config

/**
 * Default search text for the card browser.
 *
 * An empty string is the upstream default when no value is configured.
 * Anki Desktop's Qt browser may show current-deck behavior when the setting is unset.
 *
 * [source](https://github.com/ankitects/anki/blob/main/rslib/src/config/string.rs)
 */
var Config.defaultBrowserSearch
    get() = getString(ConfigKey.String.DEFAULT_SEARCH_TEXT)
    set(value) = setString(ConfigKey.String.DEFAULT_SEARCH_TEXT, value)
