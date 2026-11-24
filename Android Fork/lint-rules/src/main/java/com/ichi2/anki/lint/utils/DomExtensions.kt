// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.utils

import org.w3c.dom.Node

fun Node.childrenSequence() =
    sequence {
        var current = firstChild

        while (current != null) {
            yield(current)
            current = current.nextSibling
        }
    }
