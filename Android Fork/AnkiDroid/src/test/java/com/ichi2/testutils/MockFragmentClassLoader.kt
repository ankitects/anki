// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.fragment.app.Fragment

/** HACK: Mockito had issues with mocking a class loader: https://github.com/ankidroid/Anki-Android/pull/10048 */
class MockFragmentClassLoader : ClassLoader() {
    override fun loadClass(name: String?): Class<*> {
        if (name == FAKE_CLASS_NAME) return Fragment::class.java
        throw IllegalStateException("Only intended for class: '$name'")
    }

    companion object {
        const val FAKE_CLASS_NAME: String = "androidx.fragment.app.Fragment"
    }
}
