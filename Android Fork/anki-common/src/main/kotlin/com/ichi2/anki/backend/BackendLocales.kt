// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

import com.google.protobuf.InvalidProtocolBufferException
import com.ichi2.anki.common.utils.ext.stringIterable
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException
import org.json.JSONException
import org.json.JSONObject

/**
 * Locales currently used by a backend for translations.
 *
 * @property all Active locales in preference order, including the final `en-US` fallback.
 */
class BackendLocales internal constructor(
    val all: List<String>,
) {
    /** The first locale tried when translating, before falling back to the remaining locales. */
    val primary: String
        get() = all.first()
}

/**
 * Returns the backend's active translation locales after resolving its preferred languages.
 * The backend uses `en-US` when none of the requested languages are supported.
 * Backend and response-decoding errors propagate to the caller.
 *
 * @throws BackendException if the backend is closed or the request fails.
 * @throws InvalidProtocolBufferException if the backend's protobuf response cannot be decoded.
 * @throws JSONException if the returned JSON cannot be read as an object containing a `langs`
 * array of strings.
 */
fun Backend.getLocales(): BackendLocales {
    // An empty module name omits translation text; an empty list would request all modules.
    val resources = i18nResources(modules = listOf(""))
    val locales = JSONObject(resources.toStringUtf8()).getJSONArray("langs").stringIterable().toList()
    return BackendLocales(locales)
}
