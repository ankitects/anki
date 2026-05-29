// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.exception

/** An exception for manual reporting to ACRA  */
class ManuallyReportedException(
    message: String?,
) : RuntimeException(message)
