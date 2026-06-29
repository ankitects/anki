// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Context
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.testutils.getInstanceFromClassName
import org.xmlpull.v1.XmlPullParser

object PreferenceTestUtils {
    fun getAttrFromXml(
        context: Context,
        @XmlRes xml: Int,
        attrName: String,
        namespace: String = AnkiDroidApp.ANDROID_NAMESPACE,
        excludeTags: Set<String> = emptySet(),
    ): List<String> {
        val occurrences = mutableListOf<String>()

        val xrp =
            context.resources.getXml(xml).apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
            }

        while (xrp.eventType != XmlPullParser.END_DOCUMENT) {
            val name = xrp.name
            if (xrp.eventType == XmlPullParser.START_TAG) {
                if (name !in excludeTags) {
                    val attr = xrp.getAttributeValue(namespace, attrName)
                    if (attr != null) {
                        occurrences.add(attr)
                    }
                }
            }
            xrp.next()
        }
        return occurrences.toList()
    }

    fun getAttrsFromXml(
        context: Context,
        @XmlRes xml: Int,
        attrNames: List<String>,
        namespace: String = AnkiDroidApp.ANDROID_NAMESPACE,
        excludeTags: Set<String> = emptySet(),
    ): List<Map<String, String>> {
        val occurrences = mutableListOf<Map<String, String>>()

        val xrp =
            context.resources.getXml(xml).apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
            }

        while (xrp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xrp.eventType == XmlPullParser.START_TAG) {
                if (xrp.name !in excludeTags) {
                    val attrValues = attrNames.associateWith { xrp.getAttributeValue(namespace, it) }
                    occurrences.add(attrValues)
                }
            }
            xrp.next()
        }
        return occurrences.toList()
    }

    /** @return fragments found on [xml] */
    private fun getFragmentsFromXml(
        context: Context,
        @XmlRes xml: Int,
    ): List<Fragment> = getAttrFromXml(context, xml, "fragment").map { getInstanceFromClassName(it) }

    /** @return recursively fragments found on [xml] and on their children **/
    private fun getFragmentsFromXmlRecursively(
        context: Context,
        @XmlRes xml: Int,
    ): List<Fragment> {
        val fragments = getFragmentsFromXml(context, xml).toMutableList()
        for (fragment in fragments.filterIsInstance<PreferenceXmlSource>()) {
            fragments.addAll(getFragmentsFromXmlRecursively(context, fragment.preferenceResource))
        }
        return fragments.toList()
    }

    /** @return [List] of all the distinct preferences fragments **/
    fun getAllPreferencesFragments(context: Context): List<Fragment> {
        val fragments = getFragmentsFromXmlRecursively(context, R.xml.preference_headers) + HeaderFragment()
        return fragments.distinctBy { it::class } // and remove any repeated fragments
    }

    context(test: RobolectricTest)
    fun String.resValue(): String = resValue(test.targetContext)

    fun String.resValue(context: Context): String =
        if (this.startsWith("@")) {
            context.getString(this.substring(1).toInt())
        } else {
            this
        }

    fun attrToStringArray(
        value: String,
        context: Context,
    ): Array<String> = context.resources.getStringArray(value.substring(1).toInt())

    fun getKeysFromXml(
        context: Context,
        @XmlRes xml: Int,
        excludeCategories: Boolean = false,
    ): List<String> {
        val exclusions =
            if (excludeCategories) {
                setOf("PreferenceCategory", "com.ichi2.anki.preferences.ExtendedPreferenceCategory")
            } else {
                emptySet()
            }
        return getAttrFromXml(context, xml, "key", excludeTags = exclusions)
            .map { it.resValue(context) }
    }

    fun getAllPreferenceKeys(context: Context): Set<String> =
        getAllPreferencesFragments(context)
            .filterIsInstance<PreferenceXmlSource>()
            .map { it.preferenceResource }
            .flatMapTo(hashSetOf()) {
                getKeysFromXml(context, it, excludeCategories = false)
            } + ViewerCommand.entries.map { it.preferenceKey }

    fun getAllCustomButtonKeys(context: Context): Set<String> {
        val keys = getKeysFromXml(context, R.xml.preferences_custom_buttons).toMutableSet()
        keys.remove("reset_custom_buttons")
        keys.remove("appBarButtonsScreen")
        return keys
    }

    fun getDeveloperOptionsKeys(context: Context): Set<String> = getKeysFromXml(context, R.xml.preferences_developer_options).toSet()
}
