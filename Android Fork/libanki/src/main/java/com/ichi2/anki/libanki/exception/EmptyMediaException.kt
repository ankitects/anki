// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.exception

/**
 * Exception raised when attempting to add an empty, or non-existing media file
 *
 * Empty media files cannot be added to AnkiWeb
 */
class EmptyMediaException : Exception()
