/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tests

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import com.ichi2.themes.Themes.setTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(Parameterized::class)
class LayoutValidationTest : InstrumentedTest() {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    var resourceId = 0

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var name: String? = null

    @Test
    @Throws(Exception::class)
    fun ensureLayout() {
        // This should be fine to run on a device - but WebViews may be instantiated.
        // TODO: GestureDisplay.kt - why was mSwipeView.drawable null
        val targetContext = testContext
        setTheme(targetContext)
        val li = LayoutInflater.from(targetContext)
        val root: ViewGroup = LinearLayout(targetContext)
        ensureNoCrashOnUiThread { li.inflate(resourceId, root, true) }
    }

    /** Crashing on the UI thread takes down the process  */
    @Throws(Exception::class)
    private fun ensureNoCrashOnUiThread(runnable: Runnable) {
        val failed = AtomicReference<Exception?>()
        val hasRun = AtomicBoolean(false)
        runOnUiThread {
            try {
                runnable.run()
            } catch (e: Exception) {
                failed.set(e)
            } finally {
                hasRun.set(true)
            }
        }
        while (!hasRun.get()) {
            // spin
        }
        if (failed.get() != null) {
            throw failed.get()!!
        }
    }

    private fun runOnUiThread(runnable: Runnable) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    companion object {
        @Parameterized.Parameters(name = "{1}")
        @Throws(
            IllegalAccessException::class,
            InvocationTargetException::class,
            InstantiationException::class,
        )
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<out Any>> {
            val ctor: Constructor<*> = com.ichi2.anki.R.layout::class.java.declaredConstructors[0]
            ctor.isAccessible = true // Required for at least API 16, maybe later.
            val layout = ctor.newInstance()

            // There are hidden public fields: abc_list_menu_item_layout for example
            val nonAnkiFieldNames = HashSet<String>()
            nonAnkiFieldNames.addAll(getFieldNames(com.google.android.material.R.layout::class.java))
            nonAnkiFieldNames.addAll(getFieldNames(androidx.preference.R.layout::class.java)) // preference_category_material

            // Names of layouts that should be ignored by the layout inflation test.
            // Currently, ignores layouts that use `FragmentContainerView`
            // with a specified fragment name, as these would currently fail the test, throwing:
            //   UnsupportedOperationException: FragmentContainerView must be within
            //   a FragmentActivity to use android:name="..."
            val ignoredLayoutIds =
                listOf(
                    com.ichi2.anki.R.layout.activity_introduction,
                    com.ichi2.anki.R.layout.fragment_reviewer,
                    com.ichi2.anki.R.layout.fragment_preferences,
                    com.ichi2.anki.R.layout.fragment_drawing,
                    com.ichi2.anki.R.layout.fragment_card_browser_searchview,
                ) +
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        listOf(com.ichi2.anki.R.layout.widget_small_unthemed)
                    } else {
                        emptyList()
                    }

            return layout::class.java.fields
                .map { arrayOf<Any>(it.getInt(layout), it.name) }
                .filterNot { (id, name) -> name in nonAnkiFieldNames || id in ignoredLayoutIds }
        }

        private fun <T> getFieldNames(clazz: Class<T>): HashSet<String> {
            val badFields = clazz.fields
            val badFieldNames = HashSet<String>()
            for (f in badFields) {
                badFieldNames.add(f.name)
            }
            return badFieldNames
        }
    }
}
