// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

fun interface JavascriptEvaluator {
    fun eval(js: String)

    fun evaluateAfterDOMContentLoaded(script: String) {
        eval(
            """
                var codeToRun = function() { 
                    $script
                }
                
                if (document.readyState === "loading") {
                  document.addEventListener("DOMContentLoaded", codeToRun);
                } else {
                  codeToRun();
                }
        """,
        )
    }
}
