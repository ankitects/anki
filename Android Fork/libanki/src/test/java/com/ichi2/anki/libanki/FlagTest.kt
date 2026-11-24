// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.libanki.testutils.ext.newNote
import com.ichi2.anki.libanki.testutils.ext.setFlag
import org.junit.Test
import kotlin.test.assertEquals

class FlagTest : InMemoryAnkiTest() {
    /*****************
     ** Flags        *
     *****************/
    @Suppress("CheckResult")
    @Test
    fun test_flags() {
        val n = col.newNote()
        n.setItem("Front", "one")
        n.setItem("Back", "two")
        col.addNote(n)
        val c = n.cards()[0]

        // make sure higher bits are preserved
        val origBits = 0b101 shl 3
        c.update { setFlag(origBits) }
        // no flags to start with
        assertEquals(0, c.userFlag())
        assertEquals(1, col.findCards("flag:0").size)
        assertEquals(0, col.findCards("flag:1").size)
        // set flag 2
        col.setUserFlagForCards(listOf(c.id), 2)
        c.load()
        assertEquals(2, c.userFlag())
        // assertEquals(origBits, c.flags & origBits);TODO: create direct access to real flag value
        assertEquals(0, col.findCards("flag:0").size)
        assertEquals(1, col.findCards("flag:2").size)
        assertEquals(0, col.findCards("flag:3").size)
        // change to 3
        col.setUserFlagForCards(listOf(c.id), 3)
        c.load()
        assertEquals(3, c.userFlag())
        // unset
        col.setUserFlagForCards(listOf(c.id), 0)
        c.load()
        assertEquals(0, c.userFlag())

        // should work with Cards method as well
        c.setUserFlag(2)
        assertEquals(2, c.userFlag())
        c.setUserFlag(3)
        assertEquals(3, c.userFlag())
        c.setUserFlag(0)
        assertEquals(0, c.userFlag())

        // test new flags
        col.setUserFlagForCards(listOf(c.id), 7)
        assertEquals(1, col.findCards("flag:7").size)
    }
}
