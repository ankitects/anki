// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.exception

/**
 * If a known storage path is unusable
 *
 * @see [SystemStorageException] if no path is available
 */
class StorageAccessException(
    msg: String? = null,
    e: Throwable? = null,
) : Exception(msg, e)
