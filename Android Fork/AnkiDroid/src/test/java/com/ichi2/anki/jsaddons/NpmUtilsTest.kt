// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mani infinyte01@gmail.com

package com.ichi2.anki.jsaddons

import com.ichi2.anki.jsaddons.NpmUtils.validateName
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class NpmUtilsTest {
    @Test
    fun validateNameTest() {
        // test case taken from
        // https://stackoverflow.com/questions/50050436/use-npm-package-to-validate-a-package-name

        // valid
        assertTrue(validateName("dashes-hyphens-minus-sign-valid"))
        assertTrue(validateName("example.com-periods-valid"))
        assertTrue(validateName("under_scores-valid"))
        assertTrue(validateName("example-123-numbers-valid"))
        assertTrue(validateName("123-starting-with-numbers-valid"))
        assertTrue(validateName("@scope/at-sign-for-scope-names-valid"))

        // invalid
        assertFalse(validateName(""))
        assertFalse(validateName("slashes@using-at-sign-for-non-scopename-uses-invalid"))
        assertFalse(validateName("slashes/on-nonscoped-names-invalid"))
        assertFalse(validateName("pipes|invalid"))
        assertFalse(validateName("exclamations!for!new!packages!invalid"))
        assertFalse(validateName("space invalid"))
        assertFalse(validateName("html%20entities-invalid"))
        assertFalse(validateName("square[brackets]-invalid"))
        assertFalse(validateName("commas,invalid"))
        assertFalse(validateName("colon:invalid"))
        assertFalse(validateName("semicolon;invalid"))
        assertFalse(validateName("single-quote\'invalid"))
        assertFalse(validateName("double-quote\"invalid"))
        assertFalse(validateName("tilde~invalid"))
        assertFalse(validateName("simple-(brackets)-invalid"))
        assertFalse(validateName("curly-{braces}-invalid"))
        assertFalse(validateName("asterisks*-invalid"))
        assertFalse(validateName("carets^-invalid"))
        assertFalse(validateName("pound-sign#-invalid"))
        assertFalse(validateName("plus-sign+-invalid"))
        assertFalse(validateName("question-mark?-invalid"))
    }
}
