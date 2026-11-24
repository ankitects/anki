// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class FragmentRequestKeyNamingDetectorTest {
    @Test
    fun `reports constants in manager and extension calls`() {
        lint()
            .allowMissingSdk()
            .files(
                manager,
                fragment,
                activity,
                fragmentExtensions,
                activityExtensions,
                kotlin(
                    """
                    package test
                    import androidx.fragment.app.*
                    import com.ichi2.anki.utils.ext.setFragmentResultListener

                    const val RESULT_KEY = "result"
                    class Screen : Fragment() {
                        companion object {
                            const val FRAGMENT_KEY = "fragment"
                        }
                        fun send(manager: FragmentManager, activity: FragmentActivity) {
                            manager.setFragmentResult(RESULT_KEY, "payload")
                            manager.setFragmentResult(Screen.FRAGMENT_KEY, "payload")
                            manager.setFragmentResultListener(RESULT_KEY) {}
                            manager.clearFragmentResult(RESULT_KEY)
                            manager.clearFragmentResultListener(RESULT_KEY)
                            setFragmentResult(result = "payload", requestKey = RESULT_KEY)
                            setFragmentResultListener(RESULT_KEY) {}
                            activity.setFragmentResultListener(RESULT_KEY) {}
                        }
                    }
                    """,
                ).indented(),
            ).issues(FragmentRequestKeyNamingDetector.ISSUE)
            .run()
            .expectErrorCount(8)
            .expectContains("Fragment Result API request key constants must start with REQUEST_: RESULT_KEY")
    }

    @Test
    fun `reports request key properties accessed through Kotlin getters`() {
        lint()
            .allowMissingSdk()
            .files(
                manager,
                fragment,
                fragmentExtensions,
                kotlin(
                    """
                    package test
                    import androidx.fragment.app.*

                    val RESULT_KEY = "result"
                    object Keys {
                        val FRAGMENT_KEY = "fragment"
                    }
                    class Screen : Fragment() {
                        companion object {
                            val REQUE_ = "dfjdsjkfdsjfds"
                        }
                        fun listen(manager: FragmentManager) {
                            setFragmentResultListener(REQUE_) {}
                            manager.setFragmentResult(Screen.REQUE_, "payload")
                            manager.setFragmentResult(RESULT_KEY, "payload")
                            manager.setFragmentResult(Keys.FRAGMENT_KEY, "payload")
                        }
                    }
                    """,
                ).indented(),
            ).issues(FragmentRequestKeyNamingDetector.ISSUE)
            .run()
            .expectErrorCount(4)
            .expectContains("Fragment Result API request key constants must start with REQUEST_: REQUE_")
            .expectContains("Fragment Result API request key constants must start with REQUEST_: RESULT_KEY")
            .expectContains("Fragment Result API request key constants must start with REQUEST_: FRAGMENT_KEY")
    }

    @Test
    fun `allows compliant and nonconstant properties accessed through Kotlin getters`() {
        lint()
            .allowMissingSdk()
            .files(
                manager,
                fragment,
                fragmentExtensions,
                kotlin(
                    """
                    package test
                    import androidx.fragment.app.*

                    val REQUEST_TOP_LEVEL = "topLevel"
                    object Keys {
                        val REQUEST_OBJECT = "object"
                    }
                    class Screen : Fragment() {
                        companion object {
                            val REQUEST_LISTEN = "listen"
                            val dynamicKey = "request_" + System.nanoTime()
                            var mutableKey = "mutable"
                            val computedKey get() = "request_" + System.nanoTime()
                            val delegatedKey by lazy { "request_" + System.nanoTime() }
                        }
                        val instanceKey = "instance"
                        fun listen(manager: FragmentManager) {
                            setFragmentResultListener(REQUEST_LISTEN) {}
                            manager.setFragmentResult(REQUEST_TOP_LEVEL, "payload")
                            manager.setFragmentResult(Keys.REQUEST_OBJECT, "payload")
                            manager.setFragmentResult(dynamicKey, "payload")
                            manager.setFragmentResult(mutableKey, "payload")
                            manager.setFragmentResult(computedKey, "payload")
                            manager.setFragmentResult(delegatedKey, "payload")
                            manager.setFragmentResult(instanceKey, "payload")
                        }
                    }
                    """,
                ).indented(),
            ).issues(FragmentRequestKeyNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows request constants payload keys and dynamic keys`() {
        lint()
            .allowMissingSdk()
            .files(
                manager,
                kotlin(
                    """
                    package test
                    import androidx.fragment.app.FragmentManager

                    object Keys {
                        const val REQUEST_TEST = "test"
                        const val RESULT_VALUE = "value"
                    }
                    fun send(manager: FragmentManager, requestKey: String) {
                        manager.setFragmentResult(Keys.REQUEST_TEST, Keys.RESULT_VALUE)
                        manager.setFragmentResult(requestKey, "payload")
                        val dynamicKey = "request_" + System.nanoTime()
                        manager.setFragmentResult(dynamicKey, "payload")
                    }
                    class Unrelated {
                        fun setFragmentResult(requestKey: String, result: String) {}
                        fun send() = setFragmentResult(Keys.RESULT_VALUE, "payload")
                    }
                    """,
                ).indented(),
            ).issues(FragmentRequestKeyNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        private val manager =
            kotlin(
                """
                package androidx.fragment.app
                class FragmentManager {
                    fun setFragmentResult(requestKey: String, result: String) {}
                    fun setFragmentResultListener(requestKey: String, listener: () -> Unit) {}
                    fun clearFragmentResult(requestKey: String) {}
                    fun clearFragmentResultListener(requestKey: String) {}
                }
                """,
            ).indented()
        private val fragment =
            kotlin(
                """
                package androidx.fragment.app
                open class Fragment
                """,
            ).indented()
        private val activity =
            kotlin(
                """
                package androidx.fragment.app
                class FragmentActivity
                """,
            ).indented()
        private val fragmentExtensions =
            kotlin(
                """
                @file:JvmName("FragmentKt")
                package androidx.fragment.app
                fun Fragment.setFragmentResult(requestKey: String, result: String) {}
                fun Fragment.setFragmentResultListener(requestKey: String, listener: () -> Unit) {}
                """,
            ).indented()
        private val activityExtensions =
            kotlin(
                """
                @file:JvmName("FragmentResultKt")
                package com.ichi2.anki.utils.ext
                import androidx.fragment.app.FragmentActivity
                fun FragmentActivity.setFragmentResultListener(requestKey: String, listener: () -> Unit) {}
                """,
            ).indented()
    }
}
