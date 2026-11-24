// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File

/** Adapts AndroidX's Unix-only containment check to Robolectric's host filesystem. */
@Implements(className = "androidx.core.content.FileProvider\$SimplePathStrategy", isInAndroidSdk = false)
class ShadowFileProviderPathStrategy {
    @Implementation
    protected fun belongsToRoot(
        filePath: String,
        rootPath: String,
    ): Boolean {
        val file = File(filePath).toPath()
        val root = File(rootPath).toPath()
        return file != root && file.startsWith(root)
    }
}
