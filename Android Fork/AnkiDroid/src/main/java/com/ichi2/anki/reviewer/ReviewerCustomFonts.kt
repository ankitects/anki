// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.anki.reviewer

class ReviewerCustomFonts {
    /**
     * The CSS used to set the theme font.
     */
    private val customStyle =
        "BODY {font-family: 'OpenSans';font-weight: normal;font-style: normal;font-stretch: normal;}"

    fun updateCssStyle(cssStyle: StringBuilder) {
        cssStyle.append(customStyle)
    }
}
