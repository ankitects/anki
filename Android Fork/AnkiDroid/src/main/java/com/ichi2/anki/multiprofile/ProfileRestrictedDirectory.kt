// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import java.io.File

/**
 * Represents a highly sensitive, restricted directory on the device's internal storage
 * belonging to a specific user profile.
 *
 * SECURITY WARNING:
 * This directory holds isolated databases, shared preferences, and internal app data.
 * - DO NOT store arbitrary user-provided media, downloads, or cache files here.
 * - DO NOT expose paths from this directory to external intents or FileProviders.
 * - Any path traversal vulnerabilities here could leak another user's private data.
 */
@JvmInline
value class ProfileRestrictedDirectory(
    val file: File,
)
