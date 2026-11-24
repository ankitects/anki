// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.compose.runtime.Composer
import anki.i18n.GeneratedTranslations
import anki.i18n.PreviewTranslations
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Uses preview strings without loading Rust in tests that span multiple SDK sandboxes. */
@Implements(className = "com.ichi2.anki.ui.internationalization.ComposableTranslationsKt", isInAndroidSdk = false)
class ShadowPreviewTranslations {
    companion object {
        // Match the compiled signature of the @Composable tr() provider.
        @JvmStatic
        @Implementation
        @Suppress("UNUSED_PARAMETER")
        fun tr(
            composer: Composer?,
            changed: Int,
        ): GeneratedTranslations = PreviewTranslations
    }
}
