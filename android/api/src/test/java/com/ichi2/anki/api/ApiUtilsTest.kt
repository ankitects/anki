//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.api

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Created by rodrigobresan on 19/10/17.
 *
 *
 * In case of any questions, feel free to ask me
 *
 *
 * E-mail: rcbresan@gmail.com
 * Slack: bresan
 */
@RunWith(RobolectricTestRunner::class)
internal class ApiUtilsTest {
    @Test
    fun joinFieldsShouldJoinWhenListIsValid() {
        val fieldList = arrayOf("A", "B", "C")
        assertEquals("A" + DELIMITER + "B" + DELIMITER + "C", Utils.joinFields(fieldList))
    }

    @Test
    fun joinFieldsShouldReturnNullWhenListIsNull() {
        assertNull(Utils.joinFields(null))
    }

    @Test
    fun splitFieldsShouldSplitRightWhenStringIsValid() {
        val fieldList = "A" + DELIMITER + "B" + DELIMITER + "C"
        val output = Utils.splitFields(fieldList)
        assertEquals("A", output[0])
        assertEquals("B", output[1])
        assertEquals("C", output[2])
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsValid() {
        val tags = setOf("A", "B", "C")
        assertEquals("A B C", Utils.joinTags(tags))
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsNull() {
        assertEquals("", Utils.joinTags(null))
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsEmpty() {
        assertEquals("", Utils.joinTags(emptySet()))
    }

    @Test
    fun joinTagsShouldReplaceSpacesWithUnderscores() {
        assertEquals("multi_word tag2", Utils.joinTags(linkedSetOf("multi word", "tag2")))
    }

    @Test
    fun splitTagsShouldReturnNullWhenStringIsValid() {
        val tags = "A B C"
        val output = Utils.splitTags(tags)
        assertEquals("A", output[0])
        assertEquals("B", output[1])
        assertEquals("C", output[2])
    }

    @Test
    fun shouldGenerateProperCheckSum() {
        assertEquals(3533307532L, Utils.fieldChecksum("AnkiDroid"))
    }

    companion object {
        // We need to keep a copy because a change to Utils.FIELD_SEPARATOR should break the tests
        private const val DELIMITER = "\u001F"
    }
}
