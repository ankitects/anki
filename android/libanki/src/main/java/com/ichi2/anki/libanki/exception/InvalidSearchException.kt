// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.exception

class InvalidSearchException(
    e: Exception,
) : IllegalArgumentException(e.message, e)
