// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TestUtils")

/** make default HTML / JS debugging true for debug build and disable for unit/android tests
 * isRunningAsUnitTest checks if we are in debug or testing environment by checking if org.junit.Test class
 * is imported.
 * https://stackoverflow.com/questions/28550370/how-to-detect-whether-android-app-is-running-ui-test-with-espresso
 */
val isRunningAsUnitTest: Boolean
    get() {
        try {
            Class.forName("org.junit.Test")
        } catch (ignored: ClassNotFoundException) {
            logger.debug("isRunningAsUnitTest: {}", false)
            return false
        }
        logger.debug("isRunningAsUnitTest: {}", true)
        return true
    }
