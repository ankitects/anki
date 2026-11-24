// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class FragmentResultKeyNamingDetectorTest {
    private fun check(source: String) =
        lint()
            .allowMissingSdk()
            .files(bundle, manager, fragment, extensions, activityExtensions, kotlin(source).indented())
            .issues(FragmentResultKeyNamingDetector.ISSUE)
            .run()

    @Test
    fun `reports payload keys in inline and local bundles`() {
        check(
            """
            package test
            import android.os.Bundle
            import androidx.fragment.app.*
            const val BAD_KEY = "value"
            fun send(manager: FragmentManager) {
                manager.setFragmentResult("request", Bundle().apply { putString(BAD_KEY, "payload") })
                val result = Bundle()
                result.putString(BAD_KEY, "payload")
                val alias = result
                manager.setFragmentResult("request", alias)
            }
        """,
        ).expectErrorCount(2)
            .expectContains("Fragment Result API bundle key constants must start with RESULT_: BAD_KEY")
    }

    @Test
    fun `reports ordinary public and private Kotlin val properties as well as const val`() {
        check(
            """
            package test
            import android.os.Bundle
            import androidx.fragment.app.*
            val TOP_LEVEL = "top"
            object Keys { val OBJECT_KEY = "object" }
            class Screen : Fragment() {
                companion object {
                    val PUBLIC_KEY = "public"
                    private val PRIVATE_KEY = "private"
                    const val CONST_KEY = "constant"
                }
                fun send() {
                    setFragmentResult(result = Bundle().apply {
                        putString(TOP_LEVEL, "value")
                        putString(Keys.OBJECT_KEY, "value")
                        putString(Screen.PUBLIC_KEY, "value")
                        putString(PRIVATE_KEY, "value")
                        putString(CONST_KEY, "value")
                    }, requestKey = "request")
                }
            }
        """,
        ).expectErrorCount(5)
    }

    @Test
    fun `checks manager fragment and activity listener payloads`() {
        check(
            """
            package test
            import androidx.fragment.app.*
            import com.ichi2.anki.utils.ext.setFragmentResultListener
            const val BAD_KEY = "value"
            class Screen : Fragment() {
                fun listen(manager: FragmentManager, activity: FragmentActivity) {
                    manager.setFragmentResultListener("request", this) { _, result -> result.getString(BAD_KEY) }
                    setFragmentResultListener("request") { _, result ->
                        val alias = result
                        alias.getString(BAD_KEY)
                    }
                    activity.setFragmentResultListener("request") { _, result -> result.getString(BAD_KEY) }
                }
            }
        """,
        ).expectErrorCount(3)
    }

    @Test
    fun `ignores arguments saved state nested bundles values and unrelated APIs`() {
        check(
            """
            package test
            import android.os.Bundle
            import androidx.fragment.app.*
            const val ARG_VALUE = "argument"
            const val STATE_VALUE = "state"
            const val REQUEST_SEND = "send"
            const val RESULT_VALUE = "result"
            class Other { fun setFragmentResult(key: String, bundle: Bundle) {} }
            fun send(manager: FragmentManager, savedState: Bundle) {
                savedState.putString(STATE_VALUE, "value")
                val args = Bundle().apply { putString(ARG_VALUE, "value") }
                Other().setFragmentResult(REQUEST_SEND, args)
                manager.setFragmentResult(REQUEST_SEND, Bundle().apply {
                    putString(RESULT_VALUE, ARG_VALUE)
                    args.putString(ARG_VALUE, "value")
                    putBundle(RESULT_VALUE, args)
                })
                manager.setFragmentResultListener(REQUEST_SEND, Any()) { _, result ->
                    result.getString(RESULT_VALUE)
                    result.getBundle(RESULT_VALUE)?.getString(ARG_VALUE)
                    args.getString(ARG_VALUE)
                }
            }
        """,
        ).expectClean()
    }

    @Test
    fun `allows runtime keys and compliant properties`() {
        check(
            """
            package test
            import android.os.Bundle
            import androidx.fragment.app.*
            val RESULT_VALUE = "value"
            val dynamicKey = "key_" + System.nanoTime()
            var mutableKey = "mutable"
            val computedKey get() = "computed"
            val delegatedKey by lazy { "delegated" }
            fun send(manager: FragmentManager, forwardedKey: String) {
                manager.setFragmentResult("request", Bundle().apply {
                    putString(RESULT_VALUE, "value")
                    putString(dynamicKey, "value")
                    putString(mutableKey, "value")
                    putString(computedKey, "value")
                    putString(delegatedKey, "value")
                    putString(forwardedKey, "value")
                })
            }
        """,
        ).expectClean()
    }

    @Test
    fun `honors suppression`() {
        check(
            """
            package test
            import android.os.Bundle
            import androidx.fragment.app.FragmentManager
            const val BAD_KEY = "key"
            @Suppress("FragmentResultKeyNaming")
            fun send(manager: FragmentManager) {
                manager.setFragmentResult("request", Bundle().apply { putString(BAD_KEY, "value") })
            }
        """,
        ).expectClean()
    }

    companion object {
        private val bundle =
            kotlin(
                """
            package android.os
            class Bundle {
                fun putString(key: String, value: String) {}
                fun putBundle(key: String, value: Bundle) {}
                fun getString(key: String): String? = null
                fun getBundle(key: String): Bundle? = null
            }
        """,
            ).indented()
        private val manager =
            kotlin(
                """
            package androidx.fragment.app
            import android.os.Bundle
            class FragmentManager {
                fun setFragmentResult(requestKey: String, result: Bundle) {}
                fun setFragmentResultListener(requestKey: String, owner: Any, listener: (String, Bundle) -> Unit) {}
            }
        """,
            ).indented()
        private val fragment =
            kotlin(
                """
            package androidx.fragment.app
            open class Fragment
            class FragmentActivity
        """,
            ).indented()
        private val extensions =
            kotlin(
                """
            @file:JvmName("FragmentKt")
            package androidx.fragment.app
            import android.os.Bundle
            fun Fragment.setFragmentResult(requestKey: String, result: Bundle) {}
            fun Fragment.setFragmentResultListener(requestKey: String, listener: (String, Bundle) -> Unit) {}
        """,
            ).indented()
        private val activityExtensions =
            kotlin(
                """
            @file:JvmName("FragmentResultKt")
            package com.ichi2.anki.utils.ext
            import android.os.Bundle
            import androidx.fragment.app.FragmentActivity
            fun FragmentActivity.setFragmentResultListener(requestKey: String, listener: (String, Bundle) -> Unit) {}
        """,
            ).indented()
    }
}
