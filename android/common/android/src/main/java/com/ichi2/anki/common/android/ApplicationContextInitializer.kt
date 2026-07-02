// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.android

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * The AnkiDroid [Application] context. Set once during `AnkiDroidApp.onCreate()`
 * via [ApplicationContextInitializer.setInstance].
 *
 * Typed as [Context] since most callers only need that level of API. The setter
 * accepts [Application] to make the lifetime guarantee explicit.
 *
 * Holding the Application statically is safe: it lives for the entire process.
 * The lint warning about static `Context` fields targets Activity/View/Service
 * contexts that have shorter lifecycles.
 */
lateinit var appContext: Context
    private set

object ApplicationContextInitializer {
    /**
     * Sets the [appContext]. Called once from `AnkiDroidApp.onCreate()`.
     */
    fun setInstance(app: Application) {
        appContext = app
    }

    /**
     * The [appContext] if it has been initialized, or `null` if not.
     *
     * Prefer this over checking initialization state explicitly it returns the
     * context (or null) in one read.
     */
    val instanceOrNull: Context?
        get() = if (::appContext.isInitialized) appContext else null

    /**
     * Clears [appContext]. Tests use this to simulate the `bmgr restore` scenario
     * where `AnkiDroidApp.onCreate()` does not run.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun clearForTesting() {
        // Top-level `lateinit var appContext` compiles to a static field on the
        // ApplicationContextInitializerKt file class. Clear it via plain Java
        // reflection so this module doesn't need to depend on kotlin-reflect at runtime.
        val cls = Class.forName("com.ichi2.anki.common.android.ApplicationContextInitializerKt")
        val field = cls.getDeclaredField("appContext")
        field.isAccessible = true
        field.set(null, null)
    }
}
