// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.logging

import android.util.Log
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** Sends SLF4J logs to Timber. SLF4J finds it through `META-INF/services`. */
class TimberServiceProvider : SLF4JServiceProvider {
    private val loggerFactory = TimberLoggerFactory()
    private val markerFactory = BasicMarkerFactory()
    private val mdcAdapter = NOPMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory

    override fun getMarkerFactory(): IMarkerFactory = markerFactory

    override fun getMDCAdapter(): MDCAdapter = mdcAdapter

    override fun getRequestedApiVersion(): String = "2.0.99"

    override fun initialize() {}
}

private class TimberLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, Logger>()

    override fun getLogger(name: String): Logger = loggers.computeIfAbsent(name) { TimberLogger(it) }
}

private class TimberLogger(
    name: String,
) : LegacyAbstractLogger() {
    private val tag = name.substringAfterLast('.')

    init {
        this.name = name
    }

    override fun isTraceEnabled() = true

    override fun isDebugEnabled() = true

    override fun isInfoEnabled() = true

    override fun isWarnEnabled() = true

    override fun isErrorEnabled() = true

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any?>?,
        throwable: Throwable?,
    ) {
        val message = MessageFormatter.basicArrayFormat(messagePattern, arguments)
        Timber.tag(tag).log(level.priority, throwable, message)
    }
}

private val Level.priority: Int
    get() =
        when (this) {
            Level.TRACE -> Log.VERBOSE
            Level.DEBUG -> Log.DEBUG
            Level.INFO -> Log.INFO
            Level.WARN -> Log.WARN
            Level.ERROR -> Log.ERROR
        }
