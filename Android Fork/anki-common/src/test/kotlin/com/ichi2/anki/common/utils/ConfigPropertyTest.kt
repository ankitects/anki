// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigPropertyTest : InMemoryAnkiTest() {
    @Test
    fun `boolean delegates use the backend defaults`() {
        col.config.remove("addToCur")
        col.config.remove("ignoreAccentsInSearch")

        assertTrue(col.config.defaultToCurrentDeck)
        assertFalse(col.config.ignoreAccents)
    }

    @Test
    fun `boolean reads and writes share storage with the backend`() {
        assertTrue(col.config.defaultToCurrentDeck)
        col.backend.setConfigBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, false, false)
        assertFalse(col.config.defaultToCurrentDeck)

        col.config.defaultToCurrentDeck = true
        assertTrue(col.backend.getConfigBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK))
        col.config.defaultToCurrentDeck = false
        assertFalse(col.backend.getConfigBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK))
    }

    @Test
    fun `string delegates use backend defaults and observe external writes`() {
        col.config.remove("cardStateCustomizer")
        assertEquals("", col.config.customScheduling)

        col.backend.setConfigString(ConfigKey.String.CARD_STATE_CUSTOMIZER, "external", false)
        assertEquals("external", col.config.customScheduling)

        col.config.customScheduling = "delegate"
        assertEquals("delegate", col.backend.getConfigString(ConfigKey.String.CARD_STATE_CUSTOMIZER))
    }

    @Test
    fun `missing JSON can have a different fallback from invalid JSON`() {
        col.config.remove("delegateFlag")
        assertEquals(false, col.config.jsonFlag)

        col.config.set("delegateFlag", "invalid boolean")
        assertNull(col.config.jsonFlag)

        col.config.jsonFlag = null
        assertNull(col.config.jsonFlag)
        assertEquals("null", col.backend.getConfigJson("delegateFlag").toStringUtf8())

        col.config.jsonFlag = true
        assertEquals(true, col.config.get<Boolean>("delegateFlag"))
    }

    @Test
    fun `JSON defaults cover missing null and invalid values without writing them back`() {
        assertEquals(4, col.config.jsonNumber)
        assertNull(col.config.get<Int>("delegateNumber"))

        col.config.set<String?>("delegateNumber", null)
        assertEquals(4, col.config.jsonNumber)
        col.config.set("delegateNumber", "invalid number")
        assertEquals(4, col.config.jsonNumber)
        assertEquals("invalid number", col.config.get<String>("delegateNumber"))

        col.config.set("delegateNumber", 8)
        assertEquals(8, col.config.jsonNumber)
        col.config.jsonNumber = 9
        assertEquals(9, col.config.get<Int>("delegateNumber"))
    }

    @Test
    fun `mapped properties convert both ways and observe external writes`() {
        assertEquals(Destination.CURRENT, col.config.destination)

        col.config.destination = Destination.REMEMBERED
        assertFalse(col.backend.getConfigBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK))
        assertEquals(Destination.REMEMBERED, col.config.destination)

        col.config.destination = Destination.CURRENT
        assertTrue(col.backend.getConfigBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK))

        col.backend.setConfigBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, false, false)
        assertEquals(Destination.REMEMBERED, col.config.destination)
    }

    @Test
    fun `delegates use the receiving collection`() {
        val otherBackend = BackendFactory.getBackend()
        try {
            otherBackend.openCollection(collectionPath = ":memory:")
            val other = Config(otherBackend)
            col.config.destination = Destination.REMEMBERED
            col.config.customScheduling = "first"
            col.config.jsonNumber = 7

            assertEquals(Destination.CURRENT, other.destination)
            assertEquals("", other.customScheduling)
            assertEquals(4, other.jsonNumber)

            other.customScheduling = "second"
            other.jsonNumber = 12
            assertEquals("first", col.config.customScheduling)
            assertEquals(7, col.config.jsonNumber)
            assertEquals(Destination.REMEMBERED, col.config.destination)
        } finally {
            otherBackend.close()
        }
    }

    @Test
    fun `delegated writes retain Config undo behavior`() {
        addBasicNote()
        val before = col.undoStatus()

        col.config.defaultToCurrentDeck = false
        assertEquals(before.undo, col.undoStatus().undo)
        assertEquals(before.redo, col.undoStatus().redo)
        col.undo()
        assertEquals(0, col.noteCount())
        assertFalse(col.config.defaultToCurrentDeck)
    }

    @Test
    fun `backend failures propagate through delegates`() {
        assertEquals(Destination.CURRENT, col.config.destination)
        col.close()

        assertFailsWith<BackendException> { col.config.destination }
        assertFailsWith<BackendException> { col.config.customScheduling = "closed" }
        assertFailsWith<BackendException> { col.config.jsonNumber }
    }
}

private enum class Destination { CURRENT, REMEMBERED }

private var Config.defaultToCurrentDeck by configProperty(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)
private val Config.ignoreAccents by configProperty(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH)
private var Config.customScheduling by configProperty(ConfigKey.String.CARD_STATE_CUSTOMIZER)
private var Config.jsonFlag by jsonConfigProperty("delegateFlag", missingValue = false)
private var Config.jsonNumber by jsonConfigProperty<Int>("delegateNumber").orDefault(4)
private var Config.destination by configProperty(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK).mapped(
    decode = { if (it) Destination.CURRENT else Destination.REMEMBERED },
    encode = { it == Destination.CURRENT },
)
