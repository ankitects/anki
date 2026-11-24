// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.testutils.EmptyApplication

/**
 * Support de-flaking [EmptyApplication] usages which have an AnkiDroidApp dependency
 *
 * usage:
 *
 * ```kt
 * @Category(EmptyApplicationCategory::class)
 * ```
 *
 * test with:
 *
 * ```bash
 * ./gradlew testFullDebugUnitTest -PemptyApplication
 * ```
 */
interface EmptyApplicationCategory
