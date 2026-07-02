/*
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.misc

import com.ichi2.testutils.assertFalse
import org.junit.Test
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import javax.xml.parsers.SAXParserFactory
import kotlin.test.assertTrue

/**
 * Test to verify that we don't end up with multiple declarations of a lint rule in lint-release.xml.
 */
class LintReleaseFileTest {
    @Test
    fun failsWithMultipleDeclarations() {
        // this runs in the AnkiDroid module folder so we need go up one level
        val lintReleaseFile = File("../lint-release.xml")
        assertTrue(lintReleaseFile.exists(), "lint-release.xml was not found")
        val parser = SAXParserFactory.newInstance().newSAXParser()
        val seenIssues = mutableListOf<String>()
        parser.parse(
            lintReleaseFile,
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes?,
                ) {
                    if (qName != null && qName == "issue") {
                        if (attributes != null) {
                            val currentIssue = attributes.getValue("id")
                            assertFalse(
                                "Duplicate $currentIssue lint rule in lint-release.xml",
                                seenIssues.contains(currentIssue),
                            )
                            seenIssues.add(currentIssue)
                        }
                    }
                }
            },
        )
    }
}
