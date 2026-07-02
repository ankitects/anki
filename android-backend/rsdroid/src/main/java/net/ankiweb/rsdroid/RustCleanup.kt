/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package net.ankiweb.rsdroid

/**
 * Specifies that the provided class requires attention during the Rust conversion
 * These act as TODOs and should be audited before a production release is produced
 * After the Rust conversion is completed, this class should be deleted.
 */
@JvmRepeatable(RustCleanupCollection::class)
@kotlin.annotation.Retention(AnnotationRetention.SOURCE)
annotation class RustCleanup(
    /** Context and rationale for the cleanup, and the action which will be taken  */
    val value: String,
)
