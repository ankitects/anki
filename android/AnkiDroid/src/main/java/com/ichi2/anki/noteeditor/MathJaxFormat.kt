// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import com.ichi2.anki.CollectionManager.TR

/** Options for inserting MathJax equation types via the toolbar */
enum class MathJaxFormat(
    val prefix: String,
    val suffix: String,
) {
    /** Display-math block: `\[ E=mc^2 \]` */
    BLOCK(prefix = "\\[", suffix = "\\]"),

    /**
     * [mhchem](https://mhchem.github.io/MathJax-mhchem/) chemistry equation: `\( \ce{ H2O } \)`
     */
    CHEMISTRY(prefix = "\\( \\ce{", suffix = "} \\)"),
    ;

    fun toTextWrapper() = Toolbar.TextWrapper(prefix = prefix, suffix = suffix)

    fun label(): String =
        when (this) {
            BLOCK -> TR.editingMathjaxBlock()
            CHEMISTRY -> TR.editingMathjaxChemistry()
        }
}
