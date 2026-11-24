// SPDX-License-Identifier: GPL-3.0-or-later

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
