// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.libanki.exception

import org.slf4j.LoggerFactory
import java.lang.Exception

private val logger = LoggerFactory.getLogger(ConfirmModSchemaException::class.java)

class ConfirmModSchemaException : Exception() {
    /**
     * Add the current exception to log.
     */
    fun log() {
        logger.trace("Schema change requires confirmation", this)
    }
}
