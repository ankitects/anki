/*
 * Copyright (c) 2026 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.preferences

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.PreferenceXmlSource
import com.ichi2.testutils.getInstanceFromClassName
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RelatedSettingsPreferenceTest {
    /**
     * Guarantees that the usages of [com.ichi2.preferences.RelatedSettingsPreference]
     * on the settings screens are correcting by checking if:
     * 1. There is a `app:relatedTitles` and a `relatedFragments` attribute on the XML
     * 2. Both aren't empty and have the same size
     * 3. The fragments in `relatedFragments` are valid
     */
    @Test
    fun testRelatedSettingsPreferencesAreValid() {
        val namespace = "http://schemas.android.com/apk/res-auto"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fragments = PreferenceTestUtils.getAllPreferencesFragments(context).filterIsInstance<PreferenceXmlSource>()

        fragments.forEach { xmlSource ->
            val xmlRes = xmlSource.preferenceResource
            val xrp =
                context.resources.getXml(xmlRes).apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
                }

            while (xrp.eventType != XmlPullParser.END_DOCUMENT) {
                if (xrp.eventType != XmlPullParser.START_TAG || xrp.name != "com.ichi2.preferences.RelatedSettingsPreference") {
                    xrp.next()
                    continue
                }
                val fragmentName = xmlSource::class.java.simpleName

                val titlesAttr = xrp.getAttributeValue(namespace, "relatedTitles")
                val fragmentsAttr = xrp.getAttributeValue(namespace, "relatedFragments")

                assertNotNull("[$fragmentName] 'relatedTitles' is missing", titlesAttr)
                assertNotNull("[$fragmentName] 'relatedFragments' is missing", fragmentsAttr)

                val titles = PreferenceTestUtils.attrToStringArray(titlesAttr!!, context)
                val fragmentDestinations = PreferenceTestUtils.attrToStringArray(fragmentsAttr!!, context)

                assertTrue(titles.isNotEmpty(), "[$fragmentName] 'relatedTitles' must not be empty")
                assertTrue(fragmentDestinations.isNotEmpty(), "[$fragmentName] 'relatedFragments' must not be empty")
                assertEquals(
                    titles.size,
                    fragmentDestinations.size,
                    "[$fragmentName] The titles and fragments arrays must be the exact same size",
                )

                // check if the fragments strings are valid
                fragmentDestinations.forEach { destFragmentName ->
                    assertDoesNotThrow<Fragment> {
                        getInstanceFromClassName(destFragmentName)
                    }
                }
                xrp.next()
            }
        }
    }
}
