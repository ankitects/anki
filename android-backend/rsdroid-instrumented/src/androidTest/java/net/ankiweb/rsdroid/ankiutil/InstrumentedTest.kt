/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb.rsdroid.ankiutil

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendFactory.getBackend
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before

open class InstrumentedTest {
    private val backendList: MutableList<Backend> = ArrayList()

    /** Whether it's expected that the backend will be in a panicked state */
    protected var panicExpected: Boolean = false

    @Before
    fun before() {
        panicExpected = false
        /*
        Timber added 1 minute to the stress test (1m18 -> 2m30). Didn't seem worth it.
        Timber.uprootAll();
        Timber.plant(new Timber.DebugTree());
         */
    }

    @After
    fun after() {
        for (b in backendList) {
            if (b.isOpen()) {
                val numbers: List<Int> =
                    try {
                        b.getActiveSequenceNumbers()
                    } catch (exc: BackendInvalidInputException) {
                        assertThat(
                            exc.localizedMessage,
                            Matchers.containsString("CollectionNotOpen"),
                        )
                        continue
                    } catch (exc: BackendException.BackendFatalError) {
                        if (panicExpected) {
                            continue
                        }
                        throw IllegalStateException("backend panicked without `panicExpected`", exc)
                    }
                assertThat(
                    "All database cursors should be closed",
                    numbers,
                    Matchers.empty(),
                )
            }
        }
        backendList.clear()
    }

    protected fun getAssetFilePath(fileName: String): String =
        try {
            Shared.getTestFilePath(context, fileName)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    protected val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    protected fun getBackend(fileName: String): Backend {
        System.loadLibrary("rsdroid")
        val path = getAssetFilePath(fileName)
        return getBackendFromPath(path)
    }

    protected val memoryBackend: Backend
        get() {
            System.loadLibrary("rsdroid")
            return getBackendFromPath(":memory:")
        }

    protected fun getBackendFromPath(path: String?): Backend {
        val backend = closedBackend
        backend.setPageSize(TEST_PAGE_SIZE.toLong())
        backend.openCollection(path!!)
        backendList.add(backend)
        return backend
    }

    protected val closedBackend: Backend
        get() = getBackend()

    companion object {
        init {
            Log.e("InstrumentedTest", "Timber has been disabled.")
        }

        const val TEST_PAGE_SIZE = 1000
        val isEmulator: Boolean
            /**
             * This is how google detects emulators in flutter and how react-native does it in the device info module
             * https://github.com/react-native-community/react-native-device-info/blob/bb505716ff50e5900214fcbcc6e6434198010d95/android/src/main/java/com/learnium/RNDeviceInfo/RNDeviceModule.java#L185
             * @return boolean true if the execution environment is most likely an emulator
             */
            get() = (
                Build.BRAND.startsWith("generic") &&
                    Build.DEVICE.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("unknown") ||
                    Build.HARDWARE.contains("goldfish") ||
                    Build.HARDWARE.contains("ranchu") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    Build.PRODUCT.contains("sdk_google") ||
                    Build.PRODUCT.contains("google_sdk") ||
                    Build.PRODUCT.contains("sdk") ||
                    Build.PRODUCT.contains("sdk_x86") ||
                    Build.PRODUCT.contains("vbox86p") ||
                    Build.PRODUCT.contains("emulator") ||
                    Build.PRODUCT.contains("simulator")
            )
    }
}
