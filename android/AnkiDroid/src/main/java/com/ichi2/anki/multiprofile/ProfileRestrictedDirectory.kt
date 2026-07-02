/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
