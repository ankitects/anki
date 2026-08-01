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
package com.ichi2.anki.cardviewer

import android.annotation.SuppressLint
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CardTemplateTest {
    @Test
    fun replaceTest() {
        // Method is sped up - ensure that it still works.
        val content = "foo"
        val style = "bar"
        val cardClass = "baz"
        val script = "script"
        val result = CardTemplate(data).render(content, style, script, cardClass).html
        assertThat(
            result,
            equalTo(
                data
                    .replace(
                        "::content::",
                        content,
                    ).replace("::style::", style)
                    .replace("::class::", cardClass)
                    .replace("::script::", script),
            ),
        )
    }

    @Test
    @SuppressLint("CheckResult") //  .render
    fun stressTest() {
        // At length = 10000000
        // ~500ms before
        // < 200 after
        val stringLength = 1000
        val content = String(CharArray(stringLength)).replace('\u0000', 'a')
        assertDoesNotThrow { CardTemplate(data).render(content, content, "", content) }
    }

    companion object {
        @Language("HTML")
        private val data = """<!doctype html>
<html class="mobile android linux js">
    <head>
        <title>AnkiDroid Flashcard</title>
        <meta charset="utf-8">
        <link rel="stylesheet" type="text/css" href="/assets/flashcard.css">
        <link rel="stylesheet" type="text/css" href="/assets/mathjax/mathjax.css">
        <style>
        ::style::
        </style>
        ::script::
        <script src="/assets/mathjax/conf.js"> </script>
        <script src="/assets/mathjax/MathJax.js"> </script>
        <script src="/assets/scripts/card.js" type="text/javascript"> </script>
    </head>
    <body class="::class::">
        <div id="content">
        ::content::
        </div>
    </body>
</html>
"""
    }
}
