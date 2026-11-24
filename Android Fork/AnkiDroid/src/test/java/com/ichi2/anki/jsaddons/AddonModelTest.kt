// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mani infinyte01@gmail.com

package com.ichi2.anki.jsaddons

import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.jsaddons.AddonsConst.ANKIDROID_JS_ADDON_KEYWORDS
import com.ichi2.anki.jsaddons.AddonsConst.REVIEWER_ADDON
import com.ichi2.utils.FileOperation
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringEndsWith.endsWith
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.test.assertIs

class AddonModelTest {
    private lateinit var validNpmPackageJson: File
    private lateinit var notValidNpmPackageJson: File
    private lateinit var addonsPackageListTestJson: String

    @Before
    fun setUp() {
        validNpmPackageJson = File(FileOperation.getFileResource("valid-ankidroid-js-addon-test.json"))
        notValidNpmPackageJson = File(FileOperation.getFileResource("not-valid-ankidroid-js-addon-test.json"))
        addonsPackageListTestJson = FileOperation.getFileResource("test-js-addon.json")
    }

    @Test
    @Throws(IOException::class)
    fun isValidAnkiDroidAddonTest() {
        // test addon is valid or not, for a valid addon the result is Valid
        val result = getAddonModelFromJson(validNpmPackageJson)
        val addon = assertIs<AddonValidationResult.Valid>(result, "package.json contains required fields").addonModel

        // needs to test these fields
        assertEquals(addon.name, "valid-ankidroid-js-addon-test")
        assertEquals(addon.addonTitle, "Valid AnkiDroid JS Addon")
        assertEquals(addon.version, "1.0.0")
        assertEquals(addon.ankidroidJsApi, "0.0.3")
        assertEquals(addon.addonType, "reviewer")
        assertEquals(addon.icon, "") // reviewer icon is empty

        val expected: List<String> = listOf("ankidroid-js-addon")
        assertEquals(addon.keywords, expected)
    }

    @Test
    @Throws(IOException::class)
    fun notValidAnkiDroidAddonTest() {
        // test addon is valid or not, for a not valid addon the result is Invalid
        val result = getAddonModelFromJson(notValidNpmPackageJson)
        // assert that the package.json was not mapped to an addon model
        val errors = assertIs<AddonValidationResult.Invalid>(result, "package.json not contains required fields").errors
        // assert that error list contains error when the package.json not mapped to AddonModel
        assertFalse(errors.isEmpty())
    }

    @Test
    fun unreadableManifestReturnsErrorTest() {
        // a missing/unreadable package.json must be reported as Invalid, not thrown:
        // one corrupt addon directory must not abort listing the other addons
        val result = getAddonModelFromJson(File("/does/not/exist/package.json"))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an unreadable manifest").errors
        assertFalse("unreadable manifest is reported as an error", errors.isEmpty())
    }

    @Test
    fun getAddonModelListFromJsonTest() {
        val url = File(addonsPackageListTestJson).toURI().toURL()
        val result = getAddonModelListFromJson(url)

        // first addon name and tgz download url
        val addon1 = result.first[0]
        assertEquals(addon1.name, "ankidroid-js-addon-progress-bar")
        assertThat(addon1.dist!!.tarball, endsWith(".tgz"))

        // second addon name and tgz download url
        val addon2 = result.first[1]
        assertEquals(addon2.name, "valid-ankidroid-js-addon-test")
        assertThat(addon2.dist!!.tarball, endsWith(".tgz"))
    }

    /**
     * A valid manifest, with individual fields overridable so each test can knock one out
     */
    private fun addonData(
        name: String? = "valid-ankidroid-js-addon-test",
        addonTitle: String? = "Valid AnkiDroid JS Addon",
        icon: String? = "",
        version: String? = "1.0.0",
        description: String? = "A test addon",
        main: String? = "index.js",
        ankidroidJsApi: String? = CURRENT_JS_API_VERSION,
        addonType: String? = REVIEWER_ADDON,
        keywords: List<String>? = listOf(ANKIDROID_JS_ADDON_KEYWORDS),
        author: Map<String, String>? = mapOf("name" to "AnkiDroid"),
        license: String? = "MIT",
        homepage: String? = "https://example.com",
        dist: DistInfo? = DistInfo("https://example.com/addon.tgz"),
    ): AddonData =
        AddonData(
            name,
            addonTitle,
            icon,
            version,
            description,
            main,
            ankidroidJsApi,
            addonType,
            keywords,
            author,
            license,
            homepage,
            dist,
        )

    @Test // the validator must report errors, never throw
    fun missingNameReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(name = null))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("missing 'name' is reported as an error", errors.isEmpty())
    }

    @Test // the validator must report errors, never throw
    fun missingKeywordsReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(keywords = null))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("missing 'keywords' is reported as an error", errors.isEmpty())
    }

    @Test // 'version' is required during model construction, so it must be validated
    fun missingVersionReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(version = null))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("missing 'version' is reported as an error", errors.isEmpty())
    }

    @Test // exercises the checks after the required-fields guard: their errors must be returned
    fun invalidAddonTypeReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(addonType = "invalid-type"))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("invalid 'addonType' is reported as an error", errors.isEmpty())
    }

    @Test
    fun missingDistIsValidTest() {
        // 'dist' is metadata added by the npm registry API; the package.json inside a
        // tarball does not contain it, so a locally installed addon must still validate
        val result = getAddonModelFromAddonData(addonData(dist = null))
        assertIs<AddonValidationResult.Valid>(result, "model is built from a manifest without 'dist'")
    }

    @Test
    fun missingOptionalMetadataIsValidTest() {
        // description/author/license are commonly absent from real package.json files
        val result = getAddonModelFromAddonData(addonData(description = null, author = null, license = null))
        val addon = assertIs<AddonValidationResult.Valid>(result, "model is built from a manifest without optional metadata").addonModel
        assertEquals("", addon.description)
        assertEquals(emptyMap<String, String>(), addon.author)
        assertEquals("", addon.license)
    }
}
