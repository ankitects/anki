// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Nishtha Jain <jnishtha305@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class OpenInputStreamSafeDetectorTest {
    @Test
    fun testDirectOpenInputStreamCall() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    class MyClass {
                        fun loadData(resolver: android.content.ContentResolver, uri: android.net.Uri) {
                            val stream = resolver.openInputStream(uri)
                        }
                    }
                    """,
                ).indented(),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectContains("Use openInputStreamSafe() instead of openInputStream()")
    }

    @Test
    fun testOpenInputStreamSafeCall() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    class MyClass {
                        fun loadData(resolver: android.content.ContentResolver, uri: android.net.Uri) {
                            val stream = resolver.openInputStreamSafe(uri)
                        }
                    }
                    """,
                ).indented(),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testJavaDirectOpenInputStreamCall() {
        lint()
            .allowMissingSdk()
            .files(
                java(
                    """
                    public class MyClass {
                        public void loadData(android.content.ContentResolver resolver, android.net.Uri uri) {
                            java.io.InputStream stream = resolver.openInputStream(uri);
                        }
                    }
                    """,
                ).indented(),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectContains("Use openInputStreamSafe() instead of openInputStream()")
    }

    @Test
    fun `openInputStreamSafe is not flagged`() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
@Suppress("UnusedReceiverParameter")
fun android.content.ContentResolver.openInputStreamSafe(uri: Uri): InputStream? {
    return openInputStream(uri)
}
                    """,
                ),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectClean()
    }
}
